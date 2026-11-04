package com.aionemu.gameserver.services.playerbot;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import com.aionemu.commons.database.DatabaseFactory;
import com.aionemu.gameserver.configs.main.PlayerBotWardenConfig;
import com.aionemu.gameserver.dao.PlayerBotWardenDAO;
import com.aionemu.gameserver.model.gameobjects.*;
import com.aionemu.gameserver.model.gameobjects.player.*;
import com.aionemu.gameserver.model.templates.npc.NpcRating;
import com.aionemu.gameserver.network.aion.serverpackets.*;
import com.aionemu.gameserver.utils.*;
import com.aionemu.gameserver.world.World;
import com.aionemu.gameserver.skillengine.SkillEngine;
import com.aionemu.gameserver.skillengine.model.Effect;

/** Public services of assigned Wardens. Native class AI, movement, quests and SQL retain authority. */
public final class PlayerBotWardens {
 private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(PlayerBotWardens.class);
 static final int QUESTION=900853;
 private static final Map<Integer,Integer> ESCORTS=new ConcurrentHashMap<>();
 private static final Map<Integer,Aid> AID=new ConcurrentHashMap<>();
 private static final Map<Integer,Long> DEATHS=new ConcurrentHashMap<>(),REPORTS=new ConcurrentHashMap<>();
 private static final Map<Integer,Long> RESCUE_WAIT=new ConcurrentHashMap<>();
 private static final Map<Integer,RequestResponseHandler<Creature>> ASKING=new ConcurrentHashMap<>();
 private static final Set<Integer> DISTRESS_PENDING=ConcurrentHashMap.newKeySet();
 private static final Effect.ForceType WARD=Effect.ForceType.getInstance("ROAD_WARDEN");
 static final class Aid {int human,corpse;long next,arrived;boolean warning,fairWarning;}
 static void responseEnded(Aid aid){aid.human=0;}
 static void restored(PlayerBotRoadDuty.State s) {
  if(s.career!=PlayerBotRoadDuty.Career.WARDEN)return;var aid=AID.computeIfAbsent(s.session.bot().getObjectId(),k->new Aid());
  aid.warning=Boolean.parseBoolean(s.values.getProperty("escortWarned","false"));aid.fairWarning=Boolean.parseBoolean(s.values.getProperty("fairWarned","false"));
 }
 public static void initialize() {
  try(var c=DatabaseFactory.getConnection()){PlayerBotWardenDAO.prepare(c);PlayerBotWardenCompanies.initialize(c);}
  catch(SQLException error){throw new IllegalStateException("Cannot initialize native Warden storage",error);}
  if(com.aionemu.gameserver.configs.main.PlayerBotConfig.ROAD_DUTY && PlayerBotWardenConfig.ENABLED)PlayerBotWardenBeats.all();
 }
 private interface Work<T>{T run(Connection c)throws SQLException;}
 /** HP restoration can call onDie before a loaded bot receives its controller flags.
  * A connected, positive-account human is the only live traveller; corpse login
  * restores its existing SQL evidence in entered(), rather than recording a new death. */
 private static boolean human(Player player) {
  return !player.isPlayerBot() && player.isOnline() && player.getAccount()!=null && player.getAccount().getId()>0;
 }
 private static <T> T transact(Work<T> work)throws SQLException {
  try(var c=DatabaseFactory.getConnection()){c.setAutoCommit(false);try{T result=work.run(c);c.commit();return result;}catch(SQLException|RuntimeException e){c.rollback();throw e;}}
 }
 private static Player visitor(Player bot) {
  var s=PlayerBotRoadDuty.state(bot);return s==null || s.visitor==0 || s.escortUntil<=System.currentTimeMillis()?null:World.getInstance().getPlayer(s.visitor);
 }
 static boolean traveller(Player bot,Player human) {
  var s=PlayerBotRoadDuty.state(bot);if(s==null || !PlayerBotRoadDuty.publicWarden(bot))return false;
  var protectedHuman=visitor(bot);var aid=AID.get(bot.getObjectId());if(protectedHuman==null && aid!=null && aid.human!=0)protectedHuman=World.getInstance().getPlayer(aid.human);
  return protectedHuman!=null && (human==protectedHuman || PlayerBotTeams.same(human,protectedHuman))
   && human.getWorldId()==bot.getWorldId() && human.getInstanceId()==bot.getInstanceId() && s.route.distance(human)<=PlayerBotWardenConfig.LEASH;
 }
 static int responseTarget(Player bot){var s=PlayerBotRoadDuty.state(bot);if(s!=null && s.visitor!=0)return s.visitor;var aid=AID.get(bot.getObjectId());return aid==null?0:aid.human!=0?aid.human:aid.corpse;}
 static boolean responding(Player bot){return responseTarget(bot)!=0;}
 static boolean assisting(Player bot,Npc npc) {
  if(!PlayerBotRoadDuty.publicWarden(bot) || npc.isDead())return false;var human=visitor(bot);var aid=AID.get(bot.getObjectId());
  if(!PlayerBotWardenBeats.field(npc.getWorldId(),npc.getX(),npc.getY(),npc.getZ()))return false;
  if(human==null && aid!=null && aid.human!=0)human=World.getInstance().getPlayer(aid.human);
  if(human==null || human.isDead() || human.getWorldId()!=bot.getWorldId() || human.getInstanceId()!=bot.getInstanceId()
   || !PositionUtil.isInRange(bot,npc,PlayerBotWardenConfig.DISTRESS_RANGE))return false;
  return npc.getAggroList().isHating(human) || human.getSummon()!=null && npc.getAggroList().isHating(human.getSummon()) || human.getTarget()==npc;
 }
 static boolean distress(boolean friendly,boolean human,boolean combat,double hp,boolean elite,double range,double corridor,long now,long due) {
  return friendly && human && combat && (hp<PlayerBotWardenConfig.DISTRESS_HP || elite) && range<=PlayerBotWardenConfig.DISTRESS_RANGE
   && corridor<=PlayerBotWardenConfig.BOUNTY_CORRIDOR && now>=due;
 }
 static boolean rescue(boolean monsterDeath,boolean dead,boolean hostileNearby,boolean monstersNearby,double range,long now,long arrived) {
  return monsterDeath && dead && !hostileNearby && !monstersNearby && range<=4 && arrived>0 && now-arrived>=PlayerBotWardenConfig.RESCUE_DELAY*1000L;
 }
 /** Snapshot only this actor's known list, once per second. No population/world scan. */
 static void observe(Player bot) {
  if(!PlayerBotWardenConfig.ENABLED || !PlayerBotRoadDuty.publicWarden(bot))return;
  long now=System.currentTimeMillis();var aid=AID.computeIfAbsent(bot.getObjectId(),k->new Aid());if(now<aid.next)return;aid.next=now+1000;
  var state=PlayerBotRoadDuty.state(bot);
  if(state.visitor!=0) {
   ESCORTS.putIfAbsent(state.visitor,bot.getObjectId());var h=World.getInstance().getPlayer(state.visitor);
   if(h==null || !h.isOnline() || h.isDead() || h.getWorldId()!=bot.getWorldId() || h.getInstanceId()!=bot.getInstanceId()
    || state.route.distance(h)>PlayerBotWardenConfig.LEASH || !PlayerBotWardenBeats.field(h.getWorldId(),h.getX(),h.getY(),h.getZ()) || now>=state.escortUntil || escortEnd(state,h))end(state,"The road escort has ended.");
  }
  if(bot.isDead())return;
  boolean fighting=bot.getController().isInCombat();
  if(aid.human!=0){var h=World.getInstance().getPlayer(aid.human);if(h==null || h.isDead() || !h.getController().isInCombat() || !PositionUtil.isInRange(bot,h,PlayerBotWardenConfig.DISTRESS_RANGE))responseEnded(aid);}
  var known=new ArrayList<Player>();bot.getKnownList().forEachPlayer(p->{if(human(p) && p.getRace()==bot.getRace() && !bot.isEnemy(p) && !p.isEnemy(bot) && p.getInstanceId()==bot.getInstanceId() && PositionUtil.isInRange(bot,p,Math.max(PlayerBotWardenConfig.DISTRESS_RANGE,PlayerBotWardenConfig.RESCUE_RANGE)))known.add(p);});
  known.sort(Comparator.comparingDouble(p->PositionUtil.getDistance(bot,p)));
  for(var human:known.stream().limit(16).toList()) {
   if(!PlayerBotWardenBeats.field(human.getWorldId(),human.getX(),human.getY(),human.getZ()))continue;
   ward(bot,human);
   if(human.getTarget()==bot && PositionUtil.isInRange(bot,human,PlayerBotWardenConfig.INVITE_RANGE))report(state,human,now);
   if(fighting)continue;
   if(human.isDead() && !human.getResStatus() && now>=RESCUE_WAIT.getOrDefault(human.getObjectId(),0L) && PositionUtil.isInRange(bot,human,PlayerBotWardenConfig.RESCUE_RANGE) && DEATHS.getOrDefault(human.getObjectId(),0L)>0 && state.visitor==0 && aid.corpse==0)aid.corpse=human.getObjectId();
   boolean[] elite={false},monsterFight={false};human.getKnownList().forEachNpc(n->{if(!n.isDead() && (n.getAggroList().isHating(human) || human.getSummon()!=null && n.getAggroList().isHating(human.getSummon()))) {monsterFight[0]=true;if(n.getObjectTemplate().getRating()!=NpcRating.NORMAL && n.getObjectTemplate().getRating()!=NpcRating.JUNK)elite[0]=true;}});
   long due=Long.parseLong(state.values.getProperty("distressAt","0"));
   if(aid.human==0 && state.visitor==0 && distress(true,true,human.getController().isInCombat() && monsterFight[0],human.getLifeStats().getHpPercentage(),elite[0],PositionUtil.getDistance(bot,human),state.route.distance(human),now,due)) {
    var candidates=new ArrayList<Player>();human.getKnownList().forEachPlayer(p->{var s=PlayerBotRoadDuty.state(p);var a=AID.get(p.getObjectId());if(PlayerBotRoadDuty.publicWarden(p) && !p.isDead() && p.getRace()==human.getRace() && s.visitor==0 && (a==null || a.human==0)
     && now>=Long.parseLong(s.values.getProperty("distressAt","0")) && s.route.distance(human)<=PlayerBotWardenConfig.BOUNTY_CORRIDOR
     && PositionUtil.isInRange(p,human,PlayerBotWardenConfig.DISTRESS_RANGE))candidates.add(p);});
    candidates.sort(Comparator.comparingDouble((Player p)->PositionUtil.getDistance(p,human)).thenComparingInt(Player::getObjectId));
    requestDistress(human,candidates.stream().limit(Math.max(0,PlayerBotWardenConfig.DISTRESS_MAX)).toList(),now);
   }
  }
 }
 private static void requestDistress(Player human,List<Player> candidates,long now) {
  if(!human(human) || candidates.isEmpty() || !DISTRESS_PENDING.add(human.getObjectId()))return;
  ThreadPoolManager.getInstance().execute(()->{
   try {
    if(!human(human) || !transact(c->PlayerBotWardenDAO.distress(c,human.getObjectId(),now,PlayerBotWardenConfig.DISTRESS_COOLDOWN*1000L)))return;
    for(var bot:candidates) {var s=PlayerBotRoadDuty.state(bot);if(s==null)continue;synchronized(s.session) {
     var aid=AID.computeIfAbsent(bot.getObjectId(),k->new Aid());
     if(!PlayerBotRoadDuty.publicWarden(bot) || bot.isDead() || s.visitor!=0 || aid.human!=0 || aid.corpse!=0 || human.isDead()
      || now<Long.parseLong(s.values.getProperty("distressAt","0")) || !human.getController().isInCombat()
      || s.route.distance(human)>PlayerBotWardenConfig.BOUNTY_CORRIDOR || !PositionUtil.isInRange(bot,human,PlayerBotWardenConfig.DISTRESS_RANGE))continue;
     aid.human=human.getObjectId();s.values.setProperty("distressAt",""+(now+PlayerBotWardenConfig.DISTRESS_COOLDOWN*1000L));s.save();
    }}
   }catch(SQLException error){log.warn("Warden distress admission awaits storage for player {}: {}",human.getObjectId(),error.toString());}
   finally{DISTRESS_PENDING.remove(human.getObjectId());}
  });
 }
 private static void ward(Player bot,Player human) {
  int skill=PlayerBotWardenConfig.WARD_SKILL;if(skill<=0 || PlayerBotWardenConfig.WARD_SECONDS<=0 || human.isDead() || human.isInInstance() || !PositionUtil.isInRange(bot,human,PlayerBotWardenConfig.WARD_RANGE))return;
  var template=com.aionemu.gameserver.dataholders.DataManager.SKILL_DATA.getSkillTemplate(skill);if(template==null)return;
  for(var effect:human.getEffectController().getAbnormalEffects())if(effect.getStack().equals(template.getStack())) {
   // Another native blessing on this stack wins. Only our own short ward is refreshed.
   if(effect.getForceType()!=WARD || effect.getRemainingTimeMillis()>PlayerBotWardenConfig.WARD_SECONDS*500L)return;
   effect.endEffect();break;
  }
  SkillEngine.getInstance().applyEffectDirectly(skill,bot,human,PlayerBotWardenConfig.WARD_SECONDS*1000,WARD);
 }
 public static void clearWard(Player human) {
  for(var effect:human.getEffectController().getAbnormalEffects())if(effect.getForceType()==WARD)effect.endEffect();
 }
 private static void report(PlayerBotRoadDuty.State s,Player human,long now) {
  if(REPORTS.getOrDefault(human.getObjectId(),0L)>now)return;REPORTS.put(human.getObjectId(),now+PlayerBotWardenConfig.REPORT_SECONDS*1000L);
  var points=s.route.points();var next=points.get(s.point);double remaining=PositionUtil.getDistance(s.session.bot(),next.x(),next.y(),next.z());for(int i=s.point;i+s.direction>=0 && i+s.direction<points.size();i+=s.direction)remaining+=PlayerBotWardenBeats.distance(points.get(i),points.get(i+s.direction));
  var nearest=PlayerBotWardenBeats.threats(s.route.id()).stream().min(Comparator.comparingDouble(t->PositionUtil.getDistance(human,t.point().x(),t.point().y(),t.point().z()))).orElse(null);
  String enemy=nearest==null?"No native elite reported on this beat.":"Reported elite: "+nearest.name()+" "+Math.round(PositionUtil.getDistance(human,nearest.point().x(),nearest.point().y(),nearest.point().z()))+" m "+direction(nearest.point().x()-human.getX(),nearest.point().y()-human.getY())+".";
  PacketSendUtility.sendMessage(human,s.route.name()+". Next stop: "+Math.round(remaining)+" m. "+enemy);
 }
 static String direction(float x,float y){int octant=Math.floorMod((int)Math.round(Math.atan2(y,x)/(Math.PI/4)),8);return List.of("east","northeast","north","northwest","west","southwest","south","southeast").get(octant);}
 static boolean travel(PlayerBotRoadDuty.State s,PlayerBotNavigation navigation,long now) {
  var bot=s.session.bot();var aid=AID.computeIfAbsent(bot.getObjectId(),k->new Aid());
  if(s.visitor!=0) {
   var human=World.getInstance().getPlayer(s.visitor);
   boolean valid=human!=null && human.isOnline() && !human.isDead() && human.getWorldId()==bot.getWorldId() && human.getInstanceId()==bot.getInstanceId()
    && PlayerBotWardenBeats.field(human.getWorldId(),human.getX(),human.getY(),human.getZ());
   double range=valid?s.route.distance(human):Double.POSITIVE_INFINITY;
   if(!valid || now>=s.escortUntil || range>PlayerBotWardenConfig.LEASH || escortEnd(s,human))end(s,"The road escort has ended.");
   else {
    if(range>=PlayerBotWardenConfig.LEASH_WARNING && !aid.warning){aid.warning=true;s.values.setProperty("escortWarned","true");s.save();PacketSendUtility.sendMessage(human,"You are near the edge of the Warden's beat. Return to the road to keep your escort.");}
    if(!PositionUtil.isInRange(bot,human,6))navigation.approach(human,4);else navigation.stop();s.progress.reset();return true;
   }
  }
  if(aid.corpse!=0) {
   var human=World.getInstance().getPlayer(aid.corpse);
   if(human==null || !human.isDead() || human.getWorldId()!=bot.getWorldId() || human.getInstanceId()!=bot.getInstanceId() || !PositionUtil.isInRange(bot,human,PlayerBotWardenConfig.RESCUE_RANGE)){aid.corpse=0;aid.arrived=0;return false;}
   if(!PositionUtil.isInRange(bot,human,4)){navigation.approach(human,3);return true;}navigation.stop();if(aid.arrived==0)aid.arrived=now;
   boolean[] hostile={false},monsters={false};human.getKnownList().forEachPlayer(p->{if(!p.isDead() && p.getRace()!=human.getRace() && PositionUtil.isInRange(p,human,PlayerBotWardenConfig.RESCUE_HOSTILE_RANGE))hostile[0]=true;});
   human.getKnownList().forEachNpc(n->{if(!n.isDead() && human.isEnemy(n) && PositionUtil.isInRange(n,human,PlayerBotWardenConfig.RESCUE_HOSTILE_RANGE))monsters[0]=true;});
   if(rescue(true,true,hostile[0],monsters[0],PositionUtil.getDistance(bot,human),now,aid.arrived)) {
    aid.corpse=0;aid.arrived=0;ThreadPoolManager.getInstance().execute(()->offerRescue(bot,human));
   }
   return true;
  }
  if(aid.human!=0) {
   var human=World.getInstance().getPlayer(aid.human);
   if(human!=null && !human.isDead() && human.getController().isInCombat() && human.getWorldId()==bot.getWorldId() && human.getInstanceId()==bot.getInstanceId()) {
    if(!PositionUtil.isInRange(bot,human,8))navigation.approach(human,6);else navigation.stop();s.progress.reset();return true;
   }
  }
  return false;
 }
 static boolean reachedEnd(PlayerBotPatrolRoutes.Route route,PlayerBotNavigation.Point start,PlayerBotNavigation.Point human) {
  return PlayerBotWardenBeats.distance(start,human)>=50 && (PlayerBotWardenBeats.distance(human,route.points().getFirst())<6
   || PlayerBotWardenBeats.distance(human,route.points().getLast())<6);
 }
 private static void origin(PlayerBotRoadDuty.State s,Player human) {
  s.values.setProperty("escortStartX",""+human.getX());s.values.setProperty("escortStartY",""+human.getY());s.values.setProperty("escortStartZ",""+human.getZ());
 }
 private static boolean escortEnd(PlayerBotRoadDuty.State s,Player human) {
  // Old escorts have no origin. Their first observed traveller position is the safe baseline.
  if(!s.values.containsKey("escortStartX")){origin(s,human);s.save();return false;}
  var start=new PlayerBotNavigation.Point(Float.parseFloat(s.values.getProperty("escortStartX")),Float.parseFloat(s.values.getProperty("escortStartY")),Float.parseFloat(s.values.getProperty("escortStartZ")));
  return reachedEnd(s.route,start,new PlayerBotNavigation.Point(human.getX(),human.getY(),human.getZ()));
 }
 private static void offerRescue(Player bot,Player human) {
  try {
   if(!human(human) || !human.isDead() || human.getResStatus() || !clearCorpse(human) || !PositionUtil.isInRange(bot,human,4))return;
   boolean reserved=transact(c->PlayerBotWardenDAO.rescue(c,human.getObjectId(),System.currentTimeMillis(),PlayerBotWardenConfig.RESCUE_COOLDOWN*60000L));
   try(var c=DatabaseFactory.getConnection()){RESCUE_WAIT.put(human.getObjectId(),PlayerBotWardenDAO.rescueDue(c,human.getObjectId()));}
   if(!reserved)return;
   human.setResurrectionSkill(0);human.setPlayerResActivate(true);RESCUES.put(human.getObjectId(),new Rescue(bot.getObjectId(),System.currentTimeMillis()+60000));PacketSendUtility.sendPacket(human,new SM_RESURRECT(bot));
   PacketSendUtility.sendMessage(human,"Warden's Rescue: accept resurrection here without soul sickness.");
  }catch(SQLException error){log.warn("Warden rescue awaits storage for player {}: {}",human.getObjectId(),error.toString());}
 }
 private record Rescue(int bot,long until){}
 private static final Map<Integer,Rescue> RESCUES=new ConcurrentHashMap<>();
 /** Consume only our own native prompt. Refused/expired rescues leave the traveller dead. */
 public static boolean completeRescue(Player human) {
  var rescue=RESCUES.remove(human.getObjectId());if(rescue==null)return false;
  var bot=World.getInstance().getPlayer(rescue.bot());
  if(rescue.until()>System.currentTimeMillis() && bot!=null && PlayerBotRoadDuty.publicWarden(bot) && !bot.isDead()
   && human.isDead() && PositionUtil.isInRange(bot,human,4) && clearCorpse(human)) {
   com.aionemu.gameserver.services.player.PlayerReviveService.revive(human,35,35,false,0);
   human.getGameStats().updateStatsAndSpeedVisually();human.unsetResPosState();human.setIsFlyingBeforeDeath(false);DEATHS.remove(human.getObjectId());
  }else human.setPlayerResActivate(false);
  return true;
 }
 private static boolean clearCorpse(Player human) {
  boolean[] blocked={false};human.getKnownList().forEachPlayer(p->{if(!p.isDead() && p.getRace()!=human.getRace() && PositionUtil.isInRange(p,human,PlayerBotWardenConfig.RESCUE_HOSTILE_RANGE))blocked[0]=true;});
  human.getKnownList().forEachNpc(n->{if(!n.isDead() && human.isEnemy(n) && PositionUtil.isInRange(n,human,PlayerBotWardenConfig.RESCUE_HOSTILE_RANGE))blocked[0]=true;});return !blocked[0];
 }
 public static void entered(Player human) {
  if(!human(human))return;var request=ASKING.get(human.getObjectId());if(request!=null)expireQuestion(human.getObjectId(),human.getResponseRequester(),request);clearWard(human);
  if(!PlayerBotWardenConfig.ENABLED || !com.aionemu.gameserver.configs.main.PlayerBotConfig.ROAD_DUTY || !human.isDead())return;
  ThreadPoolManager.getInstance().execute(()->{if(!human(human))return;try(var c=DatabaseFactory.getConnection()){DEATHS.put(human.getObjectId(),PlayerBotWardenDAO.death(c,human.getObjectId()));}catch(SQLException e){log.warn("Warden rescue death evidence unavailable for player {}: {}",human.getObjectId(),e.toString());}});
 }
 static List<Player> party(Player bot) {
  var human=visitor(bot);var aid=AID.get(bot.getObjectId());if(human==null && aid!=null && aid.human!=0)human=World.getInstance().getPlayer(aid.human);
  if(human==null)return List.of(bot);var members=new LinkedHashSet<Player>();members.add(bot);members.add(human);members.addAll(PlayerBotTeams.members(human));return List.copyOf(members);
 }
 /** Public aid recipients, consumed by the native party-skill owners without creating a party or reward share. */
 public static List<Player> supportMembers(Player bot) {
  if(!PlayerBotWardenConfig.ENABLED || !PlayerBotRoadDuty.publicWarden(bot))return List.of();
  return party(bot).stream().filter(p->p!=bot && p.isSpawned() && !p.isDead() && p.getRace()==bot.getRace()
   && p.getWorldId()==bot.getWorldId() && p.getInstanceId()==bot.getInstanceId() && PositionUtil.isInRange(bot,p,PlayerBotWardenConfig.DISTRESS_RANGE)
   && PlayerBotRoadDuty.recipient(bot,p)).toList();
 }
 public static boolean supportMember(Player bot,Creature target){return target instanceof Player p && supportMembers(bot).contains(p);}
 static void invited(Player human,Player bot) {
  var s=PlayerBotRoadDuty.state(bot);if(s==null)return;
  synchronized(s.session) {
   if(s.visitor==human.getObjectId()){end(s,"Warden released. You can request another escort after the cooldown.");return;}
   if(!PlayerBotRoadDuty.publicWarden(bot) || !s.session.autonomous() || !PlayerBotService.getInstance().current(s.session)
     || bot.isDead() || !human(human) || human.isDead() || human.getRace()!=bot.getRace()) {PacketSendUtility.sendMessage(human,bot.getName()+" cannot help right now.");return;}
   if(human.getWorldId()!=bot.getWorldId() || human.getInstanceId()!=bot.getInstanceId() || !PositionUtil.isInRange(bot,human,PlayerBotWardenConfig.INVITE_RANGE)) {PacketSendUtility.sendMessage(human,"Move closer to "+bot.getName()+" and invite them again.");return;}
   if(!PlayerBotWardenBeats.field(human.getWorldId(),human.getX(),human.getY(),human.getZ())) {PacketSendUtility.sendMessage(human,"Wardens only serve on the road. Leave the settlement and invite again.");return;}
   if(bot.getController().isInCombat()){PacketSendUtility.sendMessage(human,bot.getName()+" is fighting; ask again when the road is clear.");return;}
   if(s.visitor!=0 || ESCORTS.containsKey(human.getObjectId())){PacketSendUtility.sendMessage(human,"A Warden can escort one traveller, and you can have one escort at a time.");return;}
   if(ASKING.containsKey(human.getObjectId())){PacketSendUtility.sendMessage(human,"Answer your pending Road Bounty question, or invite again after it expires.");return;}
   if(!PlayerBotRoadBounty.available(human)){start(human,bot,false);return;}
   var response=new RequestResponseHandler<Creature>(bot) {
    @Override public void acceptRequest(Creature requester,Player responder){if(ASKING.remove(responder.getObjectId(),this))start(responder,bot,true);}
    @Override public void denyRequest(Creature requester,Player responder){if(ASKING.remove(responder.getObjectId(),this))start(responder,bot,false);}
   };
   if(!ask(human.getObjectId(),human.getResponseRequester(),response)){PacketSendUtility.sendMessage(human,"Answer your pending Road Bounty question, or invite again after it expires.");return;}
   ThreadPoolManager.getInstance().schedule(()->{if(expireQuestion(human.getObjectId(),human.getResponseRequester(),response) && human.isOnline())PacketSendUtility.sendMessage(human,"The Road Bounty question expired. Invite a Warden again when ready.");},Math.max(1,PlayerBotWardenConfig.QUESTION_SECONDS)*1000L);
   PacketSendUtility.sendPacket(human,new SM_QUESTION_WINDOW(QUESTION,0,0,"Take today's Road Bounty?"));
  }
 }
 static boolean ask(int human,ResponseRequester requester,RequestResponseHandler<Creature> response) {
  if(ASKING.putIfAbsent(human,response)!=null)return false;
  if(requester.putRequest(QUESTION,response))return true;ASKING.remove(human,response);return false;
 }
 static boolean expireQuestion(int human,ResponseRequester requester,RequestResponseHandler<Creature> response) {
  if(!ASKING.remove(human,response))return false;requester.remove(QUESTION,response);return true;
 }
 private static void start(Player human,Player bot,boolean bounty) {
  ThreadPoolManager.getInstance().execute(()->{
   var s=PlayerBotRoadDuty.state(bot);try {
    if(s==null)return;synchronized(s.session) {
     long now=System.currentTimeMillis();if(!PlayerBotRoadDuty.publicWarden(bot) || bot.isDead() || human.isDead() || !human(human) || s.visitor!=0
      || human.getRace()!=bot.getRace() || human.getWorldId()!=bot.getWorldId() || human.getInstanceId()!=bot.getInstanceId() || !PositionUtil.isInRange(bot,human,PlayerBotWardenConfig.INVITE_RANGE)
      || bot.getController().isInCombat() || !PlayerBotWardenBeats.field(human.getWorldId(),human.getX(),human.getY(),human.getZ())) {PacketSendUtility.sendMessage(human,bot.getName()+" cannot help right now. Return to the road nearby and invite again when the road is clear.");return;}
     long due=transact(c->PlayerBotWardenDAO.escort(c,human.getObjectId(),bot.getObjectId(),now,PlayerBotWardenConfig.ESCORT_MINUTES*60000L,PlayerBotWardenConfig.ESCORT_COOLDOWN*60000L));
     if(due>now){PacketSendUtility.sendMessage(human,"An escort is active or cooling down. Try again in "+((due-now+999)/1000)+" seconds.");return;}
     s.visitor=human.getObjectId();s.stopUntil=s.escortUntil=now+PlayerBotWardenConfig.ESCORT_MINUTES*60000L;ESCORTS.put(human.getObjectId(),bot.getObjectId());
     var aid=AID.computeIfAbsent(bot.getObjectId(),k->new Aid());aid.warning=false;aid.fairWarning=false;origin(s,human);s.values.setProperty("fairWarned","false");s.values.setProperty("escortWarned","false");s.save();
     if(bounty)PlayerBotRoadBounty.accept(human);PacketSendUtility.sendMessage(human,bot.getName()+" will escort you toward either end of this road, for up to "+PlayerBotWardenConfig.ESCORT_MINUTES+" minutes. Invite again to release.");
    }
   }catch(SQLException|RuntimeException error){log.warn("Warden escort refused for player {}: {}",human.getObjectId(),error.toString());PacketSendUtility.sendMessage(human,"The escort could not be saved. Invite again shortly.");}
  });
 }
 private static void end(PlayerBotRoadDuty.State s,String message) {
  int human=s.visitor,bot=s.session.bot().getObjectId();if(human==0)return;ESCORTS.remove(human,bot);s.visitor=0;s.stopUntil=s.escortUntil=0;for(String key:List.of("escortStartX","escortStartY","escortStartZ"))s.values.remove(key);s.save();
  var h=World.getInstance().getPlayer(human);if(h!=null)PacketSendUtility.sendMessage(h,message);
  ThreadPoolManager.getInstance().execute(()->{try{transact(c->{PlayerBotWardenDAO.ended(c,human,bot,System.currentTimeMillis(),PlayerBotWardenConfig.ESCORT_COOLDOWN*60000L);return null;});}catch(SQLException e){log.error("Warden cooldown retains its SQL reservation",e);}});
 }
 public static void died(Player human,Creature killer) {
  if(!PlayerBotWardenConfig.ENABLED || !com.aionemu.gameserver.configs.main.PlayerBotConfig.ROAD_DUTY || !human(human))return;
  int id=human.getObjectId();long at=killer!=null && killer.getMaster() instanceof Npc?System.currentTimeMillis():0;
  DEATHS.remove(id); // Invalidate an earlier monster death, including when this write fails.
  ThreadPoolManager.getInstance().execute(()->{try{transact(c->{PlayerBotWardenDAO.death(c,id,at);return null;});DEATHS.put(id,at);}catch(SQLException e){log.warn("Warden death record unavailable for player {}: {}",id,e.toString());}});
 }
 public static boolean fair(long team,long warden,int percent){return warden<=0 || team>0 && (double)team*100/((double)team+warden)>=percent;}
 public static void unfair(Player human) {
  Integer id=ESCORTS.get(human.getObjectId());
  var team=human.getCurrentTeam();
  var members=team instanceof com.aionemu.gameserver.model.team.alliance.PlayerAlliance alliance && alliance.isInLeague()
   ?alliance.getLeague().getMembers().stream().flatMap(a->a.getMembers().stream()).toList():PlayerBotTeams.members(human);
  if(id==null)for(var member:members)if(ESCORTS.containsKey(member.getObjectId())){human=member;id=ESCORTS.get(member.getObjectId());break;}
  var aid=id==null?null:AID.get(id);
  if(aid!=null)synchronized(aid){if(!aid.fairWarning){aid.fairWarning=true;var bot=World.getInstance().getPlayer(id);var s=PlayerBotRoadDuty.state(bot);if(s!=null){s.values.setProperty("fairWarned","true");s.save();}PacketSendUtility.sendMessage(human,"Your team must deal at least "+PlayerBotWardenConfig.FAIR_DAMAGE+"% of the combined damage to earn XP or Road Bounty credit with a Warden.");}}
 }
 static void close(Player bot){var s=PlayerBotRoadDuty.state(bot);if(PlayerBotWardenConfig.ENABLED && s!=null && s.career==PlayerBotRoadDuty.Career.WARDEN && s.visitor!=0)end(s,"Your Warden has returned to duty.");AID.remove(bot.getObjectId());}
 private PlayerBotWardens(){}
}
