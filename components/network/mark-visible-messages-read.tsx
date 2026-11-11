"use client";

import { useEffect, useRef } from "react";
import { useRouter } from "next/navigation";
import { markProfessionalMessagesRead } from "@/lib/network/messaging";

/**
 * Receipt is recorded only when the recipient actually views an unread thread.
 * Delays the request slightly to avoid marking a conversation during navigation.
 */
export function MarkVisibleMessagesRead({
  conversation,
  unreadCount,
}: {
  conversation: string;
  unreadCount: number;
}) {
  const router = useRouter();
  const attempted = useRef("");
  useEffect(() => {
    if (unreadCount <= 0) return;
    let timer: ReturnType<typeof setTimeout> | undefined;
    let disposed = false;
    const key = `${conversation}:${unreadCount}`;
    const mark = () => {
      if (disposed || document.visibilityState !== "visible" || !document.hasFocus()) return;
      if (attempted.current === key) return;
      timer = setTimeout(async () => {
        if (disposed || document.visibilityState !== "visible" || !document.hasFocus()) return;
        attempted.current = key;
        try {
          const body = new FormData();
          body.set("conversation", conversation);
          await markProfessionalMessagesRead(body);
          if (!disposed) router.refresh();
        } catch {
          if (!disposed) attempted.current = "";
        }
      }, 900);
    };
    const onVisibility = () => {
      if (timer) clearTimeout(timer);
      timer = undefined;
      mark();
    };
    mark();
    document.addEventListener("visibilitychange", onVisibility);
    window.addEventListener("focus", onVisibility);
    window.addEventListener("blur", onVisibility);
    return () => {
      disposed = true;
      if (timer) clearTimeout(timer);
      document.removeEventListener("visibilitychange", onVisibility);
      window.removeEventListener("focus", onVisibility);
      window.removeEventListener("blur", onVisibility);
    };
  }, [conversation, unreadCount, router]);
  return null;
}
