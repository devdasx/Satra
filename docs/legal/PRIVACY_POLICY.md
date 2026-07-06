# Satra Privacy Policy

Effective date: July 6, 2026

Satra is an open-source, self-custody crypto wallet. This Privacy Policy explains what data stays on your device, what data may be sent to public blockchain infrastructure, and the choices you have when using Satra.

## Summary

- Satra does not create user accounts.
- Satra does not sell personal data.
- Satra does not use ads, advertising SDKs, analytics SDKs, or tracking SDKs.
- Recovery phrases, private keys, passcodes, and biometric settings stay on your device and are never sent to Satra.
- Public wallet data may be sent to public RPC, API, Electrum, explorer, indexer, and blockchain providers so the app can sync balances, show transaction history, receive, send, and broadcast transactions.

## Data Stored On Your Device

Satra stores wallet data locally on your device, including:

- wallet names and wallet records;
- public wallet addresses;
- locally derived addresses;
- encrypted or protected secret material needed for self-custody wallet use;
- recovery phrase and private-key material that you create or import;
- balances, transaction records, market cache, and sync status;
- address book entries and recipient history;
- app preferences, selected currency, language, notification preferences, passcode settings, and biometric settings.

Recovery phrases and private keys are self-custody secrets. They stay on your device and are never sent to Satra, Satra servers, or a Satra account. Satra does not have access to them and cannot recover them for you.

## Data Sent To Public Blockchain Providers

Satra needs public blockchain infrastructure to work as a wallet. Depending on the network and action, the app may send the following public or transaction-related data to public RPC, API, Electrum, explorer, indexer, or blockchain providers:

- public wallet addresses;
- transaction hashes;
- transaction history queries;
- balances;
- chain and network identifiers;
- token contract identifiers;
- unsigned transaction simulation or fee-estimation requests;
- signed broadcast transactions when you choose to send a transaction.

This is required for wallet sync, transaction history, receiving, sending, fee estimation, and broadcasting. These providers are third parties. Your IP address may be visible to them when your device connects to their services.

Signed transactions sent for broadcast do not reveal your recovery phrase or private key, but they are public blockchain transactions. Once broadcast, blockchain transaction data may be public, permanent, and outside Satra's control.

## Market Data And Currency Conversion

Satra may request public market prices and currency conversion rates from third-party market data providers. These requests are used to show estimated fiat values and cached prices in the app. Market price requests are not account-based, and Satra does not use them for advertising or analytics.

## Camera, Clipboard, And Local Device Features

Satra may use your camera when you choose to scan a QR code. QR scanning is used to read wallet addresses or payment data. Satra does not use the camera for analytics or identity verification.

Satra may read from the clipboard only when you choose a paste action, and may write to the clipboard only when you choose a copy action.

Satra may use local biometric authentication if you enable it. Biometric data is handled by the Android system and is not sent to Satra.

## No Accounts, Ads, Analytics, Or Sale Of Data

Satra does not create accounts and does not require an email address, phone number, username, or password for a Satra account.

Satra does not use ads, advertising SDKs, analytics SDKs, or tracking SDKs. Satra does not sell your data.

## Third-Party Providers

Satra may connect to public infrastructure providers for supported networks and market data. These may include public RPC nodes, Electrum servers, explorers, indexers, market data APIs, and currency exchange-rate APIs.

Those providers operate independently from Satra and may process requests under their own privacy policies. Because wallet addresses and blockchain transactions are public-chain data, providers may be able to associate requests with your IP address, timing, network, and public wallet activity.

## Security

Satra is designed as a self-custody wallet. Your recovery phrase and private keys are the only way to control your funds. Keep them offline, private, and backed up securely.

Satra cannot reverse blockchain transactions, recover lost recovery phrases, reset private keys, or restore funds if your backup is lost or exposed.

Satra uses encrypted network connections where supported by its configured providers. Public blockchain data and signed transactions may still become public on the relevant blockchain network.

## Data Retention And Deletion

Satra does not create accounts and does not maintain a Satra-hosted user profile, so there is no Satra account record or server-side wallet profile to delete.

Data stored by Satra remains on your device until you delete it, remove a wallet, reset the app, clear app data, or uninstall the app. These actions remove local wallet records, cached balances, cached transactions, preferences, and local app data from that device.

Satra cannot delete public blockchain records after they exist on a blockchain. Satra also does not control retention by third-party public RPC, API, Electrum, explorer, indexer, blockchain, market data, or currency-rate providers. Those providers may retain public wallet queries, request metadata, IP addresses, or public blockchain records under their own policies.

## Children

Satra is not intended for children. Do not use Satra if you are not legally allowed to use cryptocurrency wallet software in your country or region.

## Changes To This Policy

Satra may update this Privacy Policy when the app, supported networks, provider behavior, or legal requirements change. The updated policy will be published in the Satra GitHub repository and may also be linked from the app and Google Play listing.

## Contact

For privacy questions, contact:

care@satra.app
