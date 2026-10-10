/* Adapted from pinned mod-playerbots 037c01418b5d01506917a3db9b44fd56ac5f965c:
 * Warlock/Strategy/AfflictionWarlockStrategy.cpp, GenericWarlockStrategy.cpp and
 * WarlockTriggers.cpp, GenericWarlockNonCombatStrategy.cpp and GenericTriggers.cpp.
 * GPL-2.0-or-later; third-party/playerbots/AUTHORS.md.
 * Native learned spell priorities, no-pet preparation and actual buff recipients. */
package com.aionemu.gameserver.services.playerbot;

import java.util.List;
import java.util.ArrayList;
import com.aionemu.gameserver.model.PlayerClass;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.stats.container.StatEnum;
import com.aionemu.gameserver.skillengine.effect.SpellAtkDrainInstantEffect;
import com.aionemu.gameserver.skillengine.model.SkillTemplate;

/** Learned spell priorities; never adds skills, spends resources or changes a build. */
final class PlayerBotSpiritmaster {
 // GenericWarlockNonCombatStrategy's no-pet preparation and BuffTrigger's
 // actual aura recipient, adapted to Aion's two-stage spirit orders.
 static boolean idleUseful(Player bot,PlayerBotSkills.Entry entry,Creature recipient,boolean combat) {
  if(!applies(bot.getPlayerClass()))return true;
  var skill=entry.template();
  if(entry.kind()==PlayerBotRules.SkillKind.BUFF && summonPreparation(skill))
   return combat || bot.getSummon()==null;
  if(entry.kind()!=PlayerBotRules.SkillKind.PET_ORDER)return true;
  var pet=bot.getSummon();var actual=PlayerBotSkills.actualTemplate(bot,entry);
  if(pet==null || pet.isDead() || pet.isBeingReleased() || pet.isCasting() || actual==null)return false;
  if(PlayerBotSkills.classify(actual)!=PlayerBotRules.SkillKind.BUFF)return true;
  Creature first=switch(actual.getProperties().getFirstTarget()) {
   case ME -> pet;
   case MYMASTER -> bot;
   default -> recipient;
  };
  // A PC-only area order is cast by the spirit but puts its aura on nearby
  // players. Checking the spirit's aura list would always report it missing,
  // including during combat. A filled native beneficiary must stop recasting
  // in both engines rather than displacing the next useful attack.
  var targets=new ArrayList<Creature>();targets.add(first);
  var result=actual.getProperties().validateEffectedList(targets,first,pet,actual,first.getX(),first.getY(),first.getZ());
  return result.isValid() && targets.stream().anyMatch(target -> !target.isDead() && missingBuff(bot,actual,target));
 }
 static boolean missingBuff(Player bot,SkillTemplate skill,Creature target) {
  return PlayerBotBuffs.canAdd(bot.getPlayerClass(),skill,PlayerBotBuffs.active(target.getEffectController()));
 }
 static boolean summonPreparation(SkillTemplate skill) {
  return skill.getEffects()!=null && skill.getEffects().getEffects().stream().filter(e->e.getChange()!=null)
   .flatMap(e->e.getChange().stream()).anyMatch(c->c.getValue()>0
    && (c.getStat()==StatEnum.BOOST_CASTING_TIME_SUMMON || c.getStat()==StatEnum.BOOST_CASTING_TIME_SUMMONHOMING));
 }
 static boolean applies(PlayerClass pc) { return pc==PlayerClass.SPIRIT_MASTER; }
 static String strategy(PlayerClass pc) {
  return applies(pc) ? "spiritmaster single target" : PlayerBotSorcerer.strategy(pc);
 }
 static boolean restores(SkillTemplate skill, boolean health) {
  return skill.getEffects()!=null && skill.getEffects().getEffects().stream()
   .anyMatch(e -> e instanceof SpellAtkDrainInstantEffect drain
    && (health ? drain.getHpPercent() : drain.getMpPercent())>0);
 }
 static double band(SkillTemplate skill, boolean chain, boolean refresh, boolean vulnerability,
                    double health, double mana, double targetHealth, double fitness) {
  double tie=Math.clamp(Double.isFinite(fitness)?fitness:0,0,7)/100;
  if(chain)return 25+tie;
  // Life Tap's resource-recovery purpose maps to learned Aion damage+recovery,
  // not invented HP spending, a WoW spell ID or a free MP grant.
  if(health<65 && restores(skill,true) || mana<40 && restores(skill,false))return 22+tie;
  if(vulnerability)return 19+tie;
  if(refresh)return 18+tie;
  if(!PlayerBotOffense.instant(skill))return 0;
  // No fabricated Drain Soul execute multiplier: direct native recovery damage
  // is merely preferred when finishing, after native usefulness/affordability.
  if(targetHealth<25 && (restores(skill,true)||restores(skill,false)))return 16.5+tie;
  return 5.2+tie;
 }
 static double damage(Player bot,PlayerBotSkills.Entry entry,Creature target,
                      List<PlayerBotOffense.Active> effects,double fitness) {
  var actual=PlayerBotSkills.actualTemplate(bot,entry);if(actual==null)return 0;
  double targetHealth=PlayerBotDefense.health(target);
  boolean periodic=PlayerBotOffense.periodic(actual);
  boolean refresh=periodic && PlayerBotOffense.refresh(actual,entry.level(),effects,targetHealth);
  if(periodic && !refresh && !PlayerBotOffense.instant(actual))return 0;
  boolean vulnerability=PlayerBotSorcerer.vulnerability(actual)
   && effects.stream().noneMatch(e->PlayerBotSorcerer.sameFamily(actual,e.skill()));
  return band(actual,PlayerBotSkills.followUp(entry)&&PlayerBotSkills.chainAvailable(bot,entry),refresh,
   vulnerability,PlayerBotDefense.health(bot),PlayerBotDefense.mana(bot),targetHealth,fitness);
 }
 private PlayerBotSpiritmaster() {}
}
