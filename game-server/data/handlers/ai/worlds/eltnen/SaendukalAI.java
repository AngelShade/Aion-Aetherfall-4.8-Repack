package ai.worlds.eltnen;

import java.util.concurrent.Future;

import com.aionemu.commons.utils.Rnd;
import com.aionemu.gameserver.ai.AIName;
import com.aionemu.gameserver.ai.AIState;
import com.aionemu.gameserver.ai.HpPhases;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.VisibleObject;
import com.aionemu.gameserver.model.templates.npcskill.NpcSkillTargetAttribute;
import com.aionemu.gameserver.skillengine.model.SkillTemplate;
import com.aionemu.gameserver.utils.ThreadPoolManager;

import ai.AggressiveNpcAI;

/**
 * Encounter AI for Grand Chieftain Saendukal (대족장 샨두카).
 * Legendary World Boss of Kaidan Headquarters (Eltnen, Map 210020000, NPC 211040 / Empyrean Crucible NPC 280338).
 *
 * Mechanics & Phases:
 * - Skill Level: 31 across all boss skills.
 * - Dialogue / Shout synchronization (client_ai="ND2_RnI" / string_ids 341090-341097):
 *     skill_no 4 -> Deadly Chain (16855) / Darkness Snare (17845): "The shackles of Darkness!"
 *     skill_no 5 -> Wrath Explosion (16861): CAST_K "Give death", ATTACK_K "to the miserable soul, kraark!"
 *     skill_no 6 -> Wide Crippling Wave (16609) / Head Wound (17855): "Move aside!"
 *     skill_no 7 -> Statue Curse (17856): CAST_K "A cursed body", ATTACK_K "will be rendered frozen."
 *     skill_no 8 -> Pulverizing Assault (16860): "Shatter!"
 *     skill_no 9 -> Strong Protection (16415) / Bellicosity (16873): "His majesty the Dragon Lord is with us, kraark!"
 *
 * - Combat Start:
 *     Casts Bellicosity (16873) self-buff on first engagement.
 *
 * - Anti-Kite / Pull Mechanism:
 *     If the target exceeds 10m range (and within 37m) while in combat, Saendukal pulls them back with
 *     Deadly Chain (16855) (15s internal cooldown).
 *
 * - Phase Progression (75%, 50%, 25%, 10%):
 *     Phase 1 (~75% HP): Statue Curse (17856, AoE petrify) chained into Pulverizing Assault (16860, "Shatter!").
 *     Phase 2 (~50% HP): Deadly Chain (16855, pull/root) chained into Wrath Explosion (16861, 25m mortal explosion).
 *     Phase 3 (~25% HP): Strong Protection (16415, 90% mitigation shield) -> Statue Curse (17856) -> Wrath Explosion (16861) -> Pulverizing Assault (16860).
 *     Phase 4 (~10% HP Final Stand / Berserk): Re-casts Bellicosity (16873) and initiates a recurring 20s assault
 *     alternating between Wrath Explosion (16861) and Deadly Chain (16855) + Pulverizing Assault (16860).
 *
 * - General Rotation:
 *     Whenever Statue Curse (17856) is cast from the 25% prob skill pool, automatically chains into Pulverizing Assault (16860).
 *
 * - Clean Lifecycle:
 *     Cancels tasks, resets HP phases, clears queued skills, and resets skill numbers on despawn, death, and return home.
 */
@AIName("saendukal")
public class SaendukalAI extends AggressiveNpcAI implements HpPhases.PhaseHandler {

	private static final int SKILL_LEVEL = 31;

	// Skills
	private static final int SKILL_STRONG_PROTECTION = 16415;
	private static final int SKILL_WIDE_CRIPPLING_WAVE = 16609;
	private static final int SKILL_DEADLY_CHAIN = 16855;
	private static final int SKILL_PULVERIZING_ASSAULT = 16860;
	private static final int SKILL_WRATH_EXPLOSION = 16861;
	private static final int SKILL_BELLICOSITY = 16873;
	private static final int SKILL_DARKNESS_SNARE = 17845;
	private static final int SKILL_HEAD_WOUND = 17855;
	private static final int SKILL_STATUE_CURSE = 17856;

	// Phases: 75%, 50%, 25%, 10%
	private final HpPhases hpPhases = new HpPhases(75, 50, 25, 10);

	// Combat & Combo State
	private boolean startedFight = false;
	private long lastPullTime = 0;
	private int comboPhase = 0;
	private int comboStep = 0;
	private Future<?> berserkTask;

	public SaendukalAI(Npc owner) {
		super(owner);
	}

	@Override
	protected void handleAttack(Creature creature) {
		super.handleAttack(creature);
		checkCombatStart();
		hpPhases.tryEnterNextPhase(this);
	}

	private void checkCombatStart() {
		if (!startedFight) {
			startedFight = true;
			getOwner().queueSkill(SKILL_BELLICOSITY, SKILL_LEVEL, 0, NpcSkillTargetAttribute.ME);
		}
	}

	@Override
	public void handleHpPhase(int phaseHpPercent) {
		switch (phaseHpPercent) {
			case 75 -> {
				comboPhase = 75;
				comboStep = 1;
				getOwner().queueSkill(SKILL_STATUE_CURSE, SKILL_LEVEL, 0, NpcSkillTargetAttribute.MOST_HATED);
			}
			case 50 -> {
				comboPhase = 50;
				comboStep = 1;
				getOwner().queueSkill(SKILL_DEADLY_CHAIN, SKILL_LEVEL, 0, NpcSkillTargetAttribute.MOST_HATED);
			}
			case 25 -> {
				comboPhase = 25;
				comboStep = 1;
				getOwner().queueSkill(SKILL_STRONG_PROTECTION, SKILL_LEVEL, 0, NpcSkillTargetAttribute.ME);
			}
			case 10 -> {
				comboPhase = 0;
				comboStep = 0;
				getOwner().queueSkill(SKILL_BELLICOSITY, SKILL_LEVEL, 0, NpcSkillTargetAttribute.ME);
				startBerserkAssault();
			}
		}
	}

	@Override
	public void onStartUseSkill(SkillTemplate skillTemplate, int skillLevel) {
		int skillNo = switch (skillTemplate.getSkillId()) {
			case SKILL_DEADLY_CHAIN, SKILL_DARKNESS_SNARE -> 4;
			case SKILL_WRATH_EXPLOSION -> 5;
			case SKILL_WIDE_CRIPPLING_WAVE, SKILL_HEAD_WOUND -> 6;
			case SKILL_STATUE_CURSE -> 7;
			case SKILL_PULVERIZING_ASSAULT -> 8;
			case SKILL_STRONG_PROTECTION, SKILL_BELLICOSITY -> 9;
			default -> 0;
		};
		if (skillNo > 0) {
			getOwner().setSkillNumber(skillNo);
		}
		super.onStartUseSkill(skillTemplate, skillLevel);
	}

	@Override
	public void onEndUseSkill(SkillTemplate skillTemplate, int skillLevel) {
		super.onEndUseSkill(skillTemplate, skillLevel);
		getOwner().setSkillNumber(0);
		handleComboProgression(skillTemplate.getSkillId());
	}

	private void handleComboProgression(int completedSkillId) {
		if (isDead() || !getOwner().isSpawned() || !isInState(AIState.FIGHT)) {
			return;
		}
		switch (comboPhase) {
			case 75 -> {
				if (completedSkillId == SKILL_STATUE_CURSE) {
					comboPhase = 0;
					comboStep = 0;
					getOwner().queueSkill(SKILL_PULVERIZING_ASSAULT, SKILL_LEVEL, 0, NpcSkillTargetAttribute.MOST_HATED);
				}
			}
			case 50 -> {
				if (completedSkillId == SKILL_DEADLY_CHAIN) {
					comboPhase = 0;
					comboStep = 0;
					getOwner().queueSkill(SKILL_WRATH_EXPLOSION, SKILL_LEVEL, 0, NpcSkillTargetAttribute.MOST_HATED);
				}
			}
			case 25 -> {
				if (completedSkillId == SKILL_STRONG_PROTECTION && comboStep == 1) {
					comboStep = 2;
					getOwner().queueSkill(SKILL_STATUE_CURSE, SKILL_LEVEL, 0, NpcSkillTargetAttribute.MOST_HATED);
				} else if (completedSkillId == SKILL_STATUE_CURSE && comboStep == 2) {
					comboStep = 3;
					getOwner().queueSkill(SKILL_WRATH_EXPLOSION, SKILL_LEVEL, 0, NpcSkillTargetAttribute.MOST_HATED);
				} else if (completedSkillId == SKILL_WRATH_EXPLOSION && comboStep == 3) {
					comboPhase = 0;
					comboStep = 0;
					getOwner().queueSkill(SKILL_PULVERIZING_ASSAULT, SKILL_LEVEL, 0, NpcSkillTargetAttribute.MOST_HATED);
				}
			}
			case 10 -> {
				if (completedSkillId == SKILL_DEADLY_CHAIN) {
					comboPhase = 0;
					comboStep = 0;
					getOwner().queueSkill(SKILL_PULVERIZING_ASSAULT, SKILL_LEVEL, 0, NpcSkillTargetAttribute.MOST_HATED);
				}
			}
			default -> {
				// Non-phase Statue Curse from random prob-25 skill pool: follow up with Pulverizing Assault
				if (completedSkillId == SKILL_STATUE_CURSE) {
					getOwner().queueSkill(SKILL_PULVERIZING_ASSAULT, SKILL_LEVEL, 0, NpcSkillTargetAttribute.MOST_HATED);
				}
			}
		}
	}

	@Override
	protected void handleTargetTooFar() {
		checkAntiKite();
		super.handleTargetTooFar();
	}

	private void checkAntiKite() {
		if (isDead() || !isInState(AIState.FIGHT) || getOwner().isCasting() || comboPhase != 0) {
			return;
		}
		VisibleObject targetObj = getTarget();
		if (targetObj instanceof Creature target && !target.isDead()) {
			if (!isInRange(target, 10) && isInRange(target, 37)) {
				long now = System.currentTimeMillis();
				if (now - lastPullTime > 15000) {
					lastPullTime = now;
					getOwner().queueSkill(SKILL_DEADLY_CHAIN, SKILL_LEVEL, 0, NpcSkillTargetAttribute.MOST_HATED);
				}
			}
		}
	}

	private void startBerserkAssault() {
		cancelBerserkTask();
		berserkTask = ThreadPoolManager.getInstance().scheduleAtFixedRate(this::executeBerserkTick, 20000, 20000);
	}

	private void executeBerserkTick() {
		if (isDead() || !getOwner().isSpawned() || !isInState(AIState.FIGHT)) {
			cancelBerserkTask();
			return;
		}
		if (getOwner().isCasting() || comboPhase != 0) {
			return;
		}
		if (Rnd.nextBoolean()) {
			getOwner().queueSkill(SKILL_WRATH_EXPLOSION, SKILL_LEVEL, 0, NpcSkillTargetAttribute.MOST_HATED);
		} else {
			comboPhase = 10;
			getOwner().queueSkill(SKILL_DEADLY_CHAIN, SKILL_LEVEL, 0, NpcSkillTargetAttribute.MOST_HATED);
		}
	}

	private void cancelBerserkTask() {
		if (berserkTask != null && !berserkTask.isDone()) {
			berserkTask.cancel(true);
			berserkTask = null;
		}
	}

	private void cleanupEncounter() {
		cancelBerserkTask();
		hpPhases.reset();
		comboPhase = 0;
		comboStep = 0;
		startedFight = false;
		lastPullTime = 0;
		getOwner().clearQueuedSkills();
		getOwner().setSkillNumber(0);
	}

	@Override
	protected void handleSpawned() {
		cleanupEncounter();
		super.handleSpawned();
	}

	@Override
	protected void handleBackHome() {
		cleanupEncounter();
		super.handleBackHome();
	}

	@Override
	protected void handleDied() {
		cleanupEncounter();
		super.handleDied();
	}

	@Override
	protected void handleDespawned() {
		cleanupEncounter();
		super.handleDespawned();
	}
}
