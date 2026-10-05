# Night Saendukal encounter repair

The 211701 night encounter is staged in source and `target-deploy` for the next
authorized server start. The existing Eltnen spawn already limits it to 21:00–04:00
and places it at the throne. Its AI still checks for living 211040 on spawn and on
attack, so the daytime giant remains the mutually exclusive encounter.

## Behavior repaired

- Night Saendukal now applies Bellicosity when combat begins and has the level-31
  Saendukal NPC skill pool. The native dialogue IDs remain 340406, 340404 and 340407.
- Royal Guard 211036 had no heal in its skill set despite serving as the encounter's
  healer. It now receives the same level-31 Healing Energy skill used by the nearby
  Kaidan healer, while retaining its current offensive and defensive skills.
- A lethal burst that skips the 50% callback now still starts the four-guard wave.
  Damage is capped at 50% HP while retreat is pending, then the phase check runs
  just after the hit. The earlier death fallback awarded chest 211861 immediately,
  which let parties bypass the guard objective. It now starts the guard wave as a
  final safety path; the chest remains gated on every successfully spawned guard's
  death, and failed guard spawns are excluded from the remaining count.
- Chest 211861 uses custom drops because this server excludes chests from global
  drop rules. It now carries the same named reward pools as giant Saendukal 211040:
  a 35% one-choice legendary sword/spear group, 30% unique Saendukal's Anger,
  30% one-choice Anointed armor group, and 30% Saendukal's Mask.

## Validation boundary

Source and deployed handler/static data are kept in sync. Static data reload,
native fight behavior, actual guard skill casts, chest opening and persisted
rewards remain pending in-game checks. An earlier 5 October snapshot showed
GameServer PID `34968`; the latest process inventory found no GameServer JVM.
The server remains stopped per the offline-only instruction, and no restart or
live attach was performed.
