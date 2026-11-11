import { NextRequest, NextResponse } from "next/server";
import { createClient } from "@supabase/supabase-js";
import { readJob } from "@/lib/recruitment/validation";

export const runtime = "nodejs";
const noStore = { "Cache-Control": "no-store" };
const fail = (message: string, status: number) =>
  NextResponse.json({ error: message }, { status, headers: noStore });

/**
 * Android job creation: same validation and user-scoped RLS as the web flow.
 * Client-provided employer_id/status are never trusted.
 */
export async function POST(request: NextRequest) {
  const bearer = request.headers.get("authorization") || "";
  if (!/^Bearer [A-Za-z0-9._~-]+$/.test(bearer))
    return fail("Please sign in before publishing a job.", 401);

  const url = process.env.NEXT_PUBLIC_SUPABASE_URL;
  const key = process.env.NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY;
  if (!url || !key) return fail("Job publishing is not configured.", 503);
  const client = createClient(url, key, {
    global: { headers: { Authorization: bearer } },
    auth: { persistSession: false, autoRefreshToken: false },
  });
  const { data: { user }, error: authError } = await client.auth.getUser(bearer.slice(7));
  if (authError || !user?.email_confirmed_at)
    return fail("A verified SERA account is required.", 401);

  const contentLength = Number(request.headers.get("content-length") || 0);
  if (contentLength > 26000) return fail("Job details are too long.", 413);
  let payload: unknown;
  try {
    const body = await request.text();
    if (body.length > 26000) return fail("Job details are too long.", 413);
    payload = JSON.parse(body);
  } catch {
    return fail("Invalid job details.", 400);
  }
  if (!payload || typeof payload !== "object" || Array.isArray(payload))
    return fail("Invalid job details.", 400);
  const values = payload as Record<string, unknown>;
  const stringFields = ["title", "company", "location", "work_type", "description", "requirements"];
  if (stringFields.some(name => typeof values[name] !== "string"))
    return fail("Complete all required job details.", 400);
  if (typeof values.salary_min !== "number" || typeof values.salary_max !== "number" ||
      values.posting_acknowledgement !== true)
    return fail("Check the salary and confirm that you are authorized to post.", 400);

  const data = new FormData();
  stringFields.forEach(name => data.set(name, String(values[name])));
  data.set("salary_min", String(values.salary_min));
  data.set("salary_max", String(values.salary_max));
  data.set("posting_acknowledgement", "yes");
  const input = readJob(data);
  if (!input) return fail("Review the job title, description, requirements and salary range.", 422);

  const result = await client.from("jobs")
    .insert({ ...input, employer_id: user.id })
    .select("id")
    .single();
  if (result.error || !result.data) {
    console.error("Native job publication failed", { code: result.error?.code });
    return fail(result.error?.code === "42501"
      ? "You do not have permission to post this job."
      : "Unable to publish this job. Review your details or try again.", 409);
  }
  return NextResponse.json({ success: true, job_id: result.data.id },
    { status: 201, headers: noStore });
}
