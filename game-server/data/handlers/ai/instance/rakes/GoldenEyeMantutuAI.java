package ai.instance.rakes;

import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

import com.aionemu.commons.utils.Rnd;
import com.aionemu.gameserver.ai.AIName;
import com.aionemu.gameserver.ai.AIState;
import com.aionemu.gameserver.ai.manager.EmoteManager;
import com.aionemu.gameserver.ai.poll.AIQuestion;
import com.aionemu.gameserver.controllers.attack.AggroTarget;
import com.aionemu.gameserver.model.EmotionType;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.VisibleObject;
import com.aionemu.gameserver.model.gameobjects.state.CreatureState;
import com.aionemu.gameserver.network.aion.serverpackets.SM_EMOTION;
import com.aionemu.gameserver.skillengine.SkillEngine;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.utils.ThreadPoolManager;

import ai.AggressiveNpcAI;

/**
 * @author xTz
 */
@AIName("golden_eye_mantutu")
public class GoldenEyeMantutuAI extends AggressiveNpcAI {

	private boolean canThink = true;
	private final AtomicBoolean isHome = new AtomicBoolean(true);
	private Future<?> hungerTask;
 private final SteelRakeTasks tasks=new SteelRakeTasks(this);

	public GoldenEyeMantutuAI(Npc owner) {
		super(owner);
	}

	@Override
	public boolean canThink() {
		return canThink;
	}

	@Override
	protected void handleCustomEvent(int eventId, Object... args) {
		if (eventId == 1 && args != null && args.length > 0 && args[0] instanceof Npc device
   && device.isSpawned() && device.getWorldId()==getOwner().getWorldId() && device.getInstanceId()==getOwner().getInstanceId()
   && (device.getNpcId()==281128 && getEffectController().hasAbnormalEffect(20489)
   || device.getNpcId()==281129 && getEffectController().hasAbnormalEffect(20490))) {
			canThink = false;
			getMoveController().abortMove();
			EmoteManager.emoteStopAttacking(getOwner());
			Npc npc = (Npc) args[0];
			getOwner().setTarget(npc);
			setStateIfNot(AIState.FOLLOWING);
			getMoveController().moveToTargetObject();
			getOwner().setState(CreatureState.ACTIVE, true);
			PacketSendUtility.broadcastPacket(getOwner(), new SM_EMOTION(getOwner(), EmotionType.CHANGE_SPEED, 0, getObjectId()));
   tasks.later(()->{if(!canThink && getTarget()==device && !isHome.get()){restoreDevice(device);resumeCombat();}},20000);
		}
	}

	@Override
	protected void handleMoveArrived() {
		super.handleMoveArrived();
		if (!canThink) {
			VisibleObject target = getTarget();
			getMoveController().abortMove();
			if (target != null && target.isSpawned() && target instanceof Npc npc) {
				if (npc.getNpcId() == 281128 || npc.getNpcId() == 281129) {
					startFeedTime(npc);
				}
			} else resumeCombat();
		}
	}

	private void startFeedTime(final Npc npc) {
		tasks.later(() -> {
			if (!isDead() && npc != null && npc.isSpawned() && !isHome.get()) {
				switch (npc.getNpcId()) {
					case 281128: // Feed Supply Device
						getEffectController().removeEffect(20489);
						spawn(701386, 716.508f, 508.571f, 939.607f, (byte) 119);
						break;
					case 281129: // Water Supply Device
						getEffectController().removeEffect(20490);
						spawn(701387, 716.389f, 494.207f, 939.607f, (byte) 119);
						break;
				}
				npc.getController().delete();
				resumeCombat();
			}
		}, 6000);
	}

 private void restoreDevice(Npc device){
  int id=device.getNpcId()==281128?701386:701387;
  if(getPosition().getWorldMapInstance().getNpc(id)==null){if(id==701386)spawn(id,716.508f,508.571f,939.607f,(byte)119);else spawn(id,716.389f,494.207f,939.607f,(byte)119);}
  if(device.isSpawned())device.getController().delete();
 }
 private void resumeCombat(){
  canThink=true;Creature creature=getAggroList().getTarget(AggroTarget.MOST_HATED);getOwner().setTarget(creature);
  setStateIfNot(AIState.FIGHT);if(creature!=null){getOwner().getGameStats().renewLastAttackTime();getOwner().getGameStats().renewLastAttackedTime();getOwner().getGameStats().renewLastSkillTime();handleMoveValidate();}else think();
 }

	@Override
	public boolean ask(AIQuestion question) {
		return switch (question) {
			case IS_IMMUNE_TO_ABNORMAL_STATES -> true;
			default -> super.ask(question);
		};
	}

	@Override
	protected void handleAttack(Creature creature) {
		super.handleAttack(creature);
		if (isHome.compareAndSet(true, false)) {
			doSchedule();
		}
	}

	@Override
	protected void handleDespawned() {
		cancelHungerTask();
		super.handleDespawned();
	}

	@Override
	protected void handleDied() {
		cancelHungerTask();
		Npc npc = getPosition().getWorldMapInstance().getNpc(219037);
		if (npc != null && !npc.isDead()) {
			npc.getEffectController().removeEffect(18189);
		}
		super.handleDied();
	}

	@Override
	protected void handleBackHome() {
		cancelHungerTask();
  for(int id:new int[]{281128,281129})for(Npc device:getPosition().getWorldMapInstance().getNpcs(id))device.getController().delete();
  if(getPosition().getWorldMapInstance().getNpc(701386)==null)spawn(701386,716.508f,508.571f,939.607f,(byte)119);
  if(getPosition().getWorldMapInstance().getNpc(701387)==null)spawn(701387,716.389f,494.207f,939.607f,(byte)119);
		getEffectController().removeEffect(20489);
		getEffectController().removeEffect(20490);
		canThink = true;
		isHome.set(true);
		super.handleBackHome();
	}

	private void doSchedule() {
  tasks.later(()->{
   if(isHome.get())return;
   int skill=Rnd.nextBoolean()?20489:20490;
   SkillEngine.getInstance().getSkill(getOwner(),skill,20,getOwner()).useNoAnimationSkill();
   scheduleHunger();
  },10000);
	}

 private void scheduleHunger(){tasks.later(()->{if(!isHome.get()){int skill=Rnd.nextBoolean()?20489:20490;SkillEngine.getInstance().getSkill(getOwner(),skill,20,getOwner()).useNoAnimationSkill();scheduleHunger();}},30000);}
	private void cancelHungerTask() {
  tasks.reset();
		if (hungerTask != null && !hungerTask.isDone()) {
			hungerTask.cancel(true);
		}
	}

}
