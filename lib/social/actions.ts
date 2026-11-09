"use server";

import { serverClient } from "@/lib/supabase/server";
import { revalidatePath } from "next/cache";
import { redirect } from "next/navigation";

export async function createPost(formData: FormData) {
  const client = await serverClient();
  const { data: { user } } = await client.auth.getUser();
  if (!user) throw new Error("Unauthorized");

  const type = formData.get("type") as string || "text";
  const title = formData.get("title") as string;
  const content = formData.get("content") as string;
  const imageUrl = formData.get("image_url") as string;
  const category = formData.get("category") as string;
  const location = formData.get("location") as string;
  const skillsRaw = formData.get("skills") as string;
  const skills = skillsRaw ? skillsRaw.split(",").map(s => s.trim()).filter(Boolean) : [];

  if (!content) throw new Error("Content is required");

  const { error } = await client.from("posts").insert({
    user_id: user.id,
    type,
    title: title || null,
    content,
    image_url: imageUrl || null,
    category: category || null,
    location: location || null,
    skills,
  });

  if (error) throw new Error(error.message);

  revalidatePath("/");
  revalidatePath("/feed");
  redirect("/");
}

export async function updatePost(formData: FormData) {
  const client = await serverClient();
  const { data: { user } } = await client.auth.getUser();
  if (!user) throw new Error("Unauthorized");

  const postId = formData.get("post_id") as string;
  const title = formData.get("title") as string;
  const content = formData.get("content") as string;
  const category = formData.get("category") as string;
  const location = formData.get("location") as string;

  const { error } = await client
    .from("posts")
    .update({
      title: title || null,
      content,
      category: category || null,
      location: location || null,
      updated_at: new Date().toISOString(),
    })
    .eq("id", postId)
    .eq("user_id", user.id);

  if (error) throw new Error(error.message);

  revalidatePath("/");
  revalidatePath(`/posts/${postId}`);
}

export async function deletePost(formData: FormData) {
  const client = await serverClient();
  const { data: { user } } = await client.auth.getUser();
  if (!user) throw new Error("Unauthorized");

  const postId = formData.get("post_id") as string;

  const { error } = await client
    .from("posts")
    .delete()
    .eq("id", postId)
    .eq("user_id", user.id);

  if (error) throw new Error(error.message);

  revalidatePath("/");
  revalidatePath("/feed");
}

export async function toggleLikePost(formData: FormData) {
  const client = await serverClient();
  const { data: { user } } = await client.auth.getUser();
  if (!user) throw new Error("Unauthorized");

  const postId = formData.get("post_id") as string;
  const isLiked = formData.get("is_liked") === "true";

  if (isLiked) {
    await client.from("post_likes").delete().eq("post_id", postId).eq("user_id", user.id);
  } else {
    await client.from("post_likes").insert({ post_id: postId, user_id: user.id });
  }

  revalidatePath("/");
  revalidatePath(`/posts/${postId}`);
}

export async function addComment(formData: FormData) {
  const client = await serverClient();
  const { data: { user } } = await client.auth.getUser();
  if (!user) throw new Error("Unauthorized");

  const postId = formData.get("post_id") as string;
  const content = formData.get("content") as string;

  if (!content) return;

  await client.from("post_comments").insert({
    post_id: postId,
    user_id: user.id,
    content,
  });

  revalidatePath(`/posts/${postId}`);
}

export async function toggleSavePost(formData: FormData) {
  const client = await serverClient();
  const { data: { user } } = await client.auth.getUser();
  if (!user) throw new Error("Unauthorized");

  const postId = formData.get("post_id") as string;
  const isSaved = formData.get("is_saved") === "true";

  if (isSaved) {
    await client.from("post_saves").delete().eq("post_id", postId).eq("user_id", user.id);
  } else {
    await client.from("post_saves").insert({ post_id: postId, user_id: user.id });
  }

  revalidatePath("/");
  revalidatePath(`/posts/${postId}`);
  revalidatePath("/saved");
}
