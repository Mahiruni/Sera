# Signature design system — Phase 2

Status: isolated foundation and component preview. No production asset replacement or global CSS/theme override.

Single source: brand/sera-tokens.json. Run node scripts/generate-signature-tokens.mjs after token changes and use --check in CI. Generated CSS and Kotlin use the same semantic colors. Web primitives and preview are scoped to .signature-system. Android primitives are opt-in and do not replace SeraTheme.kt. Existing fonts and dependencies are reused.

Web preview: /design/signature, development or Vercel preview only, noindex. Interactive sample navigation, buttons, activity, post, messaging, settings, notification privacy, call confirmation, light/dark and multilingual typography. All examples are local; no production calls or account writes. Android: SignatureAction, SignatureSurface, SignatureActivityCount, SignatureCallAction, SignaturePreference and SignatureCallConfirmation. Compose test exercises confirmation.

Rollout: audit and reconcile open PRs #37–40 before migrating production surfaces. Keep actual notification default full, respect user choice, never initiate a real call before confirmation. Preserve existing feed caching, auth, localization and calling infrastructure. Next: integrate shell, feed and profile via opt-in components after build, real visual and device review. Android launcher masks and legal mark clearance still pending. No new dependencies or fonts.
