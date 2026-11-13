"use client";
import {useEffect} from "react";
import {useRouter} from "next/navigation";
import {subscribeSeraRefresh} from "@/lib/network/refresh-coordinator";
export function InboxRefresh(){
  const router=useRouter();
  useEffect(()=>subscribeSeraRefresh(()=>router.refresh(),true),[router]);
  return null;
}
