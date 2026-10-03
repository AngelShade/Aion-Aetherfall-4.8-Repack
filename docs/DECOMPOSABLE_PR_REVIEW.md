# Decomposable PR backport and source verification

Verified on 3 October 2026 against PR #200 head
`404a14855bfdb9c6d45570f426361f3c72a51cd3` and PR #211 head
`8789b5f858add24e8df2b51d12e23099f05aeb2b`.

## Result

The server source now uses [PR #200's rework](https://github.com/beyond-aion/aion-server/pull/200):
independent reward branches, weighted alternatives, bundles, class/faction/level
restrictions, selection packets, possession-limit checks, cooldown handling and
custom overrides. The retail and upstream custom XML files are copied exactly
from the pinned commit. Both PRs were open and unmerged when inspected; the
upstream `4.8` branch still had empty definitions for the six PR #211 boxes.

PR #211's Angel Wings table agrees with the new data. Its other five definitions
were replaced with the documented upstream tables. The 19-egg pool, ten summer
items and 50-cherry quantity were not substantiated by the retrieved evidence.

This is a verified implementation of the available tables, **not certification
of every historical official 4.8 reward or probability**. In particular, the
Midsummer multi-item pool remains unresolved.

## Exact-ID findings and sources

| Original box | Applied contents | Source and finding |
| --- | --- | --- |
| `188052649` Pet Egg Box | Traveling Kitter Egg `190020174` or Golden Pack Saam Egg `190020180`, one egg, 50% each | [Published contents](https://aioncodex.com/query.php?a=contents&id=188052649&gid=4320&v=2&l=us), [4.8 metadata](https://aioncodex.com/48/item/188052649/). Confirms the two-egg table in #200; does not support #211's 19 alternatives. |
| `188052918` Angel Wings Chest | One of Black `187050022`, Red `187050023`, Ghostly `187050024` or White `187050025`, 25% each | [Published contents](https://aioncodex.com/query.php?a=contents&id=188052918&gid=5122&v=2&l=us), [4.8 metadata](https://aioncodex.com/48/item/188052918/). Agrees with both PRs. |
| `188053543` Shu-Ghost box | Shu-Ghost's Egg `190020221` plus **200** Aether Cherries `182007162`, guaranteed together | [Egg group](https://aioncodex.com/query.php?a=contents&id=188053543&gid=6807&v=2&l=us), [cherry group](https://aioncodex.com/query.php?a=contents&id=188053543&gid=6810&v=2&l=us), [4.8 metadata](https://aioncodex.com/48/item/188053543/). #211's egg is supported; its 50 quantity is not. |
| `188053544` Pinkybell box | Pinkybell's Egg `190020223` plus **200** cherries, guaranteed together | [Egg group](https://aioncodex.com/query.php?a=contents&id=188053544&gid=6808&v=2&l=us), [cherry group](https://aioncodex.com/query.php?a=contents&id=188053544&gid=6810&v=2&l=us), [4.8 metadata](https://aioncodex.com/48/item/188053544/). Same quantity correction. |
| `188053545` Royal Kitter box | Royal Kitter Egg `190020214` plus **200** cherries, guaranteed together | [Egg group](https://aioncodex.com/query.php?a=contents&id=188053545&gid=6809&v=2&l=us), [cherry group](https://aioncodex.com/query.php?a=contents&id=188053545&gid=6810&v=2&l=us), [4.8 metadata](https://aioncodex.com/48/item/188053545/). Same quantity correction. |
| `188053634` Midsummer Honey Box | Stigma Shard `141000001`, count 1, as recorded by #200 | [Published contents](https://aioncodex.com/query.php?a=contents&id=188053634&gid=7104&v=2&l=us), [4.8 metadata](https://aioncodex.com/48/item/188053634/). Both retrieved tables agree on the shard. The installed tooltip describes varied contents; it does not identify an actual historical pool. #211's summer items and equal odds remain unsupported. |

The contents endpoints were discovered from the exact-ID pages, retrieved
independently, and compared with the upstream XML. AionCodex is a third-party
extracted-data database; its US dataset is not an archived 4.8 server dataset.
Agreement between it and #200 is corroboration, not proof of independent data
lineage or identical 4.8 odds.

The [PR #200 description](https://github.com/beyond-aion/aion-server/pull/200)
attributes structure to the 4.9 client and quantities/weights to 4.6 and 5.8
server data matched by cName, and explicitly documents cross-version gaps.
Those original datasets were not independently obtained during this review.
The [response to #211](https://github.com/beyond-aion/aion-server/pull/211#issuecomment-5958526988)
is therefore justified in asking for the missing provenance.

## Primary client evidence and its limit

Read the installed Aion 4.8 NA client at
`<CLIENT_ROOT>` without modifying it:

- `Data/Items/Items.pak`, entry `client_items_misc.xml`, establishes all six
  original IDs, their internal names and tooltip keys.
- `L10N/enu/data/data.pak`, entries `strings/client_strings_item.xml`,
  `client_strings_item2.xml` and `client_strings_item3.xml`, establishes the
  English names and descriptions.
- The Pet Egg tooltip describes pack-pet eggs generally. The three named-pet
  boxes describe an egg and cherries, without quantities. The wing tooltip
  identifies Angel Wings generally. Midsummer describes varied items without
  listing them. None establishes a 19-egg pool, ten summer rewards or exact odds.
- The Items archive has no disassembly-table entry. This checked client cannot
  supply the missing server reward tables.

`tools/decomposable-items/sources.json` retains archive-entry SHA-256 hashes,
IDs, internal names, tooltip keys, descriptions, source URLs, table hashes and
the pinned upstream hashes. Raw downloaded evidence is under
`output/pr211-review`; it is intentionally outside Git.

## Local integration

The migration found 27 local differences from #200's base. Six are the reviewed
#211 boxes; the other **21** remain effective in
`local_decomposable_items.xml`, marked `override="true"`. This includes the
sixteen previously restored Season Pass boxes and five existing local supply
bundles. Upstream `custom_decomposable_items.xml` is retained unchanged.

The local layer preserves exact original loot probabilities, inclusive quantity
ranges, grouped rewards, class matching and faction matching. A small optional
`max_count` extension preserves the three existing local quantity ranges;
upstream retail definitions continue using fixed `count`.

Additional integration changes:

- `reload decomposables` loads all XML definitions together, preserving override
  precedence regardless of file order and rejecting duplicate definitions.
- Season Pass validation and previews use the new sets, with class/faction/level
  filtering and fixed quantity bounds. The sixteen-box generator/checks were
  ported to the local override file.
- Branch sorting uses ascending chance, already corrected in the pinned #200
  revision. The selection-response result byte uses granted `1`, not granted
  `0`, following the [retail disassembly review](https://github.com/beyond-aion/aion-server/pull/200#issuecomment-5658985753).
  Serialization is tested; the byte's effect in the game client remains a live
  acceptance test.
- The legacy one-time sixteen-box mail helper retains its original hash guard;
  it cannot blindly reload these new tables or resend mail after the schema
  transition.

## Verification completed

- All GameServer sources compile under Java 25 against the deployed dependency
  JARs. Offline Maven could not resolve an uncached Git metadata plugin, so the
  full source compile used `javac`; a Maven lifecycle build is not claimed.
- **85,996** source/data/migration checks: exact pinned XML hashes, duplicate
  detection, parent/reward IDs, exact retrieved six-box probabilities and all
  21 preserved local loot distributions across both factions and all 17 classes.
- **14,969** production checks: XSD validation of all three XML files, actual
  JAXB loading, reverse file order, override precedence, rejection paths,
  rare-branch sorting, class/faction/level filtering, inclusive quantity ranges,
  six-box opening logic and selection-response wire layout. The merged loader
  reports **4,091 active definitions**, including **470 overrides**.
- **10,409** restored-box source/catalog checks and **8,889** production restored
  box checks preserve the prior sixteen-box work.
- **532** existing Season Pass checks pass, without database tests.
- A scoped prepared JAR contains 18 class replacements and retires the five old
  decomposable-model classes. Every unrelated deployed JAR entry is preserved
  byte for byte. Only the two decomposable consumers change in the shared
  Season Pass service.
- Cohesive installation, backups, later-file protection and rollback after an
  injected mid-install failure pass on disposable deployment copies.
- Focused `git diff --check` passes.

No live player, mail or database was changed. No GitHub response, commit, push,
closure or PR update was performed. The running server retains its previous
code/data until the cohesive bundle is installed and restarted.

## Reproduce and install

From the repository root:

```powershell
python tools/decomposable-items/verify.py --legacy output/pr211-review/local-overrides-before.xml
python client-mods/season-pass/verify_restored_boxes.py
python tools/decomposable-items/prepare_bundle.py --output output/pr211-review/prepared-next
```

The prepared bundle currently resides at `output/pr211-review/prepared-v1`.
Its manifest binds every replacement to the original deployment hash. It
includes the JAR, both upstream data files, the local overrides, schema,
item-template additions, import registry and both affected dynamic handlers.
The deployed item-template comparison found only the two upstream additions,
`188054343` and `188054344`; the existing templates agree.

After gracefully stopping GameServer, install all code and data together:

```powershell
& tools/decomposable-items/install.ps1 -PreparedPath output/pr211-review/prepared-v1
```

The installer refuses a running GameServer or a deployment changed since
staging. Then start GameServer normally. An XML-only reload of the old running
server cannot apply this Java/schema transition.

Live acceptance still requires opening the six boxes, confirming each named egg
and 200 cherries arrive together, testing a selectable reward box, testing the
possession-limit rejection without consuming a box, and checking the existing
class/faction-specific restored boxes and Season Pass previews. Midsummer's
historical varied reward pool requires additional evidence regardless of
whether the applied one-shard table opens successfully.
