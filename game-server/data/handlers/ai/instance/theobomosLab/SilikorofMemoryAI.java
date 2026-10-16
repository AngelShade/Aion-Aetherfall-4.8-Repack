package ai.instance.theobomosLab;

import com.aionemu.commons.utils.Rnd;
import com.aionemu.gameserver.ai.AIName;
import com.aionemu.gameserver.ai.HpPhases;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.skillengine.SkillEngine;
import com.aionemu.gameserver.world.WorldPosition;

import ai.AggressiveNpcAI;
import com.aionemu.gameserver.instance.handlers.TheobomosLabHandler;

/**
 * @author Ritsu
 */
@AIName("silikor")
public class SilikorofMemoryAI extends AggressiveNpcAI implements HpPhases.PhaseHandler {

	private final HpPhases hpPhases = new HpPhases(50, 25, 10);

	public SilikorofMemoryAI(Npc owner) {
		super(owner);
	}

	@Override
	protected void handleAttack(Creature creature) {
		super.handleAttack(creature);
		hpPhases.tryEnterNextPhase(this);
	}

	@Override
	public void handleHpPhase(int phaseHpPercent) {
		sp(281054);
		sp(281053);
	}

	private void sp(int npcId) {
		double angleRadians = Math.toRadians(Rnd.nextFloat(360f));
		int distance = Rnd.get(0, 2);
		float x1 = (float) (Math.cos(angleRadians) * distance);
		float y1 = (float) (Math.sin(angleRadians) * distance);
		WorldPosition p = getPosition();
		spawn(npcId, p.getX() + x1, p.getY() + y1, p.getZ(), p.getHeading());
	}

	@Override
	protected void handleSpawned() {
		super.handleSpawned();
		restoreBarrier();
	}

	@Override
	protected void handleBackHome() {
		clearSummons();
		super.handleBackHome();
		hpPhases.reset();
		// Native return-home removes BUFF-slot effects; retreat must not bypass the seal puzzle.
		restoreBarrier();
	}

	@Override
	protected void handleDied() {
		clearSummons();
		super.handleDied();
	}

	@Override
	protected void handleDespawned() {
		clearSummons();
		super.handleDespawned();
	}

	private void clearSummons() {
		getPosition().getWorldMapInstance().getNpcs(281054, 281053).forEach(npc -> npc.getController().deleteIfAliveOrCancelRespawn());
	}

	private void restoreBarrier() {
		if (getNpcId() != 214668)
			return;
		if (getPosition().getWorldMapInstance().getInstanceHandler() instanceof TheobomosLabHandler lab && !lab.isSilikorSealed())
			return;
		SkillEngine.getInstance().getSkill(getOwner(), 18481, 1, getOwner()).useSkill();
	}

}
