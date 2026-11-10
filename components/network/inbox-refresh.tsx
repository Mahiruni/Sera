"use client";
import { useEffect } from "react";
import { useRouter } from "next/navigation";

/** Refresh the inbox while it is visible; avoid background network requests. */
export function InboxRefresh() {
  const router = useRouter();
  useEffect(() => {
    const refresh = () => {
      if (document.visibilityState === "visible") router.refresh();
    };
    const interval = window.setInterval(refresh, 12000);
    document.addEventListener("visibilitychange", refresh);
    return () => {
      window.clearInterval(interval);
      document.removeEventListener("visibilitychange", refresh);
    };
  }, [router]);
  return null;
}
