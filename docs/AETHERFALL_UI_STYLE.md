# Aetherfall UI style

The house style for every Aetherfall web window (Central Market, Season Pass,
Cash Shop) and other branded UI. Look: Aion's dark broker/warehouse windows,
lit with Aetherfall's ice-silver and aether glow. Reference implementation:
`game-server/config/central-market/media/market.css`.

## Palette

| Token | Hex | Use |
| --- | --- | --- |
| abyss | `#070d16` | page background, footer |
| deep | `#0a1521` | side columns, warehouse |
| surface | `#0e1b29` | panel titles, cards, table rows |
| raised | `#142538` | active tab/category, book heading |
| line / line-hi | `#1f3447` / `#2e4a61` | dividers / control borders |
| ice / mist / dim | `#dfe9f0` / `#93abbc` / `#6f889a` | text / secondary / hints |
| aether / aether-deep | `#8fe3ec` / `#3a9fbe` | focus, selection, crest, meters, charts |
| gold / gold-hi / gold-deep | `#d6b878` / `#f1dca8` / `#8a6d3c` | trim, prices, headings, section rules |
| title | `#f4e8c8` | window titles |
| rise / fall | `#8fd8b4` / `#e8a08e` | gains, availability / losses, errors |

Item grades follow Aion: common `#dfe9f0`, superior `#8bcb6d`, heroic `#6fb0f5`,
fabled `#f1d08a`, eternal `#f0a85e`, mythic `#d395ee`.

## Language

- Georgia serif for titles, headings and large Kinah values; Tahoma for everything else.
- Gold 1px rule under the header, tab bars, panel titles and table headings; cyan
  for selection and focus. Gold = value/ownership, cyan = aether/interaction.
- Ornament: `✦` crest and section marks (cyan). The realm name appears as
  letter-spaced gold `AETHERFALL` small caps beside the window title.
- Primary actions are gold bevel buttons; everything else is flat navy with a
  lighter top border.

## Rendering rules (embedded Awesomium WebKit, performance first)

- No CSS variables, `calc`, flexbox or grid; ES5 script. Use fixed absolute panes.
- Flat fills. Gradients only on fixed small chrome (header, primary buttons, meters).
- No transitions, animations, filters, blur, `backdrop-filter`, text shadows on
  scrolling content, or box shadows on rows/buttons. One translucent layer max (dialog shade).
- Hover/selection changes colours and pre-reserved borders/outlines only, never size.
- Script toggles layout with classes; avoid reading `offsetHeight`/layout to position panes.
- Images load lazily for visible rows only; no background art behind scrolling lists.
- Bump `?v=` asset versions in the HTML whenever CSS/JS change.

## Client interface

The switchable in-game skin addon follows this guide; its plan is
`docs/AETHERFALL_UI_MOD_PLAN.md`.
