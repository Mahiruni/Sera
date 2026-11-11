"use client";

import { FormEvent, useState } from "react";
import Link from "next/link";
import { Building2, ArrowRight, CheckCircle2, LoaderCircle } from "lucide-react";
import { normalizeOrganizationSlug, validateOrganizationDraft } from "@/lib/organizations/validation";
import styles from "./page.module.css";

export default function CreateOrganizationPage() {
  const [kind, setKind] = useState<"company" | "organization">("company");
  const [name, setName] = useState("");
  const [slug, setSlug] = useState("");
  const [slugEdited, setSlugEdited] = useState(false);
  const [city, setCity] = useState("");
  const [industry, setIndustry] = useState("");
  const [description, setDescription] = useState("");
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [message, setMessage] = useState("");
  const [created, setCreated] = useState(false);
  const [busy, setBusy] = useState(false);
  const handleName = (value: string) => {
    setName(value);
    if (!slugEdited) setSlug(normalizeOrganizationSlug(value));
  };
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy) return;
    setMessage("");
    const validated = validateOrganizationDraft({ kind, name, slug, city, industry, description });
    if (!validated.ok) { setErrors(validated.errors); return; }
    setErrors({});
    setBusy(true);
    try {
      const response = await fetch("/api/organizations", {
        method: "POST",
        credentials: "same-origin",
        headers: { "content-type": "application/json" },
        body: JSON.stringify(validated.value),
      });
      const payload = await response.json();
      if (!response.ok) {
        if (payload.errors) setErrors(payload.errors);
        else setMessage(payload.error || "Unable to create your organization.");
        return;
      }
      setCreated(true);
    } catch {
      setMessage("Connection interrupted. Please try again.");
    } finally { setBusy(false); }
  }
  return (
    <main className={styles.page}>
      <div className={styles.shell}>
        <div className={styles.intro}>
          <span className={styles.symbol}><Building2 size={24} strokeWidth={1.7} /></span>
          <span className={styles.kicker}>SERA BUSINESS · ETHIOPIA</span>
          <h1>Build your presence.<br /><span>Find your people.</span></h1>
          <p>Give your business or organization a professional home on SERA. Connect with talent, partners, and opportunities.</p>
          <div className={styles.promise}><CheckCircle2 size={18} /> Your organization starts unverified. Verification is reviewed separately.</div>
        </div>
        <section className={styles.card} aria-labelledby="create-org-heading">
          {created ? (
            <div className={styles.success} role="status">
              <CheckCircle2 size={38} />
              <h2 id="create-org-heading">Your organization is created</h2>
              <p>Your business identity is registered. Profile management and account switching are coming in the next development milestone.</p>
              <Link href="/account" className={styles.primary}>Back to your account <ArrowRight size={17} /></Link>
            </div>
          ) : (
            <>
              <div className={styles.cardTop}><span>01 / CREATE IDENTITY</span><span>ETHIOPIA FIRST</span></div>
              <h2 id="create-org-heading">Create an organization</h2>
              <p className={styles.helper}>Use the professional name your partners will recognize.</p>
              <form onSubmit={submit} noValidate>
                <fieldset className={styles.kindSet}>
                  <legend>Account type</legend>
                  <div className={styles.kindGrid}>
                    {(["company", "organization"] as const).map((option) => (
                      <label key={option} className={kind === option ? styles.selected : styles.option}>
                        <input type="radio" name="kind" checked={kind === option} onChange={() => setKind(option)} />
                        <span>{option === "company" ? "Company" : "Organization"}</span>
                      </label>
                    ))}
                  </div>
                  {errors.kind && <small role="alert">{errors.kind}</small>}
                </fieldset>
                <label className={styles.field}>Official name
                  <input required maxLength={160} value={name} onChange={(e) => handleName(e.target.value)} placeholder="e.g. Hisab Technologies" aria-invalid={Boolean(errors.name)} />
                  {errors.name && <small role="alert">{errors.name}</small>}
                </label>
                <label className={styles.field}>SERA address
                  <div className={styles.slugField}><span>sera.et/business/</span><input required maxLength={80} value={slug} onChange={(e) => {setSlugEdited(true);setSlug(e.target.value);}} placeholder="your-company" aria-invalid={Boolean(errors.slug)} /></div>
                  {errors.slug && <small role="alert">{errors.slug}</small>}
                </label>
                <div className={styles.row}>
                  <label className={styles.field}>City<input maxLength={120} value={city} onChange={(e) => setCity(e.target.value)} placeholder="Addis Ababa" />{errors.city && <small role="alert">{errors.city}</small>}</label>
                  <label className={styles.field}>Industry<input maxLength={120} value={industry} onChange={(e) => setIndustry(e.target.value)} placeholder="Technology" />{errors.industry && <small role="alert">{errors.industry}</small>}</label>
                </div>
                <label className={styles.field}>About your organization <span className={styles.optional}>(optional)</span>
                  <textarea maxLength={2000} rows={4} value={description} onChange={(e) => setDescription(e.target.value)} placeholder="What do you do, and who do you serve?" />
                  {errors.description && <small role="alert">{errors.description}</small>}
                </label>
                {message && <p className={styles.error} role="alert">{message}</p>}
                <button className={styles.primary} type="submit" disabled={busy}>
                  {busy ? <><LoaderCircle size={17} className={styles.spin} /> Creating…</> : <>Create organization <ArrowRight size={17} /></>}
                </button>
                <p className={styles.note}>You will need a verified SERA account. Creating an organization does not grant a verification badge.</p>
              </form>
            </>
          )}
        </section>
      </div>
    </main>
  );
}
