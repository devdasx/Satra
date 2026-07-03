# Satra Brand Kit — v1.0

**Satra** — from Sanskrit *sat*, “that which is real.” Satra is the keeper of what’s true: a crypto wallet as a quiet instrument of ownership, not a casino.

The mark is the **Tessera**: four tiles on a grid. Two solid quarter-discs on the diagonal give it motion; two quiet tiles at 40% give it structure. Modular assets, one system.

---

## 1. What’s in this folder

```
satra-brand-kit/
├── README.md                  ← you are here
├── logo/
│   ├── svg/                   ← vector masters (use these whenever possible)
│   │   ├── satra-mark-ink.svg            mark only, for light backgrounds
│   │   ├── satra-mark-bone.svg           mark only, for dark backgrounds
│   │   ├── satra-lockup-horizontal-*.svg mark + wordmark, side by side
│   │   ├── satra-lockup-stacked-*.svg    mark above wordmark, centered uses
│   │   └── satra-wordmark-*.svg          wordmark only
│   └── png/                   ← raster exports (transparent background)
├── app-icon/
│   ├── app-icon-1024.png      full-bleed square — iOS App Store master
│   ├── app-icon-rounded-*.png pre-rounded 512 / 180 / 120
│   ├── favicon.svg            scalable favicon
│   └── favicon-32/16.png
├── splash/
│   ├── splash-dark-1170x2532.png   default splash (iPhone 13/14/15 size)
│   ├── splash-light-1170x2532.png
│   └── splash-dark.svg             vector master, rescale for any device
├── icons/                     ← 12 UI icons, 24px grid, currentColor
└── colors/
    ├── tokens.css             ← drop-in CSS custom properties (light + dark)
    └── tokens.json            ← same tokens for JS / native / Tailwind config
```

---

## 2. Logo usage

### Which file do I use?

| Situation | File |
|---|---|
| App header, website nav (light bg) | `logo/svg/satra-lockup-horizontal-ink.svg` |
| App header, website nav (dark bg) | `logo/svg/satra-lockup-horizontal-bone.svg` |
| Splash, empty states, centered layouts | `satra-lockup-stacked-*.svg` |
| Avatars, watermarks, tiny spaces | `satra-mark-*.svg` |
| Legal footers, inline text mentions | `satra-wordmark-*.svg` |
| Social profile picture | `app-icon/app-icon-rounded-512.png` |

**Ink on light, Bone on dark. Never any other color.** The mark is never gradient, never outlined, never given a shadow.

### Rules

- **Clearspace:** keep one tile-width (¼ of the mark’s width) of empty space on all sides.
- **Minimum sizes:** mark 20 px; horizontal lockup 80 px wide. Below that, use nothing.
- **Wordmark** is Outfit SemiBold, lowercase, −1.5% tracking. Never letterspace it, never set it in caps, never re-type it in another font.
- The two 40%-opacity tiles are part of the mark. Don’t make them solid, don’t recolor them independently, don’t rotate or rearrange tiles.
- Don’t place the ink mark on photos or busy backgrounds; put it on an Ink or Bone tile first.
- The SVG lockups reference the Outfit font. On machines without Outfit installed, prefer the PNG lockups (text is baked in).

---

## 3. Color system

Philosophy: **60 / 30 / 10.** Ink dominates (60%), Bone breathes (30%), greys do the quiet work (10%). Green/amber/red exist **only** as UI signals — never in the logo, never as decoration.

### Primitives (raw palette — never use directly in components)

| Name | Hex | Role |
|---|---|---|
| Ink | `#0B0B0C` | brand black |
| Graphite | `#2C2C30` | dark surface / hover |
| Steel | `#8E8E93` | secondary text |
| Mist | `#D9D8D4` | lines, disabled |
| Bone | `#F7F6F3` | brand off-white |
| White | `#FFFFFF` | paper / cards |
| Green | `#2E7D5A` | success base |
| Amber | `#B07C2A` | warning base |
| Red | `#B3452E` | error base |

### Semantic tokens — what to use where

Components must reference **semantic tokens**, not hex values. That’s the entire dark-mode strategy: flip the theme, every component follows.

| UI element | Token | Light | Dark |
|---|---|---|---|
| App background | `--satra-bg-app` | `#F7F6F3` | `#0B0B0C` |
| Recessed wells / grouped-list bg | `--satra-bg-subtle` | `#EFEDE8` | `#0F0F11` |
| Card on app background | `--satra-surface-card` | `#FFFFFF` | `#141416` |
| Card on a card (nested) | `--satra-surface-card-nested` | `#F7F6F3` | `#1D1D20` |
| List row | `--satra-surface-list` | `#FFFFFF` | `#141416` |
| List row hover/pressed | `--satra-surface-list-hover` | `#F2F1ED` | `#1A1A1D` |
| Card border | `--satra-border` | `#E4E2DD` | `#26262A` |
| Divider between rows | `--satra-divider` | `#ECEAE5` | `#1F1F23` |
| Title / headline | `--satra-text-title` | `#131316` | `#F7F6F3` |
| Body text | `--satra-text-body` | `#2C2C30` | `#D9D8D4` |
| Subtitle | `--satra-text-subtitle` | `#55555A` | `#A8A8AD` |
| Muted / meta / captions | `--satra-text-muted` | `#8E8E93` | `#8E8E93` |
| Text on accent | `--satra-text-inverse` | `#F7F6F3` | `#0B0B0C` |
| Accent (selection, active tab, links) | `--satra-accent` | `#0B0B0C` | `#F7F6F3` |
| Accent tint (selected row bg, chips) | `--satra-accent-soft` | `rgba(11,11,12,.06)` | `rgba(247,246,243,.08)` |
| Primary button bg / text | `--satra-btn-primary-bg` / `-text` | Ink / Bone | Bone / Ink |
| Primary button hover | `--satra-btn-primary-hover` | `#2C2C30` | `#FFFFFF` |
| Primary button disabled | `--satra-btn-primary-disabled-*` | Mist / Steel | Graphite / `#55555A` |
| Secondary button border / text | `--satra-btn-secondary-*` | Mist / `#131316` | `#3A3A3F` / Bone |
| Primary icons | `--satra-icon-primary` | `#0B0B0C` | `#F7F6F3` |
| Secondary icons (the 40% rule) | `--satra-icon-secondary` | `rgba(11,11,12,.4)` | `rgba(247,246,243,.4)` |
| Success text/icon · bg | `--satra-success` · `-bg` | `#2E7D5A` · `#E7F1EC` | `#4E9E76` · `rgba(46,125,90,.18)` |
| Warning text/icon · bg | `--satra-warning` · `-bg` | `#B07C2A` · `#F7EFDF` | `#C99A4B` · `rgba(176,124,42,.16)` |
| Error text/icon · bg | `--satra-error` · `-bg` | `#B3452E` · `#F6E7E3` | `#C96A54` · `rgba(179,69,46,.16)` |
| Focus ring | `--satra-focus-ring` | `rgba(11,11,12,.35)` | `rgba(247,246,243,.4)` |
| Modal scrim | `--satra-scrim` | `rgba(11,11,12,.5)` | `rgba(0,0,0,.6)` |
| Card shadow | `--satra-shadow-card` | `0 1px 3px rgba(11,11,12,.06)` | none |

Each status color also ships a `-text` variant (e.g. `--satra-error-text`) — a darker/lighter shade for text sitting **on** the tinted `-bg`, so banners always pass contrast.

---

## 4. How to wire it up

### Web

```html
<link rel="stylesheet" href="colors/tokens.css">
```

```css
body            { background: var(--satra-bg-app);        color: var(--satra-text-body); }
h1, h2          { color: var(--satra-text-title); }
.card           { background: var(--satra-surface-card);  border: 1px solid var(--satra-border);
                  box-shadow: var(--satra-shadow-card); }
.card .card     { background: var(--satra-surface-card-nested); }
.btn-primary    { background: var(--satra-btn-primary-bg); color: var(--satra-btn-primary-text); }
.btn-primary:hover    { background: var(--satra-btn-primary-hover); }
.btn-primary:disabled { background: var(--satra-btn-primary-disabled-bg);
                        color: var(--satra-btn-primary-disabled-text); }
.btn-secondary  { background: var(--satra-btn-secondary-bg); color: var(--satra-btn-secondary-text);
                  border: 1px solid var(--satra-btn-secondary-border); }
.banner-error   { background: var(--satra-error-bg); color: var(--satra-error-text); }
.banner-warning { background: var(--satra-warning-bg); color: var(--satra-warning-text); }
.banner-success { background: var(--satra-success-bg); color: var(--satra-success-text); }
```

### Dark / light mode

`tokens.css` follows the OS automatically (`prefers-color-scheme`). To let users override:

```js
// force dark:   <html data-theme="dark">
// force light:  <html data-theme="light">
// follow OS:    remove the attribute
document.documentElement.dataset.theme = 'dark';
```

Rules of the theme switch:

1. **Never hardcode a hex in a component.** If you need a color that has no token, you probably need a new token — add it to both themes at once.
2. Dark mode is **not** inverted light mode: shadows disappear (elevation comes from surface steps Ink → `#141416` → `#1D1D20`), and status colors get brighter + their backgrounds become translucent tints.
3. The accent flips (Ink ↔ Bone). Anything “selected” or “active” uses `--satra-accent`, so selection always reads as the strongest thing on screen.
4. Test both themes with the same screen side by side before shipping.

### Native (iOS / Android / React Native)

Read `colors/tokens.json` and map `light` / `dark` dictionaries to your platform’s theme system (`UIColor(dynamicProvider:)`, Compose `darkColorScheme()`, RN context). Token names are identical.

---

## 5. Typography

| Role | Font | Weight | Notes |
|---|---|---|---|
| Wordmark | Outfit | 600 | lowercase, −1.5% tracking — ship as image, don’t re-type |
| Titles / UI | Outfit | 600 | Display 56 · Headline 32 · Title 20 |
| Body | Outfit | 400 | 15 px / 1.5 |
| Captions / labels | Outfit | 500 | 12 px |
| **All numbers** (balances, addresses, hashes) | Space Grotesk | 500 | `font-variant-numeric: tabular-nums` — nothing shifts when values tick |

```html
<link href="https://fonts.googleapis.com/css2?family=Outfit:wght@400;500;600;700&family=Space+Grotesk:wght@500;600;700&display=swap" rel="stylesheet">
```

---

## 6. Iconography

12 icons in `icons/`, drawn on the mark’s DNA: 24-px grid, solid geometry, quarter-disc corners, secondary elements at 40% (the tessera two-tone rule).

- They’re `currentColor` — color them with `--satra-icon-primary`; use `--satra-icon-secondary` for inactive/nav states.
- Default stroke weight is 2.5–2.6 at 24 px. Scale the whole icon, never the stroke alone.
- Set: `add, move, receive, swap, list, empty, wallet, assets, history, security, scan, settings`.
- New icons must follow: geometry first, 2 px corner radii, one solid + one 40% element max.

---

## 7. App icon & splash

- **iOS:** upload `app-icon/app-icon-1024.png` (square, full-bleed — Apple rounds it).
- **Android:** use `app-icon-rounded-512.png`, or build an adaptive icon: background layer = solid Ink `#0B0B0C`, foreground layer = `logo/png/satra-mark-bone-512.png` at ~70% of the canvas.
- **Web:** `favicon.svg` first, PNG 32/16 fallbacks.
- The app icon is always **Ink tile + Bone mark**. Never white-on-white, never colored.
- The mark fills **70% of the tile** — at every size (1024 → 16 px favicon). Don’t shrink it for “safety margins”; the geometry survives edge-to-edge.
- **Splash:** dark splash (`splash-dark`) is the default for both themes — it hides load time best on OLED. Use `splash-light` only if the platform forces theme-matched splashes. The vector master `splash-dark.svg` rescales to any resolution: mark at center, wordmark 104 px below it, tagline 34 px near the bottom.

---

## 8. Do & Don’t

**Do**
- Ink on Bone, Bone on Ink — always maximum quietness
- Use the mark alone once the brand is established in context
- Keep Gain green / Loss red strictly for numbers and status

**Don’t**
- Recolor, gradient, outline, shadow, rotate, or rearrange the mark
- Set the wordmark in caps or another font
- Put status colors in marketing or the logo
- Hardcode hexes in app code — tokens only
- Place logos on photography without an Ink/Bone tile behind them
