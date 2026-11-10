import { MemberAvatar } from "@/components/activity/avatar";
import Link from "next/link";

import {
  ArrowUpRight,
  PenLine,
  ImagePlus,
  FolderKanban,
  Quote,
  BriefcaseBusiness,
  Compass,
  MessageCircle,
  Bell,
} from "lucide-react";
import { PostComposer } from "@/components/social/PostComposer";
import { PostList, SocialPagination } from "@/components/social/feed";
import type { SocialPost } from "@/lib/social/data";
import { postKinds, type PostKind } from "@/lib/social/validation";
import { profileEssentials, skillTopics } from "@/lib/network/model";
import {
  MemberPreview,
  OpportunityPreview,
  NetworkLinks,
  NextOnSera,
  type MemberCard,
  type OpportunityCard,
} from "./shared";
export type HomeProfile = {
  full_name?: string;
  headline?: string;
  summary?: string;
  location?: string;
  skills?: string;
  experience?: string;
  education?: string;
};
export function NetworkHome({
  userId,
  profile,
  posts,
  people,
  jobs,
  avatarUrl = null,
  peopleError = false,
  jobsError = false,
  page = 1,
  hasMore = false,
  kind,
  mine = false,
}: {
  userId: string;
  profile: HomeProfile | null;
  posts: SocialPost[];
  people: MemberCard[];
  jobs: OpportunityCard[];
  avatarUrl?: string | null;
  peopleError?: boolean;
  jobsError?: boolean;
  page?: number;
  hasMore?: boolean;
  kind?: PostKind;
  mine?: boolean;
}) {
  const essentials = profileEssentials(profile),
    topics = skillTopics(profile?.skills);
  const filters = new URLSearchParams();
  if (kind) filters.set("kind", kind);
  if (mine) filters.set("view", "mine");
  const name = profile?.full_name?.trim() || "Your professional profile";
  return (
    <div className="sera-network network-app social-page">
      <div className="network-app-grid">
        <aside className="network-left" aria-label="Your professional identity">
          <section className="network-profile-card">
            <div className="network-profile-cover" />
            <div className="network-profile-info">
              <span className="network-own-avatar">
                <MemberAvatar name={name} photo={avatarUrl} size={57} />
              </span>
              <h2><Link href={"/members/" + userId}>{name}</Link></h2>
              <p>
                {profile?.headline ||
                  "Give your professional story a clear headline."}
              </p>
              <Link href="/settings">
                Edit professional profile <ArrowUpRight size={13} />
              </Link>
              <div className="network-completeness">
                <div>
                  <span>Profile essentials</span>
                  <strong>{essentials.percent}%</strong>
                </div>
                <progress
                  value={essentials.percent}
                  max={100}
                  aria-label="Profile essentials completed"
                />
                <small>
                  {essentials.percent === 100
                    ? essentials.next
                    : "Next: " + essentials.next}
                </small>
              </div>
            </div>
          </section>
          <NetworkLinks />
          <div className="network-left-note">
            <span className="network-kicker">YOUR INTERESTS, IN FOCUS</span>
            {topics.length ? (
              <div className="network-skills">
                {topics.map((topic) => (
                  <Link
                    href={
                      "/discover?tab=stories&q=" + encodeURIComponent(topic)
                    }
                    key={topic}
                  >
                    {topic}
                  </Link>
                ))}
              </div>
            ) : (
              <p>
                Add your skills to your profile for quick links to the topics
                you care about.
              </p>
            )}
            <Link href="/settings">Edit your interests →</Link>
          </div>
        </aside>
        <section
          className="network-content"
          aria-label="Professional community feed"
        >
          <header className="network-welcome">
            <div>
              <span className="network-kicker">
                YOUR DAILY DOSE OF POSSIBILITY
              </span>
              <h1>
                {mine ? "Your work. Your voice." : "A good place to grow."}
              </h1>
              <p className="network-welcome-subtitle">
                {mine
                  ? "Everything you have shared with your professional community."
                  : "Discover ideas, people and opportunities worth your time."}
              </p>
            </div>
            <span>People · Ideas · Opportunities</span>
          </header>
          <nav
            className="network-mobile-shortcuts"
            aria-label="Community shortcuts"
          >
            <Link href="/discover">
              <Compass size={14} />
              Discover
            </Link>
            <Link href="/jobs">
              <BriefcaseBusiness size={14} />
              Opportunities
            </Link>
            <Link href="/network/messages">
              <MessageCircle size={14} aria-hidden="true" /> Messages
            </Link>
            <Link href="/notifications">
              <Bell size={14} aria-hidden="true" /> Notifications
            </Link>
            <Link href="/settings">
              My profile <ArrowUpRight size={13} />
            </Link>
          </nav>
          <nav className="network-feed-tabs" aria-label="Feed view">
            <Link href="/feed" aria-current={!mine ? "page" : undefined}>
              Community
            </Link>
            <Link
              href="/feed?view=mine"
              aria-current={mine ? "page" : undefined}
            >
              My stories
            </Link>
            <Link href="/discover?tab=projects">
              Explore projects <ArrowUpRight size={11} />
            </Link>
          </nav>
          <details className="network-compose-toggle">
            <summary aria-label="Create a new professional post">
              <span className="network-compose-avatar">
                <MemberAvatar name={name} photo={avatarUrl} />
              </span>
              <span>Something worth sharing?</span>
              <PenLine size={18} />
              <span className="network-compose-types">
                <span>
                  <ImagePlus size={14} />
                  Photo
                </span>
                <span>
                  <FolderKanban size={14} />
                  Project
                </span>
                <span>
                  <Quote size={14} />
                  Thought
                </span>
              </span>
            </summary>
            <PostComposer />
          </details>
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
                href={"/feed?" + (mine ? "view=mine&" : "") + "kind=" + k}
                aria-current={kind === k ? "page" : undefined}
              >
                {k === "question" ? "Discussions" : k}
              </Link>
            ))}
          </nav>
          <PostList posts={posts} userId={userId} />
          <SocialPagination
            page={page}
            hasMore={hasMore}
            href={"/feed" + (filters.size ? "?" + filters.toString() : "")}
          />
        </section>
        <aside
          className="network-right"
          aria-label="Explore your professional world"
        >
          <section className="network-side-card">
            <div className="network-side-heading">
              <h2>People sharing on Sera</h2>
              <Link href="/discover">
                Explore <ArrowUpRight size={11} />
              </Link>
            </div>
            {people.length ? (
              people.map((member) => (
                <MemberPreview member={member} key={member.id} />
              ))
            ) : (
              <p className="network-side-empty">
                {peopleError
                  ? "People could not be loaded. Please refresh."
                  : "Meet the professionals behind community stories as more people share their work."}
              </p>
            )}
          </section>
          <section className="network-side-card">
            <div className="network-side-heading">
              <h2>A new door to open</h2>
              <Link href="/jobs">
                All roles <ArrowUpRight size={11} />
              </Link>
            </div>
            {jobs.length ? (
              jobs.map((job) => <OpportunityPreview job={job} key={job.id} />)
            ) : (
              <p className="network-side-empty">
                {jobsError
                  ? "Opportunities could not be loaded. Please refresh."
                  : "New employer-posted opportunities will appear here."}
              </p>
            )}
          </section>
          <NextOnSera compact />
        </aside>
      </div>
    </div>
  );
}
