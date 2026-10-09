# PB-PORT-005C: Cleric recovery timing

## Confirmed gap and bounded port

This slice adds Cleric health-band ordering from pinned WotLK Playerbots
`037c01418b5d01506917a3db9b44fd56ac5f965c`, using
`src/Ai/Class/Priest/Strategy/HealPriestStrategy.cpp::InitTriggers` and
`src/Ai/Base/Trigger/HealthTriggers.cpp`. SHA-256 values match the existing
`third-party/playerbots/SHA256SUMS`:

- HealPriestStrategy: `978dd9cdb7421e508185e409169a9d171ee29fa7258d852f437575ebda37c83e`.
- HealthTriggers: `ca79c770996537c1ec72eb14ad7a616bb2617d3160cf90b061a1b15e26009bad`.

The upstream strategy distinguishes critical/low-health direct recovery from
almost-full Renew maintenance and protective setup. These are behavior purposes,
not copies of WoW spell IDs or values. Native Aion learned skills supply the
effects, amounts, targets, costs, chain conditions and cooldowns.

Existing Aion support already includes native affected-target group/pet healing,
injury/range triage, hybrid heal/cleanse scoring, in-flight reservations with an
emergency exception, legal resurrection, stronger/effect stacking checks and
native cast execution. Those are preserved rather than re-ported.

The missing distinction was in the final heal scorer: `PlayerBotHealing.snapshot`
included a HoT's first tick in the heal amount but `priority` penalized only cast
duration. An instant-cast HoT with a large delayed tick could outrank a smaller
immediate heal at critical HP. Conditional heals received the generic emergency
band even when their activation threshold had not been reached. Hybrid snapshots
also added future HoT ticks to immediate recovery.

## Native implementation

`PlayerBotCleric` applies only to CLERIC. Session names its strategy `cleric recovery`
and retains the same combat/non-combat composition and PASSIVE-order gate.
`PlayerBotHealing.priority` evaluates the new recovery profile separately for each
actual eligible recipient and retains existing group/pet bonus and cleanse scoring.

- Direct healing arrives at cast completion; snapshot amounts use native boost,
  debuff and disease rules without calculating effects or rolling criticals.
- HoTs arrive after cast duration plus `AbstractOverTimeEffect`'s native first
  tick (`checktime + 300ms`). Zero-checktime effects schedule no recovery.
- HP CaseHeal arrives immediately only at/below its native `cond_value`; read-only
  metadata getters expose that condition. Above it, the effect remains protective
  setup capped at the ordinary support band, not emergency recovery. MP CaseHeal
  is not HP recovery.
- Hybrid effects count only healing arriving at the earliest delivery time.
  Future ticks are not claimed as immediate HP.
- Below 55% HP, the scorer prefers useful faster recovery, with a larger timing
  preference below 30%. At 70–85%, needed periodic maintenance gains a small
  preference. The existing heal-fit/overheal calculation still uses the native
  useful amount. A slower learned heal remains a fallback if quicker skills are
  unaffordable, disabled or unavailable.

There is no new casting interrupt, resource grant, skill/build replacement,
database write, actor spawning or item-ID operation. Native CaseHeal execution
is unchanged. Chanter and every other class retain their existing scoring.
Priest base-class behavior is also unchanged by this Cleric-only slice.

## Validation and delivery

`PlayerBotClericCheck` exercises production Session priorities, final CastAction
usefulness/possibility and engine arbitration on world-free actors. It reproduces
the former delayed-HoT ordering and covers critical/low HP, conditional thresholds,
hybrid timing, legal slow fallback, costs/cooldowns, duplicate HoTs, full/dead/
unspawned/diseased recipients, reservation preservation and actual Cleric XML
metadata. No native cast, world registration, database or ID operations run.

Delivery must use `python tools/release-game-server.py --scope PB-PORT-005C` and
the same canonical gate with `--review <review> --install` after focused regression
checks. Complete Maven artifacts, handler compilation, static-data load, linkage,
client/settings hashes, process inspection and rollback guards are mandatory.
Build output and receipts remain external. Installation evidence is recorded below; gameplay acceptance is **PB-VAL-013 pending user testing**.

Next bounded class review is **PB-PORT-005D Chanter**: compare existing native
mantras, buffs, melee and supporting heals with exact upstream purposes before
choosing missing behavior. Full Cleric strategy (including focus assignments,
proactive protection and broader role composition), full class/world parity and
the item-ID release investigation remain unfinished.

## Current release evidence

Normal Maven build: external `staging/target/release-20261009-043646-914342`;
GameServer SHA `bc9de279a811fbd35c8ab1188ada2f07e41a4fa34fe6560efad32f50d9362d6c`.
Commons remains byte-identical. Release preparation reports zero changed managed
data resources, 1,757 compiled handlers, 3,284 linked classes / 171,909 executable
member references and 3,544 prior JAR entries unchanged. Only Healing.priority
and Session.tick change existing method behavior; CaseHeal only adds read-only
getters and retains all eight execution methods.

87 Cleric checks plus 14 existing suites pass: 1,905 world-free assertions total.
Initial shutdown and client-hash guards refused before copying. After the user's
normal server shutdown and full passing client inventory refresh, the canonical
release completed guarded backup/copy in receipt
`playerbots-source-build-20261009-045131-933181`. The builder GameServer JAR was
copied unchanged; current Commons, launcher, all managed resources, settings,
geometry and client files remain unchanged. Affected generated handler caches
were archived and invalidated through the required gate. Postinstall inventory
passes all 20 checks / 31 client hashes. No startup, attach or gameplay occurred;
PB-VAL-013 remains pending user testing.
