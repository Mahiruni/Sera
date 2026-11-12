import {requiredProfile} from "@/lib/auth/profile";
import {serverClient} from "@/lib/supabase/server";
import {PostBin,type BinPost} from "@/components/social/PostBin";
export const dynamic="force-dynamic";
export const metadata={title:"Post bin",robots:{index:false,follow:false}};
export default async function Bin(){await requiredProfile("/settings/bin");const db=await serverClient();const {data,error}=await db.rpc("sera_list_post_bin");return <PostBin now={new Date().getTime()} posts={(data||[]) as BinPost[]} error={error?"Unable to load your bin. Please retry.":undefined}/>}
