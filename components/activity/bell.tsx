"use client";
import Link from "next/link";
import { Bell, Mail } from "lucide-react";
import { useEffect, useState } from "react";
import { professionalUnreadCount, professionalInboxUnreadCount } from "@/lib/network/notifications";

/** The header is an entry point to the canonical professional notification inbox.
 * The full inbox owns all read-state mutations and displays every notification kind.
 */
function ActivityIndicator({inbox=false}:{inbox?:boolean}) {
  const [count, setCount] = useState(0);
  useEffect(() => {
    let active = true;
    async function refresh() {
      try {
        const next = await (inbox?professionalInboxUnreadCount():professionalUnreadCount());
        if (active) setCount(next);
      } catch {
        // Retain the last known count during transient network failures.
      }
    }
    void refresh();
    const interval = window.setInterval(() => {
      if (document.visibilityState === "visible") void refresh();
    }, 30000);
    const onVisible = () => {
      if (document.visibilityState === "visible") void refresh();
    };
    document.addEventListener("visibilitychange", onVisible);
    window.addEventListener("focus", onVisible);
    return () => {
      active = false;
      window.clearInterval(interval);
      document.removeEventListener("visibilitychange", onVisible);
      window.removeEventListener("focus", onVisible);
    };
  }, [inbox]);
  return (
    <Link
      href={inbox?"/network/messages":"/notifications"}
      className="header-control activity-trigger"
      aria-label={(inbox?"Inbox":"Notifications")+(count?`, ${count} unread`:"")}
      title={inbox?"Inbox":"Notifications"}
    >
      {inbox?<Mail size={22} aria-hidden="true"/>:<Bell size={22} aria-hidden="true"/>}
      {count > 0 && <b aria-hidden="true">{count > 99 ? "99+" : count}</b>}
    </Link>
  );
}

export function ActivityBell(){return <ActivityIndicator/>}
export function InboxBadge(){return <ActivityIndicator inbox/>}
