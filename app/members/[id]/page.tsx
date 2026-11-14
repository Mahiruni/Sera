import { serverClient } from "@/lib/supabase/server";
import { changeProfessionalRelationship, reportProfessionalMember } from "@/lib/network/relationships";
import { memberCards } from "@/lib/activity/members";
import { MemberAvatar } from "@/components/activity/avatar";
import { ProfessionalPresence } from "@/components/network/professional-presence";
import { notFound } from "next/navigation";
import Link from "next/link";
import {IdentityBadge} from "@/components/identity/badge";
import { requiredProfile } from "@/lib/auth/profile";
import { loadSocialFeed } from "@/lib/social/data";
import { uuid, pageNumber } from "@/lib/social/validation";
import {
  PostList,
  SocialNav,
  SocialPagination,
} from "@/components/social/feed";
export const metadata = {
  title: "Member stories",
  robots: { index: false, follow: false },
};
export default async function Member({
  params,
  searchParams,
}: {
  params: Promise<{ id: string }>;
  searchParams: Promise<{ page?: string; preview?: string }>;
}) {
  const { user } = await requiredProfile();
  const { id } = await params;
  if (!uuid(id)) notFound();
  const db = await serverClient();
  // Check audience before loading member cards, posts, or profile metadata.
  // Missing preferences default to signed-in members. Errors fail closed.
  if (id !== user.id) {
    const { data: blockedRelationship, error: blockError } = await db
      .from("professional_blocks")
      .select("blocker_id")
      .or(`and(blocker_id.eq.${id},blocked_id.eq.${user.id}),and(blocker_id.eq.${user.id},blocked_id.eq.${id})`)
      .limit(1)
      .maybeSingle();
    if (blockError || blockedRelationship) notFound();
    const { data: preference, error: preferenceError } = await db
      .from("professional_profile_visibility")
      .select("visibility")
      .eq("user_id", id)
      .maybeSingle();
    if (preferenceError) notFound();
    if (preference?.visibility === "connections") {
      const { data: connection, error: connectionError } = await db
        .from("professional_connection_requests")
        .select("status")
        .eq("status", "accepted")
        .or(`and(requester_id.eq.${user.id},recipient_id.eq.${id}),and(requester_id.eq.${id},recipient_id.eq.${user.id})`)
        .limit(1)
        .maybeSingle();
      if (connectionError || !connection) notFound();
    }
    // "public" remains member-only until a separately reviewed anonymous route exists.
  }
  const preview = id === user.id && (await searchParams).preview === "visitor";
  const page = pageNumber((await searchParams).page);
  const result = await loadSocialFeed({ authorId: id, page });
  const author = result.posts[0]?.author;
  const member = (await memberCards([id])).get(id);
  const [follow, sent, received] = id === user.id ? [null, null, null] : await Promise.all([
    db.from("professional_follows").select("follower_id").eq("follower_id", user.id).eq("followed_id", id).maybeSingle(),
    db.from("professional_connection_requests").select("status").eq("requester_id", user.id).eq("recipient_id", id).maybeSingle(),
    db.from("professional_connection_requests").select("status").eq("requester_id", id).eq("recipient_id", user.id).maybeSingle(),
  ]);
  const { data: block } = id === user.id ? { data: null } : await db.from("professional_blocks").select("blocker_id").eq("blocker_id", user.id).eq("blocked_id", id).maybeSingle();
  const blocked = Boolean(block);
  const following = Boolean(follow?.data);
  const sentStatus = sent?.data?.status;
  const receivedStatus = received?.data?.status;
  return (
    <div className="container page social-page social-detail sera-member-page sera-web-member">
      {preview && <aside className="sera-visitor-preview" role="status">
        <div><strong>Visitor preview</strong><p>This is a member-view simulation. It does not yet verify anonymous or connection-restricted access.</p></div>
        <Link href="/settings">Exit preview</Link>
      </aside>}
      <SocialNav />
      <header className="social-hero sera-member-hero">
        {id === user.id && !preview && <div className="sera-member-owner-tools"><Link href="/settings">Edit private profile</Link><Link href={`/members/${id}?preview=visitor`}>View as visitor</Link></div>}
        <MemberAvatar
          name={member?.name || author?.full_name || "Community member"}
          photo={member?.photo}
          size={84}
        />
        <h1>{member?.name || author?.full_name || "Sera community member"}{member?.identityVerified&&<IdentityBadge/>}</h1>
        <div className="sera-member-activity-row"><ProfessionalPresence member={id} compact /></div>
        <p>
          {author?.headline ||
            "Ideas, achievements and work shared with the community."}
        </p>
        {author?.location && <p>{author.location}</p>}
        {id !== user.id && member && (
          <div className="sera-member-actions">
            {!blocked && <form action={changeProfessionalRelationship}>
              <input type="hidden" name="target" value={id} />
              <input type="hidden" name="action" value={following ? "unfollow" : "follow"} />
              <button type="submit" className="network-button">{following ? "Following ✓" : "Follow"}</button>
            </form>}
            {blocked ? <span>You blocked this member</span> : receivedStatus === "pending" ? (
              <form action={changeProfessionalRelationship}>
                <input type="hidden" name="target" value={id} />
                <input type="hidden" name="action" value="accept" />
                <button type="submit" className="network-button">Accept invitation</button>
              </form>
            ) : sentStatus === "pending" ? (
              <form action={changeProfessionalRelationship}>
                <input type="hidden" name="target" value={id} />
                <input type="hidden" name="action" value="withdraw" />
                <button type="submit" className="network-button">Withdraw invitation</button>
              </form>
            ) : sentStatus === "accepted" || receivedStatus === "accepted" ? (
              <span>Connected ✓</span>
            ) : !sentStatus && !receivedStatus ? (
              <form action={changeProfessionalRelationship}>
                <input type="hidden" name="target" value={id} />
                <input type="hidden" name="action" value="request" />
                <button type="submit" className="network-button">Connect</button>
              </form>
            ) : <span className="sera-member-status">Invitation {sentStatus || receivedStatus}</span>}
            <form action={changeProfessionalRelationship}>
              <input type="hidden" name="target" value={id} />
              <input type="hidden" name="action" value={blocked ? "unblock" : "block"} />
              <button type="submit" aria-label={blocked ? "Unblock member" : "Block member"} className="network-button">{blocked ? "Unblock" : "Block"}</button>
            </form>
          </div>
        )}
        {id !== user.id && member && <details className="sera-member-report">
          <summary style={{ cursor: "pointer", fontSize: 13 }}>Report this member</summary>
          <form action={reportProfessionalMember} style={{ display: "grid", gap: 10, padding: 14, border: "1px solid #b9dce4", borderRadius: 16 }}>
            <input type="hidden" name="target" value={id} />
            <label htmlFor="report-reason">Reason</label>
            <select id="report-reason" name="reason" required defaultValue="spam" style={{ padding: 10, borderRadius: 10 }}>
              <option value="spam">Spam</option><option value="harassment">Harassment</option><option value="impersonation">Impersonation</option><option value="inappropriate">Inappropriate content</option><option value="other">Other</option>
            </select>
            <label htmlFor="report-details">Additional context (optional)</label>
            <textarea id="report-details" name="details" maxLength={1000} rows={3} style={{ padding: 10, borderRadius: 10 }} />
            <button type="submit" className="network-button">Submit confidential report</button>
          </form>
        </details>}
      </header>
      <PostList
        posts={result.posts}
        userId={user.id}
        empty="No stories to show here yet."
      />
      <SocialPagination
        page={page}
        hasMore={result.hasMore}
        href={"/members/" + id}
      />
    </div>
  );
}
