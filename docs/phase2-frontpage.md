# Phase 2 front-page release

This release applies the Phase 2 direction to the public front page and signed-in home feed. It does not claim the entire Phase 2 backend is complete.

## Delivered

- Cyan-blue and white professional-network front page with editorial typography, a clearly identified illustrative project preview, community/profile/opportunity entry points and an upcoming-features section.
- Matching three-column member home: actual profile essentials, owned profile photo, skill shortcuts, collapsible existing publishing composer, real community stories, contributing professionals and recent open jobs. Responsive single-column mobile layout.
- A verified-member Discover route with People, Stories, Projects, Jobs, Companies and Topics tabs; sanitized multilingual search and bounded pagination.
- Contributor cards use the existing curated `post_authors` contract, never private profile fields. Companies come from open employer job postings; dedicated company accounts are not represented as implemented. Topics come from the 500 most recent published stories and are labeled accordingly.
- Persistent light/dark preference for the new front-page, feed and discovery surfaces. Existing unrelated pages retain their current appearance. Curved glass footer, active-icon animation, recruitment and account flows remain available.
- Updated homepage metadata, structured-data description and English footer introduction.

## Pending Phase 2 backend

Follow/unfollow, connection requests and their privacy settings; one-to-one real-time messaging, presence, read receipts and attachments; public profile opt-in and covers/certifications; community membership/moderation; persistent real-time notifications; blocking/reporting and personalized recommendations are separate backend work. These are labeled as coming next, with no fake counts, fake connection actions or simulated message delivery.

This release makes no schema, authorization-policy or hosted database changes. Existing authentication, recruitment, profiles, CV files and social interactions are reused. Read-only discovery continues to use the authenticated user's Supabase client and existing RLS. Only the current user's owned avatar path is signed for the profile sidebar.

## Verification

- TypeScript / route generation, optimized production build and the existing lint check.
- 33 tests, including search-filter sanitization, multilingual keywords, pagination bounds, profile completeness and skill shortcuts, plus existing authorization, media, recruitment and profile regressions.
- Browser checks of the public page in light/dark, desktop and 390px fixture layouts, member-feed component layouts and composer expansion. Both 390px page fixtures have equal viewport/content widths after the overflow fix.
- Temporary visual fixtures are removed before release. Browser fixture verification is not an authenticated end-to-end run against hosted Supabase.

The authoritative user-provided roadmap is retained in `docs/phase2-roadmap.md` so later implementation can follow the original requirements.
