"use client";
import { useActionState } from "react";
import {
  applyLinkedInImport,
  connectLinkedIn,
  disconnectLinkedIn,
} from "@/lib/auth/linkedin";
import type { ActionState } from "@/lib/auth/actions";
import LinkedInExportImport from "./LinkedInExportImport";
import { useSera } from "../store";
import { li, liFields, liMessage } from "@/lib/auth/linkedin-copy";

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

const box = { width: 22, height: 22, minHeight: 0, padding: 0, margin: 0, flex: "none" } as const;

export default function LinkedInPanel(p: PanelProps) {
  const [imp, importAction, importing] = useActionState<ActionState, FormData>(applyLinkedInImport, {});
  const [dis, disconnectAction, disconnecting] = useActionState<ActionState, FormData>(disconnectLinkedIn, {});
  const { lang } = useSera();
  const t = li[lang];
  const labels: Record<string, string> = liFields[lang];
  const nameDiffers = Boolean(p.name) && p.name !== p.profileName;
  return (
    <div className="panel auth-form" style={{ display: "grid", gap: 16 }}>
      <h1 style={{ margin: 0 }}>LinkedIn</h1>
      <p className="auth-help" role="status">
        {t.status} <strong>{p.connected ? t.connected : t.notConnected}</strong>
      </p>
      {p.connectError && (
        <p className="auth-feedback" role="alert">
          {t.connectError}
        </p>
      )}
      {p.justImported && (
        <p className="auth-feedback success" role="status">
          {t.justImported}
        </p>
      )}

      {!p.connected && (
        <form action={connectLinkedIn}>
          <p className="auth-help">
            {t.connectIntro}
          </p>
          <button className="button" style={{ background: "#0A66C2" }}>{t.connectBtn}</button>
        </form>
      )}

      {p.connected && (
        <form action={importAction} aria-busy={importing} style={{ display: "grid", gap: 12 }}>
          <h2 style={{ margin: 0, fontSize: "1.05rem" }}>{t.buildTitle}</h2>
          {p.name && (
            <label className="auth-help" style={{ display: "flex", gap: 10, alignItems: "center" }}>
              <input type="checkbox" name="field" value="full_name" defaultChecked={!p.profileName || !nameDiffers} style={box} />
              <span>
                {t.nameLabel} <strong>{p.name}</strong>
                {nameDiffers && p.profileName ? t.replaces(p.profileName) : ""}
              </span>
            </label>
          )}
          {p.picture && (
            <label className="auth-help" style={{ display: "flex", gap: 10, alignItems: "center" }}>
              <input type="checkbox" name="field" value="avatar" defaultChecked={!p.hasAvatar} style={box} />
              {/* eslint-disable-next-line @next/next/no-img-element */}
              <img src={p.picture} alt="" width={40} height={40} style={{ borderRadius: 999 }} referrerPolicy="no-referrer" />
              <span>{t.photo}{p.hasAvatar ? t.photoReplaces : ""}</span>
            </label>
          )}
          {p.email && (
            <p className="auth-help">{t.email(p.email)}</p>
          )}
          <p className="auth-help">
            {t.limit}
          </p>
          {imp.message && <p className="auth-feedback" role="alert">{liMessage(lang, imp.message)}</p>}
          <button className="button" disabled={importing}>
            {importing ? t.importing : t.importBtn}
          </button>
          {p.importedAt && (
            <p className="auth-help">
              {t.lastImport} {new Date(p.importedAt).toLocaleString()}
              {p.importedFields.length
                ? ` · ${t.importedFrom} ${p.importedFields.map((f) => labels[f] ?? f).join(", ")}`
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
              {liMessage(lang, dis.message)}
            </p>
          )}
          <button className="button" disabled={disconnecting || p.onlyMethod} style={{ background: "transparent", color: "inherit", border: "1px solid currentColor" }}>
            {t.disconnect}
          </button>
          {p.onlyMethod && (
            <p className="auth-help">{t.onlyMethod}</p>
          )}
        </form>
      )}
    </div>
  );
}
