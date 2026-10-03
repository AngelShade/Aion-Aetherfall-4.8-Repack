# AzerothCore Playerbots adaptation

Source: https://github.com/mod-playerbots/mod-playerbots

Pinned revision: `037c01418b5d01506917a3db9b44fd56ac5f965c` (retrieved 2026-10-03).

`upstream/` contains unchanged reference files used in the port. Their SHA-256 hashes are
recorded in `SHA256SUMS`. `LICENSE` and `AUTHORS.md` are unchanged upstream copies.
The original source headers permit GPL version 2 or any later version; this repository
uses GPL version 3. Preserve upstream attribution when distributing the adaptations.

The Aion game server is Java. These C++ files depend on AzerothCore's WoW player,
spell, world/session, navigation and combat APIs, so they are reference source rather
than a library linked into Aion. The Java adapters use Aion's real Player, skill engine,
equipment, progression, geodata and native packets.

| Upstream source | Aion adaptation |
| --- | --- |
| Engine.cpp, Action.h, Trigger.h, Strategy.h | PlayerBotEngine: strategy/trigger/action arbitration, usefulness/possibility checks, prerequisites, alternatives and relevance multipliers |
| HealPriestStrategy.cpp, HealthTriggers.cpp | PlayerBotRules and PlayerBotSession: health bands, emergency healing, healing before damage |
| TankAssistStrategy.cpp, DpsAssistStrategy.cpp | PlayerBotSession: party aggro, tank pickup and leader target assistance |

Intentional differences: each decision rebuilds a bounded queue from current state,
ties are stable, and failed movement prerequisites cannot authorize a cast. Actions
are re-evaluated next tick rather than retaining stale targets across combat/death/map
transitions. WoW spell IDs, rotations, opcodes and database schemas are not portable
and are not treated as Aion equivalents. The rest of Playerbots has not been ported;
see `docs/PLAYERBOTS.md` for the implementation and validation boundaries.
