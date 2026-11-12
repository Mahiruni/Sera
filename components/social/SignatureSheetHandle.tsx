"use client";
import {useEffect,useRef,useState} from "react";
export function SignatureSheetHandle({onClose,disabled=false}:{onClose:()=>void;disabled?:boolean}){
 const ref=useRef<HTMLButtonElement>(null);const start=useRef<number|null>(null);const gesture=useRef(false);
 const [expanded,setExpanded]=useState(false);
 useEffect(()=>{const sheet=ref.current?.parentElement;if(!sheet)return;const viewport=window.visualViewport;const resize=()=>{const height=viewport?.height??window.innerHeight;sheet.style.setProperty("--sera-sheet-height",height+"px");sheet.style.setProperty("--sera-sheet-bottom",Math.max(0,window.innerHeight-height-(viewport?.offsetTop??0))+"px");};resize();viewport?.addEventListener("resize",resize);viewport?.addEventListener("scroll",resize);window.addEventListener("resize",resize);return()=>{viewport?.removeEventListener("resize",resize);viewport?.removeEventListener("scroll",resize);window.removeEventListener("resize",resize);sheet.style.removeProperty("--sera-sheet-height");sheet.style.removeProperty("--sera-sheet-bottom");};},[]);
 useEffect(()=>{const sheet=ref.current?.parentElement;if(!sheet)return;sheet.toggleAttribute("data-sheet-expanded",expanded);return()=>sheet.removeAttribute("data-sheet-expanded");},[expanded]);
 return <button ref={ref} type="button" className="sera-signature-grip" aria-label={expanded?"Collapse sheet":"Expand sheet"} aria-expanded={expanded} disabled={disabled}
 onPointerDown={e=>{start.current=e.clientY;gesture.current=false;e.currentTarget.setPointerCapture(e.pointerId)}}
 onPointerCancel={()=>{start.current=null;gesture.current=false}}
 onPointerUp={e=>{if(start.current===null)return;const d=e.clientY-start.current;start.current=null;if(Math.abs(d)<35)return;gesture.current=true;if(d<0)setExpanded(true);else if(expanded)setExpanded(false);else if(d>55)onClose()}}
 onClick={()=>{if(gesture.current){gesture.current=false;return;}setExpanded(v=>!v)}}><span aria-hidden="true"/></button>;
}
