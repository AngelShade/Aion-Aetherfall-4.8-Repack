/*
 * Defensive decisions adapted from mod-playerbots, pinned revision
 * 037c01418b5d01506917a3db9b44fd56ac5f965c: GenericMageStrategy,
 * FrostMageStrategy, GenericHunterStrategy and AssassinationRogueStrategy.
 * Attribution: third-party/playerbots/AUTHORS.md. SPDX-License-Identifier: GPL-2.0-or-later
 * Native Aion targeting, effect slots, protections and existing panic policy are local adapters.
 */
package com.aionemu.gameserver.services.playerbot;

import static com.aionemu.gameserver.skillengine.effect.EffectType.*;
import java.util.*;
import com.aionemu.gameserver.model.PlayerClass;
import com.aionemu.gameserver.model.gameobjects.*;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.services.playerbot.PlayerBotRules.*;
import com.aionemu.gameserver.skillengine.effect.AbnormalState;
import com.aionemu.gameserver.skillengine.effect.ShieldEffect;
import com.aionemu.gameserver.skillengine.model.*;
import com.aionemu.gameserver.utils.PositionUtil;

/** Protect before panic and control pursuers, using only existing learned native skills. */
final class PlayerBotDefense {
 static boolean resistOnly(SkillTemplate skill) {
  return skill.hasAnyEffect(ALWAYSRESIST) && !skill.hasAnyEffect(SHIELD,MPSHIELD,PROTECT,LIMITEDREDUCEDAMAGE,ALWAYSDODGE,ALWAYSPARRY,ALWAYSBLOCK);
 }
 static boolean barrier(SkillTemplate skill,int level) {
  return !skill.hasAnyEffect(MPSHIELD) && skill.getEffects()!=null && skill.getEffects().getEffects().stream()
   .anyMatch(e->e instanceof ShieldEffect && e.getDuration2()+(long)e.getDuration1()*level>=60000);
 }
 static double policy(PlayerClass pc,SkillTemplate skill,int level,double health,double mana,boolean combat,boolean pressure,boolean incomingMagic) {
  if(!Double.isFinite(health) || health<=0 || !Double.isFinite(mana))return 0;
  // Aion's resistance charges cannot remove existing debuffs like WoW Cloak.
  // Spend them for an incoming hostile magical cast, not a melee-only attacker.
  if(resistOnly(skill))return combat && incomingMagic && health<(pc==PlayerClass.ASSASSIN ? 25 : 35)
   ? pc==PlayerClass.ASSASSIN ? 27 : PlayerBotEngine.EMERGENCY+1 : 0;
  // Preserve the installed native panic behavior for previously supported defenses.
  if(health<35)return PlayerBotEngine.EMERGENCY+1;
  if(!combat)return 0;
  return switch(pc) {
   case MAGE,SORCERER -> skill.hasAnyEffect(MPSHIELD) && health<45 && mana>0 ? 85
    : barrier(skill,level) && (health<65 || pressure) ? 29 : 0;
   case RANGER -> health<45 ? 35 : 0;
   case ASSASSIN -> health<45 && skill.hasAnyEffect(ALWAYSDODGE,ALWAYSPARRY,ALWAYSBLOCK) ? 29 : 0;
   default -> 0;
  };
 }
 static double health(Creature target){return 100.0*target.getLifeStats().getCurrentHp()/Math.max(1,target.getLifeStats().getMaxHp());}
 static double mana(Creature target){return 100.0*target.getLifeStats().getCurrentMp()/Math.max(1,target.getLifeStats().getMaxMp());}
 static List<Npc> attackers(Player bot,Creature target) {
  var attackers=new ArrayList<Npc>();bot.getKnownList().forEachNpc(n->{
   if(n.getTarget()==target && PlayerBotService.allowsTarget(bot,n))attackers.add(n);
  });return attackers;
 }
 static boolean magical(List<Npc> attackers,Creature target) {
  return attackers.stream().anyMatch(n->{var cast=n.getCastingSkill();return cast!=null && cast.getSkillTemplate().getType()==SkillType.MAGICAL
   && cast.getSkillTemplate().getProperties()!=null && cast.getSkillTemplate().getProperties().getTargetRelation()==com.aionemu.gameserver.skillengine.properties.TargetRelationAttribute.ENEMY
   && (cast.getFirstTarget()==target || cast.getEffectedList().contains(target));});
 }
 static boolean addable(Player bot,PlayerBotSkills.Entry entry,Creature target) {
  return !target.isDead() && PlayerBotBuffs.canAdd(bot.getPlayerClass(),entry.template(),
   target.getEffectController().getAbnormalEffects().stream().map(Effect::getSkillTemplate).toList());
 }
 static double priority(Player bot,PlayerBotSkills.Entry entry,Creature anchor,boolean combat,List<Player> party) {
  double result=0;
  for(var target:PlayerBotHealing.targets(bot,entry,anchor,party)) {
   if(!addable(bot,entry,target))continue;var attackers=attackers(bot,target);
   double existing=entry.kind()==SkillKind.DEFENSE ? policy(bot.getPlayerClass(),entry.template(),entry.level(),health(target),mana(target),combat,!attackers.isEmpty(),magical(attackers,target)) : 0;
   result=Math.max(result,Math.max(existing,PlayerBotTemplar.priority(bot,entry,target,combat,attackers)));
  }return result;
 }
 static Creature recipient(Player bot,PlayerBotSkills.Entry entry,List<Player> party,boolean combat) {
  Creature best=null;double value=0;
  for(var target:PlayerBotSupport.allowed(bot,entry,party)) {
   double score=priority(bot,entry,target,combat,party);
   if(score>value || score>0 && score==value && best!=null && health(target)<health(best)){best=target;value=score;}
  }return best;
 }
 static double peelPolicy(PlayerClass pc,Role role,SkillTemplate skill,double distance,boolean pursuing,boolean rooted,boolean snared) {
  if(!Double.isFinite(distance) || distance<0 || !pursuing || rooted || role==Role.TANK || role==Role.MELEE)return 0;
  return switch(pc) {
   case MAGE,SORCERER -> skill.hasAnyEffect(ROOT) && distance<=5 ? 50 : 0;
   case RANGER -> skill.hasAnyEffect(ROOT) && distance<=5 ? 21 : skill.hasAnyEffect(SNARE) && !snared ? 20 : 0;
   default -> 0;
  };
 }
 static double peelPriority(Player bot,Role role,PlayerBotSkills.Entry entry,Creature target) {
  if(!entry.template().hasAnyEffect(ROOT,SNARE) || role==Role.TANK || role==Role.MELEE
   || bot.getPlayerClass()!=PlayerClass.MAGE && bot.getPlayerClass()!=PlayerClass.SORCERER && bot.getPlayerClass()!=PlayerClass.RANGER
   || !(target instanceof Npc npc) || entry.template().isCharge() || !PlayerBotEnemyUtility.ready(bot,entry,npc)
   || PlayerBotService.getInstance().isReserved(bot,target,SkillKind.CONTROL))return 0;
  return peelPolicy(bot.getPlayerClass(),role,entry.template(),PositionUtil.getDistance(bot,target),target.getTarget()==bot,
   target.getEffectController().isInAnyAbnormalState(AbnormalState.CANT_MOVE_STATE),target.getEffectController().isAbnormalSet(AbnormalState.SNARE));
 }
 static Creature peelRecipient(Player bot,Role role,PlayerBotSkills.Entry entry,Npc primary) {
  if(!entry.template().hasAnyEffect(ROOT,SNARE) || entry.template().isCharge())return null;
  List<Npc> candidates=new ArrayList<>();if(primary!=null)candidates.add(primary);
  bot.getKnownList().forEachNpc(n->{if(n!=primary && n.getTarget()==bot)candidates.add(n);});
  candidates.sort(Comparator.<Npc>comparingInt(n->n==primary ? 0 : 1).thenComparingDouble(n->PositionUtil.getDistance(bot,n)).thenComparingInt(Npc::getObjectId));
  Npc best=null;double value=0;for(var target:candidates){double score=peelPriority(bot,role,entry,target);if(score>value){best=target;value=score;}}
  return best;
 }
 static boolean useful(Player bot,Role role,PlayerBotSkills.Entry entry,Creature target,List<Npc> enemies) {
  if(entry.kind()==SkillKind.DEFENSE) {
   var group=bot.getPlayerGroup();var party=group==null ? List.of(bot) : group.getMembers();
   boolean combat=enemies.stream().anyMatch(n->n.isSpawned() && !n.isDead() && PlayerBotService.allowsTarget(bot,n));
   return priority(bot,entry,target,combat,party)>0;
  }
  if(entry.kind()==SkillKind.CONTROL) {
   boolean interrupt=PlayerBotSkills.canInterrupt(entry.template()) && target.isCasting()
    && !PlayerBotService.getInstance().isReserved(bot,target,SkillKind.CONTROL);
   return interrupt || peelPriority(bot,role,entry,target)>0;
  }return true;
 }
 private PlayerBotDefense(){}
}
