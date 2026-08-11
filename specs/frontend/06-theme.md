# 06 — Visual Theme (GW1 Aesthetic)

Resolves the styling open question from [00-overview](00-overview.md). Goal: evoke Guild Wars 1's UI chrome — dark carved-wood/bronze frames, parchment content panels, gilded borders — without bundling any of ArenaNet's actual game assets. This is a fan-built guild tool, not an officially licensed product, so the look is built from open-license fonts and hand-authored CSS rather than fonts/icons/textures extracted from the client.

## Approach: hand-rolled CSS, not a component framework

Skip Tailwind/MUI/Chakra/etc. This is a strongly bespoke visual identity that a generic utility or component framework would fight rather than help with — more effort goes into overriding the framework's own opinions than into just writing the CSS. Instead:
- **CSS Modules** (`*.module.css`, Vite supports this natively) per component, for scoping.
- One shared `src/styles/theme.css` defining the design tokens below as CSS custom properties, imported once globally in `main.tsx`.

## Design tokens (`src/styles/theme.css`)

```css
:root {
  /* chrome */
  --gw-bg-dark: #1a120b;          /* app background, deep bark brown */
  --gw-panel-dark: #2b1d12;       /* nav/sidebar chrome */
  --gw-border-gold: #b8860b;      /* primary accent border */
  --gw-border-gold-bright: #d4af37;

  /* content panels */
  --gw-parchment: #e8dcc3;
  --gw-parchment-dark: #cfc0a0;
  --gw-text-on-parchment: #2b1d12;
  --gw-text-on-dark: #e8dcc3;

  /* status */
  --gw-success: #4a7c3f;          /* completed run */
  --gw-danger: #7c2d2d;           /* wipe */
  --gw-muted: #6b6355;            /* resign / unknown / unresolved role */

  /* typography */
  --gw-font-display: 'Cinzel', serif;
  --gw-font-body: 'EB Garamond', Georgia, serif;
}
```

`Cinzel` and `EB Garamond` are open-license Google Fonts — no asset-rights concerns — that read as "carved stone / illuminated manuscript," evocative of GW1's UI feel without touching its actual (proprietary) font.

## Typography usage

- **Headings, nav labels, button text, role badges**: `--gw-font-display` (Cinzel). Decorative and low-legibility at small sizes or in dense text, so reserve it for short, large text only.
- **Body copy, table cells, form inputs**: `--gw-font-body` (EB Garamond) — the run-history and leaderboard tables (`specs/frontend/04-leaderboards.md`, `05-run-history.md`) are data-dense and need a legible serif, not the display font. If EB Garamond's numerals read as too stylized once real duration/date data is on screen, drop numeric cells to a plain `system-ui` — a call to make against real data, not preemptively.

## Panel/frame treatment

Rectangular, not rounded — GW1's chrome is carved-wood/metal frames, not soft corners. A reusable panel style:

```css
.gw-panel {
  background: var(--gw-parchment);
  color: var(--gw-text-on-parchment);
  border: 2px solid var(--gw-border-gold);
  box-shadow: inset 0 0 0 1px var(--gw-border-gold-bright), 0 2px 6px rgba(0, 0, 0, 0.4);
  padding: 1rem 1.25rem;
}
```

Nav/sidebar chrome uses the dark tokens (`--gw-bg-dark` / `--gw-panel-dark`) with gold borders instead of the parchment tokens — dark frame around parchment content, matching GW1's actual panel-in-frame layout.

## Status & role indicators

- Run completion badge (`specs/backend/06-run-history.md`'s `completed`/`end_reason`): green (`--gw-success`) for completed, red (`--gw-danger`) for wipe, muted grey (`--gw-muted`) for resign/unknown.
- Role badges (T1–T4, LT, Spiker, Derv, SoS, Necro, RangerNecro, Emo, `specs/backend/02-ingestion-upload-run.md`): small text pills in the display font, one flat accent color per badge for v1 — no per-profession iconography yet (see below).
- `role = null` (unresolved profession combo): render with `--gw-muted` and an explicit "unresolved" label rather than blanking it out — it's diagnostic information worth surfacing, not an empty cell.

## Deferred: profession/skill iconography

GW1's actual profession icons are ArenaNet assets and aren't being bundled. If icons are wanted later, that means either commissioning original artwork or sourcing something appropriately licensed. v1 ships with text-only profession/role labels — flagging this so it isn't assumed to already be handled, not blocking anything.

## Scope

No other frontend spec needs structural changes for this — `01`–`05` describe page structure and data flow, not visual treatment, and now have a concrete theme to render against.
