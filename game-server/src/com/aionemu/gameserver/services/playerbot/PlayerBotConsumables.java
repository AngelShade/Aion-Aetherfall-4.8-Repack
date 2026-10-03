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
	static List<Item> candidates(Player bot) {
		return bot.getInventory().getItems().stream().filter(item -> {
			var actions = item.getItemTemplate().getActions();
			if (item.getItemCount() <= 0 || item.isEquipped() || bot.hasCooldown(item) || actions == null
				|| actions.getItemActions().size() != 1 || actions.getSkillUseAction() == null) return false;
			var template = DataManager.SKILL_DATA.getSkillTemplate(actions.getSkillUseAction().getSkillId());
			return isRecovery(template) && needed(bot, template);
		}).sorted(java.util.Comparator.comparingInt((Item item) -> item.getItemTemplate().getLevel()).reversed()).toList();
	}

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

	private static boolean needed(Player bot, SkillTemplate template) {
		return template.hasAnyEffect(EffectType.HEAL, EffectType.HEALINSTANT)
			&& bot.getLifeStats().getCurrentHp() < bot.getLifeStats().getMaxHp() * 0.45
			|| template.hasAnyEffect(EffectType.MPHEAL, EffectType.MPHEALINSTANT)
			&& bot.getLifeStats().getCurrentMp() < bot.getLifeStats().getMaxMp() * 0.25
			|| template.hasAnyEffect(EffectType.DISPELDEBUFF, EffectType.DISPELDEBUFFMENTAL, EffectType.DISPELDEBUFFPHYSICAL)
			&& bot.getEffectController().getAbnormalEffects().stream().anyMatch(e -> e.getSkillTemplate().getTargetSlot()
				== com.aionemu.gameserver.skillengine.model.SkillTargetSlot.DEBUFF);
	}

	static boolean use(Player bot, Item item) {
		if (bot.isCasting() || item != bot.getInventory().getItemByObjId(item.getObjectId()) || !PlayerRestrictions.canUseItem(bot, item)) return false;
		var action = item.getItemTemplate().getActions().getSkillUseAction();
		var template = DataManager.SKILL_DATA.getSkillTemplate(action.getSkillId());
		if (!isRecovery(template) || !needed(bot, template)) return false;
		bot.setTarget(bot);
		if (!action.canAct(bot, item, null)) return false;
		var skill = SkillEngine.getInstance().getSkill(bot, action.getSkillId(), action.getLevel(), bot, item.getItemTemplate());
		if (skill == null) return false;
		bot.getMoveController().abortMove();
		bot.getObserveController().notifyItemuseObservers(item);
		skill.setItemObjectId(item.getObjectId());
		skill.setClientHitTime(Math.max(0, Math.round(DataManager.MOTION_DATA.calculateAnimationTimeUntilFirstHit(bot, skill))));
		return skill.useSkill();
	}
	private PlayerBotConsumables() {}
}
