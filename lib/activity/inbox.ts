"use server";
import { currentUser, serverClient } from "@/lib/supabase/server";
import { memberCards } from "./members";

async function recipient() {
  const user = await currentUser();
  if (!user?.email_confirmed_at) throw new Error("Sign in to view activity.");
  return { db: await serverClient(), id: user.id };
}
export async function inbox(unreadOnly = false, before?: number) {
  const { db, id } = await recipient();
  let query = db
    .from("member_notifications")
    .select("id,kind,actor_id,post_id,comment_id,created_at,read_at")
    .eq("recipient_id", id)
    .order("id", { ascending: false })
    .limit(21);
  if (before && Number.isSafeInteger(before)) query = query.lt("id", before);
  if (unreadOnly) query = query.is("read_at", null);
  const [list, count] = await Promise.all([query, unreadCount()]);
  if (list.error) throw new Error("Activity could not be loaded.");
  const rows = (list.data || []).slice(0, 20);
  const people = await memberCards(rows.map((row) => row.actor_id));
  return {
    count,
    more: (list.data?.length || 0) > 20,
    rows: rows.map((row) => ({
      id: Number(row.id),
      kind: String(row.kind),
      name: people.get(row.actor_id)?.name || "Sera member",
      photo: people.get(row.actor_id)?.photo || null,
      date: String(row.created_at),
      unread: !row.read_at,
      href:
        "/posts/" +
        row.post_id +
        (row.comment_id
          ? "?thread=" + row.comment_id + "#comment-" + row.comment_id
          : ""),
    })),
  };
}
export async function unreadCount() {
  const { db, id } = await recipient();
  const result = await db
    .from("member_notifications")
    .select("id", { count: "exact", head: true })
    .eq("recipient_id", id)
    .is("read_at", null);
  if (result.error) throw new Error("Activity count unavailable.");
  return result.count || 0;
}
export async function markActivityRead(id: number, through = false) {
  if (!Number.isSafeInteger(id) || id <= 0)
    throw new Error("Invalid notification.");
  const { db, id: owner } = await recipient();
  const query = db
    .from("member_notifications")
    .update({ read_at: new Date().toISOString() })
    .eq("recipient_id", owner)
    .is("read_at", null);
  const { error } = await (through ? query.lte("id", id) : query.eq("id", id));
  if (error) throw new Error("Read status could not be saved.");
}
