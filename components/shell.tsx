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
  Languages,
  Check,
  LogOut,
} from "lucide-react";
import { useSera } from "./store";
import { Brand } from "./ui";
import type { Key } from "@/lib/i18n";
export function Shell({ children }: { children: React.ReactNode }) {
  const { t, lang, setLang, state, setState, notice, storageError } = useSera();
  const pathname = usePathname();
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
  const links: [string, Key][] = [
    ["/", "home"],
    ["/jobs", "jobs"],
    ["/saved", "saved"],
    ["/workspace", "workspace"],
    ["/profile", "profile"],
    ["/applications", "applications"],
    ["/matches", "matches"],
    ["/messages", "messages"],
    ["/employer", "employer"],
    ["/employer/post", "post"],
    ["/employer/pipeline", "pipeline"],
    ["/pricing", "promote"],
    ["/account", "privacy"],
  ];
  const dock = [
    { href: "/", key: "home" as Key, Icon: Home },
    { href: "/jobs", key: "jobs" as Key, Icon: Compass },
    { href: "/saved", key: "saved" as Key, Icon: Bookmark },
    { href: "/workspace", key: "workspace" as Key, Icon: LayoutDashboard },
  ];
  return (
    <>
      <a href="#main-content" className="skip-link">
        Skip to content
      </a>
      <header className="site-header">
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
        <Link
          className="header-control profile-control"
          href={state.role ? "/profile" : "/account"}
          aria-label={t("profile")}
        >
          {state.role ? (
            <span>
              {(state.role === "employer" ? state.company : state.name)
                .slice(0, 1)
                .toUpperCase()}
            </span>
          ) : (
            <UserRound size={20} />
          )}
        </Link>
      </header>
      <dialog
        ref={dialog}
        id="navigation-drawer"
        className="drawer"
        aria-labelledby="drawer-title"
        onKeyDown={(event) => {
          if (event.key !== "Tab") return;
          const items = event.currentTarget.querySelectorAll<HTMLElement>(
            'a[href],button:not([disabled]),[tabindex="0"]',
          );
          const first = items[0];
          const last = items[items.length - 1];
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
          <nav>
            {links.map(([href, key]) => (
              <Link
                href={href}
                key={href}
                onClick={close}
                aria-current={pathname === href ? "page" : undefined}
                className={pathname === href ? "active" : ""}
              >
                {t(key)}
                <ArrowUpRight size={15} />
              </Link>
            ))}
          </nav>
          <div className="language-switch">
            <Languages size={17} />
            <span>{t("language")}</span>
            <button aria-pressed={lang === "en"} onClick={() => setLang("en")}>
              EN
            </button>
            <button aria-pressed={lang === "am"} onClick={() => setLang("am")}>
              አማ
            </button>
          </div>
          {state.role && (
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
          <p className="drawer-foot">{t("demoNote")}</p>
        </div>
      </dialog>
      <main id="main-content" tabIndex={-1}>
        {children}
      </main>
      <footer className="site-footer">
        <Brand small />
        <p>{t("footer")}</p>
        <Link href="/account">
          {t("privacy")}
          <ArrowUpRight size={13} />
        </Link>
      </footer>
      <nav className="footer-dock" aria-label="Quick navigation">
        {dock.map(({ href, key, Icon }, i) => {
          const active =
            href === "/"
              ? pathname === "/"
              : href === "/workspace"
                ? [
                    "/workspace",
                    "/profile",
                    "/applications",
                    "/messages",
                    "/matches",
                    "/employer",
                    "/employer/post",
                    "/employer/pipeline",
                  ].includes(pathname)
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
