# Satra Play Store Phone Screenshot Brief

## Format

- Quantity: 8 phone screenshots.
- Recommended size: 1080 x 1920 px.
- Aspect ratio: 9:16.
- File type: PNG.
- Google Play limits: each screenshot under 8 MB, each side between 320 px and 3840 px.
- Style: clean native Android UI, Satra brand tokens, system font, no fake unsupported assets, no profit promises.
- Demo wallet: use the same wallet state across all screenshots so the set feels connected.

## Demo Wallet Data

- Wallet name: Imported Wallet
- Total balance: $1,032.67
- Performance: +$245.61, +41.52%
- Local currency: USD
- Main visible assets:
  - Tether USD: $412.30, 412.300000 USDT, networks Ethereum, Arbitrum, Base, Polygon
  - Ether: $127.49, 0.072425 ETH, Ethereum
  - Bitcoin: $304.18, 0.00291042 BTC, Bitcoin
  - Polygon: $66.08, 38.9200 POL, Polygon
  - Litecoin: $42.15, 0.356400 LTC, Litecoin
  - USD Coin: $80.47, 80.470000 USDC, Base

## Screenshot 1 - Home Portfolio

Marketing title: Your crypto, clearly organized.

Marketing subtitle: Track balances, charts, and supported assets from one non-custodial wallet.

Screen to show: Home screen with the balance card open, 1M chart selected, Send and Receive buttons visible, and the Coins & tokens section below.

Important UI details:
- Total balance: $1,032.67
- Chart label: 1M
- Performance: +$245.61, +41.52%
- Top assets: Tether USD, Ether, Bitcoin, Polygon
- Bottom navigation visible with Home selected

## Screenshot 2 - Token Detail

Marketing title: One token, every network.

Marketing subtitle: View a token balance, chart, networks, send, receive, and activity in one place.

Screen to show: Tether USD detail screen after tapping USDT from the home asset list.

Important UI details:
- Token: Tether USD
- Symbol: USDT
- Total token balance: $412.30
- Amount: 412.300000 USDT
- Networks shown: Ethereum, Arbitrum, Base, Polygon, BNB Chain
- Buttons: Send and Receive
- Activity preview: Received USDT, Sent USDT, Pending USDT

## Screenshot 3 - Send Flow

Marketing title: Send with the right network.

Marketing subtitle: Select the asset, choose the network, enter the recipient, and review before signing.

Screen to show: Send screen for USDT on Polygon with the form filled but not yet submitted.

Important UI details:
- Asset: Tether USD
- Network: Polygon
- Recipient: 0x8f3C...91d2
- Amount: 50.00 USDT
- Estimated network fee: 0.0042 POL
- Review button visible
- Scan and paste controls visible near recipient input

## Screenshot 4 - Receive Flow

Marketing title: Receive without address mistakes.

Marketing subtitle: Pick the asset, choose the network, then share the correct QR code and address.

Screen to show: Receive QR screen for USDT on Base.

Important UI details:
- Asset: Tether USD
- Network: Base
- QR code centered
- Address: 0x742d...44e
- Buttons: Copy address and Share
- Small warning: Only send USDT on Base to this address

## Screenshot 5 - Activity Timeline

Marketing title: A complete activity timeline.

Marketing subtitle: Review sent, received, pending, failed, and confirmed transactions across supported networks.

Screen to show: Activity tab with mixed EVM and Bitcoin-family history.

Important UI details:
- Filter pill: All networks
- Transactions:
  - Received USDT, +175.013331 USDT, Ethereum, Confirmed
  - Sent ETH, -0.014000 ETH, Ethereum, Confirmed
  - Received BTC, +0.00124000 BTC, Bitcoin, Confirmed
  - Sent POL, -12.5000 POL, Polygon, Pending
  - Failed USDC send, Base, Failed
- Bottom navigation visible with Activity selected

## Screenshot 6 - Markets And Prices

Marketing title: Prices in your currency.

Marketing subtitle: Follow supported coin and token prices in USD by default, with local currency support.

Screen to show: Markets tab with a market list and search/filter controls.

Important UI details:
- Currency: USD
- Search field: Search markets
- Rows:
  - Bitcoin, BTC, $104,530.20, +1.8%
  - Ether, ETH, $1,760.11, +2.4%
  - Tether USD, USDT, $1.00, 0.0%
  - USD Coin, USDC, $1.00, 0.0%
  - Polygon, POL, $0.31, +0.9%
  - Solana, SOL, $151.42, +3.1%
- Bottom navigation visible with Markets selected

## Screenshot 7 - Security Settings

Marketing title: Security stays local.

Marketing subtitle: Use passcode, biometrics, auto-lock, and erase protection on your device.

Screen to show: Settings > Security screen.

Important UI details:
- Passcode: On
- Biometrics: On
- Auto-lock: Immediately
- Erase wallet protection: 10 failed attempts
- Sensitive actions require passcode
- Reset wallet entry visible below the safe settings section

## Screenshot 8 - Create Or Import

Marketing title: Start your way.

Marketing subtitle: Create a new wallet, restore a phrase, import a private key, or watch an address.

Screen to show: Import wallet source screen or onboarding action screen, depending on which design looks stronger.

Important UI details:
- Options:
  - Create wallet
  - Recovery phrase
  - Private key
  - Watch-only address
- Open-source trust copy visible where it fits
- Terms and privacy links visible if using onboarding
- Keep the screenshot clean: this is the trust and setup screenshot, not a dense portfolio screenshot

## Designer Notes

- Keep overlay text short and high-contrast.
- Do not cover balances, QR codes, addresses, or primary buttons with marketing text.
- Use real Satra logo assets from `brand/satra-brand-kit`.
- Use token logos directly without white circular backgrounds.
- Use demo addresses only; do not use a real user wallet address.
- Avoid words like "bank", "guaranteed", "earn", "profit", "anonymous", or "untraceable".
- Do not imply support for assets outside `docs/SUPPORTED_ASSETS.md`.
