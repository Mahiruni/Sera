import { notFound } from "next/navigation";
import { requiredProfile } from "@/lib/auth/profile";
import { loadSocialFeed, loadComments } from "@/lib/social/data";
import { uuid, pageNumber } from "@/lib/social/validation";
import { SocialNav } from "@/components/social/feed";
import { PostCard } from "@/components/social/PostCard";
import { Comments } from "@/components/social/comments";
export const metadata = {
  title: "Community story",
  robots: { index: false, follow: false },
};
export default async function Story({
  params,
  searchParams,
}: {
  params: Promise<{ id: string }>;
  searchParams: Promise<{ comments?: string }>;
}) {
  const { id } = await params;
  const { user } = await requiredProfile(uuid(id) ? "/posts/" + id : "/feed");
  if (!uuid(id)) notFound();
  const { posts } = await loadSocialFeed({ postId: id });
  const post = posts[0];
  if (!post) notFound();
  const page = pageNumber((await searchParams).comments);
  const thread = await loadComments(id, page);
  return (
    <div className="container page social-page social-detail">
      <SocialNav />
      <PostCard post={post} currentUserId={user.id} detail />
      <Comments
        comments={thread.comments}
        postId={id}
        userId={user.id}
        ownerId={post.author_id}
        page={page}
        hasMore={thread.hasMore}
      />
    </div>
  );
}
