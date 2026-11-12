import type { Key } from "./i18n";
import type { Role } from "./model";
export type NavEntry = { href: string; key: Key; icon: string };
export type NavGroup = { id: string; title: Key; items: NavEntry[] };
export type DockSection =
  "home" | "discover" | "saved" | "workspace" | "profile";

export function dockSection(pathname: string): DockSection | null {
  const within = (routes: string[]) =>
    routes.some(
      (route) => pathname === route || pathname.startsWith(route + "/"),
    );
  if (pathname === "/" || within(["/feed", "/posts"])) return "home";
  if (within(["/discover", "/jobs", "/members", "/categories"]))
    return "discover";
  if (within(["/saved"])) return "saved";
  if (within(["/settings", "/profile", "/account", "/sign-in", "/sign-up"]))
    return "profile";
  if (
    within([
      "/workspace",
      "/dashboard",
      "/employer",
      "/applications",
      "/messages",
      "/network/messages",
      "/connections",
      "/notifications",
      "/matches",
      "/moderation",
      "/admin/moderation",
    ])
  )
    return "workspace";
  return null;
}
export function navigationGroups(role: Role | null): NavGroup[] {
  return [
    {
      id: "explore",
      title: "exploreGroup",
      items: [
        { href: "/", key: "home", icon: "home" },
        { href: "/feed", key: "community", icon: "messages" },
        { href: "/discover", key: "discover", icon: "discover" },
        { href: "/jobs", key: "findJobs", icon: "employer" },
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
        { href: "/network/messages", key: "messages", icon: "messages" },
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
  return pathname === href || pathname.startsWith(href + "/");
}

/** Auto-hiding chrome belongs only to the authenticated main feed. */
export function feedAutoHideEnabled(pathname:string,authenticated:boolean,drawerOpen=false){
  return authenticated&&pathname==="/feed"&&!drawerOpen;
}
export type FeedChromeState={anchor:number;hidden:boolean};
export function nextFeedChrome(state:FeedChromeState,scrollY:number,maxScroll:number):FeedChromeState{
  const y=Math.max(0,Math.min(scrollY,Math.max(0,maxScroll)));
  const anchor=state.hidden?Math.max(state.anchor,y):Math.min(state.anchor,y);
  const hidden=state.hidden?y>16&&anchor-y<10:y>72&&y-anchor>=20;
  return {anchor:hidden!==state.hidden?y:anchor,hidden};
}

/** Authenticated drawer uses real member destinations, not the public marketing home. */
export function memberNavigationGroups():NavGroup[]{return [
{id:"primary",title:"exploreGroup",items:[{href:"/feed",key:"home",icon:"home"},{href:"/connections",key:"networkMenu",icon:"network"},{href:"/discover",key:"discover",icon:"discover"},{href:"/jobs",key:"findJobs",icon:"employer"},{href:"/network/messages",key:"messages",icon:"messages"},{href:"/saved",key:"saved",icon:"saved"},{href:"/workspace",key:"workspace",icon:"workspace"}]},
{id:"career",title:"yourWorkspace",items:[{href:"/applications",key:"applications",icon:"applications"},{href:"/matches",key:"matches",icon:"matches"},{href:"/employer",key:"employerDashboard",icon:"employer"},{href:"/employer/post",key:"post",icon:"post"},{href:"/employer/pipeline",key:"pipeline",icon:"pipeline"}]},
{id:"preferences",title:"preferencesGroup",items:[{href:"/settings",key:"settingsMenu",icon:"settings"},{href:"/help",key:"helpContact",icon:"help"},{href:"/privacy",key:"privacyTerms",icon:"privacy"},{href:"/guides",key:"guides",icon:"about"},{href:"/education",key:"education",icon:"about"}]}];}
