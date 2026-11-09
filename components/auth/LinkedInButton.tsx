"use client";
import { useActionState } from "react";
import Link from "next/link";
import { LoaderCircle } from "lucide-react";
import { continueWithLinkedIn } from "@/lib/auth/linkedin";
import { continueWithGoogle } from "@/lib/auth/google";
import type { ActionState } from "@/lib/auth/actions";
import { useSera } from "../store";
import { li, liMessage } from "@/lib/auth/linkedin-copy";

const base = {
  display: "flex",
  width: "100%",
  alignItems: "center",
  justifyContent: "center",
  gap: 10,
  minHeight: 48,
  borderRadius: 999,
  font: "inherit",
  fontWeight: 600,
} as const;

export default function LinkedInButton({ mode }: { mode: "signin" | "signup" }) {
  const [liState, liAction, liPending] = useActionState<ActionState, FormData>(
    continueWithLinkedIn,
    {},
  );
  const [gState, gAction, gPending] = useActionState<ActionState, FormData>(
    continueWithGoogle,
    {},
  );
  const { lang } = useSera();
  const t = li[lang];
  return (
    <div style={{ marginTop: 16, display: "grid", gap: 12 }}>
      <p className="auth-help" style={{ textAlign: "center", margin: 0 }}>
        {t.or}
      </p>
      <form action={gAction} aria-busy={gPending}>
        <button
          disabled={gPending}
          style={{
            ...base,
            border: "1px solid #dadce0",
            background: "#fff",
            color: "#1f1f1f",
            cursor: gPending ? "wait" : "pointer",
          }}
        >
          {gPending ? (
            <LoaderCircle className="auth-spinner" size={18} />
          ) : (
            <svg width="20" height="20" viewBox="0 0 48 48" aria-hidden="true">
              <path fill="#EA4335" d="M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z" />
              <path fill="#4285F4" d="M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z" />
              <path fill="#FBBC05" d="M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z" />
              <path fill="#34A853" d="M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z" />
            </svg>
          )}
          {t.continueGoogle}
        </button>
        {gState.message && (
          <p className="auth-feedback" role="alert">
            {liMessage(lang, gState.message)}
          </p>
        )}
      </form>
      <form action={liAction} aria-busy={liPending}>
        <button
          disabled={liPending}
          style={{
            ...base,
            border: 0,
            background: "#0A66C2",
            color: "#fff",
            cursor: liPending ? "wait" : "pointer",
          }}
        >
          {liPending ? (
            <LoaderCircle className="auth-spinner" size={18} />
          ) : (
            <svg width="20" height="20" viewBox="0 0 24 24" aria-hidden="true" fill="currentColor">
              <path d="M20.45 20.45h-3.56v-5.57c0-1.33-.03-3.04-1.85-3.04-1.86 0-2.14 1.45-2.14 2.94v5.67H9.35V9h3.41v1.56h.05c.48-.9 1.64-1.85 3.37-1.85 3.6 0 4.27 2.37 4.27 5.46v6.28zM5.34 7.43a2.06 2.06 0 1 1 0-4.12 2.06 2.06 0 0 1 0 4.12zM7.12 20.45H3.56V9h3.56v11.45z" />
            </svg>
          )}
          {t.continueWith}
        </button>
        {liState.message && (
          <p className="auth-feedback" role="alert">
            {liMessage(lang, liState.message)}
          </p>
        )}
      </form>
      {mode === "signup" && (
        <p className="auth-help" style={{ textAlign: "center", margin: 0 }}>
          {t.agreePre}
          <Link href="/terms" target="_blank" rel="noopener">{t.terms}</Link>
          {t.and}
          <Link href="/privacy" target="_blank" rel="noopener">{t.privacy}</Link>
          {lang === "am" ? " ይስማማሉ።" : "."}
        </p>
      )}
    </div>
  );
}
