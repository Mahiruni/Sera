import Link from "next/link";
import type { SocialPost } from "@/lib/social/data";
import { PostCard } from "./PostCard";
export function SocialNav() {
  return (
    <nav className="social-nav" aria-label="Community">
      <Link href="/feed">Community feed</Link>
      <Link href="/feed?view=mine">My stories</Link>
      <Link href="/saved">Saved</Link>
      <Link href="/jobs">Find opportunities</Link>
      <Link href="/dashboard">Workspace</Link>
    </nav>
  );
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
