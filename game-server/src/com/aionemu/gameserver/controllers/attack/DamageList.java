package com.aionemu.gameserver.controllers.attack;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.aionemu.gameserver.model.gameobjects.AionObject;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.player.Player;

/**
 * List of combined creature damages, grouped by their master (if present, like with Summons and summoned objects).
 */
public class DamageList {

	private final Map<Creature, DamageInfo<Creature>> damageByCreature = new HashMap<>();
	private final Set<AionObject> experienceExcluded = new HashSet<>();

	DamageList(Collection<AggroInfo> aggroInfos, Creature owner) {
		long wardenDamage=0;
		for (AggroInfo aggroInfo : aggroInfos) {
			if (aggroInfo.getDamage() <= 0)
				continue;
			Creature attackerMaster = aggroInfo.getAttacker().getMaster();
			// The native aggro entry remembers which hits were public Warden aid.
			int assistance = owner instanceof com.aionemu.gameserver.model.gameobjects.Npc ? aggroInfo.getWardenDamage() : 0;
			wardenDamage += assistance;
			int claimDamage = aggroInfo.getDamage() - assistance;
			if (claimDamage <= 0)
				continue;
			// Don't include damage from creatures outside the known list.
			if (!owner.getKnownList().knows(attackerMaster))
				continue;
			damageByCreature.computeIfAbsent(attackerMaster, DamageInfo::new).addDamage(claimDamage,
				assistance > 0 ? aggroInfo.getFirstNonWardenDamageTime() : aggroInfo.getFirstDamageTime());
		}
		if (wardenDamage>0 && com.aionemu.gameserver.configs.main.PlayerBotWardenConfig.ENABLED) {
			var teams = new HashMap<AionObject, Long>();
			for (var entry : damageByCreature.entrySet())
				if (entry.getKey() instanceof Player player)
					teams.merge(rewardOwner(player), (long) entry.getValue().getDamage(), Long::sum);
			for (var entry : teams.entrySet())
				if (!com.aionemu.gameserver.services.playerbot.PlayerBotWardens.fair(entry.getValue(), wardenDamage,
					com.aionemu.gameserver.configs.main.PlayerBotWardenConfig.FAIR_DAMAGE))
					experienceExcluded.add(entry.getKey());
			// Keep the complete native damage/loot list. Only XP and Road Bounty eligibility change.
			for (Creature attacker : damageByCreature.keySet())
				if (attacker instanceof Player player && !isExperienceEligible(player))
					com.aionemu.gameserver.services.playerbot.PlayerBotWardens.unfair(player);
		}
	}

	private static AionObject rewardOwner(AionObject attacker) {
		AionObject team = attacker instanceof Player player && player.getCurrentTeam() != null ? player.getCurrentTeam() : attacker;
		// Native league reward distribution visits every member alliance. Their fair share is combined too.
		return team instanceof com.aionemu.gameserver.model.team.alliance.PlayerAlliance alliance && alliance.isInLeague() ? alliance.getLeague() : team;
	}

	public boolean isExperienceEligible(AionObject attacker) {
		return !experienceExcluded.contains(rewardOwner(attacker));
	}

	public TeamDamageList toTeamDamages() {
		return new TeamDamageList(this);
	}

	public Collection<DamageInfo<Creature>> getCreatureDamages() {
		return damageByCreature.values();
	}

	public DamageInfo<Creature> getMostDamage() {
		return damageByCreature.values().stream().max(Comparator.comparingInt(DamageInfo::getDamage)).orElse(null);
	}

	public int getTotalDamage() {
		return damageByCreature.values().stream().mapToInt(DamageInfo::getDamage).sum();
	}

}
