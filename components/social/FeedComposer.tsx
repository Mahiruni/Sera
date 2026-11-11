"use client";
import { useCallback, useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { createPortal } from "react-dom";
import { ImagePlus, FolderKanban, Quote, PenLine, X, Users, Video } from "lucide-react";
import { MemberAvatar } from "@/components/activity/avatar";
import { PostComposer } from "@/components/social/PostComposer";
import type { PostKind } from "@/lib/social/validation";

export function FeedComposer({ name, avatarUrl }: { name: string; avatarUrl?: string | null }) {
  const router = useRouter();
  const [open, setOpen] = useState(false);
  const [initialKind, setInitialKind] = useState<PostKind>("idea");
  const trigger = useRef<HTMLButtonElement>(null);
  const close = useRef<HTMLButtonElement>(null);
  const dialog = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (!open) return;
    const triggerElement = trigger.current;
    const oldOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    close.current?.focus();
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") { event.preventDefault(); setOpen(false); }
      if (event.key !== "Tab") return;
      const elements = Array.from(dialog.current?.querySelectorAll<HTMLElement>('button:not([disabled]), input:not([disabled]), textarea:not([disabled]), select:not([disabled]), summary, a[href]') ?? []).filter(el => el.getClientRects().length > 0);
      if (!elements.length) return;
      const first = elements[0], last = elements[elements.length - 1];
      if (event.shiftKey && (document.activeElement === first || !dialog.current?.contains(document.activeElement))) { event.preventDefault(); last.focus(); }
      else if (!event.shiftKey && (document.activeElement === last || !dialog.current?.contains(document.activeElement))) { event.preventDefault(); first.focus(); }
    };
    document.addEventListener("keydown", onKey);
    return () => { document.removeEventListener("keydown", onKey); document.body.style.overflow = oldOverflow; triggerElement?.focus(); };
  }, [open]);
  const launch = (kind: PostKind) => { setInitialKind(kind); setOpen(true); };
  const onPublished = useCallback((id: string) => { setOpen(false); router.push("/posts/" + id); router.refresh(); }, [router]);
  const dismiss = () => {
    const form = dialog.current?.querySelector("form");
    const dirty = form ? Array.from(new FormData(form).entries()).some(([key, value]) => !["kind", "quote_source"].includes(key) && (typeof value === "string" ? value.trim().length > 0 : value.size > 0)) : false;
    if (dirty && !window.confirm("Close the editor? Any unsaved changes will be lost.")) return;
    setOpen(false);
  };
  return <>
    <section className="sera-feed-compose" aria-label="Create a post">
      <div className="sera-feed-compose-top">
        <MemberAvatar name={name} photo={avatarUrl} size={46} />
        <button ref={trigger} type="button" className="sera-feed-compose-trigger" onClick={() => launch("idea")}>What&apos;s on your mind, {name.split(" ")[0]}?</button>
        <button type="button" className="sera-feed-compose-pencil" aria-label="Create a post" onClick={() => launch("idea")}><PenLine size={19}/></button>
      </div>
      <div className="sera-feed-compose-actions">
        <button type="button" onClick={() => launch("photos")}><ImagePlus size={19}/> Photo</button>
        <button type="button" onClick={() => launch("photos")}><Video size={19}/> Media</button>
        <button type="button" onClick={() => launch("project")}><FolderKanban size={19}/> Project</button>
        <button type="button" onClick={() => launch("quote")}><Quote size={19}/> Thought</button>
      </div>
    </section>
    {open && typeof document !== "undefined" && createPortal(
      <div className="sera-compose-backdrop" onMouseDown={e => { if (e.target === e.currentTarget) dismiss(); }}>
        <div ref={dialog} role="dialog" aria-modal="true" aria-labelledby="sera-compose-heading" className="sera-compose-dialog">
          <div className="sera-compose-dialog-head"><h2 id="sera-compose-heading"><span className="sera-premium-heading-icon"><PenLine size={21}/></span> Create Post</h2><button ref={close} type="button" aria-label="Close editor" onClick={dismiss}><X size={21}/></button></div>
          <div className="sera-compose-dialog-author"><MemberAvatar name={name} photo={avatarUrl} size={42}/><div><strong>{name}</strong><small className="sera-premium-audience"><Users size={14}/> SERA members</small></div></div>
          <div className="sera-compose-dialog-content"><PostComposer key={initialKind} initialKind={initialKind} onPublished={onPublished} /></div>
        </div>
      </div>, document.body
    )}
  </>;
}
