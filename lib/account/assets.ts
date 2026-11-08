import "server-only";
import { serverClient } from "@/lib/supabase/server";
export const ASSET_BUCKET = "sera-account-private";
export type Assets = {
  user_id: string;
  avatar_path: string | null;
  cv_path: string | null;
  branded_path: string | null;
  cv_name: string | null;
  cv_uploaded_at: string | null;
  cv_status: string | null;
  version: string;
};
export async function accountAssets(userId: string) {
  const client = await serverClient();
  const { data, error } = await client
    .from("account_assets")
    .select("*")
    .eq("user_id", userId)
    .maybeSingle();
  if (error) throw Error("Account files are temporarily unavailable.");
  return data as Assets | null;
}
export function ownedPath(path: string | null | undefined, userId: string) {
  return (
    !!path &&
    path.startsWith(userId + "/") &&
    !path.includes("..") &&
    /^[a-zA-Z0-9/_.-]+$/.test(path)
  );
}
