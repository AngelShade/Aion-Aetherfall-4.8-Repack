package com.aionemu.gameserver.services.playerbot;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.skillengine.properties.TargetRelationAttribute;
import com.aionemu.gameserver.utils.PositionUtil;

/** Calls visible in the native party: marks, current attacks, and the people leading the way. */
final class PlayerBotDredgionParty {
 private static final long ATTACK_SIGNAL_MS=12000;

 record PvpFocus(Creature target,boolean human) {}

 static Npc humanObjective(Player bot,Predicate<Npc> eligible) {
  var team=bot.getCurrentTeam();
  if(team==null)return null;
  int marked=PlayerBotMarks.nativeAttackMark(bot,id->bot.getWorldMapInstance().getObject(id) instanceof Npc npc
   && !PlayerBotMarks.nativeCcMarked(bot,npc) && eligible.test(npc));
  if(marked!=0 && bot.getWorldMapInstance().getObject(marked) instanceof Npc npc)return npc;
  Creature called=attackedTarget(bot,team.getLeaderObject(),team.getMembers(),
   target->target instanceof Npc npc && !PlayerBotMarks.nativeCcMarked(bot,npc) && eligible.test(npc),false,System.currentTimeMillis());
  return called instanceof Npc npc?npc:null;
 }

 static PvpFocus pvpFocus(Player bot,List<Creature> visible,long now,boolean shareBotTargets) {
  var team=bot.getCurrentTeam();
  if(team==null || visible.isEmpty())return new PvpFocus(null,false);
  Predicate<Creature> eligible=target->visible.contains(target) && !PlayerBotPvp.held(target) && !PlayerBotMarks.nativeCcMarked(bot,target);
  int marked=PlayerBotMarks.nativeAttackMark(bot,id->visible.stream().anyMatch(target->target.getObjectId()==id && eligible.test(target)));
  if(marked!=0)for(Creature target:visible)if(target.getObjectId()==marked)return new PvpFocus(target,true);
  var members=team.getMembers();
  Creature human=attackedTarget(bot,team.getLeaderObject(),members,eligible,false,now);
  if(human!=null)return new PvpFocus(human,true);
  Creature shared=shareBotTargets?attackedTarget(bot,team.getLeaderObject(),members,eligible,true,now):null;
  return new PvpFocus(shared,false);
 }

 private static Creature attackedTarget(Player bot,Player leader,List<Player> members,Predicate<Creature> eligible,boolean bots,long now) {
  Map<Creature,Integer> votes=new HashMap<>();
  for(Player member:members) {
   if(member==bot || member.isPlayerBot()!=bots || !present(bot,member))continue;
   Creature target=attack(member,now);
   if(target==null || !eligible.test(target))continue;
   if(!bots && member==leader)return target;
   votes.merge(target,1,Integer::sum);
  }
  Creature best=null;
  int count=0;
  for(var vote:votes.entrySet()) {
   Creature target=vote.getKey();
   if(best==null || vote.getValue()>count || vote.getValue()==count && target.getObjectId()<best.getObjectId()) {
    best=target;
    count=vote.getValue();
   }
  }
  return best;
 }

 private static Creature attack(Player member,long now) {
  var cast=member.getCastingSkill();
  Creature castTarget=cast==null?null:cast.getFirstTarget();
  if(castTarget!=null && cast.getSkillTemplate().getProperties().getTargetRelation()==TargetRelationAttribute.ENEMY
   && member.isEnemy(castTarget))return castTarget;
  if(!(member.getTarget() instanceof Creature target) || !member.isEnemy(target))return null;
  return target.getAggroList().stream().anyMatch(info->info.getAttacker().getMaster()==member
   && info.getDamage()>0 && now-info.getLastInteractionTime()<ATTACK_SIGNAL_MS)?target:null;
 }

 private static boolean present(Player bot,Player member) {
  return member.isPlaying() && member.isSpawned() && !member.isDead()
   && member.getWorldMapInstance()==bot.getWorldMapInstance() && member.getRace()==bot.getRace();
 }

 /** Follow a moving human lead. When a bot has the leader badge, a sole human can lead the way. */
 static Player guide(Player bot) {
  var team=bot.getCurrentTeam();
  if(team==null)return null;
  Player leader=team.getLeaderObject();
  if(!leader.isPlayerBot() && present(bot,leader))return leader;
  Player only=null;
  for(Player member:team.getMembers()) {
   if(member.isPlayerBot() || !present(bot,member))continue;
   if(only!=null)return null;
   only=member;
  }
  return only;
 }

 static boolean walkingWith(Player bot,Player guide) {
  return guide.getMoveController().isInMove() && !guide.isFlying() && !guide.getController().isInCombat()
   && PositionUtil.isInRange(bot,guide,45) && bot.getKnownList().sees(guide);
 }

 private PlayerBotDredgionParty() {}
}
