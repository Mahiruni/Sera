import { Building2, UserRound, ShieldCheck } from "lucide-react";
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
  const Icon = publisher.type === "company" ? Building2 : UserRound;
  return (
    <span className={styles.group} aria-label={label + ": " + publisher.displayName}>
      <span className={styles.badge}>
        <Icon size={14} strokeWidth={1.9} aria-hidden="true" />
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
