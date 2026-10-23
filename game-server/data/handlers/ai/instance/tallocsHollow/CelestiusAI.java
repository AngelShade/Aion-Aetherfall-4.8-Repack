package ai.instance.tallocsHollow;

import java.util.concurrent.Future;

import com.aionemu.gameserver.ai.AIName;
import com.aionemu.gameserver.ai.AIState;
import com.aionemu.gameserver.ai.NpcAI;
import com.aionemu.gameserver.ai.manager.WalkManager;
import com.aionemu.gameserver.model.EmotionType;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.state.CreatureState;
import com.aionemu.gameserver.model.skill.NpcSkillTemplateEntry;
import com.aionemu.gameserver.model.templates.npcskill.NpcSkillTargetAttribute;
import com.aionemu.gameserver.model.templates.npcskill.QueuedNpcSkillTemplate;
import com.aionemu.gameserver.network.aion.serverpackets.SM_EMOTION;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.utils.ThreadPoolManager;

import ai.AggressiveNpcAI;

/**
 * @author xTz
 */
@AIName("celestius")
public class CelestiusAI extends AggressiveNpcAI {

	private static final int SUMMONS_ID = 281514;
	private Future<?> helpersTask;
	private int encounter;

	public CelestiusAI(Npc owner) {
		super(owner);
	}

	@Override
	protected synchronized void handleAttack(Creature creature) {
		super.handleAttack(creature);
		if (isInState(AIState.FIGHT) && helpersTask == null)
			startHelpersCall();
	}

	private void startHelpersCall() {
		int pull = ++encounter;
		helpersTask = ThreadPoolManager.getInstance().scheduleAtFixedRate(() -> queueHelpersCall(pull), 1000, 25000);
	}

	private synchronized void queueHelpersCall(int pull) {
		if (pull != encounter || !getOwner().isSpawned() || isDead() || !isInState(AIState.FIGHT)
			|| getOwner().hasQueuedSkill(skill -> skill.getSkillId() == 18981))
			return;
		// Reinforcements belong to a completed native cast, never an interrupted or previous pull's call.
		getOwner().queueSkill(new NpcSkillTemplateEntry(new QueuedNpcSkillTemplate(18981, 44, 0, NpcSkillTargetAttribute.ME)) {
			@Override
			public void fireOnEndCastEvents(Npc npc) {
				super.fireOnEndCastEvents(npc);
				spawnHelpers(pull);
			}
		});
	}

	private synchronized void spawnHelpers(int pull) {
		if (pull != encounter || helpersTask == null || !getOwner().isSpawned() || isDead() || !isInState(AIState.FIGHT))
			return;
		startWalker((Npc) spawn(SUMMONS_ID, 518, 813, 1378, (byte) 0), "3001900001");
		startWalker((Npc) spawn(SUMMONS_ID, 551, 795, 1376, (byte) 0), "3001900002");
		startWalker((Npc) spawn(SUMMONS_ID, 574, 854, 1375, (byte) 0), "3001900003");
	}

	private void startWalker(Npc npc, String walkId) {
		npc.getSpawn().setWalkerId(walkId);
		WalkManager.startWalking((NpcAI) npc.getAi());
		npc.setState(CreatureState.ACTIVE, true);
		PacketSendUtility.broadcastPacket(npc, new SM_EMOTION(npc, EmotionType.CHANGE_SPEED, 0, npc.getObjectId()));
	}

	private synchronized void cleanUp() {
		encounter++;
		if (helpersTask != null) {
			helpersTask.cancel(false);
			helpersTask = null;
		}
		getOwner().clearQueuedSkills();
		deleteSummons();
	}

	private void deleteSummons() {
		getPosition().getWorldMapInstance().getNpcs(SUMMONS_ID).stream()
			.filter(npc -> npc.getSpawn().getCreatorId() == getObjectId()).forEach(npc -> npc.getController().delete());
	}

	@Override
	protected synchronized void handleBackHome() {
		cleanUp();
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

}
