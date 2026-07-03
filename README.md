# Satra

Satra is an open-source Android crypto wallet project.

The project is starting with product design and screen implementation first, then wallet behavior will be added step by step. The Android app is built with Kotlin, Jetpack Compose, and Material 3. The wallet core will be non-custodial and multi-chain.

## Current Status

- Android project scaffolded.
- First onboarding screen implemented in Compose.
- Brand kit added under `brand/satra-brand-kit` and applied to the current UI, app icon, splash theme, colors, typography, and icon resources.
- Multi-chain wallet core not implemented yet.
- Supported assets and networks documented in `docs/SUPPORTED_ASSETS.md`.

## Tech Stack

- Kotlin
- Jetpack Compose
- Material 3
- Android Gradle Plugin
- Multi-chain wallet core planned

## Supported Assets

Satra supports only the networks, native coins, and tokens listed in `docs/SUPPORTED_ASSETS.md`.

## Development

Satra's visual identity comes from `brand/satra-brand-kit/README.md`; use the kit's semantic tokens and assets instead of ad hoc colors, fonts, or logo treatments.

Open the project in Android Studio, or build from the command line after installing an Android SDK:

```sh
./gradlew :app:assembleDebug
```

The project uses the Gradle wrapper and expects Android SDK platform 37 with Android SDK Build Tools 36.0.0 or newer.

## License

Apache License 2.0
