import "server-only";
import { serverClient } from "@/lib/supabase/server";
export type LiveJob = {
  id: string;
  employer_id: string;
  title: string;
  company: string;
  location: string;
  work_type: string;
  salary_min: number;
  salary_max: number;
  description: string;
  requirements: string;
  status: string;
  created_at: string;
};
export type LiveApplication = {
  id: string;
  job_id: string;
  candidate_id: string;
  candidate_name: string;
  skills: string;
  experience: string;
  cover_note: string;
  stage: string;
  created_at: string;
  jobs: Pick<
    LiveJob,
    "id" | "title" | "company" | "employer_id" | "status"
  > | null;
};
export const salary = (j: Pick<LiveJob, "salary_min" | "salary_max">) =>
  j.salary_max > 0
    ? `${j.salary_min.toLocaleString("en-US")}–${j.salary_max.toLocaleString("en-US")} ETB / month`
    : "Salary not specified";
export async function liveJob(id: string) {
  const c = await serverClient();
  const r = await c.from("jobs").select("*").eq("id", id).maybeSingle();
  if (r.error) throw Error("Job details could not be loaded. Please retry.");
  return r.data as LiveJob | null;
}
