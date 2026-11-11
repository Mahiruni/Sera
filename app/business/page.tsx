import { redirect } from "next/navigation";
import Link from "next/link";
import { Building2, ArrowUpRight, Plus, UserRound, ShieldCheck } from "lucide-react";
import { currentUser } from "@/lib/supabase/server";
import { currentOrganizationMemberships } from "@/lib/organizations/membership";
import styles from "./page.module.css";

export const metadata = { title: "Your organizations | Sera.et", robots: { index: false, follow: false } };

export default async function BusinessAccountsPage() {
  const user = await currentUser();
  if (!user?.email_confirmed_at) redirect("/sign-in?next=%2Fbusiness");
  let memberships: Awaited<ReturnType<typeof currentOrganizationMemberships>> = [];
  let unavailable = false;
  try { memberships = await currentOrganizationMemberships(); } catch { unavailable = true; }
  return <main className={styles.page}>
    <div className={styles.shell}>
      <header className={styles.header}>
        <span className={styles.eyebrow}>SERA BUSINESS · YOUR IDENTITIES</span>
        <h1>Your work, every perspective.</h1>
        <p>Choose your personal identity or an organization you belong to. Access to company tools is checked against your membership.</p>
      </header>
      <div className={styles.grid}>
        <Link href="/account" className={styles.card}>
          <span className={styles.icon}><UserRound size={23}/></span>
          <span className={styles.content}><strong>Personal account</strong><small>Your individual SERA identity</small></span>
          <ArrowUpRight className={styles.arrow} size={20}/>
        </Link>
        {memberships.map(({ organization, role }) => <Link className={styles.card} key={organization.id} href={`/business/${organization.id}`}>
          <span className={styles.icon}><Building2 size={23}/></span>
          <span className={styles.content}><strong>{organization.name}</strong><small>{organization.kind === "company" ? "Company" : "Organization"} · {role} · {organization.verification_status === "verified" ? "Verified" : "Not verified"}</small></span>
          <ArrowUpRight className={styles.arrow} size={20}/>
        </Link>)}
        <Link href="/business/create" className={`${styles.card} ${styles.create}`}>
          <span className={styles.icon}><Plus size={23}/></span>
          <span className={styles.content}><strong>Create a business identity</strong><small>Build your presence on SERA</small></span>
          <ArrowUpRight className={styles.arrow} size={20}/>
        </Link>
      </div>
      {unavailable ? <p className={styles.notice} role="status">Organizations could not be loaded. Try again after the database foundation is deployed.</p>
        : memberships.length === 0 ? <p className={styles.notice}>Organizations you own or join will appear here.</p> : null}
      <p className={styles.footnote}><ShieldCheck size={15}/> Switching identities does not change your sign-in or grant new permissions.</p>
    </div>
  </main>;
}
