"use client";
import "@/app/auth-premium.css";
import { useActionState, useMemo, useState, type FormEvent } from "react";
import Link from "next/link";
import { SeraBrandMark } from "@/components/ui";
import {
  ArrowRight,
  BriefcaseBusiness,
  CircleAlert,
  CircleCheck,
  Eye,
  EyeOff,
  LoaderCircle,
  LockKeyhole,
  Mail,
  Phone,
  Search,
  UserRound,
} from "lucide-react";
import { useSera } from "../store";
import { register, signIn, type ActionState } from "@/lib/auth/actions";
import { continueWithGoogle } from "@/lib/auth/google";
import { continueWithLinkedIn } from "@/lib/auth/linkedin";
import { liMessage } from "@/lib/auth/linkedin-copy";

type Mode = "signin" | "signup";
type Notice = "password-updated" | "signed-out";
type Key = "name" | "email" | "phone" | "password" | "confirm" | "terms";
type Errors = Partial<Record<Key, string>>;
type Values = {
  name: string;
  email: string;
  dial: string;
  phone: string;
  password: string;
  confirm: string;
  journey: "candidate" | "employer";
  terms: boolean;
};

const COUNTRIES = [
  ["🇪🇹", "ET", "+251"],
  ["🇰🇪", "KE", "+254"],
  ["🇩🇯", "DJ", "+253"],
  ["🇸🇴", "SO", "+252"],
  ["🇪🇷", "ER", "+291"],
  ["🇸🇩", "SD", "+249"],
  ["🇸🇸", "SS", "+211"],
  ["🇺🇬", "UG", "+256"],
  ["🇹🇿", "TZ", "+255"],
  ["🇪🇬", "EG", "+20"],
  ["🇸🇦", "SA", "+966"],
  ["🇦🇪", "AE", "+971"],
  ["🇶🇦", "QA", "+974"],
  ["🇰🇼", "KW", "+965"],
  ["🇬🇧", "GB", "+44"],
  ["🇺🇸", "US", "+1"],
  ["🇨🇦", "CA", "+1"],
  ["🇩🇪", "DE", "+49"],
] as const;

const T = {
  en: {
    signinTitle: "Welcome back",
    signinSub: "Welcome back to your professional community. Your people, ideas and opportunities are waiting.",
    signupTitle: "Create your account",
    signupSub: "Build your professional identity, share your work and connect with people who move you forward.",
    name: "Full name",
    namePh: "Your full name",
    email: "Email address",
    phone: "Phone number",
    phoneOpt: "optional",
    password: "Password",
    confirm: "Confirm password",
    passwordPh: "Enter your password",
    newPasswordPh: "At least 12 characters",
    confirmPh: "Re-enter your password",
    show: "Show password",
    hide: "Hide password",
    forgot: "Forgot password?",
    signIn: "Sign in",
    signingIn: "Signing in...",
    create: "Create account",
    creating: "Creating account...",
    google: "Continue with Google",
    linkedin: "Continue with LinkedIn",
    connectingGoogle: "Connecting to Google...",
    connectingLinkedin: "Connecting to LinkedIn...",
    or: "or",
    accountType: "I am a",
    seeker: "Professional",
    seekerSub: "Share work & connect",
    employer: "Employer",
    employerSub: "Discover & hire talent",
    agree: "I agree to the",
    terms: "Terms",
    and: "and",
    privacy: "Privacy Policy",
    haveAccount: "Already have an account?",
    newHere: "New to Sera?",
    country: "Country code",
    strength: ["", "Weak", "Fair", "Good", "Strong"],
    closed: "Account registration is temporarily unavailable. Please try again later.",
    pwUpdated: "Your password was updated. Sign in with the new password.",
    signedOut: "You have signed out.",
    eName: "Enter your full name (at least 2 characters).",
    eEmail: "Enter a valid email address.",
    ePhone: "Enter a valid phone number.",
    ePwRequired: "Enter your password.",
    ePwShort: "Use at least 12 characters.",
    eConfirm: "Passwords do not match.",
    eTerms: "Please accept the Terms and Privacy Policy.",
    secure: "Secure sign-in",
  },
  am: {
    signinTitle: "እንኳን ደህና ተመለሱ",
    signinSub: "ወደ የግል ቦታዎ ይመለሱ።",
    signupTitle: "የSera መለያዎን ይፍጠሩ",
    signupSub: "በቀላሉ ይጀምሩ። የቀረውን በኋላ ያክሉ።",
    name: "ሙሉ ስም",
    namePh: "ሙሉ ስምዎ",
    email: "የኢሜይል አድራሻ",
    phone: "ስልክ ቁጥር",
    phoneOpt: "አማራጭ",
    password: "የይለፍ ቃል",
    confirm: "የይለፍ ቃል ያረጋግጡ",
    passwordPh: "የይለፍ ቃልዎ",
    newPasswordPh: "ቢያንስ 12 ቁምፊዎች",
    confirmPh: "የይለፍ ቃልዎን እንደገና ያስገቡ",
    show: "የይለፍ ቃል አሳይ",
    hide: "የይለፍ ቃል ደብቅ",
    forgot: "የይለፍ ቃል ረሱ?",
    signIn: "ይግቡ",
    signingIn: "በመግባት ላይ...",
    create: "መለያ ፍጠር",
    creating: "መለያ በመፍጠር ላይ...",
    google: "በGoogle ይቀጥሉ",
    linkedin: "በLinkedIn ይቀጥሉ",
    connectingGoogle: "ወደ Google በመገናኘት ላይ...",
    connectingLinkedin: "ወደ LinkedIn በመገናኘት ላይ...",
    or: "ወይም",
    accountType: "እኔ",
    seeker: "ሥራ ፈላጊ",
    seekerSub: "ሥራ ይፈልጉ",
    employer: "ቀጣሪ",
    employerSub: "ሠራተኛ ይቅጠሩ",
    agree: "እኔ",
    terms: "ውሎችን",
    and: "እና",
    privacy: "የግላዊነት መግለጫን",
    haveAccount: "መለያ አለዎት?",
    newHere: "አዲስ ነዎት?",
    country: "የአገር ኮድ",
    strength: ["", "ደካማ", "መካከለኛ", "ጥሩ", "ጠንካራ"],
    closed: "የመለያ ምዝገባ ለጊዜው አይገኝም። እባክዎ ቆይተው ይሞክሩ።",
    pwUpdated: "የይለፍ ቃልዎ ተዘምኗል። በአዲሱ የይለፍ ቃል ይግቡ።",
    signedOut: "ወጥተዋል።",
    eName: "ሙሉ ስምዎን ያስገቡ (ቢያንስ 2 ቁምፊዎች)።",
    eEmail: "ትክክለኛ ኢሜይል ያስገቡ።",
    ePhone: "ትክክለኛ ስልክ ቁጥር ያስገቡ።",
    ePwRequired: "የይለፍ ቃልዎን ያስገቡ።",
    ePwShort: "ቢያንስ 12 ቁምፊዎች ይጠቀሙ።",
    eConfirm: "የይለፍ ቃሎቹ አይመሳሰሉም።",
    eTerms: "እባክዎ ውሎችንና የግላዊነት መግለጫውን ይቀበሉ።",
    secure: "ደህንነቱ የተጠበቀ መግቢያ",
  },
  om: {
    signinTitle:"Baga nagaan deebite", signinSub:"Gara hawaasa ogummaa keetti deebi'i.",
    signupTitle:"Herrega kee bani", signupSub:"Eenymummaa ogummaa kee ijaari, hojii kee qoodi, namoota waliin wal qunnami.",
    name:"Maqaa guutuu",namePh:"Maqaa kee guutuu",email:"Teessoo imeelii",phone:"Lakkoofsa bilbilaa",phoneOpt:"filannoo",
    password:"Jecha iccitii",confirm:"Jecha iccitii mirkaneessi",passwordPh:"Jecha iccitii galchi",newPasswordPh:"Qubee yoo xiqqaate 12",confirmPh:"Jecha iccitii irra deebi'i",
    show:"Jecha iccitii agarsiisi",hide:"Jecha iccitii dhoksi",forgot:"Jecha iccitii dagatte?",
    signIn:"Seeni",signingIn:"Seenaa jira...",create:"Herrega bani",creating:"Herrega banuu...",google:"Google waliin itti fufi",linkedin:"LinkedIn waliin itti fufi",connectingGoogle:"Google waliin wal qunnamaa jira...",connectingLinkedin:"LinkedIn waliin wal qunnamaa jira...",or:"yookaan",
    accountType:"Ani",seeker:"Ogeessa",seekerSub:"Hojii qoodi, wal qunnami",employer:"Qacaraa",employerSub:"Ogeeyyii barbaadi",
    agree:"Waliin gala",terms:"Haala itti fayyadamaa",and:"fi",privacy:"Imaammata iccitii",haveAccount:"Herrega qabdaa?",newHere:"Sera irratti haaraa dhaa?",country:"Koodii biyyaa",strength:["","Dadhabaa","Giddu galeessa","Gaarii","Cimaa"],
    closed:"Yeroo ammaa galmeen herregaa hin jiru. Booda irra deebi'i.",pwUpdated:"Jecha iccitii kee haaromfame. Jecha haaraa fayyadami.",signedOut:"Herrega keessaa baate.",
    eName:"Maqaa guutuu galchi (yoo xiqqaate qubee 2).",eEmail:"Teessoo imeelii sirrii galchi.",ePhone:"Lakkoofsa bilbilaa sirrii galchi.",ePwRequired:"Jecha iccitii galchi.",ePwShort:"Qubee yoo xiqqaate 12 fayyadami.",eConfirm:"Jechoonni iccitii wal hin siman.",eTerms:"Haala itti fayyadamaa fi imaammata iccitii fudhadhu.",secure:"Seensa nageenya qabu",
  },
};
type Copy = (typeof T)["en"];

const EMAIL = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const digits = (s: string) => s.replace(/\D/g, "").replace(/^0+/, "");

function validate(v: Values, mode: Mode, t: Copy): Errors {
  const e: Errors = {};
  if (mode === "signup" && v.name.trim().length < 2) e.name = t.eName;
  if (!EMAIL.test(v.email.trim()) || v.email.trim().length > 254)
    e.email = t.eEmail;
  if (mode === "signup") {
    const d = digits(v.phone);
    if (d && (d.length < 7 || d.length > 12)) e.phone = t.ePhone;
  }
  if (mode === "signin") {
    if (!v.password) e.password = t.ePwRequired;
  } else {
    if (v.password.length < 12 || v.password.length > 128)
      e.password = t.ePwShort;
    if (v.confirm !== v.password || !v.confirm) e.confirm = t.eConfirm;
    if (!v.terms) e.terms = t.eTerms;
  }
  return e;
}

function strength(pw: string) {
  if (!pw) return 0;
  const pts =
    Number(pw.length >= 12) +
    Number(pw.length >= 16) +
    Number(/[a-z]/.test(pw) && /[A-Z]/.test(pw)) +
    Number(/\d/.test(pw)) +
    Number(/[^A-Za-z0-9]/.test(pw));
  if (pw.length < 12) return 1;
  return Math.min(4, Math.max(2, pts - 1));
}

function PasswordInput(props: {
  id: string;
  name: string;
  value: string;
  onChange: (v: string) => void;
  onBlur: () => void;
  placeholder: string;
  autoComplete: string;
  invalid: boolean;
  describedBy?: string;
  show: string;
  hide: string;
  enterKeyHint: "next" | "go" | "done";
}) {
  const [visible, setVisible] = useState(false);
  return (
    <span className="ax-input">
      <LockKeyhole className="ax-ico" size={18} aria-hidden="true" />
      <input
        id={props.id}
        name={props.name}
        type={visible ? "text" : "password"}
        value={props.value}
        onChange={(e) => props.onChange(e.target.value)}
        onBlur={props.onBlur}
        placeholder={props.placeholder}
        autoComplete={props.autoComplete}
        autoCapitalize="none"
        autoCorrect="off"
        spellCheck={false}
        maxLength={128}
        enterKeyHint={props.enterKeyHint}
        aria-invalid={props.invalid}
        aria-describedby={props.describedBy}
        className="ax-has-trailing"
      />
      <button
        type="button"
        className="ax-eye"
        aria-label={visible ? props.hide : props.show}
        aria-pressed={visible}
        onClick={() => setVisible((s) => !s)}
      >
        {visible ? <EyeOff size={19} /> : <Eye size={19} />}
      </button>
    </span>
  );
}

function FieldError({ id, text }: { id: string; text?: string }) {
  if (!text) return null;
  return (
    <span className="ax-err" id={`${id}-err`} role="alert">
      <CircleAlert size={14} aria-hidden="true" />
      {text}
    </span>
  );
}

const GoogleMark = () => (
  <svg width="20" height="20" viewBox="0 0 48 48" aria-hidden="true">
    <path fill="#EA4335" d="M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z" />
    <path fill="#4285F4" d="M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z" />
    <path fill="#FBBC05" d="M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z" />
    <path fill="#34A853" d="M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z" />
  </svg>
);
const LinkedInMark = () => (
  <svg width="20" height="20" viewBox="0 0 24 24" aria-hidden="true" fill="currentColor">
    <path d="M20.45 20.45h-3.56v-5.57c0-1.33-.03-3.04-1.85-3.04-1.86 0-2.14 1.45-2.14 2.94v5.67H9.35V9h3.41v1.56h.05c.48-.9 1.64-1.85 3.37-1.85 3.6 0 4.27 2.37 4.27 5.46v6.28zM5.34 7.43a2.06 2.06 0 1 1 0-4.12 2.06 2.06 0 0 1 0 4.12zM7.12 20.45H3.56V9h3.56v11.45z" />
  </svg>
);

export function PremiumAuth({
  mode,
  registrationOpen = false,
  destination,
  notice,
}: {
  mode: Mode;
  registrationOpen?: boolean;
  destination?: string;
  notice?: Notice;
}) {
  const { lang, locale } = useSera();
  const t: Copy = locale === "om" ? T.om : lang === "am" ? T.am : T.en;
  const signup = mode === "signup";
  const [state, action, pending] = useActionState<ActionState, FormData>(
    signup ? register : signIn,
    {},
  );
  const [gState, gAction, gPending] = useActionState<ActionState, FormData>(
    continueWithGoogle,
    {},
  );
  const [lState, lAction, lPending] = useActionState<ActionState, FormData>(
    continueWithLinkedIn,
    {},
  );
  const busy = pending || gPending || lPending;
  const [v, setV] = useState<Values>({
    name: "",
    email: "",
    dial: "+251",
    phone: "",
    password: "",
    confirm: "",
    journey: "candidate",
    terms: false,
  });
  const [touched, setTouched] = useState<Partial<Record<Key, boolean>>>({});
  const errors = useMemo(() => validate(v, mode, t), [v, mode, t]);
  const set = <K extends keyof Values>(k: K, val: Values[K]) =>
    setV((p) => ({ ...p, [k]: val }));
  const touch = (k: Key) => setTouched((p) => ({ ...p, [k]: true }));
  const err = (k: Key) => (touched[k] ? errors[k] : undefined);
  const id = (k: string) => `ax-${mode}-${k}`;
  const level = strength(v.password);
  const national = digits(v.phone);
  const phoneValue = national ? `${v.dial}${national}` : "";

  function onSubmit(e: FormEvent<HTMLFormElement>) {
    const keys = Object.keys(errors) as Key[];
    if (!keys.length) return;
    e.preventDefault();
    setTouched({
      name: true,
      email: true,
      phone: true,
      password: true,
      confirm: true,
      terms: true,
    });
    document.getElementById(id(keys[0]))?.focus();
  }

  const noticeText =
    notice === "password-updated"
      ? t.pwUpdated
      : notice === "signed-out"
        ? t.signedOut
        : null;

  return (
    <section className={`ax ax-${mode} sera-web-auth`} aria-labelledby="ax-title">
      <div className="ax-brand-panel" aria-hidden="true"><div className="ax-brand-panel-mark"><SeraBrandMark/><span>Sera</span></div><div><h2>{locale==="om"?"Hojii kee. Namoota kee.":lang==="am"?"ሥራዎ። የእርስዎ ሰዎች።":"Your work. Your people."}</h2><p>{signup?t.signupSub:t.signinSub}</p></div><div className="ax-brand-panel-lines"/></div>
      <div className="ax-card">
        <header className="ax-head">
          <Link href="/" className="ax-brand" aria-label="Sera home">
            <SeraBrandMark/> Sera
          </Link>
          <h1 id="ax-title">{signup ? t.signupTitle : t.signinTitle}</h1>
          <p>{signup ? t.signupSub : t.signinSub}</p>
        </header>

        {noticeText && (
          <p className="ax-alert ok" role="status">
            <CircleCheck size={17} aria-hidden="true" />
            {noticeText}
          </p>
        )}
        {signup && !registrationOpen && (
          <p className="ax-alert info" role="status">
            <CircleAlert size={17} aria-hidden="true" />
            {t.closed}
          </p>
        )}

        <form
          action={action}
          onSubmit={onSubmit}
          noValidate
          className="ax-form"
          aria-busy={pending}
        >
          {signup && (
            <fieldset className="ax-type">
              <legend>{t.accountType}</legend>
              <label>
                <input
                  type="radio"
                  name="journey"
                  value="candidate"
                  checked={v.journey === "candidate"}
                  onChange={() => set("journey", "candidate")}
                />
                <span className="ax-type-card">
                  <Search size={18} aria-hidden="true" />
                  <b>{t.seeker}</b>
                  <small>{t.seekerSub}</small>
                </span>
              </label>
              <label>
                <input
                  type="radio"
                  name="journey"
                  value="employer"
                  checked={v.journey === "employer"}
                  onChange={() => set("journey", "employer")}
                />
                <span className="ax-type-card">
                  <BriefcaseBusiness size={18} aria-hidden="true" />
                  <b>{t.employer}</b>
                  <small>{t.employerSub}</small>
                </span>
              </label>
            </fieldset>
          )}

          {signup && (
            <div className="ax-field">
              <label htmlFor={id("name")}>{t.name}</label>
              <span className="ax-input">
                <UserRound className="ax-ico" size={18} aria-hidden="true" />
                <input
                  id={id("name")}
                  name="full_name"
                  value={v.name}
                  onChange={(e) => set("name", e.target.value)}
                  onBlur={() => touch("name")}
                  placeholder={t.namePh}
                  autoComplete="name"
                  autoCapitalize="words"
                  enterKeyHint="next"
                  maxLength={100}
                  aria-invalid={Boolean(err("name"))}
                  aria-describedby={err("name") ? `${id("name")}-err` : undefined}
                />
              </span>
              <FieldError id={id("name")} text={err("name")} />
            </div>
          )}

          <div className="ax-field">
            <label htmlFor={id("email")}>{t.email}</label>
            <span className="ax-input">
              <Mail className="ax-ico" size={18} aria-hidden="true" />
              <input
                id={id("email")}
                name="email"
                type="email"
                inputMode="email"
                value={v.email}
                onChange={(e) => set("email", e.target.value)}
                onBlur={() => touch("email")}
                placeholder="you@example.com"
                autoComplete={signup ? "email" : "username"}
                autoCapitalize="none"
                autoCorrect="off"
                spellCheck={false}
                enterKeyHint="next"
                maxLength={254}
                aria-invalid={Boolean(err("email"))}
                aria-describedby={err("email") ? `${id("email")}-err` : undefined}
              />
            </span>
            <FieldError id={id("email")} text={err("email")} />
          </div>

          {signup && (
            <div className="ax-field">
              <label htmlFor={id("phone")}>
                {t.phone} <em>({t.phoneOpt})</em>
              </label>
              <div className="ax-phone">
                <select
                  aria-label={t.country}
                  value={v.dial}
                  onChange={(e) => set("dial", e.target.value)}
                  className="ax-dial"
                >
                  {COUNTRIES.map(([flag, iso, dial]) => (
                    <option key={iso} value={dial}>
                      {flag} {iso} {dial}
                    </option>
                  ))}
                </select>
                <span className="ax-input">
                  <Phone className="ax-ico" size={18} aria-hidden="true" />
                  <input
                    id={id("phone")}
                    type="tel"
                    inputMode="tel"
                    value={v.phone}
                    onChange={(e) =>
                      set("phone", e.target.value.replace(/[^\d\s]/g, ""))
                    }
                    onBlur={() => touch("phone")}
                    placeholder="911 234 567"
                    autoComplete="tel-national"
                    enterKeyHint="next"
                    maxLength={16}
                    aria-invalid={Boolean(err("phone"))}
                    aria-describedby={err("phone") ? `${id("phone")}-err` : undefined}
                  />
                </span>
              </div>
              <input type="hidden" name="phone" value={phoneValue} />
              <FieldError id={id("phone")} text={err("phone")} />
            </div>
          )}

          <div className="ax-field">
            <div className="ax-row">
              <label htmlFor={id("password")}>{t.password}</label>
              {!signup && (
                <Link href="/forgot-password" className="ax-link">
                  {t.forgot}
                </Link>
              )}
            </div>
            <PasswordInput
              id={id("password")}
              name="password"
              value={v.password}
              onChange={(x) => set("password", x)}
              onBlur={() => touch("password")}
              placeholder={signup ? t.newPasswordPh : t.passwordPh}
              autoComplete={signup ? "new-password" : "current-password"}
              invalid={Boolean(err("password"))}
              describedBy={err("password") ? `${id("password")}-err` : undefined}
              show={t.show}
              hide={t.hide}
              enterKeyHint={signup ? "next" : "go"}
            />
            {signup && (
              <div className="ax-meter" data-level={level} aria-live="polite">
                <span aria-hidden="true">
                  <i />
                  <i />
                  <i />
                  <i />
                </span>
                <small>{t.strength[level]}</small>
              </div>
            )}
            <FieldError id={id("password")} text={err("password")} />
          </div>

          {signup && (
            <div className="ax-field">
              <label htmlFor={id("confirm")}>{t.confirm}</label>
              <PasswordInput
                id={id("confirm")}
                name="confirm_password"
                value={v.confirm}
                onChange={(x) => set("confirm", x)}
                onBlur={() => touch("confirm")}
                placeholder={t.confirmPh}
                autoComplete="new-password"
                invalid={Boolean(err("confirm"))}
                describedBy={err("confirm") ? `${id("confirm")}-err` : undefined}
                show={t.show}
                hide={t.hide}
                enterKeyHint="done"
              />
              <FieldError id={id("confirm")} text={err("confirm")} />
            </div>
          )}

          {signup && (
            <div className="ax-field">
              <label className="ax-consent" htmlFor={id("terms")}>
                <input
                  id={id("terms")}
                  type="checkbox"
                  name="accept_terms"
                  value="yes"
                  checked={v.terms}
                  onChange={(e) => {
                    set("terms", e.target.checked);
                    touch("terms");
                  }}
                  aria-invalid={Boolean(err("terms"))}
                  aria-describedby={err("terms") ? `${id("terms")}-err` : undefined}
                />
                <span>
                  {t.agree}{" "}
                  <Link href="/terms" target="_blank" rel="noopener">
                    {t.terms}
                  </Link>{" "}
                  {t.and}{" "}
                  <Link href="/privacy" target="_blank" rel="noopener">
                    {t.privacy}
                  </Link>
                  {lang === "am" ? " ተቀብያለሁ።" : "."}
                </span>
              </label>
              <FieldError id={id("terms")} text={err("terms")} />
            </div>
          )}

          <input type="hidden" name="next" value={destination || "/dashboard"} />
          {signup && (
            <>
              <input type="hidden" name="locale" value={locale} />
              <label className="ax-hp" aria-hidden="true">
                Website
                <input name="website" tabIndex={-1} autoComplete="off" />
              </label>
            </>
          )}

          {state.message && (
            <p
              className={`ax-alert ${state.success ? "ok" : "err"}`}
              role={state.success ? "status" : "alert"}
              lang="en"
            >
              {state.success ? (
                <CircleCheck size={17} aria-hidden="true" />
              ) : (
                <CircleAlert size={17} aria-hidden="true" />
              )}
              {state.message}
            </p>
          )}

          <button
            className="ax-primary"
            disabled={busy || (signup && !registrationOpen)}
          >
            {pending && <LoaderCircle className="ax-spin" size={18} />}
            {pending
              ? signup
                ? t.creating
                : t.signingIn
              : signup
                ? t.create
                : t.signIn}
            {!pending && <ArrowRight size={18} aria-hidden="true" />}
          </button>
        </form>

        <div className="ax-divider" role="separator">
          <span>{t.or}</span>
        </div>

        <div className="ax-social">
          <form action={gAction} aria-busy={gPending}>
            <button className="ax-sbtn ax-google" disabled={busy}>
              {gPending ? <LoaderCircle className="ax-spin" size={18} /> : <GoogleMark />}
              {gPending ? t.connectingGoogle : t.google}
            </button>
            {gState.message && (
              <p className="ax-alert err" role="alert">
                <CircleAlert size={17} aria-hidden="true" />
                {liMessage(lang, gState.message)}
              </p>
            )}
          </form>
          <form action={lAction} aria-busy={lPending}>
            <button className="ax-sbtn ax-linkedin" disabled={busy}>
              {lPending ? <LoaderCircle className="ax-spin" size={18} /> : <LinkedInMark />}
              {lPending ? t.connectingLinkedin : t.linkedin}
            </button>
            {lState.message && (
              <p className="ax-alert err" role="alert">
                <CircleAlert size={17} aria-hidden="true" />
                {liMessage(lang, lState.message)}
              </p>
            )}
          </form>
        </div>

        <p className="ax-switch">
          {signup ? t.haveAccount : t.newHere}{" "}
          <Link href={signup ? "/sign-in" : "/sign-up"}>
            {signup ? t.signIn : t.create}
          </Link>
        </p>
        <p className="ax-secure">
          <LockKeyhole size={13} aria-hidden="true" />
          {t.secure}
        </p>
      </div>
    </section>
  );
}
