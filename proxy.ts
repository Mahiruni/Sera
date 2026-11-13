import { NextResponse, type NextRequest } from "next/server";
import { createServerClient } from "@supabase/ssr";
export async function proxy(request: NextRequest) {
  const path = request.nextUrl.pathname;
  const choice = request.cookies.get("sera-language")?.value;
  const language = path === "/am" ? "am" : path === "/om" ? "om" : path === "/en" ? "en" : choice === "am" || choice === "om" ? choice : "en";
  const headers = new Headers(request.headers);
  headers.set("x-sera-language", language);
  let response = NextResponse.next({ request: { headers } });
  const url = process.env.NEXT_PUBLIC_SUPABASE_URL;
  const key = process.env.NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY;
  if (
    url &&
    key &&
    request.cookies.getAll().some((c) => c.name.startsWith("sb-"))
  ) {
    const client = createServerClient(url, key, {
      cookieOptions: {
        httpOnly: true,
        sameSite: "lax",
        secure: process.env.NODE_ENV === "production",
      },
      cookies: {
        getAll: () => request.cookies.getAll(),
        setAll(items, cacheHeaders) {
          items.forEach(({ name, value }) => request.cookies.set(name, value));
          headers.set("cookie", request.cookies.toString());
          response = NextResponse.next({ request: { headers } });
          items.forEach(({ name, value, options }) =>
            response.cookies.set(name, value, options),
          );
          Object.entries(cacheHeaders).forEach(([name, value]) =>
            response.headers.set(name, value),
          );
        },
      },
    });
    await client.auth.getUser();
    response.headers.set("Cache-Control", "private, no-store");
  }
  if (
    [
      "/dashboard",
      "/settings",
      "/onboarding",
      "/reset-password",
      "/sign-in",
      "/sign-up",
      "/verify-email",
      "/forgot-password",
    ].includes(path) ||
    path.startsWith("/auth/")
  )
    response.headers.set("Cache-Control", "private, no-store");
  if (process.env.VERCEL_ENV === "preview")
    response.headers.set("X-Robots-Tag", "noindex, nofollow");
  return response;
}
export const config = {
  matcher: [
    "/((?!api|_next/static|_next/image|favicon.ico|icon.svg|opengraph-image|robots.txt|sitemap.xml).*)",
  ],
};
