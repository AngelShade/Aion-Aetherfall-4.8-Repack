/* Purpose adapted from mod-playerbots 037c01418b5d01506917a3db9b44fd56ac5f965c:
 * Hunter/Strategy/MarksmanshipHunterStrategy.cpp, GenericHunterStrategy.cpp and
 * HunterTriggers.cpp (Ranger, Gunner), Priest shadow/holy strategies (Songweaver)
 * and Warrior arms/fury strategies (Aethertech). GPL-2.0-or-later; upstream
 * contributors: third-party/playerbots/AUTHORS.md. Aion native chains, effects,
 * charge stages and stat changes replace WoW procs, auras and spell IDs. */
package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import com.aionemu.gameserver.model.PlayerClass;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.stats.container.StatEnum;
import com.aionemu.gameserver.skillengine.effect.*;
import com.aionemu.gameserver.skillengine.model.SkillTemplate;
import com.aionemu.gameserver.utils.PositionUtil;
import static com.aionemu.gameserver.services.playerbot.PlayerBotRules.*;

/**
 * Single-target rotations for the four classes that previously used only generic
 * skill fitness: Ranger, Gunner, Songweaver and Aethertech. Priorities follow the
 * WoW bot order (procs/combo follow-ups, then keeping debuffs and periodic damage
 * up, then control when an enemy closes in on a ranged class, then the strongest
 * filler), all read from native skill data. Never grants skills or changes a build;
 * native cooldowns, costs, chains and range checks stay in the cast gates.
 */
final class PlayerBotClassRotations {
 static boolean applies(PlayerClass pc) {
  return pc==PlayerClass.RANGER || pc==PlayerClass.GUNNER || pc==PlayerClass.BARD || pc==PlayerClass.RIDER;
 }
 private static boolean ranged(PlayerClass pc){return pc==PlayerClass.RANGER || pc==PlayerClass.GUNNER || pc==PlayerClass.BARD;}

 static boolean control(SkillTemplate skill) {
  return skill.hasAnyEffect(EffectType.ROOT,EffectType.SIMPLEROOT,EffectType.SNARE,EffectType.SLOW,
   EffectType.ABSOLUTESNARE,EffectType.ABSOLUTESLOW,EffectType.STUN,EffectType.SLEEP,EffectType.STUMBLE);
 }
 /** Armor/resistance/attack weakening, like Hunter's Mark or Sunder Armor. */
 static boolean weakening(SkillTemplate skill) {
  if(skill.getEffects()==null)return false;
  return skill.getEffects().getEffects().stream().filter(e->e instanceof StatdownEffect && e.getChange()!=null)
   .flatMap(e->e.getChange().stream()).anyMatch(c->c.getValue()<0 && Set.of(
    StatEnum.PHYSICAL_DEFENSE,StatEnum.MAGICAL_RESIST,StatEnum.PHYSICAL_ATTACK,StatEnum.MAGICAL_ATTACK,
    StatEnum.FIRE_RESISTANCE,StatEnum.WATER_RESISTANCE,StatEnum.WIND_RESISTANCE,StatEnum.EARTH_RESISTANCE).contains(c.getStat()));
 }
 static boolean offensiveBoost(SkillTemplate skill) {
  if(skill==null || skill.getEffects()==null)return false;
  return skill.getEffects().getEffects().stream().filter(e->e.getChange()!=null)
   .flatMap(e->e.getChange().stream()).anyMatch(c->c.getValue()>0 && Set.of(
    StatEnum.ATTACK_SPEED,StatEnum.PHYSICAL_ATTACK,StatEnum.PHYSICAL_CRITICAL,StatEnum.PHYSICAL_ACCURACY,
    StatEnum.MAGICAL_ATTACK,StatEnum.MAGICAL_CRITICAL,StatEnum.BOOST_MAGICAL_SKILL).contains(c.getStat()));
 }
 private static boolean active(SkillTemplate skill,List<PlayerBotOffense.Active> effects) {
  return effects.stream().anyMatch(e->PlayerBotSorcerer.sameFamily(skill,e.skill()) && e.remaining()>1500);
 }

 /** NaN leaves area skills and non-applicable classes on the installed path. */
 static double damage(Player bot,PlayerBotSkills.Entry entry,Creature target,List<PlayerBotOffense.Active> effects,double fitness) {
  var pc=bot.getPlayerClass();
  if(!applies(pc))return Double.NaN;
  var skill=PlayerBotSkills.actualTemplate(bot,entry);if(skill==null)return 0;
  if(!PlayerBotSorcerer.singleTarget(skill))return Double.NaN;
  double tie=Math.clamp(Double.isFinite(fitness)?fitness:0,0,7);
  double health=PlayerBotDefense.health(target);
  boolean moving=bot.getMoveController().isInMove();
  // 1. Available native follow-up (WoW procs and combo follow-ups) before anything else.
  if(PlayerBotSkills.followUp(entry) && PlayerBotSkills.chainAvailable(bot,entry))return 24+tie/7;
  // 2. Ranged classes stop a melee attacker that has reached them (Concussive Shot / Wing Clip).
  if(ranged(pc) && control(skill) && target.getTarget()==bot && PositionUtil.isInRange(bot,target,6)
   && !active(skill,effects) && health>15)return 22+tie/7;
  // 3. Keep one weakening debuff up on a healthy target (Hunter's Mark / Sunder Armor).
  if(weakening(skill) && !active(skill,effects) && health>30)return 19+tie/7;
  // 4. Keep periodic damage up while it still has time to pay off.
  if(PlayerBotOffense.periodic(skill)) {
   boolean maintain=PlayerBotOffense.refresh(skill,entry.level(),effects,health);
   if(maintain)return 18+tie/7;
   if(!PlayerBotOffense.instant(skill))return 0;
  }
  // 5. Gunner charged shots (Aimed Shot purpose) only on a healthy target while standing.
  if(skill.getSkillChargeCondition()!=null)return pc==PlayerClass.GUNNER && !moving && health>40 ? 16+tie/7 : 0;
  if(!PlayerBotOffense.instant(skill))return 0;
  // 6. Fillers: strongest learned damage; instant shots while moving, finishers when low.
  double filler=10+tie;
  if(moving && skill.getDuration()>0)filler-=4;
  if(health<25 && skill.getDuration()==0)filler+=1;
  // Aethertech drains MP to fuel its mech: prefer MP-returning strikes when low.
  if(pc==PlayerClass.RIDER && skill.hasAnyEffect(EffectType.MPHEALINSTANT,EffectType.MPHEAL)
   && 100.0*bot.getLifeStats().getCurrentMp()/Math.max(1,bot.getLifeStats().getMaxMp())<40)filler+=3;
  return filler;
 }

 /** In-combat self offensive cooldowns (Rapid Fire / Recklessness purpose). NaN = unchanged. */
 static double support(Player bot,Role role,PlayerBotSkills.Entry entry,Creature recipient,boolean combat) {
  if(!applies(bot.getPlayerClass()) || !combat || recipient!=bot || entry.kind()!=SkillKind.BUFF)return Double.NaN;
  if(!offensiveBoost(entry.template()))return Double.NaN;
  // Songweaver keeps healing first: its offensive songs wait while the party needs care.
  if(bot.getPlayerClass()==PlayerClass.BARD && role==Role.HEALER)return Double.NaN;
  return 18;
 }
 private PlayerBotClassRotations() {}
}
