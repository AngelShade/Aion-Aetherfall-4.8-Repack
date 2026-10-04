/* PartyMemberToHeal.cpp, HealthTriggers.cpp and HealPriestStrategy.cpp adapted
 * from mod-playerbots revision 037c01418b5d01506917a3db9b44fd56ac5f965c.
 * SPDX-License-Identifier: GPL-2.0-or-later. Native Aion targets and heal formulas
 * replace WoW spells; no effects, costs or random rolls occur during planning.
 */
package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import com.aionemu.gameserver.model.gameobjects.*;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.skillengine.effect.*;
import com.aionemu.gameserver.skillengine.model.*;
import com.aionemu.gameserver.skillengine.properties.*;
import com.aionemu.gameserver.utils.PositionUtil;
import com.aionemu.gameserver.world.geo.GeoService;
import static com.aionemu.gameserver.services.playerbot.PlayerBotRules.*;

final class PlayerBotHealing {
 static boolean managed(PlayerBotSkills.Entry e){return e.kind()==SkillKind.HEAL || e.kind()==SkillKind.CLEANSE;}
 static boolean heals(PlayerBotSkills.Entry e){return e.template().hasAnyEffect(EffectType.HEALINSTANT,EffectType.HEAL,EffectType.CASEHEAL);}
 static List<Player> party(Player bot) {var group=bot.getCurrentGroup();return group==null ? List.of(bot) : List.copyOf(group.getMembers());}
 static List<Creature> members(List<Player> party) {
  List<Creature> result=new ArrayList<>();for(var p:party){result.add(p);var pet=p.getSummon();if(pet!=null && !pet.isBeingReleased())result.add(pet);}return result;
 }
 static boolean reachable(Player bot,PlayerBotSkills.Entry e,Creature target) {
  return target.isSpawned() && target.getWorldId()==bot.getWorldId() && target.getInstanceId()==bot.getInstanceId()
   && (target==bot || PositionUtil.isInRange(bot,target,Math.max(40,e.range()*2)) && GeoService.getInstance().canSee(bot,target))
   && PlayerBotSkills.canPlan(bot,e,target,false);
 }
 static double health(Creature target){return 100.0*target.getLifeStats().getCurrentHp()/Math.max(1,target.getLifeStats().getMaxHp());}
 static double probe(double hp,double distance,double range,boolean pet) {
  return hp+(pet || distance>range ? 30 : Math.max(0,distance)/10);
 }
 static boolean sameBuff(Creature target,SkillTemplate skill) {
  return target.getEffectController().hasAbnormalEffect(e->e.getSkillId()==skill.getSkillId()
   || skill.getStack()!=null && !skill.getStack().isEmpty() && !"NONE".equalsIgnoreCase(skill.getStack()) && Objects.equals(e.getSkillTemplate().getStack(),skill.getStack()));
 }
 static boolean inFlight(Player bot,Creature target,List<Player> party) {
  if(PlayerBotService.getInstance().isReserved(bot,target,SkillKind.HEAL))return true;
  for(var member:party) {
   if(member==bot)continue;var cast=member.getCastingSkill();
   if(cast!=null && cast.getSkillTemplate().hasAnyEffect(EffectType.HEALINSTANT,EffectType.HEAL,EffectType.CASEHEAL)
    && (cast.getFirstTarget()==target || cast.getEffectedList().contains(target)))return true;
  }return false;
 }
 static boolean needs(Player bot,PlayerBotSkills.Entry e,Creature target,List<Player> party) {
  if(target.isDead() || !target.isSpawned() || target.getWorldId()!=bot.getWorldId() || target.getInstanceId()!=bot.getInstanceId())return false;
  double hp=health(target);if(hp>=85 || hp<=0)return false;
  if(target instanceof Summon && e.template().getProperties().getFirstTarget()!=FirstTargetAttribute.MYPET && hp>=65)return false;
  // Upstream permits another heal below medium health even while a heal is in
  // progress. Keep this exception so reservations cannot starve a falling tank.
  if(hp>=65 && inFlight(bot,target,party))return false;
  var skill=e.template();return skill.hasAnyEffect(EffectType.HEALINSTANT)
   || !skill.hasAnyEffect(EffectType.HEAL,EffectType.CASEHEAL) || !sameBuff(target,skill);
 }
 static List<Creature> targets(Player bot,PlayerBotSkills.Entry e,Creature anchor,List<Player> party) {
  List<Creature> targets=new ArrayList<>();targets.add(anchor);var p=e.template().getProperties();
  var result=p.validateEffectedList(targets,anchor,bot,e.template(),anchor.getX(),anchor.getY(),anchor.getZ());
  if(!result.isValid())return List.of();
  if((p.getTargetType()==null || p.getTargetType()==TargetRangeAttribute.ONLYONE) && !targets.contains(anchor))return List.of();
  var members=members(party);
  return targets.stream().filter(members::contains).filter(c->!c.isDead() && c.isSpawned()
   && c.getWorldId()==bot.getWorldId() && c.getInstanceId()==bot.getInstanceId()).distinct().toList();
 }
 static double snapshot(Player bot,PlayerBotSkills.Entry e,Creature target) {
  var effect=new Effect(bot,target,e.template(),e.level());double amount=0;
  for(var template:e.template().getEffects().getEffects()) {
   // HoT fit uses the first native tick, never pretends the full duration is
   // immediate healing. Conditional CaseHeal stays a protective action.
   if(template instanceof HealInstantEffect heal)amount+=health(target)>0 && target.getEffectController().isAbnormalSet(AbnormalState.DISEASE) ? 0 : heal.applyHealDeboost(effect,heal.calculateSnapshotHealValue(effect,HealType.HP));
   else if(template instanceof HealEffect heal)amount+=heal.applyHealDeboost(effect,heal.calculateSnapshotHealValue(effect,HealType.HP));
  }return Math.max(0,amount);
 }
 static double groupBonus(int injuredPlayers,int injuredPets){return Math.min(8,Math.max(0,injuredPlayers-1)*2+Math.max(0,injuredPets-1)*.5);}
 static double priority(Player bot,PlayerBotSkills.Entry e,Creature anchor,List<Player> party) {
  double urgency=0,fit=0;int players=0,pets=0;
  for(var target:targets(bot,e,anchor,party))if(heals(e) && needs(bot,e,target,party)) {
   boolean pet=target instanceof Summon;double hp=health(target);
   double candidate=healPriority(pet && e.template().getProperties().getFirstTarget()!=FirstTargetAttribute.MYPET ? Math.min(84,hp+30) : hp,target.getAggroList().stream().findAny().isPresent());
   if(candidate<=0)continue;urgency=Math.max(urgency,candidate);
   fit+=PlayerBotTactics.healFit(target.getLifeStats().getMaxHp()-target.getLifeStats().getCurrentHp(),snapshot(bot,e,target),e.template().getDuration(),hp<30);
   if(pet)pets++;else players++;
  }
  double healing=urgency==0 ? 0 : urgency+fit/Math.max(1,players+pets)+groupBonus(players,pets);
  double dispel=0;int cleanses=0;
  if(e.kind()==SkillKind.CLEANSE)for(var target:targets(bot,e,anchor,party))if(!PlayerBotService.getInstance().isReserved(bot,target,SkillKind.CLEANSE)) {
   double value=PlayerBotDispel.score(e,target);if(value>0){dispel=Math.max(dispel,value);cleanses++;}
  }
  return Math.max(healing,cleanses==0 ? 0 : PlayerBotEngine.DISPEL+dispel+Math.min(4,cleanses-1));
 }
 static Creature recipient(Player bot,PlayerBotSkills.Entry e,List<Player> party) {
  var props=e.template().getProperties();List<Creature> candidates=props.getFirstTarget()==FirstTargetAttribute.ME ? List.of(bot) : members(party);
  boolean group=props.getTargetType()!=null && props.getTargetType()!=TargetRangeAttribute.ONLYONE && props.getTargetMaxCount()!=1;
  Creature best=null;double bestPriority=Double.NEGATIVE_INFINITY,bestProbe=Double.POSITIVE_INFINITY;
  for(var target:candidates)if(!target.isDead() && reachable(bot,e,target)) {
   double priority=priority(bot,e,target,party),probe=probe(health(target),PositionUtil.getDistance(bot,target),e.range(),target instanceof Summon);
   double ranking=group || e.kind()==SkillKind.CLEANSE ? priority : -probe;
   if(priority>0 && (ranking>bestPriority || ranking==bestPriority && (probe<bestProbe || probe==bestProbe && target.getObjectId()<(best==null ? Integer.MAX_VALUE : best.getObjectId())))) {
    best=target;bestPriority=ranking;bestProbe=probe;
   }
  }return best;
 }
 static boolean useful(Player bot,PlayerBotSkills.Entry e,Creature anchor){return priority(bot,e,anchor,party(bot))>0;}
 static void reserve(Player bot,PlayerBotSkills.Entry e,Skill skill,long until) {
  var members=members(party(bot));for(var target:List.copyOf(skill.getEffectedList()))
   if(members.contains(target) && !target.isDead()) {
    if(heals(e))PlayerBotService.getInstance().reserve(bot,target,SkillKind.HEAL,until);
    if(e.kind()==SkillKind.CLEANSE)PlayerBotService.getInstance().reserve(bot,target,SkillKind.CLEANSE,until);
   }
 }
 static Creature resurrection(Player bot,PlayerBotSkills.Entry e,List<Player> party) {
  // There is no blanket combat veto. Native CombatCheck/weapon/resource/target
  // restrictions decide whether this particular learned resurrection is legal.
  return PlayerBotSupport.allowed(bot,e,party).stream().filter(p->p.isDead() && !p.getResStatus()
   && !PlayerBotService.getInstance().isReserved(bot,p,SkillKind.RESURRECT))
   .min(Comparator.comparingDouble(p->PositionUtil.getDistance(bot,p))).orElse(null);
 }
 private PlayerBotHealing(){}
}
