"use client";
import {postAudiences,audienceLabels,readAudience,type PostAudience} from "@/lib/social/audience";
import { startTransition, useActionState, useEffect, useRef, useState } from "react";
import Image from "next/image";
import { ImagePlus, Send, FileText, ChevronDown, Lightbulb, Quote, Award, FolderKanban, MessageCircle, Save, Smile } from "lucide-react";
import { createPost, updatePost } from "@/lib/social/actions";
import { postKinds, validMedia, type PostKind } from "@/lib/social/validation";
import type { SocialPost } from "@/lib/social/data";
import { Submit } from "./forms";
import {PostConfirmation} from "./PostControlDialog";
import {PostMedia} from "./post-media";
import { restoreSeraDraft, saveSeraDraft, seraDraftKey } from "./post-drafts";
import "./composer-premium.css";

const labels: Record<PostKind, string> = { idea:"Idea", photos:"Photo", quote:"Quote", talent:"Talent", project:"Project", achievement:"Achievement", question:"Discussion" };
const icons = {idea:Lightbulb,photos:ImagePlus,quote:Quote,talent:Award,project:FolderKanban,achievement:Award,question:MessageCircle};
export function PostComposer({ post, initialKind = "idea", draftOwnerId, onPublished }: { post?: SocialPost; initialKind?: PostKind; draftOwnerId?: string; onPublished?: (postId: string) => void }) {
  const draftKey = draftOwnerId ? seraDraftKey(draftOwnerId, post?.id) : null;
  const [audience,setAudience]=useState<PostAudience>(post?.audience||"public");
  const [kind,setKind] = useState<PostKind>(post?.kind || initialKind);
  const repost=Boolean(post?.repost_of);
  const [body,setBody] = useState(repost?post?.repost_note||"":post?.body||"");
  const [confirmEdit,setConfirmEdit]=useState<FormData|null>(null);
  const [title,setTitle] = useState(post?.title || "");
  const [quoteSource,setQuoteSource] = useState(post?.quote_source || "");
  const [category,setCategory] = useState(post?.category || "");
  const [location,setLocation] = useState(post?.location || "");
  const [skills,setSkills] = useState(post?.skills.join(", ") || "");
  const [projectUrl,setProjectUrl] = useState(post?.project_url || "");
  const [draftLoaded,setDraftLoaded] = useState(false);
  const [showHeadline,setShowHeadline] = useState(Boolean(post?.title) || initialKind === "project");
  const [detailsOpen,setDetailsOpen] = useState(Boolean(post) || initialKind === "photos");
  const [emojiOpen,setEmojiOpen]=useState(false);
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
        setAudience(readAudience(saved.audience)||"only_me"); setKind(saved.kind); setBody(saved.body); setTitle(saved.title);
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
        kind, audience, body, title, quoteSource, category, location, skills, projectUrl,
        showHeadline, detailsOpen, savedAt: Date.now()
      });
    }, 400);
    return () => window.clearTimeout(timeout);
  }, [draftLoaded, draftKey, kind, audience, body, title, quoteSource, category,
      location, skills, projectUrl, showHeadline, detailsOpen, state.success]);

  const saveDraft=()=>{
    if (!draftKey) { setDraftMessage("Cannot save this draft until you are signed in."); return; }
    const okay = saveSeraDraft(draftKey, {
      kind, audience, body, title, quoteSource, category, location, skills, projectUrl,
      showHeadline, detailsOpen, savedAt: Date.now()
    });
    setDraftMessage(okay ? "Text draft saved on this device. Attachments are not saved." : "Draft could not be saved");
  };
  const removePhoto=(index:number)=>{const input=fileInput.current;if(!input?.files)return;try{const transfer=new DataTransfer();Array.from(input.files).filter((_,i)=>i!==index).forEach(f=>transfer.items.add(f));input.files=transfer.files;chooseFiles(Array.from(transfer.files));}catch{input.value="";chooseFiles([]);setDraftMessage("Choose your remaining photos again.");}};
  const chooseFiles=(files:File[])=>{
    urls.current.forEach(URL.revokeObjectURL);
    const valid=validMedia(files);
    urls.current=valid?files.map(file=>URL.createObjectURL(file)):[];
    setPreviews(urls.current);
    setFileError(valid?"":"Choose up to four JPG, PNG or WebP images totaling 3 MB or less.");
  };
  return <section className="social-composer sera-premium-composer" aria-label={post?"Edit post":"Create a post"}>
    <form ref={form} aria-busy={pending} onSubmit={e=>{e.preventDefault();if(fileError||pending)return;const data=new FormData(e.currentTarget);if(kind!=="quote"&&!String(data.get("title")||"").trim()){const derived=String(data.get("body")||"").trim().replace(/\s+/g," ").slice(0,140);data.set("title",derived.length>=3?derived:"Photo update");}if(post)setConfirmEdit(data);else startTransition(()=>action(data));}}>
      {post&&<input type="hidden" name="post_id" value={post.id}/>}
      <input type="hidden" name="kind" value={kind}/>
      {!post&&<label className="sera-post-audience">Audience <select name="audience" value={audience} onChange={e=>setAudience(e.target.value as PostAudience)}>{postAudiences.map(a=><option key={a} value={a}>{audienceLabels[a]}</option>)}</select><small>{audience==="public"?"Visible to SERA members":audience==="connections"?"Your accepted connections":audience==="followers"?"People who follow you":"Only you"}</small></label>}
      {!post&&<div className="sera-premium-types" role="group" aria-label="Post type">{postKinds.map(k=>{const Icon=icons[k];return <button type="button" key={k} aria-pressed={kind===k} onClick={()=>{setKind(k);if(k==="project")setShowHeadline(true);if(k==="photos")fileInput.current?.click();}}><Icon size={17}/>{labels[k]}</button>;})}</div>}
      {!repost&&(showHeadline||kind==="project")?<label className="sera-premium-headline"><span>Headline</span><input name="title" maxLength={140} value={title} onChange={e=>setTitle(e.target.value)} placeholder="Give your post a headline"/></label>:<input type="hidden" name="title" value={title}/>}
      <div className="sera-premium-editor"><label htmlFor="sera-premium-body" className="sr-only">{kind==="quote"?"Your quote":"Your story"}</label><textarea id="sera-premium-body" ref={textarea} name="body" rows={8} maxLength={repost?400:kind==="quote"?600:10000} minLength={repost?0:kind==="quote"?3:1} required={!repost&&kind!=="photos"} value={body} onChange={e=>{setBody(e.target.value);e.target.style.height="auto";e.target.style.height=Math.max(160,e.target.scrollHeight)+"px";}} placeholder={kind==="quote"?"Share a thought worth remembering…":kind==="project"?"What have you been building?":"What would you like to share with your professional community?"}/><span className="sera-premium-counter">{body.length.toLocaleString()} / {(repost?400:kind==="quote"?600:10000).toLocaleString()}</span></div>
      {kind==="quote"?<label className="sera-premium-headline">Attribution <input name="quote_source" maxLength={120} value={quoteSource} onChange={e=>setQuoteSource(e.target.value)} placeholder="Who said it?"/></label>:<input type="hidden" name="quote_source" value={quoteSource}/>}
      {!post&&<div className="sera-premium-toolbar" aria-label="Post attachments and tools">
        <button type="button" onClick={()=>{fileInput.current?.click();}}><ImagePlus size={21} color="#19a63b"/><span>Photo</span></button>
        <button type="button" aria-expanded={emojiOpen} onClick={()=>setEmojiOpen(v=>!v)}><Smile size={21}/><span>Emoji</span></button>
      </div>}
      {emojiOpen&&<div className="sera-emoji-picker" role="group" aria-label="Choose emoji">{["✨","👍","👏","💡","🎉","❤️","🚀","🤝","😊","🔥"].map(emoji=><button type="button" key={emoji} onClick={()=>{const el=textarea.current;const start=el?.selectionStart??body.length,end=el?.selectionEnd??start;const next=body.slice(0,start)+emoji+body.slice(end);if(next.length<=(kind==="quote"?600:10000))setBody(next);setEmojiOpen(false);requestAnimationFrame(()=>{el?.focus();el?.setSelectionRange(start+emoji.length,start+emoji.length);});}}>{emoji}</button>)}</div>}
      {post&&!repost&&<PostMedia images={post.images}/>}
      {!repost&&<div className="sera-premium-extras"><button type="button" onClick={()=>setShowHeadline(v=>!v)}><FileText size={16}/> {showHeadline?"Hide headline":"Add headline"}</button><button type="button" onClick={()=>setDetailsOpen(v=>!v)} aria-expanded={detailsOpen}><ChevronDown size={16}/> Details & skills</button></div>}
      <div className="sera-premium-details" hidden={repost||!detailsOpen}>
        <div className="social-form-grid"><label>Field<input name="category" maxLength={60} value={category} onChange={e=>setCategory(e.target.value)} placeholder="Engineering, design…"/></label><label>Location<input name="location" maxLength={120} value={location} onChange={e=>setLocation(e.target.value)} placeholder="Addis Ababa"/></label></div>
        <label>Skills<input name="skills" maxLength={400} value={skills} onChange={e=>setSkills(e.target.value)} placeholder="React, design, engineering"/></label>
        <label>Project link<input type="url" name="project_url" maxLength={500} value={projectUrl} onChange={e=>setProjectUrl(e.target.value)} placeholder="https://"/></label>
      </div>
      {!post&&<input ref={fileInput} className="sera-premium-file" type="file" name="media" multiple accept="image/jpeg,image/png,image/webp" required={kind==="photos"} onChange={e=>chooseFiles([...(e.target.files||[])])}/>}
      {!!previews.length&&<div className="social-preview">{previews.map((url,i)=><div key={url} className="sera-photo-preview"><Image unoptimized width={90} height={90} src={url} alt={`Selected photo ${i+1}`}/><button type="button" onClick={()=>removePhoto(i)} aria-label={`Remove photo ${i+1}`}>×</button></div>)}</div>}
      {pending&&<p role="status" className="social-status">Publishing securely… {previews.length?"Uploading and checking your photos.":""}</p>}
      {(fileError||state.error)&&<p role="alert" className="social-error">{fileError||state.error}</p>}
      {(state.success||draftMessage)&&<p role="status" className="social-status">{state.success||draftMessage}</p>}
      <div className="sera-premium-footer"><button type="button" className="sera-premium-save" onClick={saveDraft} disabled={pending}><Save size={19}/> Save draft</button><Submit className="sera-premium-publish" disabled={Boolean(fileError)||pending||(!post&&kind==="photos"&&!previews.length)||(!repost&&kind!=="photos"&&body.trim().length<(kind==="quote"?3:1))}><Send size={19}/>{pending?"Publishing…":post?"Save changes":"Post"}</Submit></div>
    </form>
    {confirmEdit&&<PostConfirmation title="Publish your changes?" message="Your updated post will replace the current version. Existing likes and comments will remain." primary="Publish changes" secondary="Keep editing" onCancel={()=>setConfirmEdit(null)} onConfirm={()=>{const data=confirmEdit;setConfirmEdit(null);startTransition(()=>action(data));}}/>}
  </section>;
}
