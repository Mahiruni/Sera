import { InboxRefresh } from "@/components/network/inbox-refresh";
import Link from "next/link";
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
  const selected = conversations.find((c) => c.id === requested) ?? conversations[0];
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
  for (const attachment of attachments ?? []) {
    const { data } = await db.storage.from("professional-message-files").createSignedUrl(attachment.storage_path, 300);
    if (!data?.signedUrl) continue;
    const links = fileLinks.get(attachment.message_id) ?? [];
    links.push({ name: attachment.file_name, url: data.signedUrl });
    fileLinks.set(attachment.message_id, links);
  }
  const { data: unreadMessages } = await db.from("professional_messages")
    .select("conversation_id").is("read_at", null).neq("sender_id", user.id).limit(1000);
  const unreadCounts = new Map<string, number>();
  for (const message of unreadMessages ?? []) {
    unreadCounts.set(message.conversation_id, (unreadCounts.get(message.conversation_id) ?? 0) + 1);
  }
  const { data: accepted } = await db.from("professional_connection_requests")
    .select("requester_id,recipient_id").eq("status", "accepted")
    .or("requester_id.eq." + user.id + ",recipient_id.eq." + user.id).limit(100);
  const existing = new Set(conversations.map((c) => c.member_a === user.id ? c.member_b : c.member_a));
  const candidates = [...new Set((accepted ?? []).map((r) => r.requester_id === user.id ? r.recipient_id : r.requester_id))].filter((id) => !existing.has(id));
  const candidatePeople = await memberCards(candidates);
  return (
    <main className="sera-network network-app" style={{ maxWidth: 1100, margin: "auto", padding: "24px 16px 120px" }}>
      <p className="network-kicker">PRIVATE PROFESSIONAL CONVERSATIONS</p>
      <h1 style={{ fontSize: "clamp(2rem, 5vw, 3rem)", letterSpacing: "-.04em" }}>Your messages</h1>
      <InboxRefresh />
      <p>Conversations are visible only to their participants. <Link href="/connections">Manage connections</Link></p>
      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(min(100%, 310px), 1fr))", gap: 18 }}>
        <aside aria-label="Conversations" style={{ border: "1px solid #d5e9ef", borderRadius: 22, padding: 16 }}>
          <h2>Inbox ({conversations.length})</h2>
          {conversations.map((c) => {
            const id = c.member_a === user.id ? c.member_b : c.member_a;
            const person = people.get(id);
            return (
              <Link key={c.id} href={"/network/messages?thread=" + c.id}
                aria-current={selected?.id === c.id ? "page" : undefined}
                style={{ display: "flex", alignItems: "center", gap: 12, padding: 12, borderRadius: 14, background: selected?.id === c.id ? "#e4f7fb" : "transparent", color: "#134c61", marginBottom: 6 }}>
                <MemberAvatar name={person?.name ?? "Sera member"} photo={person?.photo} />
                <strong>{person?.name ?? "Sera member"}</strong>
                {Boolean(unreadCounts.get(c.id)) && <span aria-label={`${unreadCounts.get(c.id)} unread messages`} style={{ marginLeft: "auto", borderRadius: 99, padding: "3px 9px", background: "#087c9b", color: "white", fontSize: 12 }}>{unreadCounts.get(c.id)}</span>}
              </Link>
            );
          })}
          {!conversations.length && <p>No conversations yet. Connect with a professional to start one.</p>}
          {candidates.length > 0 && <>
            <h3>Start a conversation</h3>
            {candidates.map((id) => <form key={id} action={startConversation} style={{ display: "flex", gap: 8, marginBottom: 8, alignItems: "center" }}>
              <input type="hidden" name="recipient" value={id} />
              <span style={{ flex: 1 }}>{candidatePeople.get(id)?.name ?? "Connected member"}</span>
              <button type="submit">Message</button>
            </form>)}
          </>}
        </aside>
        <section aria-label="Message thread" style={{ border: "1px solid #d5e9ef", borderRadius: 22, padding: 18, minHeight: 350 }}>
          {selected ? <>
            <h2>{people.get(selected.member_a === user.id ? selected.member_b : selected.member_a)?.name ?? "Conversation"}</h2>
            <form method="get" action="/network/messages" role="search" style={{ display: "flex", gap: 8, marginTop: 16 }}>
              <input type="hidden" name="thread" value={selected.id} />
              <input name="q" type="search" aria-label="Search this conversation" placeholder="Search messages" defaultValue={search} maxLength={120} style={{ flex: 1, minWidth: 0, padding: 11, borderRadius: 12, border: "1px solid #a8d5df" }} />
              <button className="network-button" type="submit">Search</button>
              {search && <Link href={"/network/messages?thread=" + selected.id} style={{ alignSelf: "center" }}>Clear</Link>}
            </form>
            <div aria-live="polite" style={{ display: "flex", flexDirection: "column", gap: 12, margin: "20px 0" }}>
              {[...(messages ?? [])].reverse().map((m) => <article key={m.id} style={{ maxWidth: "88%", alignSelf: m.sender_id === user.id ? "flex-end" : "flex-start", padding: "12px 16px", borderRadius: 18, background: m.sender_id === user.id ? "#dff6fa" : "#f0f3f5", color: "#173d4b", overflowWrap: "anywhere" }}>
                <p style={{ whiteSpace: "pre-wrap", margin: 0 }}>{m.body}</p>
                {(fileLinks.get(m.id) ?? []).map((file) => <a key={file.url} href={file.url} target="_blank" rel="noopener noreferrer" style={{ display: "block", fontWeight: 700, marginTop: 8, textDecoration: "underline" }}>📎 {file.name}</a>)}
                <small>{new Date(m.created_at).toLocaleString()} {m.sender_id === user.id && m.read_at ? "· Read" : ""}</small>
              </article>)}
              {!messages?.length && <p>{search ? "No matching messages found." : "Say hello and start a meaningful professional conversation."}</p>}
            </div>
            <form action={sendProfessionalMessage} encType="multipart/form-data" style={{ display: "grid", gap: 8 }}>
              <input type="hidden" name="conversation" value={selected.id} />
              <label htmlFor="professional-message">Your message</label>
              <textarea id="professional-message" name="body" maxLength={4000} rows={3} placeholder="Write a thoughtful message…" style={{ width: "100%", padding: 12, borderRadius: 14, border: "1px solid #a8d5df" }} />
              <label htmlFor="professional-attachment">Optional private attachment (JPG, PNG, WebP, PDF; up to 5 MB)</label>
              <input id="professional-attachment" name="attachment" type="file" accept="image/jpeg,image/png,image/webp,application/pdf" />
              <button type="submit" className="network-button">Send message</button>
            </form>
            <form action={markProfessionalMessagesRead} style={{ marginTop: 12 }}>
              <input type="hidden" name="conversation" value={selected.id} />
              <button type="submit">Mark received messages as read</button>
            </form>
          </> : <p>Select a conversation or start one with an accepted connection.</p>}
        </section>
      </div>
    </main>
  );
}
