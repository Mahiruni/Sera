"use client";
import { useEffect, useId, useRef, useState, type FormEvent, type KeyboardEvent } from "react";
import { useRouter } from "next/navigation";
import { Search, ArrowRight, Clock3, X } from "lucide-react";
import { sanitizeDiscoveryQuery } from "@/lib/network/model";
import { recentSearches, rememberSearch, type SearchSuggestion } from "@/lib/network/search-model";

export function UniversalSearchBox({ query="", userId, prominent=false }: { query?: string; userId: string; prominent?: boolean }) {
  const router=useRouter();
  const id=useId();
  const root=useRef<HTMLDivElement>(null);
  const input=useRef<HTMLInputElement>(null);
  const [value,setValue]=useState(query);
  const [open,setOpen]=useState(false);
  const [history,setHistory]=useState<string[]>([]);
  const [results,setResults]=useState<SearchSuggestion[]>([]);
  const [resultQuery,setResultQuery]=useState("");
  const [active,setActive]=useState(-1);
  const [loading,setLoading]=useState(false);
  const [failed,setFailed]=useState(false);
  const storageKey="sera.search.recent."+userId;
  const clean=sanitizeDiscoveryQuery(value);
  const suggestions=resultQuery===clean?results:[];
  const options=clean?suggestions.map(s=>({label:s.label,kind:s.kind,id:s.id})):history.map(s=>({label:s,kind:"Recent",id:s}));
  function readHistory() {
    try { return recentSearches(JSON.parse(sessionStorage.getItem(storageKey)||"[]")); } catch { return []; }
  }
  function saveHistory(next:string[]) {
    setHistory(next);
    try { sessionStorage.setItem(storageKey,JSON.stringify(next)); } catch { /* optional history */ }
  }
  function submit(term:string) {
    const q=sanitizeDiscoveryQuery(term);
    if(q) saveHistory(rememberSearch(readHistory(),q));
    setValue(q); setOpen(false); setActive(-1);
    router.push("/discover?tab=all"+(q?"&q="+encodeURIComponent(q):""));
  }
  function onSubmit(event:FormEvent<HTMLFormElement>) {
    event.preventDefault(); submit(active>=0&&options[active]?options[active].label:value);
  }
  function onKey(event:KeyboardEvent<HTMLInputElement>) {
    if(event.nativeEvent.isComposing) return;
    if(event.key==="Escape") {setOpen(false);setActive(-1);}
    if(event.key==="ArrowDown"||event.key==="ArrowUp") {
      event.preventDefault(); setOpen(true);
      setActive(old=>options.length?(old+(event.key==="ArrowDown"?1:options.length-1)+options.length)%options.length:-1);
    }
  }
  useEffect(()=>{
    if(!open||clean.length<2) return;
    const controller=new AbortController();
    const timer=window.setTimeout(async()=>{
      setLoading(true);setFailed(false);
      try {
        const response=await fetch("/api/search/suggestions?q="+encodeURIComponent(clean),{signal:controller.signal,cache:"no-store"});
        if(!response.ok) throw new Error("Unavailable");
        const body=await response.json() as {suggestions:SearchSuggestion[]};
        if(!controller.signal.aborted){setResults(body.suggestions);setResultQuery(clean);}
      } catch {if(!controller.signal.aborted){setResults([]);setResultQuery(clean);setFailed(true);}}
      finally {if(!controller.signal.aborted)setLoading(false);}
    },250);
    return ()=>{window.clearTimeout(timer);controller.abort();};
  },[clean,open]);
  useEffect(()=>{
    function outside(event:PointerEvent) {if(root.current&&!root.current.contains(event.target as Node)){setOpen(false);setActive(-1);}}
    document.addEventListener("pointerdown",outside);
    return ()=>document.removeEventListener("pointerdown",outside);
  },[]);
  const expanded=open && (clean.length>=2||(!clean&&history.length>0));
  return <div className={"network-search-shell"+(prominent?" network-search-shell-prominent":"")} ref={root}>
    <form action="/discover" role="search" className={"network-search"+(prominent?" network-discover-search":"")} onSubmit={onSubmit}>
      <Search size={18} aria-hidden="true"/>
      <label className="sr-only" htmlFor={id}>Search SERA</label>
      <input ref={input} id={id} name="q" type="search" value={value} maxLength={80} placeholder="People, jobs, posts, skills…" autoComplete="off" role="combobox" aria-autocomplete="list" aria-expanded={expanded} aria-controls={id+"-options"} aria-activedescendant={expanded&&active>=0&&options[active]?id+"-option-"+active:undefined}
        onFocus={()=>{setHistory(readHistory());setOpen(true);}} onChange={e=>{setValue(e.target.value);setOpen(true);setActive(-1);setLoading(false);setFailed(false);}} onKeyDown={onKey}/>
      <input type="hidden" name="tab" value="all"/>
      <button type="submit" aria-label="Search SERA"><ArrowRight size={18}/></button>
    </form>
    {expanded&&<div className="network-search-popover">
      <div className="network-search-popover-head"><strong>{clean?"Suggestions":"Recent searches"}</strong>{!clean&&<button type="button" onClick={()=>saveHistory([])}>Clear all</button>}</div>
      <div id={id+"-options"} role="listbox" aria-label={clean?"Search suggestions":"Recent searches"}>
        {options.map((option,i)=><div className="network-search-option-row" key={option.kind+option.id}>
          <div role="option" id={id+"-option-"+i} aria-selected={active===i} className="network-search-option" onPointerDown={e=>e.preventDefault()} onClick={()=>submit(option.label)}>
            {clean?<Search size={15} aria-hidden="true"/>:<Clock3 size={15} aria-hidden="true"/>}<span>{option.label}</span>{clean&&<small>{option.kind}</small>}
          </div>
          {!clean&&<button type="button" aria-label={"Remove "+option.label} onClick={()=>saveHistory(history.filter(q=>q!==option.label))}><X size={16}/></button>}
        </div>)}
      </div>
      {clean&&<p className="network-search-status" role="status">{loading||resultQuery!==clean?"Searching…":failed?"Suggestions unavailable. You can still search.":!options.length?"No suggestions yet. Try searching this keyword.":""}</p>}
      {clean&&<button className="network-search-all" type="button" onClick={()=>submit(value)}>Search for “{clean}” <ArrowRight size={15}/></button>}
    </div>}
  </div>;
}
