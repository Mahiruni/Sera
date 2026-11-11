import { MemberAvatar } from "@/components/activity/avatar";
import Link from "next/link";
import {
  ArrowUpRight,
  ArrowRight,
  BriefcaseBusiness,
  FileText,
  Bookmark,
  MessagesSquare,
  UserRound,
  MapPin,
  Check,
  CheckCircle2,
  Plus,
  LockKeyhole,
  ChevronRight,
  BookOpen,
  LayoutDashboard,
} from "lucide-react";
import type { Profile } from "@/components/auth/forms";
import type { WorkspaceData } from "@/lib/workspace/data";
import {
  profileChecklist,
  activityDate,
  stageLabel,
  type WorkspaceMode,
} from "@/lib/workspace/model";
import type { LinkedInBadge } from "@/lib/auth/linkedin-badge";
import { WorkspaceNav } from "./navigation";
import { WorkspaceSubmit } from "./submit";
import { changeJobStatus } from "@/lib/recruitment/actions";
export function WorkspaceEmpty({
  title,
  text,
  href,
  label,
}: {
  title: string;
  text: string;
  href?: string;
  label?: string;
}) {
  return (
    <div className="ws-empty">
      <span className="ws-empty-symbol">
        <BriefcaseBusiness size={23} />
      </span>
      <h3>{title}</h3>
      <p>{text}</p>
      {href && (
        <Link href={href}>
          {label}
          <ArrowRight size={14} />
        </Link>
      )}
    </div>
  );
}
function SectionTitle({
  title,
  href,
  label = "View all",
}: {
  title: string;
  href?: string;
  label?: string;
}) {
  return (
    <div className="ws-section-title">
      <div>
        <h2>{title}</h2>
      </div>
      {href && (
        <Link href={href}>
          {label}
          <ArrowUpRight size={14} />
        </Link>
      )}
    </div>
  );
}
export function WorkspaceOverview({
  profile,
  userId,
  data,
  mode,
  jobsPage = 1,
  appsPage = 1,
  status = "all",
  applicantStage = "all",
  applicantSearch = "",
  statusError = false,
  linkedin = null,
}: {
  profile: Profile;
  userId: string;
  data: WorkspaceData;
  mode: WorkspaceMode;
  jobsPage?: number;
  appsPage?: number;
  status?: string;
  applicantStage?: string;
  applicantSearch?: string;
  statusError?: boolean;
  linkedin?: LinkedInBadge | null;
}) {
  const employer = mode === "employer",
    checklist = profileChecklist(profile, data.assets, mode),
    first = profile.full_name.trim().split(/\s+/)[0] || "there";
  const metrics = [
    {
      label: employer ? "Open postings" : "Applications",
      value: employer ? data.open : data.apps.count,
      note: employer
        ? "Currently accepting introductions"
        : "Introductions you have submitted",
      icon: employer ? BriefcaseBusiness : FileText,
      href: employer ? "#postings" : "/applications",
    },
    {
      label: "Interview & offer",
      value: data.interviews,
      note: employer
        ? "Applications at these stages"
        : "Your current application stages",
      icon: CheckCircle2,
      href: employer ? "#incoming" : "/applications",
    },
    {
      label: employer ? "Total applicants" : "Saved roles",
      value: employer ? data.apps.count : data.saved.count,
      note: employer
        ? "Across your own job postings"
        : "Kept in your private collection",
      icon: employer ? UserRound : Bookmark,
      href: employer ? "#incoming" : "/saved",
    },
    {
      label: "Messages",
      value: data.messages.count,
      note: "Messages in your application conversations",
      icon: MessagesSquare,
      href: "#conversations",
    },
  ];
  const pageHref = (section: "jobs" | "apps", page: number) =>
    `/employer?jobsPage=${section === "jobs" ? page : jobsPage}&appsPage=${section === "apps" ? page : appsPage}&status=${status}&stage=${encodeURIComponent(applicantStage)}&candidate=${encodeURIComponent(applicantSearch)}#${section === "jobs" ? "postings" : "incoming"}`;
  return (
    <div className={`container page workspace-page${employer ? " workspace-employer" : ""}`}>
      <div className="ws-context">
        <span>
          <LayoutDashboard size={14} />
          SERA / {employer ? "EMPLOYER WORKSPACE" : "CAREER WORKSPACE"}
        </span>
        <Link href={employer ? "/dashboard?view=candidate" : "/employer"}>
          {employer ? "Your career space" : "Employer workspace"}
          <ArrowUpRight size={13} />
        </Link>
      </div>
      <header className="ws-welcome">
        <div className="ws-identity">
          <Link
            href="/settings"
            className="ws-avatar"
            aria-label="Open your profile"
          >
            <MemberAvatar
              name={profile.full_name}
              photo={
                data.assets?.avatar_path
                  ? `/api/account/files?kind=avatar&v=${data.assets.version}`
                  : null
              }
              size={58}
            />
          </Link>
          <div>
            <h1>
              {employer ? "Let’s build your team." : `Welcome back, ${first}.`}
            </h1>
            <p>
              {employer
                ? "Your postings, introductions and conversations. All together."
                : "A clear view of your progress, and a useful next step."}
            </p>
          </div>
        </div>
        <Link
          className="button ws-primary"
          href={employer ? "/employer/post" : "/jobs"}
        >
          {employer ? <Plus size={17} /> : <BriefcaseBusiness size={17} />}
          {employer ? "Post a job" : "Find opportunities"}
          <ArrowUpRight size={15} />
        </Link>
      </header>
      <WorkspaceNav employer={employer} />
      <div className="ws-metrics" aria-label="Your live activity">
        {metrics.map(({ label, value, note, icon: Icon, href }) => (
          <Link className="ws-metric" href={href} key={label}>
            <div>
              <span>{label}</span>
              <Icon size={18} />
            </div>
            <strong>
              {value === null ? "—" : (value?.toLocaleString("en") ?? "—")}
            </strong>
            <p>{value === null ? "Temporarily unavailable" : note}</p>
          </Link>
        ))}
      </div>
      {statusError && (
        <p className="auth-feedback" role="alert">
          The posting could not be updated. Please try again.
        </p>
      )}
      <div className="ws-layout">
        <div className="ws-main">
          {employer && (
            <section className="ws-surface" id="postings">
              <SectionTitle
                title="Job postings"
              />
              <form className="ws-filters" action="/employer">
                <label>
                  Status
                  <select name="status" defaultValue={status}>
                    <option value="all">All postings</option>
                    <option value="open">Open</option>
                    <option value="closed">Closed</option>
                  </select>
                </label>
                <button className="button secondary">Apply filter</button>
              </form>
              {data.jobs.error ? (
                <WorkspaceEmpty
                  title="Postings could not be loaded"
                  text="Refresh this page to try again."
                />
              ) : data.jobs.rows.length ? (
                <div className="ws-records">
                  {data.jobs.rows.map((j) => (
                    <article className="ws-job-row" key={j.id}>
                      <div>
                        <span className={`ws-status ${j.status}`}>
                          {j.status === "open" ? "Open" : "Closed"}
                        </span>
                        <h3>
                          <Link href={"/jobs/" + j.id}>{j.title}</Link>
                        </h3>
                        <p>
                          {j.company} · {j.location}
                        </p>
                        <time dateTime={j.created_at}>
                          Posted {activityDate(j.created_at)}
                        </time>
                      </div>
                      <form action={changeJobStatus}>
                        <input type="hidden" name="jobsPage" value={jobsPage} />
                        <input type="hidden" name="appsPage" value={appsPage} />
                        <input type="hidden" name="filter" value={status} />
                        <input type="hidden" name="job_id" value={j.id} />
                        <input
                          type="hidden"
                          name="status"
                          value={j.status === "open" ? "closed" : "open"}
                        />
                        <WorkspaceSubmit>
                          {j.status === "open"
                            ? "Close posting"
                            : "Reopen posting"}
                        </WorkspaceSubmit>
                      </form>
                    </article>
                  ))}
                </div>
              ) : (
                <WorkspaceEmpty
                  title="Your first opportunity starts here"
                  text="Publish a real role with clear responsibilities and requirements."
                  href="/employer/post"
                  label="Create a posting"
                />
              )}
              <div className="ws-pagination">
                {jobsPage > 1 && (
                  <Link href={pageHref("jobs", jobsPage - 1)}>← Previous</Link>
                )}
                {(data.jobs.count || 0) > jobsPage * 8 && (
                  <Link href={pageHref("jobs", jobsPage + 1)}>Next →</Link>
                )}
              </div>
            </section>
          )}
          <section className="ws-surface" id="incoming">
            <SectionTitle
              title={employer ? "Incoming applications" : "Recent applications"}
              href={employer ? undefined : "/applications"}
            />
            {employer && (
              <form className="ws-filters" action="/employer" method="get">
                <input type="hidden" name="status" value={status} />
                <label>
                  Recruitment stage
                  <select name="stage" defaultValue={applicantStage}>
                    <option value="all">All stages</option>
                    <option value="applied">Submitted</option>
                    <option value="screened">In review</option>
                    <option value="interview">Interview</option>
                    <option value="offer">Offer</option>
                    <option value="rejected">Not selected</option>
                    <option value="withdrawn">Withdrawn</option>
                  </select>
                </label>
                <label>
                  Candidate or skill
                  <input name="candidate" maxLength={80} defaultValue={applicantSearch} placeholder="Name or skill" />
                </label>
                <button className="button secondary" type="submit">Filter applicants</button>
              </form>
            )}
            {data.apps.error ? (
              <WorkspaceEmpty
                title="Applications could not be loaded"
                text="Please refresh. We have not replaced your activity with sample data."
              />
            ) : data.apps.rows.length ? (
              <div className="ws-records">
                {data.apps.rows.map((a) => (
                  <Link
                    className="ws-application-row"
                    href={"/applications/" + a.id}
                    key={a.id}
                  >
                    <span className="ws-company-initial" aria-hidden="true">
                      {employer ? (
                        <MemberAvatar
                          name={a.candidate_name}
                          photo={data.candidatePhotos[a.candidate_id]}
                        />
                      ) : (
                        (a.jobs?.company || "S").slice(0, 1)
                      )}
                    </span>
                    <div>
                      <h3>
                        {employer
                          ? a.candidate_name
                          : a.jobs?.title || "Posting unavailable"}
                      </h3>
                      <p>
                        {employer
                          ? a.jobs?.title
                          : a.jobs?.company || "Employer"}
                        <span> · Applied {activityDate(a.created_at)}</span>
                      </p>
                    </div>
                    <span className={`ws-status ${a.stage}`}>
                      {stageLabel(a.stage)}
                    </span>
                    <ChevronRight size={15} />
                  </Link>
                ))}
              </div>
            ) : (
              <WorkspaceEmpty
                title={
                  employer
                    ? "Good introductions take a first posting."
                    : "Your next introduction is waiting."
                }
                text={
                  employer
                    ? "Applications to your roles will appear here. You stay in control of every stage."
                    : "Explore live roles and send an introduction when you find an opportunity that fits."
                }
                href={employer ? "/employer/post" : "/jobs"}
                label={employer ? "Post a job" : "Explore opportunities"}
              />
            )}{" "}
            {employer && (
              <div className="ws-pagination">
                {appsPage > 1 && (
                  <Link href={pageHref("apps", appsPage - 1)}>← Previous</Link>
                )}
                {(data.apps.count || 0) > appsPage * 8 && (
                  <Link href={pageHref("apps", appsPage + 1)}>Next →</Link>
                )}
              </div>
            )}
          </section>
          <section className="ws-surface" id="conversations">
            <SectionTitle
              title="Latest messages"
            />
            {data.messages.error ? (
              <WorkspaceEmpty
                title="Messages could not be loaded"
                text="Refresh this page to try again."
              />
            ) : data.messages.rows.length ? (
              <div className="ws-records">
                {data.messages.rows.map((m) => (
                  <Link
                    key={m.id}
                    className="ws-message-row"
                    href={"/applications/" + m.application_id}
                  >
                    <span className="ws-message-symbol">
                      <MessagesSquare size={18} />
                    </span>
                    <div>
                      <div className="ws-message-heading">
                        <h3>
                          {m.applications?.jobs?.title ||
                            "Application conversation"}
                        </h3>
                        <time dateTime={m.created_at}>
                          {activityDate(m.created_at)}
                        </time>
                      </div>
                      <p>
                        <b>
                          {m.sender_id === userId
                            ? "You: "
                            : employer
                              ? "Candidate: "
                              : "Employer: "}
                        </b>
                        {m.body}
                      </p>
                    </div>
                    <ArrowUpRight size={15} />
                  </Link>
                ))}
              </div>
            ) : (
              <WorkspaceEmpty
                title="A quiet inbox. A clear starting point."
                text="Messages exchanged within your applications will appear here."
              />
            )}
          </section>
          {!employer && (
            <section className="ws-surface">
              <SectionTitle title="Saved for your next move" href="/saved" />
              {data.saved.error ? (
                <WorkspaceEmpty
                  title="Saved roles could not be loaded"
                  text="Refresh this page to try again."
                />
              ) : data.saved.rows.length ? (
                <div className="ws-saved-grid">
                  {data.saved.rows.map((s) => (
                    <Link
                      href={s.jobs ? "/jobs/" + s.job_id : "/saved"}
                      className="ws-saved-card"
                      key={s.job_id}
                    >
                      <Bookmark size={17} />
                      <span>{s.jobs?.company || "Saved posting"}</span>
                      <h3>
                        {s.jobs?.title || "This posting is no longer public"}
                      </h3>
                      <p>
                        {s.jobs ? (
                          <>
                            <MapPin size={12} />
                            {s.jobs.location} · {s.jobs.status}
                          </>
                        ) : (
                          "Review or remove it in Saved jobs."
                        )}
                      </p>
                      <ArrowUpRight size={15} />
                    </Link>
                  ))}
                </div>
              ) : (
                <WorkspaceEmpty
                  title="Keep a possibility close."
                  text="Save a role from its detail page and return to it on any device."
                  href="/jobs"
                  label="Browse live roles"
                />
              )}
            </section>
          )}
        </div>
        <aside className="ws-sidebar">
          <section className="ws-surface ws-profile-card">
            <SectionTitle
              title="Your profile, ready."
              href="/settings"
              label="Manage"
            />
            {data.assetsError ? (
              <p className="ws-muted">
                File status is unavailable. Your setup progress cannot be
                calculated right now.
              </p>
            ) : (
              <>
                <div className="ws-completion">
                  <strong>
                    {checklist.percent}
                    <span>%</span>
                  </strong>
                  <p>
                    {checklist.complete} of {checklist.total} setup details
                    added
                  </p>
                </div>
                <progress
                  value={checklist.percent}
                  max={100}
                  aria-label="Profile setup completed"
                />
                <p className="ws-muted">
                  Add details at your own pace. Your profile stays private.
                </p>
                <ul className="ws-checklist">
                  {checklist.entries.map(([label, done]) => (
                    <li key={label} className={done ? "done" : ""}>
                      <span aria-hidden="true">
                        {done ? <Check size={11} /> : null}
                      </span>
                      {label}
                    </li>
                  ))}
                </ul>
              </>
            )}
            <Link className="ws-profile-action" href="/settings">
              {data.assetsError
                ? "Manage your profile"
                : checklist.percent === 100
                  ? "Review your profile"
                  : "Add your next detail"}
              <ArrowRight size={15} />
            </Link>
          </section>
          <section className="ws-cv-card">
            <span>
              <FileText size={20} />
              {data.assetsError
                ? "CV status unavailable"
                : data.assets?.cv_path
                  ? "Your CV is in place."
                  : "A stronger first introduction."}
            </span>
            <p>
              {data.assetsError
                ? "Open your account to retry."
                : data.assets?.cv_path
                  ? "Manage your original and Sera-branded copies in your private profile."
                  : "Upload a PDF and keep an original plus a separate Sera-branded copy."}
            </p>
            <Link href="/settings#cv">
              {data.assets?.cv_path ? "Manage CV" : "Upload your CV"}
              <ArrowUpRight size={14} />
            </Link>
          </section>
          <section className="ws-surface ws-quick-links">
            <h2>A useful next step</h2>
            <Link href="/settings">
              <UserRound size={16} />
              Update your profile
              <ChevronRight size={14} />
            </Link>
            <Link href="/settings/linkedin">
              <UserRound size={16} />
              LinkedIn import & connection
              {linkedin && (
                <span
                  role="status"
                  title={
                    linkedin.importedAt
                      ? "Last import: " +
                        new Date(linkedin.importedAt).toLocaleDateString(
                          "en-GB",
                        )
                      : undefined
                  }
                  style={{
                    marginLeft: "auto",
                    fontSize: 12,
                    fontWeight: 600,
                    padding: "2px 8px",
                    borderRadius: 999,
                    background: linkedin.connected ? "#0A66C2" : "transparent",
                    color: linkedin.connected ? "#fff" : "inherit",
                    border: linkedin.connected ? 0 : "1px solid currentColor",
                  }}
                >
                  {linkedin.connected ? "Connected" : "Not connected"}
                </span>
              )}
              <ChevronRight size={14} />
            </Link>
            <Link href="/guides">
              <BookOpen size={16} />
              Career & education guides
              <ChevronRight size={14} />
            </Link>
            <Link href={employer ? "/dashboard?view=candidate" : "/employer"}>
              <BriefcaseBusiness size={16} />
              {employer ? "Explore your career space" : "Represent an employer"}
              <ChevronRight size={14} />
            </Link>
          </section>
          <div className="ws-privacy">
            <LockKeyhole size={17} />
            <p>
              {employer
                ? "Your workspace shows only your postings and the introductions shared with them. Employer verification is not granted by using these tools."
                : "Your profile and files stay private. An application shares only the details you explicitly acknowledge."}
            </p>
          </div>
        </aside>
      </div>
    </div>
  );
}
