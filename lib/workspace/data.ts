import "server-only";
import { serverClient } from "@/lib/supabase/server";
import { accountAssets } from "@/lib/account/assets";
import type { LiveJob, LiveApplication } from "@/lib/recruitment/data";
import type { WorkspaceMode } from "./model";
export type WorkspaceMessage = {
  id: string;
  body: string;
  created_at: string;
  sender_id: string;
  application_id: string;
  applications: {
    candidate_id: string;
    jobs: { title: string; employer_id: string } | null;
  } | null;
};
export type SavedPreview = {
  job_id: string;
  jobs: Pick<
    LiveJob,
    "id" | "title" | "company" | "location" | "status"
  > | null;
};
export async function workspaceData(
  userId: string,
  mode: WorkspaceMode,
  options: { jobsPage?: number; appsPage?: number; status?: string } = {},
) {
  const c = await serverClient(),
    employer = mode === "employer";
  const appsQuery = () => {
    const q = c
      .from("applications")
      .select("*,jobs!inner(id,title,company,employer_id,status)", {
        count: "exact",
      });
    return employer
      ? q.eq("jobs.employer_id", userId)
      : q.eq("candidate_id", userId);
  };
  let jobQuery = c
    .from("jobs")
    .select("*", { count: "exact" })
    .eq("employer_id", userId);
  if (["open", "closed"].includes(options.status || ""))
    jobQuery = jobQuery.eq("status", options.status!);
  const appOffset = ((options.appsPage || 1) - 1) * 8,
    jobOffset = ((options.jobsPage || 1) - 1) * 8;
  const msgQuery = c
    .from("application_messages")
    .select(
      "id,body,created_at,sender_id,application_id,applications!inner(candidate_id,jobs!inner(title,employer_id))",
      { count: "exact" },
    );
  const scopedMessages = employer
    ? msgQuery.eq("applications.jobs.employer_id", userId)
    : msgQuery.eq("applications.candidate_id", userId);
  const [apps, jobs, saved, messages, interviews, open, assets] =
    await Promise.all([
      appsQuery()
        .order("created_at", { ascending: false })
        .range(employer ? appOffset : 0, employer ? appOffset + 7 : 5),
      employer
        ? jobQuery
            .order("created_at", { ascending: false })
            .range(jobOffset, jobOffset + 7)
        : Promise.resolve({ data: [], error: null, count: 0 }),
      employer
        ? Promise.resolve({ data: [], error: null, count: 0 })
        : c
            .from("saved_jobs")
            .select("job_id,jobs(id,title,company,location,status)", {
              count: "exact",
            })
            .eq("user_id", userId)
            .order("created_at", { ascending: false })
            .limit(3),
      scopedMessages.order("created_at", { ascending: false }).limit(3),
      appsQuery().in("stage", ["interview", "offer"]).limit(0),
      employer
        ? c
            .from("jobs")
            .select("id", { count: "exact", head: true })
            .eq("employer_id", userId)
            .eq("status", "open")
        : Promise.resolve({ count: 0, error: null }),
      accountAssets(userId)
        .then((data) => ({ data, error: false }))
        .catch(() => ({ data: null, error: true })),
    ]);
  return {
    apps: {
      rows: (apps.data || []) as unknown as LiveApplication[],
      count: apps.error ? null : apps.count,
      error: !!apps.error,
    },
    jobs: {
      rows: (jobs.data || []) as LiveJob[],
      count: jobs.error ? null : jobs.count,
      error: !!jobs.error,
    },
    saved: {
      rows: (saved.data || []) as unknown as SavedPreview[],
      count: saved.error ? null : saved.count,
      error: !!saved.error,
    },
    messages: {
      rows: (messages.data || []) as unknown as WorkspaceMessage[],
      count: messages.error ? null : messages.count,
      error: !!messages.error,
    },
    interviews: interviews.error ? null : interviews.count,
    open: open.error ? null : open.count,
    assets: assets.data,
    assetsError: assets.error,
  };
}
export type WorkspaceData = Awaited<ReturnType<typeof workspaceData>>;
