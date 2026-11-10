import Link from "next/link";
import { requiredProfile } from "@/lib/auth/profile";
import { loadSocialFeed } from "@/lib/social/data";
import { pageNumber, postKinds, type PostKind } from "@/lib/social/validation";
import { PostComposer } from "@/components/social/PostComposer";
import {
  PostList,
  SocialNav,
  SocialPagination,
} from "@/components/social/feed";
export const metadata = {
  title: "Professional community",
  robots: { index: false, follow: false },
};
export default async function Feed({
  searchParams,
}: {
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const { user } = await requiredProfile();
  const params = await searchParams;
  const page = pageNumber(params.page);
  const kind = postKinds.includes(params.kind as PostKind)
    ? (params.kind as PostKind)
    : undefined;
  const mine = params.view === "mine";
  const result = await loadSocialFeed({
    page,
    kind,
    authorId: mine ? user.id : undefined,
  });
  const filters = new URLSearchParams();
  if (kind) filters.set("kind", kind);
  if (mine) filters.set("view", "mine");
  return (
    <div className="container page social-page">
      <header className="social-hero">
        <p className="eyebrow">SERA.et / PEOPLE & POSSIBILITIES</p>
        <h1>{mine ? "The stories you bring." : "More than a résumé."}</h1>
        <p>
          Ideas, talents and work worth sharing. Build your professional story,
          one connection at a time.
        </p>
      </header>
      <SocialNav />
      <div className="social-layout">
        <section className="social-main" aria-label="Community stories">
          <PostComposer />
          <nav className="social-filters" aria-label="Filter stories">
            <Link
              href={mine ? "/feed?view=mine" : "/feed"}
              aria-current={!kind ? "page" : undefined}
            >
              All stories
            </Link>
            {postKinds.map((k) => (
              <Link
                key={k}
                href={`/feed?${mine ? "view=mine&" : ""}kind=${k}`}
                aria-current={kind === k ? "page" : undefined}
              >
                {k === "question" ? "Discussions" : k}
              </Link>
            ))}
          </nav>
          <PostList posts={result.posts} userId={user.id} />
          <SocialPagination
            page={page}
            hasMore={result.hasMore}
            href={"/feed" + (filters.size ? "?" + filters.toString() : "")}
          />
        </section>
        <aside className="social-aside">
          <p className="eyebrow">MAKE YOUR NEXT MOVE</p>
          <h2>Let your work open doors.</h2>
          <p>
            Your perspective belongs here. Share a project, celebrate a
            milestone or ask a question that moves your field forward.
          </p>
          <Link href="/settings">Complete your profile →</Link>
          <Link href="/jobs">Explore career opportunities →</Link>
        </aside>
      </div>
    </div>
  );
}
