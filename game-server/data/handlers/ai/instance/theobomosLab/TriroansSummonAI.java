package ai.instance.theobomosLab;

import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import com.aionemu.gameserver.ai.AIName;
import com.aionemu.gameserver.ai.NpcAI;
import com.aionemu.gameserver.ai.manager.WalkManager;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.templates.walker.RouteStep;
import com.aionemu.gameserver.skillengine.SkillEngine;
import com.aionemu.gameserver.skillengine.model.Skill;
import com.aionemu.gameserver.skillengine.model.SkillTemplate;
import com.aionemu.gameserver.utils.ThreadPoolManager;

/** The arriving spirit activates its own generator; native skill properties select targets. */
@AIName("triroan_summon")
public class TriroansSummonAI extends NpcAI {

	private final AtomicBoolean activated = new AtomicBoolean();
	private Future<?> despawnTask;
	private int generatorSkill;

	public TriroansSummonAI(Npc owner) {
		super(owner);
	}

	@Override
	public boolean canThink() {
		return false;
	}

	@Override
	protected void handleSpawned() {
		activated.set(false);
		generatorSkill = switch (getNpcId()) {
			case 280975 -> 18486; // Curse of Flame
			case 280976 -> 18483; // Scream of Water
			case 280977 -> 18485; // Wall of Earth
			case 280978 -> 18484; // Wind Blade of Rage
			default -> throw new IllegalStateException("Unknown Triroan spirit: " + getNpcId());
		};
		super.handleSpawned();
	}

	@Override
	protected void handleMoveArrived() {
		// Inspect the reached step before a generic walking callback advances the route.
		RouteStep step = getOwner().getMoveController().getCurrentStep();
		if (step == null || !step.isLastStep()) {
			super.handleMoveArrived();
			WalkManager.targetReached(this);
			return;
		}
		if (!activated.compareAndSet(false, true))
			return;
		WalkManager.stopWalking(this);
		Npc boss = getPosition().getWorldMapInstance().getNpc(214669);
		if (boss == null || boss.isDead()) {
			getOwner().getController().delete();
			return;
		}
		Skill skill = SkillEngine.getInstance().getSkill(getOwner(), generatorSkill, 41, getOwner());
		boolean started = skill.useSkill();
		// Retain the visual through the native cast; also expire an interrupted/failed cast.
		scheduleDespawn(started ? skill.getCastDuration() + 3000 : 3000);
	}

	@Override
	public void onEndUseSkill(SkillTemplate skillTemplate, int skillLevel) {
		if (skillTemplate.getSkillId() == generatorSkill)
			scheduleDespawn(3000);
	}

	private synchronized void scheduleDespawn(int delay) {
		if (despawnTask != null)
			despawnTask.cancel(false);
		if (getOwner().isSpawned() && !isDead())
			despawnTask = ThreadPoolManager.getInstance().schedule(() -> getOwner().getController().delete(), delay);
	}

	@Override
	protected synchronized void handleDespawned() {
		if (despawnTask != null)
			despawnTask.cancel(false);
		super.handleDespawned();
	}

	@Override
	protected void handleDied() {
		getOwner().getController().delete();
		super.handleDied();
	}
}
