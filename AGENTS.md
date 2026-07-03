# Agent Rules for Satra

- Product name: Satra.
- GitHub repository: `devdasx/Satra`.
- After any file edit, run the relevant available checks, commit the edit, and push to `origin` before ending the turn, unless the user explicitly says not to push.
- Keep the Android app UI in Kotlin, Jetpack Compose, and Material 3.
- Support system light and dark mode using native Material 3 behavior and dynamic color where available.
- Every screen must be responsive across compact phones, tablets, foldables, and desktop-class/windowed Android surfaces.
- Each screen should fit inside the visible viewport without vertical scrolling whenever the content can reasonably fit; primary actions must be visible without scrolling. Use scroll only as a fallback for genuinely cramped window sizes.
- Put every user-facing string in English resource keys under `app/src/main/res/values/strings.xml` as soon as it is added. Do not leave visible text hardcoded in Compose.
- Track future localization for the top 25 languages by total speakers; keep English as the source language until the full-app translation pass.
- Satra is a multi-chain, multi-asset wallet. Do not describe it as Bitcoin-only.
- Supported assets and networks are defined in `docs/SUPPORTED_ASSETS.md`; do not add, remove, substitute, or imply support for assets outside that document.
- Keep the wallet-core direction non-custodial. Do not introduce custodial wallet assumptions.
