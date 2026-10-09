/* Adapted from mod-playerbots 037c01418b5d01506917a3db9b44fd56ac5f965c:
 * Shaman/Strategy/EnhancementShamanStrategy.cpp, GenericShamanStrategy.cpp,
 * Shaman/ShamanTriggers.cpp. GPL-2.0-or-later; contributors: third-party/playerbots/AUTHORS.md.
 * Missing learned party support maps to native mantras, not WoW totem actors/slots. */
package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.PlayerClass;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.stats.container.StatEnum;
import com.aionemu.gameserver.skillengine.effect.*;
import com.aionemu.gameserver.skillengine.model.*;
import com.aionemu.gameserver.skillengine.properties.*;
import com.aionemu.gameserver.utils.PositionUtil;
import static com.aionemu.gameserver.services.playerbot.PlayerBotRules.*;

/** Fill available native mantra slots without replacing an existing aura or changing a build. */
final class PlayerBotChanter {
 static boolean applies(PlayerClass pc) { return pc == PlayerClass.CHANTER; }
 static String strategy(PlayerClass pc) { return applies(pc) ? "chanter mantras" : PlayerBotCleric.strategy(pc); }
 static boolean mantra(SkillTemplate skill) {
  var p=skill.getProperties();
  return skill.isToggle() && skill.getSubType()==SkillSubType.CHANT && skill.getTargetSlot()==SkillTargetSlot.NOSHOW
   && p!=null && p.getFirstTarget()==FirstTargetAttribute.ME && p.getTargetType()==TargetRangeAttribute.ONLYONE
   && p.getTargetRelation()==TargetRelationAttribute.FRIEND && skill.hasAnyEffect(EffectType.AURA);
 }
 static boolean improves(SkillTemplate skill, Set<StatEnum> stats) {
  return skill.getEffects().getEffects().stream().filter(e->e.getChange()!=null).flatMap(e->e.getChange().stream())
   .anyMatch(c->stats.contains(c.getStat()) && (c.getStat().getSign()<0 ? c.getValue()<0 : c.getValue()>0));
 }
 static List<Player> recipients(Player bot,AuraEffect aura,List<Player> party) {
  float boost=Math.max(0,bot.getGameStats().getStat(StatEnum.BOOST_MANTRA_RANGE,100).getCurrent())/100f;
  return party.stream().filter(p->p.isSpawned() && !p.isDead() && p.getWorldId()==bot.getWorldId() && p.getInstanceId()==bot.getInstanceId())
   .filter(p->p==bot || Math.abs(p.getZ()-bot.getZ())<=aura.getDistanceZ()*boost
    && PositionUtil.isInRange(bot,p,aura.getDistance()*boost,false)).toList();
 }
 static double payload(SkillTemplate skill,List<Player> party,boolean combat) {
  boolean hp=skill.hasAnyEffect(EffectType.HEAL,EffectType.HEALINSTANT),mp=skill.hasAnyEffect(EffectType.MPHEAL,EffectType.MPHEALINSTANT);
  boolean physical=improves(skill,Set.of(StatEnum.PHYSICAL_ATTACK,StatEnum.PHYSICAL_ACCURACY,StatEnum.PHYSICAL_CRITICAL,StatEnum.ATTACK_SPEED));
  boolean magical=improves(skill,Set.of(StatEnum.BOOST_MAGICAL_SKILL,StatEnum.MAGICAL_ACCURACY,StatEnum.MAGICAL_CRITICAL));
  boolean defense=improves(skill,Set.of(StatEnum.PHYSICAL_DEFENSE,StatEnum.BLOCK,StatEnum.PARRY,StatEnum.EVASION,
   StatEnum.PHYSICAL_CRITICAL_RESIST,StatEnum.MAGICAL_CRITICAL_RESIST,StatEnum.PHYSICAL_CRITICAL_DAMAGE_REDUCE));
  boolean walking=improves(skill,Set.of(StatEnum.SPEED));
  boolean flight=improves(skill,Set.of(StatEnum.FLY_SPEED)) || skill.hasAnyEffect(EffectType.FPHEAL);
  // Flight-only support does not occupy a new slot for an entirely grounded party.
  if(flight && !hp && !mp && !physical && !magical && !defense && !walking && party.stream().noneMatch(Player::isFlying))return 0;
  double value=0;
  if(hp)value+=party.stream().anyMatch(p->PlayerBotHealing.health(p)<65)?5:1;
  if(mp)value+=party.stream().anyMatch(p->PlayerBotDefense.mana(p)<40)?5:1;
  if(physical)value+=Math.min(3,party.stream().filter(p->{var role=roleFor(p.getPlayerClass());return role==Role.TANK || role==Role.MELEE || p.getPlayerClass()==PlayerClass.CHANTER || p.getPlayerClass()==PlayerClass.RANGER;}).count());
  if(magical)value+=Math.min(3,party.stream().filter(p->roleFor(p.getPlayerClass())==Role.HEALER || roleFor(p.getPlayerClass())==Role.RANGED && p.getPlayerClass()!=PlayerClass.RANGER || p.getPlayerClass()==PlayerClass.BARD).count());
  if(defense)value+=party.stream().anyMatch(p->PlayerBotHealing.health(p)<65)?3:1;
  if(walking)value+=!combat && party.stream().anyMatch(p->p.getMoveController().isInMove())?4:1;
  if(flight && party.stream().anyMatch(Player::isFlying))value+=4;
  if(value<=0)return 0;
  // Normalize upstream combat support setup below Aion interrupts, urgent heals and encounters.
  return (combat?PlayerBotEngine.HIGH+6:PlayerBotEngine.NORMAL)+Math.min(6,value);
 }
 static double support(Player bot,PlayerBotSkills.Entry entry,Creature recipient,List<Player> party,boolean combat) {
  if(!applies(bot.getPlayerClass()) || entry.kind()!=SkillKind.BUFF || recipient!=bot || !mantra(entry.template()))return Double.NaN;
  if(DataManager.SKILL_DATA==null)return 0;
  double best=0;
  for(var effect:entry.template().getEffects().getEffects())if(effect instanceof AuraEffect aura) {
   var nativeBuff=DataManager.SKILL_DATA.getSkillTemplate(aura.getSkillId());
   if(nativeBuff==null || nativeBuff.getEffects()==null)continue;
   var recipients=recipients(bot,aura,party);
   if(!recipients.isEmpty())best=Math.max(best,payload(nativeBuff,recipients,combat));
  }
  return best;
 }
 private PlayerBotChanter() {}
}
