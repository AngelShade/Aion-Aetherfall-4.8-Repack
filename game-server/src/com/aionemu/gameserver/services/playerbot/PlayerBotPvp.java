/* Native Aion adapter for AttackAction::Attack and AttackersValue::{Calculate,
 * AddAttackersOf,IsPossibleTarget}, mod-playerbots 037c01418b5d01506917a3db9b44fd56ac5f965c.
 * GPL-2.0-or-later; third-party/playerbots/AUTHORS.md. Tactical weights are local policy. */
package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import com.aionemu.gameserver.configs.main.PlayerBotConfig;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.skillengine.effect.AbnormalState;
import com.aionemu.gameserver.skillengine.effect.EffectType;
import com.aionemu.gameserver.utils.PositionUtil;
import com.aionemu.gameserver.world.geo.GeoService;
import static com.aionemu.gameserver.services.playerbot.PlayerBotRules.*;

/** Bounded, visible combat information only. Humans receive no special target bonus. */
final class PlayerBotPvp {
 private final PlayerBotSession session;
 private volatile Creature current;
 private float originX,originY,originZ;
 private long started,quietUntil;
 final PlayerBotRifts rifts=new PlayerBotRifts();
 PlayerBotPvp(PlayerBotSession session){this.session=session;}
 static boolean playerOwned(Creature target){return target!=null && target.getMaster() instanceof Player;}
 static boolean held(Creature target){return target.getEffectController().isAbnormalSet(AbnormalState.SLEEP)
  || target.getEffectController().isAbnormalSet(AbnormalState.PARALYZE)
  || target.getEffectController().isUnderFear();}
 private boolean legal(Creature target) {
  Player bot=session.bot();
  return PlayerBotConfig.PVP_ENABLED && playerOwned(target) && target!=bot && target.isSpawned() && !target.isDead()
   && !target.getLifeStats().isAboutToDie() && !target.isInvulnerable() && !target.isSpawnProtectedFrom(bot)
   && target.getWorldId()==bot.getWorldId() && target.getInstanceId()==bot.getInstanceId()
   && bot.isEnemy(target) && bot.canSee(target) && bot.getKnownList().sees(target)
   && !(target.getMaster() instanceof Player p && p.isUsingFlightTransporterOrWindstream());
 }
 private static boolean recent(Creature ally,Creature enemy,long now) {
  return ally.getAggroList().stream().anyMatch(a->a.getHate()>0 && now-a.getLastInteractionTime()<12000
   && (a.getAttacker()==enemy || a.getAttacker().getMaster()==enemy.getMaster()));
 }
 static boolean threatens(Creature enemy,List<Player> allies,long now) {
  var cast=enemy.getCastingSkill();
  return allies.stream().anyMatch(p->!p.isDead() && (recent(p,enemy,now)
   || p.getSummon()!=null && recent(p.getSummon(),enemy,now)
   || cast!=null && cast.getFirstTarget()!=null && cast.getFirstTarget().getMaster()==p
    && cast.getSkillTemplate().getProperties().getTargetRelation()==com.aionemu.gameserver.skillengine.properties.TargetRelationAttribute.ENEMY));
 }
 boolean allows(Creature target,List<Player> party,boolean explicit,Order order) {
  if(order==Order.PASSIVE || !legal(target))return false;
  if(held(target) && !explicit)return false;
  Player bot=session.bot();
  if(!PositionUtil.isInRange(session.autonomous()?bot:session.owner(),target,session.autonomous()?PlayerBotConfig.PVP_CHASE_DISTANCE:45))return false;
  if(!session.autonomous() && order==Order.GUARD && !PositionUtil.isInRange(bot,target,20))return false;
  if(target==current && PositionUtil.getDistance(target,originX,originY,originZ)>PlayerBotConfig.PVP_CHASE_DISTANCE)return false;
  return explicit || target==current || threatens(target,party,System.currentTimeMillis());
 }
 Creature choose(List<Player> party,Order order,int commanded,boolean mayStart,long now) {
  Player bot=session.bot();
  if(order==Order.PASSIVE || !PlayerBotConfig.PVP_ENABLED){clear();return null;}
  // Ended engagements, including a kill/despawn, must rest before scanning for
  // another unprovoked fight. Explicit orders and real defence can still act.
  if(current!=null && !legal(current)){quietUntil=now+15000;clear();}
  var objects=new ArrayList<Creature>();bot.getKnownList().forEachObject(o->{if(o instanceof Creature c && playerOwned(c))objects.add(c);});
  var visibleCandidates=objects.stream().filter(this::legal).filter(c->PositionUtil.isInRange(bot,c,PlayerBotConfig.PVP_CHASE_DISTANCE));
  var visible=session.autonomous()?nearest(bot,visibleCandidates,32):visibleCandidates
   .sorted(Comparator.comparingDouble(c->PositionUtil.getDistance(bot,c))).limit(32).toList();
  var nearbyCandidates=bot.getKnownList().streamPlayers().filter(p->p.isSpawned() && !p.isDead() && bot.canSee(p)
   && p.getWorldId()==bot.getWorldId() && p.getInstanceId()==bot.getInstanceId() && PositionUtil.isInRange(bot,p,40));
  var nearby=session.autonomous()?nearest(bot,nearbyCandidates,48):nearbyCandidates
   .sorted(Comparator.comparingDouble(p->PositionUtil.getDistance(bot,p))).limit(48).toList();
  var allies=session.autonomous()?java.util.stream.Stream.concat(party.stream(),nearby.stream()
   .filter(p->p.getRace()==bot.getRace() && !bot.isEnemy(p))).distinct().toList():party;
  double friendly=power(bot),hostile=0;
  for(var p:nearby)if(bot.isEnemy(p))hostile+=power(p);else if(p.getRace()==bot.getRace())friendly+=power(p);
  Creature best=null;double score=Double.NEGATIVE_INFINITY;
  for(var target:visible) {
   if(session.autonomous() && !legal(target))continue;
   boolean explicit=target.getObjectId()==commanded || !session.autonomous() && party.stream().anyMatch(p->
    p.getTarget()==target && recent(target,p,now));
   boolean direct=threatens(target,party,now);
   boolean defence=direct || threatens(target,allies,now);
   if(!session.autonomous() && !PositionUtil.isInRange(session.owner(),target,order==Order.GUARD?20:45))continue;
   if(held(target) && !explicit)continue;
   boolean continuing=target==current;
   // Answer attacks on self/party immediately, but nearby bystanders must still
   // judge numbers and avoid piling onto an already crowded target.
   if(!explicit && !direct && !continuing && (hp(bot)<70 || hostile>friendly*PlayerBotConfig.PVP_STRENGTH_LIMIT
    || nearby.stream().filter(p->!bot.isEnemy(p) && p.getTarget()==target).count()>=PlayerBotConfig.PVP_ATTACKERS_PER_TARGET))continue;
   if(continuing && (PositionUtil.getDistance(target,originX,originY,originZ)>PlayerBotConfig.PVP_CHASE_DISTANCE
    || now-started>PlayerBotConfig.PVP_CHASE_SECONDS*1000L && !PositionUtil.isInRange(bot,target,PlayerBotConfig.PVP_NOTICE_DISTANCE))) {
    quietUntil=now+15000;clear();continuing=false;
    if(!defence || !PositionUtil.isInRange(bot,target,PlayerBotConfig.PVP_NOTICE_DISTANCE))continue;
   }
   if(!explicit && !defence && !continuing) {
    if(!session.autonomous() || !mayStart || now<quietUntil || hp(bot)<70 || mp(bot)<30
     || Math.abs(target.getLevel()-bot.getLevel())>PlayerBotConfig.PVP_LEVEL_DIFFERENCE
     || !PositionUtil.isInRange(bot,target,PlayerBotConfig.PVP_NOTICE_DISTANCE)
     || hostile>friendly*PlayerBotConfig.PVP_STRENGTH_LIMIT
     || Math.floorMod(bot.getObjectId()*31L+target.getObjectId()*17L,100)>=PlayerBotConfig.PVP_AGGRESSION
     || nearby.stream().filter(p->!bot.isEnemy(p) && p.getTarget()==target).count()>=PlayerBotConfig.PVP_ATTACKERS_PER_TARGET)continue;
   }
   // New engagement needs actual sight; retained combat can approach a briefly broken sight line.
   if(!continuing && !GeoService.getInstance().canSee(bot,target))continue;
   double value=(explicit?200:0)+(defence?100:0)+(continuing?18:0)-PositionUtil.getDistance(bot,target)
    +(100-hp(target))*.2;
   var cast=target.getCastingSkill();
   if(cast!=null && cast.getSkillTemplate().hasAnyEffect(EffectType.HEAL,EffectType.HEALINSTANT,EffectType.CASEHEAL,EffectType.RESURRECT))value+=35;
   else if(target instanceof Player p && roleFor(p.getPlayerClass())==Role.HEALER && p.getController().isInCombat())value+=15;
   if(value>score || value==score && best!=null && target.getObjectId()<best.getObjectId()){score=value;best=target;}
  }
  if(best!=current) {
   if(best!=null){originX=bot.getX();originY=bot.getY();originZ=bot.getZ();started=now;}
   current=best;
  }
  return best;
 }
 private record Distance<T extends Creature>(T actor,double distance,int id){}
 private static <T extends Creature> List<T> nearest(Player bot,java.util.stream.Stream<T> candidates,int limit){
  // Moving actors must not change comparison keys part way through TimSort.
  // Companion selection retains its existing path; world decisions snapshot once.
  return candidates.map(c->new Distance<>(c,PositionUtil.getDistance(bot,c),c.getObjectId()))
   .sorted(Comparator.<Distance<T>>comparingDouble(Distance::distance).thenComparingInt(Distance::id))
   .limit(limit).map(Distance::actor).toList();
 }
 private static double power(Player p){return Math.pow(Math.max(1,p.getLevel()),2)*(.35+.65*hp(p)/100);}
 static double hp(Creature p){return PlayerBotDefense.health(p);}
 private static double mp(Creature p){return PlayerBotDefense.mana(p);}
 boolean retreat(Creature target){return session.autonomous() && hp(session.bot())<PlayerBotConfig.PVP_RETREAT_HEALTH
  && target.getTarget()==session.bot();}
 static boolean controlUseful(PlayerBotSkills.Entry entry,Creature target) {
  return playerOwned(target) && !held(target) && entry.kind()==SkillKind.CONTROL
   && entry.template().hasAnyEffect(EffectType.STUN,EffectType.ROOT,EffectType.SNARE,EffectType.SLEEP,EffectType.FEAR,EffectType.SILENCE,EffectType.PARALYZE);
 }
 boolean active(){return current!=null;}
 void clear(){current=null;}
}
