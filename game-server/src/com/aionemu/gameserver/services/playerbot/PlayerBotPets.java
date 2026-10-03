package com.aionemu.gameserver.services.playerbot;

import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.gameobjects.Npc;
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
	private PlayerBotNavigation navigation;
	private long nextAttack;
	void tick(Player master, Npc enemy, boolean mayMove, boolean active, boolean withholdDamage) {
		Summon pet = master.getSummon();
		if (pet == null || pet.isDead() || !pet.isSpawned() || pet.isBeingReleased()) return;
		if (current != pet) {
			current = pet; pet.enablePlayerBotMovement(); navigation = new PlayerBotNavigation(pet); nextAttack = 0;
		}
		if (pet.getMoveController() instanceof com.aionemu.gameserver.controllers.movement.PlayerBotSummonMoveController move && move.hasFailed()) {
			release(master); return;
		}
		if (pet.getWorldId() != master.getWorldId() || pet.getInstanceId() != master.getInstanceId()) { release(master); return; }
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
			nextAttack = System.currentTimeMillis() + Math.max(400, template.getDuration());
			return;
		}
		if (enemy == null || !PlayerBotService.allowsTarget(master, enemy) || !PlayerBotEncounters.allowsAttack(pet, enemy)) {
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
		if (System.currentTimeMillis() >= nextAttack && pet.canAttack()) {
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
		current = null; navigation = null;
	}
}
