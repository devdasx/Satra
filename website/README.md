# Satra — Website

The marketing and legal website for **Satra**, an open-source, non-custodial Android crypto wallet.

> *Satra — from the Sanskrit* sat, *“that which is real.”* A crypto wallet as a quiet instrument of ownership, not a casino.

This folder is self-contained. Open `index.html` in any modern browser, or serve this folder as a static site. Only production HTML routes should be published.

---

## Pages / screens

| # | Screen | File | What it does |
|---|--------|------|--------------|
| 1 | **Landing** | `index.html` | Hero — “Keep what’s real.” Live dark-theme phone mock of the home/portfolio screen. Followed by a fair comparison band vs MetaMask / Trust Wallet / Rabby, and a full site footer. Home page. |
| 2 | **Security** | `security/index.html` | How self-custody works: on-device key generation, recovery phrase, passcode / biometrics / auto-lock / erase, honest sync, watch-only. |
| 3 | **Privacy** | `privacy/index.html` | The full Privacy Policy as a document with a sticky table of contents and numbered sections. |
| 4 | **Terms** | `terms/index.html` | The full Terms of Use as a document with a sticky table of contents and numbered sections. |
| 5 | **Open source** | `open-source/index.html` | Why Satra is public under Apache 2.0 — read / verify / fork, repository map, security-through-openness. |
| 6 | **FAQ** | `faq/index.html` | Grounded questions across Basics, Keys & custody, Privacy, Security, Transactions, and Support. |
| 7 | **Contact** | `contact/index.html` | Email + GitHub-issues cards and a backend-less form that composes a `mailto:` in the visitor’s own email app. |

All pages cross-link through the top nav and the footer.

---

## Folder structure

```
website/
├── index.html                    ← production home page
├── README.md                     ← you are here
├── support.js                    ← runtime that renders the generated screens
│
├── security/index.html
├── privacy/index.html
├── terms/index.html
├── open-source/index.html
├── faq/index.html
├── contact/index.html
│
└── brand/
    └── satra-brand-kit/
        ├── app-icon/             ← favicon.svg (+ PNG fallbacks) used by every page
        └── logo/svg/             ← Satra mark, wordmark & lockups (ink / bone)
```

The public site should not include local design-export `.dc.html` files.

---

## Design system

Everything follows the **Satra Brand Kit**.

- **Color — 60 / 30 / 10.** Ink `#0B0B0C` dominates, Bone `#F7F6F3` breathes, greys do the quiet work. Green (`#4E9E76` / `#2E7D5A`) appears only as a UI signal (gains, “Ready”), never as decoration. Dark surfaces step Ink → `#141416` → `#1D1D20` for elevation.
- **Type.** Outfit for titles, UI and body; **Space Grotesk** (tabular figures) for every number — balances, prices, dates, addresses — so values don’t shift as they tick.
- **The mark** is the Tessera: two solid quarter-discs on the diagonal, two 40%-opacity tiles for structure. Ink on light, Bone on dark — never recolored, gradiented, or outlined.

Fonts load from Google Fonts (Outfit + Space Grotesk); an internet connection is needed for the exact typefaces, otherwise the system sans-serif fallback is used.

---

## Editing

To change a page, edit the production HTML file for that route:

- **Copy** lives directly in the markup — edit the text in place.
- **Colors, spacing, layout** are inline styles on each element.

---

## Content & legal notes

- All product claims are grounded in the Satra repository (README, Play Store description, and `docs/legal/`). The Privacy and Terms pages reproduce the source documents verbatim; if the source changes, update these to match.
- Effective date shown on the legal pages: **July 6, 2026**.
- Third-party public providers (RPC, Electrum, indexers, price APIs) are disclosed honestly: they are not run by Satra and may see a visitor’s IP, request timing, and queried public addresses under their own privacy policies.
- Satra never asks for a recovery phrase or private keys — this is stated on the Security, FAQ, and Contact screens.

---

## License

Satra is released under the **Apache License 2.0**. See the main repository:
<https://github.com/devdasx/Satra>

Contact: **care@satra.app**
