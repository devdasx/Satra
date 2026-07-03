# Agent Rules for Satra

- Product name: Satra.
- GitHub repository: `devdasx/Satra`.
- After any file edit, run the relevant available checks, commit the edit, and push to `origin` before ending the turn, unless the user explicitly says not to push.
- Keep the Android app UI in Kotlin, Jetpack Compose, and Material 3.
- Support system light and dark mode using native Material 3 behavior, but do not let Android dynamic color or wallpaper colors override the Satra brand tokens.
- Treat `brand/satra-brand-kit/README.md` and `brand/satra-brand-kit/colors/tokens.json` as the source of truth for Satra logo usage, colors, typography, iconography, app icon, and splash behavior.
- Use the Satra Tessera mark and lockups only as provided by the brand kit: Ink on light surfaces, Bone on dark surfaces, no gradients, shadows, outlines, rotation, rearrangement, or arbitrary recoloring.
- Use Outfit for UI text, Outfit SemiBold for Satra wordmark assets, and Space Grotesk with tabular numbers for numeric wallet values.
- Every screen must be responsive across compact phones, tablets, foldables, and desktop-class/windowed Android surfaces.
- Each screen should fit inside the visible viewport without vertical scrolling whenever the content can reasonably fit; primary actions must be visible without scrolling. Use scroll only as a fallback for genuinely cramped window sizes.
- In paged onboarding/tutorial UI, keep visuals and copy in fixed-size page slots so swiping never shifts the header, dots, actions, or other surrounding layout; every page must have its own meaningful visual.
- Put every user-facing string in English resource keys under `app/src/main/res/values/strings.xml` as soon as it is added. Do not leave visible text hardcoded in Compose.
- Track future localization for the top 25 languages by total speakers; keep English as the source language until the full-app translation pass.
- Satra is a multi-chain, multi-asset wallet. Do not describe it as Bitcoin-only.
- Supported assets and networks are defined in `docs/SUPPORTED_ASSETS.md`; do not add, remove, substitute, or imply support for assets outside that document.
- Keep the wallet-core direction non-custodial. Do not introduce custodial wallet assumptions.
