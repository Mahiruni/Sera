"use client";
import Link from "next/link";
import { usePathname } from "next/navigation";
import {
  LayoutDashboard,
  BriefcaseBusiness,
  FileText,
  Bookmark,
  UserRound,
  Plus,
  MessageCircle,
  Bell,
} from "lucide-react";
export function WorkspaceNav({ employer = false }: { employer?: boolean }) {
  const path = usePathname();
  const items = employer
    ? [
        {
          href: "/employer",
          label: "Hiring overview",
          icon: BriefcaseBusiness,
        },
        {
          href: "/dashboard?view=candidate",
          label: "Career workspace",
          icon: LayoutDashboard,
        },
        { href: "/network/messages", label: "Messages", icon: MessageCircle },
        { href: "/notifications", label: "Notifications", icon: Bell },
      ]
    : [
        {
          href: "/dashboard?view=candidate",
          label: "Overview",
          icon: LayoutDashboard,
        },
        { href: "/applications", label: "Applications", icon: FileText },
        { href: "/saved", label: "Saved jobs", icon: Bookmark },
        { href: "/settings", label: "My profile", icon: UserRound },
        { href: "/network/messages", label: "Messages", icon: MessageCircle },
        { href: "/notifications", label: "Notifications", icon: Bell },
      ];
  return (
    <nav
      className="workspace-nav"
      aria-label={employer ? "Employer workspace" : "Career workspace"}
    >
      {!employer && (
        <Link href="/feed" className={path === "/feed" ? "is-current" : ""}>
          <LayoutDashboard size={16} />
          <span>Community</span>
        </Link>
      )}
      {items.map(({ href, label, icon: Icon }) => {
        const base = href.split("?")[0],
          active =
            path === base ||
            (base !== "/dashboard" && path.startsWith(base + "/"));
        return (
          <Link
            key={href}
            href={href}
            className={active ? "is-current" : ""}
            aria-current={active ? "page" : undefined}
          >
            <Icon size={16} />
            <span>{label}</span>
          </Link>
        );
      })}
    </nav>
  );
}
