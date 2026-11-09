"use client";
import { useActionState } from "react";
import {
  applyLinkedInImport,
  connectLinkedIn,
  disconnectLinkedIn,
} from "@/lib/auth/linkedin";
import type { ActionState } from "@/lib/auth/actions";
import LinkedInExportImport from "./LinkedInExportImport";

export type PanelProps = {
  connected: boolean;
  onlyMethod: boolean;
  name: string | null;
  picture: string | null;
  email: string | null;
  profileName: string;
  hasAvatar: boolean;
  importedAt: string | null;
  importedFields: string[];
  justImported: boolean;
  connectError: boolean;
  current: Record<string, string>;
};

const labels: Record<string, string> = { full_name: "name", avatar: "photo", headline: "headline", summary: "about", location: "location", skills: "skills", experience: "experience", education: "education" };
const box = { width: 22, height: 22, minHeight: 0, padding: 0, margin: 0, flex: "none" } as const;

export default function LinkedInPanel(p: PanelProps) {
  const [imp, importAction, importing] = useActionState<ActionState, FormData>(applyLinkedInImport, {});
  const [dis, disconnectAction, disconnecting] = useActionState<ActionState, FormData>(disconnectLinkedIn, {});
  const nameDiffers = Boolean(p.name) && p.name !== p.profileName;
  return (
    <div className="panel auth-form" style={{ display: "grid", gap: 16 }}>
      <h1 style={{ margin: 0 }}>LinkedIn</h1>
      <p className="auth-help" role="status">
        Status: <strong>{p.connected ? "Connected" : "Not connected"}</strong>
      </p>
      {p.connectError && (
        <p className="auth-feedback" role="alert">
          LinkedIn could not be connected. Please try again.
        </p>
      )}
      {p.justImported && (
        <p className="auth-feedback success" role="status">
          Imported. You can edit everything in your profile.
        </p>
      )}

      {!p.connected && (
        <form action={connectLinkedIn}>
          <p className="auth-help">
            Connect LinkedIn to bring your name and photo into your Sera profile. You choose what is imported.
          </p>
          <button className="button" style={{ background: "#0A66C2" }}>Connect LinkedIn</button>
        </form>
      )}

      {p.connected && (
        <form action={importAction} aria-busy={importing} style={{ display: "grid", gap: 12 }}>
          <h2 style={{ margin: 0, fontSize: "1.05rem" }}>Build your Sera profile with LinkedIn</h2>
          {p.name && (
            <label className="auth-help" style={{ display: "flex", gap: 10, alignItems: "center" }}>
              <input type="checkbox" name="field" value="full_name" defaultChecked={!p.profileName || !nameDiffers} style={box} />
              <span>
                Name: <strong>{p.name}</strong>
                {nameDiffers && p.profileName ? ` (replaces “${p.profileName}”)` : ""}
              </span>
            </label>
          )}
          {p.picture && (
            <label className="auth-help" style={{ display: "flex", gap: 10, alignItems: "center" }}>
              <input type="checkbox" name="field" value="avatar" defaultChecked={!p.hasAvatar} style={box} />
              {/* eslint-disable-next-line @next/next/no-img-element */}
              <img src={p.picture} alt="" width={40} height={40} style={{ borderRadius: 999 }} referrerPolicy="no-referrer" />
              <span>Profile photo{p.hasAvatar ? " (replaces your current photo)" : ""}</span>
            </label>
          )}
          {p.email && (
            <p className="auth-help">Email: {p.email} — shown for reference; your account email is not changed.</p>
          )}
          <p className="auth-help">
            LinkedIn’s standard sign-in does not share your headline, position, company, location, education or work history. Add those in your profile or upload your CV.
          </p>
          {imp.message && <p className="auth-feedback" role="alert">{imp.message}</p>}
          <button className="button" disabled={importing}>
            {importing ? "Importing…" : "Import selected"}
          </button>
          {p.importedAt && (
            <p className="auth-help">
              Last import: {new Date(p.importedAt).toLocaleString()}
              {p.importedFields.length
                ? ` · imported from LinkedIn: ${p.importedFields.map((f) => labels[f] ?? f).join(", ")}`
                : ""}
            </p>
          )}
        </form>
      )}

      {p.connected && <LinkedInExportImport current={p.current} />}

      {p.connected && (
        <form action={disconnectAction} aria-busy={disconnecting}>
          {dis.message && (
            <p className={`auth-feedback ${dis.success ? "success" : ""}`} role={dis.success ? "status" : "alert"}>
              {dis.message}
            </p>
          )}
          <button className="button" disabled={disconnecting || p.onlyMethod} style={{ background: "transparent", color: "inherit", border: "1px solid currentColor" }}>
            Disconnect LinkedIn
          </button>
          {p.onlyMethod && (
            <p className="auth-help">LinkedIn is currently your only sign-in method, so it can’t be removed yet.</p>
          )}
        </form>
      )}
    </div>
  );
}
