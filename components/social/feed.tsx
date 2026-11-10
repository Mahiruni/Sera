import Link from "next/link";
import type { SocialPost } from "@/lib/social/data";
import { PostCard } from "./PostCard";
/** Navigation is provided globally by Shell\u2019s persistent footer dock. */
export function SocialNav() {
  return null;
}
export function PostList({
  posts,
  userId,
  empty = "Your next connection starts with a story.",
}: {
  posts: SocialPost[];
  userId: string;
  empty?: string;
}) {
  return (
    <div className="social-post-list">
      {posts.length ? (
        posts.map((post) => (
          <PostCard post={post} currentUserId={userId} key={post.id} />
        ))
      ) : (
        <div className="social-empty">
          <h2>{empty}</h2>
          <p>
            Share a useful idea, a project or a talent with your professional
            community.
          </p>
        </div>
      )}
    </div>
  );
}
export function SocialPagination({
  page,
  hasMore,
  href,
}: {
  page: number;
  hasMore: boolean;
  href: string;
}) {
  const link = (n: number) =>
    href + (href.includes("?") ? "&" : "?") + "page=" + n;
  return (
    (page > 1 || hasMore) && (
      <nav className="social-pagination" aria-label="Post pages">
        {page > 1 && <Link href={link(page - 1)}>← Newer stories</Link>}
        <span>Page {page}</span>
        {hasMore && <Link href={link(page + 1)}>Older stories →</Link>}
      </nav>
    )
  );
}
