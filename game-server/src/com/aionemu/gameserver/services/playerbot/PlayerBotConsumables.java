package com.aionemu.gameserver.services.playerbot;

import java.util.List;

import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.gameobjects.Item;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.restrictions.PlayerRestrictions;
import com.aionemu.gameserver.skillengine.SkillEngine;
import com.aionemu.gameserver.skillengine.effect.EffectType;
import com.aionemu.gameserver.skillengine.model.SkillTemplate;
import com.aionemu.gameserver.skillengine.properties.FirstTargetAttribute;

/** Uses the companion's existing recovery supplies through ordinary item costs and cooldowns. */
final class PlayerBotConsumables {
	static List<Item> candidates(Player bot) { return PlayerBotSupplies.candidates(bot); }

	static boolean isRecovery(SkillTemplate template) {
		if (template == null || template.getProperties() == null || template.getEffects() == null
			|| template.getProperties().getFirstTarget() != FirstTargetAttribute.ME) return false;
		return !template.getEffects().getEffects().isEmpty() && template.getEffects().getEffects().stream().allMatch(effect ->
			effect.getValue() >= 0 && (effect instanceof com.aionemu.gameserver.skillengine.effect.HealEffect
				|| effect instanceof com.aionemu.gameserver.skillengine.effect.HealInstantEffect
				|| effect instanceof com.aionemu.gameserver.skillengine.effect.MPHealEffect
				|| effect instanceof com.aionemu.gameserver.skillengine.effect.MPHealInstantEffect
				|| effect instanceof com.aionemu.gameserver.skillengine.effect.DispelDebuffEffect
				|| effect instanceof com.aionemu.gameserver.skillengine.effect.DispelDebuffMentalEffect
				|| effect instanceof com.aionemu.gameserver.skillengine.effect.DispelDebuffPhysicalEffect));
	}

	private static boolean needed(Player bot, SkillTemplate template) { return PlayerBotSupplies.needed(bot, template); }

	static boolean use(Player bot, Item item) { return PlayerBotSupplies.use(bot, item); }

	private PlayerBotConsumables() {}
}
