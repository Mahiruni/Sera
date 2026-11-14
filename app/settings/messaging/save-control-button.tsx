"use client";
import {useFormStatus} from "react-dom";
import type {ReactNode} from "react";
import styles from "./control-center.module.css";
export function SaveControlButton({children,pendingLabel}:{children:ReactNode;pendingLabel:string}){const {pending}=useFormStatus();return <button className={styles.save} type="submit" disabled={pending} aria-busy={pending}>{pending?pendingLabel:children}</button>;}
