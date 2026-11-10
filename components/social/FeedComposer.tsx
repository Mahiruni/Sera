"use client";
import { useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { ImagePlus, FolderKanban, Quote, PenLine, X } from "lucide-react";
import { MemberAvatar } from "@/components/activity/avatar";
import { PostComposer } from "@/components/social/PostComposer";
import type { PostKind } from "@/lib/social/validation";

export function FeedComposer({ name, avatarUrl }: { name: string; avatarUrl?: string | null }) {
  const [open, setOpen] = useState(false);
  const [initialKind, setInitialKind] = useState<PostKind>("idea");
  const [mounted, setMounted] = useState(false);
  const trigger = useRef<HTMLButtonElement>(null);
  const close = useRef<HTMLButtonElement>(null);
  const dialog = useRef<HTMLDivElement>(null);
  useEffect(() => setMounted(true), []);
  useEffect(() => {
    if (!open) return;
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
    return () => { document.removeEventListener("keydown", onKey); document.body.style.overflow = oldOverflow; trigger.current?.focus(); };
  }, [open]);
  const launch = (kind: PostKind) => { setInitialKind(kind); setOpen(true); };
  const dismiss = () => {
    if (dialog.current?.querySelector("input:not([type=hidden]):not([type=file]):not([type=submit]), textarea") && !window.confirm("Close the editor? Any unsaved changes will be lost.")) return;
    setOpen(false);
  };
  return <>
    <section className="sera-feed-compose" aria-label="Create a post">
      <div className="sera-feed-compose-top">
        <MemberAvatar name={name} photo={avatarUrl} size={46} />
        <button ref={trigger} type="button" className="sera-feed-compose-trigger" onClick={() => launch("idea")}>What's on your mind?</button>
        <button type="button" className="sera-feed-compose-pencil" aria-label="Create a post" onClick={() => launch("idea")}><PenLine size={19}/></button>
      </div>
      <div className="sera-feed-compose-actions">
        <button type="button" onClick={() => launch("photos")}><ImagePlus size={19}/> Photo</button>
        <button type="button" onClick={() => launch("project")}><FolderKanban size={19}/> Project</button>
        <button type="button" onClick={() => launch("quote")}><Quote size={19}/> Thought</button>
      </div>
    </section>
    {mounted && open && createPortal(
      <div className="sera-compose-backdrop" onMouseDown={e => { if (e.target === e.currentTarget) dismiss(); }}>
        <div ref={dialog} role="dialog" aria-modal="true" aria-labelledby="sera-compose-heading" className="sera-compose-dialog">
          <div className="sera-compose-dialog-head"><h2 id="sera-compose-heading">Create a post</h2><button ref={close} type="button" aria-label="Close editor" onClick={dismiss}><X size={21}/></button></div>
          <div className="sera-compose-dialog-author"><MemberAvatar name={name} photo={avatarUrl} size={42}/><div><strong>{name}</strong><small>Visible to SERA members</small></div></div>
          <div className="sera-compose-dialog-content"><PostComposer key={initialKind} initialKind={initialKind} /></div>
        </div>
      </div>, document.body
    )}
  </>;
}
