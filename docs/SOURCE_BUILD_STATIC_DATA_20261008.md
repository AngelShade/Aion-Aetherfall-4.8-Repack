# Source build/static-data alignment — 8 October 2026

## Cause and repair

**PB-BUILD-002** repairs the reported startup failure:
`unexpected element ... local:"items". Expected elements are <{}set>`.

The complete source-built GameServer already contained the previously prepared
decomposable-item model. Its required matching resources were omitted from the
source-build delivery: runtime still had the old `<items>` XML and schema, while
the maintained source model expects `<set>`. Earlier class/linkage checks did
not exercise the complete static-data bootstrap, so they did not detect this
code/data mismatch. The failure was reproduced at the decomposable-items section
of the actual merged runtime cache.

The normal Maven builder produces the distribution with its maintained data
and handlers. Resource packaging is now mandatory in `tools/build-components.ps1`;
the old `-IncludeResources` argument is accepted for compatibility. Eight matching
resource files and the complete unchanged builder
GameServer JAR were copied together:

- Three decomposable XML files and their schema, including the maintained local
  override definitions from the previously reviewed rework.
- The import registry, using a single merged decomposable folder root.
- Item templates: exactly two required additions, `188054343` (Vasharti
  Legionnaire's Weapon Box) and `188054344` (Vasharti Brigade General's Armor
  Box). The maintained decomposable tables already referenced these parent
  boxes, but their item-template definitions were missing from runtime. Adding
  the definitions makes those references valid; it does not grant items to
  characters. All 102,010 existing item definitions are unchanged.
- Updated `admincommands/Reload.java` and `playercommands/Decompose.java`, whose
  APIs match the source-built model.

This completes runtime resource delivery of the previously prepared
[decomposable rework](DECOMPOSABLE_PR_REVIEW.md); it does not restore the old
six-box data tables. The previously reviewed six-box tables and local override
definitions now accompany the model already in the runtime JAR. Historical
retail-content uncertainty and actual box-opening acceptance remain pending.

Receipt: `playerbots-source-build-20261008-220257-711786` under external
`archives/server/game-server/backups`.

The old merged XML/cache metadata and the three affected generated command
classes were backed up and invalidated. The normal server loaders will rebuild
them when the user next starts GameServer. No server start, restart, attach,
world actors, database access or gameplay test was performed.

## Evidence and preservation

- Normal Maven build and distribution packaging succeed.
- The complete deployed static-data copy plus exact builder resources passes
  production XML merge, full XSD validation and JAXB unmarshalling, including
  asynchronous data callbacks: **102,012 items, 4,091 decomposable definitions,
  470 overrides**. This is an isolated data load, not a GameServer startup.
- The maintained check was repeated after installation against actual deployed
  XML/schema resources and passed with the same counts.
- The report is hash-bound to the builder JAR, all checked resource files and
  766 unchanged runtime static-data inputs. Both changed handlers compile.
- **3,549 JAR entries remain byte-identical**, including all movement-repair,
  bot, class-strategy, core and bundled-UI class/resource entries. Only the normal
  builder manifest differs. PB-REPAIR-NAV-004 is retained.
- Unrelated imports, geometry, settings, Commons, launcher, database and client
  files remain preserved. Refreshed inventory passes 20 checks/31 client hashes.

## Required delivery gate

`tools/check-source-static-data.py` now stages actual runtime XML outside the
installation, optionally overlays explicit unchanged Maven assembly resources,
and executes the production data merge/schema/JAXB path without GameServer,
database or world initialization.

`tools/deliver-source-component.py` requires that successful complete load for
every GameServer JAR delivery, even when only Java source changed. It binds the
report to the exact builder JAR and current data hashes, checks package linkage,
backs up every replacement/invalidation, refreshes shutdown inspection, and
copies complete builder files unchanged with rollback guards. No method-by-method
patching or generated class rewriting is used.

Actual server startup, bot movement and box opening are pending user testing.
