/* Adapted from pinned mod-playerbots 037c01418b5d01506917a3db9b44fd56ac5f965c:
 * AfflictionWarlockStrategy, FireMageStrategy and AssassinationRogueStrategy.
 * GPL-2.0-or-later; attribution: third-party/playerbots/AUTHORS.md.
 * Aion native effect stacks, rune levels and spell conditions replace WoW auras/combo points. */
package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import com.aionemu.gameserver.model.PlayerClass;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.skillengine.effect.*;
import com.aionemu.gameserver.skillengine.model.*;

/** Read-only offensive decisions. Never calculates native damage or consumes rune/effect resources. */
final class PlayerBotOffense {
 record Active(SkillTemplate skill,int level,long remaining) {}
 static List<Active> active(Creature target) {
  return target.getEffectController().getAbnormalEffects().stream()
   .map(e->new Active(e.getSkillTemplate(),e.getSkillLevel(),e.getRemainingTimeMillis())).toList();
 }
 static boolean periodic(SkillTemplate skill) {
  return skill.hasAnyEffect(EffectType.SPELLATTACK,EffectType.SPELLATKDRAIN,EffectType.POISON,EffectType.BLEED);
 }
 static boolean instant(SkillTemplate skill) {
  return skill.getEffects()!=null && skill.getEffects().getEffects().stream().anyMatch(e->e instanceof DamageEffect);
 }
 static boolean samePeriodic(SkillTemplate next,SkillTemplate current) {
  if(!periodic(current))return false;
  return next.getSkillId()==current.getSkillId()
   || next.getStack()!=null && !next.getStack().isBlank() && next.getStack().equals(current.getStack())
   || next.getConflictId()!=0 && next.getConflictId()==current.getConflictId()
   || next.getTargetSlot()==current.getTargetSlot() && next.getEffects().getEffects().stream()
    .anyMatch(e->e instanceof AbstractOverTimeEffect && e.getEffectId()!=0 && current.getEffects().getEffects().stream()
     .anyMatch(c->c instanceof AbstractOverTimeEffect && c.getEffectId()==e.getEffectId()));
 }
 static long refreshWindow(SkillTemplate skill) {
  // Refresh in the final native tick window; include cast time so an expiring DoT does not fall off during casting.
  int tick=skill.getEffects().getEffects().stream().filter(e->e instanceof AbstractOverTimeEffect)
   .mapToInt(e->((AbstractOverTimeEffect)e).getChecktime()).filter(t->t>0).min().orElse(1000);
  return Math.max(750,Math.max(0,skill.getDuration())+(long)tick);
 }
 static boolean refresh(SkillTemplate skill,int level,List<Active> effects,double targetHealth) {
  if(!periodic(skill))return true;
  if(targetHealth<25 && !instant(skill))return false;
  for(var current:effects)if(samePeriodic(skill,current.skill())) {
   boolean stronger=current.skill().getLvl()>skill.getLvl()
    || current.skill().getLvl()==skill.getLvl() && current.level()>level;
   if(stronger || current.remaining()>refreshWindow(skill))return false;
  }
  return true;
 }
 static int runes(SkillTemplate skill,List<Active> effects) {
  for(var e:skill.getEffects().getEffects())if(e instanceof SignetBurstEffect burst)
   return effects.stream().filter(a->Objects.equals(a.skill().getStack(),burst.getSignet()))
    .mapToInt(a->Math.min(burst.getSignetlvl(),a.level())).max().orElse(0);
  return 0;
 }
 static long runeTime(SkillTemplate skill,List<Active> effects) {
  for(var e:skill.getEffects().getEffects())if(e instanceof SignetBurstEffect burst)
   return effects.stream().filter(a->Objects.equals(a.skill().getStack(),burst.getSignet()))
    .mapToLong(Active::remaining).min().orElse(0);
  return 0;
 }
 static double finisher(int runes,double targetHealth,long remaining,long commitment,boolean builderAvailable) {
  // Native signet_data_templates defines a small nonzero multiplier at level zero.
  // Prefer building, but retain the legal reduced-damage fallback for builds without an available builder.
  if(runes<=0)return builderAvailable ? 0 : 8;
  if(runes>=4)return 25; // original four-combo-point purpose, backed by Aion rune levels
  if(targetHealth<25 || remaining<=Math.max(0,commitment)+1500)return 24;
  return builderAvailable ? 0 : 12; // a learned finisher remains usable when this build has no available builder
 }
 static boolean builderAvailable(Player bot,List<PlayerBotSkills.Entry> skills,Creature target) {
  return skills.stream().anyMatch(e->e.template().hasAnyEffect(EffectType.CARVESIGNET)
   && !bot.isSkillDisabled(e.template()) && PlayerBotSkills.chainAvailable(bot,e) && PlayerBotSkills.canPlan(bot,e,target,false));
 }
 static double routine(Player bot,PlayerBotSkills.Entry entry,Creature target,List<PlayerBotSkills.Entry> skills,double fitness) {
  var actual=PlayerBotSkills.actualTemplate(bot,entry);if(actual==null)return 0;
  var effects=active(target);double health=PlayerBotDefense.health(target);
  if(PlayerBotSorcerer.applies(bot.getPlayerClass()) && PlayerBotSorcerer.singleTarget(actual))return PlayerBotSorcerer.damage(bot,entry,target,effects,fitness);
  if(PlayerBotSpiritmaster.applies(bot.getPlayerClass()) && PlayerBotSorcerer.singleTarget(actual))return PlayerBotSpiritmaster.damage(bot,entry,target,effects,fitness);
  if(actual.hasAnyEffect(EffectType.SIGNETBURST))
   return finisher(runes(actual,effects),health,runeTime(actual,effects),actual.getDuration()+750L,builderAvailable(bot,skills,target));
  if(periodic(actual)) {
   boolean wanted=refresh(actual,entry.level(),effects,health);
   if(!wanted && !instant(actual))return 0;
   if(wanted) {
    double band=switch(bot.getPlayerClass()) {
     case SPIRIT_MASTER -> 18;
     case SORCERER,MAGE -> 18.5;
     default -> 17;
    };
    return band+Math.min(1,Math.max(0,fitness)/7);
   }
  }
  return (PlayerBotSkills.followUp(entry) ? 23 : 10)+fitness;
 }
 static boolean useful(Player bot,PlayerBotSkills.Entry entry,Creature target) {
  if(entry.kind()!=PlayerBotRules.SkillKind.DAMAGE && entry.kind()!=PlayerBotRules.SkillKind.PET_ORDER)return true;
  var skill=PlayerBotSkills.actualTemplate(bot,entry);if(skill==null)return false;
  var effects=active(target);
  // Hybrid direct damage remains useful on an ongoing same-rank DoT, but
  // never risk replacing a stronger native periodic effect for that damage.
  if(periodic(skill))for(var current:effects)if(samePeriodic(skill,current.skill())
   && (current.skill().getLvl()>skill.getLvl()
    || current.skill().getLvl()==skill.getLvl() && current.level()>entry.level()))return false;
  if(target.isCasting() && PlayerBotSkills.canInterrupt(skill) || PlayerBotEnemyUtility.purge(skill))return true;
  if(skill.hasAnyEffect(EffectType.SIGNETBURST))
   return finisher(runes(skill,effects),PlayerBotDefense.health(target),runeTime(skill,effects),
    skill.getDuration()+750L,builderAvailable(bot,PlayerBotSkills.read(bot),target))>0;
  return !periodic(skill) || instant(skill) || refresh(skill,entry.level(),effects,PlayerBotDefense.health(target));
 }
 private PlayerBotOffense() {}
}
