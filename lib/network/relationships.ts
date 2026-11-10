"use server";
import { revalidatePath } from "next/cache";
import { currentUser, serverClient } from "@/lib/supabase/server";

type NetworkAction = "follow" | "unfollow" | "request" | "accept" | "decline" | "withdraw";
const validId = (id: unknown): id is string =>
  typeof id === "string" && /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(id);

export async function changeProfessionalRelationship(form: FormData) {
  const user = await currentUser();
  if (!user?.email_confirmed_at) return;
  const action = form.get("action") as NetworkAction;
  const target = form.get("target");
  if (!validId(target) || target === user.id) return;
  const db = await serverClient();
  if (action === "follow") {
    await db.from("professional_follows").upsert(
      { follower_id: user.id, followed_id: target },
      { onConflict: "follower_id,followed_id", ignoreDuplicates: true },
    );
  } else if (action === "unfollow") {
    await db.from("professional_follows").delete()
      .eq("follower_id", user.id).eq("followed_id", target);
  } else if (action === "request") {
    await db.from("professional_connection_requests").insert({
      requester_id: user.id, recipient_id: target,
    });
  } else if (action === "accept" || action === "decline") {
    await db.from("professional_connection_requests")
      .update({ status: action === "accept" ? "accepted" : "declined" })
      .eq("requester_id", target).eq("recipient_id", user.id).eq("status", "pending");
  } else if (action === "withdraw") {
    await db.from("professional_connection_requests")
      .update({ status: "withdrawn" })
      .eq("requester_id", user.id).eq("recipient_id", target).eq("status", "pending");
  }
  revalidatePath("/connections");
  revalidatePath("/discover");
}
