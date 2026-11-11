import { InboxRefresh } from "@/components/network/inbox-refresh";
import { PageIntro } from "@/components/page-intro";
import { ConversationTyping } from "@/components/network/conversation-typing";
import { ProfessionalPresence } from "@/components/network/professional-presence";
import Link from "next/link";
import { Send, Paperclip, Search, MessageCircle, ArrowLeft } from "lucide-react";
import { requiredProfile } from "@/lib/auth/profile";
import { serverClient } from "@/lib/supabase/server";
import { memberCards } from "@/lib/activity/members";
import { MemberAvatar } from "@/components/activity/avatar";
import { startConversation, sendProfessionalMessage, markProfessionalMessagesRead } from "@/lib/network/messaging";
export const metadata = { title: "Professional messages | SERA.et", robots: { index: false, follow: false } };
export default async function ProfessionalInbox({ searchParams }: {
  searchParams: Promise<{ thread?: string; q?: string }>;
}) {
  const { user } = await requiredProfile("/network/messages");
  const db = await serverClient();
  const { data: threads } = await db.from("professional_conversations")
    .select("id,member_a,member_b,created_at")
    .or("member_a.eq." + user.id + ",member_b.eq." + user.id)
    .order("created_at", { ascending: false }).limit(80);
  const conversations = threads ?? [];
  const people = await memberCards(conversations.map((c) => c.member_a === user.id ? c.member_b : c.member_a));
  const params = await searchParams;
  const requested = params.thread;
  const search = typeof params.q === "string" ? params.q.trim().slice(0, 120) : "";
  const selected = requested ? conversations.find((c) => c.id === requested) : conversations[0];
  const { data: messages } = selected
    ? await (() => {
        let query = db.from("professional_messages")
          .select("id,sender_id,body,created_at,read_at")
          .eq("conversation_id", selected.id);
        if (search) query = query.ilike("body", "%" + search.replace(/[%,_]/g, "") + "%");
        return query.order("created_at", { ascending: false }).limit(100);
      })()
    : { data: [] };
  const messageIds = (messages ?? []).map((m) => m.id);
  const { data: attachments } = messageIds.length
    ? await db.from("professional_message_attachments").select("message_id,storage_path,file_name,mime_type").in("message_id", messageIds)
    : { data: [] };
  const fileLinks = new Map<string, { name: string; url: string }[]>();
  const attachmentRows = attachments ?? [];
  if (attachmentRows.length) {
    const { data: signed } = await db.storage.from("professional-message-files")
      .createSignedUrls(attachmentRows.map((item) => item.storage_path), 300);
    attachmentRows.forEach((attachment, index) => {
      const url = signed?.[index]?.signedUrl;
      if (!url) return;
      const links = fileLinks.get(attachment.message_id) ?? [];
      links.push({ name: attachment.file_name, url });
      fileLinks.set(attachment.message_id, links);
    });
  }
  const unreadCounts = new Map<string, number>();
  if (conversations.length) {
    const { data: unreadMessages } = await db.from("professional_messages")
      .select("conversation_id")
      .in("conversation_id", conversations.map((conversation) => conversation.id))
      .is("read_at", null).neq("sender_id", user.id).limit(1000);
    for (const message of unreadMessages ?? []) {
      unreadCounts.set(message.conversation_id, (unreadCounts.get(message.conversation_id) ?? 0) + 1);
    }
  }
  const { data: accepted } = await db.from("professional_connection_requests")
    .select("requester_id,recipient_id").eq("status", "accepted")
    .or("requester_id.eq." + user.id + ",recipient_id.eq." + user.id).limit(100);
  const existing = new Set(conversations.map((c) => c.member_a === user.id ? c.member_b : c.member_a));
  const candidates = [...new Set((accepted ?? []).map((r) => r.requester_id === user.id ? r.recipient_id : r.requester_id))].filter((id) => !existing.has(id));
  const candidatePeople = await memberCards(candidates);
  return (
    <div className="sera-network network-app sera-inbox">
      <PageIntro icon={MessageCircle} title="Your messages"
        description="A private space to exchange ideas and move work forward.">
        <Link className="button secondary" href="/connections">Your connections</Link>
      </PageIntro>
      <InboxRefresh />
      <div className="sera-inbox-layout">
        <aside aria-label="Conversations" className="sera-inbox-sidebar">
          <h2>Inbox ({conversations.length})</h2>
          {conversations.map((c) => {
            const id = c.member_a === user.id ? c.member_b : c.member_a;
            const person = people.get(id);
            return (
              <Link key={c.id} href={"/network/messages?thread=" + c.id}
                aria-current={selected?.id === c.id ? "page" : undefined}
                className="sera-conversation-link">
                <MemberAvatar name={person?.name ?? "Sera member"} photo={person?.photo} />
                <strong className="sera-conversation-name">{person?.name ?? "Sera member"}</strong>
                {Boolean(unreadCounts.get(c.id)) && <span aria-label={`${unreadCounts.get(c.id)} unread messages`} className="sera-conversation-unread">{unreadCounts.get(c.id)}</span>}
              </Link>
            );
          })}
          {!conversations.length && <p>No conversations yet. Connect with a professional to start one.</p>}
          {candidates.length > 0 && <>
            <h3>Start a conversation</h3>
            {candidates.map((id) => <form key={id} action={startConversation} style={{ display: "flex", gap: 8, marginBottom: 8, alignItems: "center" }}>
              <input type="hidden" name="recipient" value={id} />
              <MemberAvatar name={candidatePeople.get(id)?.name ?? "Connected member"} photo={candidatePeople.get(id)?.photo} size={32} /><span style={{ flex: 1 }}>{candidatePeople.get(id)?.name ?? "Connected member"}</span>
              <button type="submit">Message</button>
            </form>)}
          </>}
        </aside>
        <section aria-label="Message thread" className="sera-inbox-thread">
          {selected ? <>
            <div className="sera-inbox-member"><MemberAvatar name={people.get(selected.member_a === user.id ? selected.member_b : selected.member_a)?.name ?? "Sera member"} photo={people.get(selected.member_a === user.id ? selected.member_b : selected.member_a)?.photo} size={48} /><div><h2><Link href={"/members/" + (selected.member_a === user.id ? selected.member_b : selected.member_a)}>{people.get(selected.member_a === user.id ? selected.member_b : selected.member_a)?.name ?? "Conversation"}</Link></h2><small>Private professional conversation</small></div></div>
            <ProfessionalPresence member={selected.member_a === user.id ? selected.member_b : selected.member_a} />
            <form method="get" action="/network/messages" role="search" className="sera-inbox-search">
              <input type="hidden" name="thread" value={selected.id} />
              <input name="q" type="search" aria-label="Search this conversation" placeholder="Search messages" defaultValue={search} maxLength={120} />
              <button className="network-button" type="submit"><Search size={16} aria-hidden="true" /> Search</button>
              {search && <Link href={"/network/messages?thread=" + selected.id} className="sera-inbox-search-clear">Clear</Link>}
            </form>
            <div aria-live="polite" className="sera-inbox-messages">
              {[...(messages ?? [])].reverse().map((m) => <article key={m.id} className={m.sender_id === user.id ? "sera-message-bubble is-mine" : "sera-message-bubble"}>
                <p style={{ whiteSpace: "pre-wrap", margin: 0 }}>{m.body}</p>
                {(fileLinks.get(m.id) ?? []).map((file) => <a key={file.url} href={file.url} target="_blank" rel="noopener noreferrer" style={{ display: "block", fontWeight: 700, marginTop: 8, textDecoration: "underline" }}>📎 {file.name}</a>)}
                <small><time dateTime={m.created_at}>{new Date(m.created_at).toLocaleString("en-GB", { timeZone: "Africa/Addis_Ababa", dateStyle: "medium", timeStyle: "short" })}</time> {m.sender_id === user.id && m.read_at ? "· Read" : ""}</small>
              </article>)}
              {!messages?.length && <p>{search ? "No matching messages found." : "Say hello and start a meaningful professional conversation."}</p>}
            </div>
            <form action={sendProfessionalMessage} encType="multipart/form-data" className="sera-inbox-composer">
              <input type="hidden" name="conversation" value={selected.id} />
              <ConversationTyping conversation={selected.id} />
              <label htmlFor="professional-attachment"><Paperclip size={16} aria-hidden="true" /> Optional private attachment (JPG, PNG, WebP, PDF; up to 5 MB)</label>
              <input id="professional-attachment" name="attachment" type="file" accept="image/jpeg,image/png,image/webp,application/pdf" />
              <button type="submit" className="network-button"><Send size={16} aria-hidden="true" /> Send message</button>
            </form>
            <form action={markProfessionalMessagesRead} className="sera-inbox-read-form">
              <input type="hidden" name="conversation" value={selected.id} />
              <button type="submit" className="sera-inbox-read-button">Mark received messages as read</button>
            </form>
          </> : <div className="sera-inbox-empty"><MessageCircle size={34} aria-hidden="true" /><p>Select a conversation or start one with an accepted connection.</p><Link href="/connections"><ArrowLeft size={15} aria-hidden="true" /> Explore connections</Link></div>}
        </section>
      </div>
    </div>
  );
}
