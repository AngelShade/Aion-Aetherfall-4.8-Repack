package ai.instance.fireTemple;

import java.util.concurrent.Future;
import com.aionemu.gameserver.ai.AIActions;
import com.aionemu.gameserver.ai.AIName;
import com.aionemu.gameserver.ai.NpcAI;
import com.aionemu.gameserver.ai.poll.AIQuestion;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.skillengine.SkillEngine;
import com.aionemu.gameserver.skillengine.model.Skill;
import com.aionemu.gameserver.skillengine.model.SkillTemplate;
import com.aionemu.gameserver.utils.ThreadPoolManager;

/** Destructible objects cast their native AoE at their own position. */
@AIName("kromede_trap")
public class KromedeTrapAI extends NpcAI {
	private Future<?> task;
	private int generation;
	private Skill explosion;
	public KromedeTrapAI(Npc owner) { super(owner); }
	@Override
	protected synchronized void handleSpawned() {
		super.handleSpawned();
		int epoch = ++generation;
		task = ThreadPoolManager.getInstance().schedule(() -> detonate(epoch), 5500);
	}
	private synchronized void detonate(int epoch) {
		if (epoch != generation || isDead() || !getOwner().isSpawned()) return;
		explosion = SkillEngine.getInstance().getSkill(getOwner(), 17050, 28, getOwner());
		if (explosion == null || !explosion.useSkill()) {
			AIActions.deleteOwner(this);
		} else {
			// Also clean up if this cast is interrupted and has no completion callback.
			task = ThreadPoolManager.getInstance().schedule(() -> delete(epoch),
				explosion.getCastDuration() + explosion.getHitTime() + 1500);
		}
	}
	@Override
	public synchronized void onEndUseSkill(SkillTemplate template, int level) {
		super.onEndUseSkill(template, level);
		if (template.getSkillId() != 17050) return;
		int epoch = generation;
		if (task != null) task.cancel(false);
		int delay = explosion == null ? 1000 : Math.max(1000, explosion.getHitTime() + 500);
		task = ThreadPoolManager.getInstance().schedule(() -> delete(epoch), delay);
	}
	private synchronized void delete(int epoch) {
		if (epoch == generation && getOwner().isSpawned()) AIActions.deleteOwner(this);
	}
	private void cancel() {
		generation++;
		if (task != null) task.cancel(false);
		getOwner().getController().abortCast();
		explosion = null;
	}
	@Override
	protected synchronized void handleDied() {
		cancel(); super.handleDied();
		int epoch = generation;
		task = ThreadPoolManager.getInstance().schedule(() -> delete(epoch), 1000);
	}
	@Override
	protected synchronized void handleDespawned() { cancel(); super.handleDespawned(); }
	@Override
	public boolean isMoveSupported() { return false; }
	@Override
	public boolean ask(AIQuestion question) {
		return switch (question) {
			case ALLOW_DECAY, ALLOW_RESPAWN, REWARD_AP_XP_DP_LOOT -> false;
			default -> super.ask(question);
		};
	}
}
