"use client";
import { useEffect } from "react";
import { useRouter } from "next/navigation";

/** Live inbox invalidation with a bounded fallback and visibility-aware lifecycle. */
export function InboxRefresh() {
  const router = useRouter();
  useEffect(() => {
    let source: EventSource | null = null;
    let pending: ReturnType<typeof setTimeout> | undefined;
    let lastRefresh = 0;
    let disposed = false;
    const refresh = () => {
      if (disposed || document.visibilityState !== "visible" || !navigator.onLine) return;
      if (pending) clearTimeout(pending);
      const delay = Math.max(250, 1000 - (Date.now() - lastRefresh));
      pending = setTimeout(() => {
        if (disposed || document.visibilityState !== "visible" || !navigator.onLine) return;
        lastRefresh = Date.now();
        router.refresh();
      }, delay);
    };
    const disconnect = () => {
      source?.close();
      source = null;
    };
    const connect = () => {
      disconnect();
      if (disposed || document.visibilityState !== "visible" || !navigator.onLine) return;
      refresh();
      source = new EventSource("/api/network/messages/stream");
      source.onmessage = refresh;
      // EventSource reconnects automatically after transient failures.
      source.onerror = () => refresh();
    };
    const onVisibility = () => {
      if (document.visibilityState === "visible") connect();
      else disconnect();
    };
    connect();
    const interval = window.setInterval(refresh, 30000);
    document.addEventListener("visibilitychange", onVisibility);
    window.addEventListener("online", connect);
    window.addEventListener("offline", disconnect);
    window.addEventListener("focus", refresh);
    return () => {
      disposed = true;
      disconnect();
      window.clearInterval(interval);
      if (pending) clearTimeout(pending);
      document.removeEventListener("visibilitychange", onVisibility);
      window.removeEventListener("online", connect);
      window.removeEventListener("offline", disconnect);
      window.removeEventListener("focus", refresh);
    };
  }, [router]);
  return null;
}
