import "server-only";
import { createClient } from "@supabase/supabase-js";

/** Only use after server-side getUser(), ownership checks, and completed moderation. */
export function moderationWriter() {
  const url = process.env.NEXT_PUBLIC_SUPABASE_URL;
  const key = process.env.SERA_SUPABASE_SERVICE_ROLE_KEY;
  if (!url || !key) return null;
  return createClient(url, key, { auth: { persistSession: false, autoRefreshToken: false } });
}

/** The application must not enable its gate without both trusted credentials. */
export function securePublishingConfigured() {
  return Boolean(process.env.SERA_SUPABASE_SERVICE_ROLE_KEY && process.env.NEXT_PUBLIC_SUPABASE_URL);
}
