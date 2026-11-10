import { notFound } from "next/navigation";
import { requiredProfile } from "@/lib/auth/profile";
import { loadSocialFeed } from "@/lib/social/data";
import { uuid, pageNumber } from "@/lib/social/validation";
import {
  PostList,
  SocialNav,
  SocialPagination,
} from "@/components/social/feed";
export const metadata = {
  title: "Member stories",
  robots: { index: false, follow: false },
};
export default async function Member({
  params,
  searchParams,
}: {
  params: Promise<{ id: string }>;
  searchParams: Promise<{ page?: string }>;
}) {
  const { user } = await requiredProfile();
  const { id } = await params;
  if (!uuid(id)) notFound();
  const page = pageNumber((await searchParams).page);
  const result = await loadSocialFeed({ authorId: id, page });
  const author = result.posts[0]?.author;
  return (
    <div className="container page social-page social-detail">
      <SocialNav />
      <header className="social-hero">
        <p className="eyebrow">MEMBER STORIES</p>
        <h1>{author?.full_name || "Sera community member"}</h1>
        <p>
          {author?.headline ||
            "Ideas, achievements and work shared with the community."}
        </p>
        {author?.location && <p>{author.location}</p>}
      </header>
      <PostList
        posts={result.posts}
        userId={user.id}
        empty="No stories to show here yet."
      />
      <SocialPagination
        page={page}
        hasMore={result.hasMore}
        href={"/members/" + id}
      />
    </div>
  );
}
