import "server-only";
import {cache} from "react";
import { serverClient } from "@/lib/supabase/server";
import { ASSET_BUCKET } from "@/lib/account/assets";

export type MemberIdentity = { name: string; photo: string | null; identityVerified?:boolean };
export async function memberCards(ids: string[]) {
  const cards = new Map<string, MemberIdentity>();
  const unique = [...new Set(ids)];
  if (!unique.length) return cards;
  const db = await serverClient();
  for (let offset = 0; offset < unique.length; offset += 200) {
    const batch=unique.slice(offset,offset+200);
    const [{data:badges},{data}]=await Promise.all([
      db.rpc("sera_identity_badges",{p_ids:batch}),
      db.rpc("member_cards",{ids:batch}),
    ]);
    const verified=new Set((badges??[]).map((r:{id:string})=>r.id));
    const rows = (data ?? []) as {
      id: string;
      full_name: string;
      avatar_path: string | null;
    }[];
    const paths = rows.flatMap((row) =>
      row.avatar_path ? [row.avatar_path] : [],
    );
    const urls = paths.length
      ? await db.storage.from(ASSET_BUCKET).createSignedUrls(paths, 300)
      : null;
    const signed = new Map(
      urls?.data?.map((item) => [item.path, item.signedUrl]),
    );
    for (const row of rows)
      cards.set(row.id, {
        name: row.full_name,
        identityVerified:verified.has(row.id),
        photo: signed.get(row.avatar_path) || null,
      });
  }
  return cards;
}

// Request-scoped only: never share member identities between requests/accounts.
export const memberCard=cache(async(id:string)=>(await memberCards([id])).get(id));
