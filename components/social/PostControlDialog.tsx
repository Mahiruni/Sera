"use client";
import {useEffect,useRef,type ReactNode} from "react";
import {X} from "lucide-react";
export function PostControlDialog({title,children,onClose,busy=false,compact=false}:{title:string;children:ReactNode;onClose:()=>void;busy?:boolean;compact?:boolean}){
 const ref=useRef<HTMLDialogElement>(null);
 const close=useRef(onClose);close.current=onClose;
 useEffect(()=>{const node=ref.current;if(!node)return;const prev=document.activeElement instanceof HTMLElement?document.activeElement:null;node.showModal();return()=>{node.close();prev?.focus()}},[]);
 return <dialog ref={ref} aria-label={title} className={"sera-control-dialog"+(compact?" sera-control-compact":"")} onCancel={e=>{e.preventDefault();if(!busy)close.current()}} onClick={e=>{if(e.target===e.currentTarget&&!busy)close.current()}}>
 <section className="sera-control-surface"><header className="sera-control-heading"><h2>{title}</h2><button type="button" aria-label="Close" disabled={busy} onClick={onClose}><X size={21}/></button></header>{children}</section></dialog>
}
export function PostConfirmation({title,message,primary,secondary="Cancel",destructive=false,busy=false,onCancel,onConfirm}:{title:string;message:string;primary:string;secondary?:string;destructive?:boolean;busy?:boolean;onCancel:()=>void;onConfirm:()=>void}){
 return <PostControlDialog title={title} onClose={onCancel} busy={busy} compact><p className="sera-control-explanation">{message}</p><footer className="sera-control-footer"><button type="button" className="sera-control-secondary" disabled={busy} onClick={onCancel}>{secondary}</button><button type="button" className={destructive?"sera-control-danger":"sera-control-primary"} disabled={busy} onClick={onConfirm}>{busy?"Please wait…":primary}</button></footer></PostControlDialog>
}
