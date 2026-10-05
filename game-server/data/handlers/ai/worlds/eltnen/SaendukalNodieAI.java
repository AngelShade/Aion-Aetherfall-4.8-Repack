package ai.worlds.eltnen;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import com.aionemu.gameserver.ai.AIActions;
import com.aionemu.gameserver.ai.AIName;
import com.aionemu.gameserver.ai.HpPhases;
import com.aionemu.gameserver.controllers.observer.DeathObserver;
import com.aionemu.gameserver.model.ChatType;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.VisibleObject;
import com.aionemu.gameserver.model.templates.npcskill.NpcSkillTargetAttribute;
import com.aionemu.gameserver.network.aion.serverpackets.SM_SYSTEM_MESSAGE;
import com.aionemu.gameserver.skillengine.model.Effect;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.utils.ThreadPoolManager;
import com.aionemu.gameserver.world.WorldMapInstance;

import ai.AggressiveNpcAI;

/**
 * Encounter AI for Night Grand Chieftain Saendukal (대족장 샨두카).
 * Eltnen Kaidan Headquarters (Map 210020000, NPC 211701).
 *
 * Authentic "No-Die" (KrallWarriorHKBossNodie) Mechanics:
 * 1. Mutual Exclusion:
 *    On spawn, checks if the Legendary World Boss (211040) is alive within the map instance.
 *    If 211040 is alive, 211701 immediately despawns to prevent duplicate overlay.
 *
 * 2. Combat Engagement:
 *    On first attack, shouts string 340406 ("누구냐!") and string 340404 ("한판 붙자!").
 *
 * 3. 50% HP Retreat & Big Elite Guard:
 *    At 50% HP, shouts string 340407 ("도움이 필요하다! 다들 도와줘!" - "I need help! Everyone help me!").
 *    Spawns 4 Big Elite Krall Royal Guards:
 *      - 212033 (Crack Kaidan Captain)
 *      - 212022 (Kaidan Juggernaut)
 *      - 212025 (Crack Kaidan Skullcracker)
 *      - 211036 (Kaidan Shaman)
 *    Saendukal retreats and vanishes ("No-Die").
 *
 * 4. Treasure Reward:
 *    Tracks the defeat of all 4 Royal Guards. Upon wiping the guard squad,
 *    spawns the Kaidan Chieftain's Treasure Chest (211861) containing full boss loot.
 *
 * 5. Burst Protection:
 *    If a lethal hit bypasses the 50% callback, handleDied() still spawns the guards;
 *    the chest remains gated on the surviving guard wave being defeated.
 */
@AIName("saendukal_nodie")
public class SaendukalNodieAI extends AggressiveNpcAI implements HpPhases.PhaseHandler {

	private static final int GIANT_SAENDUKAL_ID = 211040;
	private static final int TREASURE_CHEST_ID = 211861;

	// Elite Guard IDs
	private static final int GUARD_CAPTAIN_ID = 212033;      // Crack Kaidan Captain
	private static final int GUARD_JUGGERNAUT_ID = 212022;    // Kaidan Juggernaut
	private static final int GUARD_SKULLCRACKER_ID = 212025;  // Crack Kaidan Skullcracker
	private static final int GUARD_SHAMAN_ID = 211036;        // Kaidan Shaman

	// Shouts
	private static final int SHOUT_WHO_GOES_THERE = 340406;
	private static final int SHOUT_LETS_FIGHT = 340404;
	private static final int SHOUT_HELP_ME = 340407;
	private static final int SKILL_LEVEL = 31;
	private static final int SKILL_BELLICOSITY = 16873;

	private final HpPhases hpPhases = new HpPhases(50);
	private final AtomicBoolean attackedShouted = new AtomicBoolean(false);
	private final AtomicBoolean retreated = new AtomicBoolean(false);
	private final AtomicBoolean retreatCheckScheduled = new AtomicBoolean(false);
	private final AtomicBoolean chestSpawned = new AtomicBoolean(false);

	public SaendukalNodieAI(Npc owner) {
		super(owner);
	}

	@Override
	protected void handleSpawned() {
		super.handleSpawned();
		checkMutualExclusion();
	}

	private boolean checkMutualExclusion() {
		WorldMapInstance instance = getPosition().getWorldMapInstance();
		if (instance != null) {
			Npc giant = instance.getNpc(GIANT_SAENDUKAL_ID);
			if (giant != null && !giant.isDead() && giant.isSpawned()) {
				// Giant Saendukal is alive; night surrogate yields
				AIActions.deleteOwner(this);
				return true;
			}
		}
		return false;
	}

	@Override
	protected void handleAttack(Creature creature) {
		if (checkMutualExclusion()) {
			return;
		}

		if (attackedShouted.compareAndSet(false, true)) {
			Npc owner = getOwner();
			owner.queueSkill(SKILL_BELLICOSITY, SKILL_LEVEL, 0, NpcSkillTargetAttribute.ME);
			PacketSendUtility.broadcastPacket(owner, new SM_SYSTEM_MESSAGE(ChatType.NPC, owner, SHOUT_WHO_GOES_THERE));
			ThreadPoolManager.getInstance().schedule(() -> {
				if (owner != null && !owner.isDead() && owner.isSpawned()) {
					PacketSendUtility.broadcastPacket(owner, new SM_SYSTEM_MESSAGE(ChatType.NPC, owner, SHOUT_LETS_FIGHT));
				}
			}, 1500);
		}

		super.handleAttack(creature);
		hpPhases.tryEnterNextPhase(this);
	}

	@Override
	public float modifyDamage(Creature attacker, float damage, Effect effect) {
		if (retreated.get()) {
			return 0;
		}
		if (getOwner().isDead()) {
			return damage;
		}

		int maxHp = getLifeStats().getMaxHp();
		int retreatHp = maxHp / 2;
		int currentHp = getLifeStats().getCurrentHp();
		if (currentHp <= retreatHp) {
			scheduleRetreatPhaseCheck();
			return 0;
		}

		int damageUntilRetreat = currentHp - retreatHp;
		if (damage >= damageUntilRetreat) {
			scheduleRetreatPhaseCheck();
			return damageUntilRetreat;
		}
		return damage;
	}

	private void scheduleRetreatPhaseCheck() {
		if (retreatCheckScheduled.compareAndSet(false, true)) {
			ThreadPoolManager.getInstance().schedule(() -> {
				try {
					if (!retreated.get() && getOwner().isSpawned() && !isDead()
						&& getLifeStats().getHpPercentage() <= 50) {
						hpPhases.tryEnterNextPhase(this);
					}
				} finally {
					retreatCheckScheduled.set(false);
				}
			}, 100);
		}
	}

	@Override
	public void handleHpPhase(int phaseHpPercent) {
		if (phaseHpPercent == 50) {
			triggerRetreatAndGuards();
		}
	}

	private void triggerRetreatAndGuards() {
		if (!retreated.compareAndSet(false, true)) {
			return;
		}

		Npc owner = getOwner();
		PacketSendUtility.broadcastPacket(owner, new SM_SYSTEM_MESSAGE(ChatType.NPC, owner, SHOUT_HELP_ME));

		float x = owner.getX();
		float y = owner.getY();
		float z = owner.getZ();
		byte h = owner.getHeading();
		VisibleObject target = getTarget();

		spawnRoyalGuards(x, y, z, h, target instanceof Creature creatureTarget ? creatureTarget : null);

		// Saendukal escapes and vanishes ("No-Die" retreat)
		AIActions.deleteOwner(this);
	}

	private void spawnRoyalGuards(float x, float y, float z, byte h, Creature target) {
		// Spawn 4 Big Elite Guards surrounding the throne
		int[] guardIds = {GUARD_CAPTAIN_ID, GUARD_JUGGERNAUT_ID, GUARD_SKULLCRACKER_ID, GUARD_SHAMAN_ID};
		float[][] offsets = {
			{0.0f, 3.0f},
			{3.0f, 0.0f},
			{-3.0f, 0.0f},
			{0.0f, -3.0f}
		};

		AtomicInteger remainingGuards = new AtomicInteger(guardIds.length);

		for (int i = 0; i < guardIds.length; i++) {
			float gx = x + offsets[i][0];
			float gy = y + offsets[i][1];
			Npc guard = (Npc) spawn(guardIds[i], gx, gy, z, h);
			if (guard != null) {
				if (target != null && !target.isDead()) {
					guard.getAggroList().addHate(target, 1000);
				}
				guard.getObserveController().attach(new DeathObserver(c -> {
					if (remainingGuards.decrementAndGet() == 0) {
						spawnTreasureChest(x, y, z, h);
					}
				}));
			} else {
				if (remainingGuards.decrementAndGet() == 0) {
					spawnTreasureChest(x, y, z, h);
				}
			}
		}
	}

	private void spawnTreasureChest(float x, float y, float z, byte h) {
		if (chestSpawned.compareAndSet(false, true)) {
			spawn(TREASURE_CHEST_ID, x, y, z, h);
		}
	}

	@Override
	protected void handleDied() {
		// A lethal burst must not skip the Royal Guard phase or award the chest early.
		// If the 50% callback was bypassed, still spawn the guard wave and let its
		// death observers award the chest only after all successfully spawned guards die.
		if (retreated.compareAndSet(false, true)) {
			Npc owner = getOwner();
			VisibleObject target = getTarget();
			spawnRoyalGuards(owner.getX(), owner.getY(), owner.getZ(), owner.getHeading(),
				target instanceof Creature creatureTarget ? creatureTarget : null);
		}
		super.handleDied();
	}
}
