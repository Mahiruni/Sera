import { BriefcaseBusiness, ShieldCheck } from "lucide-react";
import { IdentityMark } from "@/components/identity/identity-mark";
import styles from "./publisher-badge.module.css";

export type PublisherIdentity =
  | { type: "individual"; displayName: string }
  | { type: "company"; displayName: string; verified: boolean }
  | { type: "unclassified"; displayName: string };

export function PublisherBadge({ publisher }: { publisher: PublisherIdentity }) {
  const label = publisher.type === "company"
    ? "Company opportunity"
    : publisher.type === "individual"
      ? "Individual opportunity"
      : "Poster provided";
  const kind = publisher.type === "company" ? "company" : "personal";
  return (
    <span className={styles.group} aria-label={label + ": " + publisher.displayName}>
      <span className={styles.badge}>
        {publisher.type === "unclassified" ? <BriefcaseBusiness size={14} strokeWidth={1.9} aria-hidden="true" /> : <IdentityMark kind={kind} size="small" />}
        <span>{label}</span>
      </span>
      {publisher.type === "company" && publisher.verified && (
        <span className={styles.verified} title="Company identity verified by SERA">
          <ShieldCheck size={14} strokeWidth={1.9} aria-hidden="true" />
          <span>Verified company</span>
        </span>
      )}
    </span>
  );
}
