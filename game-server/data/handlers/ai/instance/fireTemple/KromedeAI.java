package ai.instance.fireTemple;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Future;
import com.aionemu.commons.utils.Rnd;
import com.aionemu.gameserver.ai.AIName;
import com.aionemu.gameserver.ai.AIState;
import com.aionemu.gameserver.ai.AttackIntention;
import com.aionemu.gameserver.controllers.attack.AggroTarget;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.skillengine.SkillEngine;
import com.aionemu.gameserver.skillengine.model.Skill;
import com.aionemu.gameserver.skillengine.model.SkillTemplate;
import com.aionemu.gameserver.utils.PositionUtil;
import com.aionemu.gameserver.utils.ThreadPoolManager;
import ai.AggressiveNpcAI;

/** Both variants use the same completion-driven encounter and native hate/chase rules. */
@AIName("kromede")
public class KromedeAI extends AggressiveNpcAI {
	private final KromedeEncounter encounter = new KromedeEncounter();
	private final Set<Npc> traps = new HashSet<>();
	private Future<?> pulseTask;
	private int generation;
	private Skill pending;
	private boolean blessed;
	private long readyAt;

	public KromedeAI(Npc owner) { super(owner); }

	@Override
	protected synchronized void handleSpawned() {
		resetEncounter();
		super.handleSpawned();
		schedulePulse();
	}

	private void schedulePulse() {
		int epoch = generation;
		pulseTask = ThreadPoolManager.getInstance().schedule(() -> pulse(epoch), 250);
	}

	private synchronized void pulse(int epoch) {
		if (epoch != generation || isDead() || !getOwner().isSpawned()) return;
		try {
			if (isInState(AIState.RETURNING)) return;
			long now = System.currentTimeMillis();
			if (pending != null) {
				if (getOwner().isCasting()) return;
				// An interrupted cast has no completion event. Retry that step.
				pending = null;
				readyAt = now + 250;
			}
			if (getOwner().isCasting() || now < readyAt) return;
			if (!blessed) {
				Skill buff = SkillEngine.getInstance().getSkill(getOwner(), 17052, 28, getOwner());
				blessed = buff != null && buff.useNoAnimationSkill();
				return;
			}
			if (!isInState(AIState.FIGHT)) return;
			Creature target = getAggroList().getTarget(AggroTarget.MOST_HATED);
			if (target == null || target.isDead()) { think(); return; }
			getOwner().setTarget(target);
			int skillId = encounter.next(now, getLifeStats().getHpPercentage());
			if (skillId == 0) return;
			if ((skillId == 17047 || skillId == 16847) && !PositionUtil.isInRange(getOwner(), target, 2f)) {
				super.handleTargetTooFar(); // Native movement also enforces leash limits.
				return;
			}
			getOwner().getMoveController().abortMove();
			Creature firstTarget = skillId == 16847 ? target : getOwner();
			Skill skill = KromedeSkills.create(getOwner(), skillId, firstTarget);
			if (skill != null) {
				pending = skill;
				if (!skill.useSkill()) pending = null;
			}
		} finally {
			if (epoch == generation && !isDead() && getOwner().isSpawned()) schedulePulse();
		}
	}

	@Override
	public synchronized void onEndUseSkill(SkillTemplate template, int level) {
		super.onEndUseSkill(template, level);
		if (pending == null || pending.getSkillId() != template.getSkillId() || !isInState(AIState.FIGHT)) return;
		Skill completed = pending;
		pending = null;
		long now = System.currentTimeMillis();
		encounter.complete(template.getSkillId(), now, getLifeStats().getHpPercentage());
		readyAt = now + Math.max(300, completed.getHitTime());
		if (template.getSkillId() == 16674) spawnTraps();
	}

	@Override
	public synchronized AttackIntention chooseAttackIntention() {
		Creature target = getAggroList().getTarget(AggroTarget.MOST_HATED);
		if (target == null) return AttackIntention.FINISH_ATTACK;
		getOwner().setTarget(target);
		if (encounter.isSequence() || pending != null || getOwner().isCasting()) return AttackIntention.FINISH_ATTACK;
		return AttackIntention.SIMPLE_ATTACK;
	}

	@Override
	protected synchronized void handleTargetTooFar() {
		// Self-centered AoEs stay at the caster while executing.
		if (pending == null && !getOwner().isCasting()) super.handleTargetTooFar();
	}

	private void spawnTraps() {
		traps.removeIf(npc -> !npc.isSpawned());
		float angle = Rnd.nextFloat(360f);
		for (int i = 0; i < 3; i++) {
			double radians = Math.toRadians(angle + i * 120);
			float distance = Rnd.get(30, 50) / 10f;
			Npc trap = (Npc) spawn(280501, getOwner().getX() + (float) Math.cos(radians) * distance,
				getOwner().getY() + (float) Math.sin(radians) * distance, getOwner().getZ(), getOwner().getHeading());
			if (trap != null) traps.add(trap);
		}
	}

	private void resetEncounter() {
		generation++; // Invalidate even a callback that was already dequeued.
		if (pulseTask != null) pulseTask.cancel(false);
		getOwner().getController().abortCast();
		pending = null;
		for (Npc trap : traps) if (trap.isSpawned()) trap.getController().delete();
		traps.clear();
		encounter.reset();
		blessed = false;
		readyAt = 0;
	}

	@Override
	protected synchronized void handleNotAtHome() { resetEncounter(); super.handleNotAtHome(); }
	@Override
	protected synchronized void handleBackHome() { resetEncounter(); super.handleBackHome(); schedulePulse(); }
	@Override
	protected synchronized void handleDied() { resetEncounter(); super.handleDied(); }
	@Override
	protected synchronized void handleDespawned() { resetEncounter(); super.handleDespawned(); }
}
