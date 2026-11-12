import { NextRequest, NextResponse } from "next/server";
import { currentUser, serverClient } from "@/lib/supabase/server";
import { sanitizeDiscoveryQuery } from "@/lib/network/model";
import { rankSuggestions, type SearchSuggestion } from "@/lib/network/search-model";
export const dynamic = "force-dynamic";
const headers = { "Cache-Control": "private, no-store", Vary: "Cookie" };
export async function GET(request: NextRequest) {
  const user = await currentUser();
  if (!user?.email_confirmed_at) return NextResponse.json({ error: "Sign in required" }, { status: 401, headers });
  const query = sanitizeDiscoveryQuery(request.nextUrl.searchParams.get("q"));
  if (query.length < 2) return NextResponse.json({ suggestions: [] }, { headers });
  const db = await serverClient();
  const pattern = "%" + query + "%";
  const [people, jobs, posts] = await Promise.all([
    db.from("post_authors").select("id,full_name").ilike("full_name",pattern).order("full_name").limit(12),
    db.from("jobs").select("id,title").eq("status","open").ilike("title",pattern).order("title").limit(12),
    db.from("posts").select("id,title,kind").eq("status","published").is("deleted_at",null).ilike("title",pattern).order("title").limit(12),
  ]);
  if (people.error || jobs.error || posts.error) return NextResponse.json({ error: "Suggestions unavailable" }, { status: 503, headers });
  const rows: SearchSuggestion[] = [
    ...(people.data || []).map(p => ({id:p.id,label:p.full_name,kind:"People" as const})),
    ...(jobs.data || []).map(p => ({id:p.id,label:p.title,kind:"Jobs" as const})),
    ...(posts.data || []).map(p => ({id:p.id,label:p.title,kind:p.kind==="project"?"Projects" as const:"Posts" as const})),
  ];
  return NextResponse.json({ suggestions: rankSuggestions(rows.filter(row=>row.label?.trim()),query) }, { headers });
}
