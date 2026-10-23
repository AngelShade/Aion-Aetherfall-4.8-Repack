package ai.instance.tallocsHollow;

import java.util.concurrent.Future;

import com.aionemu.gameserver.ai.AIName;
import com.aionemu.gameserver.ai.AIState;
import com.aionemu.gameserver.ai.poll.AIQuestion;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.spawnengine.SpawnEngine;
import com.aionemu.gameserver.utils.ThreadPoolManager;

import ai.AggressiveNpcAI;

/**
 * @author xTz, Sykra
 */
@AIName("mosquaegg")
public class MosquaEggAI extends AggressiveNpcAI {

	private Future<?> supraklawSpawnTask;

	public MosquaEggAI(Npc owner) {
		super(owner);
	}

	@Override
	protected synchronized void handleSpawned() {
		super.handleSpawned();
		supraklawSpawnTask = ThreadPoolManager.getInstance().schedule(this::hatch, 17000);
	}

	private synchronized void hatch() {
		if (isDead() || !getOwner().isSpawned() || supraklawSpawnTask == null)
			return;
		if (getOwner().getCreator() instanceof Npc queen && queen.getNpcId() == 215480
			&& queen.isSpawned() && !queen.isDead()
			&& (queen.getAi().getState() == AIState.FIGHT || queen.getAi().getState() == AIState.FEAR
				|| queen.getAi().getState() == AIState.CONFUSE)) {
			// Keep the hatchling under the queen's ownership after its egg is deleted.
			SpawnEngine.spawnObject(SpawnEngine.newSingleTimeSpawn(getOwner().getWorldId(), 217132,
				getOwner().getX(), getOwner().getY(), getOwner().getZ(), getOwner().getHeading(), queen.getObjectId()),
				getOwner().getInstanceId());
		}
		getOwner().getController().delete();
	}

	@Override
	protected synchronized void handleDespawned() {
		cancelSpawnTask();
		super.handleDespawned();
	}

	@Override
	protected synchronized void handleDied() {
		cancelSpawnTask();
		super.handleDied();
	}

	@Override
	public boolean ask(AIQuestion question) {
		return switch (question) {
			case ALLOW_DECAY, REWARD_AP_XP_DP_LOOT, REWARD_LOOT -> false;
			default -> super.ask(question);
		};
	}

	private void cancelSpawnTask() {
		if (supraklawSpawnTask != null && !supraklawSpawnTask.isDone()) {
			supraklawSpawnTask.cancel(false);
			supraklawSpawnTask = null;
		}
	}

}
