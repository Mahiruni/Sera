"use client";
import { useActionState } from "react";
import Link from "next/link";
import { LoaderCircle } from "lucide-react";
import { continueWithLinkedIn } from "@/lib/auth/linkedin";
import type { ActionState } from "@/lib/auth/actions";
import { useSera } from "../store";
import { li, liMessage } from "@/lib/auth/linkedin-copy";

export default function LinkedInButton({ mode }: { mode: "signin" | "signup" }) {
  const [state, action, pending] = useActionState<ActionState, FormData>(
    continueWithLinkedIn,
    {},
  );
  const { lang } = useSera();
  const t = li[lang];
  return (
    <form action={action} aria-busy={pending} style={{ marginTop: 16 }}>
      <p className="auth-help" style={{ textAlign: "center", margin: "0 0 12px" }}>
        {t.or}
      </p>
      <button
        disabled={pending}
        style={{
          display: "flex",
          width: "100%",
          alignItems: "center",
          justifyContent: "center",
          gap: 10,
          minHeight: 48,
          border: 0,
          borderRadius: 999,
          background: "#0A66C2",
          color: "#fff",
          font: "inherit",
          fontWeight: 600,
          cursor: pending ? "wait" : "pointer",
        }}
      >
        {pending ? (
          <LoaderCircle className="auth-spinner" size={18} />
        ) : (
          <svg width="20" height="20" viewBox="0 0 24 24" aria-hidden="true" fill="currentColor">
            <path d="M20.45 20.45h-3.56v-5.57c0-1.33-.03-3.04-1.85-3.04-1.86 0-2.14 1.45-2.14 2.94v5.67H9.35V9h3.41v1.56h.05c.48-.9 1.64-1.85 3.37-1.85 3.6 0 4.27 2.37 4.27 5.46v6.28zM5.34 7.43a2.06 2.06 0 1 1 0-4.12 2.06 2.06 0 0 1 0 4.12zM7.12 20.45H3.56V9h3.56v11.45z" />
          </svg>
        )}
        {t.continueWith}
      </button>
      {mode === "signup" && (
        <p className="auth-help" style={{ textAlign: "center" }}>
          {t.agreePre}
          <Link href="/terms" target="_blank" rel="noopener">{t.terms}</Link>
          {t.and}
          <Link href="/privacy" target="_blank" rel="noopener">{t.privacy}</Link>
          {lang === "am" ? " ይስማማሉ።" : "."}
        </p>
      )}
      {state.message && (
        <p className="auth-feedback" role="alert">
          {liMessage(lang, state.message)}
        </p>
      )}
    </form>
  );
}
