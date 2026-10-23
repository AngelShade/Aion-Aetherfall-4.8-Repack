package ai.instance.beshmundirTemple;

import java.util.concurrent.Future;

import com.aionemu.gameserver.ai.AIName;
import com.aionemu.gameserver.ai.AIState;
import com.aionemu.gameserver.controllers.attack.AggroTarget;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.utils.ThreadPoolManager;

/** BeyondAionSharp 3d880c2b: first curse at 5s, then every 12s, reporting only to the creating boss. */
@AIName("macunbello_soul_reaper")
public class MacunbelloSoulReaperAI extends MacunbelloCombatAI {

	private final Object curseLock = new Object();
	private Future<?> curseTask;
	private int generation;
	private boolean cursedOnce;

	public MacunbelloSoulReaperAI(Npc owner) {
		super(owner);
	}

	@Override
	protected void handleAttack(Creature creature) {
		super.handleAttack(creature);
		if (!enabled || isDead() || !isInState(AIState.FIGHT))
			return;
		synchronized (curseLock) {
			if (isDead() || !getOwner().isSpawned() || !isInState(AIState.FIGHT))
				return;
			if (curseTask == null) {
				int fight = ++generation;
				curseTask = ThreadPoolManager.getInstance().scheduleAtFixedRate(() -> curseTick(fight), 5000, 12000);
			}
		}
	}

	private void curseTick(int fight) {
		synchronized (curseLock) {
			if (fight != generation || isDead() || !isInState(AIState.FIGHT))
				return;
			Creature victim = getAggroList().getTarget(AggroTarget.RANDOM);
			if (!validTarget(victim))
				return;
			getAggroList().addHate(victim, 10000); // Donor's implemented flat hate; its unused 10% constant is not applied.
			queue(19050, victim, () -> reportCurse(fight, victim));
		}
	}

	private void reportCurse(int fight, Creature victim) {
		float range;
		synchronized (curseLock) {
			if (fight != generation || isDead() || !isInState(AIState.FIGHT))
				return;
			range = cursedOnce ? 40 : 50;
			cursedOnce = true;
		}
		if (getCreator() instanceof Npc boss && boss.getAi() instanceof MacunbelloAI ai)
			ai.onReaperCurse(getOwner(), victim, range);
	}

	private void stopCurse() {
		synchronized (curseLock) {
			generation++;
			if (curseTask != null)
				curseTask.cancel(false);
			curseTask = null;
			cursedOnce = false;
			getOwner().clearQueuedSkills();
		}
	}

	@Override
	protected void handleNotAtHome() {
		if (enabled)
			stopCurse();
		super.handleNotAtHome();
	}

	@Override
	protected void handleBackHome() {
		if (enabled)
			stopCurse();
		super.handleBackHome();
		if (enabled)
			getOwner().getController().delete();
	}

	@Override
	protected void handleDied() {
		if (enabled)
			stopCurse();
		super.handleDied();
	}

	@Override
	protected void handleDespawned() {
		if (enabled)
			stopCurse();
		super.handleDespawned();
	}
}
