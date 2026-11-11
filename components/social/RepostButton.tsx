"use client";
import { useActionState, useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { X } from "lucide-react";
import { useRouter } from "next/navigation";
import { repostPost } from "@/lib/social/actions";
import { SeraReactionIcon } from "./SeraReactionIcon";

/** One-click entry, deliberate sheet confirmation, moderated publication. */
export function RepostButton({ id, author, disabled = false }: {
  id: string; author: string; disabled?: boolean;
}) {
  const router = useRouter();
  const [open, setOpen] = useState(false);
  const [state, action, pending] = useActionState(async (
    previous: import("@/lib/social/actions").SocialState,
    data: FormData
  ) => {
    const result = await repostPost(previous, data);
    if (result.success) {
      setOpen(false);
      router.refresh();
    }
    return result;
  }, {});
  const trigger = useRef<HTMLButtonElement>(null);
  const close = useRef<HTMLButtonElement>(null);
  const dialog = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (!open) return;
    const previouslyFocused = trigger.current;
    const oldOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    close.current?.focus();
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape" && !pending) { event.preventDefault(); setOpen(false); }
      if (event.key !== "Tab") return;
      const focusables = Array.from(dialog.current?.querySelectorAll<HTMLElement>(
        'button:not([disabled]), textarea:not([disabled]), input:not([disabled])'
      ) || []).filter(el => el.getClientRects().length > 0);
      if (!focusables.length) return;
      const first = focusables[0], last = focusables[focusables.length - 1];
      if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
      else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
    };
    document.addEventListener("keydown", onKey);
    return () => { document.removeEventListener("keydown", onKey); document.body.style.overflow = oldOverflow; previouslyFocused?.focus(); };
  }, [open, pending]);
  return <>
    <button ref={trigger} type="button" onClick={() => setOpen(true)} disabled={disabled}
      aria-label={disabled ? "You cannot repost your own post" : "Repost this professional update"}
      title={disabled ? "Your own post" : "Repost to your network"}>
      <SeraReactionIcon kind="repost" /><span>Repost</span>
    </button>
    {open && typeof document !== "undefined" && createPortal(
      <div className="sera-repost-backdrop" onMouseDown={e => {
        if (e.target === e.currentTarget && !pending) setOpen(false);
      }}>
        <div ref={dialog} className="sera-repost-sheet" role="dialog" aria-modal="true" aria-labelledby={"repost-" + id}>
          <div className="sera-repost-head">
            <h2 id={"repost-" + id}><SeraReactionIcon kind="repost" /> Repost</h2>
            <button ref={close} type="button" aria-label="Close" disabled={pending} onClick={() => setOpen(false)}>
              <X size={21} />
            </button>
          </div>
          <form action={action} className="sera-repost-body" aria-busy={pending}>
            <input type="hidden" name="post_id" value={id}/>
            <p>Share <strong>{author}</strong>&apos;s update with clear attribution.</p>
            <label htmlFor={"repost-note-" + id}>Add your perspective <small>(optional)</small></label>
            <textarea id={"repost-note-" + id} name="note" maxLength={400}
              placeholder="Why does this matter to your network?" rows={3} disabled={pending} />
            {state.error && <p role="alert" className="social-error">{state.error}</p>}
            <div className="sera-repost-actions">
              <button type="button" disabled={pending} onClick={() => setOpen(false)}>Cancel</button>
              <button type="submit" disabled={pending}>
                <SeraReactionIcon kind="repost" />
                {pending ? "Reposting…" : "Repost"}
              </button>
            </div>
          </form>
        </div>
      </div>, document.body)}
  </>;
}
