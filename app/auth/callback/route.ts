import { NextRequest, NextResponse } from "next/server";
import { serverClient } from "@/lib/supabase/server";
import { safeDestination } from "@/lib/auth/validation";
import { siteOrigin } from "@/lib/seo";
export async function GET(request: NextRequest) {
  const code = request.nextUrl.searchParams.get("code");
  if (code && code.length < 3000) {
    const client = await serverClient();
    const { error } = await client.auth.exchangeCodeForSession(code);
    if (!error) {
      const response = NextResponse.redirect(
        new URL(
          safeDestination(
            request.nextUrl.searchParams.get("next"),
            "/onboarding",
          ),
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
