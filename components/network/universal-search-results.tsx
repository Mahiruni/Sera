import Link from "next/link";
import { ArrowRight, FileText, FolderKanban, Search, Building2 } from "lucide-react";
import type { ReactNode } from "react";
import { MemberPreview, OpportunityPreview } from "@/components/network/shared";
import { loadUniversalSearch } from "@/lib/network/universal-search";
import type { DiscoveryKind } from "@/lib/network/model";
import type { SocialPost } from "@/lib/social/data";

function categoryHref(kind: DiscoveryKind, query: string) {
  return "/discover?tab=" + kind + "&q=" + encodeURIComponent(query);
}

function SearchSection({ title, kind, query, children }: {
  title: string; kind: DiscoveryKind; query: string; children: ReactNode;
}) {
  return (
    <section className="network-universal-section" aria-label={title}>
      <header className="network-universal-section-head">
        <h2>{title}</h2>
        <Link href={categoryHref(kind, query)} className="network-text-link">
          See all <ArrowRight size={15} aria-hidden="true" />
        </Link>
      </header>
      <div className="network-universal-cards">{children}</div>
    </section>
  );
}

function PostPreview({ post }: { post: SocialPost }) {
  const Icon = post.kind === "project" ? FolderKanban : FileText;
  const title = post.title.trim() || post.body.trim().slice(0, 90) || "Post";
  const snippet = post.repost_of ? post.repost_note || "" : post.body;
  return (
    <Link className="network-universal-preview" href={"/posts/" + post.id}>
      <span className="network-universal-icon"><Icon size={20} aria-hidden="true" /></span>
      <span className="network-universal-copy">
        <strong>{title}</strong>
        <small>{post.author.full_name}</small>
        {snippet && snippet !== title && <p>{snippet.slice(0, 160)}</p>}
      </span>
      <ArrowRight size={17} aria-hidden="true" />
    </Link>
  );
}

export async function UniversalSearchResults({ query }: { query: string }) {
  const result = await loadUniversalSearch(query);
  if (!result) {
    return <div className="network-discover-empty"><Search size={28} aria-hidden="true" /><h2>What are you looking for?</h2><p>Search a name, skill, company, job title or topic.</p></div>;
  }
  const hasResults = result.people.length + result.jobs.length + result.posts.length + result.projects.length + result.companies.length + result.topics.length > 0;
  return (
    <div className="network-universal-results">
      <div className="network-results-heading"><h2>Results for “{query}”</h2></div>
      {result.unavailable && <p role="status" className="network-search-caption">Some categories could not load. Refresh to try again.</p>}
      {!hasResults && <div className="network-discover-empty"><Search size={27} aria-hidden="true" /><h2>{result.unavailable ? "Search is temporarily unavailable" : "No matching results"}</h2><p>{result.unavailable ? "Please try again." : "Try a different keyword or a broader search."}</p></div>}
      {!!result.people.length && <SearchSection title="People" kind="people" query={query}>{result.people.map(member => <MemberPreview key={member.id} member={member} />)}</SearchSection>}
      {!!result.jobs.length && <SearchSection title="Jobs" kind="jobs" query={query}>{result.jobs.map(job => <OpportunityPreview key={job.id} job={job} />)}</SearchSection>}
      {!!result.posts.length && <SearchSection title="Posts" kind="stories" query={query}>{result.posts.map(post => <PostPreview key={post.id} post={post} />)}</SearchSection>}
      {!!result.projects.length && <SearchSection title="Projects" kind="projects" query={query}>{result.projects.map(post => <PostPreview key={post.id} post={post} />)}</SearchSection>}
      {!!result.companies.length && <SearchSection title="Companies hiring" kind="companies" query={query}>{result.companies.map(job => <Link className="network-universal-preview" href={"/jobs/" + job.id} key={job.company}><span className="network-universal-icon"><Building2 size={20} aria-hidden="true" /></span><span className="network-universal-copy"><strong>{job.company}</strong><small>{job.location} · Open opportunity</small></span><ArrowRight size={17} aria-hidden="true" /></Link>)}</SearchSection>}
      {!!result.topics.length && <SearchSection title="Topics" kind="topics" query={query}>{result.topics.map(topic => <Link className="network-topic" key={topic} href={categoryHref("stories", topic)}><strong>#{topic}</strong></Link>)}</SearchSection>}
    </div>
  );
}
