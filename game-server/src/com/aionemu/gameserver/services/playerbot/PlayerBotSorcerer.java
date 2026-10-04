/* Adapted from mod-playerbots 037c01418b5d01506917a3db9b44fd56ac5f965c:
 * Mage/Strategy/FireMageStrategy.cpp, GenericMageStrategy.cpp and MageTriggers.cpp.
 * GPL-2.0-or-later; upstream contributors: third-party/playerbots/AUTHORS.md.
 * Native chains, learned effects and MP replace WoW Hot Streak, spell IDs and gems. */
package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import com.aionemu.gameserver.model.PlayerClass;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.stats.container.StatEnum;
import com.aionemu.gameserver.skillengine.effect.*;
import com.aionemu.gameserver.skillengine.model.SkillTemplate;
import static com.aionemu.gameserver.services.playerbot.PlayerBotRules.*;

/** One learned single-target class strategy. Never grants skills or modifies a build. */
public final class PlayerBotSorcerer {
 static boolean applies(PlayerClass pc){return pc==PlayerClass.SORCERER || pc==PlayerClass.MAGE;}
 static String strategy(PlayerClass pc){return applies(pc)?"sorcerer single target":"class skills";}
 static boolean singleTarget(SkillTemplate skill){return skill.getProperties()!=null
  && skill.getProperties().getTargetType()==com.aionemu.gameserver.skillengine.properties.TargetRangeAttribute.ONLYONE;}
 static boolean offensiveBoost(SkillTemplate skill) {
  if(skill==null || skill.getEffects()==null)return false;
  return skill.getEffects().getEffects().stream().filter(e->e.getChange()!=null)
   .flatMap(e->e.getChange().stream()).anyMatch(c->c.getValue()>0 && Set.of(
    StatEnum.BOOST_MAGICAL_SKILL,StatEnum.BOOST_SPELL_ATTACK,StatEnum.BOOST_CASTING_TIME_SKILL,StatEnum.BOOST_CASTING_TIME_ATTACK).contains(c.getStat()));
 }
 static boolean vulnerability(SkillTemplate skill) {
  if(skill.getEffects()==null)return false;
  return skill.getEffects().getEffects().stream().filter(e->e instanceof StatdownEffect && e.getChange()!=null)
   .flatMap(e->e.getChange().stream()).anyMatch(c->c.getValue()<0 && Set.of(
    StatEnum.MAGICAL_RESIST,StatEnum.FIRE_RESISTANCE,StatEnum.WATER_RESISTANCE,
    StatEnum.WIND_RESISTANCE,StatEnum.EARTH_RESISTANCE).contains(c.getStat()));
 }
 static boolean sameFamily(SkillTemplate a,SkillTemplate b) {
  return a.getSkillId()==b.getSkillId() || a.getStack()!=null && !a.getStack().isBlank() && a.getStack().equals(b.getStack())
   || a.getConflictId()!=0 && a.getConflictId()==b.getConflictId();
 }
 static double band(SkillTemplate skill,boolean chain,boolean moving,double health,
                    boolean maintainDot,boolean maintainVulnerability,double fitness) {
  double tie=Math.clamp(Double.isFinite(fitness)?fitness:0,0,7)/100;
  if(chain)return 25+tie; // Fire Mage Hot Streak purpose: use an admitted native proc before fillers.
  if(maintainVulnerability)return 19+tie;
  if(maintainDot)return 18.5+tie;
  if(!PlayerBotOffense.instant(skill))return 0;
  // Aion has no Fire Mage school-immunity/execute spell copy. Native feasibility
  // and damage efficiency choose among learned fillers; quick direct damage finishes.
  return (skill.getDuration()==0 ? health<25 ? 5.4 : moving ? 5.3 : 5.1 : moving ? 5.2 : 5.3)+tie;
 }
 static double damage(Player bot,PlayerBotSkills.Entry entry,Creature target,List<PlayerBotOffense.Active> effects,double fitness) {
  var skill=PlayerBotSkills.actualTemplate(bot,entry);if(skill==null)return 0;
  double health=PlayerBotDefense.health(target);
  boolean periodic=PlayerBotOffense.periodic(skill),maintain=periodic && PlayerBotOffense.refresh(skill,entry.level(),effects,health);
  if(periodic && !maintain && !PlayerBotOffense.instant(skill))return 0;
  boolean debuff=vulnerability(skill) && effects.stream().noneMatch(e->sameFamily(skill,e.skill()));
  // Native chain availability is checked again by the final CastAction.
  return band(skill,PlayerBotSkills.followUp(entry) && PlayerBotSkills.chainAvailable(bot,entry),
   bot.getMoveController().isInMove(),health,maintain,debuff,fitness);
 }
 /** NaN leaves every unrelated class/role/support action on the installed path. */
 static double support(Player bot,Role role,PlayerBotSkills.Entry entry,Creature recipient,boolean combat) {
  if(!applies(bot.getPlayerClass()) || !combat || role!=Role.RANGED || recipient!=bot)return Double.NaN;
  if(entry.kind()==SkillKind.MANA)
   // Upstream Evocation is band 90. Normalize below Aion encounter actions (60+)
   // so channel setup cannot outrank hazard escape/protection. Native <40% MP admission remains.
   return PlayerBotDefense.health(bot)<35 ? 21 : PlayerBotEngine.ENCOUNTER-1;
  if(entry.kind()!=SkillKind.BUFF || !offensiveBoost(entry.template()))return Double.NaN;
  // Generic Fire/Frostfire boost ordering. Buff stacking, health/MP admission,
  // learned ranks, cooldowns and costs stay in the native recipient/cast gates.
  return entry.template().hasAnyEffect(EffectType.BOOSTSPELLATTACK)
   || entry.template().getEffects().getEffects().stream().filter(e->e.getChange()!=null)
     .flatMap(e->e.getChange().stream()).anyMatch(c->c.getStat()==StatEnum.BOOST_MAGICAL_SKILL && c.getValue()>0)?18:17.5;
 }
 private PlayerBotSorcerer(){}
}
