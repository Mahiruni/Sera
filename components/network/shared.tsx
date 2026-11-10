import Link from "next/link";
import {
  ArrowUpRight,
  Search,
  BriefcaseBusiness,
  Compass,
  Bookmark,
  MessageCircle,
  UsersRound,
  Bell,
  ArrowRight,
} from "lucide-react";
import { ThemeToggle } from "./theme-toggle";
export type MemberCard = {
  id: string;
  full_name: string;
  headline: string;
  location: string;
};
export type OpportunityCard = {
  id: string;
  title: string;
  company: string;
  location: string;
  work_type: string;
};
export function NetworkBrand() {
  return (
    <Link href="/" className="network-wordmark" aria-label="Sera.et home">
      Sera<span>.et</span>
      <i aria-hidden="true" />
    </Link>
  );
}
export function NetworkToolbar({
  query = "",
  discover = false,
}: {
  query?: string;
  discover?: boolean;
}) {
  return (
    <div className="network-toolbar">
      <NetworkBrand />
      <form action="/discover" role="search" className="network-search">
        <Search size={17} aria-hidden="true" />
        <label className="sr-only" htmlFor="network-q">
          Search professionals, skills and stories
        </label>
        <input
          id="network-q"
          name="q"
          defaultValue={query}
          maxLength={80}
          placeholder="People, skills, possibilities…"
        />
        {discover && <input type="hidden" name="tab" value="people" />}
        <button type="submit" aria-label="Search Sera">
          <ArrowRight size={17} />
        </button>
      </form>
      <ThemeToggle />
    </div>
  );
}
export function MemberPreview({ member }: { member: MemberCard }) {
  return (
    <Link href={"/members/" + member.id} className="network-person">
      <span className="network-person-avatar">
        {(member.full_name || "S").slice(0, 1).toUpperCase()}
      </span>
      <span>
        <strong>{member.full_name || "Sera member"}</strong>
        <small>
          {member.headline ||
            member.location ||
            "Professional community member"}
        </small>
      </span>
      <ArrowUpRight size={16} aria-hidden="true" />
    </Link>
  );
}
export function OpportunityPreview({ job }: { job: OpportunityCard }) {
  return (
    <Link href={"/jobs/" + job.id} className="network-job">
      <span className="network-job-logo">
        {job.company.slice(0, 1).toUpperCase()}
      </span>
      <span>
        <strong>{job.title}</strong>
        <small>
          {job.company} · {job.location}
        </small>
        <em>{job.work_type}</em>
      </span>
      <ArrowUpRight size={15} aria-hidden="true" />
    </Link>
  );
}
export function NextOnSera({ compact = false }: { compact?: boolean }) {
  return (
    <section
      className={compact ? "network-next compact" : "network-next"}
      id="next-on-sera"
    >
      <div className="network-section-heading">
        <div>
          <span className="network-kicker">A MORE CONNECTED SERA</span>
          <h2>
            {compact ? "Next on Sera" : "Good work brings people together."}
          </h2>
        </div>
        <span className="network-upcoming">Coming next</span>
      </div>
      {!compact && (
        <p className="network-muted">
          Our next chapter creates more ways to connect, collaborate and belong.
        </p>
      )}
      <div className="network-next-grid">
        {[
          {
            Icon: UsersRound,
            title: "Your professional circle",
            body: "Follow people, build connections and find collaborators who share your interests.",
          },
          {
            Icon: MessageCircle,
            title: "Conversations that move work forward",
            body: "One-to-one professional messaging, shared files and real-time conversations.",
          },
          {
            Icon: Bell,
            title: "Communities & updates",
            body: "Spaces for your industry, thoughtful discussions and a personal notification center.",
          },
        ].map(({ Icon, title, body }) => (
          <div key={title}>
            <Icon size={20} aria-hidden="true" />
            <h3>{title}</h3>
            {!compact && <p>{body}</p>}
          </div>
        ))}
      </div>
    </section>
  );
}
export function NetworkLinks() {
  return (
    <nav className="network-shortcuts" aria-label="Professional workspace">
      <Link href="/feed">
        <Compass size={18} />
        Your feed
      </Link>
      <Link href="/discover">
        <UsersRound size={18} />
        Discover people
      </Link>
      <Link href="/jobs">
        <BriefcaseBusiness size={18} />
        Opportunities
      </Link>
      <Link href="/saved">
        <Bookmark size={18} />
        Saved for later
      </Link>
      <Link href="/messages">
        <MessageCircle size={18} />
        Application messages
      </Link>
    </nav>
  );
}
