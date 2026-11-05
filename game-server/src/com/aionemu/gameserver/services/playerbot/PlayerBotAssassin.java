/* AssassinationRogueStrategy.cpp at mod-playerbots
 * 037c01418b5d01506917a3db9b44fd56ac5f965c: mature/urgent finishers precede
 * ordinary builders. GPL-2.0-or-later; third-party/playerbots/AUTHORS.md.
 * Aion observed rune stacks replace WoW combo points and energy. */
package com.aionemu.gameserver.services.playerbot;

import com.aionemu.gameserver.model.PlayerClass;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.skillengine.model.SkillTemplate;
import com.aionemu.gameserver.skillengine.properties.*;

/** Keep ordinary chain fitness within its band so ready rune finishers and defenses win. */
final class PlayerBotAssassin {
 static String strategy(PlayerClass pc){return pc==PlayerClass.ASSASSIN ? "assassin runes" : PlayerBotGladiator.strategy(pc);}
 static double adapter(Player bot,PlayerBotRules.Role role,PlayerBotSkills.Entry entry,com.aionemu.gameserver.model.gameobjects.Creature enemy,double fallback) {
  if(bot.getPlayerClass()!=PlayerClass.ASSASSIN)return Double.NaN;
  return switch(PlayerBotSkillAdapters.family(entry.template())) {
   case HIDE,MOVEMENT -> fallback+2;
   case PROC -> "AS_BACKFOCUS".equals(entry.template().getGroup()) && !com.aionemu.gameserver.utils.PositionUtil.isBehind(bot,enemy) ? 0 : fallback+1;
   // Eye of Wrath's critical penalty is worthwhile only with time to use its opening hit.
   case ONETIME -> PlayerBotDefense.health(bot)>=50 && PlayerBotDefense.health(enemy)>=50 ? fallback+3 : 0;
   default -> Double.NaN;
  };
 }
 static double chain(Player bot,PlayerBotSkills.Entry entry,SkillTemplate skill,double fitness){
  var p=skill.getProperties();
  if(bot.getPlayerClass()!=PlayerClass.ASSASSIN || skill.isCharge() || skill.isPassive()
   || !PlayerBotSkills.followUp(entry) || p==null || p.getTargetRelation()!=TargetRelationAttribute.ENEMY
   || p.getTargetType()!=TargetRangeAttribute.ONLYONE)return Double.NaN;
  // Existing Offense.finisher owns maturity/expiry/HP and builder availability.
  // Its 25/24 bands must precede ordinary chains; existing defenses at 29 stay higher.
  // Preserve fitness as a tie within the chain band, not across policy bands.
  return PlayerBotEngine.HIGH+3+Math.clamp(Double.isFinite(fitness)?fitness:0,0,7)/8;
 }
 private PlayerBotAssassin(){}
}
