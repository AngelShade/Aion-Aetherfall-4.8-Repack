/*
 * Enemy utility decisions adapted from mod-playerbots at
 * 037c01418b5d01506917a3db9b44fd56ac5f965c: GenericMageStrategy,
 * GenericWarlockStrategy, GenericHunterStrategy and AssassinationRogueStrategy.
 * Upstream contributors: third-party/playerbots/AUTHORS.md.
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Aion dispel categories/power, native casting and party eligibility are local adapters.
 */
package com.aionemu.gameserver.services.playerbot;

import static com.aionemu.gameserver.skillengine.effect.EffectType.*;
import java.lang.reflect.*;
import java.util.*;
import com.aionemu.gameserver.controllers.effect.EffectController;
import com.aionemu.gameserver.model.PlayerClass;
import com.aionemu.gameserver.model.gameobjects.*;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.skillengine.effect.*;
import com.aionemu.gameserver.skillengine.model.*;
import com.aionemu.gameserver.utils.PositionUtil;
import com.aionemu.gameserver.world.geo.GeoService;

/** Read-only utility planning. Never invokes dispel calculation: that consumes native effect power. */
final class PlayerBotEnemyUtility {
 private record DispelFields(Field power, Field delta, Field level) {}
 private static final Method DISPELLABLE;
 private static final Map<Class<?>, DispelFields> FIELDS;
 static {
  try {
   DISPELLABLE=EffectController.class.getDeclaredMethod("isDispellable",Effect.class);DISPELLABLE.setAccessible(true);
   var fields=new HashMap<Class<?>,DispelFields>();
   for(var type:List.of(AbstractDispelEffect.class,DispelBuffCounterAtkEffect.class)) {
    Field power=type.getDeclaredField("power"),delta=type.getDeclaredField("dpower"),level=type.getDeclaredField("dispelLevel");
    power.setAccessible(true);delta.setAccessible(true);level.setAccessible(true);fields.put(type,new DispelFields(power,delta,level));
   }
   FIELDS=Map.copyOf(fields);
  }catch(ReflectiveOperationException failure){throw new ExceptionInInitializerError(failure);}
 }
 static boolean purge(SkillTemplate skill){return skill!=null && skill.hasAnyEffect(DISPELBUFF,DISPELNPCBUFF,DISPELBUFFCOUNTERATK);}
 static boolean nativeDispellable(Creature target,Effect effect) {
  try{return (boolean)DISPELLABLE.invoke(target.getEffectController(),effect);}
  catch(ReflectiveOperationException failure){throw new IllegalStateException("Native dispel eligibility unavailable",failure);}
 }
 static double severity(SkillTemplate buff) {
  return buff.hasAnyEffect(SHIELD,MPSHIELD,LIMITEDREDUCEDAMAGE,ALWAYSDODGE,ALWAYSPARRY,ALWAYSBLOCK) ? 8
   : buff.hasAnyEffect(HEAL,HEALINSTANT,CASEHEAL,REFLECTOR,BOOSTSPELLATTACK,ONETIMEBOOSTSKILLATTACK) ? 6
   : buff.hasAnyEffect(STATUP,STATBOOST,WEAPONSTATUP,WEAPONSTATBOOST) ? 4 : 1;
 }
 static boolean eligible(Effect effect,boolean npc,boolean counter,Creature caster,int level) {
  var slot=effect.getTargetSlot();var category=effect.getDispelCategory();
  if(counter) {
   // Counter purges can consume the caster's own debuffs as well as buffs.
   // Account for their count quota, but never trigger this utility to erase a DoT.
   if(slot!=SkillTargetSlot.BUFF && slot!=SkillTargetSlot.DEBUFF && category!=DispelCategoryType.ALL)return false;
   if(slot==SkillTargetSlot.DEBUFF && effect.getEffector()!=caster)return false;
   return (category==DispelCategoryType.ALL || category==DispelCategoryType.BUFF) && effect.getReqDispelLevel()<=level;
  }
  return slot==SkillTargetSlot.BUFF && (npc ? category==DispelCategoryType.NPC_BUFF
   : category==DispelCategoryType.BUFF && effect.getReqDispelLevel()<=level);
 }
 static double purgeScore(Creature caster,PlayerBotSkills.Entry entry,Creature target) {
  if(target==null || entry.template().getEffects()==null)return 0;
  double best=0;
  for(var template:entry.template().getEffects().getEffects()) {
   boolean counter=template instanceof DispelBuffCounterAtkEffect,npc=template instanceof DispelNpcBuffEffect;
   if(!counter && !npc && !(template instanceof DispelBuffEffect))continue;
   var fields=FIELDS.get(counter ? DispelBuffCounterAtkEffect.class : AbstractDispelEffect.class);
   try {
    int power=fields.power.getInt(template)+fields.delta.getInt(template)*entry.level();
    int count=template.getValue()+template.getDelta()*entry.level(),level=fields.level.getInt(template);
    if(power<=0 || count<=0)continue;
    double score=0;
    for(var effect:target.getEffectController().getAbnormalEffects()) {
     if(count==0)break;
     if(!nativeDispellable(target,effect) || !eligible(effect,npc,counter,caster,level))continue;
     boolean removes=effect.getPower()<=power;
     if(effect.getTargetSlot()==SkillTargetSlot.BUFF && effect.getRemainingTimeMillis()>500)
      score=Math.max(score,severity(effect.getSkillTemplate())*(removes ? 1 : .5));
     if(removes)count--;
    }
    best=Math.max(best,score);
   }catch(IllegalAccessException failure){throw new IllegalStateException("Native dispel metadata unavailable",failure);}
  }
  return best;
 }
 static double purgePriority(PlayerClass pc,double score) {
  if(score<=0)return 0;
  // Spellsteal's protective purpose maps to Aion dispelling; no foreign buff is copied.
  double band=switch(pc){case MAGE,SORCERER->PlayerBotEngine.INTERRUPT;case RANGER->PlayerBotEngine.DISPEL+11;default->PlayerBotEngine.DISPEL;};
  return band+score;
 }
 static double interruptPriority(PlayerClass pc,Creature target) {
  if(target==null || !target.isCasting())return 0;
  var cast=target.getCastingSkill();boolean heal=cast!=null && cast.getSkillTemplate().hasAnyEffect(HEALINSTANT,HEAL,CASEHEAL,RESURRECT);
  return PlayerBotEngine.INTERRUPT+(pc==PlayerClass.ASSASSIN ? heal ? 1 : 2 : 0);
 }
 static boolean damagePayload(SkillTemplate skill) {
  return skill.hasAnyEffect(SPELLATTACK,SPELLATTACKINSTANT,SKILLATTACKINSTANT,SKILLATKDRAININSTANT,
   SPELLATKDRAIN,SPELLATKDRAININSTANT,BLEED,POISON,SIGNET,SIGNETBURST,CARVESIGNET,SKILLLAUNCHER);
 }
 static boolean injured(List<Player> party) {
  return party.stream().anyMatch(p->!p.isDead() && 100.0*p.getLifeStats().getCurrentHp()/Math.max(1,p.getLifeStats().getMaxHp())<85);
 }
 static boolean ready(Player bot,PlayerBotSkills.Entry entry,Npc target) {
  if(target==null || target.isDead() || !target.isSpawned() || !PlayerBotService.allowsTarget(bot,target)
   || !PositionUtil.isInAttackRange(bot,target,entry.range()) || !GeoService.getInstance().canSee(bot,target)
   || bot.isSkillDisabled(entry.template()) || !PlayerBotSkills.chainAvailable(bot,entry) || !PlayerBotSkills.canPlan(bot,entry,target))return false;
  List<Creature> targets=new ArrayList<>();targets.add(target);
  var result=entry.template().getProperties().validateEffectedList(targets,target,bot,entry.template(),target.getX(),target.getY(),target.getZ());
  return result.isValid() && targets.contains(target);
 }
 static double utility(Player bot,PlayerBotSkills.Entry entry,Npc target) {
  if(!ready(bot,entry,target))return 0;
  double score=purge(entry.template()) ? purgePriority(bot.getPlayerClass(),purgeScore(bot,entry,target)) : 0;
  if(PlayerBotSkills.canInterrupt(entry.template()) && target.isCasting()
   && !PlayerBotService.getInstance().isReserved(bot,target,PlayerBotRules.SkillKind.CONTROL))
   score=Math.max(score,interruptPriority(bot.getPlayerClass(),target));
  return score;
 }
 static Creature recipient(Player bot,PlayerBotSkills.Entry entry,Npc primary) {
  boolean purge=purge(entry.template());
  if(!purge && !PlayerBotSkills.canInterrupt(entry.template()))return primary;
  List<Npc> candidates=new ArrayList<>();if(primary!=null)candidates.add(primary);
  var group=bot.getPlayerGroup();
  if(group!=null)bot.getKnownList().forEachNpc(n->{
   if(n!=primary && group.getMembers().stream().anyMatch(p->n.getAggroList().isHating(p)
    || p.getSummon()!=null && n.getAggroList().isHating(p.getSummon())))candidates.add(n);
  });
  Npc best=null;double value=0;
  // Stable target choice favors the current target on ties, then distance/id.
  candidates.sort(Comparator.<Npc>comparingInt(n->n==primary ? 0 : 1)
   .thenComparingDouble(n->PositionUtil.getDistance(bot,n)).thenComparingInt(Npc::getObjectId));
  for(var candidate:candidates){double score=utility(bot,entry,candidate);if(score>value){best=candidate;value=score;}}
  return best!=null ? best : purge && !damagePayload(entry.template()) ? null : primary;
 }
 static boolean useful(Player bot,PlayerBotSkills.Entry entry,Creature target) {
  var actual=PlayerBotSkills.actualTemplate(bot,entry);
  if(actual==null)return false;
  var evaluated=entry.kind()==PlayerBotRules.SkillKind.PET_ORDER ? new PlayerBotSkills.Entry(actual,actual.getLvl(),PlayerBotSkills.classify(actual),entry.range()) : entry;
  Creature caster=entry.kind()==PlayerBotRules.SkillKind.PET_ORDER ? bot.getSummon() : bot;
  return !purge(actual) || damagePayload(actual) || purgeScore(caster,evaluated,target)>0
   || PlayerBotSkills.canInterrupt(actual) && target.isCasting();
 }
 private PlayerBotEnemyUtility(){}
}
