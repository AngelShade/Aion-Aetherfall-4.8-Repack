package com.aionemu.gameserver.services.playerbot;

import java.lang.reflect.*;
import java.util.*;
import java.util.function.Consumer;
import sun.misc.Unsafe;
import com.aionemu.gameserver.controllers.attack.*;
import com.aionemu.gameserver.controllers.movement.PlayerMoveController;
import com.aionemu.gameserver.model.gameobjects.*;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.world.knownlist.KnownList;

/** Actual travel entry and native timer on inert actors. No World, route execution,
 * timers, transport, IDs, SQL or casts; a future scan prevents catalog admission. */
public final class PlayerBotPostKillTravelCheck {
 private static int checks;private static Unsafe unsafe;private static Object state;
 private static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);checks++;}
 static final class Seen extends KnownList {
  List<VisibleObject> objects=new ArrayList<>();boolean hidden;
  Seen(){super(null);}
  @Override public boolean sees(VisibleObject o){return !hidden&&objects.contains(o);}
  @Override public void forEachObject(Consumer<VisibleObject> c){objects.forEach(c);}
 }
 static final class Actor extends PlayerBotFieldCombatCheck.Actor {
  Summon spirit;boolean casting;Move move;Set<Creature> hostile;
  @Override public boolean isCasting(){return casting;}
  @Override public Summon getSummon(){return spirit;}
  @Override public boolean isEnemy(Creature c){return c instanceof Enemy enemy && !enemy.friendly || hostile!=null&&hostile.contains(c);}
  @Override public PlayerMoveController getMoveController(){return move;}
 }
 static final class Move extends PlayerMoveController {
  boolean moving;int stopped;
  Move(Player actor){super(actor);}
  @Override public boolean isInMove(){return moving;}
  @Override public void abortMove(){moving=false;stopped++;}
 }
 static final class Enemy extends PlayerBotFieldCombatCheck.Enemy {
  boolean friendly;
 }
 static final class Spirit extends Summon {
  boolean dead,spawned;int id;
  Spirit(){super(0,null,null,null,null,0);}
  @Override public int getObjectId(){return id;}
  @Override public boolean isDead(){return dead;}
  @Override public boolean isSpawned(){return spawned;}
  @Override public int getWorldId(){return 1;}
  @Override public int getInstanceId(){return 1;}
 }
 private static void put(Object object,String field,Object value)throws Exception{PlayerBotQuestObjectivesCheck.field(object,field,value);}
 private static void hate(AggroList list,Creature enemy,int value)throws Exception{
  var ctor=AggroInfo.class.getDeclaredConstructor(Creature.class);ctor.setAccessible(true);
  var info=ctor.newInstance(enemy);info.setHate(value);
  var f=AggroList.class.getDeclaredField("aggroList");f.setAccessible(true);
  @SuppressWarnings("unchecked") var entries=(Map<Integer,AggroInfo>)f.get(list);
  entries.put(enemy.getObjectId(),info);
 }
 private static boolean admitted(PlayerBotSession session)throws Exception{
  put(state,"noActivitySince",0L);
  check(!PlayerBotWorldActivities.travel(session,null),"no selected destination never executes native movement or transport");
  var f=state.getClass().getDeclaredField("noActivitySince");f.setAccessible(true);return f.getLong(state)>0;
 }
 public static void main(String[] args)throws Exception{
  var f=Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);unsafe=(Unsafe)f.get(null);
  var bot=(Actor)unsafe.allocateInstance(Actor.class);bot.id=1999999201;bot.spawned=true;bot.attack=true;
  bot.control=new PlayerBotFieldCombatCheck.Clock();bot.control.last=System.currentTimeMillis();
  bot.move=new Move(bot);
  var seen=new Seen();bot.seen=seen;put(bot,"aggroList",new AggroList(bot));
  var session=(PlayerBotSession)unsafe.allocateInstance(PlayerBotSession.class);put(session,"bot",bot);put(session,"owner",bot);
  var statesField=PlayerBotWorldActivities.class.getDeclaredField("STATES");statesField.setAccessible(true);
  @SuppressWarnings("unchecked") var states=(Map<Integer,Object>)statesField.get(null);
  var stateType=Class.forName(PlayerBotWorldActivities.class.getName()+"$State");var ctor=stateType.getDeclaredConstructor();ctor.setAccessible(true);
  state=ctor.newInstance();put(state,"nextScan",Long.MAX_VALUE);Object prior=states.put(bot.id,state);
  try{
   check(bot.control.isInCombat(),"actual native ten-second timer remains active immediately after kill");
   if(args.length>0&&args[0].equals("old")){
    check(!admitted(session),"REPRO: old travel rejects locomotion solely for residual combat timer");
    check(bot.control.isInCombat(),"reproduction does not clear core combat state");
    var goal=new PlayerBotWorldActivities.Goal(0,PlayerBotQuestRoutes.Kind.HUNT,new PlayerBotWorldTravel.Place(1,213360,0,0,0),0);
    put(state,"goal",goal);bot.move.moving=true;long before=System.currentTimeMillis();
    PlayerBotWorldActivities.prepare(bot,new PlayerBotNavigation(bot));
    var scan=stateType.getDeclaredField("nextScan");scan.setAccessible(true);
    check(scan.getLong(state)>=before+5000,"REPRO: old arrival adds five-second delay after unavailable destination");
    System.out.println("OK: "+checks+" installed050 post-kill travel failure reproduced");return;
   }
   check(admitted(session),"new actual travel entry reaches planner despite residual timer and no live threats");
   check(bot.control.isInCombat(),"locomotion planning preserves native dialog and teleporter combat timer");
   var enemy=(Enemy)unsafe.allocateInstance(Enemy.class);enemy.id=1999999202;enemy.world=enemy.instance=1;enemy.spawned=true;
   put(enemy,"aggroList",new AggroList(enemy));seen.objects.add(enemy);
   hate(bot.getAggroList(),enemy,10);check(!admitted(session),"actual incoming living hostile hate blocks travel");
   enemy.dead=true;check(admitted(session),"dead attacker hate cannot trap post-kill movement");enemy.dead=false;
   enemy.spawned=false;check(admitted(session),"despawned attacker hate is stale");enemy.spawned=true;
   enemy.world=2;check(admitted(session),"cross-map hate is stale");enemy.world=1;
   enemy.instance=2;check(admitted(session),"cross-instance hate is stale");enemy.instance=1;
   seen.hidden=true;check(admitted(session),"unknown attacker hate cannot retain a route threat");seen.hidden=false;
   enemy.friendly=true;check(admitted(session),"friendly creature cannot become a travel combat veto");enemy.friendly=false;
   hate(bot.getAggroList(),enemy,0);check(admitted(session),"zero hate does not block ordinary journey");
   hate(enemy.getAggroList(),bot,10);check(!admitted(session),"known enemy hating bot blocks travel even without reciprocal hate");
   enemy.dead=true;check(admitted(session),"dead known enemy cannot keep offensive travel veto");enemy.dead=false;
   hate(enemy.getAggroList(),bot,0);
   var spirit=(Spirit)unsafe.allocateInstance(Spirit.class);spirit.id=1999999203;spirit.spawned=true;put(spirit,"aggroList",new AggroList(spirit));bot.spirit=spirit;
   hate(spirit.getAggroList(),enemy,10);check(!admitted(session),"living owned spirit's incoming threat blocks travel");
   spirit.dead=true;check(admitted(session),"dead spirit's stale hate cannot veto travel");spirit.dead=false;
   spirit.spawned=false;check(admitted(session),"despawned spirit's stale hate cannot veto travel");spirit.spawned=true;
   hate(spirit.getAggroList(),enemy,0);hate(enemy.getAggroList(),spirit,10);check(!admitted(session),"known hostile hating owned spirit blocks travel");
   hate(enemy.getAggroList(),spirit,0);bot.spirit=null;
   var hostilePlayer=(Actor)unsafe.allocateInstance(Actor.class);hostilePlayer.id=1999999204;hostilePlayer.spawned=true;put(hostilePlayer,"aggroList",new AggroList(hostilePlayer));
   bot.hostile=new HashSet<>();bot.hostile.add(hostilePlayer);seen.objects.add(hostilePlayer);
   hate(bot.getAggroList(),hostilePlayer,10);check(!admitted(session),"actual hostile player incoming hate blocks travel under native PvP hostility");
   hate(bot.getAggroList(),hostilePlayer,0);hate(hostilePlayer.getAggroList(),bot,10);check(!admitted(session),"known hostile player targeting bot blocks travel without reciprocal hate");
   hate(hostilePlayer.getAggroList(),bot,0);check(admitted(session),"unengaged hostile player does not create invented combat");
   bot.hostile.add(spirit);seen.objects.add(spirit);hate(spirit.getAggroList(),bot,10);
   check(!admitted(session),"enemy summon targeting bot blocks travel through Creature threat path");
   spirit.dead=true;check(admitted(session),"dead enemy summon cannot retain travel veto");spirit.dead=false;
   hate(spirit.getAggroList(),bot,0);bot.hostile.clear();
   for(String guard:List.of("dead","trading","looting","casting")){
    put(bot,guard,true);check(!admitted(session),"native "+guard+" admission remains guarded");put(bot,guard,false);
   }
   var owner=(Actor)unsafe.allocateInstance(Actor.class);put(session,"owner",owner);
   check(!admitted(session),"private companions cannot acquire world journey behavior");put(session,"owner",bot);
   bot.control.last=0;check(admitted(session),"ordinary safe journey remains admitted without a timer");
   var goal=new PlayerBotWorldActivities.Goal(0,PlayerBotQuestRoutes.Kind.HUNT,new PlayerBotWorldTravel.Place(1,213360,0,0,0),0);
   put(state,"goal",goal);bot.move.moving=true;long arrival=System.currentTimeMillis();
   var nav=new PlayerBotNavigation(bot);PlayerBotWorldActivities.prepare(bot,nav);
   var scan=stateType.getDeclaredField("nextScan");scan.setAccessible(true);
   var selected=stateType.getDeclaredField("goal");selected.setAccessible(true);
   check(selected.get(state)==null,"arrival returns control to local native work without immediately picking another route");
   check(scan.getLong(state)==0,"next planner callback may replace unavailable destination without an extra five-second wait");
   check(bot.move.stopped==1&&!bot.move.moving,"arrival stops native locomotion before local interaction");
   var exclusions=stateType.getDeclaredField("excluded");exclusions.setAccessible(true);
   @SuppressWarnings("unchecked") var excluded=(Map<String,Long>)exclusions.get(state);
   check(excluded.get(PlayerBotWorldActivities.key(goal))>=arrival+30000,"original destination exclusion remains bounded and prevents immediate dead-hint replay");
   // Existing native arbitration still gives local work precedence over world travel.
   for(String local:List.of("loot","native quest interaction","recovery")){
    var plan=new PlayerBotStrategyComposition.Plan(PlayerBotEngine.State.NON_COMBAT);var engine=new PlayerBotEngine();
    plan.defaults("local",PlayerBotFieldCombatCheck.action(local,true,true),PlayerBotEngine.DEFAULT+2,PlayerBotEngine.State.NON_COMBAT);
    plan.defaults("world",PlayerBotFieldCombatCheck.action("world travel",true,true),PlayerBotEngine.DEFAULT-2,PlayerBotEngine.State.NON_COMBAT);
    check(plan.tick(engine,64)&&engine.getLastAction().equals(local),"local "+local+" remains above replaceable world journey");
   }
  }finally{if(prior==null)states.remove(bot.id);else states.put(bot.id,prior);}
  System.out.println("OK: "+checks+" native post-kill locomotion and current threat admission checks");
 }
}
