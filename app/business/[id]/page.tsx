import Link from "next/link";
import { notFound, redirect } from "next/navigation";
import { ArrowLeft, Building2, ShieldCheck } from "lucide-react";
import { currentUser, serverClient } from "@/lib/supabase/server";
import { requireOrganizationMembership } from "@/lib/organizations/membership";
import styles from "../page.module.css";

export const metadata = { title: "Business workspace | Sera.et", robots: { index: false, follow: false } };

export default async function BusinessWorkspacePage({
  params,
}: {
  params: Promise<{ id: string }>;
}) {
  const user = await currentUser();
  const { id } = await params;
  if (!user?.email_confirmed_at) redirect("/sign-in?next=%2Fbusiness");
  const membership = await requireOrganizationMembership(id);
  if (!membership) notFound();
  const db = await serverClient();
  const { data: organization, error } = await db.from("sera_organizations")
    .select("id,name,kind,slug,description,city,industry,verification_status")
    .eq("id", id).single();
  if (error || !organization) notFound();
  return <main className={styles.page}>
    <div className={styles.shell}>
      <Link href="/business" style={{display:"inline-flex",alignItems:"center",gap:8,marginBottom:30,color:"var(--primary)",fontWeight:700}}><ArrowLeft size={17}/> Switch identity</Link>
      <header className={styles.header}>
        <span className={styles.eyebrow}>SERA BUSINESS · {organization.kind.toUpperCase()}</span>
        <h1>{organization.name}</h1>
        <p>{organization.description || "Your professional organization workspace on SERA."}</p>
      </header>
      <section className={styles.card} style={{maxWidth:620}}>
        <span className={styles.icon}><Building2 size={24}/></span>
        <span className={styles.content}>
          <strong>Organization overview</strong>
          <small>{organization.city || "Ethiopia"} · {organization.industry || "Industry not set"}</small>
          <small>Membership: {membership.role} · Verification: {organization.verification_status}</small>
        </span>
      </section>
      <p className={styles.footnote}><ShieldCheck size={16}/> This workspace checks your organization membership on the server. Company tools will be introduced in the next milestone.</p>
    </div>
  </main>;
}
