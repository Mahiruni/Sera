import { NextResponse, type NextRequest } from "next/server";
export function proxy(request: NextRequest) {
  const path = request.nextUrl.pathname;
  const language = path === "/am" ? "am" : path === "/ar" ? "ar" : "en";
  const headers = new Headers(request.headers);
  headers.set("x-sera-language", language);
  const response = NextResponse.next({ request: { headers } });
  if (process.env.VERCEL_ENV === "preview")
    response.headers.set("X-Robots-Tag", "noindex, nofollow");
  return response;
}
export const config = {
  matcher: [
    "/((?!api|_next/static|_next/image|favicon.ico|icon.svg|opengraph-image|robots.txt|sitemap.xml).*)",
  ],
};
