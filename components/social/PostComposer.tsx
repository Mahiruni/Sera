"use client";
import { startTransition, useActionState, useEffect, useRef, useState } from "react";
import Image from "next/image";
import { ImagePlus, Send, FileText, ChevronDown, Lightbulb, Quote, Award, FolderKanban, MessageCircle, Save, AtSign, Smile, Video, BarChart3 } from "lucide-react";
import { createPost, updatePost } from "@/lib/social/actions";
import { postKinds, validMedia, type PostKind } from "@/lib/social/validation";
import type { SocialPost } from "@/lib/social/data";
import { Submit } from "./forms";
import { restoreSeraDraft, saveSeraDraft, seraDraftKey } from "./post-drafts";
import "./composer-premium.css";

const labels: Record<PostKind, string> = { idea:"Idea", photos:"Photo", quote:"Quote", talent:"Talent", project:"Project", achievement:"Achievement", question:"Discussion" };
const icons = {idea:Lightbulb,photos:ImagePlus,quote:Quote,talent:Award,project:FolderKanban,achievement:Award,question:MessageCircle};
export function PostComposer({ post, initialKind = "idea", draftOwnerId, onPublished }: { post?: SocialPost; initialKind?: PostKind; draftOwnerId?: string; onPublished?: (postId: string) => void }) {
  const draftKey = draftOwnerId ? seraDraftKey(draftOwnerId, post?.id) : null;
  const [kind,setKind] = useState<PostKind>(post?.kind || initialKind);
  const [body,setBody] = useState(post?.body || "");
  const [title,setTitle] = useState(post?.title || "");
  const [quoteSource,setQuoteSource] = useState(post?.quote_source || "");
  const [category,setCategory] = useState(post?.category || "");
  const [location,setLocation] = useState(post?.location || "");
  const [skills,setSkills] = useState(post?.skills.join(", ") || "");
  const [projectUrl,setProjectUrl] = useState(post?.project_url || "");
  const [draftLoaded,setDraftLoaded] = useState(false);
  const [showHeadline,setShowHeadline] = useState(Boolean(post?.title) || initialKind === "project");
  const [detailsOpen,setDetailsOpen] = useState(Boolean(post) || initialKind === "photos");
  const [previews,setPreviews] = useState<string[]>([]);
  const [fileError,setFileError] = useState("");
  const [draftMessage,setDraftMessage] = useState("");
  const urls = useRef<string[]>([]);
  const fileInput = useRef<HTMLInputElement>(null);
  const form = useRef<HTMLFormElement>(null);
  const textarea = useRef<HTMLTextAreaElement>(null);
  const [state,action,pending] = useActionState(async (previous: import("@/lib/social/actions").SocialState,data:FormData) => {
    const result = await (post ? updatePost : createPost)(previous,data);
    if (result.success) {
      try { if (draftKey) localStorage.removeItem(draftKey); } catch {}
    }
    if (result.success && !post) {
      form.current?.reset(); urls.current.forEach(URL.revokeObjectURL); urls.current=[]; setPreviews([]); setBody(""); setTitle(""); setFileError("");
    }
    return result;
  },{});
  useEffect(()=>{ if(state.postId && onPublished) onPublished(state.postId); },[state.postId,onPublished]);
  useEffect(()=>()=>{ urls.current.forEach(URL.revokeObjectURL); },[]);
  useEffect(() => {
    // Restore after hydration: avoids SSR/client mismatches in the sheet.
    const timeout = window.setTimeout(() => {
      const saved = draftKey ? restoreSeraDraft(draftKey) : null;
      if (saved) {
        setKind(saved.kind); setBody(saved.body); setTitle(saved.title);
        setQuoteSource(saved.quoteSource); setCategory(saved.category);
        setLocation(saved.location); setSkills(saved.skills);
        setProjectUrl(saved.projectUrl); setShowHeadline(saved.showHeadline);
        setDetailsOpen(saved.detailsOpen);
        setDraftMessage("Draft restored. Attachments need to be selected again.");
      }
      setDraftLoaded(true);
    }, 0);
    return () => window.clearTimeout(timeout);
  }, [draftKey]);

  // Automatic local text draft; Flush on sheet dismissal handles rapid edits.
  useEffect(() => {
    if (!draftLoaded || !draftKey || state.success) return;
    const timeout = window.setTimeout(() => {
      saveSeraDraft(draftKey, {
        kind, body, title, quoteSource, category, location, skills, projectUrl,
        showHeadline, detailsOpen, savedAt: Date.now()
      });
    }, 400);
    return () => window.clearTimeout(timeout);
  }, [draftLoaded, draftKey, kind, body, title, quoteSource, category,
      location, skills, projectUrl, showHeadline, detailsOpen, state.success]);

  const saveDraft=()=>{
    if (!draftKey) { setDraftMessage("Cannot save this draft until you are signed in."); return; }
    const okay = saveSeraDraft(draftKey, {
      kind, body, title, quoteSource, category, location, skills, projectUrl,
      showHeadline, detailsOpen, savedAt: Date.now()
    });
    setDraftMessage(okay ? "Text draft saved on this device. Attachments are not saved." : "Draft could not be saved");
  };
  const chooseFiles=(files:File[])=>{
    urls.current.forEach(URL.revokeObjectURL);
    const valid=validMedia(files);
    urls.current=valid?files.map(file=>URL.createObjectURL(file)):[];
    setPreviews(urls.current);
    setFileError(valid?"":"Choose up to four JPG, PNG or WebP images totaling 3 MB or less.");
  };
  return <section className="social-composer sera-premium-composer" aria-label={post?"Edit post":"Create a post"}>
    <form ref={form} onSubmit={e=>{e.preventDefault();if(fileError||pending)return;const data=new FormData(e.currentTarget);if(kind!=="quote"&&!String(data.get("title")||"").trim()){const derived=String(data.get("body")||"").trim().replace(/\s+/g," ").slice(0,140);data.set("title",derived.length>=3?derived:"Photo update");}startTransition(()=>action(data));}}>
      {post&&<input type="hidden" name="post_id" value={post.id}/>}
      <input type="hidden" name="kind" value={kind}/>
      {!post&&<div className="sera-premium-types" role="group" aria-label="Post type">{postKinds.map(k=>{const Icon=icons[k];return <button type="button" key={k} aria-pressed={kind===k} onClick={()=>{setKind(k);if(k==="project")setShowHeadline(true);if(k==="photos")setDetailsOpen(true);}}><Icon size={17}/>{labels[k]}</button>;})}</div>}
      {(showHeadline||kind==="project")?<label className="sera-premium-headline"><span>Headline</span><input name="title" maxLength={140} value={title} onChange={e=>setTitle(e.target.value)} placeholder="Give your post a headline"/></label>:<input type="hidden" name="title" value={title}/>}
      <div className="sera-premium-editor"><label htmlFor="sera-premium-body" className="sr-only">{kind==="quote"?"Your quote":"Your story"}</label><textarea id="sera-premium-body" ref={textarea} name="body" rows={5} maxLength={kind==="quote"?600:10000} minLength={kind==="quote"?3:1} required={kind!=="photos"} value={body} onChange={e=>{setBody(e.target.value);e.target.style.height="auto";e.target.style.height=Math.max(170,e.target.scrollHeight)+"px";}} placeholder={kind==="quote"?"Share a thought worth remembering…":kind==="project"?"What have you been building?":"What would you like to share with your professional community?"}/><span className="sera-premium-counter">{body.length.toLocaleString()} / {(kind==="quote"?600:10000).toLocaleString()}</span></div>
      {kind==="quote"?<label className="sera-premium-headline">Attribution <input name="quote_source" maxLength={120} value={quoteSource} onChange={e=>setQuoteSource(e.target.value)} placeholder="Who said it?"/></label>:<input type="hidden" name="quote_source" value={quoteSource}/>}
      <div className="sera-premium-toolbar" aria-label="Post attachments and tools">
        <button type="button" onClick={()=>{setDetailsOpen(true);fileInput.current?.click();}}><ImagePlus size={21} color="#19a63b"/><span>Photo</span></button>
        <button type="button" disabled title="Video publishing is coming soon"><Video size={21}/><span>Video</span></button>
        <button type="button" disabled title="Document publishing is coming soon"><FileText size={21}/><span>Document</span></button>
        <button type="button" disabled title="Mentions are coming soon"><AtSign size={21}/><span>Mention</span></button>
        <button type="button" disabled title="Polls are coming soon"><BarChart3 size={21}/><span>Poll</span></button>
        <button type="button" onClick={()=>{textarea.current?.focus();setBody(v=>v+"✨");}}><Smile size={21}/><span>Emoji</span></button>
      </div>
      <div className="sera-premium-extras"><button type="button" onClick={()=>setShowHeadline(v=>!v)}><FileText size={16}/> {showHeadline?"Hide headline":"Add headline"}</button><button type="button" onClick={()=>setDetailsOpen(v=>!v)} aria-expanded={detailsOpen}><ChevronDown size={16}/> Details & skills</button></div>
      <div className="sera-premium-details" hidden={!detailsOpen}>
        <div className="social-form-grid"><label>Field<input name="category" maxLength={60} value={category} onChange={e=>setCategory(e.target.value)} placeholder="Engineering, design…"/></label><label>Location<input name="location" maxLength={120} value={location} onChange={e=>setLocation(e.target.value)} placeholder="Addis Ababa"/></label></div>
        <label>Skills<input name="skills" maxLength={400} value={skills} onChange={e=>setSkills(e.target.value)} placeholder="React, design, engineering"/></label>
        <label>Project link<input type="url" name="project_url" maxLength={500} value={projectUrl} onChange={e=>setProjectUrl(e.target.value)} placeholder="https://"/></label>
      </div>
      {!post&&<input ref={fileInput} className="sera-premium-file" type="file" name="media" multiple accept="image/jpeg,image/png,image/webp" required={kind==="photos"} onChange={e=>chooseFiles([...(e.target.files||[])])}/>}
      {!!previews.length&&<div className="social-preview">{previews.map((url,i)=><Image unoptimized width={90} height={90} key={url} src={url} alt={`Selected photo ${i+1}`}/>)}</div>}
      {(fileError||state.error)&&<p role="alert" className="social-error">{fileError||state.error}</p>}
      {(state.success||draftMessage)&&<p role="status" className="social-status">{state.success||draftMessage}</p>}
      <div className="sera-premium-footer"><button type="button" className="sera-premium-save" onClick={saveDraft} disabled={pending}><Save size={19}/> Save draft</button><Submit className="sera-premium-publish" disabled={Boolean(fileError)||pending||(kind!=="photos"&&body.trim().length<(kind==="quote"?3:1))}><Send size={19}/>{pending?"Publishing…":post?"Save changes":"Publish post"}</Submit></div>
    </form>
  </section>;
}
