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
  const {data:items}=await db.from("sera_portfolio_items").select("id,kind,title,description,issuer,external_url,featured_at").eq("user_id",id).eq("shared",true).order("featured_at",{ascending:false,nullsFirst:false}).order("created_at",{ascending:false}).limit(50);
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
    {!!items?.length&&<section className="sera-public-portfolio-section"><h2>Projects & achievements</h2>{items.map(item=><article key={item.id} style={{padding:"16px 0",borderBottom:"1px solid #dce9e8"}}><small>{item.featured_at?"Featured · ":""}{item.kind} · Self-reported</small><h3>{item.title}</h3>{item.issuer&&<p>{item.issuer}</p>}<p style={{whiteSpace:"pre-wrap"}}>{item.description}</p>{item.external_url.startsWith("https://")&&<a href={item.external_url} target="_blank" rel="noopener noreferrer">View project or credential</a>}</article>)}</section>}
    <p className="sera-public-portfolio-footnote">Shared by this professional on SERA.et. Contact details, CV files, and private activity are not included.</p>
  </main>;
}
