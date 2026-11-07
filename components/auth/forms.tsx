"use client";
import { useActionState, useState } from "react";
import Link from "next/link";
import {
  Eye,
  EyeOff,
  ArrowRight,
  ShieldCheck,
  LoaderCircle,
  ArrowUpLeft,
  Mail,
  LockKeyhole,
  UserRound,
  Sparkles,
  Check,
  BriefcaseBusiness,
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
      <span className="password-input auth-input-wrap">
        <LockKeyhole className="auth-input-icon" size={17} aria-hidden="true" />
        <input
          required
          name={confirm ? "confirm_password" : "password"}
          type={show ? "text" : "password"}
          autoComplete={
            confirm || newPassword ? "new-password" : "current-password"
          }
          minLength={confirm || newPassword ? 12 : 1}
          maxLength={128}
          placeholder={
            newPassword || confirm
              ? lang === "am"
                ? "ቢያንስ 12 ቁምፊዎች"
                : "At least 12 characters"
              : lang === "am"
                ? "የይለፍ ቃልዎ"
                : "Enter your password"
          }
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
  const isEntry = mode === "signin" || mode === "signup";
  return (
    <section className={`auth-layout auth-mode-${mode}`}>
      <aside className="auth-story">
        <Link href="/" className="auth-wordmark" aria-label="Sera.et home">
          Sera<span>.et</span>
        </Link>
        <div className="auth-story-content">
          <p className="auth-story-kicker">
            <span />
            {lang === "am"
              ? "ለቀጣዩ እርምጃዎ"
              : "A LITTLE CLARITY. A NEW POSSIBILITY."}
          </p>
          <h2>
            {lang === "am" ? (
              <>
                ቀጣዩ ምዕራፍዎ
                <br />
                <em>ከዚህ ይጀምራል።</em>
              </>
            ) : (
              <>
                Your next chapter.
                <br />
                <em>Starts with you.</em>
              </>
            )}
          </h2>
          <p className="auth-story-description">
            {lang === "am"
              ? "ለእርስዎ የተዘጋጀ ቦታ። መገለጫዎን ያዘጋጁ፣ ምርጫዎችዎን ያስቀምጡ፣ ወደ ፊት ይቀጥሉ።"
              : "A space for who you are — and where you want to go. Bring your profile and preferences together."}
          </p>
          <div className="auth-art" aria-hidden="true">
            <div className="auth-art-orbit orbit-one" />
            <div className="auth-art-orbit orbit-two" />
            <div className="auth-art-spark">
              <Sparkles size={23} />
            </div>
            <div className="auth-art-ticket">
              <BriefcaseBusiness size={20} />
              <span>{lang === "am" ? "አዲስ አቅጣጫ" : "A fresh perspective"}</span>
            </div>
            <div className="auth-art-profile">
              <div className="auth-art-profile-top">
                <span className="auth-art-avatar">
                  <UserRound size={27} strokeWidth={1.5} />
                </span>
                <span className="auth-art-check">
                  <Check size={13} />
                </span>
              </div>
              <strong>
                {lang === "am" ? "የእርስዎ ልዩ መንገድ።" : "Uniquely you."}
              </strong>
              <span>
                {lang === "am"
                  ? "ችሎታዎ። ምርጫዎ። ወደፊትዎ።"
                  : "Your skills. Your direction."}
              </span>
              <div className="auth-art-lines">
                <i />
                <i />
              </div>
              <span className="auth-art-pill">
                <span />
                {lang === "am" ? "የእርስዎ ቀጣይ ምዕራፍ" : "Made for your next move"}
              </span>
            </div>
            <span className="auth-art-dot dot-a" />
            <span className="auth-art-dot dot-b" />
          </div>
        </div>
        <div className="auth-story-bottom">
          <span>
            <ShieldCheck size={16} />
            {lang === "am"
              ? "የግል መለያ። የእርስዎ ቁጥጥር።"
              : "Your profile. Your space. Your control."}
          </span>
          <Link href="/">
            {lang === "am" ? "ወደ መነሻ" : "Back to Sera"}
            <ArrowUpLeft size={14} />
          </Link>
        </div>
      </aside>
      <div className="auth-form-area">
        <Link
          href="/"
          className="auth-wordmark auth-mobile-brand"
          aria-label="Sera.et home"
        >
          Sera<span>.et</span>
        </Link>
        {isEntry && (
          <nav
            className="auth-tabs"
            aria-label={lang === "am" ? "የመለያ መዳረሻ" : "Account access"}
          >
            <Link
              href="/sign-in"
              aria-current={mode === "signin" ? "page" : undefined}
            >
              {copy.login}
            </Link>
            <Link
              href="/sign-up"
              aria-current={mode === "signup" ? "page" : undefined}
            >
              {copy.create}
            </Link>
          </nav>
        )}
        <div className="auth-form-heading">
          <span className="auth-heading-icon">
            {mode === "verify" ? <Mail size={21} /> : <ArrowRight size={21} />}
          </span>
          <h1>{heading}</h1>
          <p>
            {mode === "signup"
              ? lang === "am"
                ? "በቀላሉ ይጀምሩ። የቀረውን በኋላ ያክሉ።"
                : "A few details. A space of your own."
              : mode === "signin"
                ? lang === "am"
                  ? "ወደ የግል ቦታዎ ይመለሱ።"
                  : "Good to see you again. Make yourself at home."
                : mode === "verify"
                  ? "Open the confirmation email to continue. Check your spam folder, or request a fresh link below."
                  : mode === "forgot"
                    ? "Enter your account email. We’ll send you a link to start again."
                    : "Choose a strong, unique password for your account."}
          </p>
        </div>
        <form action={action} className="auth-form" aria-busy={pending}>
          {mode === "signup" && !registrationOpen && (
            <p className="auth-notice" role="status">
              Account registration is temporarily unavailable. Please try again
              later.
            </p>
          )}
          {mode === "signup" && (
            <label className="auth-label">
              {copy.name}
              <span className="auth-input-wrap">
                <UserRound
                  className="auth-input-icon"
                  size={17}
                  aria-hidden="true"
                />
                <input
                  name="full_name"
                  required
                  minLength={2}
                  maxLength={100}
                  autoComplete="name"
                  placeholder={lang === "am" ? "ሙሉ ስምዎ" : "Your full name"}
                />
              </span>
            </label>
          )}
          {mode !== "reset" && (
            <label className="auth-label">
              {copy.email}
              <span className="auth-input-wrap">
                <Mail
                  className="auth-input-icon"
                  size={17}
                  aria-hidden="true"
                />
                <input
                  name="email"
                  type="email"
                  required
                  maxLength={254}
                  autoComplete="email"
                  placeholder="you@example.com"
                  autoCapitalize="none"
                  spellCheck={false}
                />
              </span>
            </label>
          )}
          {["signup", "signin", "reset"].includes(mode) && (
            <PasswordField newPassword={mode !== "signin"} />
          )}
          {mode === "signin" && (
            <Link href="/forgot-password" className="auth-forgot">
              {copy.forgot}
            </Link>
          )}
          {mode === "reset" && <PasswordField confirm />}
          {mode === "signup" && (
            <>
              <p className="auth-help auth-password-hint">
                {lang === "am"
                  ? "ቢያንስ 12 ቁምፊዎች። ልዩ የይለፍ ቃል ይጠቀሙ።"
                  : "12 characters minimum. Make it unique to you."}
              </p>
              <div className="auth-field-grid">
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
                      </option>
                    ))}
                  </select>
                </label>
              </div>
              <p className="auth-help">
                {lang === "am"
                  ? "ኢሜይሎች በመረጡት ቋንቋ ይላካሉ፤ ለአፋርኛ እንግሊዝኛ ይጠቀማሉ። የቀጣሪ ምርጫ ማረጋገጫ አይሰጥም።"
                  : "Account emails follow your language, with English fallback for Afar. Employer selection does not grant verified status."}
              </p>
              <label className="auth-honeypot" aria-hidden="true">
                Website
                <input name="website" tabIndex={-1} autoComplete="off" />
              </label>
              <label className="auth-help auth-consent">
                <input
                  type="checkbox"
                  name="accept_terms"
                  value="yes"
                  required
                />
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
                </span>
              </label>
            </>
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
            className="button auth-submit"
            disabled={pending || (mode === "signup" && !registrationOpen)}
          >
            {pending ? (
              <LoaderCircle className="auth-spinner" size={18} />
            ) : null}
            {pending ? (lang === "am" ? "በሂደት ላይ…" : "Working…") : submit}
            {!pending && <ArrowRight size={18} />}
          </button>
          <div className="auth-form-links">
            {mode === "signup" ? (
              <p>
                {lang === "am" ? "መለያ አለዎት?" : "Already have an account?"}{" "}
                <Link href="/sign-in">{copy.login}</Link>
              </p>
            ) : mode === "signin" ? (
              <>
                <p>
                  {lang === "am" ? "አዲስ ነዎት?" : "New to Sera?"}{" "}
                  <Link href="/sign-up">{copy.create}</Link>
                </p>
                <Link href="/verify-email" className="auth-secondary-link">
                  {lang === "am"
                    ? "ኢሜይል እንደገና ያረጋግጡ"
                    : "Need a new verification email?"}
                </Link>
              </>
            ) : (
              <Link href="/sign-in">
                <ArrowUpLeft size={14} />
                {copy.login}
              </Link>
            )}
            {mode === "reset" && (
              <Link href="/forgot-password">Request a fresh reset link</Link>
            )}
          </div>
        </form>
        <p className="auth-footnote">
          <LockKeyhole size={13} />
          {lang === "am"
            ? "የግል መለያ። መገለጫዎ በእርስዎ ቁጥጥር ስር ነው።"
            : "Private account access · Your profile, your control"}
        </p>
        <nav
          className="auth-policy-links"
          aria-label={lang === "am" ? "የመለያ መረጃ" : "Account information"}
        >
          <Link href="/privacy">{lang === "am" ? "ግላዊነት" : "Privacy"}</Link>
          <span aria-hidden="true">·</span>
          <Link href="/terms">{lang === "am" ? "ውሎች" : "Terms"}</Link>
          <span aria-hidden="true">·</span>
          <Link href="/contact">{lang === "am" ? "እገዛ" : "Get help"}</Link>
        </nav>
      </div>
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
