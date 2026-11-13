import { memberCards } from "@/lib/activity/members";
import "server-only";
import { sanitizeDiscoveryQuery } from "@/lib/network/model";
import { searchSince, type SearchFilters } from "@/lib/network/search-filters";
import { serverClient, currentUser } from "@/lib/supabase/server";
import { type PostKind } from "./validation";
export type SocialComment = {
  id: string;
  author_id: string;
  parent_id: string | null;
  body: string;
  created_at: string;
  author: string;
  photo?: string | null;
  headline?:string; liked?:boolean; like_count?:number;
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
  identityVerified?:boolean;
  author: { id: string; full_name: string; headline: string; location: string };
  images: { url: string; alt: string; width?:number; height?:number }[];
  liked: boolean;
  saved: boolean;
  repost_of?: string|null;
  repost_note?: string;
  audience?: "public"|"connections"|"followers"|"only_me";
  reposted?: boolean;
  repost_count?: number;
  original?: {id:string;author_id:string;title:string;body:string;published_at:string;audience?:string;name:string;photo?:string|null;images:{url:string;alt:string;width?:number;height?:number}[]}|null;
};
export async function loadSocialFeed(
  options: {
    authorId?: string;
    query?: string;
    savedOnly?: boolean;
    postId?: string;
    kind?: PostKind;
    excludeKind?: PostKind;
    filters?: SearchFilters;
    page?: number;
    personalized?: boolean;
  } = {},
) {
  const db = await serverClient();
  const user = await currentUser();
  const auth = { user };
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
  if (options.excludeKind) query = query.neq("kind", options.excludeKind);
  const filters = options.filters;
  if (filters?.location) query = query.ilike("location", "%"+filters.location+"%");
  if (filters?.skill) query = query.contains("skills", [filters.skill]);
  const since = searchSince(filters?.days || "");
  if (since) query = query.gte("published_at", since);
  const start = ((options.page || 1) - 1) * 20;
  const { data: rows, error } = options.personalized && !options.authorId && !options.query && !options.savedOnly && !options.postId && !options.kind && !options.excludeKind && !options.filters ? await db.rpc("sera_ranked_feed",{p_offset:start,p_limit:21}) : await query
    .order("published_at", { ascending: false })
    .order("id", { ascending: false })
    .range(start, start + 20);
  if (error)
    throw new Error("Unable to load the community feed. Please try again.");
  const posts = (rows || []).slice(0, 20) as unknown as SocialPost[];
  const sourceIds=[...new Set(posts.flatMap(p=>p.repost_of?[p.repost_of]:[]))];
  const {data:sourceRows}=sourceIds.length?await db.from("posts").select("id,author_id,title,body,published_at,audience").in("id",sourceIds).eq("status","published"):{data:[]};
  const sources=sourceRows||[];
  const ids = [...new Set([...posts.map((p) => p.id),...sources.map(p=>p.id)])];
  if (!ids.length) return { posts: [], userId: auth.user.id, hasMore: false };
  const [profiles, images, likes, saves, people, repostStateResult] = await Promise.all([
    db.rpc("sera_feed_headlines",{p_ids:[...new Set(posts.map(p=>p.author_id))]}),
    db
      .from("post_images")
      .select("id,post_id,alt,width,height")
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
    memberCards([...posts.map((p) => p.author_id),...sources.map(p=>p.author_id)]),
    db.rpc("sera_repost_states",{p_ids:[...new Set(posts.map(p=>p.repost_of||p.id))]}),
  ]);
  if ([profiles, images, likes, saves].some((r) => r.error))
    throw new Error("Unable to load post details. Please refresh.");
  const imageRows = images.data || [];
  const {data:repostStates}=repostStateResult;
  const states=new Map<string,{reposted:boolean;repost_count:number}>((repostStates||[]).map((r:{id:string;reposted:boolean;repost_count:number})=>[r.id,r]));
  const authorMap = new Map<string,{id:string;headline:string;full_name:string;location:string}>((profiles.data || []).map((p:{id:string;headline:string}) => [p.id,{id:p.id,headline:p.headline||"",full_name:"SERA member",location:""}]));
  const liked = new Set((likes.data || []).map((p) => p.post_id));
  const saved = new Set((saves.data || []).map((p) => p.post_id));
  return {
    userId: auth.user.id,
    hasMore: (rows?.length || 0) > 20,
    posts: posts.map((p) => ({
      ...p,
      photo: people.get(p.author_id)?.photo,
      identityVerified:people.get(p.author_id)?.identityVerified,
      author: { ...(authorMap.get(p.author_id) || {
        id: p.author_id,
        full_name: "Sera member",
        headline: "",
        location: "",
      }), full_name: people.get(p.author_id)?.name || authorMap.get(p.author_id)?.full_name || "Sera member" },
      images: imageRows.flatMap((i) =>
        i.post_id === p.id
          ? [
              {
                url: `/api/post-media/${i.id}`,
                alt: i.alt || "Community photo",
                width:i.width||undefined,height:i.height||undefined,
              },
            ]
          : [],
      ),
      original: p.repost_of ? (()=>{const o=sources.find(x=>x.id===p.repost_of);return o?{...o,name:people.get(o.author_id)?.name||"SERA member",photo:people.get(o.author_id)?.photo,images:imageRows.flatMap((i)=>i.post_id===o.id?[{url:`/api/post-media/${i.id}`,alt:i.alt||"Community photo",width:i.width||undefined,height:i.height||undefined}]:[])}:null})():null,
      reposted:states.get(p.repost_of||p.id)?.reposted||false,
      repost_count:Number(states.get(p.repost_of||p.id)?.repost_count||0),
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
  const heads=new Map<string,string>();const reactions=new Map<string,{liked:boolean;like_count:number}>();
  const authors=[...new Set(all.map(c=>c.author_id))];for(let i=0;i<authors.length;i+=100){const {data}=await db.rpc("sera_feed_headlines",{p_ids:authors.slice(i,i+100)});for(const h of data||[])heads.set(h.id,h.headline||"");}
  for(let i=0;i<all.length;i+=200){const {data}=await db.rpc("sera_comment_reaction_states",{p_ids:all.slice(i,i+200).map(c=>c.id)});for(const r of data||[])reactions.set(r.id,{liked:r.liked,like_count:Number(r.like_count)});}
  return {
    hasMore: (roots.data?.length || 0) > 10,
    comments: all.map((c) => ({
      ...c,
      photo: people.get(c.author_id)?.photo,
      author: people.get(c.author_id)?.name || "Sera member", headline:heads.get(c.author_id)||"",liked:reactions.get(c.id)?.liked||false,like_count:reactions.get(c.id)?.like_count||0,
    })) as SocialComment[],
  };
}
