"use client";
import type {ButtonHTMLAttributes,ReactNode} from "react";
import "./signature-tokens.css";
import "./signature.css";
type Tone="primary"|"secondary"|"activity"|"ghost";
export function SignatureButton({tone="primary",children,...props}:ButtonHTMLAttributes<HTMLButtonElement>&{tone?:Tone}){return <button {...props} type={props.type??"button"} className={["sig-btn",props.className].filter(Boolean).join(" ")} data-tone={tone}>{children}</button>}
export function SignatureIconButton({label,tone="default",children,...props}:ButtonHTMLAttributes<HTMLButtonElement>&{label:string;tone?:"default"|"call"}){return <button {...props} type={props.type??"button"} aria-label={label} className="sig-icon" data-tone={tone}>{children}</button>}
export function SignatureCard({children}:{children:ReactNode}){return <section className="sig-card">{children}</section>}
export function SignatureBadge({children}:{children:ReactNode}){return <span className="sig-badge">{children}</span>}
export function SignatureSwitch({label,checked,onChange}:{label:string;checked:boolean;onChange:(value:boolean)=>void}){return <label className="sig-switch"><span>{label}</span><input type="checkbox" role="switch" checked={checked} onChange={e=>onChange(e.target.checked)}/></label>}
