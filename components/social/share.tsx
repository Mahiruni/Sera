"use client";
import { useEffect, useRef, useState, useTransition } from "react";
import { createPortal } from "react-dom";
import { Check, Copy, Smartphone, X } from "lucide-react";
import { sharePost } from "@/lib/social/actions";
import { SeraReactionIcon } from "./SeraReactionIcon";

/** User-directed sharing; never count a cancelled OS share. */
export function ShareButton({ id, title, count }: { id: string; title: string; count: number }) {
  const [open, setOpen] = useState(false);
  const [message, setMessage] = useState("");
  const [pending, start] = useTransition();
  const trigger = useRef<HTMLButtonElement>(null);
  const close = useRef<HTMLButtonElement>(null);
  const sheet = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    const previouslyFocused = trigger.current;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    close.current?.focus();
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape" && !pending) { event.preventDefault(); setOpen(false); }
      if (event.key !== "Tab") return;
      const controls = Array.from(sheet.current?.querySelectorAll<HTMLButtonElement>("button:not([disabled])") || []);
      if (!controls.length) return;
      const first = controls[0], last = controls[controls.length - 1];
      if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
      else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
    };
    document.addEventListener("keydown", onKey);
    return () => {
      document.removeEventListener("keydown", onKey);
      document.body.style.overflow = previousOverflow;
      previouslyFocused?.focus();
    };
  }, [open, pending]);

  function perform(kind: "copy" | "device") {
    start(async () => {
      setMessage("");
      const url = new URL("/posts/" + id, window.location.origin).href;
      try {
        if (kind === "device" && typeof navigator.share === "function") {
          await navigator.share({ title: title || "A story on SERA.et", url });
        } else {
          await navigator.clipboard.writeText(url);
        }
        setOpen(false);
        const data = new FormData();
        data.set("post_id", id);
        const result = await sharePost({}, data);
        setMessage(result.error
          ? "Shared, but its count could not be updated."
          : kind === "copy" ? "Link copied to clipboard." : "Shared successfully.");
      } catch (error) {
        if (error instanceof DOMException && error.name === "AbortError") return;
        setMessage("Sharing failed. Please try again.");
      }
    });
  }

  return <div className="social-share">
    <button ref={trigger} type="button" disabled={pending} onClick={() => setOpen(true)}
      aria-label={`Share this story, ${count} shares`}>
      <SeraReactionIcon kind="share" /><span className="social-action-count">{count > 0 ? count : "Share"}</span>
    </button>
    {message && <p role="status">{message}</p>}
    {open && typeof document !== "undefined" && createPortal(
      <div className="sera-repost-backdrop" onMouseDown={e => {
        if (e.target === e.currentTarget && !pending) setOpen(false);
      }}>
        <div ref={sheet} className="sera-repost-sheet sera-share-sheet" role="dialog" aria-modal="true"
          aria-labelledby={"share-" + id}>
          <header className="sera-repost-head">
            <h2 id={"share-" + id}><SeraReactionIcon kind="share" /> Share this story</h2>
            <button ref={close} type="button" disabled={pending} onClick={() => setOpen(false)} aria-label="Close share menu"><X size={21} /></button>
          </header>
          <div className="sera-share-options">
            <p>Pass a valuable idea to someone in your professional world.</p>
            <button type="button" disabled={pending} onClick={() => perform("copy")}>
              <Copy size={21} /><span>Copy link<small>Keep a direct link to this story</small></span><Check size={17} className="sera-share-chevron" />
            </button>
            {typeof navigator.share === "function" && <button type="button" disabled={pending} onClick={() => perform("device")}>
              <Smartphone size={21} /><span>Share with another app<small>Choose an available Android app</small></span>
            </button>}
          </div>
        </div>
      </div>, document.body)}
  </div>;
}
