import "server-only";
import { serverClient } from "@/lib/supabase/server";

export type SocialPost = {
  id: string;
  author_id: string;
  kind: string;
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
  edited_at: string | null;
  author: {
    id: string;
    full_name: string;
    headline: string;
    location: string;
  };
  images: { url: string; alt: string }[];
  comments: {
    id: string;
    author_id: string;
    parent_id: string | null;
    body: string;
    created_at: string;
    author: string;
  }[];
  liked: boolean;
  saved: boolean;
};

export async function loadSocialFeed(options: {
  authorId?: string;
  savedOnly?: boolean;
} = {}) {
  const db = await serverClient();
  const { data: auth } = await db.auth.getUser();

  if (!auth.user) return { posts: [], userId: "" };

  let savedIds: string[] = [];

  if (options.savedOnly) {
    const { data } = await db
      .from("post_saves")
      .select("post_id")
      .order("created_at", { ascending: false });

    savedIds = (data || []).map(x => x.post_id);

    if (!savedIds.length) {
      return { posts: [], userId: auth.user.id };
    }
  }

  let query = db
    .from("posts")
    .select("*")
    .eq("status", "published")
    .order("published_at", { ascending: false })
    .limit(40);

  if (options.authorId) {
    query = query.eq("author_id", options.authorId);
  }

  if (options.savedOnly) {
    query = query.in("id", savedIds);
  }

  const { data: rows, error } = await query;

  if (error) throw new Error("Unable to load the community feed.");

  const posts = rows || [];
  const ids = posts.map(p => p.id);
  const authors = [...new Set(posts.map(p => p.author_id))];

  if (!ids.length) {
    return { posts: [], userId: auth.user.id };
  }

  const [profiles, images, comments, likes, saves] = await Promise.all([
    db.from("post_authors")
      .select("id,full_name,headline,location")
      .in("id", authors),

    db.from("post_images")
      .select("post_id,path,alt,sort_order")
      .in("post_id", ids)
      .order("sort_order"),

    db.from("post_comments")
      .select("id,post_id,author_id,parent_id,body,created_at")
      .in("post_id", ids)
      .order("created_at")
      .limit(200),

    db.from("post_likes")
      .select("post_id")
      .in("post_id", ids),

    db.from("post_saves")
      .select("post_id")
      .in("post_id", ids),
  ]);

  const commentAuthorIds = [
    ...new Set((comments.data || []).map(c => c.author_id))
  ];

  const commentProfiles = commentAuthorIds.length
    ? await db.from("post_authors")
        .select("id,full_name")
        .in("id", commentAuthorIds)
    : { data: [] };

  const authorMap = new Map(
    (profiles.data || []).map(p => [p.id, p])
  );

  const commentMap = new Map(
    (commentProfiles.data || []).map(p => [p.id, p.full_name])
  );

  const imageMap = new Map<string, SocialPost["images"]>();

  for (const img of images.data || []) {
    const signed = await db.storage
      .from("sera-post-media")
      .createSignedUrl(img.path, 3600);

    if (!signed.data?.signedUrl) continue;

    const list = imageMap.get(img.post_id) || [];

    list.push({
      url: signed.data.signedUrl,
      alt: img.alt || "Sera community image",
    });

    imageMap.set(img.post_id, list);
  }

  const commentsMap = new Map<string, SocialPost["comments"]>();

  for (const c of comments.data || []) {
    const list = commentsMap.get(c.post_id) || [];

    list.push({
      id: c.id,
      author_id: c.author_id,
      parent_id: c.parent_id,
      body: c.body,
      created_at: c.created_at,
      author: commentMap.get(c.author_id) || "Sera member",
    });

    commentsMap.set(c.post_id, list);
  }

  const liked = new Set((likes.data || []).map(x => x.post_id));
  const saved = new Set((saves.data || []).map(x => x.post_id));

  return {
    userId: auth.user.id,
    posts: posts.map(p => ({
      ...p,
      author: authorMap.get(p.author_id) || {
        id: p.author_id,
        full_name: "Sera member",
        headline: "",
        location: "",
      },
      images: imageMap.get(p.id) || [],
      comments: commentsMap.get(p.id) || [],
      liked: liked.has(p.id),
      saved: saved.has(p.id),
    })) as SocialPost[],
  };
}
