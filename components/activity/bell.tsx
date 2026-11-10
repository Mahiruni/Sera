"use client";
import {
  Bell,
  CheckCheck,
  Heart,
  MessageCircle,
  Reply,
  Forward,
  X,
} from "lucide-react";
import { useCallback, useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { inbox, markActivityRead, unreadCount } from "@/lib/activity/inbox";
import { MemberAvatar } from "./avatar";

const events = {
  like: [Heart, "liked your post"],
  share: [Forward, "shared your post"],
  reply: [Reply, "replied to your comment"],
  comment: [MessageCircle, "commented on your post"],
} as const;
type Row = Awaited<ReturnType<typeof inbox>>["rows"][number];

export function ActivityBell() {
  const router = useRouter();
  const panel = useRef<HTMLDialogElement>(null);
  const [open, setOpen] = useState(false),
    [filter, setFilter] = useState(false);
  const [count, setCount] = useState(0),
    [rows, setRows] = useState<Row[]>([]);
  const [more, setMore] = useState(false),
    [loading, setLoading] = useState(false);
  const [error, setError] = useState(""),
    [busy, setBusy] = useState(false);
  const [announcement, setAnnouncement] = useState("");
  const previousCount = useRef<number | null>(null);
  const sequence = useRef(0);
  const [inHistory, setInHistory] = useState(false);
  const refresh = useCallback(
    async (before?: number) => {
      const ticket = ++sequence.current;
      try {
        if (open) setLoading(true);
        const page = open ? await inbox(filter, before) : null;
        const next = page?.count ?? (await unreadCount());
        if (sequence.current !== ticket) return;
        if (
          previousCount.current !== null &&
          next > previousCount.current &&
          !open
        )
          setAnnouncement("New activity on your work");
        previousCount.current = next;
        setCount(next);
        setError("");
        if (page) {
          setRows((current) =>
            before
              ? [
                  ...current,
                  ...page.rows.filter(
                    (r) => !current.some((x) => x.id === r.id),
                  ),
                ]
              : page.rows,
          );
          setMore(page.more);
        }
      } catch {
        if (sequence.current === ticket)
          setError("Activity could not be loaded. Try again.");
      } finally {
        if (sequence.current === ticket) setLoading(false);
      }
    },
    [open, filter],
  );
  const refreshRef = useRef(refresh);
  const browsingHistory = useRef(false);
  useEffect(() => {
    refreshRef.current = refresh;
    browsingHistory.current = false;
    ++sequence.current;
    const timer = setTimeout(() => void refresh(), 0);
    return () => clearTimeout(timer);
  }, [refresh]);
  useEffect(() => {
    let source: EventSource | null = null,
      timer: ReturnType<typeof setTimeout>;
    const sync = () => {
      clearTimeout(timer);
      timer = setTimeout(() => {
        if (!browsingHistory.current) void refreshRef.current();
        else
          void unreadCount()
            .then(setCount)
            .catch(() => {});
      }, 200);
    };
    const connect = () => {
      source?.close();
      source = null;
      if (document.visibilityState === "visible") {
        sync();
        source = new EventSource("/api/activity/stream");
        source.onmessage = sync;
      }
    };
    connect();
    const poll = setInterval(() => {
      if (!document.hidden) sync();
    }, 45000);
    document.addEventListener("visibilitychange", connect);
    window.addEventListener("online", connect);
    return () => {
      source?.close();
      clearTimeout(timer);
      clearInterval(poll);
      document.removeEventListener("visibilitychange", connect);
      window.removeEventListener("online", connect);
    };
  }, []);
  useEffect(() => {
    if (!announcement) return;
    const timer = setTimeout(() => setAnnouncement(""), 3500);
    return () => clearTimeout(timer);
  }, [announcement]);
  async function read(id: number, all = false, href?: string) {
    setBusy(true);
    try {
      await markActivityRead(id, all);
      if (href) {
        panel.current?.close();
        router.push(href);
      } else {
        browsingHistory.current = false;
        setInHistory(false);
        await refresh();
      }
    } catch {
      setError("Read status could not be saved. Try again.");
    } finally {
      setBusy(false);
    }
  }
  return (
    <>
      <button
        className="header-control activity-trigger"
        aria-label={`Notifications, ${count} unread`}
        title="Notifications"
        aria-haspopup="dialog"
        aria-expanded={open}
        onClick={() => {
          setOpen(true);
          setLoading(true);
          setInHistory(false);
          setRows([]);
          setFilter(false);
          panel.current?.showModal();
        }}
      >
        <Bell size={20} />
        {count > 0 && <b>{count > 99 ? "99+" : count}</b>}
      </button>
      <span
        className={announcement ? "activity-toast" : "sr-only"}
        role="status"
      >
        {announcement}
      </span>
      <dialog
        className="activity-panel"
        ref={panel}
        aria-labelledby="activity-title"
        onClose={() => setOpen(false)}
        onClick={(e) => {
          if (e.target === panel.current) panel.current.close();
        }}
      >
        <div className="activity-inner">
          <header>
            <div>
              <small>SERA.ET</small>
              <h2 id="activity-title">Notifications</h2>
            </div>
            <button
              aria-label="Close notifications"
              onClick={() => panel.current?.close()}
            >
              <X size={19} />
            </button>
          </header>
          <div className="activity-tools">
            {[false, true].map((unread) => (
              <button
                key={String(unread)}
                aria-pressed={filter === unread}
                onClick={() => {
                  if (filter !== unread) {
                    setRows([]);
                    setFilter(unread);
                    setInHistory(false);
                    setLoading(true);
                  }
                }}
              >
                {unread ? "Unread" : "All"}
              </button>
            ))}
            <button
              className="activity-mark"
              disabled={!count || !rows.length || busy || loading}
              onClick={() => read(rows[0].id, true)}
            >
              <CheckCheck size={15} />
              Mark all read
            </button>
          </div>
          {inHistory && (
            <button
              className="activity-more"
              onClick={() => {
                browsingHistory.current = false;
                setInHistory(false);
                void refresh();
              }}
            >
              Back to latest activity
            </button>
          )}
          {error && (
            <p role="alert">
              {error} <button onClick={() => refresh()}>Retry</button>
            </p>
          )}
          <div className="activity-rows" aria-busy={loading}>
            {rows.map((row) => {
              const [Icon, verb] = events[row.kind as keyof typeof events];
              return (
                <button
                  key={row.id}
                  className="activity-row"
                  data-unread={row.unread}
                  disabled={busy}
                  onClick={() => read(row.id, false, row.href)}
                >
                  <span className="activity-face">
                    <MemberAvatar name={row.name} photo={row.photo} size={42} />
                    <i>
                      <Icon size={11} />
                    </i>
                  </span>
                  <span>
                    <strong>{row.name}</strong> {verb}.
                    <time dateTime={row.date}>
                      {new Date(row.date).toLocaleString(undefined, {
                        month: "short",
                        day: "numeric",
                        hour: "numeric",
                        minute: "2-digit",
                      })}
                    </time>
                  </span>
                  {row.unread && (
                    <span className="activity-dot" aria-label="Unread" />
                  )}
                </button>
              );
            })}
            {!rows.length && !error && (
              <div className="activity-empty">
                <Bell size={26} />
                <h3>
                  {loading
                    ? "Loading activity…"
                    : filter
                      ? "You’re all caught up"
                      : "Your activity starts here"}
                </h3>
                {!loading && !filter && (
                  <p>
                    Likes, comments, replies and shares on your work appear
                    here.
                  </p>
                )}
              </div>
            )}
            {more && rows.length > 0 && (
              <button
                className="activity-more"
                disabled={loading}
                onClick={() => {
                  browsingHistory.current = true;
                  setInHistory(true);
                  void refresh(rows.at(-1)?.id);
                }}
              >
                Load older activity
              </button>
            )}
          </div>
        </div>
      </dialog>
    </>
  );
}
