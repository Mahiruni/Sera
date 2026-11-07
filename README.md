# Sera ሥራ

A responsive Next.js App Router frontend for an Ethiopian recruitment marketplace, migrated from the supplied `sera-marketplace-2.html`. The green, ivory, and gold interface supports English and Amharic, a transparent two-control header, an accessible modal navigation drawer, and a floating glass navigation dock.

## Run

Requires Node.js 22.13+ (Node.js 24 recommended).

```sh
npm ci
npm run dev
```

Open the local address printed by Next.js. The development launcher accepts both Next.js flags and the preview runner's `--host`, `--port`, and `--strictPort` flags.

```sh
npm run lint
npm run typecheck
npm test
npm run build
npm start
```

## Routes

| Path                 | Experience                                                         |
| -------------------- | ------------------------------------------------------------------ |
| `/`                  | Landing page, category discovery, sample roles                     |
| `/jobs`              | Search, category/location/work-type filters, salary sort           |
| `/jobs/[id]`         | Role details, bookmark, editable cover template, demo application  |
| `/saved`             | Bookmarked roles                                                   |
| `/matches`           | Explainable keyword overlap, without AI claims                     |
| `/account`           | Candidate/employer demo entry, role switch, local-data reset       |
| `/workspace`         | Workspace for the currently selected demo role                     |
| `/profile`           | Candidate profile editing / employer identity                      |
| `/applications`      | Candidate application tracking, cover letters, interview checklist |
| `/messages`          | Local application conversations, both demo roles                   |
| `/employer`          | Local employer dashboard and opening/closing sample roles          |
| `/employer/post`     | Sample role creation, editable templates, salary validation        |
| `/employer/pipeline` | Demo candidate review and application-stage changes                |
| `/pricing`           | Original featured-listing concept; checkout explicitly unavailable |

## Honest demo boundary

**This is a production-buildable frontend, not a live recruitment backend.** The original HTML used localStorage accounts with plaintext demo passwords, simulated payment success, and a browser-side AI request with heuristic fallbacks. Those mechanisms are not suitable for production and have not been carried forward as live services.

- All eight original vacancies and their employers are sample content, not verified live listings. Original sample dates and invented marketplace-wide statistics are not shown as current facts.
- Demo roles are freely switchable UI state. They are not authentication or authorization.
- Demo profiles, vacancies, bookmarks, applications, and messages remain in the current browser under the versioned `sera-next-v1` key. No data is delivered to another person. Same-origin browser tabs synchronize local state.
- Live registration is unavailable. No passwords or email addresses are collected.
- Matching reports literal keyword overlap and exposes matching terms in the employer view. It is not an AI assessment or a hiring recommendation.
- Cover letters and job descriptions use visibly editable templates. Interview preparation is a general checklist.
- Payment checkout is disabled. No transactions, premium status changes, or payment-provider requests occur.
- Storage failures display a warning. A confirmed local reset is available from `/account`. Do not enter sensitive data in this demo.
- Sample content is excluded from search indexing by default. Remove the noindex metadata only after replacing samples with verified live content.

## Production integration still needed

Before accepting real users, add server-backed identity, server-enforced roles and ownership checks, a durable database, verified vacancies and employer onboarding, secure document storage, validated API mutations, abuse controls, and delivery infrastructure. Any AI integration must run server-side with protected credentials, explicit provenance, and appropriate evaluation. Any payment integration must verify provider webhooks server-side and be idempotent. Local demo state must never be trusted for authorization or payment status.

No backend or hosting project was supplied or provisioned in this implementation. A GitHub push is not evidence of deployment. The project can be imported into a Next.js-compatible host with `npm run build` and `npm start`; a conventional Vercel import should detect Next.js automatically.

## Implementation notes

- Reusable shell, cards, headings, notices, loading UI, route components, shared translations, domain functions, and a versioned browser-store adapter.
- `useSyncExternalStore` keeps browser data hydration-safe and subscribes to storage changes.
- Native `<dialog>` provides modal focus containment, Escape dismissal, and focus restoration.
- Self-hosted variable fonts support Latin and Ethiopic text; CSS creates the hero artwork without raster downloads.
- Reduced-motion support, keyboard focus indicators, form labels, a skip link, stable loading placeholders, safe-area spacing, and a non-blur dock fallback are included.
- `docs/reference/sera-marketplace-2.html` retains the user's source for migration reference. It is not served or embedded by the application.

See [verification notes](docs/VERIFICATION.md) for checks and limits.
