import Link from "next/link";
import { PageIntro } from "@/components/page-intro";
import { UsersRound, ArrowUpRight } from "lucide-react";
import { requiredProfile } from "@/lib/auth/profile";
import { serverClient } from "@/lib/supabase/server";
import { memberCards } from "@/lib/activity/members";
import { MemberAvatar } from "@/components/activity/avatar";
import { changeProfessionalRelationship } from "@/lib/network/relationships";

export const metadata = { title: "Professional connections | SERA.et", robots: { index: false, follow: false } };

export default async function ConnectionsPage() {
  const { user } = await requiredProfile("/connections");
  const db = await serverClient();
  const [incoming, outgoing, follows, followers] = await Promise.all([
    db.from("professional_connection_requests").select("requester_id,status").eq("recipient_id", user.id).order("created_at", { ascending: false }).limit(100),
    db.from("professional_connection_requests").select("recipient_id,status").eq("requester_id", user.id).order("created_at", { ascending: false }).limit(100),
    db.from("professional_follows").select("followed_id").eq("follower_id", user.id).limit(100),
    db.from("professional_follows").select("follower_id").eq("followed_id", user.id).limit(100),
  ]);
  const incomingRows = incoming.data ?? [];
  const outgoingRows = outgoing.data ?? [];
  const followRows = follows.data ?? [];
  const followerRows = followers.data ?? [];
  const ids = [
    ...incomingRows.map((r) => r.requester_id),
    ...outgoingRows.map((r) => r.recipient_id),
    ...followRows.map((r) => r.followed_id),
    ...followerRows.map((r) => r.follower_id),
  ];
  const members = await memberCards(ids);
  const followed = new Set(followRows.map((r) => r.followed_id));
  const accepted = new Set([
    ...incomingRows.filter((r) => r.status === "accepted").map((r) => r.requester_id),
    ...outgoingRows.filter((r) => r.status === "accepted").map((r) => r.recipient_id),
  ]);
  const pendingIncoming = incomingRows.filter((r) => r.status === "pending");
  const pendingOutgoing = outgoingRows.filter((r) => r.status === "pending");
  function person(id: string, actions: { action: string; label: string }[] = []) {
    const card = members.get(id);
    if (!card) return null;
    return (
      <li key={id} className="sera-connection-person">
        <MemberAvatar name={card.name} photo={card.photo} size={46} />
        <Link href={"/members/" + id} style={{ flex: 1, minWidth: 120, fontWeight: 700 }}>{card.name}</Link>
        {actions.map(({ action, label }) => (
          <form action={changeProfessionalRelationship} key={action}>
            <input type="hidden" name="target" value={id} />
            <input type="hidden" name="action" value={action} />
            <button type="submit" className={"sera-connection-action" + (["decline", "withdraw", "unfollow"].includes(action) ? " secondary" : "")}>{label}</button>
          </form>
        ))}
      </li>
    );
  }
  return (
    <div className="sera-network network-app sera-connections">
      <PageIntro icon={UsersRound} eyebrow="YOUR PROFESSIONAL CIRCLE" title="Good work starts with good people."
        description="Build your circle. Keep up with the people who move you forward.">
        <Link href="/discover?tab=people" className="button secondary">Find your people <ArrowUpRight size={16} /></Link>
      </PageIntro>
      <section className="sera-connection-section" aria-labelledby="requests-heading">
        <h2 id="requests-heading">Invitations received ({pendingIncoming.length})</h2>
        {pendingIncoming.length ? <ul style={{ listStyle: "none", padding: 0 }}>{pendingIncoming.map((r) => person(r.requester_id, [{ action: "accept", label: "Accept" }, { action: "decline", label: "Decline" }]))}</ul> : <p>No pending invitations.</p>}
      </section>
      <section aria-labelledby="sent-heading">
        <h2 id="sent-heading">Invitations sent ({pendingOutgoing.length})</h2>
        {pendingOutgoing.length ? <ul style={{ listStyle: "none", padding: 0 }}>{pendingOutgoing.map((r) => person(r.recipient_id, [{ action: "withdraw", label: "Withdraw" }]))}</ul> : <p>No outstanding invitations.</p>}
      </section>
      <section aria-labelledby="connections-heading">
        <h2 id="connections-heading">Your connections ({accepted.size})</h2>
        {accepted.size ? <ul style={{ listStyle: "none", padding: 0 }}>{[...accepted].map((id) => person(id))}</ul> : <p>Accepted professional connections will appear here.</p>}
      </section>
      <section aria-labelledby="following-heading">
        <h2 id="following-heading">Following ({followRows.length})</h2>
        {followRows.length ? <ul style={{ listStyle: "none", padding: 0 }}>{followRows.map((r) => person(r.followed_id, [{ action: "unfollow", label: "Unfollow" }]))}</ul> : <p>Explore professionals to follow.</p>}
      </section>
      <section aria-labelledby="followers-heading">
        <h2 id="followers-heading">Followers ({followerRows.length})</h2>
        {followerRows.length ? <ul style={{ listStyle: "none", padding: 0 }}>{followerRows.map((r) => person(r.follower_id, followed.has(r.follower_id) ? [] : [{ action: "follow", label: "Follow back" }]))}</ul> : <p>Your followers will appear here.</p>}
      </section>
    </div>
  );
}
