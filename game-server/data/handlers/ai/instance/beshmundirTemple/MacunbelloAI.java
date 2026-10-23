package ai.instance.beshmundirTemple;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Future;

import com.aionemu.commons.utils.Rnd;
import com.aionemu.gameserver.ai.AIName;
import com.aionemu.gameserver.ai.AIState;
import com.aionemu.gameserver.controllers.attack.AggroTarget;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.templates.npcskill.NpcSkillTargetAttribute;
import com.aionemu.gameserver.skillengine.SkillEngine;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.utils.PositionUtil;
import com.aionemu.gameserver.utils.ThreadPoolManager;

/** BeyondAionSharp 3d880c2b: timed rotation and owned reapers; native variant spawning is retained. */
@AIName("macunbello")
public class MacunbelloAI extends MacunbelloCombatAI {

	private static final int[] NORMAL_BANDS = { 91, 71, 51, 31, 11 };
	private static final int[] HARD_BANDS = { 91, 81, 71, 61, 51, 41, 31, 21, 11 };
	private static final float[][] WAVE_POSITIONS = {
		{ 955.54f, 160.44f, 242.3f, 275 }, { 1004.12f, 159.32f, 242.3f, 225 },
		{ 952f, 110.41f, 243.4f, 90 }, { 1007.07f, 110.33f, 243.4f, 90 }
	};
	private final Object encounterLock = new Object();
	private final Set<Npc> reapers = new HashSet<>();
	private Future<?> phaseTask;
	private Future<?> shockwaveTask;
	private Future<?> waveTask;
	private int generation;
	private int bandsCrossed;
	private boolean hardReaperCalled;

	public MacunbelloAI(Npc owner) {
		super(owner);
	}

	@Override
	protected void handleSpawned() {
		super.handleSpawned();
		if (enabled)
			queue(19049, NpcSkillTargetAttribute.ME);
	}

	@Override
	protected void handleAttack(Creature creature) {
		super.handleAttack(creature);
		if (!enabled || isDead() || !isInState(AIState.FIGHT))
			return;
		synchronized (encounterLock) {
			if (isDead() || !getOwner().isSpawned() || !isInState(AIState.FIGHT))
				return;
			if (phaseTask == null) {
				int fight = ++generation;
				PacketSendUtility.broadcastMessage(getOwner(), 1500060);
				queue(19053, NpcSkillTargetAttribute.MOST_HATED);
				// The pinned implementation uses 10s, despite its 15s prose description.
				phaseTask = ThreadPoolManager.getInstance().scheduleAtFixedRate(() -> phaseTick(fight), 10000, 10000);
				shockwaveTask = ThreadPoolManager.getInstance().scheduleAtFixedRate(() -> shockwaveTick(fight), 10000, 10000);
				scheduleWave(fight, 20000);
			}
			if (getNpcId() == 216164 && !hardReaperCalled && Rnd.nextInt(100) < 5) {
				hardReaperCalled = true;
				ownReaper((Npc) rndSpawnInRange(281775, 5));
			}
		}
	}

	private boolean fighting(int fight) {
		return fight == generation && !isDead() && getOwner().isSpawned() && isInState(AIState.FIGHT);
	}

	private void phaseTick(int fight) {
		synchronized (encounterLock) {
			if (!fighting(fight))
				return;
			int[] bands = getNpcId() == 216164 ? HARD_BANDS : NORMAL_BANDS;
			if (bandsCrossed < bands.length && getLifeStats().getHpPercentage() < bands[bandsCrossed]) {
				bandsCrossed++;
				queue(19054, NpcSkillTargetAttribute.RANDOM);
			} else {
				queue(19053, NpcSkillTargetAttribute.ME);
			}
		}
	}

	private void shockwaveTick(int fight) {
		synchronized (encounterLock) {
			if (fighting(fight))
				queue(19052, NpcSkillTargetAttribute.MOST_HATED);
		}
	}

	private void scheduleWave(int fight, long delay) {
		waveTask = ThreadPoolManager.getInstance().schedule(() -> waveTick(fight), delay);
	}

	private void waveTick(int fight) {
		synchronized (encounterLock) {
			if (!fighting(fight))
				return;
			boolean desperate = getLifeStats().getHpPercentage() <= 50;
			PacketSendUtility.broadcastMessage(getOwner(), 1500061);
			for (int i = 0; i < (desperate ? 4 : 2); i++) {
				float[] p = WAVE_POSITIONS[i];
				ownReaper((Npc) spawn(281698, p[0], p[1], p[2], (byte) (p[3] * 120 / 360)));
			}
			scheduleWave(fight, desperate ? 40000 : 30000);
		}
	}

	private void ownReaper(Npc reaper) {
		reapers.removeIf(npc -> !npc.isSpawned());
		reapers.add(reaper);
		// Native hate admission starts combat; seed all currently eligible attackers, not an invented player pool.
		getAggroList().stream().map(info -> info.getAttacker()).filter(this::validTarget)
			.filter(target -> getAggroList().isHating(target)).forEach(target -> reaper.getAggroList().addHate(target, 1));
	}

	void onReaperCurse(Npc reaper, Creature victim, float range) {
		synchronized (encounterLock) {
			if (phaseTask == null || !fighting(generation) || !reapers.contains(reaper) || !validTarget(victim)
				|| !PositionUtil.isInRange(getOwner(), reaper, range))
				return;
			PacketSendUtility.broadcastMessage(getOwner(), 1500062);
			queue(19051, victim, null);
		}
	}

	private void stopEncounter() {
		Set<Npc> cleanup;
		synchronized (encounterLock) {
			generation++;
			if (phaseTask != null)
				phaseTask.cancel(false);
			if (shockwaveTask != null)
				shockwaveTask.cancel(false);
			if (waveTask != null)
				waveTask.cancel(false);
			phaseTask = shockwaveTask = waveTask = null;
			bandsCrossed = 0;
			hardReaperCalled = false;
			getOwner().clearQueuedSkills();
			cleanup = new HashSet<>(reapers);
			reapers.clear();
		}
		for (Npc reaper : cleanup)
			if (reaper.isSpawned())
				reaper.getController().delete();
	}

	@Override
	protected void handleNotAtHome() {
		if (enabled)
			stopEncounter();
		super.handleNotAtHome();
	}

	@Override
	protected void handleBackHome() {
		if (enabled)
			stopEncounter();
		super.handleBackHome();
		if (enabled) {
			// ReturningEventHandler removes buffs; preserve the instance's original weakness selection.
			int weakness = switch (getNpcId()) {
				case 216735 -> 19046;
				case 216734 -> 19047;
				case 216737 -> 19048;
				default -> 0;
			};
			if (weakness != 0)
				SkillEngine.getInstance().applyEffectDirectly(weakness, getOwner(), getOwner());
			queue(19049, NpcSkillTargetAttribute.ME);
		}
	}

	@Override
	protected void handleDied() {
		if (enabled) {
			PacketSendUtility.broadcastMessage(getOwner(), 1500063);
			stopEncounter();
		}
		super.handleDied();
	}

	@Override
	protected void handleDespawned() {
		if (enabled)
			stopEncounter();
		super.handleDespawned();
	}
}
