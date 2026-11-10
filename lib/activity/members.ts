import "server-only";
import { serverClient } from "@/lib/supabase/server";
import { ASSET_BUCKET } from "@/lib/account/assets";

export type MemberIdentity = { name: string; photo: string | null };
export async function memberCards(ids: string[]) {
  const cards = new Map<string, MemberIdentity>();
  const unique = [...new Set(ids)];
  if (!unique.length) return cards;
  const db = await serverClient();
  for (let offset = 0; offset < unique.length; offset += 200) {
    const { data } = await db.rpc("member_cards", {
      ids: unique.slice(offset, offset + 200),
    });
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
        photo: signed.get(row.avatar_path) || null,
      });
  }
  return cards;
}
