import seed from "./seed.json";
export type Lang = "en" | "am";
export type Role = "seeker" | "employer";
export type Stage = "applied" | "screened" | "interview" | "offer" | "rejected";
export type Profile = {
  headline: string;
  skills: string;
  exp: string;
  cv: string;
  loc: string;
  langs: string;
};
export type Job = {
  id: number;
  empId: number;
  title: string;
  org: string;
  loc: string;
  cat: string;
  type: string;
  payMin: number;
  payMax: number;
  featured: boolean;
  status: string;
  posted: string;
  desc: string;
  req: string;
};
export type Application = {
  id: number;
  jobId: number;
  seekerId: number;
  stage: Stage;
  cover: string;
  date: string;
};
export type Message = {
  id: number;
  appId: number;
  from: Role;
  text: string;
  ts: string;
};
export type State = {
  version: 1;
  role: Role | null;
  name: string;
  company: string;
  profile: Profile;
  jobs: Job[];
  apps: Application[];
  messages: Message[];
  saved: number[];
};
export const categories = seed.cats.map(({ id, en, am }) => ({ id, en, am }));
export const locations = [
  "Addis Ababa",
  "Adama",
  "Hawassa",
  "Bahir Dar",
  "Dire Dawa",
  "Mekelle",
  "Remote (Ethiopia)",
];
export const stages: Stage[] = [
  "applied",
  "screened",
  "interview",
  "offer",
  "rejected",
];
export const initialState: State = {
  version: 1,
  role: null,
  name: "Almaz Bekele",
  company: "Habesha Manufacturing PLC",
  profile: {
    headline: "Accountant · IFRS · Peachtree",
    skills:
      "Peachtree, QuickBooks, Excel, IFRS, bank reconciliation, VAT filing",
    exp: "3 years accounting at a manufacturing firm in Addis Ababa.",
    cv: "",
    loc: "Addis Ababa",
    langs: "Amharic, English",
  },
  jobs: seed.jobs,
  apps: [],
  messages: [],
  saved: [],
};
export function filterJobs(jobs: Job[], q = "", cat = "", loc = "", type = "") {
  const query = q.trim().toLowerCase();
  return jobs.filter(
    (j) =>
      j.status === "open" &&
      (!query ||
        `${j.title} ${j.org} ${j.req}`.toLowerCase().includes(query)) &&
      (!cat || j.cat === cat) &&
      (!loc || j.loc === loc) &&
      (!type || j.type === type),
  );
}
export function pay(j: Job) {
  return j.payMin === 0 && j.payMax === 0
    ? "Salary not specified"
    : `${j.payMin.toLocaleString("en-US")}${j.payMax !== j.payMin ? "–" + j.payMax.toLocaleString("en-US") : ""} ETB`;
}
export function keywordMatch(p: Profile, j: Job) {
  const words = new Set(
    `${p.headline} ${p.skills} ${p.exp} ${p.cv}`
      .toLowerCase()
      .match(/[a-z0-9+#]+/g) || [],
  );
  const required = [
    ...new Set(j.req.toLowerCase().match(/[a-z0-9+#]{3,}/g) || []),
  ];
  const hits = required.filter((w) => words.has(w));
  return {
    percent: required.length
      ? Math.round((hits.length / required.length) * 100)
      : 0,
    hits,
  };
}
export function apply(state: State, jobId: number, cover: string): State {
  if (
    state.role !== "seeker" ||
    !state.jobs.some((j) => j.id === jobId && j.status === "open") ||
    state.apps.some((a) => a.jobId === jobId)
  )
    return state;
  return {
    ...state,
    apps: [
      ...state.apps,
      {
        id: Date.now(),
        jobId,
        seekerId: 1,
        stage: "applied",
        cover,
        date: new Date().toISOString(),
      },
    ],
  };
}
export function isState(value: unknown): value is State {
  if (!value || typeof value !== "object") return false;
  const s = value as State;
  return (
    s.version === 1 &&
    (s.role === null || s.role === "seeker" || s.role === "employer") &&
    typeof s.name === "string" &&
    typeof s.company === "string" &&
    s.profile != null &&
    ["headline", "skills", "exp", "cv", "loc", "langs"].every(
      (k) => typeof s.profile[k as keyof Profile] === "string",
    ) &&
    Array.isArray(s.jobs) &&
    s.jobs.every(
      (j) =>
        typeof j.id === "number" &&
        [
          "title",
          "org",
          "loc",
          "cat",
          "type",
          "status",
          "posted",
          "desc",
          "req",
        ].every((k) => typeof j[k as keyof Job] === "string") &&
        Number.isFinite(j.payMin) &&
        Number.isFinite(j.payMax) &&
        typeof j.empId === "number",
    ) &&
    Array.isArray(s.apps) &&
    s.apps.every(
      (a) =>
        typeof a.id === "number" &&
        typeof a.jobId === "number" &&
        stages.includes(a.stage) &&
        typeof a.cover === "string",
    ) &&
    Array.isArray(s.messages) &&
    s.messages.every(
      (m) =>
        typeof m.id === "number" &&
        typeof m.appId === "number" &&
        (m.from === "seeker" || m.from === "employer") &&
        typeof m.text === "string" &&
        typeof m.ts === "string",
    ) &&
    Array.isArray(s.saved) &&
    s.saved.every((id) => typeof id === "number")
  );
}
