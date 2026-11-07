# Sera.et SEO foundation — 7 November 2026

## Release scope
Text-only Sera.et identity, existing cyan/white design, server-rendered public landing, careers/education/institution research hubs, three original practical guides, unique metadata/canonicals, Open Graph image, robots.txt, allowlisted sitemap, WebSite and breadcrumb JSON-LD. The recruitment demo remains at /demo; original discovery, accounts and employer flows remain browser-local demonstrations. No vacancies, partners, prices, accreditation, contact details or ranking results are invented. No JobPosting schema is emitted for demonstration roles.

The previous site applied noindex to every page. This release makes only substantive public English pages index-eligible. Search engines decide whether and where to index/rank them; number-one rankings cannot be promised.

## Index policy
Eligible: /, /about, /how-it-works, /opportunities, /education, /institutions, /guides and the three published /guides/[slug] articles.
Noindex: recruitment demo and sample roles; all account/workspace, profile, application, messaging and employer screens; draft privacy/terms, contact/help, category filters, pricing, language status pages and unreviewed Amharic public translation.
Previews receive noindex headers and an empty sitemap. Private records must eventually require server authorization; robots/noindex is not access control. Do not block noindex pages in robots.txt: crawlers need to read that instruction.

Canonicals use the existing verified Vercel alias. The project currently does not have sera.et attached. Availability/registration/ownership of sera.et has not been established. SITE_URL refuses sera.et until SERA_CUSTOM_DOMAIN_VERIFIED=true; this flag records a completed human/hosting verification step, not proof by itself. Never enable it as a shortcut. After DNS ownership, hosting verification and HTTPS are confirmed, change SITE_URL, redirect old production host to the new host, redeploy, inspect canonical/OG/sitemap URLs and submit the new sitemap. Keep historical path redirects.

## Language governance
Preferences: English (en), Amharic (am), Afaan Oromo (om), Tigrinya (ti), Arabic (ar), Afar (aa), Somali (so). Existing app English/Amharic flows remain. Public English content has fixed English URLs regardless of local preference. /am has an Amharic translation draft and stays noindex pending native editorial review. The five remaining locale pages explicitly state translation is in progress, remain noindex and fall back to English in the app. /ar has server-rendered lang=ar and dir=rtl for its Arabic availability message. No fake translated pages or hreflang entries. Enabling SERA_AM_REVIEWED after approval adds reciprocal en/am/x-default alternates and /am to the sitemap. Full translated hubs require substantive reviewed translations and separate URLs before expansion.

## Initial keyword map (research hypotheses, no measured volumes or ranking claims)
| Intent | Target | Useful material |
|---|---|---|
| Sera / Sera.et | / | Clear identity, available features, honest demo boundaries |
| checking jobs in Ethiopia | /opportunities | Opportunity verification and application preparation |
| how to verify a job opportunity | /guides/check-a-job-opportunity | Original actionable checklist |
| compare colleges/universities in Ethiopia | /education and comparison guide | Questions about programmes, intake, fees and sources |
| university institution research | /institutions | Research process, not an unverified directory |
| organize assignments/exams/projects | academic planning guide | Submission/revision checklist, no restricted examination material |

Validate these hypotheses using Search Console impressions, query intent and actual content needs. Avoid stuffing every education or job keyword into titles, mass-generated location pages, purchased links and duplicate thin translations. Search results may use different titles/snippets.

## Webmaster connection — prepared, not connected
1. Use an organization-controlled Google account. Open https://search.google.com/search-console and add the URL-prefix property https://sera-dusky-seven.vercel.app/ for the current release. Use the provided HTML meta verification token as GOOGLE_SITE_VERIFICATION in production hosting environment variables, redeploy, and complete verification in Google. Do not provide passwords to the application or commit credentials.
2. Submit https://sera-dusky-seven.vercel.app/sitemap.xml and inspect / plus a published guide. Record actual indexing status separately from submission success. Review Pages, crawl errors, enhancement issues, impressions and clicks.
3. Open https://www.bing.com/webmasters/ for Bing and related discovery. Verify the same property using its actual verification token in BING_SITE_VERIFICATION; redeploy and submit the sitemap. Google verification/import is an alternative only after authorized account connection. No account was connected and no sitemap was submitted during this code release.
4. Once sera.et is owned, prefer a DNS-verified Google Domain property; add the real DNS TXT challenge at the authoritative DNS provider. This requires access to the domain, not just a planned name. Confirm the final host before requesting indexing.
5. Assess IndexNow after real publishing/update/delete events exist. It requires a verified host key and only genuine changed eligible public URLs. Do not submit local demo changes, private records or placeholder translations. It is not a ranking guarantee.

No analytics or advertising tracker is added. Establish consent/data-retention requirements before adding marketing analytics. Track baseline before targets: indexed eligible pages, non-brand impressions/clicks, CTR by page/query, mobile field performance, and genuine conversions after real accounts exist. Do not invent baselines.

## Editorial and publication rules
Each article needs a clear purpose, responsible editor, real publication/revision date, source links for external factual claims, corrections process and language review. Current three articles are original general planning guidance, with no externally asserted fee, vacancy, registration or regulatory facts. Obtain current official evidence before publishing institution, employer, fee, admissions or exam information. Do not fabricate reviewers or credentials. Reviews should improve usefulness, not merely alter dates. Deleted public records require an intentional 404/410 or a genuinely relevant redirect; corrections preserve audit history. Expired real vacancies must lose JobPosting eligibility promptly.

## Next milestones, separate from this release
1. Dedicated database, authentication and server-enforced candidate/employer/student/teacher/institution/admin permissions; invitation and account recovery, data isolation, deletion/export/revision controls and audited moderation.
2. Real recruitment publishing with verified source, expiry, application handling and eligible JobPosting structured data only on genuine single-vacancy pages.
3. Institution/campus/programme data with provenance; student/teacher enrollment, private assignments/tests/midterms/grades, projects, costs and updates. Prevent public indexing of student records and restricted exam material.
4. Notifications, messaging and calling with permissions, delivery evidence and abuse controls; file conversion with validation and isolated processing. None is represented as active now.
5. Reviewed multilingual content and a sustainable blog/editorial programme; marketing after real account and consent flows are verified.

## Primary references
- https://developers.google.com/search/docs/fundamentals/seo-starter-guide
- https://developers.google.com/search/docs/specialty/international/localized-versions
- https://developers.google.com/search/docs/appearance/structured-data/job-posting
- https://developers.google.com/search/docs/crawling-indexing/block-indexing
- https://developers.google.com/search/docs/essentials/spam-policies
- https://www.bing.com/indexnow/getstarted
