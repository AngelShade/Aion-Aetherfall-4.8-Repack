/* Follow/formation and tank/DPS assist adapted from pinned mod-playerbots
 * FollowActions.cpp, MovementActions.cpp and assist strategies. Teleport thresholds,
 * bounded catch-up speed and conservative pull selection are Aion adaptations.
 * GPL-2.0-or-later; third-party/playerbots/AUTHORS.md. */
package com.aionemu.gameserver.services.playerbot;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import com.aionemu.gameserver.model.gameobjects.*;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.questEngine.QuestEngine;
import com.aionemu.gameserver.questEngine.model.QuestStatus;
import com.aionemu.gameserver.services.QuestService;
import com.aionemu.gameserver.utils.PositionUtil;
import com.aionemu.gameserver.world.geo.GeoService;
import static com.aionemu.gameserver.services.playerbot.PlayerBotRules.*;

public final class PlayerBotPartyBehavior {
 static final class State {
  final PlayerBotSession session; final int account, character;
  volatile Role role; volatile Order order = Order.FOLLOW; volatile boolean enabled = true;
  long nextTeleport; int sharedTarget;
  State(PlayerBotSession session) {
   this.session=session;account=session.owner().getAccount().getId();character=session.bot().getObjectId();
   Path path=path();if(Files.exists(path))try(var in=Files.newInputStream(path)) {
    var p=new Properties();p.load(in);
    if(!p.getProperty("account", "").equals(Integer.toString(account)) || !p.getProperty("character", "").equals(Integer.toString(character)))throw new IllegalStateException("Companion behavior settings owner mismatch");
    enabled=Boolean.parseBoolean(p.getProperty("questCombat","true"));
   }catch(Exception error){throw new IllegalStateException("Cannot load companion behavior settings",error);}
  }
  Path path(){return Path.of("config","playerbots","behavior-character-"+character+".properties");}
  void save() {
   try {
    Files.createDirectories(path().getParent());var p=new Properties();p.setProperty("account",Integer.toString(account));p.setProperty("character",Integer.toString(character));p.setProperty("questCombat",Boolean.toString(enabled));
    Path temp=Files.createTempFile(path().getParent(),"bot-behavior-",".tmp");
    try{try(var out=Files.newOutputStream(temp)){p.store(out,"Companion nearby quest combat");}try{Files.move(temp,path(),StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(temp,path(),StandardCopyOption.REPLACE_EXISTING);}}finally{Files.deleteIfExists(temp);}
   }catch(Exception error){throw new IllegalStateException("Cannot save companion behavior settings",error);}
  }
 }
 private static final Map<Integer,State> STATES=new ConcurrentHashMap<>();
 private static final ThreadLocal<Boolean> CATCH_UP=ThreadLocal.withInitial(()->false);
 static State state(PlayerBotSession session){return STATES.computeIfAbsent(session.bot().getObjectId(),id->new State(session));}
 static void update(PlayerBotSession session,Role role,Order order){State s=state(session);s.role=role;s.order=order;}
 public static void configure(PlayerBotSession session,boolean enabled){synchronized(session){if(session.closing())throw new IllegalArgumentException("Companion is saving/dismissing.");State s=state(session);boolean old=s.enabled;s.enabled=enabled;try{s.save();}catch(RuntimeException e){s.enabled=old;throw e;}if(!enabled)s.sharedTarget=0;}}
 static Map<String,Object> snapshot(PlayerBotSession session){return Map.of("questCombat",state(session).enabled);}
 static void close(PlayerBotSession session){STATES.remove(session.bot().getObjectId());PlayerBotSupplies.close(session.bot());PlayerBotRecovery.close(session.owner());PlayerBotTemporary.close(session);PlayerBotTank.close(session.bot());PlayerBotFormation.close(session.bot());PlayerBotTransfers.close(session.bot());PlayerBotQuestConversations.close(session.bot());PlayerBotQuestObjects.close(session.bot());PlayerBotPartyCompletion.close(session.bot());}
 static boolean catchUpNeeded(double distance,boolean ownerCombat,Order order){return Double.isFinite(distance) && (order==Order.FOLLOW || order==Order.PASSIVE) && distance>(ownerCombat ? 18 : 60);}
 static boolean canRelocate(PlayerBotSession session) {
  Player owner=session.owner(),bot=session.bot();
  return !bot.isDead() && !owner.isDead() && owner.isSpawned() && !bot.getController().isInCombat()
   && bot.getAggroList().stream().findAny().isEmpty() && (!owner.getController().isInCombat()
    || CATCH_UP.get() && owner.getWorldId()==bot.getWorldId() && owner.getInstanceId()==bot.getInstanceId());
 }
 static boolean catchUp(PlayerBotSession session) {
  Player owner=session.owner(),bot=session.bot();State s=state(session);long now=System.currentTimeMillis();
  if(session.closing() || now<s.nextTeleport || !catchUpNeeded(PositionUtil.getDistance(owner,bot),owner.getController().isInCombat(),s.order)
   || bot.getWorldId()!=owner.getWorldId() || bot.getInstanceId()!=owner.getInstanceId() || !owner.isOnline() || bot.isCasting() || !bot.canPerformMove())return false;
  CATCH_UP.set(true);
  try{if(!canRelocate(session) || !PlayerBotService.getInstance().relocate(session))return false;s.nextTeleport=now+5000;s.sharedTarget=0;PlayerBotQuestSync.returnToOwner(bot);PlayerBotQuestSync.notice(bot,"I caught up with you.","catch-up",30000);return true;}finally{CATCH_UP.remove();}
 }
 public static double speedMultiplier(Player bot,float x,float y,float z) {
  State s=STATES.get(bot.getObjectId());if(s==null || s.order!=Order.FOLLOW && s.order!=Order.PASSIVE)return 1;
  Player owner=s.session.owner();
  if(bot.getController().isInCombat() || owner.getController().isInCombat() || bot.getEffectController().isUnderFear() || bot.getEffectController().isConfused()
   || bot.getEffectController().isAbnormalSet(com.aionemu.gameserver.skillengine.effect.AbnormalState.SLOW) || !bot.canPerformMove()
   || bot.getWorldId()!=owner.getWorldId() || bot.getInstanceId()!=owner.getInstanceId() || PositionUtil.getDistance(bot,owner)<=8
   || PositionUtil.getDistance(owner,x,y,z)>8)return 1;
  return catchUpSpeed(bot.getGameStats().getMovementSpeedFloat(),owner.getGameStats().getMovementSpeedFloat());
 }
 static double catchUpSpeed(double botSpeed,double ownerSpeed){return !Double.isFinite(botSpeed) || !Double.isFinite(ownerSpeed) || botSpeed<=0 ? 1 : Math.min(2,Math.max(1.1,ownerSpeed*1.1/botSpeed));}
 static boolean canInitiate(Role role,boolean tankPresent){return role==Role.TANK || !tankPresent && (role==Role.MELEE || role==Role.RANGED);}
 static boolean hostileObjective(Player bot,Npc npc,Set<Integer> required) {
  return required.contains(npc.getNpcId()) && npc.isSpawned() && !npc.isDead() && npc.getMaster()==npc && bot.isEnemy(npc)
   && npc.getLevel()<=bot.getLevel()+2 && (npc.getObjectTemplate().getRating()==com.aionemu.gameserver.model.templates.npc.NpcRating.NORMAL || npc.getObjectTemplate().getRating()==com.aionemu.gameserver.model.templates.npc.NpcRating.JUNK);
 }
 static Set<Integer> targets(Player bot,int mission) {
  Set<Integer> ids=new HashSet<>();
  for(var q:bot.getQuestStateList().getUncompletedQuests())if(q.getStatus()==QuestStatus.START && (mission==0 || q.getQuestId()==mission) && !PlayerBotQuestSync.skipped(bot,q.getQuestId())) {
   ids.addAll(QuestEngine.getInstance().getRequiredKillNpcIds(bot,q.getQuestId()));
   var t=com.aionemu.gameserver.dataholders.DataManager.QUEST_DATA.getQuestById(q.getQuestId());
   if(t!=null)for(var drop:t.getQuestDrop())if(PlayerBotQuestObjects.needs(bot,q,drop))ids.add(drop.getNpcId());
  }
  return ids;
 }
 static boolean isolated(Player owner,Player bot,Npc target) {
  boolean[] safe={true};owner.getKnownList().forEachNpc(other->{
   if(other!=target && other.isSpawned() && !other.isDead() && other.getMaster()==other && bot.isEnemy(other)
    && other.getWorldId()==target.getWorldId() && other.getInstanceId()==target.getInstanceId() && PositionUtil.isInRange(other,target,12))safe[0]=false;
  });return safe[0];
 }
 static Npc hunt(Player owner,Player bot,int mission) {
  State s=STATES.get(bot.getObjectId());if(s==null || !s.enabled || s.order!=Order.FOLLOW || s.role==null || owner.isDead() || owner.isFlying() || owner.getMoveController().isInMove())return null;
  var group=owner.getPlayerGroup();if(group==null)return null;
  boolean tank=group.getMembers().stream().anyMatch(p->{State other=STATES.get(p.getObjectId());return other!=null && other.enabled && other.role==Role.TANK && other.order==Order.FOLLOW && !p.isDead();});
  // One claim per party: followers assist the initiator instead of independently pulling.
  Set<Integer> required=targets(bot,mission);List<Npc> candidates=new ArrayList<>();
  owner.getKnownList().forEachNpc(npc->{
   if(npc.getWorldId()!=owner.getWorldId() || npc.getInstanceId()!=owner.getInstanceId() || !PositionUtil.isInRange(owner,npc,25) || !bot.getKnownList().sees(npc) || !GeoService.getInstance().canSee(bot,npc))return;
   boolean claimed=group.getMembers().stream().anyMatch(p->{State other=STATES.get(p.getObjectId());return other!=null && other.sharedTarget==npc.getObjectId() && other.enabled && !p.isDead();});
   if(claimed && npc.isSpawned() && !npc.isDead() && bot.isEnemy(npc)){candidates.add(npc);return;}
   if(PlayerBotQuestObjectives.pull(bot,npc) && canInitiate(s.role,tank) && hostileObjective(bot,npc,required) && npc.getAggroList().stream().findAny().isEmpty() && isolated(owner,bot,npc))candidates.add(npc);
  });
  Npc chosen=candidates.stream().min(Comparator.comparingDouble(n->PositionUtil.getDistance(owner,n)-(group.getMembers().stream().anyMatch(p->{State other=STATES.get(p.getObjectId());return other!=null && other.sharedTarget==n.getObjectId() && other.enabled;}) ? 10000 : 0))).orElse(null);
  s.sharedTarget=chosen==null ? 0 : chosen.getObjectId();return chosen;
 }
 static boolean explicit(Player bot,Npc npc) {State s=STATES.get(bot.getObjectId());return s!=null && s.enabled && s.order==Order.FOLLOW && s.sharedTarget==npc.getObjectId() && PositionUtil.isInRange(s.session.owner(),npc,25) && !s.session.owner().isDead();}
 private PlayerBotPartyBehavior(){}
}
