"use client";
import { useEffect, useRef, useState } from "react";

export function ConversationTyping({ conversation }: { conversation: string }) {
  const [typing, setTyping] = useState(false);
  const lastSent = useRef(0);
  const idle = useRef<ReturnType<typeof setTimeout> | null>(null);
  useEffect(() => {
    let mounted = true;
    const read = async () => {
      if (document.visibilityState !== "visible") return;
      try {
        const response = await fetch("/api/network/messages/typing?conversation=" + encodeURIComponent(conversation), { cache: "no-store" });
        if (response.ok && mounted) setTyping(Boolean((await response.json()).typing));
      } catch { /* transient network loss */ }
    };
    void read();
    const interval = window.setInterval(() => { void read(); }, 3000);
    return () => { mounted = false; window.clearInterval(interval); };
  }, [conversation]);
  useEffect(() => () => { if (idle.current) clearTimeout(idle.current); }, []);
  const announce = (active: boolean) => {
    void fetch("/api/network/messages/typing", {
      method: "POST", headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ conversation, typing: active }), keepalive: !active,
    }).catch(() => {});
  };
  const onInput = () => {
    const now = Date.now();
    if (now - lastSent.current > 3500) {
      lastSent.current = now;
      announce(true);
    }
    if (idle.current) clearTimeout(idle.current);
    idle.current = setTimeout(() => announce(false), 2500);
  };
  return <>
    <p role="status" aria-live="polite" style={{ minHeight: 20, fontSize: 12, color: "#356477", margin: "4px 0" }}>
      {typing ? "Your connection is typing…" : ""}
    </p>
    <label className="sr-only" htmlFor="professional-message">Your message</label>
    <textarea id="professional-message" name="body" maxLength={4000} rows={1}
      onInput={onInput} onBlur={() => announce(false)}
      placeholder="Message…"
      className="sera-chat-input" />
  </>;
}
