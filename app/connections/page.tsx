import Link from "next/link";
import { recommendProfessionals, type ProfessionalCandidate } from "@/lib/network/recommendations";
import { openProfessionalConversation } from "@/lib/network/messaging";
import { Submit } from "@/components/social/forms";
import { UsersRound, ArrowUpRight, Search, UserPlus } from "lucide-react";
import { requiredProfile } from "@/lib/auth/profile";
import { serverClient } from "@/lib/supabase/server";
import { memberCards } from "@/lib/activity/members";
import { MemberAvatar } from "@/components/activity/avatar";
import { changeProfessionalRelationship } from "@/lib/network/relationships";

export const metadata = { title: "Professional connections | SERA.et", robots: { index: false, follow: false } };

export default async function ConnectionsPage({searchParams}:{searchParams:Promise<{tab?:string;q?:string}>}) {
  const params=await searchParams;const tabs=["discover","connections","invitations","following","followers"] as const;const tab=tabs.find(t=>t===params.tab)??"discover";const q=(typeof params.q==="string"?params.q:"").trim().slice(0,100);
  const { user } = await requiredProfile("/connections");
  const db = await serverClient();
  const [incoming, outgoing, follows, followers, acceptedTotal, invitationTotal] = await Promise.all([
    db.from("professional_connection_requests").select("requester_id,status").eq("recipient_id", user.id).order("created_at", { ascending: false }).limit(100),
    db.from("professional_connection_requests").select("recipient_id,status").eq("requester_id", user.id).order("created_at", { ascending: false }).limit(100),
    db.from("professional_follows").select("followed_id",{count:"exact"}).eq("follower_id", user.id).order("created_at",{ascending:false}).limit(100),
    db.from("professional_follows").select("follower_id",{count:"exact"}).eq("followed_id", user.id).order("created_at",{ascending:false}).limit(100),
    db.from("professional_connection_requests").select("id",{count:"exact",head:true}).or("requester_id.eq."+user.id+",recipient_id.eq."+user.id).eq("status","accepted"),
    db.from("professional_connection_requests").select("id",{count:"exact",head:true}).eq("recipient_id",user.id).eq("status","pending"),
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
  const discovery=tab==="discover"?await Promise.all([
    db.rpc("sera_recommend_talent"),
    db.from("posts").select("author_id").eq("status","published").eq("audience","public").is("deleted_at",null).order("created_at",{ascending:false}).limit(100),
  ]):null;
  const excluded=new Set([...ids,user.id]);
  const recommended=recommendProfessionals((discovery?.[0].data??[]) as ProfessionalCandidate[],user.id,excluded);
  const reasons=new Map(recommended.map(p=>[p.member_id,p.matched_skills]));
  const discoverIds=[...new Set([...recommended.map(p=>p.member_id),...(discovery?.[1].data??[]).map(p=>p.author_id).filter(id=>!excluded.has(id))])].slice(0,30);
  const allIds=[...new Set([...ids,...discoverIds])];
  const members=await memberCards(allIds);
  const headlines=new Map<string,string>();
  for(let i=0;i<allIds.length;i+=100){
    const {data}=await db.rpc("sera_feed_headlines",{p_ids:allIds.slice(i,i+100)});
    for(const row of data??[])headlines.set(row.id,row.headline||"");
  }
  for(const p of recommended)if(p.headline)headlines.set(p.member_id,p.headline);
  const followed = new Set(followRows.map((r) => r.followed_id));
  const accepted = new Set([
    ...incomingRows.filter((r) => r.status === "accepted").map((r) => r.requester_id),
    ...outgoingRows.filter((r) => r.status === "accepted").map((r) => r.recipient_id),
  ]);
  const pendingIncoming = incomingRows.filter((r) => r.status === "pending");
  const pendingOutgoing = outgoingRows.filter((r) => r.status === "pending");
  function person(id: string, actions: { action: string; label: string }[] = []) {
    const card = members.get(id);
    const headline=headlines.get(id)||"";const reason=reasons.get(id)||"";
    if (!card || (q&&!`${card.name} ${headline} ${reason}`.toLocaleLowerCase().includes(q.toLocaleLowerCase()))) return null;
    return (
      <li key={id} className="sera-connection-person">
        <MemberAvatar name={card.name} photo={card.photo} size={46} />
        <div className="sera-network-person-identity"><Link href={"/members/" + id} className="sera-network-person-name">{card.name}</Link>{headline&&<small>{headline}</small>}{reason&&tab==="discover"&&<small className="sera-network-person-reason">Shared skills: {reason}</small>}</div>
        {actions.map(({ action, label }) => (
          <form action={changeProfessionalRelationship} key={action}>
            <input type="hidden" name="target" value={id} />
            <input type="hidden" name="action" value={action} />
            <Submit preserveContent type="submit" className={"sera-connection-action" + (["decline", "withdraw", "unfollow"].includes(action) ? " secondary" : "")}>{label}</Submit>
          </form>
        ))}
        {tab==="connections"&&<form action={openProfessionalConversation}><input type="hidden" name="recipient" value={id}/><Submit preserveContent type="submit" className="sera-connection-action secondary">Message</Submit></form>}
      </li>
    );
  }
  const sections={connections:[...accepted].map(id=>person(id)),invitations:pendingIncoming.map(r=>person(r.requester_id,[{action:"accept",label:"Accept"},{action:"decline",label:"Decline"}])),following:followRows.map(r=>person(r.followed_id,[{action:"unfollow",label:"Unfollow"}])),followers:followerRows.map(r=>person(r.follower_id,followed.has(r.follower_id)?[]:[{action:"follow",label:"Follow back"}])),discover:discoverIds.map(id=>person(id,[{action:"request",label:"Connect"}]))};
  const counts={connections:acceptedTotal.count??accepted.size,invitations:invitationTotal.count??pendingIncoming.length,following:follows.count??followRows.length,followers:followers.count??followerRows.length,discover:discoverIds.length};
  const titles={connections:"Connections",invitations:"Invitations",following:"Following",followers:"Followers",discover:"Discover"};
  const visible=sections[tab].filter(Boolean);
  return <div className="sera-network network-app sera-connections sera-relationship-hub">
    <header className="sera-hub-heading"><div><h1>Network</h1><p>Your professional circle.</p></div><Link href="/discover?tab=people" className="sera-hub-primary"><UserPlus size={18}/> Find people</Link></header>
    <nav className="sera-hub-metrics" aria-label="Network overview">{(["connections","following","followers"] as const).map(t=><Link key={t} href={"/connections?tab="+t}><strong>{counts[t]}</strong><span>{titles[t]}</span></Link>)}</nav>
    <nav className="sera-hub-tabs" aria-label="Relationship views">{tabs.map(t=><Link key={t} href={"/connections?tab="+t} aria-current={t===tab?"page":undefined}>{titles[t]}{t==="invitations"&&counts[t]>0?<span className="sera-hub-count">{counts[t]}</span>:null}</Link>)}</nav>
    {(incoming.error||outgoing.error||follows.error||followers.error)&&<p role="alert">Some relationships could not be loaded. Please refresh.</p>}
    <>
      <form className="sera-hub-search" action="/connections"><input type="hidden" name="tab" value={tab}/><Search size={18}/><input name="q" aria-label="Search the people shown here" placeholder={"Search "+titles[tab].toLowerCase()} defaultValue={q}/><button type="submit">Search</button></form>
      <section className="sera-hub-list" aria-label={titles[tab]}><h2>{titles[tab]} <small>{visible.length}</small></h2>{visible.length?<ul>{visible}</ul>:<div className="sera-hub-empty"><UsersRound size={32}/><h3>{q?"No matching people":tab==="invitations"?"You’re all caught up":"Grow your professional circle"}</h3><p>{q?"Try a different name.":tab==="invitations"?"New invitations will appear here.":"Discover professionals who share your interests."}</p><Link href="/discover?tab=people">Discover people <ArrowUpRight size={14}/></Link></div>}</section>
      {tab==="discover"&&<Link className="sera-hub-discover" href={"/discover?tab=people"+(q?"&q="+encodeURIComponent(q):"")}>Explore the professional directory <ArrowUpRight size={18}/></Link>}
      {tab!=="discover"&&counts[tab]>100&&<p className="sera-hub-limit">Showing up to 100 recent people in this view.</p>}
      {tab==="invitations"&&pendingOutgoing.length>0&&<section className="sera-hub-list"><h2>Sent invitations <small>{pendingOutgoing.length}</small></h2><ul>{pendingOutgoing.map(r=>person(r.recipient_id,[{action:"withdraw",label:"Withdraw"}]))}</ul></section>}
    </>
  </div>;
}
