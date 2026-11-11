import { notFound } from "next/navigation";
import { serverClient } from "@/lib/supabase/server";
import { uuid } from "@/lib/social/validation";

export const dynamic = "force-dynamic";
export const metadata = { title: "Professional portfolio | SERA.et", robots: { index: false, follow: false } };

export default async function PublicPortfolio({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  if (!uuid(id)) notFound();
  const db = await serverClient();
  const { data: profile, error } = await db.from("professional_public_portfolios")
    .select("full_name,headline,summary,location,skills,experience,education")
    .eq("user_id", id).eq("published", true).maybeSingle();
  if (error || !profile) notFound();
  return <main className="container page sera-public-portfolio">
    <div className="sera-public-portfolio-hero">
      <div className="sera-public-portfolio-monogram" aria-hidden="true">{profile.full_name.trim().slice(0,1).toUpperCase()}</div>
      <h1>{profile.full_name}</h1>
      {profile.headline && <p className="sera-public-portfolio-headline">{profile.headline}</p>}
      {profile.location && <p className="sera-public-portfolio-location">{profile.location}</p>}
    </div>
    {([["About",profile.summary],["Skills",profile.skills],["Experience",profile.experience],["Education",profile.education]] as const).filter(([,value])=>Boolean(value?.trim())).map(([label,value])=>
      <section className="sera-public-portfolio-section" key={label}><h2>{label}</h2><p>{value}</p></section>
    )}
    <p className="sera-public-portfolio-footnote">Shared by this professional on SERA.et. Contact details, CV files, and private activity are not included.</p>
  </main>;
}
