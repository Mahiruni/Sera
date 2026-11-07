# Sera accounts — verified release state, 7 November 2026

## Current status

The account foundation was merged into main at `4cd1748701d993cef646e6d7b13fc0cf25f3e7a6` and deployed to production (Vercel READY).
Production origin: https://sera-dusky-seven.vercel.app
Supabase: Sera's Project (`okegwojigrtxadvgxpwm`), eu-central-1, Seraa connection.
Public Supabase URL and publishable key are configured for production and preview. The application uses no privileged key.

`SERA_ACCOUNT_REGISTRATION=enabled` is now configured for production and preview under the owner's explicit public-registration launch request. New deployments enable the website registration button and server action. Email confirmation remains required.

The public registration release replaces browser-demo privacy/terms copy with scoped account notices in English and Amharic, names Mahir Aman as operator and uses the verified mahir@hisabtech.com support address. It adds a required terms/privacy acknowledgement checked on both client and server. The action adds the notice version and server timestamp to Auth metadata; this is user-editable metadata, not an immutable legal audit or authorization claim. Recruitment features remain separate demonstrations.

## Hosted email configuration

- Custom SMTP is enabled and saved: smtp.hostinger.com, port 465.
- Sender: mahir@hisabtech.com, display name Sera.et.
- The existing stored SMTP password was not read or replaced.
- Saved the English confirmation, recovery and password-change HTML templates and subjects from `supabase/templates`.
- Enabled password-change security notifications.
- Verified all three complete editor documents against their prepared assets after navigating away/reloading. The initial editor insertion retained default content; this was corrected before concluding verification.
- Set the Auth Site URL to the exact production origin above, without a trailing slash. It previously used the alternative sera-gulitshop deployment domain, which produced inconsistent/double-slash email links.
- Preserved existing redirects, including the two exact callback destinations:
  - https://sera-dusky-seven.vercel.app/auth/callback
  - https://sera-dusky-seven.vercel.app/auth/callback?next=/reset-password

The mailbox received confirmation and recovery messages from Sera.et. Recovery was received with the correct origin, a single branded HTML document, and a plain-text alternative. Password-change notification also reached the inbox after an actual test password change. Confirmation delivery and token-hash verification worked; an earlier confirmation sample contained default content before the editor correction. The final confirmation template was independently verified after reload; a new post-correction confirmation inbox sample is still useful before public launch.

English is the active fallback. Amharic assets remain review drafts; five other language preferences fall back to English. Native localized template conditions, approved translations, Arabic RTL copy and cross-client/dark-mode inbox rendering remain unverified. The welcome template is an asset only: there is no standalone post-verification welcome dispatcher yet.

## Authenticated verification

Used the owner's connected mailbox for one explicitly labelled Sera Email Delivery Test account, with fictional profile details. No real employee/customer/identity/financial data was used.

Passed:
- Public Auth API signup requested email and issued no session before verification.
- Inbox receipt from the configured sender.
- Actual production `/auth/confirm` accepted the delivered token hash, returned a 307 redirect to `/onboarding`, and set session cookies with a private/no-store response.
- Hosted user was confirmed.
- Password sign-in, own-profile access, profile update/onboarding timestamp, and session refresh.
- Authenticated production `/dashboard` and `/settings` returned the test profile with private/no-store caching.
- Password recovery reached the inbox; its delivered link redirected to `/reset-password`, established a verified recovery session and displayed the reset form.
- Changing the disposable test password through the recovery session succeeded.
- Password-change security notification reached the inbox.
- Global sign-out cleared the test session.

These are direct API and production HTTP checks, not a claim that every browser form submission has been exercised. The test account remains labelled and retained; no irreversible account deletion was performed. Temporary local credentials/tokens are not committed.

## Implemented security and routes

Routes: `/sign-up`, `/sign-in`, `/verify-email`, `/forgot-password`, `/reset-password`, `/onboarding`, `/dashboard`, `/settings`, `/auth/confirm`, `/auth/callback`.
Private pages validate the Auth user server-side and require verified email. Callback destinations are allowlisted; auth pages are noindex and omitted from public sitemap. Per-request SSR clients use HTTP-only cookies and private/no-store responses.

Profiles are created by a private trigger. RLS restricts authenticated users to their own rows and allowed update columns; anonymous access, direct inserts, ownership reassignment and cross-user access are denied. Journey/locale metadata is normalized and never grants privileged roles. Existing demo workspaces remain separate from real accounts.

Fresh local build (including TypeScript), ESLint and all 14 automated tests passed for the deployed foundation. GitHub CI and Vercel preview passed. Current hosted Supabase security advisor returned no findings. Previous rolled-back two-user RLS tests passed. Anonymous dashboard redirect, registration pause and expired-link feedback were verified live in the browser. Runtime-log inspection was unavailable (Vercel 403); no clean-log claim is made.

## Follow-up work

1. Keep account notices aligned with actual operation; no legal compliance certification is claimed. Account deletion requests are manual through the verified mailbox.
2. Exercise browser form submissions and obtain a fresh final confirmation sample; extend inbox rendering checks beyond the connected Hostinger mailbox.
3. Review and activate translations; implement idempotent post-verification welcome delivery if required.
4. Registration is enabled for the limited account/profile release. Recruitment, document handling, payments and standalone welcome dispatch are outside this launch.

No paid upgrade, new hosting/database project or additional privileged access was created.
