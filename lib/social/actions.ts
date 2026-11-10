"use server";
import { serverClient } from "@/lib/supabase/server";
import { revalidatePath } from "next/cache";
import { redirect } from "next/navigation";
import { randomUUID } from "node:crypto";
import sharp from "sharp";
import {
  MEDIA_BUCKET,
  MEDIA_LIMIT,
  readPost,
  readComment,
  uuid,
  validMedia,
} from "./validation";
export type SocialState = { error?: string; success?: string; postId?: string };
async function member() {
  const db = await serverClient();
  const {
    data: { user },
  } = await db.auth.getUser();
  return user?.email_confirmed_at ? { db, user } : null;
}
function refresh(id: string) {
  revalidatePath("/feed");
  revalidatePath("/saved");
  revalidatePath("/posts/" + id);
  revalidatePath("/posts/" + id + "/edit");
  revalidatePath("/members/[id]", "page");
}
export async function createPost(
  _: SocialState,
  form: FormData,
): Promise<SocialState> {
  const session = await member();
  if (!session) return { error: "Sign in with a verified email to publish." };
  const input = readPost(form);
  const files = form
    .getAll("media")
    .filter((f): f is File => f instanceof File && f.size > 0);
  if (
    !input ||
    !validMedia(files) ||
    (input.kind === "photos" && !files.length)
  )
    return {
      error:
        "Check the post details. Choose up to four JPG, PNG or WebP images, totaling at most 3 MB.",
    };
  const { db } = session;
  // Draft -> images -> publication. A failed upload never leaves a partial post in the feed.
  const draft = await db
    .from("posts")
    .insert({ ...input, status: "draft" })
    .select("id")
    .single();
  if (draft.error)
    return {
      error:
        "Could not create your post. Please retry; daily publishing limits may apply.",
    };
  const id = draft.data.id as string;
  const paths: string[] = [];
  try {
    for (const [index, file] of files.entries()) {
      const image = await sharp(new Uint8Array(await file.arrayBuffer()), {
        limitInputPixels: 16000000,
        animated: false,
      })
        .rotate()
        .resize({
          width: 2400,
          height: 2400,
          fit: "inside",
          withoutEnlargement: true,
        })
        .webp({ quality: 85 })
        .toBuffer({ resolveWithObject: true });
      if (image.data.length > MEDIA_LIMIT)
        throw new Error("Image is too large");
      const path = `${session.user.id}/${id}/${randomUUID()}.webp`;
      const upload = await db.storage
        .from(MEDIA_BUCKET)
        .upload(path, image.data, { contentType: "image/webp", upsert: false });
      if (upload.error) throw upload.error;
      paths.push(path);
      const row = await db.from("post_images").insert({
        post_id: id,
        path,
        alt: input.title || "Community photo",
        sort_order: index,
        width: image.info.width,
        height: image.info.height,
      });
      if (row.error) throw row.error;
    }
    const publish = await db
      .from("posts")
      .update({ status: "published" })
      .eq("id", id)
      .eq("author_id", session.user.id)
      .select("id")
      .single();
    if (publish.error) throw publish.error;
  } catch {
    const cleanup = await db
      .from("posts")
      .delete()
      .eq("id", id)
      .eq("author_id", session.user.id);
    const media = paths.length
      ? await db.storage.from(MEDIA_BUCKET).remove(paths)
      : null;
    return {
      error:
        cleanup.error || media?.error
          ? "Publishing failed and cleanup was incomplete. Your post is not published; please contact support."
          : "Publishing failed. Your text is still here; check your images and try again.",
    };
  }
  refresh(id);
  return { success: "Published to your community.", postId: id };
}
export async function updatePost(
  _: SocialState,
  form: FormData,
): Promise<SocialState> {
  const session = await member(),
    input = readPost(form),
    id = form.get("post_id");
  if (!session || !input || !uuid(id))
    return { error: "Check your post details and sign in again." };
  // Kind is immutable. Restrict the update to its original type as well as its owner.
  const { kind, ...fields } = input;
  const result = await session.db
    .from("posts")
    .update(fields)
    .eq("id", id)
    .eq("author_id", session.user.id)
    .eq("kind", kind)
    .select("id")
    .single();
  if (result.error)
    return { error: "Could not update this post. It may have been removed." };
  refresh(id);
  return { success: "Changes saved." };
}
export async function deletePost(
  _: SocialState,
  form: FormData,
): Promise<SocialState> {
  const session = await member(),
    id = form.get("post_id");
  if (!session || !uuid(id))
    return { error: "Sign in again to remove this post." };
  const images = await session.db
    .from("post_images")
    .select("path")
    .eq("post_id", id)
    .eq("user_id", session.user.id);
  if (images.error)
    return { error: "Could not load post files. Please retry." };
  const result = await session.db
    .from("posts")
    .delete()
    .eq("id", id)
    .eq("author_id", session.user.id)
    .select("id")
    .single();
  if (result.error) return { error: "Could not remove this post." };
  const paths = (images.data || [])
    .map((i) => i.path as string)
    .filter((p) => p.startsWith(`${session.user.id}/${id}/`));
  const media = paths.length
    ? await session.db.storage.from(MEDIA_BUCKET).remove(paths)
    : null;
  refresh(id);
  if (form.get("return_to") === "/feed" && !media?.error) redirect("/feed");
  return {
    success: media?.error
      ? "Post removed. Some files could not be cleaned up; please contact support."
      : "Post removed.",
  };
}
async function setInteraction(
  table: "post_likes" | "post_saves" | "post_shares",
  form: FormData,
): Promise<SocialState> {
  const session = await member(),
    id = form.get("post_id");
  if (!session || !uuid(id)) return { error: "Sign in again to continue." };
  // Desired state makes retried requests idempotent. RLS uses the authenticated default user_id.
  const remove = table !== "post_shares" && form.get("remove") === "yes";
  const result = remove
    ? await session.db
        .from(table)
        .delete()
        .eq("post_id", id)
        .eq("user_id", session.user.id)
    : await session.db.from(table).insert({ post_id: id });
  if (result.error && result.error.code !== "23505")
    return { error: "Could not save this change. Please retry." };
  refresh(id);
  return { success: "Updated." };
}
export async function toggleLikePost(_: SocialState, form: FormData) {
  return setInteraction("post_likes", form);
}
export async function toggleSavePost(_: SocialState, form: FormData) {
  return setInteraction("post_saves", form);
}
export async function sharePost(_: SocialState, form: FormData) {
  return setInteraction("post_shares", form);
}
export async function addComment(
  _: SocialState,
  form: FormData,
): Promise<SocialState> {
  const session = await member(),
    input = readComment(form);
  if (!session || !input)
    return {
      error:
        "Write a comment of 1–2,000 characters and sign in again if needed.",
    };
  const result = await session.db.from("post_comments").insert(input);
  if (result.error)
    return {
      error:
        "Could not add your comment. The post or parent comment may have been removed; comment limits may apply.",
    };
  refresh(input.post_id);
  return { success: "Comment added." };
}
export async function deleteComment(
  _: SocialState,
  form: FormData,
): Promise<SocialState> {
  const session = await member(),
    id = form.get("comment_id"),
    postId = form.get("post_id");
  if (!session || !uuid(id) || !uuid(postId))
    return { error: "Sign in again to continue." };
  // RLS permits the comment author or the post owner, never an unrelated member.
  const result = await session.db
    .from("post_comments")
    .delete()
    .eq("id", id)
    .eq("post_id", postId)
    .select("id")
    .single();
  if (result.error) return { error: "Could not remove this comment." };
  refresh(postId);
  return { success: "Comment removed." };
}
