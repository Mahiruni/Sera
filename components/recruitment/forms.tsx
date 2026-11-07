"use client";
import { useActionState } from "react";
import { ArrowRight, LoaderCircle } from "lucide-react";
import {
  createJob,
  applyToJob,
  sendApplicationMessage,
  type RecruitmentState,
} from "@/lib/recruitment/actions";
import { workTypes } from "@/lib/recruitment/validation";
function Submit({ pending, label }: { pending: boolean; label: string }) {
  return (
    <button className="button" disabled={pending}>
      {pending ? (
        <LoaderCircle size={17} className="auth-spinner" />
      ) : (
        <ArrowRight size={17} />
      )}{" "}
      {pending ? "Please wait…" : label}
    </button>
  );
}
function Feedback({ state }: { state: RecruitmentState }) {
  return state.message ? (
    <p
      role={state.success ? "status" : "alert"}
      className={state.success ? "auth-notice" : "auth-feedback"}
    >
      {state.message}
    </p>
  ) : null;
}
export function JobPostForm({ company = "" }: { company?: string }) {
  const [state, action, pending] = useActionState(createJob, {});
  return (
    <form action={action} className="panel live-form" aria-busy={pending}>
      <div className="live-field-grid">
        {[
          { name: "title", label: "Job title", min: 3, max: 160 },
          { name: "company", label: "Organization", min: 2, max: 160 },
          {
            name: "location",
            label: "Location or remote region",
            min: 2,
            max: 160,
          },
        ].map((f) => (
          <label key={f.name} className="auth-label">
            {f.label}
            <input
              name={f.name}
              required
              minLength={f.min}
              maxLength={f.max}
              defaultValue={f.name === "company" ? company : ""}
            />
          </label>
        ))}
        <label className="auth-label">
          Work type
          <select name="work_type">
            {workTypes.map((t) => (
              <option key={t}>{t}</option>
            ))}
          </select>
        </label>
        <label className="auth-label">
          Monthly salary minimum (ETB)
          <input
            name="salary_min"
            type="number"
            min="0"
            max="2147483647"
            defaultValue="0"
          />
        </label>
        <label className="auth-label">
          Monthly salary maximum (ETB)
          <input
            name="salary_max"
            type="number"
            min="0"
            max="2147483647"
            defaultValue="0"
          />
        </label>
      </div>
      <p className="auth-help">
        Set both salary fields to zero if salary is not specified. Organization
        details are self-reported.
      </p>
      <label className="auth-label">
        Role description
        <textarea
          name="description"
          required
          minLength={40}
          maxLength={12000}
          rows={7}
        />
      </label>
      <label className="auth-label">
        Requirements
        <textarea
          name="requirements"
          required
          minLength={10}
          maxLength={8000}
          rows={5}
        />
      </label>
      <label className="live-check">
        <input
          name="posting_acknowledgement"
          type="checkbox"
          value="yes"
          required
        />
        <span>
          I am authorized to post this role. These details will be public. I
          will not request payments, identity documents or financial data from
          applicants through Sera.
        </span>
      </label>
      <Feedback state={state} />
      <Submit pending={pending} label="Publish job" />
    </form>
  );
}
export function ApplyForm({ jobId }: { jobId: string }) {
  const [state, action, pending] = useActionState(applyToJob, {});
  return (
    <form action={action} className="panel live-form" aria-busy={pending}>
      <input name="job_id" type="hidden" value={jobId} />
      <h2>Make your introduction</h2>
      <label className="auth-label">
        Cover note
        <textarea
          name="cover_note"
          required
          minLength={20}
          maxLength={4000}
          rows={6}
          placeholder="Why is this role a good fit for you?"
        />
      </label>
      <label className="live-check">
        <input type="checkbox" name="share_profile" value="yes" required />
        <span>
          Share my name, skills, experience and this note with this job’s
          poster. My phone number, email and other private profile fields are
          not included. This creates a snapshot; later profile changes do not
          alter it.
        </span>
      </label>
      <Feedback state={state} />
      <Submit pending={pending} label="Send application" />
    </form>
  );
}
export function MessageForm({ applicationId }: { applicationId: string }) {
  const [state, action, pending] = useActionState(sendApplicationMessage, {});
  return (
    <form action={action} className="live-form" aria-busy={pending}>
      <input name="application_id" type="hidden" value={applicationId} />
      <label className="auth-label">
        Your message
        <textarea name="body" required maxLength={4000} rows={3} />
      </label>
      <Feedback state={state} />
      <Submit pending={pending} label="Send message" />
    </form>
  );
}
