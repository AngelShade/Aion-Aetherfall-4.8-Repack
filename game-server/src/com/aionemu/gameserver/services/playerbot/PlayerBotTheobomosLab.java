/* Encounter role/target actions adapted in purpose from mod-playerbots 037c01418b5d01506917a3db9b44fd56ac5f965c:
 * Aq20Actions/Strategy, NaxxActions_Gluth and NaxxActions_Grobbulus. GPL-2.0-or-later; AUTHORS.md.
 * Native Aion handlers, live effects, visible actors and movement remain authoritative. */
package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import java.util.function.BooleanSupplier;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.skillengine.effect.DispelBuffEffect;
import com.aionemu.gameserver.skillengine.effect.DispelNpcBuffEffect;
import com.aionemu.gameserver.skillengine.effect.HostileUpEffect;
import com.aionemu.gameserver.skillengine.model.SkillTemplate;
import com.aionemu.gameserver.skillengine.properties.TargetRangeAttribute;
import com.aionemu.gameserver.utils.PositionUtil;
import static com.aionemu.gameserver.services.playerbot.PlayerBotEngine.*;
import static com.aionemu.gameserver.services.playerbot.PlayerBotRules.*;

/** No room pulls or scripted credit: react only to the current visible native encounter. */
final class PlayerBotTheobomosLab {
 private static final int MAP=310110000, SILIKOR=214668, TRIROAN=214669, ORB=280973;
 private record MechanicAction(String name,BooleanSupplier useful,BooleanSupplier execution) implements Action {
  public boolean isUseful(){return useful.getAsBoolean();}
  public boolean isPossible(){return true;}
  public boolean execute(){return execution.getAsBoolean();}
 }
 private static boolean guard(Npc npc){return npc.getNpcId()==280971 || npc.getNpcId()==280972;}
 private static boolean spirit(Npc npc){return npc.getNpcId()>=280975 && npc.getNpcId()<=280978;}
 private static Player observer(Creature actor){return actor instanceof Player player?player:actor.getMaster() instanceof Player master?master:null;}
 private static boolean visible(Creature actor,Npc npc){
  Player player=observer(actor);
  return player!=null && npc.getWorldId()==MAP && actor.getWorldId()==MAP && npc.getInstanceId()==actor.getInstanceId()
   && !npc.isDead() && PlayerBotTargetEligibility.visible(player,npc);
 }
 private static boolean engaged(Npc npc,List<Player> party){return party.stream().anyMatch(p->npc.getAggroList().isHating(p)
  || p.getSummon()!=null && npc.getAggroList().isHating(p.getSummon()));}
 static boolean mechanicEnemy(Player bot,Npc npc,List<Player> party){
  if(bot.getWorldId()!=MAP || !visible(bot,npc))return false;
  int boss=spirit(npc)?TRIROAN:npc.getNpcId()==281053 || npc.getNpcId()==281054?SILIKOR:0;
  return boss!=0 && npc.getCreator() instanceof Npc creator && creator.getNpcId()==boss
   && visible(bot,creator) && engaged(creator,party);
 }
 static Npc orb(Creature actor){
  if(actor.getWorldId()!=MAP)return null;
  Player player=observer(actor);if(player==null)return null;
  List<Npc> orbs=new ArrayList<>();
  player.getKnownList().forEachNpc(n->{if(n.getNpcId()==ORB && visible(actor,n))orbs.add(n);});
  return orbs.stream().min(Comparator.comparingDouble(n->PositionUtil.getDistance(actor,n))).orElse(null);
 }
 static boolean attackable(Npc npc){
  if(npc.getWorldId()!=MAP)return true;
  return npc.getNpcId()!=ORB && !(npc.getNpcId()==SILIKOR && npc.getEffectController().hasAbnormalEffect(18481))
   && !npc.getEffectController().hasAbnormalEffect(18485);
 }
 static boolean allowsAttack(Creature actor,Npc npc){
  if(npc.getWorldId()!=MAP)return true;
  if(!attackable(npc))return false;
  if(!guard(npc))return true;
  Npc orb=orb(actor);
  // Native guardDie requires centre distance <=7; leave a movement margin for new attacks.
  return orb!=null && PositionUtil.getDistance(orb,npc)<=6;
 }
 static boolean allowsSkill(Creature actor,Npc npc,SkillTemplate skill){
  if(npc.getWorldId()!=MAP)return true;
  // A periodic effect could finish a guard after it leaves the moving orb. Do not start one during the puzzle.
  if(guard(npc) && orb(actor)!=null && PlayerBotOffense.periodic(skill))return false;
  if(allowsAttack(actor,npc))return true;
  if(npc.getNpcId()==ORB || skill.getProperties()==null || skill.getProperties().getTargetType()!=TargetRangeAttribute.ONLYONE
   || PlayerBotEnemyUtility.damagePayload(skill))return false;
  if(skill.getEffects()==null || skill.getEffects().getEffects().isEmpty())return false;
  // Exact native effect classes: Provoker/DispelBuffCounterAtk can launch another damaging skill.
  boolean taunt=skill.getEffects().getEffects().stream().allMatch(e->e.getClass()==HostileUpEffect.class);
  boolean purge=skill.getEffects().getEffects().stream().allMatch(e->e.getClass()==DispelBuffEffect.class || e.getClass()==DispelNpcBuffEffect.class);
  return guard(npc) && taunt || !attackable(npc) && purge;
 }
 static Npc target(Player bot,Role role,int commanded,List<Npc> enemies){
  if(bot.getWorldId()!=MAP || commanded!=0)return null;
  if(role==Role.TANK){Npc boss=enemies.stream().filter(n->n.getNpcId()==TRIROAN && attackable(n)).findFirst().orElse(null);if(boss!=null)return boss;}
  return enemies.stream().filter(PlayerBotTheobomosLab::attackable)
   .filter(n->spirit(n) || n.getNpcId()==281053 || n.getNpcId()==281054)
   .min(Comparator.comparingInt((Npc n)->n.isCasting()?0:1)
    .thenComparingInt(n->n.getLifeStats().getCurrentHp()).thenComparingDouble(n->PositionUtil.getDistance(bot,n))
    .thenComparingInt(Npc::getObjectId)).orElse(null);
 }
 private static boolean victim(Player bot,Npc guard){return guard.getTarget()==bot || bot.getSummon()!=null && guard.getTarget()==bot.getSummon();}
 static boolean mayApproach(Player bot,Npc npc){
  return npc.getWorldId()!=MAP || !guard(npc) || !victim(bot,npc) || allowsAttack(bot,npc);
 }
 static Trigger positioning(PlayerBotSession session,PlayerBotNavigation navigation,List<Npc> enemies){
  Player bot=session.bot();if(bot.getWorldId()!=MAP || session.closing() || bot.isDead())return null;
  Npc guard=enemies.stream().filter(PlayerBotTheobomosLab::guard).filter(n->victim(bot,n))
   .min(Comparator.comparingInt(Npc::getObjectId)).orElse(null);
  Npc orb=guard==null?null:orb(bot);
  if(orb==null || PositionUtil.getDistance(guard,orb)<=5)return null;
  return new Trigger(()->true,new MechanicAction("lure Silikor guard to the moving orb",
   ()->visible(bot,guard) && visible(bot,orb) && victim(bot,guard) && PositionUtil.getDistance(guard,orb)>5,
   ()->{
    double dx=orb.getX()-guard.getX(),dy=orb.getY()-guard.getY(),length=Math.hypot(dx,dy);
    if(length<.01)return false;
    // Stand past the orb, accounting for the ranged guard's actual native attack reach.
    float reach=guard.getGameStats().getAttackRange().getCurrent()/1000f
     +guard.getObjectTemplate().getBoundRadius().getMaxOfFrontAndSide()+bot.getObjectTemplate().getBoundRadius().getMaxOfFrontAndSide();
    float lead=Math.max(2,reach-3);
    float x=orb.getX()+(float)(dx/length*lead),y=orb.getY()+(float)(dy/length*lead),z=orb.getZ();
    if(!session.autonomous() && !PositionUtil.isInRange(session.owner(),x,y,z,40))return false;
    if(PositionUtil.isInRange(bot,x,y,z,1)){navigation.stop();return true;}
    return navigation.move(x,y,z);
   }),()->ENCOUNTER+5);
 }
 /** TriroansSummonAI casts these hostile generator bursts at its own actor after arriving. */
 static boolean generatorCast(Npc npc){
  if(npc.getWorldId()!=MAP || !spirit(npc) || !"triroan_summon".equals(npc.getAi().getName()))return false;
  var cast=npc.getCastingSkill();if(cast==null)return false;
  int expected=switch(npc.getNpcId()){case 280975->18486;case 280976->18483;case 280978->18484;default->0;};
  return expected!=0 && cast.getSkillTemplate().getSkillId()==expected;
 }
 private PlayerBotTheobomosLab(){}
}
