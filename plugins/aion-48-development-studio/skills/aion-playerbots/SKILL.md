---
name: aion-playerbots
description: Implement or diagnose Aion 4.8 Playerbots, owned alts, movement, combat, quests and native population persistence, including source-grounded upstream port work.
---

# Aion Playerbots

Consult current `AGENTS.md`, the specific feature receipt and only the relevant
subsystem/scope section. For a new port, inspect pinned upstream WoW Playerbots
functions, dependencies and defaults before claiming parity. Map behavior to native
Aion and record deviations in the relevant scope/validation document. Do not reload
the entire historical port audit or reopen completed ports just for pending gameplay.

Native SQL owns identities, pools, memberships, targets, timers and progress. Do not
introduce JSON/properties shadow databases. Preserve IDs, custody and transactional
commit/retry behavior. Distinguish generated temporary bots from owned alts: owned
alts retain class, level, earned gear, appearance, skills and builds unless requested.

For movement, preserve native admission, physical landing acknowledgment, lift/glide
custody and bounded route planning. Examine the relevant maintained navigation/road
catalogue loader rather than fabricating destinations or suppressing failures.
For slow decisions, summarize actual backlog evidence and inspect scheduling scope;
avoid population-wide locks, creation bursts or unbounded hot-path scans.

Follow the normal server builder and affected-file delivery workflow. Do not attach
agents, execute SQL, start/stop servers or run gameplay fixtures without applicable
human authorization. Existing approval continues to apply within its stated scope.
Keep source/binary/data resources consistent, and preserve newer concurrent fixes.

Document the behavior gained, remaining dependency, exact installed destination and
the user's outstanding movement/combat/quest/CPU acceptance. Compilation and copied
hashes do not demonstrate full retail parity or live gameplay success.
