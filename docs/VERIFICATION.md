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

No deployment is authorized in this revision, and no deployment command is part of the verification workflow. The demo limitations remain: browser-only state, freely switchable demo roles, sample vacancies, no live authentication, no external application delivery or messages, no connected AI, and no payment checkout. No fictitious vacancies, employer verification, interview reminders, or live notifications were added from the reference image.
