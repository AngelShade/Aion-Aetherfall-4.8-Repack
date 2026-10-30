package com.aionemu.gameserver.services.playerbot;

import java.util.Comparator;
import java.util.List;

import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.skillengine.effect.DispelBuffEffect;
import com.aionemu.gameserver.skillengine.effect.DispelNpcBuffEffect;
import com.aionemu.gameserver.skillengine.effect.HostileUpEffect;
import com.aionemu.gameserver.skillengine.model.SkillTemplate;
import com.aionemu.gameserver.skillengine.properties.TargetRangeAttribute;
import com.aionemu.gameserver.utils.PositionUtil;

import static com.aionemu.gameserver.services.playerbot.PlayerBotRules.*;

/** Native Dark Poeta handlers/skill data own mechanics; this adapter reacts to visible, engaged encounters. */
final class PlayerBotDarkPoeta {

	static final int MAP = 300040000;

	private static boolean controller(Npc npc) {
		return npc.getNpcId() >= 700439 && npc.getNpcId() <= 700447;
	}

	private static int bossId(int npc) {
		return switch (npc) {
			case 700439, 700440, 700441 -> 214849;
			case 700442, 700443, 700444 -> 214850;
			case 700445, 700446, 700447 -> 214851;
			case 281150, 281334 -> 214894;
			case 281245 -> 214904;
			case 281258, 281259 -> 215280;
			case 281267, 281268 -> 215281;
			default -> 0;
		};
	}

	static boolean mechanicEnemy(Player bot, Npc npc, List<Player> party) {
		if (bot.getWorldId() != MAP || npc.getWorldMapInstance() != bot.getWorldMapInstance()
			|| npc.isDead() || !PlayerBotTargetEligibility.visible(bot, npc)) return false;
		int boss = bossId(npc.getNpcId());
		return boss != 0 && npc.getCreator() instanceof Npc creator && creator.getNpcId() == boss
			&& !creator.isDead() && creator.getWorldMapInstance() == bot.getWorldMapInstance()
			&& PlayerBotTargetEligibility.visible(bot, creator) && party.stream().anyMatch(p -> creator.getAggroList().isHating(p)
				|| p.getSummon() != null && creator.getAggroList().isHating(p.getSummon()));
	}

	static boolean attackable(Npc npc) {
		return npc.getWorldId() != MAP || npc.getNpcId() < 214849 || npc.getNpcId() > 214851
			|| !npc.getEffectController().hasAbnormalEffect(18556);
	}

	private static boolean mirror(Npc npc) {
		return controller(npc) && (npc.getEffectController().hasAbnormalEffect(18553)
			|| npc.getEffectController().hasAbnormalEffect(18554) || npc.getEffectController().hasAbnormalEffect(18555));
	}

	static boolean allowsAttack(Creature actor, Npc npc) {
		// AttackShieldObserver uses body distance and a nine-metre inner safe range.
		return npc.getWorldId() != MAP || attackable(npc) && (!mirror(npc) || PositionUtil.isInRange(actor, npc, 8, false));
	}

	static boolean allowsSkill(Creature actor, Npc npc, SkillTemplate skill) {
		if (npc.getWorldId() != MAP) return true;
		// A DoT could keep firing after the actor leaves the mirror's safe range.
		if (mirror(npc) && PlayerBotOffense.periodic(skill)) return false;
		if (allowsAttack(actor, npc)) return true;
		if (skill.getProperties() == null || skill.getProperties().getTargetType() != TargetRangeAttribute.ONLYONE
			|| PlayerBotEnemyUtility.damagePayload(skill) || skill.getEffects() == null || skill.getEffects().getEffects().isEmpty()) return false;
		// Keep native taunt and pure dispel available while a boss is shielded.
		return skill.getEffects().getEffects().stream().allMatch(e -> e.getClass() == HostileUpEffect.class)
			|| skill.getEffects().getEffects().stream().allMatch(e -> e.getClass() == DispelBuffEffect.class || e.getClass() == DispelNpcBuffEffect.class);
	}

	static Npc target(Player bot, Role role, int focus, List<Npc> enemies) {
		// Tanks retain native rescue/hate selection. Explicit commands and marks retain priority.
		if (bot.getWorldId() != MAP || role == Role.TANK || focus != 0) return null;
		return enemies.stream().filter(n -> controller(n) || bossId(n.getNpcId()) != 0)
			.min(Comparator.comparingInt((Npc n) -> controller(n) ? switch ((n.getNpcId() - 700439) % 3) {
				case 1 -> 0; // Defence: remove Marabata Barrier first.
				case 0 -> 1; // Attack: remove Torrent of Wrath.
				default -> 2;
			} : 3).thenComparingDouble(n -> PositionUtil.getDistance(bot, n)).thenComparingInt(Npc::getObjectId)).orElse(null);
	}

	static boolean approachMirror(Player bot, Npc target) {
		return bot.getWorldId() == MAP && target != null && target.getWorldMapInstance() == bot.getWorldMapInstance()
			&& !target.isDead() && PlayerBotTargetEligibility.visible(bot, target) && mirror(target)
			&& !PositionUtil.isInRange(bot, target, 7, false);
	}

	private PlayerBotDarkPoeta() { }
}
