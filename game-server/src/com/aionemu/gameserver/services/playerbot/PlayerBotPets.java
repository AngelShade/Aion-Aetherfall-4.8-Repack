package com.aionemu.gameserver.services.playerbot;

import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.Summon;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.summons.SummonMode;
import com.aionemu.gameserver.model.summons.UnsummonType;
import com.aionemu.gameserver.services.summons.SummonsService;
import com.aionemu.gameserver.utils.PositionUtil;
import com.aionemu.gameserver.world.geo.GeoService;

/** Drives an owned spirit through normal summon modes, learned orders and attack timing. */
final class PlayerBotPets {
	private Summon current;
	private volatile PlayerBotNavigation navigation;
	private long nextAttack;
	private boolean returning;
	boolean waitingForSearchTurn() { var currentNavigation=navigation;return currentNavigation!=null && currentNavigation.waitingForSearchTurn(); }
	boolean needsFollow(Player master) {
		Summon pet=master.getSummon();
		return !master.isDead() && pet!=null && pet.isSpawned() && !pet.isDead() && !pet.isBeingReleased()
			&& pet.canPerformMove() && !pet.isCasting() && pet.getWorldId()==master.getWorldId() && pet.getInstanceId()==master.getInstanceId()
			&& (returning || PositionUtil.getDistance(pet,master)>35
				|| pet.getMode()==SummonMode.GUARD && pet.getNextSkillOrder()==null && PositionUtil.getDistance(pet,master)>4);
	}
	private Summon prepare(Player master) {
		Summon pet = master.getSummon();
		if (pet == null || pet.isDead() || !pet.isSpawned() || pet.isBeingReleased()) return null;
		if (current != pet) {
			if(navigation!=null)navigation.stop();
			current = pet; pet.enablePlayerBotMovement(); navigation = new PlayerBotNavigation(pet); nextAttack = 0; returning=false;
		}
		if (pet.getMoveController() instanceof com.aionemu.gameserver.controllers.movement.PlayerBotSummonMoveController move && move.hasFailed()) {
			release(master); return null;
		}
		if (!pet.getController().checkMasterRange()) { if(navigation!=null)navigation.stop(); return null; }
		if (pet.getWorldId() != master.getWorldId() || pet.getInstanceId() != master.getInstanceId()) { release(master); return null; }
		return pet;
	}
	/** Guard following stays alive even while the master's combat/activity loop returns early. */
	boolean follow(Player master,boolean mayMove) {
		Summon pet=prepare(master);if(pet==null)return false;
		if(master.isDead() || master.isUsingFlightTransporterOrWindstream())return false;
		double distance=PositionUtil.getDistance(pet,master);
		if(distance>35)returning=true;
		else if(distance<=5)returning=false;
		if(returning) {
			pet.clearSkillOrders();
			if(pet.isCasting())pet.getController().cancelCurrentSkill(null);
			if(pet.getMode()!=SummonMode.GUARD)SummonsService.guardMode(pet);
			if(pet.getTarget()!=null)pet.setTarget(null);
		}
		if(mayMove && pet.canPerformMove() && !pet.isCasting() && pet.getMode()==SummonMode.GUARD && pet.getNextSkillOrder()==null) {
			var hazards=PlayerBotHazards.visible(pet);navigation.hazards(hazards);
			if(PlayerBotHazards.risk(new PlayerBotNavigation.Point(pet.getX(),pet.getY(),pet.getZ()),hazards)>0)navigation.escapeHazards();
			else if(distance>4)navigation.approach(master,3);
			else navigation.stop();
		}
		return returning;
	}
	void tick(Player master, Creature enemy, boolean mayMove, boolean active, boolean withholdDamage) {
		Summon summoned=master.getSummon();
		var cast=summoned==null ? null : summoned.getCastingSkill();
		if(cast!=null && cast.getFirstTarget() instanceof Npc held && PlayerBotMarks.nativeCcMarked(master,held)) {
			summoned.clearSkillOrders(); summoned.getController().cancelCurrentSkill(null);
		}
		if(enemy instanceof Npc held && PlayerBotMarks.nativeCcMarked(master,held))enemy=null;
		if(follow(master,mayMove))return;
		Summon pet=master.getSummon();
		if(pet==null || pet.isDead() || !pet.isSpawned() || pet.isBeingReleased())return;
		if (!active) {
			pet.clearSkillOrders();
			if (pet.isCasting()) pet.getController().cancelCurrentSkill(null);
			enemy = null;
		}
		var hazards = PlayerBotHazards.visible(pet);
		navigation.hazards(hazards);
		if (mayMove && pet.canPerformMove() && PlayerBotHazards.risk(new PlayerBotNavigation.Point(pet.getX(), pet.getY(), pet.getZ()), hazards) > 0) {
			pet.clearSkillOrders(); pet.getController().cancelCurrentSkill(null);
			if (!navigation.escapeHazards()) navigation.stop();
			return;
		}
		if (pet.isCasting()) return;
		if (withholdDamage) enemy = null;
		var order = pet.getNextSkillOrder();
		if (order != null) {
			var template = DataManager.SKILL_DATA.getSkillTemplate(order.getSkillId());
			if (template == null || !order.getTarget().isSpawned() || order.getTarget().isDead()
				|| order.getTarget() instanceof Npc held && PlayerBotMarks.nativeCcMarked(master,held)
				|| withholdDamage && pet.isEnemy(order.getTarget()) && !(order.getTarget().isCasting() && PlayerBotSkills.canInterrupt(template))
				|| !PlayerBotService.allowsTarget(master, order.getTarget())
				|| order.getTarget() instanceof Npc npc && !PlayerBotEncounters.allowsAttack(pet, npc)) { pet.retrieveNextSkillOrder(); return; }
			float range = Math.max(1, template.getProperties().getFirstTargetRange());
			if (!PositionUtil.isInAttackRange(pet, order.getTarget(), range) || !GeoService.getInstance().canSee(pet, order.getTarget())) {
				if (mayMove) navigation.approach(order.getTarget(), range); else navigation.stop();
				return;
			}
			navigation.stop();
			pet.getController().useSkill(pet.retrieveNextSkillOrder());
			nextAttack = Math.max(nextAttack, System.currentTimeMillis() + Math.max(400, template.getDuration()));
			return;
		}
		if (enemy == null || !PlayerBotService.allowsTarget(master, enemy) || enemy instanceof Npc npc && !PlayerBotEncounters.allowsAttack(pet, npc)) {
			if (pet.getMode() != SummonMode.GUARD) SummonsService.guardMode(pet);
			if (pet.getTarget() != null) pet.setTarget(null);
			if (mayMove && PositionUtil.getDistance(pet, master) > 4) navigation.approach(master, 3);
			else navigation.stop();
			return;
		}
		if (pet.getTarget() != enemy) pet.setTarget(enemy);
		if (pet.getMode() != SummonMode.ATTACK) pet.getController().attackMode(enemy.getObjectId());
		float range = Math.max(1.5f, pet.getGameStats().getAttackRange().getCurrent() / 1000f);
		if (!PositionUtil.isInAttackRange(pet, enemy, range) || !GeoService.getInstance().canSee(pet, enemy)) {
			if (mayMove) navigation.approach(enemy, range); else navigation.stop();
			return;
		}
		navigation.stop();
		if (System.currentTimeMillis() >= Math.max(nextAttack, pet.getController().getNextAttackMillis()) && pet.canAttack()) {
			int speed = Math.max(300, pet.getGameStats().getAttackSpeed().getCurrent());
			pet.getController().attackTarget(enemy, Math.min(500, speed / 3), false);
			nextAttack = System.currentTimeMillis() + speed;
		}
	}
	void stop(Player master) {
		if (navigation != null) navigation.stop();
		Summon pet = master.getSummon();
		if (pet != null && !pet.isBeingReleased()) {
			pet.clearSkillOrders();
			pet.getController().cancelCurrentSkill(null);
			SummonsService.guardMode(pet); pet.setTarget(null);
		}
	}
	void release(Player master) {
		stop(master);
		if (master.getSummon() != null) SummonsService.release(master.getSummon(), UnsummonType.LOGOUT);
		current = null; navigation = null; returning=false;
	}
}
