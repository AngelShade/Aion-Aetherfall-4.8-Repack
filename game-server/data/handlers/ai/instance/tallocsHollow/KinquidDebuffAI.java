package ai.instance.tallocsHollow;

import java.util.concurrent.Future;

import com.aionemu.gameserver.ai.AIName;
import com.aionemu.gameserver.ai.AIState;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.skillengine.SkillEngine;
import com.aionemu.gameserver.utils.ThreadPoolManager;

import ai.AggressiveNpcAI;

/**
 * @author xTz
 */
@AIName("kinquid_debuff")
public class KinquidDebuffAI extends AggressiveNpcAI {

	private Future<?> dispelTask;

	public KinquidDebuffAI(Npc owner) {
		super(owner);
	}

	@Override
	protected synchronized void handleSpawned() {
		super.handleSpawned();
		// Check a stationary boss too, including barriers applied after this field spawned.
		dispelTask = ThreadPoolManager.getInstance().scheduleAtFixedRate(this::dispelBarrier, 0, 1000);
	}

	@Override
	protected void handleCreatureMoved(Creature creature) {
		super.handleCreatureMoved(creature);
		if (creature instanceof Npc npc && npc.getNpcId() == 215467)
			dispelBarrier();
	}

	private synchronized void dispelBarrier() {
		if (dispelTask == null || isDead() || !getOwner().isSpawned())
			return;
		Npc boss = getPosition().getWorldMapInstance().getNpc(215467);
		int skillId = getNpcId() == 282008 ? 19235 : 19236;
		int barrierId = getNpcId() == 282008 ? 19234 : 19233;
		if (boss != null && !boss.isDead() && boss.getAi().getState() == AIState.FIGHT
			&& boss.getObjectId() == getOwner().getSpawn().getCreatorId()
			&& boss.getEffectController().hasAbnormalEffect(barrierId)
			&& isInRange(boss, DataManager.SKILL_DATA.getSkillTemplate(skillId).getProperties().getEffectiveRange()))
			SkillEngine.getInstance().getSkill(getOwner(), skillId, 46, getOwner()).useNoAnimationSkill();
	}

	private void cancelDispelTask() {
		if (dispelTask != null) {
			dispelTask.cancel(false);
			dispelTask = null;
		}
	}

	@Override
	protected synchronized void handleDied() {
		cancelDispelTask();
		super.handleDied();
	}

	@Override
	protected synchronized void handleDespawned() {
		cancelDispelTask();
		super.handleDespawned();
	}

}
