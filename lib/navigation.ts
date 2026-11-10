import type { Key } from "./i18n";
import type { Role } from "./model";
export type NavEntry = { href: string; key: Key; icon: string };
export type NavGroup = { id: string; title: Key; items: NavEntry[] };
export function navigationGroups(role: Role | null): NavGroup[] {
  return [
    {
      id: "explore",
      title: "exploreGroup",
      items: [
        { href: "/", key: "home", icon: "home" },
        { href: "/feed", key: "community", icon: "messages" },
        { href: "/jobs", key: "findJobs", icon: "discover" },
        { href: "/guides", key: "guides", icon: "about" },
        { href: "/education", key: "education", icon: "about" },
        { href: "/saved", key: "saved", icon: "saved" },
        { href: "/matches", key: "matches", icon: "matches" },
      ],
    },
    {
      id: "workspace",
      title: "yourWorkspace",
      items: [
        ...(role === "employer"
          ? []
          : [
              {
                href: "/dashboard",
                key: "workspace" as Key,
                icon: "workspace",
              },
            ]),
        { href: "/settings", key: "profile", icon: "profile" },
        ...(role === "employer"
          ? []
          : [
              {
                href: "/applications",
                key: "applications" as Key,
                icon: "applications",
              },
            ]),
        { href: "/messages", key: "messages", icon: "messages" },
      ],
    },
    {
      id: "employers",
      title: "employer",
      items: [
        { href: "/employer", key: "employerDashboard", icon: "employer" },
        { href: "/employer/post", key: "post", icon: "post" },
        { href: "/employer/pipeline", key: "pipeline", icon: "pipeline" },
        { href: "/pricing", key: "promote", icon: "featured" },
      ],
    },
    {
      id: "preferences",
      title: "preferencesGroup",
      items: [
        { href: "/about", key: "aboutSera", icon: "about" },
        { href: "/help", key: "helpContact", icon: "help" },
        { href: "/privacy", key: "privacyTerms", icon: "privacy" },
      ],
    },
  ];
}
export function navIsActive(pathname: string, href: string) {
  if (href === "/") return pathname === "/";
  if (href === "/privacy") return ["/privacy", "/terms"].includes(pathname);
  if (href === "/help")
    return ["/help", "/contact", "/how-it-works"].includes(pathname);
  return (
    pathname === href || (href === "/jobs" && pathname.startsWith("/jobs/"))
  );
}
