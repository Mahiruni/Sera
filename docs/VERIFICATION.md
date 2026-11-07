# Verification — 7 November 2026

## Scope

The source repository was empty with `main` as its default branch. This implementation introduces Next.js App Router and TypeScript; the original HTML is retained under `docs/reference` and is not embedded or served by the app.

## Automated checks

- `npm run lint`: ESLint with Next.js core-web-vitals and TypeScript rules.
- `npm run typecheck`: strict TypeScript.
- `npm test`: six meaningful domain tests: combined search filters, closed roles, candidate-only/valid/idempotent local applications, explainable keyword overlap, versioned browser-state validation, and ETB salary formatting.
- `npm run build`: optimized Next.js production build.
- GitHub Actions workflow repeats install, lint, typecheck, tests, and build. Its remote outcome must be checked separately after pushing.

## Browser checks completed

Chrome against the running development preview:

- Desktop landing page and rendered artwork; header has exactly two children and computed transparent background.
- English and Amharic navigation and home views; persisted language selection, Ethiopic rendering.
- Native responsive frames configured at 390px and 768px (usable content widths 373px and 751px with scrollbars). Home, discovery, employer form, and messages checked for horizontal overflow; scroll width matched client width.
- Drawer opens, Escape dismisses, focus returns to the hamburger, Shift+Tab wraps from the first control to the last, and Tab wraps back to the first. An explicit wrapping handler was added after checking the native browser edge case.
- Candidate demo entry, search for accountant, bookmark, role details, saving a demo application, duplicate application disabled, and persisted application after direct navigation.
- Candidate message saved locally; employer role switch; same application and candidate message visible; stage changed to interview; employer reply visible in the conversation.
- Employer job template, rejection of a maximum salary below minimum, successful valid local sample-role creation, dashboard count and role entry updated.
- Profile changes saved and survived reload; keyword matches recalculated with clearly labeled overlap scores.
- Unknown search renders empty results. Employer role retains employer dashboard through `/workspace`.
- Scrolling the posting form to its end leaves the save control clear of the dock. Safe-area and focus scroll spacing are included.

Responsive frames were used for browser viewport-width checks, not physical devices. No real-device keyboard or mobile safe-area behavior is claimed.

## Console observations

The cloud browser extension emits metadata errors and can inject `data-oai-browser-agent-*` attributes into the root HTML before hydration, producing an extension-related hydration warning. The reported mismatch named those injected attributes. No application exception was observed in the exercised flows. A Next.js smooth-scroll configuration warning was fixed with the documented root attribute.

## Limits

This is a verified frontend with local demo persistence. Real authentication, server authorization, durable shared storage, uploads, email delivery, live AI, external messaging, and payment processing remain unconfigured. Role switching is a demo tool, not a security boundary. There are no claims of real applications, verified jobs, successful payments, or AI output.

No production host was created or deployed in this work. Local browser QA and a successful build do not establish production behavior. A screenshot of the final development preview is available in `docs/screenshots/sera-desktop.png`; the Next.js developer indicator is not part of a production build.
