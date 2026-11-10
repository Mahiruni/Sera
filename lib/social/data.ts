import { memberCards } from "@/lib/activity/members";
import "server-only";
import { sanitizeDiscoveryQuery } from "@/lib/network/model";
import { serverClient } from "@/lib/supabase/server";
import { MEDIA_BUCKET, type PostKind } from "./validation";
export type SocialComment = {
  id: string;
  author_id: string;
  parent_id: string | null;
  body: string;
  created_at: string;
  author: string;
  photo?: string | null;
};
export type SocialPost = {
  id: string;
  author_id: string;
  kind: PostKind;
  title: string;
  body: string;
  quote_source: string;
  category: string;
  location: string;
  skills: string[];
  project_url: string | null;
  like_count: number;
  comment_count: number;
  share_count: number;
  created_at: string;
  published_at: string;
  edited_at: string | null;
  photo?: string | null;
  author: { id: string; full_name: string; headline: string; location: string };
  images: { url: string; alt: string }[];
  liked: boolean;
  saved: boolean;
};
export async function loadSocialFeed(
  options: {
    authorId?: string;
    query?: string;
    savedOnly?: boolean;
    postId?: string;
    kind?: PostKind;
    page?: number;
  } = {},
) {
  const db = await serverClient();
  const { data: auth } = await db.auth.getUser();
  if (!auth.user)
    return { posts: [] as SocialPost[], userId: "", hasMore: false };
  let query = db
    .from("posts")
    .select(options.savedOnly ? "*,post_saves!inner(user_id)" : "*")
    .eq("status", "published");
  if (options.savedOnly) query = query.eq("post_saves.user_id", auth.user.id);
  const search = sanitizeDiscoveryQuery(options.query);
  if (search)
    query = query.or(
      `title.ilike.%${search}%,body.ilike.%${search}%,category.ilike.%${search}%,skills.cs.{${search}}`,
    );
  if (options.authorId) query = query.eq("author_id", options.authorId);
  if (options.postId) query = query.eq("id", options.postId);
  if (options.kind) query = query.eq("kind", options.kind);
  const start = ((options.page || 1) - 1) * 20;
  const { data: rows, error } = await query
    .order("published_at", { ascending: false })
    .order("id", { ascending: false })
    .range(start, start + 20);
  if (error)
    throw new Error("Unable to load the community feed. Please try again.");
  const posts = (rows || []).slice(0, 20) as unknown as SocialPost[];
  const ids = posts.map((p) => p.id);
  if (!ids.length) return { posts: [], userId: auth.user.id, hasMore: false };
  const [profiles, images, likes, saves, people] = await Promise.all([
    db
      .from("post_authors")
      .select("id,full_name,headline,location")
      .in("id", [...new Set(posts.map((p) => p.author_id))]),
    db
      .from("post_images")
      .select("post_id,path,alt")
      .in("post_id", ids)
      .order("sort_order"),
    db
      .from("post_likes")
      .select("post_id")
      .in("post_id", ids)
      .eq("user_id", auth.user.id),
    db
      .from("post_saves")
      .select("post_id")
      .in("post_id", ids)
      .eq("user_id", auth.user.id),
    memberCards(posts.map((p) => p.author_id)),
  ]);
  if ([profiles, images, likes, saves].some((r) => r.error))
    throw new Error("Unable to load post details. Please refresh.");
  const imageRows = images.data || [];
  const signed = imageRows.length
    ? await db.storage.from(MEDIA_BUCKET).createSignedUrls(
        imageRows.map((i) => i.path),
        3600,
      )
    : null;
  if (signed?.error)
    throw new Error("Unable to load post images. Please refresh.");
  const authorMap = new Map((profiles.data || []).map((p) => [p.id, p]));
  const liked = new Set((likes.data || []).map((p) => p.post_id));
  const saved = new Set((saves.data || []).map((p) => p.post_id));
  return {
    userId: auth.user.id,
    hasMore: (rows?.length || 0) > 20,
    posts: posts.map((p) => ({
      ...p,
      photo: people.get(p.author_id)?.photo,
      author: authorMap.get(p.author_id) || {
        id: p.author_id,
        full_name: "Sera member",
        headline: "",
        location: "",
      },
      images: imageRows.flatMap((i, index) =>
        i.post_id === p.id && signed?.data?.[index]?.signedUrl
          ? [
              {
                url: signed.data[index].signedUrl,
                alt: i.alt || "Community photo",
              },
            ]
          : [],
      ),
      liked: liked.has(p.id),
      saved: saved.has(p.id),
    })) as SocialPost[],
  };
}
export async function loadComments(postId: string, page = 1, target?: string) {
  const db = await serverClient();
  // Page top-level threads so replies never lose their parent between pages.
  let rootQuery = db
    .from("post_comments")
    .select("id,author_id,parent_id,body,created_at")
    .eq("post_id", postId)
    .is("parent_id", null)
    .order("created_at", { ascending: false })
    .order("id");
  if (target) {
    const focus = await db
      .from("post_comments")
      .select("id,parent_id")
      .eq("post_id", postId)
      .eq("id", target)
      .maybeSingle();
    if (focus.error) throw new Error("Unable to load conversation.");
    if (focus.data)
      rootQuery = rootQuery.eq("id", focus.data.parent_id || focus.data.id);
  }
  const roots = await rootQuery.range((page - 1) * 10, page * 10);
  if (roots.error) throw new Error("Unable to load comments.");
  const top = (roots.data || []).slice(0, 10);
  const replies: typeof top = [];
  if (top.length) {
    // PostgREST caps each result page; fetch every reply for the displayed roots.
    for (let start = 0; ; start += 500) {
      const batch = await db
        .from("post_comments")
        .select("id,author_id,parent_id,body,created_at")
        .eq("post_id", postId)
        .in(
          "parent_id",
          top.map((c) => c.id),
        )
        .order("created_at")
        .order("id")
        .range(start, start + 499);
      if (batch.error) throw new Error("Unable to load replies.");
      replies.push(...(batch.data || []));
      if ((batch.data?.length || 0) < 500) break;
    }
  }
  const all = [...top, ...replies];
  const people = await memberCards(all.map((c) => c.author_id));
  return {
    hasMore: (roots.data?.length || 0) > 10,
    comments: all.map((c) => ({
      ...c,
      photo: people.get(c.author_id)?.photo,
      author: people.get(c.author_id)?.name || "Sera member",
    })) as SocialComment[],
  };
}
