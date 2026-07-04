# Satra

Satra is an open-source, non-custodial Android crypto wallet built with Kotlin, Jetpack Compose, and Material 3.

The app is multi-chain by design and uses the network and asset list documented in `docs/SUPPORTED_ASSETS.md`. Wallet data, addresses, balances, transactions, settings, and cached market data are stored locally on device.

## Current Status

- Android project scaffolded and running as a native Compose app.
- Onboarding, create-wallet, import-wallet, passcode, biometric setup, receive, activity, markets, settings, scanner, and main wallet screens are implemented.
- Brand kit added under `brand/satra-brand-kit` and applied to the current UI, app icon, splash theme, colors, typography, and icon resources.
- Local wallet database is implemented for wallets, derived addresses, private keys, assets, balances, transactions, app preferences, security settings, address book, notifications, and cached market records.
- Mnemonic creation/import, passphrase support, private-key import validation, watch-only import, address derivation, receive addresses, QR scanning, EVM sync, Bitcoin-family sync, Solana sync, account-chain sync scaffolding, and market price caching are in place.
- Send transaction execution and broadcast are still in progress; send preparation screens are present, but production signing/broadcast support must be completed before release.
- Supported assets and networks are documented in `docs/SUPPORTED_ASSETS.md`.

## Tech Stack

- Kotlin
- Jetpack Compose
- Material 3
- Android Gradle Plugin
- Local SQLite wallet database
- Public RPC/API/Electrum sync providers
- Non-custodial wallet derivation and import flows

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
