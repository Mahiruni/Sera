"use client";
import { useActionState, useState } from "react";
import { parseLinkedInExport, type ExportResult } from "@/lib/linkedin-export";
import { saveLinkedInExport } from "@/lib/auth/linkedin";
import type { ActionState } from "@/lib/auth/actions";
import { useSera } from "../store";
import { li, liFieldTitles, liMessage } from "@/lib/auth/linkedin-copy";

const KEYS = ["headline", "summary", "location", "skills", "experience", "education"] as const;
const box = { width: 22, height: 22, minHeight: 0, padding: 0, margin: 0, flex: "none" } as const;

export default function LinkedInExportImport({ current }: { current: Record<string, string> }) {
  const { lang } = useSera();
  const t = li[lang];
  const titles = liFieldTitles[lang];
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
        setError(t.exEmpty);
      else setResult(r);
    } catch {
      setError(t.exBad);
    } finally {
      setReading(false);
    }
  }

  return (
    <div style={{ display: "grid", gap: 12 }}>
      <h2 style={{ margin: 0, fontSize: "1.05rem" }}>{t.exTitle}</h2>
      <p className="auth-help">{t.exHelp}</p>
      <input type="file" accept=".zip,application/zip" onChange={onFile} aria-label={t.exFile} />
      {reading && <p className="auth-help" role="status">{t.reading}</p>}
      {error && <p className="auth-feedback" role="alert">{error}</p>}
      {result && (
        <form action={action} aria-busy={saving} style={{ display: "grid", gap: 14 }}>
          {KEYS.filter((k) => result.fields[k]).map((k) => (
            <div key={k} style={{ display: "grid", gap: 6 }}>
              <label className="auth-help" style={{ display: "flex", gap: 10, alignItems: "center" }}>
                <input type="checkbox" name="field" value={k} defaultChecked={!current[k]} style={box} />
                <span>
                  <strong>{titles[k]}</strong>
                  {current[k] ? t.replacesNow : ""}
                  {result.truncated.includes(k) ? t.truncated : ""}
                </span>
              </label>
              <textarea name={`v_${k}`} defaultValue={result.fields[k]} rows={k === "summary" || k === "education" || k === "experience" ? 5 : 2} />
            </div>
          ))}
          {state.message && <p className="auth-feedback" role="alert">{liMessage(lang, state.message)}</p>}
          <button className="button" disabled={saving}>{saving ? t.saving : t.save}</button>
        </form>
      )}
    </div>
  );
}
