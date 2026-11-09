"use server";
import { redirect } from "next/navigation";
import { authConfigured, serverClient } from "../supabase/server";
import { siteOrigin } from "../seo";
import type { ActionState } from "./actions";

const CALLBACK = `${siteOrigin}/auth/callback`;

/** Sign in / register with Google (Supabase handles state, PKCE and linking). */
export async function continueWithGoogle(
  _previous: ActionState,
  _data: FormData,
): Promise<ActionState> {
  if (!authConfigured())
    return { message: "Account services are not configured yet." };
  if (process.env.SERA_ACCOUNT_REGISTRATION !== "enabled")
    return {
      message:
        "Account access with Google opens together with registration. Please try again later.",
    };
  const client = await serverClient();
  const { data, error } = await client.auth.signInWithOAuth({
    provider: "google",
    options: { redirectTo: CALLBACK },
  });
  if (error || !data.url)
    return { message: "Google sign-in is unavailable right now." };
  redirect(data.url);
}
