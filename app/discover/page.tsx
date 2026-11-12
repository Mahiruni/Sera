import Link from "next/link";
import { PageIntro } from "@/components/page-intro";
import {
  UsersRound,
  PenLine,
  FolderKanban,
  BriefcaseBusiness,
  Building2,
  Hash,
  Search,
  ArrowRight,
  Compass,
} from "lucide-react";
import { requiredProfile } from "@/lib/auth/profile";
import { loadSocialFeed } from "@/lib/social/data";
import { discoverDirectory } from "@/lib/network/data";
import {
  discoveryInput,
  discoveryKinds,
  type DiscoveryKind,
} from "@/lib/network/model";
import {
  NetworkToolbar,
  MemberPreview,
  OpportunityPreview,
} from "@/components/network/shared";
import { PostList, SocialPagination } from "@/components/social/feed";
export const metadata = {
  title: "Discover people, projects and possibilities",
  robots: { index: false, follow: false },
};
const tabs: Record<DiscoveryKind, { label: string; Icon: typeof UsersRound }> =
  {
    people: { label: "People", Icon: UsersRound },
    stories: { label: "Stories", Icon: PenLine },
    projects: { label: "Projects", Icon: FolderKanban },
    jobs: { label: "Jobs", Icon: BriefcaseBusiness },
    companies: { label: "Companies", Icon: Building2 },
    topics: { label: "Topics", Icon: Hash },
  };
export default async function Discover({
  searchParams,
}: {
  searchParams: Promise<Record<string, string | string[] | undefined>>;
}) {
  const { user } = await requiredProfile("/discover");
  const { kind, query, page } = discoveryInput(await searchParams);
  const feed =
    kind === "stories" || kind === "projects"
      ? await loadSocialFeed({
          query,
          kind: kind === "projects" ? "project" : undefined,
          page,
        })
      : null;
  const directory = feed ? null : await discoverDirectory(kind, query, page);
  const anyResults = feed
    ? feed.posts.length
    : directory!.people.length +
      directory!.jobs.length +
      directory!.topics.length;
  const companies = [
    ...new Map(
      (directory?.jobs || []).map((job) => [job.company, job]),
    ).values(),
  ];
  return (
    <div className="sera-network network-app">
      <NetworkToolbar query={query} discover />
      <PageIntro icon={Compass} title="Discover your next connection."
        description="People, ideas, projects and opportunities worth exploring." />
      <Link className="network-button" href="/talent">Discover portfolio talent <FolderKanban size={17}/></Link>
      <Link className="network-button" href="/communities">Explore professional communities <UsersRound size={17}/></Link>
      <form
        action="/discover"
        className="network-search network-discover-search"
        role="search"
      >
        <Search size={18} />
        <label className="sr-only" htmlFor="discover-filter">
          Search {tabs[kind].label.toLowerCase()}
        </label>
        <input
          id="discover-filter"
          name="q"
          defaultValue={query}
          maxLength={80}
          placeholder={"Search " + tabs[kind].label.toLowerCase() + "…"}
        />
        <input type="hidden" name="tab" value={kind} />
        <button aria-label="Search selected category">
          <ArrowRight size={18} />
        </button>
      </form>
      <nav className="network-discover-tabs" aria-label="Discover categories">
        {discoveryKinds.map((tab) => {
          const { label, Icon } = tabs[tab];
          return (
            <Link
              key={tab}
              href={
                "/discover?tab=" +
                tab +
                (query ? "&q=" + encodeURIComponent(query) : "")
              }
              aria-current={kind === tab ? "page" : undefined}
            >
              <Icon size={17} />
              {label}
            </Link>
          );
        })}
      </nav>
      <div className="network-results-heading">
        <h2>
          {query
            ? "Results for “" + query + "”"
            : kind === "companies"
              ? "Organizations hiring"
              : kind === "topics"
                ? "Topics from recent stories"
                : kind === "people"
                  ? "People behind the possibilities"
                  : "Explore " + tabs[kind].label.toLowerCase()}
        </h2>
        <span>Page {page}</span>
      </div>
      {anyResults ? (
        <>
          {feed ? (
            <div className="network-discover-feed social-page">
              <PostList posts={feed.posts} userId={user.id} />
            </div>
          ) : (
            <div className="network-discover-results">
              {kind === "people" &&
                directory!.people.map((member) => (
                  <MemberPreview key={member.id} member={member} />
                ))}
              {kind === "jobs" &&
                directory!.jobs.map((job) => (
                  <OpportunityPreview key={job.id} job={job} />
                ))}
              {kind === "companies" &&
                companies.map((company) => (
                  <article key={company.company} className="network-company">
                    <span>{company.company.slice(0, 1)}</span>
                    <h3>{company.company}</h3>
                    <p>{company.location} · Listed on an open job posting</p>
                    <Link href={"/jobs?q=" + encodeURIComponent(company.title)}>
                      View a current opportunity <ArrowRight size={15} />
                    </Link>
                  </article>
                ))}
              {kind === "topics" &&
                directory!.topics.map((topic) => (
                  <Link
                    className="network-topic"
                    key={topic}
                    href={
                      "/discover?tab=stories&q=" + encodeURIComponent(topic)
                    }
                  >
                    <span>PROFESSIONAL TOPIC</span>
                    <strong>#{topic}</strong>
                  </Link>
                ))}
            </div>
          )}
          <SocialPagination
            page={page}
            hasMore={feed?.hasMore || directory?.hasMore || false}
            href={
              "/discover?tab=" +
              kind +
              (query ? "&q=" + encodeURIComponent(query) : "")
            }
          />
        </>
      ) : (
        <div className="network-discover-empty">
          <Search size={27} />
          <h2>
            {query ? "A little more exploring." : "Room for new possibilities."}
          </h2>
          <p>
            {query
              ? "No matches on this page. Try another name, skill or keyword, or explore a different category."
              : "As members share work and employers publish roles, you’ll find them here. Your own story could be the next one."}
          </p>
          <Link
            href={query ? "/discover?tab=" + kind : "/feed"}
            className="network-button"
          >
            {query ? "Clear your search" : "Share your story"}
            <ArrowRight size={16} />
          </Link>
        </div>
      )}
      <p className="network-search-caption">
        {kind === "people"
          ? "People appear after sharing a published story or joining a published conversation. Private account details stay private."
          : kind === "companies"
            ? "Organizations are drawn from current employer-posted opportunities. Dedicated company profiles are part of the next phase."
            : kind === "topics"
              ? "Topics are drawn from skills and categories on the 500 most recent published stories."
              : "Search the published work and opportunities shared on Sera."}
      </p>
    </div>
  );
}
