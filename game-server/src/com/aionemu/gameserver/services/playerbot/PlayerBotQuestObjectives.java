/* ChooseTravelTargetAction::SetGroupTarget/SetCurrentTarget and quest-gated
 * OpenLootAction, mod-playerbots 037c01418b5d01506917a3db9b44fd56ac5f965c.
 * GPL-2.0-or-later; see third-party/playerbots/AUTHORS.md.
 * Local Aion objectives only: native handlers retain all progress and loot rights. */
package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.questEngine.model.QuestStatus;
import com.aionemu.gameserver.services.DialogService;
import com.aionemu.gameserver.services.drop.DropRegistrationService;
import com.aionemu.gameserver.utils.PositionUtil;
import static com.aionemu.gameserver.services.playerbot.PlayerBotEngine.*;
import static com.aionemu.gameserver.services.playerbot.PlayerBotQuestRoutes.*;

/** One decision consumed by routes, conversations, objects, rewards and new pulls. */
final class PlayerBotQuestObjectives {
 static final class Decision {
  final PlayerBotSession session;
  Goal goal; int variables,actor,world,instance; QuestStatus status;
  boolean active,copied,paused; long nextScan,lastProgress,lastActive; double best;
  final Map<String,Long> excluded=new HashMap<>();
  final Map<String,Integer> failures=new HashMap<>();
  final Map<Integer,Long> itemsBefore=new HashMap<>();
  Decision(PlayerBotSession session){this.session=session;}
 }
 private static final Map<Integer,Decision> DECISIONS=new ConcurrentHashMap<>();
 static void prepare(PlayerBotSession session,boolean allowed,boolean mission) {
  if(mission){close(session.bot());return;} // Explicit missions keep their own range/order contract.
  var d=DECISIONS.computeIfAbsent(session.bot().getObjectId(),id->new Decision(session));
  if(!allowed){d.active=false;d.paused=true;return;}
  choose(session,System.currentTimeMillis());
 }
 static boolean current(Player bot,Decision d) {
  if(d.goal==null)return false;
  var q=bot.getQuestStateList().getQuestState(d.goal.quest());
  return q!=null && q.getStatus()==d.status && q.getQuestVars().getQuestVars()==d.variables;
 }
 static Npc actor(Player owner,int oid) {
  if(oid==0)return null;Npc[] found={null};
  owner.getKnownList().forEachNpc(n->{if(n.getObjectId()==oid && owner.getKnownList().sees(n))found[0]=n;});
  return found[0];
 }
 static boolean eligible(PlayerBotSession session,Goal goal,Npc npc) {
  var owner=session.owner();var bot=session.bot();
  if(bot.getWorldId()!=owner.getWorldId() || bot.getInstanceId()!=owner.getInstanceId() || npc==null || npc.getNpcId()!=goal.npc() || !npc.isSpawned() || npc.getWorldId()!=owner.getWorldId()
   || npc.getInstanceId()!=owner.getInstanceId() || !owner.getKnownList().sees(npc)
   || !PositionUtil.isInRange(owner,npc,leash(goal.kind())))return false;
  if(goal.kind()==Kind.HUNT)return !npc.isDead() && !npc.isFlag()
   && PlayerBotPartyBehavior.hostileObjective(bot,npc,Set.of(goal.npc())) && PlayerBotPartyBehavior.isolated(owner,bot,npc);
  if(goal.kind()==Kind.OBJECT) {
   if(!"quest_use_item".equals(npc.getAi().getName()) || !PlayerBotQuestObjects.safe(owner,bot,npc))return false;
   var loot=DropRegistrationService.getInstance().getDropRegistrationMap().get(npc.getObjectId());
   return npc.isDead() ? loot!=null && loot.isAllowedToLoot(bot) && (!loot.isBeingLooted() || bot.isLooting() && bot.getLootingNpcOid()==npc.getObjectId()) : DialogService.isInteractionAllowed(bot,npc);
  }
  return !npc.isDead() && npc.getMaster()==npc && !bot.isEnemy(npc) && DialogService.isInteractionAllowed(bot,npc);
 }
 static boolean wanted(PlayerBotSession session,Goal goal) {
  return enabled(goal.kind(),Boolean.TRUE.equals(PlayerBotPartyBehavior.snapshot(session).get("questCombat")))
   && PlayerBotQuestSync.wanted(session.owner(),session.bot(),goal.quest())
   && ids(session.owner(),session.bot(),goal.quest(),goal.kind()).contains(goal.npc());
 }
 static boolean valid(Decision d) {
  var s=d.session;var owner=s.owner();var bot=s.bot();
  return !owner.isDead() && !bot.isDead() && !s.closing() && current(bot,d) && owner.getWorldId()==d.world && owner.getInstanceId()==d.instance
   && bot.getWorldId()==d.world && bot.getInstanceId()==d.instance && wanted(s,d.goal)
   && (d.actor==0 ? !d.goal.visible() && PositionUtil.getDistance(owner,d.goal.point().x(),d.goal.point().y(),d.goal.point().z())<=leash(d.goal.kind())
    : eligible(s,d.goal,actor(owner,d.actor)));
 }
 static boolean matches(Player bot,Kind kind,int quest,Npc npc) {
  var d=DECISIONS.get(bot.getObjectId());if(d==null)return true;
  return d.active && valid(d) && d.goal.kind()==kind && d.goal.quest()==quest
   && npc!=null && d.actor==npc.getObjectId() && eligible(d.session,d.goal,npc);
 }
 static boolean job(Player bot,PlayerBotQuests.Job job) {
  var d=DECISIONS.get(bot.getObjectId());if(d==null)return true;
  return job.turnIn() ? matches(bot,Kind.REWARD,job.quest(),job.npc()) : d.active && d.goal==null;
 }
 static boolean pull(Player bot,Npc npc) {
  var d=DECISIONS.get(bot.getObjectId());return d==null || d.goal!=null && matches(bot,Kind.HUNT,d.goal.quest(),npc);
 }
 static boolean objectMatch(Player bot,Npc npc) {
  for(var q:com.aionemu.gameserver.services.QuestService.getQuestDrop(npc.getNpcId()))
   if(PlayerBotQuestObjects.needs(bot,bot.getQuestStateList().getQuestState(q.getQuestId()),q) && matches(bot,Kind.OBJECT,q.getQuestId(),npc))return true;
  return false;
 }
 static void objectStarted(Player bot,Npc npc) {
  var d=DECISIONS.get(bot.getObjectId());if(d==null || d.goal==null)return;
  d.itemsBefore.clear();for(var drop:com.aionemu.gameserver.services.QuestService.getQuestDrop(npc.getNpcId()))
   if(drop.getQuestId()==d.goal.quest())d.itemsBefore.put(drop.getItemId(),bot.getInventory().getItemCountByItemId(drop.getItemId()));
 }
 static void objectFinished(Player bot) {
  var d=DECISIONS.get(bot.getObjectId());if(d==null || d.itemsBefore.isEmpty())return;
  boolean received=false;for(var before:d.itemsBefore.entrySet())if(bot.getInventory().getItemCountByItemId(before.getKey())>before.getValue())received=true;
  d.itemsBefore.clear();attempted(bot,received);
 }
 static void rewardAttempted(Player bot,int quest) {
  var state=bot.getQuestStateList().getQuestState(quest);
  attempted(bot,state!=null && state.getStatus()!=QuestStatus.REWARD && !PlayerBotQuestMetadata.ready(bot,quest));
 }
 static boolean peers(Decision source,PlayerBotSession follower,long now) {
  var a=source.session;var b=follower;var q=source.goal==null ? null : b.bot().getQuestStateList().getQuestState(source.goal.quest());
  return a.bot()!=b.bot() && a.owner()==b.owner() && source.active && !source.copied && now-source.lastActive<=3000
   && !a.closing() && !a.bot().isDead() && a.bot().getPlayerGroup()==b.bot().getPlayerGroup()
   && b.bot().getPlayerGroup()!=null && valid(source) && q!=null
   && wanted(b,source.goal) && (source.actor==0 || eligible(b,source.goal,actor(b.owner(),source.actor)));
 }
 static Goal choose(PlayerBotSession session,long now) {
  var bot=session.bot();var owner=session.owner();
  var d=DECISIONS.computeIfAbsent(bot.getObjectId(),id->new Decision(session));
  d.active=true;d.lastActive=now;
  if(session.closing() || owner.isDead() || bot.isDead() || bot.getWorldId()!=owner.getWorldId() || bot.getInstanceId()!=owner.getInstanceId()) {
   d.active=false;d.goal=null;d.nextScan=0;return null;
  }
  if(d.world!=owner.getWorldId() || d.instance!=owner.getInstanceId()) {
   d.goal=null;d.nextScan=0;d.excluded.clear();d.failures.clear();d.world=owner.getWorldId();d.instance=owner.getInstanceId();
  }
  d.excluded.entrySet().removeIf(e->e.getValue()<=now);
  if(d.goal!=null) {
   // A static spawn is a travel hint. Bind the actual visible actor on arrival.
   if(d.actor==0 && current(bot,d) && wanted(session,d.goal)) {
    Npc[] resolved={null};owner.getKnownList().forEachNpc(npc->{
     if(eligible(session,d.goal,npc) && PositionUtil.getDistance(npc,d.goal.point().x(),d.goal.point().y(),d.goal.point().z())<=4
      && (resolved[0]==null || npc.getObjectId()<resolved[0].getObjectId()) && !excluded(d,d.goal,npc.getObjectId()))resolved[0]=npc;
    });
    if(resolved[0]!=null)d.actor=resolved[0].getObjectId();
   }
   if(valid(d)) {
    Npc npc=actor(owner,d.actor);
    if(npc!=null)d.goal=new Goal(d.goal.quest(),d.goal.npc(),d.goal.kind(),new PlayerBotNavigation.Point(npc.getX(),npc.getY(),npc.getZ()),d.copied,true,PositionUtil.getDistance(bot,npc));
    double distance=PositionUtil.getDistance(bot,d.goal.point().x(),d.goal.point().y(),d.goal.point().z());
    if(d.paused || distance<d.best-1 || d.actor!=0 && distance<=3){d.best=distance;d.lastProgress=now;}
    d.paused=false;
    if(now-d.lastProgress<12000)return d.goal;
    d.excluded.put(identity(d.goal,d.actor,d.variables),now+60000);
   }
   d.goal=null;d.nextScan=0;
  }
  d.paused=false;if(now<d.nextScan)return null;d.nextScan=now+2000;
  List<Goal> choices=new ArrayList<>();Map<Goal,Integer> actors=new HashMap<>();
  // Copy the actual peer destination, never merely prefer every NPC in the same quest.
  for(var source:DECISIONS.values())if(peers(source,session,now)) {
   Goal g=source.goal;var copy=new Goal(g.quest(),g.npc(),g.kind(),g.point(),true,g.visible(),PositionUtil.getDistance(bot,g.point().x(),g.point().y(),g.point().z()));
   if(!excluded(d,copy,source.actor)){choices.add(copy);actors.put(copy,source.actor);}
  }
  int scanned=0;
  for(var q:bot.getQuestStateList().getUncompletedQuests()) {
   if(scanned>=2048 || choices.size()>=128)break;
   if(!PlayerBotQuestSync.wanted(owner,bot,q.getQuestId()))continue;
   for(var kind:Kind.values()) {
    if(!enabled(kind,Boolean.TRUE.equals(PlayerBotPartyBehavior.snapshot(session).get("questCombat"))))continue;
    for(int npcId:new TreeSet<>(ids(owner,bot,q.getQuestId(),kind))) {
     if(++scanned>2048 || choices.size()>=128)break;
     var template=DataManager.NPC_DATA.getNpcTemplate(npcId);if(template==null)continue;
     owner.getKnownList().forEachNpc(npc->{
      if(choices.size()>=128 || npc.getNpcId()!=npcId)return;
      Goal g=new Goal(q.getQuestId(),npcId,kind,new PlayerBotNavigation.Point(npc.getX(),npc.getY(),npc.getZ()),false,true,PositionUtil.getDistance(bot,npc));
      if(eligible(session,g,npc) && !excluded(d,g,npc.getObjectId())){choices.add(g);actors.put(g,npc.getObjectId());}
     });
     if(owner.isInInstance() || kind==Kind.HUNT || DataManager.SPAWNS_DATA==null)continue;
     for(var group:DataManager.SPAWNS_DATA.getSpawnsForNpc(owner.getWorldId(),npcId)) {
      if(++scanned>2048)break;if(group.isTemporarySpawn() || group.getHandlerType()!=null)continue;
      for(var spawn:group.getSpawnTemplates()) {
       if(++scanned>2048 || choices.size()>=128)break;
       var point=new PlayerBotNavigation.Point(spawn.getX(),spawn.getY(),spawn.getZ());
       if(spawn.isAerialSpawn() || !finite(point) || PositionUtil.getDistance(owner,point.x(),point.y(),point.z())>leash(kind))continue;
       Goal g=new Goal(q.getQuestId(),npcId,kind,point,false,false,PositionUtil.getDistance(bot,point.x(),point.y(),point.z()));
       if(!excluded(d,g,0))choices.add(g);
      }
     }
    }
   }
  }
  d.goal=select(choices);d.actor=d.goal==null ? 0 : actors.getOrDefault(d.goal,0);d.copied=d.goal!=null && d.goal.grouped();
  if(d.goal!=null){var q=bot.getQuestStateList().getQuestState(d.goal.quest());d.variables=q.getQuestVars().getQuestVars();d.status=q.getStatus();}
  d.best=d.goal==null ? Double.POSITIVE_INFINITY : d.goal.distance();d.lastProgress=now;return d.goal;
 }
 static String identity(Goal g,int actor,int variables){return g.quest()+":"+variables+":"+g.kind()+":"+(actor==0 ? g.npc()+":"+g.point() : actor);}
 static boolean excluded(Decision d,Goal g,int actor) {
  var q=d.session.bot().getQuestStateList().getQuestState(g.quest());return q==null || d.excluded.containsKey(identity(g,actor,q.getQuestVars().getQuestVars()));
 }
 static void attempted(Player bot,boolean success) {
  var d=DECISIONS.get(bot.getObjectId());if(d==null || d.goal==null)return;
  String key=identity(d.goal,d.actor,d.variables);
  if(success){d.goal=null;d.nextScan=0;d.failures.remove(key);return;}
  if(d.failures.merge(key,1,Integer::sum)>=3){d.excluded.put(key,System.currentTimeMillis()+60000);d.goal=null;d.nextScan=0;d.failures.remove(key);}
 }
 static void announce(Player bot) {
  var d=DECISIONS.get(bot.getObjectId());if(d==null || d.goal==null)return;
  PlayerBotQuestSync.notice(bot,"I am heading to "+DataManager.NPC_DATA.getNpcTemplate(d.goal.npc()).getName()+" for "
   +com.aionemu.gameserver.utils.ChatUtil.quest(d.goal.quest())+" ("+d.goal.kind().name().toLowerCase(Locale.ROOT)+").",
   "route:"+identity(d.goal,d.actor,d.variables),60000);
 }
 static Trigger trigger(PlayerBotSession session,PlayerBotNavigation navigation) {
  var d=DECISIONS.get(session.bot().getObjectId());if(d==null || !d.active || d.goal==null)return null;Goal goal=d.goal;
  return new Trigger(()->true,new Action() {
   public String name(){return "quest route "+goal.quest()+" -> "+goal.npc();}
   public boolean isUseful(){return !session.closing() && d.goal==goal && valid(d) && !session.owner().isDead()
    && !session.owner().getMoveController().isInMove() && !session.owner().getController().isInCombat() && !session.bot().getController().isInCombat()
    && !session.bot().isFlying() && !session.owner().isFlying();}
   public boolean isPossible(){return session.bot().canPerformMove() && !session.bot().isCasting() && !session.bot().isLooting();}
   public boolean execute(){boolean moved=navigation.move(goal.point().x(),goal.point().y(),goal.point().z());if(moved)announce(session.bot());return moved;}
  },()->DEFAULT+1);
 }
 static void close(Player bot){DECISIONS.remove(bot.getObjectId());}
 private PlayerBotQuestObjectives(){}
}
