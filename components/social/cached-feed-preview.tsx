"use client";
import {useEffect,useState} from "react";
import {readPublicFeed,savePublicFeed,type PublicFeedPreview} from "@/lib/social/public-feed-cache";
export function SaveFeedPreview({owner,rows}:{owner:string;rows:PublicFeedPreview[]}){
  useEffect(()=>{void savePublicFeed(owner,rows).catch(()=>{});},[owner,rows]);
  return null;
}
export function CachedFeedPreview({owner,failed=false,href="/feed"}:{owner:string;failed?:boolean;href?:string}){
  const [rows,setRows]=useState<PublicFeedPreview[]>([]);
  useEffect(()=>{let alive=true;setRows([]);void readPublicFeed(owner).then(r=>{if(alive)setRows(r)}).catch(()=>{});return()=>{alive=false};},[owner]);
  return <div className="social-post-list" aria-busy={!failed}>
    {rows.length>0?<><p role="status">Previously loaded public posts · {failed?"unable to refresh":"checking for updates"}</p>
      {rows.map(row=><article className="social-card" key={row.id}>
        <strong>{row.author}</strong>{row.headline&&<p>{row.headline}</p>}{row.title&&<h3>{row.title}</h3>}<p style={{whiteSpace:"pre-wrap"}}>{row.body}</p>
      </article>)}</>:failed?<div className="social-empty" role="alert"><h2>Unable to load your feed</h2><p>Check your connection and retry.</p></div>:<div className="sera-feed-loading" aria-label="Loading posts">{[0,1,2].map(i=><div className="social-card" key={i}><span className="sera-feed-skeleton-line"/><span className="sera-feed-skeleton-line short"/></div>)}</div>}
    {failed&&<a href={href}>Retry</a>}
  </div>;
}
