"use client";
import { FormEvent, useState } from "react";
import { Building2, Camera, CheckCircle2, LoaderCircle, Save } from "lucide-react";
import styles from "./company-profile-editor.module.css";
type CompanyDetails = {
  street_address?: string; region?: string; phone_public?: string; email_public?: string;
  website_url?: string; services?: string; business_hours?: string; founded_year?: number | null;
  company_size?: string; linkedin_url?: string;
};
const labels: { key: keyof CompanyDetails; label: string; placeholder: string; type?: string }[] = [
  { key: "street_address", label: "Street and office address", placeholder: "Street, building and office" },
  { key: "region", label: "Region", placeholder: "Addis Ababa" },
  { key: "phone_public", label: "Public phone", placeholder: "+251..." ,type:"tel" },
  { key: "email_public", label: "Public business email", placeholder: "contact@company.et",type:"email" },
  { key: "website_url", label: "Official website", placeholder: "https://company.et",type:"url" },
  { key: "linkedin_url", label: "LinkedIn page", placeholder: "https://linkedin.com/company/...",type:"url" },
  { key: "business_hours", label: "Business hours", placeholder: "Mon–Fri, 9:00–17:00" },
  { key: "services", label: "Services and specialties", placeholder: "Tell professionals what your organization offers" },
];
export function CompanyProfileEditor({
  organizationId, initial, logoUrl, coverUrl,
}: {
  organizationId: string; initial: CompanyDetails; logoUrl: string | null; coverUrl: string | null;
}) {
  const [values, setValues] = useState<CompanyDetails>(initial);
  const [logo, setLogo] = useState(logoUrl);
  const [cover, setCover] = useState(coverUrl);
  const [busy, setBusy] = useState(false);
  const [uploading, setUploading] = useState<"logo" | "cover" | null>(null);
  const [notice, setNotice] = useState("");
  async function save(e: FormEvent<HTMLFormElement>) {
    e.preventDefault(); setNotice(""); setBusy(true);
    try {
      const r = await fetch(`/api/organizations/${organizationId}/details`, {
        method:"PATCH",headers:{"Content-Type":"application/json"},body:JSON.stringify(values),
      });
      const body = await r.json();
      setNotice(r.ok ? "Company details saved." : (body.error || "Could not save details."));
    } catch { setNotice("Connection interrupted. Please retry."); }
    finally { setBusy(false); }
  }
  async function upload(kind: "logo" | "cover", file?: File) {
    if (!file) return; setUploading(kind); setNotice("");
    try {
      const form = new FormData(); form.set("kind",kind); form.set("file",file);
      const r = await fetch(`/api/organizations/${organizationId}/media`, {method:"POST",body:form});
      const body = await r.json();
      if (!r.ok) { setNotice(body.error || "Image upload failed."); return; }
      if (kind === "logo") setLogo(body.url); else setCover(body.url);
      setNotice(kind === "logo" ? "Company logo saved." : "Cover image saved.");
    } catch { setNotice("Upload interrupted. Please retry."); }
    finally { setUploading(null); }
  }
  return <section className={styles.panel} aria-labelledby="company-edit-title">
    <div className={styles.heading}><span className={styles.kicker}>BRAND STUDIO · COMPANY SETTINGS</span><h2 id="company-edit-title">Make your company recognizable.</h2><p>Show your own brand while keeping the SERA experience consistent.</p></div>
    <div className={styles.media}>
      <div className={styles.cover} style={cover ? { backgroundImage:`url("${cover}")` } : undefined}>
        {!cover && <span>YOUR COMPANY · YOUR IDENTITY</span>}
        <label className={styles.upload}><Camera size={16}/>{uploading === "cover" ? "Uploading…" : "Change cover"}<input type="file" accept="image/png,image/jpeg,image/webp" disabled={uploading !== null} onChange={e => upload("cover",e.target.files?.[0])} /></label>
      </div>
      <div className={styles.logoRow}><span className={styles.logo}>
        {logo ? <img src={logo} alt="Company logo" /> : <Building2 size={27} aria-label="Company logo placeholder" />}
      </span><label className={styles.logoUpload}><Camera size={16}/>{uploading === "logo" ? "Uploading…" : "Upload logo"}<input type="file" accept="image/png,image/jpeg,image/webp" disabled={uploading !== null} onChange={e => upload("logo",e.target.files?.[0])} /></label></div>
    </div>
    <form onSubmit={save} className={styles.form}>
      {labels.map(({key,label,placeholder,type}) => <label key={key} className={styles.field}>{label}
        {key === "services" ? <textarea rows={4} maxLength={2000} value={String(values[key] || "")} placeholder={placeholder} onChange={e=>setValues(v=>({...v,[key]:e.target.value}))}/> :
          <input type={type || "text"} maxLength={key === "street_address"?300:key === "website_url"||key==="linkedin_url"?400:key==="email_public"?254:key==="business_hours"?350:key==="phone_public"?40:120} value={String(values[key] || "")} placeholder={placeholder} onChange={e=>setValues(v=>({...v,[key]:e.target.value}))} />}
      </label>)}
      <label className={styles.field}>Founded year<input type="number" min="1800" max={new Date().getFullYear()} value={values.founded_year ?? ""} onChange={e=>setValues(v=>({...v,founded_year:e.target.value?Number(e.target.value):null}))}/></label>
      <label className={styles.field}>Company size<select value={values.company_size || ""} onChange={e=>setValues(v=>({...v,company_size:e.target.value}))}>
        <option value="">Not specified</option>{["1-10","11-50","51-200","201-500","501-1000","1000+"].map(s=><option key={s} value={s}>{s} employees</option>)}
      </select></label>
      <p className={styles.privacy}>Only approved owners and admins can edit this profile. Public contact details are separate from private founder email and team membership records.</p>
      <button className={styles.save} disabled={busy}>{busy?<LoaderCircle size={17}/>:<Save size={17}/>} {busy?"Saving…":"Save company profile"}</button>
      {notice && <p className={styles.notice} role="status"><CheckCircle2 size={17}/>{notice}</p>}
    </form>
  </section>;
}
