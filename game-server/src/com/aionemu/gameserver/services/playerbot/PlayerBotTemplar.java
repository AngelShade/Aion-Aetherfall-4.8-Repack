/* TankWarriorStrategy.cpp and WarriorTriggers.h at mod-playerbots revision
 * 037c01418b5d01506917a3db9b44fd56ac5f965c: shield block, low-health shield wall
 * and party protection. GPL-2.0-or-later; third-party/playerbots/AUTHORS.md.
 * Native Aion effects, targeting, equipment and costs replace WoW spells. */
package com.aionemu.gameserver.services.playerbot;

import java.util.List;
import com.aionemu.gameserver.model.PlayerClass;
import com.aionemu.gameserver.model.gameobjects.*;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.skillengine.effect.*;
import com.aionemu.gameserver.skillengine.model.*;
import com.aionemu.gameserver.skillengine.properties.TargetRelationAttribute;
import com.aionemu.gameserver.utils.PositionUtil;
import static com.aionemu.gameserver.services.playerbot.PlayerBotRules.*;

/** Anticipate native pressure without replacing threat control or existing defenses. */
final class PlayerBotTemplar {
 static String strategy(PlayerClass pc) { return pc==PlayerClass.TEMPLAR ? "templar protection" : PlayerBotChanter.strategy(pc); }
 static boolean managed(Player bot,PlayerBotSkills.Entry e) {
  return bot.getPlayerClass()==PlayerClass.TEMPLAR && !e.template().isPassive() && !e.template().isToggle()
   && (e.kind()==SkillKind.DEFENSE || e.kind()==SkillKind.CLEANSE)
   && e.template().hasAnyEffect(EffectType.SHIELD,EffectType.ALWAYSBLOCK) && !PlayerBotHealing.heals(e);
 }
 static boolean pressure(Npc enemy,Creature target,boolean magical) {
  if(enemy.isDead() || !enemy.isSpawned() || enemy.getWorldId()!=target.getWorldId() || enemy.getInstanceId()!=target.getInstanceId()
   || !PositionUtil.isInRange(enemy,target,40))return false;
  var cast=enemy.getCastingSkill();
  if(cast!=null)return cast.getSkillTemplate().getProperties()!=null
   && cast.getSkillTemplate().getProperties().getTargetRelation()==TargetRelationAttribute.ENEMY
   && (cast.getFirstTarget()==target || cast.getEffectedList().contains(target))
   && (cast.getSkillTemplate().getType()==SkillType.MAGICAL)==magical;
  return enemy.getTarget()==target && enemy.getAttackType().isMagical()==magical;
 }
 static boolean covers(ShieldEffect shield,boolean physical,boolean magical) {
  return switch(shield.getHitType()) {
   case EVERYHIT -> physical || magical;
   case PHHIT -> physical;
   case MAHIT -> magical;
   // Conditional normal/back/skill/controlled-hit shields need a separate adapter.
   default -> false;
  };
 }
 static double priority(Player bot,PlayerBotSkills.Entry e,Creature target,boolean combat,List<Npc> attackers) {
  if(!managed(bot,e) || !combat || target.isDead() || !target.isSpawned())return 0;
  if(e.kind()==SkillKind.CLEANSE && PlayerBotService.getInstance().isReserved(bot,target,SkillKind.CLEANSE))return 0;
  boolean physical=attackers.stream().anyMatch(n->pressure(n,target,false));
  boolean magical=attackers.stream().anyMatch(n->pressure(n,target,true));
  if(!physical && !magical)return 0;
  double hp=PlayerBotDefense.health(target);
  boolean shield=e.template().getEffects().getEffects().stream().anyMatch(effect->effect instanceof ShieldEffect s
   && s.getType()==ShieldType.NORMAL && covers(s,physical,magical));
  if(shield && hp<50)return hp<35 ? PlayerBotEngine.EMERGENCY+1 : 75;
  return physical && e.template().hasAnyEffect(EffectType.ALWAYSBLOCK) ? PlayerBotEngine.INTERRUPT+1 : 0;
 }
 private PlayerBotTemplar() {}
}
