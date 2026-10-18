package ai.worlds.theobomos;

import ai.AggressiveNpcAI;

import com.aionemu.gameserver.ai.AIName;
import com.aionemu.gameserver.ai.AIState;
import com.aionemu.gameserver.controllers.attack.AggroTarget;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.questEngine.QuestEngine;
import com.aionemu.gameserver.questEngine.model.QuestEnv;
import com.aionemu.gameserver.utils.PositionUtil;

/** Native hostile lure; reaching the spring is a cure, not a monster kill. */
@AIName("cursed_zirius")
public class CursedZiriusAI extends AggressiveNpcAI {

	public CursedZiriusAI(Npc owner) {
		super(owner);
	}

	public static boolean canBreakCurse(Npc npc) {
		// The contemporary walkthrough calls for 15-20% HP before entering
		// the spring. Keep the existing quest's spring centre/radius.
		return npc.getNpcId() == 214576 && npc.isSpawned() && !npc.isDead()
			&& npc.getWorldId() == 210060000 && npc.getLifeStats().getHpPercentage() <= 20
			&& PositionUtil.getDistance(npc, 1691.41f, 219.09f, 72.62f) <= 30;
	}

	private boolean checkSpring() {
		if (!canThink() || !isInState(AIState.FIGHT) || !canBreakCurse(getOwner()))
			return false;
		Creature target = getAggroList().getTarget(AggroTarget.MOST_HATED);
		if (target != null && target.getMaster() instanceof Player player && !player.isDead())
			QuestEngine.getInstance().onAttack(new QuestEnv(getOwner(), player, 0));
		return !getOwner().isSpawned();
	}

	@Override
	public void think() {
		if (!checkSpring())
			super.think();
	}

	@Override
	protected void handleMoveValidate() {
		super.handleMoveValidate();
		checkSpring();
	}

	@Override
	protected void handleMoveArrived() {
		if (!checkSpring())
			super.handleMoveArrived();
	}
}
