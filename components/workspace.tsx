"use client";
import { useState, type FormEvent, type ReactNode } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import {
  ArrowUpRight,
  ArrowRight,
  Check,
  BriefcaseBusiness,
  UserRound,
  MessagesSquare,
  Plus,
  FileText,
  ShieldCheck,
  RotateCcw,
  LogOut,
} from "lucide-react";
import { useSera } from "./store";
import { Heading, Empty, Notice, Loading, Brand } from "./ui";
import {
  initialState,
  categories,
  locations,
  stages,
  keywordMatch,
  pay,
  type Role,
  type Stage,
  type Profile,
} from "@/lib/model";
import type { Key } from "@/lib/i18n";
export function Gate({ children, role }: { children: ReactNode; role?: Role }) {
  const { ready, state, t } = useSera();
  if (!ready) return <Loading />;
  if (!state.role || (role && state.role !== role))
    return (
      <div className="container page">
        <Heading
          eyebrow="SERA / DEMO WORKSPACE"
          title={t("account")}
          description={t("accountSub")}
        />
        <Notice>{t("localOnly")}</Notice>
        <div className="panel gate">
          <UserRound size={32} />
          <h2>{t(role === "employer" ? "tryEmployer" : "tryCandidate")}</h2>
          <p>{t("notLive")}</p>
          <Link className="button" href={`/account?role=${role || "seeker"}`}>
            {t("continue")}
            <ArrowRight size={17} />
          </Link>
        </div>
      </div>
    );
  return children;
}
export function WorkspaceNav() {
  const { state, t } = useSera();
  const links: [string, Key][] =
    state.role === "employer"
      ? [
          ["/employer", "workspace"],
          ["/employer/post", "post"],
          ["/employer/pipeline", "pipeline"],
          ["/messages", "messages"],
        ]
      : [
          ["/workspace", "workspace"],
          ["/profile", "profile"],
          ["/matches", "matches"],
          ["/applications", "applications"],
          ["/messages", "messages"],
        ];
  return (
    <nav className="workspace-nav" aria-label="Workspace navigation">
      {links.map(([href, k]) => (
        <Link href={href} key={href}>
          {t(k)}
          <ArrowUpRight size={13} />
        </Link>
      ))}
    </nav>
  );
}
export function Account() {
  const { state, setState, t, lang } = useSera();
  const search = useSearchParams();
  const router = useRouter();
  const [role, setRole] = useState<Role>(
    search.get("role") === "employer" ? "employer" : "seeker",
  );
  const [confirm, setConfirm] = useState(false);
  return (
    <div className="container page account-page">
      <div className="account-intro">
        <Brand />
        <p className="eyebrow">{t("demo")}</p>
        <h1>{t("account")}</h1>
        <p className="subtext">{t("accountSub")}</p>
        <Notice>{t("notLive")}</Notice>
        <p className="body-copy">{t("localOnly")}</p>
        <div className="privacy-points">
          <ShieldCheck size={19} />
          <span>{t("noAI")}</span>
        </div>
      </div>
      <div className="panel account-panel">
        <div className="role-tabs">
          <button
            className={role === "seeker" ? "active" : ""}
            aria-pressed={role === "seeker"}
            onClick={() => setRole("seeker")}
          >
            <UserRound size={18} />
            {t("candidate")}
          </button>
          <button
            className={role === "employer" ? "active" : ""}
            aria-pressed={role === "employer"}
            onClick={() => setRole("employer")}
          >
            <BriefcaseBusiness size={18} />
            {t("employerRole")}
          </button>
        </div>
        <form
          onSubmit={(e) => {
            e.preventDefault();
            const f = new FormData(e.currentTarget);
            const name = String(f.get("displayName")).trim();
            if (!name) return;
            setState((s) => ({
              ...s,
              role,
              ...(role === "seeker" ? { name } : { company: name }),
            }));
            router.push(role === "seeker" ? "/workspace" : "/employer");
          }}
        >
          <label>
            {t(role === "seeker" ? "name" : "company")}
            <input
              key={role}
              name="displayName"
              required
              minLength={2}
              maxLength={100}
              defaultValue={role === "seeker" ? state.name : state.company}
            />
          </label>
          <p className="small-note">{t("demoNote")}</p>
          <button className="button full">
            {t("continue")}
            <ArrowRight size={17} />
          </button>
        </form>
        {state.role && (
          <button
            className="text-button"
            onClick={() => {
              setState((s) => ({ ...s, role: null }));
              router.push("/");
            }}
          >
            <LogOut size={16} />
            {t("exit")}
          </button>
        )}
        <div className="divider" />
        {!confirm ? (
          <button
            className="text-button muted"
            onClick={() => setConfirm(true)}
          >
            <RotateCcw size={14} />
            {t("reset")}
          </button>
        ) : (
          <div className="reset-confirm">
            <p>
              {lang === "en"
                ? "Delete all demo profiles, roles, applications, messages, and bookmarks in this browser?"
                : "በዚህ አሳሽ ያለውን ሁሉንም የሙከራ ውሂብ ይሰርዙ?"}
            </p>
            <button
              className="button danger"
              onClick={() => {
                setState(structuredClone(initialState));
                setConfirm(false);
              }}
            >
              {lang === "en" ? "Delete local demo data" : "የአካባቢ ውሂብ ሰርዝ"}
            </button>
            <button className="text-button" onClick={() => setConfirm(false)}>
              {t("close")}
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
export function Dashboard({ employer = false }: { employer?: boolean }) {
  const { state, setState, t, lang } = useSera();
  employer = employer || state.role === "employer";
  const myJobs = state.jobs.filter((j) => j.empId === 2);
  const apps = employer
    ? state.apps.filter((a) => myJobs.some((j) => j.id === a.jobId))
    : state.apps;
  const completeness = Math.round(
    (Object.values(state.profile).filter(Boolean).length / 6) * 100,
  );
  const stats = employer
    ? [
        [myJobs.filter((j) => j.status === "open").length, t("active")],
        [apps.length, t("applications")],
        [
          state.messages.filter((m) => apps.some((a) => a.id === m.appId))
            .length,
          t("messages"),
        ],
      ]
    : [
        [apps.length, t("applications")],
        [state.saved.length, t("saved")],
        [completeness + "%", t("completion")],
      ];
  return (
    <Gate role={employer ? "employer" : "seeker"}>
      <div className="container page">
        <Heading
          eyebrow={t("welcome")}
          title={employer ? state.company : state.name}
          description={t("overview")}
        >
          {employer && (
            <Link className="button" href="/employer/post">
              <Plus size={16} />
              {t("post")}
            </Link>
          )}
        </Heading>
        <WorkspaceNav />
        <Notice>{t("localOnly")}</Notice>
        <div className="stats-grid">
          {stats.map(([v, label]) => (
            <div className="stat-card" key={label}>
              <span>{label}</span>
              <strong>{v}</strong>
              <span className="stat-line" />
            </div>
          ))}
        </div>
        {employer ? (
          <>
            <div className="section-head compact">
              <h2>{t("active")}</h2>
              <Link className="inline-link" href="/pricing">
                {t("promote")}
                <ArrowUpRight size={15} />
              </Link>
            </div>
            <div className="panel list-panel">
              {myJobs.map((j) => (
                <div className="list-row" key={j.id}>
                  <div>
                    <Link href={`/jobs/${j.id}`} className="row-title">
                      {j.title}
                    </Link>
                    <p>
                      {j.loc} · {pay(j)}
                    </p>
                  </div>
                  <div className="row-actions">
                    <span className="sample-tag">
                      {j.status === "open" ? t("sample") : t("closed")}
                    </span>
                    <button
                      className="text-button"
                      onClick={() =>
                        setState((s) => ({
                          ...s,
                          jobs: s.jobs.map((job) =>
                            job.id === j.id
                              ? {
                                  ...job,
                                  status:
                                    job.status === "open" ? "closed" : "open",
                                }
                              : job,
                          ),
                        }))
                      }
                    >
                      {t(j.status === "open" ? "closeRole" : "reopen")}
                    </button>
                  </div>
                </div>
              ))}
            </div>
          </>
        ) : (
          <div className="dashboard-cards">
            <Link className="panel action-card" href="/profile">
              <span className="category-icon">
                <UserRound size={22} />
              </span>
              <h2>{t("profile")}</h2>
              <p>
                {lang === "en"
                  ? "Your skills deserve a clear introduction."
                  : "ክህሎቶችዎ ግልጽ መግለጫ ይገባቸዋል።"}
              </p>
              <ArrowUpRight size={22} />
            </Link>
            <Link className="panel action-card dark" href="/matches">
              <span className="category-icon">
                <BriefcaseBusiness size={22} />
              </span>
              <h2>{t("matches")}</h2>
              <p>{t("matchNote")}</p>
              <ArrowUpRight size={22} />
            </Link>
          </div>
        )}
      </div>
    </Gate>
  );
}
export function ProfilePage() {
  const { state, setState, t, notify } = useSera();
  const save = (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    const f = new FormData(e.currentTarget);
    const p = Object.fromEntries(
      Object.keys(state.profile).map((k) => [k, String(f.get(k) || "").trim()]),
    ) as Profile;
    setState((s) => ({ ...s, profile: p }));
    notify(t("success"));
  };
  return (
    <Gate>
      <div className="container page narrow">
        <Heading
          eyebrow="SERA / PROFILE"
          title={t("profile")}
          description={t("localOnly")}
        />
        <WorkspaceNav />
        {state.role === "employer" ? (
          <div className="panel">
            <h2>{state.company}</h2>
            <p className="subtext">{t("demoNote")}</p>
            <Link href="/account" className="button secondary">
              {t("switch")}
            </Link>
          </div>
        ) : (
          <form className="panel form-panel" onSubmit={save}>
            <div className="form-grid">
              {(
                ["headline", "loc", "skills", "langs", "exp", "cv"] as const
              ).map((k) => (
                <label
                  key={k}
                  className={
                    ["skills", "exp", "cv"].includes(k) ? "span-2" : ""
                  }
                >
                  {t(
                    (
                      {
                        headline: "headline",
                        loc: "location",
                        skills: "skills",
                        langs: "languages",
                        exp: "experience",
                        cv: "cv",
                      } as const
                    )[k],
                  )}
                  {k === "loc" ? (
                    <select name={k} defaultValue={state.profile[k]}>
                      {locations.map((l) => (
                        <option key={l}>{l}</option>
                      ))}
                    </select>
                  ) : ["exp", "cv"].includes(k) ? (
                    <textarea
                      name={k}
                      defaultValue={state.profile[k]}
                      maxLength={10000}
                    />
                  ) : (
                    <input
                      name={k}
                      defaultValue={state.profile[k]}
                      maxLength={500}
                    />
                  )}
                </label>
              ))}
            </div>
            <button className="button">
              <Check size={16} />
              {t("save")}
            </button>
          </form>
        )}
      </div>
    </Gate>
  );
}
const stageKey = (s: Stage): Key => (s === "applied" ? "appliedStage" : s);
export function Applications({ pipeline = false }: { pipeline?: boolean }) {
  const { state, setState, t, lang } = useSera();
  const apps = pipeline
    ? state.apps.filter(
        (a) => state.jobs.find((j) => j.id === a.jobId)?.empId === 2,
      )
    : state.apps;
  return (
    <Gate role={pipeline ? "employer" : "seeker"}>
      <div className="container page">
        <Heading
          eyebrow={`SERA / ${pipeline ? "HIRING" : "APPLICATIONS"}`}
          title={t(pipeline ? "pipeline" : "applications")}
          description={t("localOnly")}
        />
        <WorkspaceNav />
        {apps.length ? (
          <div className="application-list">
            {apps.map((a) => {
              const job = state.jobs.find((j) => j.id === a.jobId);
              if (!job) return null;
              const match = keywordMatch(state.profile, job);
              return (
                <article className="panel application-card" key={a.id}>
                  <div className="application-heading">
                    <div>
                      <p className="eyebrow">
                        {pipeline ? state.name : job.org}
                      </p>
                      <h2>
                        <Link href={`/jobs/${job.id}`}>{job.title}</Link>
                      </h2>
                      <p className="small-note">
                        {job.loc} ·{" "}
                        {new Date(a.date).toLocaleDateString(
                          lang === "am" ? "am-ET" : "en-GB",
                        )}
                      </p>
                    </div>
                    <span className={`stage-badge stage-${a.stage}`}>
                      {t(stageKey(a.stage))}
                    </span>
                  </div>
                  {pipeline && (
                    <>
                      <label className="stage-select">
                        {t("stage")}
                        <select
                          value={a.stage}
                          onChange={(e) => {
                            const stage = e.target.value as Stage;
                            setState((s) => ({
                              ...s,
                              apps: s.apps.map((x) =>
                                x.id === a.id ? { ...x, stage } : x,
                              ),
                            }));
                          }}
                        >
                          {stages.map((st) => (
                            <option key={st} value={st}>
                              {t(stageKey(st))}
                            </option>
                          ))}
                        </select>
                      </label>
                      <p className="small-note">
                        {match.percent}% {t("matchNote")}
                      </p>
                      <p className="small-note">
                        {match.hits.join(", ") || "—"}
                      </p>
                    </>
                  )}
                  <details>
                    <summary>{t("cover")}</summary>
                    <p className="body-copy preserve-lines">{a.cover || "—"}</p>
                  </details>
                  {!pipeline && (
                    <details>
                      <summary>{t("prep")}</summary>
                      <p className="body-copy">{t("prepText")}</p>
                    </details>
                  )}
                  <Link
                    className="inline-link"
                    href={`/messages?thread=${a.id}`}
                  >
                    <MessagesSquare size={15} />
                    {t("messages")}
                    <ArrowUpRight size={14} />
                  </Link>
                </article>
              );
            })}
          </div>
        ) : (
          <Empty
            text={
              lang === "en"
                ? "Save a demo application as a candidate, then switch to the employer workspace to explore the hiring journey."
                : "እንደ ሥራ ፈላጊ የሙከራ ማመልከቻ ያስቀምጡ፤ ከዚያ ወደ ቀጣሪ ይቀይሩ።"
            }
            action={
              <Link className="button" href="/jobs">
                {t("explore")}
                <ArrowRight size={15} />
              </Link>
            }
          />
        )}
      </div>
    </Gate>
  );
}
export function PostJob() {
  const { state, setState, t, lang, notify } = useSera();
  const router = useRouter();
  const [desc, setDesc] = useState("");
  const [req, setReq] = useState("");
  const [title, setTitle] = useState("");
  const [error, setError] = useState("");
  return (
    <Gate role="employer">
      <div className="container page narrow">
        <Heading
          eyebrow="SERA / EMPLOYERS"
          title={t("newJob")}
          description={t("localOnly")}
        />
        <WorkspaceNav />
        <form
          className="panel form-panel"
          onSubmit={(e) => {
            e.preventDefault();
            const f = new FormData(e.currentTarget);
            const min = Number(f.get("min")),
              max = Number(f.get("max"));
            if (max < min) {
              setError(
                lang === "en"
                  ? "Maximum salary must be at least the minimum."
                  : "ከፍተኛ ደመወዝ ከዝቅተኛው ያነሰ መሆን አይችልም።",
              );
              return;
            }
            if (!title.trim() || !desc.trim() || !req.trim()) return;
            setState((s) => ({
              ...s,
              jobs: [
                ...s.jobs,
                {
                  id: Date.now(),
                  empId: 2,
                  title: title.trim(),
                  org: s.company,
                  loc: String(f.get("loc")),
                  cat: String(f.get("cat")),
                  type: String(f.get("type")),
                  payMin: min,
                  payMax: max,
                  featured: false,
                  status: "open",
                  posted: new Date().toISOString(),
                  desc: desc.trim(),
                  req: req.trim(),
                },
              ],
            }));
            notify(t("applied"));
            router.push("/employer");
          }}
        >
          <div className="form-grid">
            <label>
              {t("title")}
              <input
                required
                value={title}
                maxLength={120}
                onChange={(e) => setTitle(e.target.value)}
              />
            </label>
            <label>
              {t("category")}
              <select name="cat">
                {categories.map((c) => (
                  <option key={c.id} value={c.id}>
                    {c[lang]}
                  </option>
                ))}
              </select>
            </label>
            <label>
              {t("location")}
              <select name="loc">
                {locations.map((l) => (
                  <option key={l}>{l}</option>
                ))}
              </select>
            </label>
            <label>
              {t("type")}
              <select name="type">
                {["Full-time", "Contract", "Part-time", "Internship"].map(
                  (l) => (
                    <option key={l}>{l}</option>
                  ),
                )}
              </select>
            </label>
            <label>
              {t("min")}
              <input
                name="min"
                type="number"
                min="0"
                max="100000000"
                step="1"
                required
                defaultValue="18000"
              />
            </label>
            <label>
              {t("max")}
              <input
                name="max"
                type="number"
                min="0"
                max="100000000"
                step="1"
                required
                defaultValue="25000"
              />
            </label>
          </div>
          <button
            type="button"
            className="button secondary"
            disabled={!title.trim()}
            onClick={() => {
              setDesc(
                lang === "en"
                  ? `Join ${state.company} as a ${title}.\n\n[Describe the team, responsibilities, and working arrangements.]`
                  : `${state.company}ን እንደ ${title} ይቀላቀሉ።\n\n[ኃላፊነቶችን እና የሥራ ሁኔታዎችን ይግለጹ።]`,
              );
              setReq(
                lang === "en"
                  ? "[Relevant experience] · [Required skills] · [Language requirements]"
                  : "[አግባብ ያለው ልምድ] · [ክህሎቶች] · [ቋንቋዎች]",
              );
            }}
          >
            <FileText size={15} />
            {t("template")}
          </button>
          <p className="small-note">{t("noAI")}</p>
          <label>
            {t("description")}
            <textarea
              required
              value={desc}
              maxLength={10000}
              onChange={(e) => setDesc(e.target.value)}
            />
          </label>
          <label>
            {t("requirements")}
            <textarea
              required
              value={req}
              maxLength={5000}
              onChange={(e) => setReq(e.target.value)}
            />
          </label>
          {error && (
            <p role="alert" className="form-error">
              {error}
            </p>
          )}
          <button className="button">
            <Plus size={17} />
            {t("publish")}
          </button>
        </form>
      </div>
    </Gate>
  );
}
export function Messages() {
  const { state, setState, t, lang, notify } = useSera();
  const params = useSearchParams();
  const [thread, setThread] = useState(Number(params.get("thread")) || 0);
  const apps =
    state.role === "employer"
      ? state.apps.filter(
          (a) => state.jobs.find((j) => j.id === a.jobId)?.empId === 2,
        )
      : state.apps;
  const active = apps.find((a) => a.id === thread) || apps[0];
  const msgs = state.messages.filter((m) => m.appId === active?.id);
  return (
    <Gate>
      <div className="container page">
        <Heading
          eyebrow="SERA / INBOX"
          title={t("messages")}
          description={t("localOnly")}
        />
        <WorkspaceNav />
        {active ? (
          <div className="messaging panel">
            <nav className="threads" aria-label="Demo conversations">
              {apps.map((a) => {
                const job = state.jobs.find((j) => j.id === a.jobId);
                return (
                  <button
                    key={a.id}
                    className={active.id === a.id ? "active" : ""}
                    onClick={() => setThread(a.id)}
                  >
                    <strong>
                      {state.role === "seeker" ? job?.org : state.name}
                    </strong>
                    <span>{job?.title}</span>
                  </button>
                );
              })}
            </nav>
            <div className="conversation">
              <div className="conversation-heading">
                <span className="status-dot" />
                {t("demo")}
              </div>
              <div className="chat-log" role="log" aria-live="polite">
                {msgs.length ? (
                  msgs.map((m) => (
                    <div
                      className={`bubble ${m.from === state.role ? "mine" : ""}`}
                      key={m.id}
                    >
                      <small>
                        {t(m.from === "seeker" ? "candidate" : "employerRole")}
                      </small>
                      <p>{m.text}</p>
                      <time dateTime={m.ts}>
                        {new Date(m.ts).toLocaleTimeString(
                          lang === "am" ? "am-ET" : "en-GB",
                          { hour: "2-digit", minute: "2-digit" },
                        )}
                      </time>
                    </div>
                  ))
                ) : (
                  <Empty
                    text={
                      lang === "en"
                        ? "Messages stay in this browser. Switch demo roles to explore both sides."
                        : "መልዕክቶች በዚህ አሳሽ ይቀመጣሉ። ሁለቱንም ወገን ለማየት ሚና ይቀይሩ።"
                    }
                  />
                )}
              </div>
              <form
                className="chat-form"
                onSubmit={(e) => {
                  e.preventDefault();
                  const f = e.currentTarget;
                  const text = String(
                    new FormData(f).get("message") || "",
                  ).trim();
                  if (!text || !state.role) return;
                  setState((s) => ({
                    ...s,
                    messages: [
                      ...s.messages,
                      {
                        id: Date.now(),
                        appId: active.id,
                        from: s.role!,
                        text,
                        ts: new Date().toISOString(),
                      },
                    ],
                  }));
                  f.reset();
                  notify(t("applied"));
                }}
              >
                <label className="sr-only" htmlFor="message">
                  {t("message")}
                </label>
                <input
                  id="message"
                  name="message"
                  required
                  maxLength={2000}
                  placeholder={t("message")}
                />
                <button className="button" aria-label={t("send")}>
                  <ArrowRight size={18} />
                  <span>{t("send")}</span>
                </button>
              </form>
            </div>
          </div>
        ) : (
          <Empty
            text={
              lang === "en"
                ? "Save a demo application to start a conversation."
                : "ውይይት ለመጀመር የሙከራ ማመልከቻ ያስቀምጡ።"
            }
            action={
              <Link className="button" href="/jobs">
                {t("explore")}
              </Link>
            }
          />
        )}
      </div>
    </Gate>
  );
}
export function Pricing() {
  const { t, lang } = useSera();
  return (
    <div className="container page narrow">
      <Heading
        eyebrow="SERA / FEATURED"
        title={t("promote")}
        description={t("noPay")}
      />
      <div className="panel pricing-panel">
        <span className="sample-tag">
          {lang === "en"
            ? "Original concept · unavailable"
            : "የመጀመሪያ ሐሳብ · አይገኝም"}
        </span>
        <h2>
          500 <span>ETB / 30 {lang === "en" ? "days" : "ቀናት"}</span>
        </h2>
        <p>{t("noPay")}</p>
        <div className="tags">
          <span>Chapa</span>
          <span>Telebirr</span>
          <span>CBE Birr</span>
        </div>
        <button className="button full" disabled>
          {lang === "en" ? "Checkout unavailable" : "ክፍያ አይገኝም"}
        </button>
        <Link className="inline-link" href="/employer">
          {t("workspace")}
          <ArrowUpRight size={16} />
        </Link>
      </div>
    </div>
  );
}
