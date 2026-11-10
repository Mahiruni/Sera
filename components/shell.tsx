"use client";
import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import {
  Home,
  Compass,
  Bookmark,
  LayoutDashboard,
  UserRound,
  X,
  ArrowUpRight,
  Check,
  LogOut,
  Sparkles,
  BriefcaseBusiness,
  FileText,
  MessagesSquare,
  Plus,
  GitPullRequest,
  BadgeInfo,
  CircleHelp,
  ShieldCheck,
  ChevronDown,
  ArrowLeftRight,
  Star,
  UserPlus,
} from "lucide-react";
import { useSera } from "./store";
import { LanguageSelector, TranslationNotice } from "./language-selector";
import { Brand } from "./ui";
import { SiteFooter } from "./footer";
import { navigationGroups, navIsActive } from "@/lib/navigation";
import type { Key } from "@/lib/i18n";
export function Shell({
  children,
  authenticated = false,
}: {
  children: React.ReactNode;
  authenticated?: boolean;
}) {
  const { lang, t, state, setState, notice, storageError } = useSera();
  const pathname = usePathname();
  const privateWorkspace =
    authenticated &&
    [
      "/discover",
      "/feed",
      "/posts",
      "/members",
      "/dashboard",
      "/employer",
      "/applications",
      "/saved",
      "/settings",
      "/messages",
      "/workspace",
      "/profile",
    ].some((route) => pathname === route || pathname.startsWith(route + "/"));
  const [open, setOpen] = useState(false);
  const dialog = useRef<HTMLDialogElement>(null);
  const trigger = useRef<HTMLButtonElement>(null);
  useEffect(() => {
    const el = dialog.current;
    if (!el) return;
    if (open) {
      el.showModal();
      const prev = document.body.style.overflow;
      document.body.style.overflow = "hidden";
      return () => {
        document.body.style.overflow = prev;
      };
    }
    if (el.open) el.close();
  }, [open]);
  const close = () => {
    setOpen(false);
    trigger.current?.focus();
  };
  const groups = navigationGroups(authenticated ? null : state.role);
  const icons: Record<string, typeof Home> = {
    home: Home,
    discover: Compass,
    saved: Bookmark,
    matches: Sparkles,
    workspace: LayoutDashboard,
    profile: UserRound,
    applications: FileText,
    messages: MessagesSquare,
    employer: BriefcaseBusiness,
    post: Plus,
    pipeline: GitPullRequest,
    featured: Star,
    about: BadgeInfo,
    help: CircleHelp,
    privacy: ShieldCheck,
  };
  const dock = [
    { href: authenticated ? "/feed" : "/", key: "home" as Key, Icon: Home },
    { href: "/discover", key: "discover" as Key, Icon: Compass },
    { href: "/saved", key: "saved" as Key, Icon: Bookmark },
    {
      href: authenticated ? "/dashboard" : "/workspace",
      key: "workspace" as Key,
      Icon: LayoutDashboard,
    },
    {
      href: authenticated ? "/settings" : "/sign-in",
      key: "profile" as Key,
      Icon: UserRound,
    },
  ];
  return (
    <>
      <a href="#main-content" className="skip-link">
        {t("skipContent")}
      </a>
      <header className="site-header" lang={lang} dir="ltr">
        <button
          ref={trigger}
          type="button"
          className="header-control hamburger"
          aria-label={t("menu")}
          aria-expanded={open}
          aria-controls="navigation-drawer"
          onClick={() => setOpen(true)}
        >
          <span />
          <span />
        </button>
        <div className="header-account-controls">
          {([
            "/",
            "/am",
            "/about",
            "/guides",
            "/education",
            "/institutions",
            "/opportunities",
            "/how-it-works",
            "/languages",
          ].includes(pathname) ||
            pathname.startsWith("/guides/")) && (
            <Link
              className="header-signup"
              href={authenticated ? "/dashboard" : "/sign-up"}
              aria-label={
                authenticated
                  ? lang === "am"
                    ? "የእኔ መለያ"
                    : "My account"
                  : lang === "am"
                    ? "መለያ ፍጠር"
                    : "Create account"
              }
            >
              <UserPlus size={18} />
              <span>
                {authenticated
                  ? lang === "am"
                    ? "የእኔ መለያ"
                    : "My account"
                  : lang === "am"
                    ? "መለያ ፍጠር"
                    : "Create account"}
              </span>
            </Link>
          )}
          <LanguageSelector compact />
        </div>
      </header>
      <dialog
        lang={lang}
        dir="ltr"
        ref={dialog}
        id="navigation-drawer"
        className="drawer"
        aria-labelledby="drawer-title"
        onKeyDown={(event) => {
          if (event.key !== "Tab") return;
          const items = event.currentTarget.querySelectorAll<HTMLElement>(
            'a[href],button:not([disabled]),select:not([disabled]),summary,[tabindex="0"]',
          );
          const visible = Array.from(items).filter(
            (item) => item.getClientRects().length > 0,
          );
          const first = visible[0];
          const last = visible[visible.length - 1];
          if (event.shiftKey && document.activeElement === first) {
            event.preventDefault();
            last?.focus();
          } else if (!event.shiftKey && document.activeElement === last) {
            event.preventDefault();
            first?.focus();
          }
        }}
        onCancel={close}
        onClose={() => {
          setOpen(false);
          trigger.current?.focus();
        }}
        onClick={(e) => {
          if (e.target === e.currentTarget) close();
        }}
      >
        <div className="drawer-inner">
          <div className="drawer-top">
            <Brand small />
            <button
              className="icon-button"
              onClick={close}
              aria-label={t("close")}
              autoFocus
            >
              <X size={21} />
            </button>
          </div>
          <p className="eyebrow" id="drawer-title">
            {t("navigation")}
          </p>
          <nav className="grouped-navigation" aria-label={t("navigation")}>
            {groups.map((group) => {
              const active = group.items.some((item) =>
                navIsActive(pathname, item.href),
              );
              const defaultOpen =
                active ||
                (group.id === "explore" &&
                  !groups.some((g) =>
                    g.items.some((item) => navIsActive(pathname, item.href)),
                  ));
              return (
                <details
                  className="drawer-group"
                  key={`${group.id}-${pathname}`}
                  name="sera-navigation"
                  open={defaultOpen}
                >
                  <summary>
                    {t(group.title)}
                    <ChevronDown size={15} />
                  </summary>
                  <div className="drawer-group-links">
                    {group.id === "preferences" && (
                      <LanguageSelector onNavigate={close} />
                    )}
                    {group.items.map((item) => {
                      const Icon = icons[item.icon];
                      return (
                        <Link
                          key={item.href}
                          href={item.href}
                          onClick={close}
                          aria-current={
                            navIsActive(pathname, item.href)
                              ? "page"
                              : undefined
                          }
                          className={
                            navIsActive(pathname, item.href) ? "active" : ""
                          }
                        >
                          <Icon size={17} />
                          <span>{t(item.key)}</span>
                          <ArrowUpRight size={13} />
                        </Link>
                      );
                    })}
                  </div>
                </details>
              );
            })}
          </nav>
          <div className="drawer-actions">
            <Link
              className="role-switch"
              href={
                authenticated
                  ? "/settings"
                  : state.role
                    ? `/account?role=${state.role === "employer" ? "seeker" : "employer"}`
                    : "/account"
              }
              onClick={close}
            >
              <ArrowLeftRight size={16} />
              <span>
                {authenticated
                  ? t("profile")
                  : t(state.role ? "switch" : "chooseRole")}
              </span>
              <ArrowUpRight size={13} />
            </Link>
          </div>
          {!authenticated && state.role && (
            <button
              className="text-button"
              onClick={() => {
                setState((s) => ({ ...s, role: null }));
                close();
              }}
            >
              <LogOut size={16} />
              {t("exit")}
            </button>
          )}
          <p className="drawer-foot">
            {authenticated
              ? lang === "am"
                ? "የእርስዎ የግል መገለጫ እና የሥራ ቦታ።"
                : "Your private account, profile and workspaces."
              : t("demoNote")}
          </p>
        </div>
      </dialog>
      <main id="main-content" tabIndex={-1}>
        <TranslationNotice />
        {children}
      </main>
      {!privateWorkspace && <SiteFooter />}
      <nav lang={lang} className="footer-dock" aria-label={t("quickNav")}>
        <span className="dock-perimeter" aria-hidden="true" />
        {dock.map(({ href, key, Icon }, i) => {
          const active =
            href === "/"
              ? pathname === "/"
              : href === "/workspace" || href === "/dashboard"
                ? [
                    "/workspace",
                    "/dashboard",
                    "/profile",
                    "/applications",
                    "/messages",
                    "/matches",
                    "/employer",
                    "/employer/post",
                    "/employer/pipeline",
                  ].some(
                    (route) =>
                      pathname === route || pathname.startsWith(route + "/"),
                  )
                : pathname === href || pathname.startsWith(href + "/");
          return (
            <Link
              key={href}
              href={href}
              className={active ? "active" : ""}
              aria-current={active ? "page" : undefined}
            >
              <span className={`dock-icon dock-${i}`}>
                <Icon size={19} strokeWidth={1.8} />
              </span>
              <span>{t(key)}</span>
              {active && <i />}
            </Link>
          );
        })}
      </nav>
      <div className="toast" role="status" aria-live="polite">
        {notice && (
          <>
            <Check size={17} />
            {notice}
          </>
        )}
      </div>
      {storageError && (
        <div className="storage-warning" role="alert">
          Browser storage is unavailable or contains invalid data. Changes may
          not persist.
        </div>
      )}
    </>
  );
}
