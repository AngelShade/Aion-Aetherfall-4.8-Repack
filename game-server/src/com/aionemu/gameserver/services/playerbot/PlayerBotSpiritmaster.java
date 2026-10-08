/* Adapted from pinned mod-playerbots 037c01418b5d01506917a3db9b44fd56ac5f965c:
 * Warlock/Strategy/AfflictionWarlockStrategy.cpp, GenericWarlockStrategy.cpp and
 * WarlockTriggers.cpp. GPL-2.0-or-later; third-party/playerbots/AUTHORS.md.
 * Only missing single-target ordering is adapted; native pet/utility adapters remain. */
package com.aionemu.gameserver.services.playerbot;

import java.util.List;
import com.aionemu.gameserver.model.PlayerClass;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.skillengine.effect.SpellAtkDrainInstantEffect;
import com.aionemu.gameserver.skillengine.model.SkillTemplate;

/** Learned spell priorities; never adds skills, spends resources or changes a build. */
final class PlayerBotSpiritmaster {
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
