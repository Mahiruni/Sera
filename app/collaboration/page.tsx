import Link from "next/link";
import { MemberAvatar } from "@/components/activity/avatar";
import { PageIntro } from "@/components/page-intro";
import { ArrowLeft, ArrowUpRight, FolderKanban, Plus, Send, UsersRound } from "lucide-react";
import {requiredProfile} from "@/lib/auth/profile";
import {serverClient} from "@/lib/supabase/server";
import {memberCards} from "@/lib/activity/members";
import {revalidatePath} from "next/cache";
export const dynamic="force-dynamic";
async function act(f:FormData){"use server";await requiredProfile("/collaboration");const db=await serverClient();const action=String(f.get("action"));const id=String(f.get("space"));const peer=String(f.get("member"));const r=action==="create"?await db.rpc("sera_create_space",{p_name:String(f.get("name")),p_description:String(f.get("description")||"")}):action==="invite"?await db.rpc("sera_space_invite",{p_space:id,p_member:peer}):action==="accept"||action==="decline"?await db.rpc("sera_space_respond",{p_space:id,p_accept:action==="accept"}):action==="message"?await db.rpc("sera_send_space_message",{p_space:id,p_body:String(f.get("body"))}):action==="remove"?await db.rpc("sera_space_remove",{p_space:id,p_member:peer}):null;if(!r||r.error)throw new Error("Unable to complete collaboration action.");revalidatePath("/collaboration");}
type Space={id:string;name:string;description:string;owner_id:string;status:string;member_count:number};
export default async function Collaboration({searchParams}:{searchParams:Promise<{space?:string}>}){
const {user}=await requiredProfile("/collaboration");const db=await serverClient();const {data,error}=await db.rpc("sera_list_spaces");const spaces=(data||[]) as Space[];const params=await searchParams;const selected=spaces.find(s=>s.id===params.space&&s.status==="active");
const {data:messages}=selected?await db.rpc("sera_list_space_messages",{p_space:selected.id}):{data:[]};const chat=(messages||[]) as {id:string;sender_id:string;full_name:string;body:string;created_at:string}[];
const {data:members}=selected?await db.rpc("sera_space_members",{p_space:selected.id}):{data:[]};const roster=(members||[]) as {member_id:string;full_name:string;status:string}[];
const {data:connections}=await db.from("professional_connection_requests").select("requester_id,recipient_id").eq("status","accepted").or("requester_id.eq."+user.id+",recipient_id.eq."+user.id).limit(200);const peers=[...new Set((connections||[]).map(r=>r.requester_id===user.id?r.recipient_id:r.requester_id))];const cards=await memberCards(peers);
return <div className="container page sera-web-collab">
  <PageIntro icon={UsersRound} title="Collab" description="Private spaces to build, share and work together."/>
  {error&&<p role="alert">Spaces unavailable.</p>}
  <div className={"sera-collab-layout"+(selected?" has-space":"")}>
    <aside className="sera-collab-sidebar" aria-label="Your collaboration spaces">
      <div className="sera-collab-side-head"><h2>Your spaces</h2><span>{spaces.length}</span></div>
      <details className="sera-collab-create"><summary><Plus size={18}/> Create a space</summary><form action={act}><label>Space name<input name="name" required minLength={3} maxLength={80} placeholder="Name your team"/></label><label>Description<textarea name="description" maxLength={500} placeholder="What are you building together?"/></label><button name="action" value="create" className="button">Create space</button></form></details>
      <div className="sera-collab-space-list">{spaces.map(s=><article key={s.id} className={"sera-collab-space"+(selected?.id===s.id?" is-selected":"")}>
        {s.status==="active"?<Link href={"/collaboration?space="+s.id} aria-current={selected?.id===s.id?"page":undefined}><span className="sera-collab-space-mark">{s.name.slice(0,1).toUpperCase()}</span><span><strong>{s.name}</strong><small>{s.owner_id===user.id?"Owner":"Member"} · {s.member_count} members</small></span><ArrowUpRight size={16}/></Link>:<><strong>{s.name}</strong><small>Invitation · {s.member_count} members</small><p>{s.description}</p><form action={act}><input type="hidden" name="space" value={s.id}/><button name="action" value="accept" className="button">Accept</button><button name="action" value="decline" className="button secondary">Decline</button></form></>}
      </article>)}</div>
      {!spaces.length&&!error&&<p className="sera-collab-muted">No spaces yet. Create your first team.</p>}
    </aside>
    {selected?<section className="sera-collab-room" aria-label={selected.name}>
      <header className="sera-collab-room-head"><Link className="sera-collab-back" href="/collaboration" aria-label="Back to spaces"><ArrowLeft size={20}/></Link><span className="sera-collab-space-mark">{selected.name.slice(0,1).toUpperCase()}</span><div><h2>{selected.name}</h2><p>{selected.member_count} members</p></div><Link className="button secondary" href={"/collaboration/projects?space="+selected.id}><FolderKanban size={17}/> Projects</Link></header>
      {selected.description&&<p className="sera-collab-description">{selected.description}</p>}
      <section className="sera-collab-chat" aria-label="Team conversation">
        <div className="sera-collab-chat-head"><h3>Team conversation</h3><Link href={"/collaboration?space="+selected.id}>Refresh</Link></div>
        <div className="sera-collab-chat-scroll">{[...chat].reverse().map(m=><article key={m.id} className={"sera-collab-message"+(m.sender_id===user.id?" is-own":"")}><MemberAvatar name={m.full_name} photo={cards.get(m.sender_id)?.photo} size={34}/><div><strong>{m.sender_id===user.id?"You":m.full_name}</strong><p>{m.body}</p><time dateTime={m.created_at}>{new Date(m.created_at).toLocaleString("en-GB",{timeZone:"Africa/Addis_Ababa"})}</time></div></article>)}{!chat.length&&<div className="sera-collab-empty"><UsersRound size={28}/><strong>Start a team conversation</strong><p>Share an update or an idea with your team.</p></div>}</div>
        <form action={act} className="sera-collab-compose"><input type="hidden" name="space" value={selected.id}/><textarea name="body" required maxLength={4000} placeholder="Message your team" aria-label="Message your team"/><button name="action" value="message" className="button" aria-label="Send message"><Send size={19}/></button></form>
      </section>
      <details className="sera-collab-roster"><summary><UsersRound size={18}/> Team members · {roster.length}</summary><div className="sera-collab-roster-content">
        {roster.map(m=><div className="sera-collab-roster-row" key={m.member_id}><MemberAvatar name={m.full_name} photo={cards.get(m.member_id)?.photo} size={36}/><span><strong>{m.full_name}</strong><small>{m.status}</small></span>{selected.owner_id===user.id&&m.member_id!==user.id&&<form action={act}><input type="hidden" name="space" value={selected.id}/><input type="hidden" name="member" value={m.member_id}/><details><summary>Remove</summary><button name="action" value="remove" className="button secondary">Confirm removal</button></details></form>}</div>)}
        {selected.owner_id===user.id?<form action={act} className="sera-collab-invite"><input type="hidden" name="space" value={selected.id}/><select name="member" required aria-label="Invite connection"><option value="">Choose a connection</option>{peers.filter(id=>!roster.some(m=>m.member_id===id)).map(id=><option key={id} value={id}>{cards.get(id)?.name||"Sera member"}</option>)}</select><button name="action" value="invite" className="button">Invite</button></form>:<form action={act}><input type="hidden" name="space" value={selected.id}/><input type="hidden" name="member" value={user.id}/><details><summary>Leave space</summary><button name="action" value="remove" className="button secondary">Confirm leaving</button></details></form>}
      </div></details>
    </section>:<section className="sera-collab-welcome"><UsersRound size={42}/><h2>Work better together.</h2><p>Select a space to open your team conversation, projects and members.</p></section>}
  </div>
</div>;
}
