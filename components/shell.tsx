"use client";
import { useEffect, useRef, useState } from "react";
import { ActivityBell, InboxBadge } from "./activity/bell";
import Link from "next/link";
import { usePathname } from "next/navigation";
import {
  Home,
  Search,
  Bell,
  Mail,
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
} from "lucide-react";
import { useSera } from "./store";
import { LanguageSelector, TranslationNotice } from "./language-selector";
import { Brand } from "./ui";
import { SiteFooter } from "./footer";
import { navigationGroups, navIsActive, feedAutoHideEnabled, nextFeedChrome } from "@/lib/navigation";
import type { Key } from "@/lib/i18n";
export function Shell({
  children,
  authenticated = false,
  verified = false,
}: {
  children: React.ReactNode;
  authenticated?: boolean;
  verified?: boolean;
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
      "/network/messages",
      "/notifications",
      "/connections",
      "/workspace",
      "/profile",
      "/jobs",
      "/matches",
      "/moderation",
      "/admin/moderation",
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
  useEffect(() => {
    const root = document.documentElement;
    delete root.dataset.navHidden;
    if (!feedAutoHideEnabled(pathname, authenticated, open)) return;

    let chrome = {anchor:Math.max(0,window.scrollY),hidden:false};
    function onScroll() {
      // Clamp elastic overscroll before deciding which way the reader is moving.
      const next=nextFeedChrome(chrome,window.scrollY,root.scrollHeight-root.clientHeight);
      if(next.hidden!==chrome.hidden)root.toggleAttribute("data-nav-hidden",next.hidden);
      chrome=next;
    }
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => {
      window.removeEventListener("scroll", onScroll);
      delete root.dataset.navHidden;
    };
  }, [pathname, open, authenticated]);
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
  return (
    <>
      <a href="#main-content" className="skip-link">
        {t("skipContent")}
      </a>
      <header className="site-header sera-app-header" lang={lang} dir="ltr">
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
        <Link href={authenticated ? "/feed" : "/"} className="sera-header-wordmark" aria-label="SERA.et home">SERA<span>.et</span></Link>
        <div className="header-account-controls">
          <Link className="header-control" href={authenticated?"/discover#discover-filter":"/jobs"} aria-label="Search" title="Search"><Search size={22}/></Link>
          {verified ? <ActivityBell /> : <Link className="header-control" href="/notifications" aria-label="Notifications" title="Notifications"><Bell size={22}/></Link>}
          {verified?<InboxBadge/>:<Link className="header-control" href="/network/messages" aria-label="Inbox" title="Inbox"><Mail size={22}/></Link>}
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
                      <LanguageSelector compact onNavigate={close} />
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
      <main id="main-content" className="sera-main" tabIndex={-1}>
        <TranslationNotice />
        {children}
      </main>
      {!privateWorkspace && <SiteFooter />}
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
