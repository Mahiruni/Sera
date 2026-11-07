"use server";
import { redirect } from "next/navigation";
import { revalidatePath } from "next/cache";
import { serverClient, authConfigured, currentUser } from "../supabase/server";
import {
  readRegistration,
  normalizedEmail,
  passwordIssue,
  readProfile,
} from "./validation";
import { siteOrigin } from "../seo";
export type ActionState = { message?: string; success?: boolean };
const unavailable = {
  message: "Account services are not configured yet. Please try again later.",
};
export async function register(
  _previous: ActionState,
  data: FormData,
): Promise<ActionState> {
  if (!authConfigured()) return unavailable;
  if (process.env.SERA_ACCOUNT_REGISTRATION !== "enabled")
    return {
      message:
        "Registration opens after email delivery and account-security checks are complete. You can explore the demo meanwhile.",
    };
  const input = readRegistration(data);
  if (!input)
    return {
      message:
        "Check your details, accept the account terms and use a password of 12–128 characters.",
    };
  if (String(data.get("website") || ""))
    return { message: "Unable to process this request." };
  const client = await serverClient();
  const { error } = await client.auth.signUp({
    email: input.email,
    password: input.password,
    options: {
      emailRedirectTo: `${siteOrigin}/auth/callback`,
      data: {
        full_name: input.full_name,
        locale: input.locale,
        journey: input.journey,
        account_terms_version: "2026-11-07",
        account_terms_acknowledged_at: new Date().toISOString(),
      },
    },
  });
  if (error)
    return {
      message:
        error.code === "over_email_send_rate_limit"
          ? "Too many email requests. Please wait before trying again."
          : "We could not create the account. Check the details or try signing in if you already registered.",
    };
  redirect("/verify-email");
}
export async function signIn(
  _previous: ActionState,
  data: FormData,
): Promise<ActionState> {
  if (!authConfigured()) return unavailable;
  const email = normalizedEmail(data.get("email"));
  const password = String(data.get("password") || "");
  if (!email || !password || password.length > 128)
    return { message: "Enter your email address and password." };
  const client = await serverClient();
  const { error } = await client.auth.signInWithPassword({ email, password });
  if (error)
    return {
      message:
        "Sign-in was unsuccessful. Check your details and verify your email, or request a password reset.",
    };
  revalidatePath("/", "layout");
  redirect("/dashboard");
}
export async function requestReset(
  _previous: ActionState,
  data: FormData,
): Promise<ActionState> {
  if (!authConfigured()) return unavailable;
  const email = normalizedEmail(data.get("email"));
  if (!email) return { message: "Enter a valid email address." };
  const client = await serverClient();
  const { error } = await client.auth.resetPasswordForEmail(email, {
    redirectTo: `${siteOrigin}/auth/callback?next=/reset-password`,
  });
  if (error && error.code === "over_email_send_rate_limit")
    return { message: "Please wait before requesting another email." };
  if (error)
    return {
      message:
        "We could not request an email right now. Please try again later.",
    };
  return {
    success: true,
    message:
      "If this address has an account, a password-reset email will be requested. Check your inbox and spam folder.",
  };
}
export async function resendVerification(
  _previous: ActionState,
  data: FormData,
): Promise<ActionState> {
  if (!authConfigured()) return unavailable;
  const email = normalizedEmail(data.get("email"));
  if (!email) return { message: "Enter your email address." };
  const client = await serverClient();
  const { error } = await client.auth.resend({
    type: "signup",
    email,
    options: { emailRedirectTo: `${siteOrigin}/auth/callback` },
  });
  if (error && error.code === "over_email_send_rate_limit")
    return { message: "Please wait before requesting another email." };
  if (error)
    return {
      message:
        "We could not request a verification email right now. Please try again later.",
    };
  return {
    success: true,
    message:
      "If an account is awaiting verification, a new confirmation email will be requested. Please check your inbox.",
  };
}
export async function updatePassword(
  _previous: ActionState,
  data: FormData,
): Promise<ActionState> {
  if (!(await currentUser()))
    return {
      message:
        "Your reset session is missing or expired. Request a new password-reset email.",
    };
  const password = String(data.get("password") || "");
  const issue = passwordIssue(password);
  if (issue) return { message: issue };
  if (password !== data.get("confirm_password"))
    return { message: "The passwords do not match." };
  const client = await serverClient();
  const { error } = await client.auth.updateUser({ password });
  if (error)
    return {
      message:
        "Password update failed. Request a fresh reset link and try again.",
    };
  await client.auth.signOut({ scope: "global" });
  revalidatePath("/", "layout");
  redirect("/sign-in?password=updated");
}
export async function saveProfile(
  _previous: ActionState,
  data: FormData,
): Promise<ActionState> {
  const user = await currentUser();
  if (!user || !user.email_confirmed_at) redirect("/sign-in");
  const profile = readProfile(data);
  if (!profile)
    return {
      message:
        "Check your name, language, journey and field lengths (maximum 500 characters).",
    };
  const client = await serverClient();
  const { error } = await client
    .from("profiles")
    .update({ ...profile, onboarded_at: new Date().toISOString() })
    .eq("id", user.id)
    .select("id")
    .single();
  if (error)
    return { message: "Profile could not be saved. Please try again." };
  const { error: localeError } = await client.auth.updateUser({
    data: { locale: profile.locale, full_name: profile.full_name },
  });
  if (localeError)
    return {
      message:
        "Your profile was saved, but email language could not be updated. Save once more to retry.",
    };
  revalidatePath("/", "layout");
  redirect("/dashboard?profile=saved");
}
export async function signOut() {
  if (authConfigured()) {
    const client = await serverClient();
    const { error } = await client.auth.signOut();
    if (error) redirect("/settings?error=signout");
  }
  revalidatePath("/", "layout");
  redirect("/sign-in?signed=out");
}
