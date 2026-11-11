import Link from "next/link";
import { notFound } from "next/navigation";
import { findGuide, guides } from "@/lib/guides";
import { pageMetadata } from "@/lib/seo";
import { Breadcrumbs } from "@/components/public-site";
export function generateStaticParams() {
  return guides.map((guide) => ({ slug: guide.slug }));
}
export const dynamicParams = false;
export async function generateMetadata({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  const { slug } = await params;
  const guide = findGuide(slug);
  if (!guide) notFound();
  return pageMetadata(`/guides/${slug}`, guide.title, guide.description, true);
}
export default async function Page({
  params,
}: {
  params: Promise<{ slug: string }>;
}) {
  const { slug } = await params;
  const guide = findGuide(slug);
  if (!guide) notFound();
  return (
    <div className="container page guide-article">
      <Breadcrumbs
        items={[
          { name: "Guides", path: "/guides" },
          { name: guide.title, path: `/guides/${slug}` },
        ]}
      />
      <h1>{guide.title}</h1>
      <p className="subtext">{guide.description}</p>
      <p className="article-byline">
        Published by Sera.et ·{" "}
        <time dateTime={guide.published}>7 November 2026</time> · General
        guidance
      </p>
      <article className="panel information-article">
        {guide.sections.map((section) => (
          <section key={section.title}>
            <h2>{section.title}</h2>
            <p>{section.body}</p>
          </section>
        ))}
      </article>
      <aside className="article-note">
        <h2>Keep current information with its source</h2>
        <p>
          This is an original planning guide. Verify time-sensitive details
          directly with the responsible employer, institution or official body.
          Sera.et does not certify vacancies, programmes, admission decisions or
          individual eligibility.
        </p>
      </aside>
      <nav className="information-related" aria-label="Related guides">
        {guides
          .filter((other) => other.slug !== slug)
          .map((other) => (
            <Link href={`/guides/${other.slug}`} key={other.slug}>
              {other.title}
            </Link>
          ))}
        <Link href="/guides">All guides</Link>
      </nav>
    </div>
  );
}
