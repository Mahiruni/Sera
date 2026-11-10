"use client";
import { useEffect } from "react";
import { useRouter } from "next/navigation";

/** Refresh unread counts while the member is actively viewing the app.
 * No public API, service-role credential, or cross-account subscription is needed. */
export function NotificationRefresh() {
  const router = useRouter();
  useEffect(() => {
    let pending: ReturnType<typeof setTimeout> | undefined;
    const refresh = () => {
      if (document.visibilityState !== "visible" || !navigator.onLine) return;
      if (pending) clearTimeout(pending);
      pending = setTimeout(() => router.refresh(), 250);
    };
    const interval = window.setInterval(refresh, 30000);
    document.addEventListener("visibilitychange", refresh);
    window.addEventListener("focus", refresh);
    window.addEventListener("online", refresh);
    return () => {
      window.clearInterval(interval);
      if (pending) clearTimeout(pending);
      document.removeEventListener("visibilitychange", refresh);
      window.removeEventListener("focus", refresh);
      window.removeEventListener("online", refresh);
    };
  }, [router]);
  return null;
}
