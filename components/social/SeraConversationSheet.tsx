"use client";

import { startTransition, useEffect, useRef, useState, useTransition } from "react";
import { createPortal } from "react-dom";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { ChevronLeft, ChevronRight, X } from "lucide-react";
import { MemberAvatar } from "@/components/activity/avatar";
import { addComment,editComment,deleteComment,likeComment } from "@/lib/social/actions";
import { SeraReactionIcon } from "./SeraReactionIcon";
import { SignatureSheetHandle } from "./SignatureSheetHandle";

type ConversationComment = {
  id: string;
  parent_id: string | null;
  author_id: string;
  author: string;
  photo?: string | null;
  body: string;
  created_at: string;headline?:string;liked?:boolean;like_count?:number;
};
type ConversationPage = { comments: ConversationComment[]; hasMore: boolean };
const isComment = (value: unknown): value is ConversationComment => {
  if (!value || typeof value !== "object") return false;
  const item = value as Partial<ConversationComment>;
  return typeof item.id === "string" && typeof item.author_id === "string" &&
    typeof item.body === "string" && typeof item.created_at === "string" &&
    typeof item.author === "string" && (item.parent_id === null || typeof item.parent_id === "string");
};
const dateText = (value: string) => {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? "" : new Intl.DateTimeFormat("en", {
    day: "numeric", month: "short", year: "numeric"
  }).format(date);
};

/** Authenticated, paginated conversation without navigating away from the feed. */
export function SeraConversationSheet({
  postId, count, author,currentUserId=""
}: { postId: string; count: number; author: string;currentUserId?:string }) {
  const router = useRouter();
  const [open, setOpen] = useState(false);
  const [page, setPage] = useState(1);
  const [revision, setRevision] = useState(0);
  const [comments, setComments] = useState<ConversationComment[]>([]);
  const [hasMore, setHasMore] = useState(false);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState("");
  const [draft, setDraft] = useState("");
  const [editing,setEditing]=useState<ConversationComment|null>(null);
  const [removing,setRemoving]=useState<string|null>(null);
  const [replyTo, setReplyTo] = useState<ConversationComment | null>(null);
  const [submitError, setSubmitError] = useState("");
  const [confirmDiscard, setConfirmDiscard] = useState(false);
  const [pending, start] = useTransition();
  const trigger = useRef<HTMLButtonElement>(null);
  const closeButton = useRef<HTMLButtonElement>(null);
  const dialog = useRef<HTMLDivElement>(null);
  const input = useRef<HTMLTextAreaElement>(null);

  function requestClose() {
    if (pending) return;
    if (draft.trim()) { setConfirmDiscard(true); return; }
    setOpen(false);
  }
  function openSheet() {
    setPage(1); setRevision(v => v + 1); setOpen(true);
    setLoadError(""); setLoading(true); setSubmitError("");
  }
  function changePage(value: number) {
    setPage(value); setLoadError(""); setLoading(true);
    dialog.current?.querySelector(".sera-conversation-scroll")?.scrollTo({ top: 0 });
  }
  useEffect(() => {
    if (!open) return;
    const abort = new AbortController();
    async function fetchPage() {
      try {
        const response = await fetch(
          "/api/social/comments?post_id=" + encodeURIComponent(postId) + "&page=" + page,
          { signal: abort.signal, credentials: "same-origin", cache: "no-store" }
        );
        const body: unknown = await response.json();
        if (!response.ok) throw new Error(
          typeof body === "object" && body !== null && "error" in body &&
          typeof body.error === "string" ? body.error : "Unable to load the conversation."
        );
        if (!body || typeof body !== "object" || !("comments" in body) ||
            !Array.isArray(body.comments) || !body.comments.every(isComment)) {
          throw new Error("Unexpected conversation response.");
        }
        if (!abort.signal.aborted) {
          const result = body as ConversationPage;
          setComments(result.comments);
          setHasMore(result.hasMore === true);
          setLoadError("");
        }
      } catch (error) {
        if (!abort.signal.aborted) {
          setLoadError(error instanceof Error ? error.message : "Unable to load the conversation.");
        }
      } finally {
        if (!abort.signal.aborted) setLoading(false);
      }
    }
    void fetchPage();
    return () => abort.abort();
  }, [open, postId, page, revision]);

  // Focus restoration and body locking run only when the sheet opens/closes,
  // NEVER on each typed character; otherwise the keyboard loses focus.
  useEffect(() => {
    if (!open) return;
    const oldOverflow = document.body.style.overflow;
    const restoreFocus = trigger.current;
    document.body.style.overflow = "hidden";
    closeButton.current?.focus();
    return () => {
      document.body.style.overflow = oldOverflow;
      restoreFocus?.focus();
    };
  }, [open]);

  useEffect(() => {
    if (!open) return;
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        event.preventDefault();
        if (confirmDiscard) setConfirmDiscard(false);
        else requestClose();
      }
      if (event.key !== "Tab") return;
      const activeDialog = confirmDiscard
        ? dialog.current?.querySelector<HTMLElement>(".sera-conversation-confirm")
        : dialog.current;
      const focusable = Array.from(activeDialog?.querySelectorAll<HTMLElement>(
        'button:not([disabled]), textarea:not([disabled]), a[href]'
      ) ?? []).filter(el => el.getClientRects().length > 0);
      if (!focusable.length) return;
      const first = focusable[0], last = focusable[focusable.length - 1];
      if (event.shiftKey && (document.activeElement === first || !activeDialog?.contains(document.activeElement))) {
        event.preventDefault(); last.focus();
      } else if (!event.shiftKey && (document.activeElement === last || !activeDialog?.contains(document.activeElement))) {
        event.preventDefault(); first.focus();
      }
    };
    document.addEventListener("keydown", onKey);
    return () => document.removeEventListener("keydown", onKey);
  }, [open, pending, draft, confirmDiscard]);

  useEffect(() => {
    if (confirmDiscard) {
      dialog.current?.querySelector<HTMLButtonElement>(".sera-conversation-confirm button")?.focus();
    }
  }, [confirmDiscard]);

  function submitComment(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const body = draft.trim();
    if (pending || !body || body.length > 2000) return;
    const data = new FormData();
    data.set("post_id", postId);
    data.set("body", body);
    if (replyTo) data.set("parent_id", replyTo.parent_id || replyTo.id);
    setSubmitError("");
    if(editing)data.set("comment_id",editing.id);
    start(async () => {
      const result = await (editing?editComment:addComment)({}, data);
      if (result.error) { setSubmitError(result.error); return; }
      setDraft(""); setReplyTo(null);setEditing(null); setPage(1); setLoading(true);
      setRevision(v => v + 1);
      startTransition(() => router.refresh());
    });
  }

  function mutate(c:ConversationComment,kind:"like"|"delete"){if(pending)return;const f=new FormData();f.set("post_id",postId);f.set("comment_id",c.id);f.set("liked",c.liked?"no":"yes");start(async()=>{try{const r=await (kind==="like"?likeComment:deleteComment)({},f);if(r.error){setSubmitError(r.error);return;}setRemoving(null);if(kind==="like")setComments(cs=>cs.map(x=>x.id===c.id?{...x,liked:!c.liked,like_count:Math.max(0,(c.like_count||0)+(c.liked?-1:1))}:x));else{setRevision(v=>v+1);router.refresh();}}catch{setSubmitError("Connection unavailable. Please retry.");}});}
  function renderComment(comment: ConversationComment, root: ConversationComment) {
    return <article className="sera-conversation-comment" key={comment.id}>
      <Link href={"/members/" + comment.author_id} aria-label={"View " + comment.author + "'s profile"}>
        <MemberAvatar name={comment.author} photo={comment.photo} size={36} />
      </Link>
      <div className="sera-conversation-comment-content">
        <div className="sera-conversation-byline">
          <Link href={"/members/" + comment.author_id}>{comment.author}</Link>
          <time dateTime={comment.created_at}>{dateText(comment.created_at)}</time>
        </div>
        {comment.headline&&<small className="sera-author-occupation">{comment.headline}</small>}
        <p>{comment.body}</p>
        <div className="sera-comment-actions"><button type="button" disabled={pending} aria-label="Like comment" aria-pressed={!!comment.liked} onClick={()=>mutate(comment,"like")}><SeraReactionIcon kind="like" active={!!comment.liked}/>{comment.like_count||""}</button>
        {comment.author_id===currentUserId&&<><button type="button" disabled={pending} onClick={()=>{setEditing(comment);setReplyTo(null);setDraft(comment.body);input.current?.focus()}}>Edit</button><button type="button" disabled={pending} onClick={()=>setRemoving(comment.id)}>Delete</button></>}
        {removing===comment.id&&<><button type="button" onClick={()=>mutate(comment,"delete")} disabled={pending}>Confirm remove</button><button type="button" onClick={()=>setRemoving(null)}>Cancel</button></>}
        <button type="button" onClick={() => {
          setEditing(null);setReplyTo(root); setSubmitError(""); input.current?.focus();
        }}>Reply</button></div>
      </div>
    </article>;
  }
  const roots = comments.filter(comment => !comment.parent_id);

  return <div className="sera-inline-comments">
    <button ref={trigger} type="button" className="sera-conversation-trigger"
      aria-label={count + " comments, open conversation"} onClick={openSheet}>
      <SeraReactionIcon kind="comment" /><span className="social-action-count">{count}</span>
    </button>
    {open && typeof document !== "undefined" && createPortal(
      <div className="sera-conversation-backdrop" onMouseDown={event => {
        if (event.target === event.currentTarget) requestClose();
      }}>
        <div className="sera-conversation-sheet" role="dialog" aria-modal="true"
          aria-labelledby={"sera-conversation-title-" + postId} ref={dialog}>
          <SignatureSheetHandle onClose={requestClose} disabled={pending}/>
          <header className="sera-conversation-header">
            <h2 id={"sera-conversation-title-" + postId}>
              <SeraReactionIcon kind="comment" /> Comments · {count}
            </h2>
            <button ref={closeButton} type="button" aria-label="Close conversation"
              onClick={requestClose} disabled={pending}><X size={21} /></button>
          </header>
          <div className="sera-conversation-context">Discussing {author}&apos;s professional update</div>
          <div className="sera-conversation-scroll" aria-busy={loading}>
            {loading ? <p className="sera-conversation-state" role="status">Loading conversation…</p> :
              loadError ? <div className="sera-conversation-state" role="alert">
                <p>{loadError}</p>
                <button type="button" onClick={() => { setLoading(true); setRevision(v => v + 1); }}>Try again</button>
              </div> :
              roots.length ? <div className="sera-conversation-threads">
                {roots.map(root => <div className="sera-conversation-thread" key={root.id}>
                  {renderComment(root, root)}
                  {comments.filter(reply => reply.parent_id === root.id).map(reply =>
                    <div className="sera-conversation-reply" key={reply.id}>{renderComment(reply, root)}</div>
                  )}
                </div>)}
              </div> : <p className="sera-conversation-state">Start a thoughtful conversation.</p>}
            {!loading && !loadError && (page > 1 || hasMore) && <nav className="sera-conversation-pages" aria-label="Conversation pages">
              <button type="button" disabled={page === 1} onClick={() => changePage(page - 1)}>
                <ChevronLeft size={17} /> Newer
              </button><span>Page {page}</span>
              <button type="button" disabled={!hasMore} onClick={() => changePage(page + 1)}>
                Older <ChevronRight size={17} />
              </button>
            </nav>}
          </div>
          <form className="sera-conversation-composer" onSubmit={submitComment}>
            {replyTo && <div className="sera-conversation-reply-target">
              Replying to {replyTo.author}
              <button type="button" onClick={() => setReplyTo(null)}>Cancel reply</button>
            </div>}
            {editing&&<div className="sera-conversation-reply-target">Editing comment <button type="button" onClick={()=>{setEditing(null);setDraft("")}}>Cancel</button></div>}
            <label className="sr-only" htmlFor={"sera-comment-input-" + postId}>Your perspective</label>
            <textarea ref={input} id={"sera-comment-input-" + postId}
              placeholder="Add something meaningful to the conversation…"
              maxLength={2000} rows={2} value={draft} disabled={pending}
              onChange={event => setDraft(event.target.value)} />
            <div className="sera-conversation-composer-actions">
              <Link href={"/posts/" + postId + "#comments"}>Open full discussion</Link>
              <button type="submit" disabled={pending || !draft.trim()}>
                <SeraReactionIcon kind="comment" /> {pending ? "Posting…" : editing?"Save changes":replyTo ? "Reply" : "Post comment"}
              </button>
            </div>
            {submitError && <p role="alert" className="social-error">{submitError}</p>}
          </form>
          {confirmDiscard && <div className="sera-conversation-confirm" role="alertdialog"
            aria-label="Discard comment draft" aria-modal="true">
            <div>
              <h3>Discard this draft?</h3>
              <p>Your comment hasn&apos;t been published.</p>
              <button type="button" onClick={() => setConfirmDiscard(false)}>Keep writing</button>
              <button type="button" onClick={() => {
                setDraft(""); setReplyTo(null); setConfirmDiscard(false); setOpen(false);
              }}>Discard and close</button>
            </div>
          </div>}
        </div>
      </div>, document.body
    )}
  </div>;
}
