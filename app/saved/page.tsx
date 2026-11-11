import Link from "next/link";
import { loadSocialFeed } from "@/lib/social/data";
import { PostList, SocialPagination } from "@/components/social/feed";
import { pageNumber } from "@/lib/social/validation";
import { requiredProfile } from "@/lib/auth/profile";
import { serverClient } from "@/lib/supabase/server";
import { type LiveJob } from "@/lib/recruitment/data";
import { LiveJobCard, Empty } from "@/components/recruitment/ui";
import { PageIntro } from "@/components/page-intro";
import { Bookmark, BriefcaseBusiness, ArrowUpRight } from "lucide-react";
import { toggleSavedJob } from "@/lib/recruitment/actions";
import { pageMetadata } from "@/lib/seo";
export const metadata = pageMetadata(
  "/saved",
  "Saved stories and jobs",
  "Your private collection of stories and opportunities, synchronized with your account.",
);
export default async function Page({
  searchParams,
}: {
  searchParams: Promise<{ page?: string }>;
}) {
  const { user } = await requiredProfile();
  const page = pageNumber((await searchParams).page);
  const social = await loadSocialFeed({ savedOnly: true, page }).catch(
    () => null,
  );
  const c = await serverClient();
  const { data, error } = await c
    .from("saved_jobs")
    .select("job_id,jobs(*)")
    .eq("user_id", user.id)
    .order("created_at", { ascending: false })
    .limit(100);
  const rows = (data || []) as unknown as {
    job_id: string;
    jobs: LiveJob | null;
  }[];
  return (
    <div className="container page live-market workspace-detail sera-saved-page">
      <PageIntro
        icon={Bookmark}
        title="Keep the possibilities close."
        description="The stories that inspire you. The opportunities you want to return to."
      >
        <Link className="button secondary" href="/discover">
          Explore more <ArrowUpRight size={16} />
        </Link>
      </PageIntro>
      <nav className="sera-section-tabs" aria-label="Saved collections">
        <a href="#saved-stories">
          <Bookmark size={17} /> Stories
        </a>
        <a href="#saved-jobs">
          <BriefcaseBusiness size={17} /> Jobs
        </a>
      </nav>
      <section
        className="social-page sera-saved-stories"
        aria-labelledby="saved-stories"
      >
        <h2 id="saved-stories" className="sera-section-title">
          Saved stories
        </h2>
        {social ? (
          <>
            <PostList
              posts={social.posts}
              userId={user.id}
              empty="Keep a story for later."
            />
            <SocialPagination
              page={page}
              hasMore={social.hasMore}
              href="/saved"
            />
          </>
        ) : (
          <p role="alert">Saved stories could not be loaded. Please refresh.</p>
        )}
      </section>
      <section aria-labelledby="saved-jobs" className="sera-saved-jobs">
        <h2 id="saved-jobs" className="sera-section-title">
          Saved jobs
        </h2>
        {error ? (
          <Empty title="Saved jobs could not be loaded">
            <p>Please refresh.</p>
          </Empty>
        ) : rows.length ? (
          <div className="live-job-grid">
            {rows.map((r) => (
              <div key={r.job_id}>
                {r.jobs ? (
                  <LiveJobCard job={r.jobs} />
                ) : (
                  <Empty title="This posting is no longer public">
                    <p>It may have closed.</p>
                  </Empty>
                )}
                <form action={toggleSavedJob} className="live-remove">
                  <input name="job_id" type="hidden" value={r.job_id} />
                  <input name="remove" type="hidden" value="yes" />
                  <button className="button secondary">Remove</button>
                </form>
              </div>
            ))}
          </div>
        ) : (
          <Empty title="A little room for possibilities">
            <p>
              Save roles from their detail page and find them here on any
              device.
            </p>
            <Link className="inline-link" href="/jobs">
              Explore jobs →
            </Link>
          </Empty>
        )}
      </section>
    </div>
  );
}
