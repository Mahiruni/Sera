"use server";
import { serverClient } from "@/lib/supabase/server";
import { revalidatePath } from "next/cache";
import { redirect } from "next/navigation";
import { randomUUID } from "node:crypto";
import sharp from "sharp";
import { moderationEnabled, screenSubmission } from "@/lib/moderation/screen";
import { moderationWriter, securePublishingConfigured } from "@/lib/moderation/writer";
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
  if (moderationEnabled() && !securePublishingConfigured())
    return { error: "Secure publishing is not configured. Please try again later." };
  if (moderationEnabled()) {
    try {
      const normalizedImages: Buffer[] = [];
      for (const file of files) {
        normalizedImages.push(await sharp(new Uint8Array(await file.arrayBuffer()), {
          limitInputPixels: 16000000, animated: false,
        }).rotate().resize({ width: 1024, height: 1024, fit: "inside", withoutEnlargement: true })
          .webp({ quality: 70 }).toBuffer());
      }
      const verdict = await screenSubmission(input, normalizedImages);
      if (verdict.verdict === "rejected")
        return { error: "Your post may violate our community safety guidelines. Please revise the content or contact support for review." };
      if (verdict.verdict === "review")
        return { error: "Safety screening is temporarily unavailable. Your post has not been published. Please try again later." };
    } catch {
      return { error: "We could not safely check your images. Nothing has been published." };
    }
  }
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
    const publisher = securePublishingConfigured() ? moderationWriter() : db;
    if (!publisher) throw new Error("Trusted publisher unavailable");
    const publish = await publisher
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
  if (moderationEnabled() && !securePublishingConfigured())
    return { error: "Secure publishing is unavailable." };
  if (moderationEnabled()) {
    const verdict = await screenSubmission(input, []);
    if (verdict.verdict !== "approved")
      return { error: verdict.verdict === "rejected" ? "Your changes may violate community guidelines. Please revise them." : "Safety screening is unavailable. Changes were not saved." };
  }
  // Kind is immutable. Restrict the update to its original type as well as its owner.
  const { kind, ...fields } = input;
  const editor = securePublishingConfigured() ? moderationWriter() : session.db;
  if (!editor) return { error: "Secure publishing is unavailable." };
  const result = await editor
    .from("posts")
    .update(fields)
    .eq("id", id)
    .eq("author_id", session.user.id)
    .eq("kind", kind)
    .eq("status", "published")
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
/** Professionally attributed reposts follow the same moderation gate as original posts. */
export async function repostPost(_: SocialState, form: FormData): Promise<SocialState> {
  const session = await member();
  const sourceId = form.get("post_id");
  const note = form.get("note");
  if (!session || !uuid(sourceId) || typeof note !== "string" || note.length > 400)
    return { error: "Sign in and choose a valid post to repost." };
  if (moderationEnabled() && !securePublishingConfigured())
    return { error: "Secure repost publishing is temporarily unavailable." };

  const { data: source, error: missing } = await session.db.from("posts")
    .select("id,author_id,title,body,repost_of")
    .eq("id", sourceId).eq("status", "published").maybeSingle();
  if (missing || !source) return { error: "This post is no longer available." };
  if (source.author_id === session.user.id) return { error: "You cannot repost your own story." };
  if (source.repost_of) return { error: "Please repost the original story instead." };

  const { data: existing } = await session.db.from("posts").select("id")
    .eq("author_id", session.user.id).eq("repost_of", sourceId).limit(1).maybeSingle();
  if (existing) return { success: "Already reposted.",postId:existing.id };


  const prepared = new FormData();
  prepared.set("kind", "idea");
  prepared.set("title", "Repost");
  prepared.set("body", note.trim() || "Shared original post");
  const input = readPost(prepared);
  if (!input) return { error: "Could not prepare this repost." };
  const verdict = await screenSubmission(input, []);
  if (verdict.verdict !== "approved")
    return { error: verdict.verdict === "rejected" ?
      "The repost did not pass our community guidelines." :
      "Safety screening is unavailable. Nothing was reposted." };

  const draft = await session.db.from("posts").insert({
    ...input, status: "draft", repost_of: sourceId, repost_note: note.trim(),
  }).select("id").single();
  if (draft.error || !draft.data)
    return { error: draft.error?.code === "23505" ?
      "You have already reposted this update." : "Could not create this repost. Try again." };

  const publisher = securePublishingConfigured() ? moderationWriter() : session.db;
  if (!publisher) return { error: "Secure publishing is temporarily unavailable." };
  const id = draft.data.id as string;
  const publish = await publisher.from("posts").update({ status: "published" })
    .eq("id", id).eq("author_id", session.user.id)
    .eq("status", "draft").select("id").single();
  if (publish.error || !publish.data) {
    await publisher.from("posts").delete().eq("id", id).eq("author_id", session.user.id).eq("status", "draft");
    return { error: "Could not complete the repost. Please retry." };
  }
  refresh(String(sourceId));
  refresh(id);
  return { success: "Reposted to your network.", postId: id };
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
  if (moderationEnabled() && !securePublishingConfigured())
    return { error: "Secure commenting is unavailable." };
  if (moderationEnabled()) {
    const verdict = await screenSubmission({
      kind: "idea", title: "", body: input.body, quote_source: "", category: "", skills: [],
    }, []);
    if (verdict.verdict === "rejected")
      return { error: "Your comment may violate our community guidelines. Please revise it." };
    if (verdict.verdict !== "approved")
      return { error: "Comment safety screening is unavailable. Please try again later." };
  }
  if (securePublishingConfigured()) {
    const visible = await session.db.from("posts").select("id").eq("id", input.post_id).eq("status", "published").maybeSingle();
    if (visible.error || !visible.data) return { error: "This post is unavailable for comments." };
    if (input.parent_id) {
      const parent = await session.db.from("post_comments").select("id").eq("id", input.parent_id).eq("post_id", input.post_id).maybeSingle();
      if (parent.error || !parent.data) return { error: "The parent comment is unavailable." };
    }
  }
  const commenter = moderationEnabled() ? moderationWriter() : session.db;
  if (!commenter) return { error: "Secure commenting is unavailable." };
  // The member role can insert only post_id, parent_id and body. PostgreSQL
  // assigns auth.uid(); the privileged moderation writer needs an explicit author.
  const result = moderationEnabled() ? await commenter.from("post_comments").insert({ ...input, author_id: session.user.id }) : await commenter.from("post_comments").insert(input);
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

/** Report an existing published post for moderator review. Reports do not auto-delete content. */
export async function reportPost(_: SocialState, form: FormData): Promise<SocialState> {
  const session = await member();
  const id = form.get("post_id");
  const reason = form.get("reason");
  const allowed = ["harassment", "hate", "sexual", "fraud", "spam", "violence", "impersonation", "privacy", "other"];
  if (!session || !uuid(id) || typeof reason !== "string" || !allowed.includes(reason))
    return { error: "Sign in and choose a valid report reason." };
  const details = String(form.get("details") || "").trim().slice(0, 1000);
  const result = await session.db.from("moderation_reports").insert({
    post_id: id,
    reason,
    details,
  });
  if (result.error) {
    if (result.error.code === "23505") return { success: "You already reported this post. Thank you." };
    return { error: "Could not submit the report. Please try again." };
  }
  return { success: "Report received. Our moderation team can review it." };
}

export async function undoRepost(_:SocialState,form:FormData):Promise<SocialState>{const session=await member();const id=form.get("post_id");if(!session||!uuid(id))return {error:"Sign in again to continue."};const {error}=await session.db.rpc("sera_undo_repost",{p_original:id});if(error)return {error:"Could not undo repost."};refresh(id);return {success:"Repost removed."};}
