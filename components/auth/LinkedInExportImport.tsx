"use client";
import { useActionState, useState } from "react";
import { parseLinkedInExport, type ExportResult } from "@/lib/linkedin-export";
import { saveLinkedInExport } from "@/lib/auth/linkedin";
import type { ActionState } from "@/lib/auth/actions";

const FIELDS = [
  ["headline", "Headline"],
  ["summary", "About"],
  ["location", "Location"],
  ["skills", "Skills"],
  ["experience", "Experience"],
  ["education", "Education"],
] as const;
const box = { width: 22, height: 22, minHeight: 0, padding: 0, margin: 0, flex: "none" } as const;

export default function LinkedInExportImport({ current }: { current: Record<string, string> }) {
  const [result, setResult] = useState<ExportResult | null>(null);
  const [error, setError] = useState("");
  const [reading, setReading] = useState(false);
  const [state, action, saving] = useActionState<ActionState, FormData>(saveLinkedInExport, {});

  async function onFile(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (!file) return;
    setReading(true);
    setError("");
    setResult(null);
    try {
      const r = await parseLinkedInExport(file);
      if (!Object.keys(r.fields).length)
        setError("No profile data found. Request the Profile, Positions, Education and Skills items from LinkedIn.");
      else setResult(r);
    } catch {
      setError("Could not read this file. Choose the .zip that LinkedIn sent you.");
    } finally {
      setReading(false);
    }
  }

  return (
    <div style={{ display: "grid", gap: 12 }}>
      <h2 style={{ margin: 0, fontSize: "1.05rem" }}>Import more from your LinkedIn data</h2>
      <p className="auth-help">
        LinkedIn only shares name and photo at sign-in. For headline, experience, education and skills, request your data from LinkedIn
        (Settings &amp; Privacy → Data privacy → Get a copy of your data; choose Profile, Positions, Education and Skills), then choose the
        .zip it emails you. The file is read on your device; only the items you approve are saved.
      </p>
      <input type="file" accept=".zip,application/zip" onChange={onFile} aria-label="LinkedIn data export (.zip)" />
      {reading && <p className="auth-help" role="status">Reading…</p>}
      {error && <p className="auth-feedback" role="alert">{error}</p>}
      {result && (
        <form action={action} aria-busy={saving} style={{ display: "grid", gap: 14 }}>
          {FIELDS.filter(([k]) => result.fields[k]).map(([k, label]) => (
            <div key={k} style={{ display: "grid", gap: 6 }}>
              <label className="auth-help" style={{ display: "flex", gap: 10, alignItems: "center" }}>
                <input type="checkbox" name="field" value={k} defaultChecked={!current[k]} style={box} />
                <span>
                  <strong>{label}</strong>
                  {current[k] ? " — replaces what you have now" : ""}
                  {result.truncated.includes(k) ? " — shortened to fit; edit as needed" : ""}
                </span>
              </label>
              <textarea name={`v_${k}`} defaultValue={result.fields[k]} rows={k === "summary" || k === "education" || k === "experience" ? 5 : 2} />
            </div>
          ))}
          {state.message && <p className="auth-feedback" role="alert">{state.message}</p>}
          <button className="button" disabled={saving}>{saving ? "Saving…" : "Save selected"}</button>
        </form>
      )}
    </div>
  );
}
