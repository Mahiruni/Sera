"use client";
import { useEffect } from "react";
import { useRouter } from "next/navigation";

/** Server-sent change invalidations keep the authenticated inbox fresh.
 * A slower poll is retained as a fallback when Realtime is unavailable. */
export function InboxRefresh() {
  const router = useRouter();
  useEffect(() => {
    let source: EventSource | null = null;
    let pending: ReturnType<typeof setTimeout> | undefined;
    const refresh = () => {
      if (document.visibilityState !== "visible") return;
      if (pending) clearTimeout(pending);
      pending = setTimeout(() => router.refresh(), 250);
    };
    const connect = () => {
      source?.close();
      source = null;
      if (document.visibilityState === "visible") {
        refresh();
        source = new EventSource("/api/network/messages/stream");
        source.onmessage = refresh;
      }
    };
    connect();
    const interval = window.setInterval(refresh, 30000);
    document.addEventListener("visibilitychange", connect);
    window.addEventListener("online", connect);
    return () => {
      source?.close();
      window.clearInterval(interval);
      if (pending) clearTimeout(pending);
      document.removeEventListener("visibilitychange", connect);
      window.removeEventListener("online", connect);
    };
  }, [router]);
  return null;
}
