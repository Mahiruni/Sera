import { Building2, UserRound } from "lucide-react";
import styles from "./identity-mark.module.css";

export type IdentityKind = "personal" | "company" | "organization";
export function IdentityMark({ kind, size = "regular" }: { kind: IdentityKind; size?: "small" | "regular" | "large" }) {
  const isPersonal = kind === "personal";
  const Icon = isPersonal ? UserRound : Building2;
  return <span className={`${styles.mark} ${styles[kind]} ${styles[size]}`} aria-label={isPersonal ? "Personal profile" : kind === "company" ? "Company profile" : "Organization profile"} role="img">
    <Icon aria-hidden="true" strokeWidth={1.85} />
    <span className={styles.dot} aria-hidden="true" />
  </span>;
}
