"use client";
import {useEffect} from "react";
import {useRouter} from "next/navigation";
import {subscribeSeraRefresh} from "@/lib/network/refresh-coordinator";
export function NotificationRefresh(){
  const router=useRouter();
  useEffect(()=>subscribeSeraRefresh(()=>router.refresh()),[router]);
  return null;
}
