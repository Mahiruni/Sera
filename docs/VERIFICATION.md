# Verification — Cyan redesign, 7 November 2026

## Current design

The previous interface, green/gold palette, serif accent, geometric arch artwork, original mark, and old font packages have been removed. The superseded HTML archive is removed from the current tree; Git history retains the earlier migration.

The replacement uses the supplied mobile job-app reference as its visual direction:

| Token      | Value     | Purpose                                     |
| ---------- | --------- | ------------------------------------------- |
| Cyan       | `#35B4DA` | Spotlight cards, selected filters, monogram |
| Primary    | `#087F9F` | Readable button and link treatments         |
| Ice        | `#E9F7FC` | Secondary cards and soft surfaces           |
| Background | `#F5F8FC` | Cool app canvas                             |
| Ink        | `#172D3B` | Main text                                   |

All Latin interface text uses self-hosted Plus Jakarta Sans, selected for a rounded, Pinterest-like feel. This is not Pinterest's proprietary font. Noto Sans Ethiopic remains the Amharic fallback. Form controls, wordmark, menus, cards, and all workspace routes inherit the new typography.

The home page has been rebuilt with a compact mobile search area, functional work-type pills, a horizontally scrolling cyan spotlight collection, compact role rows, saved/application summaries, and sector chips. Desktop expands into a search feature and multi-column cards. Job details have a centered company badge, salary, work facts, and functional Description/Company controls. The transparent two-control header and accessible drawer are retained; the floating glass dock now uses the cyan palette.

## Automated verification

- `npm run lint`: Next.js core-web-vitals and TypeScript ESLint rules.
- `npm run typecheck`: Next.js route type generation and strict TypeScript.
- `npm test`: all six existing domain tests passed (combined filters, closed vacancies, valid/idempotent candidate applications, explainable keyword overlap, stored-state validation, and salary formatting).
- `npm run build`: optimized Next.js production build.
- The existing GitHub Actions workflow repeats the checks from a fresh install. Its remote result must be verified independently after pushing.

## Browser review completed

Chrome with the running development preview, without publishing:

- Desktop homepage at a 1348px content width; no horizontal overflow.
- Responsive frames configured at 390px and 768px. Usable widths vary with scrollbar visibility (373–388px and 751–766px). Home and job details were reviewed at both widths; mobile messaging was also checked. A compact salary-row overflow was fixed; the reviewed documents' scroll widths equal their client widths.
- Mobile and desktop layouts use the new Plus Jakarta Sans family, confirmed through computed styles.
- Header computed background is fully transparent and contains exactly two controls.
- English and Amharic home layouts, drawer language switching, labels, and persisted language changes. The compact Amharic home does not overflow.
- Work-type pills show the correct sample roles; Part time has an honest empty state and Contract shows the MEAL Officer sample.
- Mobile home search for `frontend` navigates to discovery with one matching sample role.
- Job detail Company control shows sample-employer provenance and links to that employer's other sample roles. Description returns to role responsibilities and requirements.
- Existing profile, application, and conversation data remains available because the versioned browser-store format was preserved.
- Native drawer dismissal and focus-management code is unchanged from the prior verified implementation; the new drawer styling and language controls were exercised on mobile.

The responsive frames are browser layout checks, not physical-device testing. Real-device keyboards, OS safe areas, and production behavior are not claimed.

## Preview evidence

- `docs/screenshots/sera-desktop.png`: current desktop home.
- `docs/screenshots/sera-mobile.png`: compact cyan mobile home.
- `docs/screenshots/sera-role-detail.png`: rebuilt mobile role page.

The temporary responsive-preview route was removed before the final build. The Next.js development indicator is disabled for clean review captures; runtime errors are still surfaced normally.

## Boundaries

Production deployment was authorized for the subsequent footer release; its result is checked separately from local verification. The demo limitations remain: browser-only state, freely switchable demo roles, sample vacancies, no live authentication, no external application delivery or messages, no connected AI, and no payment checkout. No fictitious vacancies, employer verification, interview reminders, or live notifications were added from the reference image.

## Information footer, grouped navigation, and illuminated dock

The footer now has a brand description, Explore / Support / Information columns, current-year copyright, and separate policy links. New bilingual routes are `/about`, `/how-it-works`, `/help`, `/contact`, `/privacy`, `/terms`, and `/categories`. Contact is explicitly unconfigured; privacy and terms are draft demo information rather than final legal policies.

The hamburger menu uses four native accordion groups. Only the selected group is expanded. Candidate workspace entries include applications; employer workspace entries omit candidate-only links and expose the employer dashboard once in the employer group. A single role-switch action and a separate Exit workspace control remain. Focus wrapping includes visible summaries and skips links inside collapsed groups.

The dock's controls remain stationary. A non-interactive masked perimeter layer rotates a narrow conic cyan/white reflection every 16 seconds, linearly. Unsupported masks/gradients get a static border. The reduced-motion media rule disables this animation, makes the reflection static, and removes its compositing hint.

Browser checks completed in development: four-column desktop footer; two-column phone footer; all footer destinations present; Amharic footer and draft privacy content; phone and tablet Help layouts with no horizontal overflow; mobile footer-bottom at 656px and dock-top at 691px (about 35px clear at the bottom); accordion grouping and employer adaptation; Shift+Tab wraps to Exit workspace even with collapsed groups; mobile role switch; border layer computed as `dock-light-orbit`, 16s, linear, `mask-composite: exclude`, and `pointer-events: none`. Reduced-motion and unsupported-browser fallback declarations were inspected in source; no OS-level reduced-motion emulation is claimed. Screenshots are under `docs/screenshots/sera-footer-*.png` and `sera-menu-mobile.png`.

The user subsequently authorized production deployment for this footer release. The existing Sera Vercel project is reused. Final deployment outcome is reported separately after GitHub source and cloud-build verification.

## SEO foundation — 7 November 2026
- Local production build, TypeScript generation/check, ESLint and all 10 domain/SEO tests passed.
- Browser checked public landing, career guide, preserved /demo, grouped drawer and seven-option language picker; selecting Amharic navigates to /am, dismisses drawer and sets lang=am. /ar renders Arabic availability text with lang=ar/dir=rtl. Five unfinished translations remain labelled rather than represented as complete.
- 390px phone and 768px tablet iframe viewports had equal document/client widths (no horizontal overflow). Fixed mobile landing text overlapping the transparent header controls. Desktop screenshot: docs/screenshots/seo-home.png.
- Development metadata inspected: unique branded title, verified-host canonical, noindex in development. Production indexing and sitemap require deployment checks; Google/Bing indexing itself is not verified by a successful build or deploy.
- No application-origin console errors observed on tested pages. Browser extension metadata errors are unrelated to the app. No backend/auth/chat/call/notification or institutional-data services were activated.
- See docs/SEO.md for indexing policy, unverified custom-domain dependency, search-account verification steps, editorial governance and staged platform roadmap.
