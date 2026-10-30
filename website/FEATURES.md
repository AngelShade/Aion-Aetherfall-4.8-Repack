# Player website features

## Repack Tools & Codex

The [GamezAION Codex](https://codex.gamezaion.com/) makes searchable game reference
data a first-class part of its website. [EuroAion's play guide](https://euroaion.com/en-US/HowToPlay)
and [FAQ](https://euroaion.com/en-US/FAQ) keep preparation and help close to the game.
The Aetherfall adaptation adds original tools for an owner-run Aion 4.8 repack:

- Eleven-class explorer with playstyle filters and comparison of up to three classes.
- Six-person companion-party planner: players, owned alts and temporary companions,
  class/role choices, starting formations, composition summary, device-local saving,
  share links and roster copying. It does not change characters or recruit bots.
- Instance atlas with name/map-ID search, faction, level and member-limit filters,
  pagination and separate faction entry facts. The normal website builder generates
  it from maintained world maps and instance data, retaining input hashes. It shows
  included defaults, not live eligibility, drop guarantees or owner-customised rules.
- Searchable companion-command reference with categories and copy controls, checked
  against maintained native help by the normal builder.

The integrated `repack.tools` CMS module gates all five routes, navigation and
sitemap entries. These features work without a game database connection. No
competitor code, copy, assets, rates or private data are imported.

The standalone website keeps its Laravel/Filament CMS and native Aetherfall account
integration. Public player features read the configured native databases; they do
not change gameplay or player records.

## October 2026 comparison

Public pages reviewed on 28 October 2026:

| Common feature | Reference | Aetherfall |
| --- | --- | --- |
| News, guides, downloads and account dashboard | [EuroAion](https://euroaion.com/en-US), [Aion America](https://www.americaprivateserver.com/en/) | Existing features retained; news now has search and category filters. |
| Public rankings | [GamezAION](https://gamezaion.com/page/rankings/discipline/), [Aion America](https://www.americaprivateserver.com/Home/Rankings) | AP, GP, lifetime PvP kills and level; name, faction and class filters. These are saved statistics, not a seasonal or arena ladder. |
| Character and legion browsing | [Aion Europe](https://aion-europe.eu/php/index.php?page=home) | Public character summaries and contribution-based legion rankings with paginated rosters. Inventory, account data and current locations stay private. |
| Weekly siege/event timetable | [EuroAion](https://euroaion.com/en-US/Tools/Schedule) | Native schedule included in each build; optional owner schedule file and server timezone. Published Event articles appear alongside it. |
| Live server and population information | [Beyond Aion](https://beyond-aion.com/), [EuroAion](https://euroaion.com/en-US) | Existing native status preserved; dedicated status page separates players from bots and unavailable data. |
| Clear first-time play instructions | [EuroAion](https://euroaion.com/en-US) | Browser-local install checklist, returning-player update instructions and solo/co-op paths. |
| Follow updates | [Beyond Aion](https://beyond-aion.com/) | RSS feed of published news, linked from the news list and page metadata. |

Generated bots, staff, inactive accounts and deleted characters are excluded from
public rosters. Normal player-owned alts remain eligible. Ranking positions refer
to the displayed filtered results. Reads are limited and briefly cached; game-save
delays can make website statistics trail in-game progress.

The website's existing module switches also control the new rankings, events and
start pages. Turning a module off removes navigation and blocks its public routes.

## Features requiring a separate service or game change

Some servers also offer vote rewards, automatic donation credit, web shops,
marketplaces, password recovery by email, account achievements and seasonal arena
rewards. These require verified provider accounts, delivery/claim rules, a mail
transport or authoritative game services. They are not simulated by this website.
Existing donation links and in-game trading/shop/pass features remain in place.

No competitor artwork, content, statistics or reward rules are copied.

## Repack descriptions

Public copy presents Aetherfall as a complete Aion 4.8 NA repack for the owner's
own installation, solo play or hosting friends. It does not present the project
as a hosted game service. Server labels refer to actual game components, setup
steps and connection status. Website-configured rates describe the linked
installation and are not presented as universal settings for every repack.

The repack overview covers eighteen feature groups, grounded in the shipped
features in [CHANGELOG.md](../CHANGELOG.md), [Season Pass](../docs/SEASON_PASS.md),
[Account Boons](../client-mods/account-boons/README.md) and
[Profile Stigma controls](../client-mods/stigma-profile/README.md).
The companion website and optional HD textures are described separately.
Content revision v4 refreshes only recognised shipped content from the preserved
v1/v3 baselines; custom articles, drafts, settings, FAQs and downloads are retained.
