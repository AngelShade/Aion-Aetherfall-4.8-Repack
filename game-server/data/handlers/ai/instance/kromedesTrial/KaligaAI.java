package ai.instance.kromedesTrial;

import ai.AggressiveNpcAI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import com.aionemu.gameserver.ai.AIName;
import com.aionemu.gameserver.ai.AIState;
import com.aionemu.gameserver.ai.AttackIntention;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.templates.spawns.SpawnTemplate;
import com.aionemu.gameserver.utils.PositionUtil;
import com.aionemu.gameserver.skillengine.model.SkillTemplate;
import com.aionemu.gameserver.skillengine.model.Effect;

/** Keeps relic-dependent effects tied to actual living relics, including interrupted casts. */
@AIName("kaliga_unjust")
public class KaligaAI extends AggressiveNpcAI {
	private int nextGolemWave;
	private final List<Npc> golems = new ArrayList<>();
	private final List<SpawnTemplate> dormantStatues = new ArrayList<>();
	public KaligaAI(Npc owner) { super(owner); }

	@Override
	protected void handleAttack(Creature creature) {
		super.handleAttack(creature);
		if (!isDead() && isInState(AIState.FIGHT))
			checkGolemWaves(getLifeStats().getHpPercentage());
	}

	@Override
	public AttackIntention chooseAttackIntention() {
		if (!isDead() && isInState(AIState.FIGHT))
			checkGolemWaves(getLifeStats().getHpPercentage());
		return super.chooseAttackIntention();
	}

	protected synchronized void checkGolemWaves(int hp) {
		while (nextGolemWave < 3 && hp <= 75 - nextGolemWave * 25) {
			nextGolemWave++;
			spawnGolemWave();
		}
	}

	protected void spawnGolemWave() {
		// Activate native dormant models at their supported authored positions.
		List<Npc> statues = getOwner().getWorldMapInstance().getNpcs(282126).stream().filter(npc -> !npc.isDead())
			.sorted(Comparator.comparingDouble(npc -> PositionUtil.getDistance(getOwner(), npc))).limit(2).toList();
		for (Npc statue : statues) {
			SpawnTemplate original = statue.getSpawn();
			dormantStatues.add(original);
			statue.getController().delete();
			Npc golem = (Npc) spawn(282124, original.getX(), original.getY(), original.getZ(), original.getHeading());
			if (golem != null)
				golems.add(golem);
		}
	}

	private synchronized void resetGolemWaves(boolean restoreStatues) {
		for (Npc golem : golems)
			if (golem.isSpawned()) golem.getController().delete();
		golems.clear();
		if (restoreStatues)
			for (SpawnTemplate statue : dormantStatues)
				spawn(282126, statue.getX(), statue.getY(), statue.getZ(), statue.getHeading(), statue.getStaticId());
		dormantStatues.clear();
		nextGolemWave = 0;
	}

	@Override
	protected void handleNotAtHome() {
		resetGolemWaves(true);
		super.handleNotAtHome();
	}

	@Override
	protected void handleDied() {
		resetGolemWaves(true);
		super.handleDied();
	}

	@Override
	protected void handleDespawned() {
		resetGolemWaves(false);
		super.handleDespawned();
	}

	@Override
	public void onEndUseSkill(SkillTemplate skillTemplate, int skillLevel) {
		removeUnsupportedRelicEffects();
		super.onEndUseSkill(skillTemplate, skillLevel);
	}

	@Override
	public void onEffectApplied(Effect effect) {
		if (effect.getSkillId() == 19247 || effect.getSkillId() == 19248)
			removeUnsupportedRelicEffects();
		super.onEffectApplied(effect);
	}

	@Override
	protected void handleBackHome() {
		getOwner().getEffectController().removeEffect(19247);
		getOwner().getEffectController().removeEffect(19248);
		getOwner().removeSkillCoolDown(19246);
		getOwner().removeSkillCoolDown(19247);
		getOwner().removeSkillCoolDown(19248);
		super.handleBackHome();
	}

	private void removeUnsupportedRelicEffects() {
		if (!hasLivingRelic(282095))
			removeRelicEffect(19247);
		if (!hasLivingRelic(282093))
			removeRelicEffect(19248);
	}

	protected boolean hasLivingRelic(int npcId) {
		return !isDead() && isInState(AIState.FIGHT)
			&& getOwner().getWorldMapInstance().getNpcs(npcId).stream().anyMatch(npc -> !npc.isDead());
	}

	protected void removeRelicEffect(int skillId) {
		getOwner().getEffectController().removeEffect(skillId);
	}
}
