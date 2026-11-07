# Sera account foundation — 7 November 2026

## Connected resources
- Supabase: Sera's Project (`okegwojigrtxadvgxpwm`), eu-central-1, Seraa connection. Originally no public tables and no accounts.
- Hosting: existing Sera Vercel project, not a new project. Public API URL and publishable key configured in production/preview environments; no privileged key is used by the app.
- `SERA_ACCOUNT_REGISTRATION=disabled` gates the website's registration action and button until the release checklist below passes. This does not modify Supabase's own project-wide signup configuration.

## Implemented
Marketing header adds an accessible Create account control next to the profile button; authenticated users get My account. Profile icon opens real sign-in/account routes. Existing `/account`, `/workspace`, employer tools and browser-local records remain explicitly separate demo features. The real private dashboard is `/dashboard`.

Routes: `/sign-up`, `/sign-in`, `/verify-email`, `/forgot-password`, `/reset-password`, `/onboarding`, `/dashboard`, `/settings`, `/auth/confirm`, `/auth/callback`. Private pages validate the user against Supabase Auth on the server and require verified email before profile access. Server actions validate inputs. PKCE callback and explicit token-hash confirmation are supported with fixed/allowlisted internal destinations. Auth pages remain noindex and are excluded from the public sitemap.

Server-only Supabase SSR clients use per-request HTTP-only cookies. Proxy refreshes sessions and forwards response cache headers; auth responses use private/no-store. No shared server client or authorization from user-editable metadata. Public rendering receives only an authenticated boolean, not a token or user record.

Registration takes name, email, password (12–128 characters), language and candidate/employer journey. The journey is a preference, never a privileged role or verified employer status. Onboarding progressively collects optional location, phone, skills/experience/preferred roles or organization details. Student/teacher/institution privileged workflows are not offered.

Profiles are created by a private trigger, not user insert permissions. Authenticated users can select/update only their own row and only permitted columns. ID and creation timestamps cannot be reassigned by clients. RLS is enabled; anonymous reads and direct client inserts are denied. Trigger metadata is normalized and length-limited. Initial migrations were created with the CLI and filenames aligned to the exact hosted migration versions returned after application.

Security advisory fixed: revoked PUBLIC/anon/authenticated execution of the existing internal `rls_auto_enable` event-trigger function. Subsequent Supabase security advisor output had no lints.

## Email design and localization
`lib/emails/render.ts` is the single rendering source; run `node --import tsx scripts/build-email-templates.ts` to regenerate files. Templates cover confirmation, recovery, welcome and password-change notice. Table layout, inline styling, cyan/white wordmark, woven-style divider, clear CTA, fallback link, safety/help sections and plain-text counterparts. All names and ordinary links are escaped/validated. Emails use system fonts that work without web fonts/images. No graphical logo, invented sender address or registration claims.

English files: `supabase/templates/*.html` and `.txt`. Amharic files marked `.am-draft` require native review before activation. Sample English/Amharic rendered HTML/text are in `docs/emails`; previews use fictional details. `/email-preview` is noindex and exposes only samples, not tokens or records.

Seven language preferences are saved. Registration supplies `locale` in signup metadata before initial email; settings updates both profile locale and auth metadata for subsequent templates. Auth metadata is for presentation only. The templates in this branch are English by default, not falsely labelled as seven completed translations. Five incomplete languages fall back to English. Amharic is a review draft. Arabic full email copy/RTL is still pending; no English email is labelled Arabic.

Templates are PREPARED, NOT INSTALLED in the hosted Auth configuration. Neither HTML preview nor successful database setup proves delivery. Native Supabase templates support `.Data.locale` conditions, but no such localized template has been activated until review/configuration are complete. The welcome template is a design asset; a post-verification, idempotent delivery mechanism is still required before claiming welcome emails are sent. Plain-text assets are prepared; multipart delivery must be verified with the actual email provider. Gmail/Outlook/Apple Mail dark-mode inbox rendering is not yet verified.

## Dashboard configuration still required
The connected Supabase tool supports SQL/schema but not Auth URL, email template or SMTP configuration. Browser dashboard requires sign-in. No account password, SMTP secret or sender DNS credential was requested in chat or committed.

After dashboard access:
1. Inspect existing SMTP/email settings and preserve unrelated settings. Confirm a verified sender the owner controls; do not invent `hello@sera.et` or assume sera.et ownership. Use the provider's actual SPF/DKIM/DMARC instructions.
2. Set the Auth Site URL to `https://sera-dusky-seven.vercel.app` and allow the exact `/auth/callback` recovery/confirmation destinations required by the app. Preserve legitimate existing redirects; avoid broad production wildcards. Do not redirect auth to an unverified custom domain.
3. Install English confirmation/recovery/password-change templates and subjects; enable the applicable password-change security notification. Token-hash confirmation links use `/auth/confirm` and avoid depending on the original signup browser. Never include actual tokens in screenshots/logs.
4. Review translated templates, then configure template conditionals or a supported localized send-email hook. English fallback remains until approval. Standalone welcome mail requires an authorized sender and idempotent post-verification dispatch; it is not a built-in Supabase confirmation event.
5. Obtain an authorized isolated test inbox. Verify actual registration, inbox arrival, confirmation, onboarding, sign-in, refresh, settings edits, recovery, expired-link resend and sign-out. Confirm each message's sender, links, language, plain-text part, responsive and dark-mode rendering. Do not claim provider acceptance means inbox receipt.
6. Complete privacy/terms and operator/contact/data-retention review for real accounts; current public policies remain draft demo notices and must not silently become final policies.
7. Enable `SERA_ACCOUNT_REGISTRATION=enabled` only after these checks pass; merge the verified release and verify production. Do not deploy the account release while delivery/security checks are incomplete.

## Verification
- 14 automated tests passed: existing demo domain behavior, SEO, redirect allowlist, registration/profile validation, no metadata privilege assignment, email escaping and unsafe-link rejection.
- `tests/profiles.rls.sql` ran on the hosted database with two disposable users inside a rolled-back transaction. PASS: own read/update, cross-user denial, ownership reassignment denial, insert denial, anonymous denial, and normalization of malicious journey/unknown locale. This is an RLS test, not end-to-end Auth delivery evidence. Fixtures were not retained.
- Production build, TypeScript checks, ESLint and all 14 tests passed for the prepared account foundation.
- Browser: 390px registration and 768px marketing viewports showed no horizontal overflow; desktop registration and email previews render. Anonymous `/dashboard` redirects to sign-in, expired-link error is visible and auth robots metadata is noindex.
- Current Supabase public settings report email provider enabled, confirmation required and native signup not disabled. SMTP sender/delivery has not been verified. No real customer account or successful email delivery is claimed.

## Dashboard follow-up
Dashboard access succeeded after the owner's CAPTCHA handoff. Confirmed Site URL is the existing production origin. Saved and verified these two exact allowed redirects:
- https://sera-dusky-seven.vercel.app/auth/callback
- https://sera-dusky-seven.vercel.app/auth/callback?next=/reset-password

The Free-plan dashboard requires custom SMTP before editing email templates; the alternative built-in-service customization requires a paid upgrade, which was not purchased. Both connected Hostinger links resolve to the same existing mailbox. Owner selected mahir@hisabtech.com as sender, displayed as Sera.et. Prepared smtp.hostinger.com:465 with that username, using Hostinger's official published settings. Password remains for secure owner entry directly in Supabase; the SMTP form is not saved and delivery/templates are not yet activated. No secret was read or committed. Screenshot documents the prepared, unsaved form.
