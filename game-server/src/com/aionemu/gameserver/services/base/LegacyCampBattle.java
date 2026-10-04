package com.aionemu.gameserver.services.base;

import java.util.concurrent.Future;
import com.aionemu.gameserver.ai.AIState;
import com.aionemu.gameserver.ai.manager.EmoteManager;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.templates.spawns.basespawns.BaseSpawnTemplate;
import com.aionemu.gameserver.services.BaseService;
import com.aionemu.gameserver.spawnengine.SpawnHandlerType;
import com.aionemu.gameserver.utils.PositionUtil;
import com.aionemu.gameserver.utils.ThreadPoolManager;
import com.aionemu.gameserver.world.geo.GeoService;

/** The eight 4.0-era home-zone camps. Uses native movement, hate, damage and capture. */
public final class LegacyCampBattle {
	private LegacyCampBattle() {}
	public static boolean legacy(int world) {
		return world == 210020000 || world == 210040000 || world == 220020000 || world == 220040000;
	}
	public static boolean specificEnemy(Creature first, Creature second) {
		if (!(first instanceof Npc npc) || !(second instanceof Npc other) || npc.isFlag() || other.isFlag()
			|| !(npc.getSpawn() instanceof BaseSpawnTemplate spawn) || !legacy(npc.getWorldId())) return false;
		if (spawn.getHandlerType() != SpawnHandlerType.SENTINEL && spawn.getHandlerType() != SpawnHandlerType.BOSS
			&& spawn.getHandlerType() != SpawnHandlerType.ATTACKER) return false;
		return DataManager.TRIBE_RELATIONS_DATA.isAggressiveRelation(npc.getTribe(), other.getTribe());
	}
	public static Npc captain(Npc attacker) {
		if (!(attacker.getSpawn() instanceof BaseSpawnTemplate spawn) || !legacy(attacker.getWorldId())
			|| spawn.getHandlerType() != SpawnHandlerType.ATTACKER || !attacker.isSpawned() || attacker.isDead()) return null;
		Npc[] selected = {null};
		attacker.getKnownList().forEachNpc(n -> {
			if (!n.isDead() && n.isSpawned() && n.getSpawn() instanceof BaseSpawnTemplate s && s.getId() == spawn.getId()
				&& s.getHandlerType() == SpawnHandlerType.BOSS && s.getOccupier() != spawn.getOccupier()
				&& attacker.isEnemy(n)) selected[0] = n;
		});
		return selected[0];
	}
	public static void beginAssault(Npc attacker) {
		if (!(attacker.getSpawn() instanceof BaseSpawnTemplate spawn) || !legacy(attacker.getWorldId())
			|| spawn.getHandlerType() != SpawnHandlerType.ATTACKER) return;
		var base = BaseService.getInstance().getActiveBase(spawn.getId());
		Future<?>[] task = new Future<?>[1];
		task[0] = ThreadPoolManager.getInstance().scheduleAtFixedRate(() -> {
			if (!attacker.isSpawned() || attacker.isDead() || base == null || base.isStopped()
				|| BaseService.getInstance().getActiveBase(spawn.getId()) != base) {
				if (task[0] != null) task[0].cancel(false);
				return;
			}
			advance(attacker);
		}, 1000, 1000);
	}
	/** Returns whether this tick submitted native movement or native hostility. */
	public static boolean advance(Npc attacker) {
		if (!attacker.getPosition().isMapRegionActive() || attacker.isCasting() || !attacker.canPerformMove()) return false;
		var ai = attacker.getAi();
		if (ai.getState() != AIState.IDLE && ai.getState() != AIState.WALKING) return false;
		Npc boss = captain(attacker);
		if (boss == null) return false;
		var geo = GeoService.getInstance();
		if (PositionUtil.isInRange(attacker, boss, Math.max(8, attacker.getAggroRange())) && geo.canSee(attacker, boss)) {
			attacker.getMoveController().abortMove();
			attacker.getAggroList().addHate(boss, 1);
			return true;
		}
		if (attacker.getMoveController().isInMove()) return false;
		float direction = (float) Math.toDegrees(Math.atan2(boss.getY() - attacker.getY(), boss.getX() - attacker.getX()));
		var point = geo.findMovementCollision(attacker, direction, 4);
		if (point == null || !Float.isFinite(point.getZ()) || PositionUtil.getDistance(attacker, point.getX(), point.getY(), point.getZ()) < .5) return false;
		ai.setStateIfNot(AIState.WALKING);
		EmoteManager.emoteStartWalking(attacker);
		return attacker.getMoveController().moveToPoint(point.getX(), point.getY(), point.getZ());
	}
}
