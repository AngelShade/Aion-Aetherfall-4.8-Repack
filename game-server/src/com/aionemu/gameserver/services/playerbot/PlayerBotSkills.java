package com.aionemu.gameserver.services.playerbot;

import static com.aionemu.gameserver.services.playerbot.PlayerBotRules.SkillKind.*;
import static com.aionemu.gameserver.skillengine.effect.EffectType.*;

import java.util.List;

import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.services.playerbot.PlayerBotRules.SkillKind;
import com.aionemu.gameserver.skillengine.model.SkillTemplate;
import com.aionemu.gameserver.skillengine.properties.FirstTargetAttribute;
import com.aionemu.gameserver.skillengine.properties.Properties;
import com.aionemu.gameserver.skillengine.properties.TargetRelationAttribute;

/** Reads learned Aion skills and their authoritative effect/target metadata. No WoW spell IDs. */
public final class PlayerBotSkills {
	public record Entry(SkillTemplate template, int level, SkillKind kind, float range) {}

	public static List<Entry> read(Player bot) {
		return bot.getSkillList().getAllSkills().stream().map(entry -> {
			SkillTemplate t = DataManager.SKILL_DATA.getSkillTemplate(entry.getSkillId());
			if (t == null || t.isPassive() || t.isProvoked() || t.getProperties() == null)
				return null;
			Properties p = t.getProperties();
			float range = p.getFirstTargetRange();
			if (p.getFirstTarget() == FirstTargetAttribute.ME && p.getTargetRelation() == TargetRelationAttribute.ENEMY)
				range = Math.max(range, Math.max(p.getEffectiveRange(), p.getEffectiveDist()));
			if (p.isAddWeaponRange())
				range += bot.getGameStats().getAttackRange().getCurrent() / 1000f;
			return new Entry(t, entry.getSkillLevel(), classify(t), Math.max(1, range));
		}).filter(e -> e != null && e.kind() != UNSUPPORTED)
			.sorted(java.util.Comparator.comparingInt(e -> e.template().getSkillId())).toList();
	}

	public static SkillKind classify(SkillTemplate t) {
		if (t.isCharge()) {
			SkillTemplate released = chargedTemplate(t);
			// Native ChargeSkill does not copy ground coordinates from its starting cast.
			return released == null || released == t || released.isCharge() || released.getProperties() == null
				|| released.getProperties().getFirstTarget() == FirstTargetAttribute.POINT ? UNSUPPORTED : classify(released);
		}
		Properties p = t.getProperties();
		if (p == null || t.getEffects() == null || p.getFirstTarget() == null)
			return UNSUPPORTED;
		if (p.getFirstTarget() == FirstTargetAttribute.TARGET_MYPARTY_NONVISIBLE)
			return UNSUPPORTED;
		// Travel, summons and shape changes require dedicated player/session adapters.
		if (t.hasAnyEffect(RETURN, RETURNPOINT, RECALLINSTANT, TARGETTELEPORT, RANDOMMOVELOC,
			SUMMONGROUPGATE, SUMMONBINDINGGROUPGATE, SUMMONHOUSEGATE, SUMMONTOTEM, SUMMONTRAP, SHAPECHANGE, PETORDERUNSUMMON))
			return UNSUPPORTED;
		if (t.hasAnyEffect(com.aionemu.gameserver.skillengine.effect.EffectType.SUMMON))
			return p.getFirstTarget() == FirstTargetAttribute.ME ? SkillKind.SUMMON : UNSUPPORTED;
		if (t.hasAnyEffect(PETORDERUSEULTRASKILL)) return PET_ORDER;
		if (t.hasAnyEffect(RIDEROBOT)) return MODE;
		if (t.hasEvadeEffect()) return RECOVERY;
		boolean hostile = p.getTargetRelation() == TargetRelationAttribute.ENEMY;
		if (hostile) {
			if (t.hasAnyEffect(HOSTILEUP, PROVOKER)) return TAUNT;
			if (t.hasAnyEffect(SPELLATTACK, SPELLATTACKINSTANT, SKILLATTACKINSTANT, SKILLATKDRAININSTANT,
				SPELLATKDRAIN, SPELLATKDRAININSTANT, BLEED, POISON, SIGNET, SIGNETBURST, CARVESIGNET,
				STATDOWN, DISPELBUFF, DISPELNPCBUFF)) return DAMAGE;
			return canInterrupt(t) || t.hasAnyEffect(ROOT) ? CONTROL : UNSUPPORTED;
		}
		if (t.hasResurrectEffect()) return SkillKind.RESURRECT;
		if (t.hasAnyEffect(DISPELDEBUFF, DISPELDEBUFFMENTAL, DISPELDEBUFFPHYSICAL)) return CLEANSE;
		if (t.hasAnyEffect(HEALINSTANT, com.aionemu.gameserver.skillengine.effect.EffectType.HEAL, CASEHEAL)) return SkillKind.HEAL;
		if (t.hasAnyEffect(MPHEALINSTANT, MPHEAL)) return MANA;
		if (t.hasAnyEffect(SHIELD, MPSHIELD, LIMITEDREDUCEDAMAGE, PROTECT, ALWAYSDODGE, ALWAYSPARRY, ALWAYSBLOCK)) return DEFENSE;
		return t.hasAnyEffect(STATUP, STATBOOST, WEAPONSTATUP, WEAPONSTATBOOST, AURA, BOOSTHEAL,
			BOOSTSPELLATTACK, BOOSTHATE, BOOSTSKILLCASTINGTIME, REFLECTOR, REBIRTH) ? BUFF : UNSUPPORTED;
	}

	public static SkillTemplate chargedTemplate(SkillTemplate start) {
		var condition = start.getSkillChargeCondition();
		if (condition == null || DataManager.SKILL_CHARGE_DATA == null) return null;
		var stages = DataManager.SKILL_CHARGE_DATA.getChargedSkillEntry(condition.getValue());
		if (stages == null || stages.getSkills() == null || stages.getSkills().isEmpty()) return null;
		return DataManager.SKILL_DATA.getSkillTemplate(stages.getSkills().getLast().getId());
	}

	public static SkillTemplate actualTemplate(Player bot, Entry entry) {
		if (entry.template().isCharge()) return chargedTemplate(entry.template());
		if (entry.kind() != PET_ORDER) return entry.template();
		if (bot.getSummon() == null || DataManager.PET_SKILL_DATA == null) return null;
		Integer id = DataManager.PET_SKILL_DATA.findPetOrderSkill(entry.template().getSkillId(), bot.getSummon().getNpcId());
		return id == null ? null : DataManager.SKILL_DATA.getSkillTemplate(id);
	}

	public static boolean canInterrupt(SkillTemplate template) {
		return template.hasAnyEffect(SILENCE, STUN, SLEEP, FEAR, PARALYZE, STUMBLE, STAGGER);
	}

	public static boolean chainAvailable(Player bot, Entry entry) {
		var chain = entry.template().getChainCondition();
		return chain == null || chain.isAvailable(bot.getChainSkills());
	}

	public static boolean followUp(Entry entry) {
		var chain = entry.template().getChainCondition();
		return chain != null && chain.isFollowUp();
	}

	public static boolean canCleanse(Entry entry, com.aionemu.gameserver.model.gameobjects.Creature target) {
		return entry.template().getEffects().getEffects().stream()
			.filter(e -> e instanceof com.aionemu.gameserver.skillengine.effect.AbstractDispelEffect)
			.map(e -> (com.aionemu.gameserver.skillengine.effect.AbstractDispelEffect) e)
			.anyMatch(e -> target.getEffectController().canDispelDebuff(e, entry.level()));
	}

	/** Planning checks resources without paying them or advancing a chain. Native casting validates again. */
	public static boolean canPlan(Player bot, Entry entry, com.aionemu.gameserver.model.gameobjects.Creature target) {
		return canPlan(bot, entry, target, true);
	}
	static boolean canPlan(Player bot, Entry entry, com.aionemu.gameserver.model.gameobjects.Creature target, boolean checkPosition) {
		var skill = com.aionemu.gameserver.skillengine.SkillEngine.getInstance().getSkillFor(bot, entry.template(), target);
		if (skill == null) return false;
		if (entry.kind() == PET_ORDER && actualTemplate(bot, entry) == null) return false;
		var counter = entry.template().getCounterSkill();
		if (counter != null && bot.getLastCounterSkill(counter) + 5000 < System.currentTimeMillis()) return false;
		var end = entry.template().getEndConditions();
		if (end != null && !end.canValidate(skill)) return false;
		var actions = entry.template().getActions();
		if (actions != null && actions.getActions().stream().anyMatch(a -> !a.canAct(skill))) return false;
		if (entry.template().getRideRobotCondition() != null && !bot.isInRobotMode()) return false;
		var start = entry.template().getStartconditions();
		if (start != null) for (var condition : start.getConditions()) {
			if (!checkPosition && (condition instanceof com.aionemu.gameserver.skillengine.condition.FrontCondition
				|| condition instanceof com.aionemu.gameserver.skillengine.condition.BackCondition)) continue;
			if (condition instanceof com.aionemu.gameserver.skillengine.condition.WeaponCondition
				|| condition instanceof com.aionemu.gameserver.skillengine.condition.LeftHandCondition
				|| condition instanceof com.aionemu.gameserver.skillengine.condition.FormCondition
				|| condition instanceof com.aionemu.gameserver.skillengine.condition.RaceCondition
				|| condition instanceof com.aionemu.gameserver.skillengine.condition.DpCondition
				|| condition instanceof com.aionemu.gameserver.skillengine.condition.AbnormalStateCondition
				|| condition instanceof com.aionemu.gameserver.skillengine.condition.TargetCondition
				|| condition instanceof com.aionemu.gameserver.skillengine.condition.OnFlyCondition
				|| condition instanceof com.aionemu.gameserver.skillengine.condition.NoFlyingCondition
				|| condition instanceof com.aionemu.gameserver.skillengine.condition.TargetFlyingCondition
				|| condition instanceof com.aionemu.gameserver.skillengine.condition.SelfFlyingCondition
				|| condition instanceof com.aionemu.gameserver.skillengine.condition.CombatCheckCondition
				|| condition instanceof com.aionemu.gameserver.skillengine.condition.FrontCondition
				|| condition instanceof com.aionemu.gameserver.skillengine.condition.BackCondition)
				if (!condition.validate(skill)) return false;
		}
		return true;
	}
	static boolean requiresBack(Entry entry) {
		return entry.template().getStartconditions() != null && entry.template().getStartconditions().getConditions().stream()
			.anyMatch(c -> c instanceof com.aionemu.gameserver.skillengine.condition.BackCondition);
	}

	private PlayerBotSkills() {}
}
