import Link from "next/link";
import { notFound, redirect } from "next/navigation";
import { ArrowLeft, ShieldCheck } from "lucide-react";
import { IdentityMark } from "@/components/identity/identity-mark";
import { CompanyProfileEditor } from "@/components/organizations/company-profile-editor";
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
  const { data: details } = await db.from("sera_organization_details")
    .select("street_address,region,phone_public,email_public,website_url,services,business_hours,founded_year,company_size,linkedin_url,logo_path,cover_path")
    .eq("organization_id", id).maybeSingle();
  const logoUrl = details?.logo_path && details.logo_path.startsWith(id + "/logo/")
    ? db.storage.from("sera-company-brand").getPublicUrl(details.logo_path).data.publicUrl : null;
  const coverUrl = details?.cover_path && details.cover_path.startsWith(id + "/cover/")
    ? db.storage.from("sera-company-brand").getPublicUrl(details.cover_path).data.publicUrl : null;
  const canEdit = membership.role === "owner" || membership.role === "admin";
  return <main className={styles.page}>
    <div className={styles.shell}>
      <Link href="/business" style={{display:"inline-flex",alignItems:"center",gap:8,marginBottom:30,color:"var(--primary)",fontWeight:700}}><ArrowLeft size={17}/> Switch identity</Link>
      <header className={styles.header}>
        <IdentityMark kind={organization.kind} size="large" />
        <span className={styles.eyebrow} style={{display:"block",marginTop:17}}>SERA BUSINESS · {organization.kind.toUpperCase()} PROFILE</span>
        <h1>{organization.name}</h1>
        <p>{organization.description || "Your professional organization workspace on SERA."}</p>
      </header>
      <section className={styles.card} style={{maxWidth:620}}>
        <IdentityMark kind={organization.kind} />
        <span className={styles.content}>
          <strong>Organization overview</strong>
          <small>{organization.city || "Ethiopia"} · {organization.industry || "Industry not set"}</small>
          <small>Membership: {membership.role} · Verification: {organization.verification_status}</small>
        </span>
      </section>
      {canEdit && <CompanyProfileEditor organizationId={id} initial={{
        street_address: details?.street_address ?? "", region: details?.region ?? "",
        phone_public: details?.phone_public ?? "", email_public: details?.email_public ?? "",
        website_url: details?.website_url ?? "", services: details?.services ?? "",
        business_hours: details?.business_hours ?? "", founded_year: details?.founded_year ?? null,
        company_size: details?.company_size ?? "", linkedin_url: details?.linkedin_url ?? "",
      }} logoUrl={logoUrl} coverUrl={coverUrl} />}
      <p className={styles.footnote}><ShieldCheck size={16}/> Members can view company details; only owners and admins can edit branding and contact information.</p>
    </div>
  </main>;
}
