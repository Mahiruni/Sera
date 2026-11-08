"use server";
import { revalidatePath } from "next/cache";
import { redirect } from "next/navigation";
import { requiredProfile } from "@/lib/auth/profile";
import { serverClient } from "@/lib/supabase/server";
import { employerReturn } from "@/lib/workspace/model";
import {
  readJob,
  readApplication,
  readMessage,
  uuid,
  applicationStages,
} from "./validation";
export type RecruitmentState = { message?: string; success?: boolean };
export async function createJob(
  _: RecruitmentState,
  data: FormData,
): Promise<RecruitmentState> {
  const { user } = await requiredProfile();
  const input = readJob(data);
  if (!input)
    return {
      message:
        "Check the job details, salary range and posting acknowledgement.",
    };
  const c = await serverClient();
  const r = await c
    .from("jobs")
    .insert({ ...input, employer_id: user.id })
    .select("id")
    .single();
  if (r.error)
    return {
      message:
        "The job could not be posted. Check your details or try later; posting limits may apply.",
    };
  revalidatePath("/jobs");
  revalidatePath("/employer");
  redirect("/jobs/" + r.data.id);
}
export async function applyToJob(
  _: RecruitmentState,
  data: FormData,
): Promise<RecruitmentState> {
  const { user, profile } = await requiredProfile();
  const input = readApplication(data);
  if (!input || !profile)
    return {
      message:
        "Add a cover note of 20–4,000 characters and confirm which profile details will be shared.",
    };
  const c = await serverClient();
  const r = await c
    .from("applications")
    .insert({
      ...input,
      candidate_id: user.id,
      candidate_name: profile.full_name,
      skills: profile.skills,
      experience: profile.experience,
    })
    .select("id")
    .single();
  if (r.error)
    return {
      message:
        r.error.code === "23505"
          ? "You already applied to this job. Find it in My applications."
          : "The application could not be sent. The job may have closed, or an application limit was reached.",
    };
  revalidatePath("/applications");
  revalidatePath("/employer");
  redirect("/applications/" + r.data.id);
}
export async function sendApplicationMessage(
  _: RecruitmentState,
  data: FormData,
): Promise<RecruitmentState> {
  const { user } = await requiredProfile();
  const input = readMessage(data);
  if (!input) return { message: "Enter a message of 1–4,000 characters." };
  const c = await serverClient();
  const r = await c
    .from("application_messages")
    .insert({ ...input, sender_id: user.id });
  if (r.error)
    return {
      message:
        "Message could not be sent. The conversation may be withdrawn or a message limit reached.",
    };
  revalidatePath("/applications/" + input.application_id);
  return { success: true, message: "Message sent." };
}
export async function changeStage(data: FormData) {
  await requiredProfile();
  const id = data.get("application_id"),
    stage = String(data.get("stage"));
  if (
    !uuid(id) ||
    (!applicationStages.includes(stage as (typeof applicationStages)[number]) &&
      stage !== "withdrawn")
  )
    return;
  const c = await serverClient();
  const r = await c
    .from("applications")
    .update({ stage })
    .eq("id", id)
    .select("id")
    .single();
  if (r.error) redirect("/applications/" + id + "?error=stage");
  revalidatePath("/applications");
  revalidatePath("/employer");
  redirect("/applications/" + id + "?updated=stage");
}
export async function changeJobStatus(data: FormData) {
  await requiredProfile();
  const id = data.get("job_id"),
    status = data.get("status");
  if (!uuid(id) || (status !== "open" && status !== "closed")) return;
  const c = await serverClient();
  const r = await c
    .from("jobs")
    .update({ status })
    .eq("id", id)
    .select("id")
    .single();
  if (r.error) redirect(employerReturn(data, true));
  revalidatePath("/jobs");
  revalidatePath("/jobs/" + id);
  revalidatePath("/employer");
  redirect(employerReturn(data));
}
export async function toggleSavedJob(data: FormData) {
  const { user } = await requiredProfile();
  const id = data.get("job_id");
  if (!uuid(id)) return;
  const c = await serverClient();
  const remove = data.get("remove") === "yes";
  const r = remove
    ? await c
        .from("saved_jobs")
        .delete()
        .eq("job_id", id)
        .eq("user_id", user.id)
    : await c
        .from("saved_jobs")
        .upsert(
          { user_id: user.id, job_id: id },
          { onConflict: "user_id,job_id", ignoreDuplicates: true },
        );
  if (r.error) redirect("/jobs/" + id + "?error=save");
  revalidatePath("/saved");
  revalidatePath("/jobs/" + id);
  redirect(remove ? "/saved" : "/jobs/" + id + "?saved=yes");
}
