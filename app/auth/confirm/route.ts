import { after } from "next/server";
import { dispatchWelcome } from "@/lib/emails/welcome";
import { NextRequest, NextResponse } from "next/server";
import { serverClient } from "@/lib/supabase/server";
import { siteOrigin } from "@/lib/seo";
export async function GET(request: NextRequest) {
  const hash = request.nextUrl.searchParams.get("token_hash");
  const type = request.nextUrl.searchParams.get("type");
  if (hash && hash.length < 300 && (type === "email" || type === "recovery")) {
    const client = await serverClient();
    const { error } = await client.auth.verifyOtp({ token_hash: hash, type });
    if (!error) {
      const verified = await client.auth.getUser();
      if (verified.data.user?.email_confirmed_at)
        after(async () => {
          await dispatchWelcome(verified.data.user!.id);
        });
      const response = NextResponse.redirect(
        new URL(
          type === "recovery" ? "/reset-password" : "/onboarding",
          siteOrigin,
        ),
      );
      response.headers.set("Cache-Control", "private, no-store");
      return response;
    }
  }
  return NextResponse.redirect(
    new URL("/verify-email?error=expired", siteOrigin),
  );
}
