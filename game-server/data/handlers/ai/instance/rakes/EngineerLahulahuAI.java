package ai.instance.rakes;

import com.aionemu.commons.utils.Rnd;
import com.aionemu.gameserver.ai.AIActions;
import com.aionemu.gameserver.ai.AIName;
import com.aionemu.gameserver.ai.HpPhases;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.utils.ThreadPoolManager;
import com.aionemu.gameserver.world.WorldMapInstance;

import ai.AggressiveNpcAI;

/**
 * @author xTz
 */
@AIName("engineerlahulahu")
public class EngineerLahulahuAI extends AggressiveNpcAI implements HpPhases.PhaseHandler {

	private final HpPhases hpPhases = new HpPhases(95, 25);
	private int skill = 18153;
	private final SteelRakeTasks tasks = new SteelRakeTasks(this);
	private Npc npc;
	private Npc npc1;
	private Npc npc2;
	private Npc npc3;
	private Npc npc4;
	private Npc npc5;
	private Npc npc6;
	private Npc npc7;
	private Npc npc8;
	private Npc npc9;
	private Npc npc10;
	private Npc npc11;

	public EngineerLahulahuAI(Npc owner) {
		super(owner);
	}

	private void registerNpcs() {
		WorldMapInstance instance = getPosition().getWorldMapInstance();
		npc = instance.getNpc(281111);
		npc1 = instance.getNpc(281325);
		npc2 = instance.getNpc(281323);
		npc3 = instance.getNpc(281322);
		npc4 = instance.getNpc(281326);
		npc5 = instance.getNpc(281113);
		npc6 = instance.getNpc(281324);
		npc7 = instance.getNpc(281109);
		npc8 = instance.getNpc(281112);
		npc9 = instance.getNpc(281114);
		npc10 = instance.getNpc(281108);
		npc11 = instance.getNpc(281110);
	}

	@Override
	protected void handleAttack(Creature creature) {
		super.handleAttack(creature);
		hpPhases.tryEnterNextPhase(this);
	}

	@Override
	public void handleHpPhase(int phaseHpPercent) {
		switch (phaseHpPercent) {
			case 95:
				registerNpcs();
				phaseBuff(18131);
				useSkills();
				break;
			case 25:
				getEffectController().removeEffect(18131);
				phaseBuff(18132);
				break;
		}
	}

	@Override
	protected void handleBackHome() {
		resetEncounter();
		super.handleBackHome();
	}

	private void doSchedule() {
		tasks.later(this::useSkills, 10000);
	}

	private void useSkills() {
		if (getPosition().isSpawned() && !isDead() && hpPhases.getCurrentPhase() > 0 && !isInState(com.aionemu.gameserver.ai.AIState.RETURNING)) {
			int rnd = Rnd.get(1, 8);
			switch (rnd) {
				case 1:
					if (npc != null && npc.isSpawned() && !npc.isDead()) {
						npc.setTarget(npc);
						npc.getController().useSkill(skill);
					}
					if (npc1 != null && npc1.isSpawned() && !npc1.isDead()) {
						npc1.setTarget(npc1);
						npc1.getController().useSkill(skill);
					}
					break;
				case 2:
					if (npc2 != null && npc2.isSpawned() && !npc2.isDead()) {
						npc2.setTarget(npc2);
						npc2.getController().useSkill(skill);
					}
					if (npc3 != null && npc3.isSpawned() && !npc3.isDead()) {
						npc3.setTarget(npc3);
						npc3.getController().useSkill(skill);
					}
					break;
				case 3:
					if (npc4 != null && npc4.isSpawned() && !npc4.isDead()) {
						npc4.setTarget(npc4);
						npc4.getController().useSkill(skill);
					}
					if (npc5 != null && npc5.isSpawned() && !npc5.isDead()) {
						npc5.setTarget(npc5);
						npc5.getController().useSkill(skill);
					}
					break;
				case 4:
					if (npc6 != null && npc6.isSpawned() && !npc6.isDead()) {
						npc6.setTarget(npc6);
						npc6.getController().useSkill(skill);
					}
					if (npc7 != null && npc7.isSpawned() && !npc7.isDead()) {
						npc7.setTarget(npc7);
						npc7.getController().useSkill(skill);
					}
					break;
				case 5:
					if (npc8 != null && npc8.isSpawned() && !npc8.isDead()) {
						npc8.setTarget(npc8);
						npc8.getController().useSkill(skill);
					}
					break;
				case 6:
					if (npc9 != null && npc9.isSpawned() && !npc9.isDead()) {
						npc9.setTarget(npc9);
						npc9.getController().useSkill(skill);
					}
					break;
				case 7:
					if (npc10 != null && npc10.isSpawned() && !npc10.isDead()) {
						npc10.setTarget(npc10);
						npc10.getController().useSkill(skill);
					}
					break;
				case 8:
					if (npc11 != null && npc11.isSpawned() && !npc11.isDead()) {
						npc11.setTarget(npc11);
						npc11.getController().useSkill(skill);
					}
					break;
			}
			doSchedule();
		}
	}

 private void phaseBuff(int id){var target=getTarget();try{AIActions.targetSelf(this);AIActions.useSkill(this,id);}finally{getOwner().setTarget(target);}}
 private void resetEncounter() {
  tasks.reset();hpPhases.reset();getEffectController().removeEffect(18131);getEffectController().removeEffect(18132);
  for(int id:new int[]{281111,281325,281323,281322,281326,281113,281324,281109,281112,281114,281108,281110})
   for(Npc pipe:getPosition().getWorldMapInstance().getNpcs(id))pipe.getController().cancelCurrentSkill(null);
 }
 @Override protected void handleDied(){resetEncounter();super.handleDied();}
 @Override protected void handleDespawned(){resetEncounter();super.handleDespawned();}

}
