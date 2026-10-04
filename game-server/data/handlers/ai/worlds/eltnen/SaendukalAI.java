package ai.worlds.eltnen;

import com.aionemu.gameserver.ai.AIName;
import com.aionemu.gameserver.ai.HpPhases;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.templates.npcskill.NpcSkillTargetAttribute;

import ai.AggressiveNpcAI;

/**
 * Encounter AI for Grand Chieftain Saendukal.
 * Casts phase skills at HP thresholds:
 * ~70% -> Wrath Explosion (16861)
 * ~50% -> Pulverizing Assault (16860)
 * ~25% -> Strong Protection (16415)
 */
@AIName("saendukal")
public class SaendukalAI extends AggressiveNpcAI implements HpPhases.PhaseHandler {

	private final HpPhases hpPhases = new HpPhases(70, 50, 25);

	public SaendukalAI(Npc owner) {
		super(owner);
	}

	@Override
	protected void handleAttack(Creature creature) {
		super.handleAttack(creature);
		hpPhases.tryEnterNextPhase(this);
	}

	@Override
	public void handleHpPhase(int phaseHpPercent) {
		switch (phaseHpPercent) {
			case 70 -> getOwner().queueSkill(16861, 31, 0);
			case 50 -> getOwner().queueSkill(16860, 31, 0);
			case 25 -> getOwner().queueSkill(16415, 31, 0, NpcSkillTargetAttribute.ME);
		}
	}

	@Override
	protected void handleBackHome() {
		super.handleBackHome();
		hpPhases.reset();
	}

	@Override
	protected void handleDied() {
		super.handleDied();
		hpPhases.reset();
	}
}
