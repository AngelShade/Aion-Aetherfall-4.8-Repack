# Golden Lua boundary trace

Run from the repository root with Python 3.12 and `lupa.lua51` installed:

```powershell
python client-mods/aetherfall-core/tests/trace/run.py --client $env:AION_CLIENT
```

`--smoke` runs a short cold-load, login, settle and `/afui` comparison for rapid
refactor iteration. The full trace drives all 11 tabs, both toggle directions,
all choice/data/profile values, both numeric limits (including all four action
bars), presets and user slots, sharing and malformed inputs, every mover editor
control, cursor dragging, custom windows, lifecycle transitions and synthetic
legacy/corrupt saves. It finishes with 1,200 idle timer ticks (one minute at 20 Hz).

The reference was recorded from the **unmodified** compressed monolith fixtures.
Their exact source hashes are in `fixtures/source-sha256.json`. The first actual
cold/world/options trace was captured before any Lua refactor began; its initial
43,312-record SHA256 was
`26b2a408a2e5bf3a86de6d5089056d520ccb60728e0c2c133aaebc7bda3d10e4`.
Subsequent harness corrections and broader scenarios always replay frozen originals.

The harness reads the real client `bin32/bin32.pak` widget framework without
modifying it. The native bridge is deterministic and stateful: dialog geometry,
instance identities, visibility and movable flags; live registry widget geometry;
pins, backdrops, cursor/focus, scale/screen, camera ABI and values, identity, auras
and slots. Native layout/registry fixtures are exact installed generated resources.
The sibling `hud_native.py` is absent in this checkout, so no native data is invented
to replace it. Original `prepare.py` generated the frozen plugin XML and lazy Lua.

Every public `AFCore` call, raw bridge call and result, widget native API call and
query result, chat message, and save filename/exact UTF-8 bytes is recorded in order.
Saved bytes are additionally base64 encoded in `saved` records. Calls are never
sorted, deduplicated, rounded, filtered or normalized before comparison. A source
refactor that changes call order or file byte order fails the comparison.

Only synthetic character identities and in-memory files are used. No live player
save, game process, binary, PAK, installation, or native build is changed.

The original golden files stay immutable during the options/profile extension.
Current runs use the actual generated lazy resources. Preserved scenarios reserve
the original synthetic plugin slots by dialog name so unrelated native ids remain
comparable. The separate `tests/options.py` run uses ordinary allocation order,
including the options dialog's delayed allocation.

`policy.py` lists the reviewed presentation differences by widget-name prefix:
the old/new options widgets, and the editor's Skin setters only. Editor geometry,
visibility, labels and input remain compared. The options dialog's native backdrop
records resolve through its original fixture slot to the same `AFUIOptions` name.
No whole scenario, native game mutation, input, file read or saved byte is excluded.
The raw output still contains every call before that comparison policy is applied.

Old click inputs are translated by `legacy_actions.py` into real owning
`AF.DB:Set` writes in the same order. The retired bar selector is test input state;
the old combined separated-bar toggle and tooltip offsets write both new settings.
Intermediate native effects stay visible. Profile actions use their owning module.
`audit.py` inventories all remaining API-count/native-effect differences and
validates every saved envelope/checksum; an audit report explicitly does not approve
those differences or turn a failing golden comparison into a passing one.

Current output defaults to the system temporary folder. To inspect a difference:

```powershell
python client-mods/aetherfall-core/tests/trace/diff.py `
  client-mods/aetherfall-core/tests/trace/fixtures/golden.jsonl.gz `
  "$env:TEMP/aetherfall-current-trace.jsonl.gz"
```

Each trace's `.summary.json` records source and framework hashes, total call
counts, exact uncompressed trace SHA256, per-source `debug.sethook` line hits,
Lua 5.1 bytecode executable line coverage, every unreached executable line and
per-feature section coverage. The full summary separately records idle-minute
call counts. This is offline boundary equivalence; visual rendering and live
gameplay remain owner acceptance.

`--record-reference` explicitly replays the frozen monolith, never current modular
Lua. Use it only after reviewing a harness/input change. A current modular build
loads `load_order.json` in order. No frozen monolith is installed or packaged.

The new options/profile renderer has a separate `options-golden.jsonl.gz`, created
only with `tests/options.py --record-reference --output <fresh-external-file>` after
review. Its assertions exercise first-use construction, deferred factories, every
shipped page/tab, all control kinds, cursor and RGB dragging, native combo selection,
steps, input validation, search, deep links, window positioning, named profiles,
scope/account/character/module copying, all six bars, AFUI1 migrations, AFUI2 sharing,
invalid imports and bounded storage refusal. It never overwrites the monolith goldens.

Known intentionally unreachable paths: HUD aura timer labels are compiled out by
`POOL = 0`; their source remains covered by the unreached-line report. Native
API/refusal/failure paths not induced by these deterministic scenarios, `/afcore`
watch/diagnostic commands, and unexpected addon errors are listed as uncovered,
not claimed as verified. The original generated XML omits optional AFHTarget and
TargetDPFill widgets and builds options/movers eagerly, so optional target-overlay
and deferred lazy-builder bodies are also unreached. Exact reasons and ranges are
in `fixtures/coverage-exclusions.json`. All broad feature sections are exercised.


## Scoped options migration review

The original monolith goldens stay unchanged. `fixtures/migrations-smoke.json.gz`
and `fixtures/migrations.json.gz` retain exact input-boundary differences for the
scoped DB, lazy options and instance migration. Each entry includes the original
input, both ordered block hashes, every specific call-count delta and a reviewed
reason. The comparison additionally requires identical cumulative native and
non-presentation widget state at every original boundary. An unreviewed ledger
or any changed block fails. No entire scenario is excluded.

`run.py` records hashes before executing the trace and rejects any source change
during the run. A completed trace can be reused for the aggregate checks only
when maintained source, trace harness, raw-content and client-framework hashes
still match. This avoids repeating an unchanged five-minute scenario run.

```powershell
python client-mods/aetherfall-core/tests/run.py --client $env:AION_CLIENT --reuse-trace $env:AFUI_VERIFIED_TRACE
```

The default aggregate command still records and compares a fresh full trace.
`options-golden.jsonl.gz` is a separate, assertion-reviewed reference for new
controls and profiles. It includes compact scale-two geometry, shipped profile
controls, native resource format writes, old master-opacity conversion, corrupt
newer-pair fallback and failed-save retry.
