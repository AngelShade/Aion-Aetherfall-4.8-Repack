package ai.instance.tallocsHollow;

import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import com.aionemu.commons.utils.Rnd;
import com.aionemu.gameserver.ai.AIName;
import com.aionemu.gameserver.ai.AIState;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.templates.npcskill.NpcSkillTargetAttribute;
import com.aionemu.gameserver.utils.ThreadPoolManager;

import ai.AggressiveNpcAI;

/**
 * @author xTz, Sykra
 */
@AIName("kinquid")
public class KinquidAI extends AggressiveNpcAI {

	private final AtomicBoolean isHome = new AtomicBoolean(true);
	private Future<?> skillTask;
	private Future<?> destroyerRespawnTask;
	private Future<?> magicalBarrierTask;
	private int encounter;

	public KinquidAI(Npc owner) {
		super(owner);
	}

	@Override
	protected synchronized void handleAttack(Creature creature) {
		super.handleAttack(creature);
		if (isInState(AIState.FIGHT) && isHome.compareAndSet(true, false)) {
			int pull = ++encounter;
			getPosition().getWorldMapInstance().setDoorState(48, false);
			cancelSkillTask();
			skillTask = ThreadPoolManager.getInstance().scheduleAtFixedRate(() -> runSkillTask(pull), 35000, 35000);
			cancelDestroyerRespawnTask();
			destroyerRespawnTask = ThreadPoolManager.getInstance().scheduleAtFixedRate(() -> respawnDestroyer(pull), 0, 25000);
		}
	}

	@Override
	protected synchronized void handleBackHome() {
		cleanUp();
		getPosition().getWorldMapInstance().setDoorState(48, true);
		super.handleBackHome();
	}

	@Override
	protected synchronized void handleDespawned() {
		cleanUp();
		super.handleDespawned();
	}

	@Override
	protected synchronized void handleDied() {
		cleanUp();
		super.handleDied();
	}

	private void cancelSkillTask() {
		if (skillTask != null && !skillTask.isDone()) {
			skillTask.cancel(false);
			skillTask = null;
		}
	}

	private void cancelDestroyerRespawnTask() {
		if (destroyerRespawnTask != null && !destroyerRespawnTask.isDone()) {
			destroyerRespawnTask.cancel(false);
			destroyerRespawnTask = null;
		}
	}

	private synchronized void cleanUp() {
		encounter++;
		isHome.set(true);
		cancelSkillTask();
		cancelDestroyerRespawnTask();
		if (magicalBarrierTask != null) {
			magicalBarrierTask.cancel(false);
			magicalBarrierTask = null;
		}
		getOwner().clearQueuedSkills();
		getEffectController().removeEffect(19233);
		getEffectController().removeEffect(19234);
		despawnDestroyer();
	}

	private boolean isCurrentPull(int pull) {
		return pull == encounter && !isHome.get() && !isDead() && getOwner().isSpawned() && isInState(AIState.FIGHT);
	}

	private synchronized void runSkillTask(int pull) {
		if (!isCurrentPull(pull))
			return;
		queueBarrier(19233);
		magicalBarrierTask = ThreadPoolManager.getInstance().schedule(() -> queueMagicalBarrier(pull), 3500);
	}

	private synchronized void queueMagicalBarrier(int pull) {
		if (isCurrentPull(pull))
			queueBarrier(19234);
	}

	private void queueBarrier(int skillId) {
		if (!getOwner().hasQueuedSkill(skill -> skill.getSkillId() == skillId))
			getOwner().queueSkill(skillId, 60, 0, NpcSkillTargetAttribute.ME);
	}

	private void despawnDestroyer() {
		for (int npcId : new int[] { 282008, 282009 })
			getPosition().getWorldMapInstance().getNpcs(npcId).stream()
				.filter(npc -> npc.getSpawn().getCreatorId() == getObjectId()).forEach(npc -> npc.getController().delete());
	}

	private synchronized void respawnDestroyer(int pull) {
		if (!isCurrentPull(pull))
			return;
		despawnDestroyer();
		int spawnId = Rnd.nextBoolean() ? 282008 : 282009;
		switch (Rnd.get(1, 3)) {
			case 1 -> spawn(spawnId, 266.70685f, 680.6733f, 1167.2369f, (byte) 0);
			case 2 -> spawn(spawnId, 292.02466f, 719.7132f, 1169.3982f, (byte) 0);
			case 3 -> spawn(spawnId, 263.4334f, 716.73004f, 1170.3693f, (byte) 0);
		}
	}

}
