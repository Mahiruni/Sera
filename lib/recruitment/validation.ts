export const workTypes = [
  "Full-time",
  "Part-time",
  "Contract",
  "Internship",
] as const;
export const applicationStages = [
  "screened",
  "interview",
  "offer",
  "rejected",
] as const;
export const uuid = (value: unknown): value is string =>
  typeof value === "string" &&
  /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(
    value,
  );
export function readJob(data: FormData) {
  const text = (key: string, min: number, max: number) => {
    const v = String(data.get(key) || "").trim();
    return v.length >= min && v.length <= max ? v : null;
  };
  const title = text("title", 3, 160),
    company = text("company", 2, 160),
    location = text("location", 2, 160),
    description = text("description", 40, 12000),
    requirements = text("requirements", 10, 8000);
  const work_type = String(data.get("work_type") || "");
  const salary_min = Number(data.get("salary_min") || 0),
    salary_max = Number(data.get("salary_max") || 0);
  if (
    !title ||
    !company ||
    !location ||
    !description ||
    !requirements ||
    !workTypes.includes(work_type as (typeof workTypes)[number]) ||
    ![salary_min, salary_max].every(
      (v) => Number.isSafeInteger(v) && v >= 0 && v <= 2147483647,
    ) ||
    salary_max < salary_min ||
    data.get("posting_acknowledgement") !== "yes"
  )
    return null;
  return {
    title,
    company,
    location,
    description,
    requirements,
    work_type,
    salary_min,
    salary_max,
  };
}
export function readApplication(data: FormData) {
  const job_id = data.get("job_id"),
    cover_note = String(data.get("cover_note") || "").trim();
  return uuid(job_id) &&
    cover_note.length >= 20 &&
    cover_note.length <= 4000 &&
    data.get("share_profile") === "yes"
    ? { job_id, cover_note, attach_cv: data.get("attach_cv") === "yes" }
    : null;
}
export function readMessage(data: FormData) {
  const application_id = data.get("application_id"),
    body = String(data.get("body") || "").trim();
  return uuid(application_id) && body.length > 0 && body.length <= 4000
    ? { application_id, body }
    : null;
}
