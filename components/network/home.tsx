import { MemberAvatar } from "@/components/activity/avatar";
import Link from "next/link";

import {
  ArrowUpRight,
  Plus,
} from "lucide-react";
import { FeedComposer } from "@/components/social/FeedComposer";
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
  personalized=false,
  identityVerified=false,
  feedContent,peopleContent,rightContent,
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
  personalized?:boolean;
  identityVerified?:boolean;
  feedContent?:React.ReactNode;peopleContent?:React.ReactNode;rightContent?:React.ReactNode;
}) {
  const essentials = profileEssentials(profile),
    topics = skillTopics(profile?.skills);
  const filters = new URLSearchParams();
  if (kind) filters.set("kind", kind);
  if (mine) filters.set("view", "mine");
  else if(personalized&&!kind)filters.set("view","for-you");
  const name = profile?.full_name?.trim() || "Your professional profile";
  return (
    <div className="sera-network network-app social-page sera-editorial-feed">
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
          <nav className="network-feed-tabs sera-web-feed-tabs" aria-label="Feed view">
            <Link href="/feed" aria-current={!mine&&!personalized&&!kind?"page":undefined}>Latest</Link>
            <Link href="/feed?view=for-you" aria-current={personalized ? "page" : undefined}>For you</Link>
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
          <FeedComposer name={name} avatarUrl={avatarUrl} userId={userId} headline={profile?.headline} identityVerified={identityVerified}/>
          {peopleContent ?? <NetworkPeopleStrip people={people}/>}
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
          {feedContent ?? <><PostList posts={posts} userId={userId}/><SocialPagination page={page} hasMore={hasMore} href={"/feed"+(filters.size?"?"+filters.toString():"")}/></>}
        </section>
        {rightContent ?? <NetworkRightRail people={people} jobs={jobs} peopleError={peopleError} jobsError={jobsError}/>}

      </div>
    </div>
  );
}

export function NetworkPeopleStrip({people}:{people:MemberCard[]}){return (
<nav className="sera-premium-people" aria-label="Professionals to discover">
            <Link href="/discover?tab=people" className="sera-premium-person sera-premium-add">
              <span className="sera-premium-person-avatar"><Plus size={27} /></span>
              <small>Find people</small>
            </Link>
            {people.slice(0, 6).map((person) => (
              <Link href={"/members/" + person.id} className="sera-premium-person" key={person.id}>
                <span className="sera-premium-person-avatar"><MemberAvatar name={person.full_name} photo={person.photo} size={52} /></span>
                <small>{person.full_name.split(" ")[0] || "Member"}</small>
              </Link>
            ))}
          </nav>
);}
export function NetworkRightRail({people,jobs,peopleError=false,jobsError=false}:{people:MemberCard[];jobs:OpportunityCard[];peopleError?:boolean;jobsError?:boolean}){return (
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
);}
