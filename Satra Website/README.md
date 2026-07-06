# Satra — Website

The marketing and legal website for **Satra**, an open-source, non-custodial Android crypto wallet.

> *Satra — from the Sanskrit* sat, *“that which is real.”* A crypto wallet as a quiet instrument of ownership, not a casino.

This folder is self-contained. Open `index.html` (it forwards to the landing screen) in any modern browser — no build step, no server required.

---

## Pages / screens

| # | Screen | File | What it does |
|---|--------|------|--------------|
| 1 | **Landing** | `Satra Landing.dc.html` | Hero — “Keep what’s real.” Product pitch with a live dark-theme phone mock of the home/portfolio screen. Home page. |
| 2 | **Security** | `Satra Security.dc.html` | How self-custody works: on-device key generation, recovery phrase, passcode / biometrics / auto-lock / erase, honest sync, watch-only. |
| 3 | **Privacy** | `Satra Privacy.dc.html` | The full Privacy Policy, verbatim, as a document (sticky table of contents + numbered sections). |
| 4 | **Terms** | `Satra Terms.dc.html` | The full Terms of Use, verbatim, as a document (sticky table of contents + numbered sections). |
| 5 | **Open source** | `Satra Open Source.dc.html` | Why Satra is public under Apache 2.0 — read / verify / fork, repository map, security-through-openness. |
| 6 | **FAQ** | `Satra FAQ.dc.html` | 24 grounded questions across 6 groups (Basics, Keys & custody, Privacy, Security, Transactions, Support), as an accordion. |
| 7 | **Contact** | `Satra Contact.dc.html` | Email + GitHub-issues cards and a backend-less form that composes a `mailto:` in the visitor’s own email app. |

All pages cross-link through the top nav and the footer.

---

## Folder structure

```
website/
├── index.html                    ← entry point (forwards to the landing screen)
├── README.md                     ← you are here
├── support.js                    ← runtime that renders the .dc.html screens
│
├── Satra Landing.dc.html
├── Satra Security.dc.html
├── Satra Privacy.dc.html
├── Satra Terms.dc.html
├── Satra Open Source.dc.html
├── Satra FAQ.dc.html
├── Satra Contact.dc.html
│
└── brand/
    └── satra-brand-kit/
        ├── app-icon/             ← favicon.svg (+ PNG fallbacks) used by every page
        └── logo/svg/             ← Satra mark, wordmark & lockups (ink / bone)
```

Each `.dc.html` screen is a single self-rendering file that loads `./support.js` from the same folder. Keep the files together and the relative paths intact.

---

## Design system

Everything follows the **Satra Brand Kit**.

- **Color — 60 / 30 / 10.** Ink `#0B0B0C` dominates, Bone `#F7F6F3` breathes, greys do the quiet work. Green (`#4E9E76` / `#2E7D5A`) appears only as a UI signal (gains, “Ready”), never as decoration. Dark surfaces step Ink → `#141416` → `#1D1D20` for elevation.
- **Type.** Outfit for titles, UI and body; **Space Grotesk** (tabular figures) for every number — balances, prices, dates, addresses — so values don’t shift as they tick.
- **The mark** is the Tessera: two solid quarter-discs on the diagonal, two 40%-opacity tiles for structure. Ink on light, Bone on dark — never recolored, gradiented, or outlined.

Fonts load from Google Fonts (Outfit + Space Grotesk); an internet connection is needed for the exact typefaces, otherwise the system sans-serif fallback is used.

---

## Editing

The screens are Design Components. To change one, edit its `.dc.html` file:

- **Copy** lives directly in the markup — edit the text in place.
- **Colors, spacing, layout** are inline styles on each element.
- A few screens expose toggles at the top of their logic class (e.g. Privacy/Terms `showToc` / `showSummary`, FAQ `expandAll`, Landing `showAnnounce` / `countUp` / `parallax`).

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
