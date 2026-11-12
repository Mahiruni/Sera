"use client";
import { useActionState, useCallback, useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import { X } from "lucide-react";
import { useRouter } from "next/navigation";
import { repostPost, undoRepost } from "@/lib/social/actions";
import { SeraReactionIcon } from "./SeraReactionIcon";
import { SignatureSheetHandle } from "./SignatureSheetHandle";

/** One-click entry, deliberate sheet confirmation, moderated publication. */
export function RepostButton({ id, author, disabled = false, reposted=false,count=0 }: {
  id: string; author: string; disabled?: boolean; reposted?:boolean;count?:number;
}) {
  const router = useRouter();
  const [open, setOpen] = useState(false);
  const [thoughts,setThoughts]=useState(false);
  const [note,setNote]=useState("");
  const [state, action, pending] = useActionState(async (
    previous: import("@/lib/social/actions").SocialState,
    data: FormData
  ) => {
    const result = await (reposted?undoRepost:repostPost)(previous, data);
    if (result.success) {
      setOpen(false);
      router.refresh();
    }
    return result;
  }, {});
  const requestClose=useCallback(()=>{if(pending)return;if(note.trim()&&!window.confirm("Discard your repost draft?"))return;setOpen(false);},[pending,note]);
  const trigger = useRef<HTMLButtonElement>(null);
  const close = useRef<HTMLButtonElement>(null);
  const dialog = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (!open) return;
    const previouslyFocused = trigger.current;
    const oldOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    close.current?.focus();
    return () => { document.body.style.overflow = oldOverflow; previouslyFocused?.focus(); };
  }, [open]);
  useEffect(() => {
    if (!open) return;
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape" && !pending) { event.preventDefault(); requestClose(); }
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
    return () => document.removeEventListener("keydown", onKey);
  }, [open, pending, requestClose]);
  return <>
    <button ref={trigger} type="button" onClick={() => {setThoughts(false);setNote("");setOpen(true)}} disabled={disabled} aria-pressed={reposted}
      aria-label={disabled ? "You cannot repost your own post" : "Repost this professional update"}
      title={disabled ? "Your own post" : "Repost to your network"}>
      <SeraReactionIcon kind="repost" active={reposted}/><span className="social-action-count">{count||""}</span>
    </button>
    {open && typeof document !== "undefined" && createPortal(
      <div className="sera-repost-backdrop" onMouseDown={e => {
        if (e.target === e.currentTarget && !pending) requestClose();
      }}>
        <div ref={dialog} className="sera-repost-sheet" role="dialog" aria-modal="true" aria-labelledby={"repost-" + id}>
          <SignatureSheetHandle onClose={requestClose} disabled={pending}/><div className="sera-repost-head">
            <h2 id={"repost-" + id}><SeraReactionIcon kind="repost" /> Repost</h2>
            <button ref={close} type="button" aria-label="Close" disabled={pending} onClick={requestClose}>
              <X size={21} />
            </button>
          </div>
          <form action={action} className="sera-repost-body" aria-busy={pending}>
            <input type="hidden" name="post_id" value={id}/>
            <p><strong>{author}</strong>&apos;s original update</p>
            {!reposted&&<div className="sera-repost-options"><button type="button" aria-pressed={!thoughts} onClick={()=>{setThoughts(false)}}>Repost</button><button type="button" aria-pressed={thoughts} onClick={()=>setThoughts(true)}>Repost with thoughts</button></div>}
            {thoughts&&!reposted?<textarea aria-label="Your thoughts" name="note" maxLength={400} placeholder="Share your perspective…" rows={5} value={note} onChange={e=>setNote(e.target.value)} disabled={pending}/>:<input type="hidden" name="note" value=""/>}
            {state.error && <p role="alert" className="social-error">{state.error}</p>}
            <div className="sera-repost-actions">
              <button type="button" disabled={pending} onClick={requestClose}>Cancel</button>
              <button type="submit" disabled={pending||(!reposted&&thoughts&&!note.trim())}>
                <SeraReactionIcon kind="repost" />
                {pending ? "Saving…" : reposted?"Undo repost":"Repost"}
              </button>
            </div>
          </form>
        </div>
      </div>, document.body)}
  </>;
}
