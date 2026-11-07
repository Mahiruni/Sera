"use client";
import { useActionState, useState } from "react";
import Link from "next/link";
import {
  Eye,
  EyeOff,
  ArrowRight,
  ShieldCheck,
  LoaderCircle,
} from "lucide-react";
import { useSera } from "../store";
import { locales } from "@/lib/locales";
import { authCopy } from "@/lib/auth/copy";
import {
  register,
  signIn,
  requestReset,
  resendVerification,
  updatePassword,
  saveProfile,
  type ActionState,
} from "@/lib/auth/actions";
type Mode = "signup" | "signin" | "forgot" | "verify" | "reset";
const actions = {
  signup: register,
  signin: signIn,
  forgot: requestReset,
  verify: resendVerification,
  reset: updatePassword,
};
export function PasswordField({
  confirm = false,
  newPassword = false,
}: {
  confirm?: boolean;
  newPassword?: boolean;
}) {
  const [show, setShow] = useState(false);
  const { lang } = useSera();
  const copy = authCopy[lang];
  return (
    <label className="auth-label">
      {confirm
        ? lang === "am"
          ? "የይለፍ ቃል ያረጋግጡ"
          : "Confirm password"
        : copy.password}
      <span className="password-input">
        <input
          required
          name={confirm ? "confirm_password" : "password"}
          type={show ? "text" : "password"}
          autoComplete={
            confirm || newPassword ? "new-password" : "current-password"
          }
          minLength={confirm || newPassword ? 12 : 1}
          maxLength={128}
        />
        <button
          type="button"
          aria-label={show ? copy.hide : copy.show}
          onClick={() => setShow(!show)}
        >
          {show ? <EyeOff size={18} /> : <Eye size={18} />}
        </button>
      </span>
    </label>
  );
}
export function AuthForm({
  mode,
  registrationOpen = false,
}: {
  mode: Mode;
  registrationOpen?: boolean;
}) {
  const { lang, locale, setLang } = useSera();
  const copy = authCopy[lang];
  const [state, action, pending] = useActionState<ActionState, FormData>(
    actions[mode],
    {},
  );
  const [journey, setJourney] = useState("candidate");
  const heading =
    mode === "signup"
      ? copy.signup
      : mode === "signin"
        ? copy.signin
        : mode === "forgot"
          ? lang === "am"
            ? "የይለፍ ቃልዎን ያድሱ"
            : "A fresh start for your password"
          : mode === "verify"
            ? lang === "am"
              ? "ኢሜይልዎን ያረጋግጡ"
              : "Check your inbox"
            : lang === "am"
              ? "አዲስ የይለፍ ቃል ይምረጡ"
              : "Choose a new password";
  const submit =
    mode === "signup"
      ? copy.create
      : mode === "signin"
        ? copy.login
        : mode === "forgot"
          ? copy.reset
          : mode === "verify"
            ? lang === "am"
              ? "ኢሜይል እንደገና ላክ"
              : "Resend verification"
            : lang === "am"
              ? "የይለፍ ቃል አዘምን"
              : "Update password";
  return (
    <section className="auth-layout">
      <div className="auth-story">
        <p className="eyebrow">
          Sera.et / {lang === "am" ? "የእርስዎ ቀጣይ ምዕራፍ" : "YOUR NEXT CHAPTER"}
        </p>
        <h1>{heading}</h1>
        <p>
          {mode === "verify"
            ? "Follow the confirmation link in your email to verify ownership. Check your spam folder too. If a previous link expired, request another below."
            : lang === "am"
              ? "የግል መለያዎን ያዘጋጁ፣ ምርጫዎችዎን ያስቀምጡ እና ቀጣዩን እርምጃ በግልጽነት ያቅዱ።"
              : "A personal space to keep your details and preferences together. Start simply, and add more when you are ready."}
        </p>
        <div className="auth-trust">
          <ShieldCheck size={21} />
          <span>{copy.notice}</span>
        </div>
        <Link href="/demo" className="inline-link">
          {lang === "am" ? "መጀመሪያ ሙከራውን ያስሱ" : "Explore the demo first"}
          <ArrowRight size={15} />
        </Link>
      </div>
      <form action={action} className="panel auth-form" aria-busy={pending}>
        {mode === "signup" && !registrationOpen && (
          <p className="auth-notice" role="status">
            Account registration is being prepared. It opens after
            email-delivery and security verification. You can review this form
            and explore the demo now.
          </p>
        )}
        {mode === "signup" && (
          <>
            <label className="auth-label">
              {copy.name}
              <input
                name="full_name"
                required
                minLength={2}
                maxLength={100}
                autoComplete="name"
              />
            </label>
            <label className="auth-label">
              {copy.journey}
              <select
                name="journey"
                value={journey}
                onChange={(e) => setJourney(e.target.value)}
              >
                <option value="candidate">{copy.candidate}</option>
                <option value="employer">{copy.employer}</option>
              </select>
            </label>
            <p className="auth-help">
              {lang === "am"
                ? "ይህ ምርጫ የተረጋገጠ ቀጣሪ መብት አይሰጥም።"
                : "Your journey is a preference, not employer verification or privileged access."}
            </p>
            <label className="auth-label">
              {copy.language}
              <select
                name="locale"
                value={locale}
                onChange={(e) => setLang(e.target.value as typeof locale)}
              >
                {locales.map((l) => (
                  <option value={l.code} key={l.code}>
                    {l.native}
                    {l.status === "in-progress" ? " · English fallback" : ""}
                  </option>
                ))}
              </select>
            </label>
            <p className="auth-help">
              Email translations use the saved preference when a reviewed
              template is available. Otherwise English is used.
            </p>
            <label className="auth-honeypot" aria-hidden="true">
              Website
              <input name="website" tabIndex={-1} autoComplete="off" />
            </label>
          </>
        )}
        {mode !== "reset" && (
          <label className="auth-label">
            {copy.email}
            <input
              name="email"
              type="email"
              required
              maxLength={254}
              autoComplete="email"
            />
          </label>
        )}
        {["signup", "signin", "reset"].includes(mode) && (
          <PasswordField newPassword={mode !== "signin"} />
        )}
        {mode === "signup" && (
          <p className="auth-help">
            Use 12–128 characters. Never reuse a password from another service.
          </p>
        )}
        {mode === "reset" && <PasswordField confirm />}
        {mode === "signup" && (
          <label className="auth-help auth-consent">
            <input type="checkbox" name="accept_terms" value="yes" required />
            <span>
              {lang === "am"
                ? "የመለያ ውሎችን እቀበላለሁ፣ የግላዊነት መግለጫውንም አንብቤያለሁ።"
                : "I agree to the account terms and have read the privacy notice."}{" "}
              <Link href="/terms" target="_blank" rel="noopener">
                {lang === "am" ? "ውሎች" : "Terms"}
              </Link>
              {" · "}
              <Link href="/privacy" target="_blank" rel="noopener">
                {lang === "am" ? "ግላዊነት" : "Privacy"}
              </Link>
              .
              {lang === "am"
                ? " የማስታወቂያ ደንበኝነት አይካተትም።"
                : " No marketing subscription is included."}
            </span>
          </label>
        )}
        {state.message && (
          <p
            className={`auth-feedback ${state.success ? "success" : ""}`}
            role={state.success ? "status" : "alert"}
            lang="en"
          >
            {state.message}
          </p>
        )}
        <button
          className="button"
          disabled={pending || (mode === "signup" && !registrationOpen)}
        >
          {pending ? <LoaderCircle className="auth-spinner" size={17} /> : null}
          {pending ? (lang === "am" ? "በሂደት ላይ…" : "Working…") : submit}
          <ArrowRight size={16} />
        </button>
        <div className="auth-form-links">
          {mode !== "signin" && <Link href="/sign-in">{copy.login}</Link>}
          {mode === "signin" && (
            <>
              <Link href="/sign-up">{copy.create}</Link>
              <Link href="/forgot-password">{copy.forgot}</Link>
              <Link href="/verify-email">
                {lang === "am" ? "ኢሜይል ያረጋግጡ" : "Verify your email"}
              </Link>
            </>
          )}
          {mode === "reset" && (
            <Link href="/forgot-password">Request a fresh reset link</Link>
          )}
        </div>
      </form>
    </section>
  );
}
export type Profile = {
  id: string;
  full_name: string;
  locale: string;
  journey: string;
  location: string;
  phone: string;
  skills: string;
  experience: string;
  preferred_roles: string;
  company_name: string;
  company_role: string;
  onboarded_at: string | null;
};
export function ProfileForm({ profile }: { profile: Profile }) {
  const { lang } = useSera();
  const copy = authCopy[lang];
  const [state, action, pending] = useActionState<ActionState, FormData>(
    saveProfile,
    {},
  );
  const [journey, setJourney] = useState(profile.journey);
  return (
    <form
      className="panel auth-form profile-form"
      action={action}
      aria-busy={pending}
    >
      <label className="auth-label">
        {copy.name}
        <input
          required
          name="full_name"
          defaultValue={profile.full_name}
          minLength={2}
          maxLength={100}
          autoComplete="name"
        />
      </label>
      <label className="auth-label">
        {copy.language}
        <select name="locale" defaultValue={profile.locale}>
          {locales.map((l) => (
            <option value={l.code} key={l.code}>
              {l.native}
              {l.status === "in-progress" ? " · English fallback" : ""}
            </option>
          ))}
        </select>
      </label>
      <label className="auth-label">
        {copy.journey}
        <select
          name="journey"
          value={journey}
          onChange={(e) => setJourney(e.target.value)}
        >
          <option value="candidate">{copy.candidate}</option>
          <option value="employer">{copy.employer}</option>
        </select>
      </label>
      {[
        { key: "location", label: copy.location },
        { key: "phone", label: copy.phone },
        ...(journey === "candidate"
          ? [
              {
                key: "skills",
                label: lang === "am" ? "ችሎታዎች (አማራጭ)" : "Skills (optional)",
              },
              {
                key: "experience",
                label: lang === "am" ? "ልምድ (አማራጭ)" : "Experience (optional)",
              },
              {
                key: "preferred_roles",
                label:
                  lang === "am"
                    ? "የሚፈልጉት ሥራ (አማራጭ)"
                    : "Preferred roles (optional)",
              },
            ]
          : [
              {
                key: "company_name",
                label:
                  lang === "am"
                    ? "የድርጅት ስም (አማራጭ)"
                    : "Organization name (optional)",
              },
              {
                key: "company_role",
                label:
                  lang === "am"
                    ? "በድርጅቱ ያለዎት ሚና (አማራጭ)"
                    : "Your organization role (optional)",
              },
            ]),
      ].map(({ key, label }) => (
        <label className="auth-label" key={key}>
          {label}
          <input
            name={key}
            type={key === "phone" ? "tel" : "text"}
            defaultValue={String(profile[key as keyof Profile] || "")}
            maxLength={500}
          />
        </label>
      ))}
      <p className="auth-help">
        Your profile is private. Employer details are self-reported and do not
        grant verified status. Never enter identity documents, financial
        information or student records.
      </p>
      {state.message && (
        <p className="auth-feedback" role="alert">
          {state.message}
        </p>
      )}
      <button className="button" disabled={pending}>
        {pending ? "Saving…" : copy.save}
        <ArrowRight size={16} />
      </button>
    </form>
  );
}
