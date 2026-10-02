The repair replaces the previous `untimed_motion` exclusions with real records
from the installed Aion 4.8 NA client. The original missing-data WARN remains.

54 of the 59 reported motion names now have 2,153 recovered timing rows. Every
row includes its exact animation-marker document and animation name in `source`.
Archive hashes and recovered values are in `motion-times/recovered-4.8.json`.
The existing 304 motion entries are preserved.

Player actions now have their actual animation lengths. Examples are Embark
(`sumrobot`, cast animation 2.0000002 seconds), quest drinking (5.0000005 seconds
for female characters, 4.8333335 for male characters), and social diving
(8.8000011 seconds except Elyos male, 5.3000002). Gathering records use the client
skill action identifiers `gathering_a`, `gathering_b`, and `aerial_gathering`.

A source animation with no hitpoint marker explicitly has `hitpoints="false"`.
Its zero min/max values mean no hit event; its positive animation length comes
from the client. A `cast_animation="true"` record stores the cast clip duration
and is never added as another delay after the cast. These are actual timing
records, not ignored names or invented hit times.

NPC records bind the client marker file's mesh to the actual NPC IDs in
`Data/Npcs/Npcs.pak`. Runtime lookup uses that NPC or transformation model ID,
so different monsters cannot accidentally share one duration just because both
use `poweratk2`. Recovered fire hitpoints now schedule NPC skill effects and honor
motion speed, attack-speed rate, motion delay, and projectile travel. Cast-only,
no-hit, explicitly instant, and unmapped actor cases add no invented delay.
Existing player race/weapon lookup remains available for player skills.

The validator now respects the existing `instant_skill` flag. Zadra's
`transform` skill (19694) is explicitly instant in both the client and server;
requiring hit-marker timings for it was a validator error. No skill templates
were changed or removed.

Four genuine gaps remain visible as WARN:

- `normalfiremo` (16959), `pointfirerh` (16960), and `areafireod` (16975): old giant
  test skills in `client_skills.xml`; no matching marker, CAF file, or CAL binding
  exists in the installed 4.8 client. The giant CAL files were checked in
  `Objects/monster/Mesh_Meshes_004.pak`. No substitute animation was guessed.
- `open` (20766): an old test skill. The only matching marker is the unrelated
  `Test_Under_03_Energy.xml/nopen_001`, with no corresponding client NPC mesh
  mapping. It cannot supply a trustworthy duration for this skill's actor.

These four require the original test-actor asset definitions or a separately
agreed change to those test skills. They are not resolved by this repair.

The archive also contains four malformed marker documents, listed in the
manifest. None supplies the recovered rows; each recovered row was read from a
successfully parsed document. These client errors remain recorded.

To verify the source rows against the installed client without writing files:

```powershell
python tools/motion-times/recover.py --client '<Aion 4.8 NA client directory>'
```

Add `--write` to regenerate the recovered source rows and provenance manifest.
The tool only reads the client. Deploy changed classes, XML and XSD together;
restart the game server to load them. Targeted Java compilation and regression
tests cover data loading, the retained warnings, actor-specific lookup, and
actual NPC hit-time scheduling. Live startup and encounter tests are still
required, particularly for Tiamat, giant actors, and transformed-player skills.
