# Sera — Next Generation: Brand Foundation

Status: exploration; production logo and product UI unchanged pending identity approval.

## Direction
Premium editorial technology: confident typography, clean whitespace, purposeful accents, original human-centered professional networking. Reference: editorial monochrome retail identity, used as inspiration only. Visible brand is Sera; sera.et remains a domain. Candidate launch line: Connect. Create. Grow. (subject to approval).

## Identity exploration
A. Editorial wordmark — bold, solid custom lettering; purple punctuation as optional campaign accent.
B. Outline identity — expressive outlined type, suitable for large editorial/launch surfaces; solid version required for small text.
C. Signature identity — refine the recognizable existing S-shaped mark and pair with a strong wordmark; teal and purple used with restraint.
Select a primary wordmark and standalone icon before creating production SVG/vector assets. Verify recognizability at 16, 24, 48 and 108px, monochrome, dark mode and Android adaptive safe zone. Do not copy the reference.

## Brand tokens
Source of truth proposal: brand/sera-tokens.json. Teal is primary actions/identity; white is light surfaces; purple is activity/notification/repost accents; dark purple is the call icon. Near-black is supporting typography only. Keep existing dark-mode semantics and validate contrast, rather than forcing literal brand colors into every text role.
Retain Plus Jakarta Sans Variable and Noto Sans Ethiopic Variable for initial typography; evaluate custom display lettering only after selection. English, Amharic, Afaan Oromo. Never use Sera.et as a visible product name.

## Baseline audit (main bebfe8968891cb4d347b4a2b09d752873053b37b)
- app/icon.svg: text-only Sera mark, dark #172d3b, 160×64 viewBox. Needs square favicon variant in approved system.
- Android launcher: existing white S contour plus mint square on adaptive icon foreground; splash uses sera_signature_mark over teal. Preserve before approval.
- app/globals.css: legacy blue/cyan tokens (#087f9f, #35b4da), existing Plus Jakarta Sans/Noto Sans Ethiopic. Multiple layered CSS files loaded by app/layout.tsx; audit effective cascade before replacing any tokens.
- SeraTheme.kt: primary teal and white already present; tertiary currently duplicates teal on main. PR #39 introduces purple role; reconcile rather than duplicate.
- Existing open work: #39 messaging controls; #40 confirmed inbox calls; #38 web signature; #37 personalized call background; #22 conversations. Do not overwrite or merge these blindly; integrate approved changes after their tests and conflict review.
- Main commit is repository baseline only; production deployment and installed APK are not verified by this audit.

## Roadmap and gates
1. Foundation: select logo, finalize mark and wordmark, palette and type, build brand guidelines. No production asset replacement before approval.
2. Shared design system: audit effective tokens, map CSS and Android theme roles, preview components, accessibility and localization.
3. Product: responsive web and Android shell, feed, Explore, account/profile, jobs, collaboration. Preserve current routes, auth, data and actions.
4. Communication: integrate messaging PR #39 and inbox call PR #40 after review; preserve full preview default, explicit privacy settings, confirmed calling, existing call service and notification privacy.
5. Verification: web build/typecheck/lint/tests, Android variants, device/slow network tests, screenshots, performance baseline, accessibility, rollback plan.
6. Launch: release approved logo assets, email, notifications and marketing materials; update store assets only after verified builds.

## Release protection
No backend schema, network transport, auth or production changes in this phase. Keep existing production logos until identity approval. Require evidence for build, deployment and APK claims. Do not use new external font or image dependencies without measurable need.
