# Combined Sera release verification

Approved PRs #39, #40 and #42; preserve current logo, layout, caching, account data and voice/video infrastructure. No Signature or website redesign (#41/#38). Main already includes the newer SeraCallBackdrop and seraCallShouldAnimate with video support; do not copy older #37 over it. Reconciled theme, unread colors, call icon tint and inbox one-shot confirmation. No production merge or deployment.

Gates: web build/lint/test, Android compilation and visual checks, actual two-device calling (No starts nothing, Yes exactly once), notification privacy and permissions, slow-network feed recovery, signed APK size and release verification. Individual PR results do not establish combined release success.
