# Grand Chieftain Saendukal Encounter AI Implementation

Implemented on 4 October 2026 for Grand Chieftain Saendukal (대족장 샨두카), Legendary World Boss of Kaidan Headquarters (Eltnen, Map ID `210020000`, NPC ID `211040`; and Empyrean Crucible NPC ID `280338`).

## 1. Overview & Mechanics

Saendukal is a Level 40 Legendary World Boss with 2,689,332 HP (`hpgauge 26`). His AI implementation (`@AIName("saendukal")`) resides in:
- `game-server/data/handlers/ai/worlds/eltnen/SaendukalAI.java`
- `target-deploy/game-server/data/handlers/ai/worlds/eltnen/SaendukalAI.java`

### Skill Inventory (Level 31)

| Skill ID | Name | Type / Prob | Description | Shout Mapping |
|---|---|---|---|---|
| `16415` | Strong Protection (강력한 보호막) | Buff / 0 | 10-hit count 90% mitigation shield on self | `9` (ATTACK_K `341097`) |
| `16609` | Wide Crippling Wave (광역 발목 강타) | AoE / 25 | 2.5s cast, 5m AoE physical attack + snare | `6` (ATTACK_K `341093`) |
| `16855` | Deadly Chain (죽음의 사슬) | Area Pull / 25 | 16m target / 25m AoE pull + 30s snare/root debuff | `4` (ATTACK_K `341090`) |
| `16860` | Pulverizing Assault (분쇄 강타) | Melee Smash / 0 | 2.5s cast, 7m frontal area high-damage attack | `8` (ATTACK_K `341096`) |
| `16861` | Wrath Explosion (분노의 폭발) | Mortal Blast / 0 | 4.0s cast, 25m mortal AoE dealing devastating damage | `5` (CAST_K `341091`, ATTACK_K `341092`) |
| `16873` | Bellicosity (호전성) | Self-Buff / 25 | Roar boosting attack power and attack speed | `9` (ATTACK_K `341097`) |
| `17845` | Darkness Snare (어둠의 덫) | Area Pull / 25 | 37m target / 10m AoE pull | `4` (ATTACK_K `341090`) |
| `17855` | Head Wound (두부 강타) | Melee Debuff / 25 | 16m target / 7m AoE silence/debuff | `6` (ATTACK_K `341093`) |
| `17856` | Statue Curse (석상 저주) | Petrify AoE / 25 | 3.5s cast, 20m AoE turning players into stone for 15s | `7` (CAST_K `341094`, ATTACK_K `341095`) |

---

## 2. Encounter Flow & Phase Architecture

### Combat Initiation
- On first engagement (`handleAttack`), Saendukal roars and applies **Bellicosity (16873)** to buff his attack power and speed.
- `hpPhases` tracking starts for 75%, 50%, 25%, 10% thresholds.

### Anti-Kite / Pull Mechanism
- If the current target moves beyond melee range (`> 10m` and `<= 37m`), Saendukal triggers an anti-kite check (`handleTargetTooFar`).
- Every 15 seconds (internal cooldown), Saendukal uses **Deadly Chain (16855)** to yank the distant target directly to his feet and roots them, shouting *"The shackles of Darkness!"*.

### Phase Progression (`HpPhases`: 75, 50, 25, 10)
1. **Phase 1 (~75% HP)**:
   - Queues **Statue Curse (17856)** (3.5s cast AoE petrify, Shout 7).
   - Upon completion (`onEndUseSkill`), immediately chains into **Pulverizing Assault (16860)** (*"Shatter!"*, Shout 8).
2. **Phase 2 (~50% HP)**:
   - Queues **Deadly Chain (16855)** (instant pull + root, Shout 4).
   - Upon completion, immediately channels **Wrath Explosion (16861)** (4.0s cast mortal explosion, Shout 5).
3. **Phase 3 (~25% HP)**:
   - Queues **Strong Protection (16415)** (90% damage mitigation shield, Shout 9).
   - Upon completion, executes the full boss combo sequence:
     1. **Statue Curse (17856)**
     2. **Wrath Explosion (16861)**
     3. **Pulverizing Assault (16860)**
4. **Phase 4 (~10% HP Final Stand / Berserk)**:
   - Re-casts **Bellicosity (16873)**.
   - Starts a recurring 20-second assault task alternating between mortal **Wrath Explosion (16861)** and **Deadly Chain (16855) + Pulverizing Assault (16860)**.

### General Rotation Chaining
- When **Statue Curse (17856)** is randomly selected during normal combat outside phases (from its 25% prob pool), Saendukal automatically follows up with **Pulverizing Assault (16860)** on the frozen players.

### Dialogue & Shout Synchronization
- Skill IDs are mapped in `onStartUseSkill` to authentic retail `skill_no` values (`4`, `5`, `6`, `7`, `8`, `9`).
- `getOwner().setSkillNumber(skillNo)` synchronizes `ShoutEventHandler.onCast` (CAST_K) and `PlayerController.onAttack` (ATTACK_K).
- `onEndUseSkill` resets `skillNumber` to 0, ensuring normal melee attacks do not trigger skill shouts.

### Clean Lifecycle
- On `handleBackHome()`, `handleDied()`, or `handleDespawned()`, `cleanupEncounter()` cancels the berserk task, resets `HpPhases`, clears queued skills, and resets skill numbers.

---

## 3. Verification & Live Status

- **Compilation**: Clean compilation with Java 25 against server classpath.
- **Dynamic Live Reload**: Executed via `SaendukalActivationAgent46` on running GameServer (PID 24908).
- **Atomic Registry**: `AIEngine.reload()` compiled and registered `ai.worlds.eltnen.SaendukalAI` into the active AI registry (460 handlers active).
- **Static & Runtime Assertions (100% Passed)**:
  - Both NPC templates `211040` and `280338` confirmed mapped to `saendukal`.
  - Isolated fixture boss spawned in private test instance; confirmed instantiated as `ai.worlds.eltnen.SaendukalAI`.
  - All 9 skill template shout mappings validated for `CAST_K` and `ATTACK_K`.
  - All 4 phase combos and normal rotation follow-ups verified.
  - Encounter cleanup confirmed resetting queued skills and tasks.
  - Human player sessions and PlayerBot companions verified intact and unaltered.
- **Receipt**: `target/saendukal-verification-report.txt`.
