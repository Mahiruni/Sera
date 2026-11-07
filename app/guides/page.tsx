import { GuideCards, Breadcrumbs } from "@/components/public-site";
import { publicMetadata } from "@/lib/seo";
export const metadata = publicMetadata("/guides");
export default function Page() {
  return (
    <div className="container page public-hub">
      <Breadcrumbs items={[{ name: "Guides", path: "/guides" }]} />
      <p className="eyebrow">Sera.et / GUIDES</p>
      <h1>Career and education guides for your next move.</h1>
      <p className="subtext">
        Original checklists for checking opportunities, comparing study options
        and organizing academic work. These are practical information, not
        official announcements or guarantees of employment or admission.
      </p>
      <GuideCards />
      <section className="public-editorial">
        <h2>How we handle information</h2>
        <p>
          We separate general guidance from facts that need a current official
          source. We do not publish invented tuition fees, deadlines, employer
          verification or institutional partnerships. If a guide needs a
          correction, a verified contact channel will be published once it is
          configured; demo chat is not a support inbox.
        </p>
        <p>
          Published by Sera.et. These guides have no named specialist reviewer,
          and do not claim professional accreditation. Institution-specific
          announcements and academic records are not currently accepted for
          publication.
        </p>
      </section>
    </div>
  );
}
