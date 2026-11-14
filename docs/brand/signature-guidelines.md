# Sera Signature identity — Phase 1 master

Approved direction 03. The Signature S comprises two flowing ribbons separated by negative space and a purple square. The primary wordmark is Sera, not Sera.et. The vector is a refinement of the concept, not a pixel trace. Production assets remain unchanged.

## Assets and usage

Use brand/signature/primary-light.svg or primary-dark.svg for the matching surfaces; primary-black.svg and primary-white.svg for monochrome printing. Use symbol-color.svg, symbol-black.svg or symbol-white.svg for compact marks. stacked-light.svg is for centered compositions; wordmark.svg for type-only placement; wordmark-outline.svg for large editorial campaigns. app-icon.svg uses a teal field, white ribbons and a lavender accent. favicon.svg is the simplified white symbol on teal. notification-symbol.svg is a monochrome white transparent candidate for status-bar notifications. android-adaptive-foreground.xml is a candidate foreground paired with a teal background; verify masks before rollout.

Use vector paths, preserve aspect ratio, negative-space seam and square. Clear space: at least one-quarter symbol height. Minimum primary lockup width 120 CSS px, standalone 24px, favicon 16px, outline 160px. No distortion, arbitrary recoloring, gradients, shadows or busy backgrounds.

## Color and typography

Teal #0D7C86 primary; white #FFFFFF surface; purple #7C3AED activity, unread, repost and notification accents; dark purple #4C1D95 calls. Near-black #111827 is a supporting neutral. Dark surface #152326, text #F0F7F6, primary #43D5CC, activity and calls #C4B5FD. Activity containers #F3ECFF light and #35254C dark. Real permitted presence remains green. Check WCAG AA contrast; do not rely on color alone.

Wordmark uses outlined Inter Display ExtraBold-derived geometry; no runtime font addition. Retain Plus Jakarta Sans Variable and Noto Sans Ethiopic Variable for the interface; English, Amharic and Afaan Oromo only. Suggested web sizes: display 40–56, H1 28–36, H2 22–26, H3 18–20, body 14–16, metadata 12–13px. Body line height 1.5–1.65; headings 1.12–1.35. Android equivalent sp with user font scaling. Ethiopic normal tracking and adequate line height. No font files in logo assets.

## Product design

Editorial whitespace; 8px spacing grid with 4px compact increments; 12–24px corners; thin neutral dividers; consistent rounded outline icons. Targets 48dp Android / 44px web. Meaningful 150–280ms motion, reduced-motion support. Preserve caching, network recovery, keyboard and navigation behaviors.

## Rollout gates

Reconcile PRs #37–40 only after test and conflict review. Preserve #39 full preview default and explicit privacy choices, #40 confirmed calling and real relative presence. Audit effective web CSS cascade and native theme roles. No database or transport changes in Phase 1. Local SVG rendering was checked; Android adaptive masks, notification rasterization, real browser/device integrations, trademark clearance and production deployment remain unverified. Connect. Create. Grow. is a working campaign line.
