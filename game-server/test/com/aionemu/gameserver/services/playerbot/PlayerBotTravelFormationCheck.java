package com.aionemu.gameserver.services.playerbot;

import java.lang.reflect.*;
import java.util.*;
import sun.misc.Unsafe;
import com.aionemu.commons.configuration.ConfigurableProcessor;
import com.aionemu.gameserver.configs.main.PlayerBotConfig;
import com.aionemu.gameserver.controllers.PlayerController;
import com.aionemu.gameserver.controllers.attack.AggroList;
import com.aionemu.gameserver.controllers.movement.PlayerBotMoveController;
import com.aionemu.gameserver.model.TaskId;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.team.group.PlayerGroup;
import com.aionemu.gameserver.world.WorldMapInstance;
import static com.aionemu.gameserver.services.playerbot.PlayerBotRules.*;
import static com.aionemu.gameserver.services.playerbot.PlayerBotEngine.*;

/** Real policy/engine/mover intent with world-free actors; no DB, world or ID allocation. */
public final class PlayerBotTravelFormationCheck {
 static Unsafe unsafe;static int checks;
 static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 static void field(Object target,String name,Object value)throws Exception{for(Class<?> type=target.getClass();type!=null;type=type.getSuperclass())try{var f=type.getDeclaredField(name);f.setAccessible(true);f.set(target,value);return;}catch(NoSuchFieldException e){}throw new NoSuchFieldException(name);}
 static class Tasks extends PlayerController {
  boolean combat,item,interaction;
  @Override public boolean isInCombat(){return combat;}
  @Override public boolean hasScheduledTask(TaskId id){return id==TaskId.ITEM_USE && item || id==TaskId.ACTION_ITEM_NPC && interaction;}
 }
 static class Group extends PlayerGroup {
  List<Player> players;Group(){super(null,null,1);}
  @Override public List<Player> getMembers(){return players;}
 }
 static class Actor extends Player {
  int id,owner;boolean online,spawned,dead,casting,trading,looting;Tasks tasks;Group group;WorldMapInstance map;PlayerBotMoveController mover;AggroList aggro;
  Actor(){super(null,null);}
  @Override public int getObjectId(){return id;}@Override public boolean isPlayerBot(){return owner!=0;}@Override public int getPlayerBotOwnerId(){return owner;}
  @Override public boolean isOnline(){return online;}@Override public boolean isSpawned(){return spawned;}@Override public boolean isDead(){return dead;}
  @Override public boolean isCasting(){return casting;}@Override public boolean isTrading(){return trading;}@Override public boolean isLooting(){return looting;}
  @Override public float getX(){return 0;}@Override public float getY(){return 0;}@Override public float getZ(){return 0;}@Override public byte getHeading(){return 0;}
  @Override public PlayerController getController(){return tasks;}@Override public PlayerGroup getPlayerGroup(){return group;}
  @Override public WorldMapInstance getWorldMapInstance(){return map;}@Override public PlayerBotMoveController getMoveController(){return mover;}
 }
 static Actor actor(int id)throws Exception{
  var a=(Actor)unsafe.allocateInstance(Actor.class);a.id=id;a.online=true;a.spawned=true;a.tasks=new Tasks();a.mover=new PlayerBotMoveController(a);field(a,"aggroList",new AggroList(a));return a;
 }
 static PlayerBotSession session(Actor owner,Actor bot)throws Exception{var s=(PlayerBotSession)unsafe.allocateInstance(PlayerBotSession.class);field(s,"owner",owner);field(s,"bot",bot);return s;}
 static void rejected(Runnable action,String why){try{action.run();throw new AssertionError(why);}catch(IllegalArgumentException expected){checks++;}}
 static void summons()throws Exception{
  var properties=new Properties();properties.setProperty("gameserver.playerbots.enable","true");properties.setProperty("gameserver.playerbots.summon.enable","true");
  check(ConfigurableProcessor.process(properties,PlayerBotConfig.class).isEmpty(),"Native configuration consumes both enable keys");
  PlayerBotSummonPolicy.request();checks++;
  PlayerBotConfig.ENABLED=false;rejected(PlayerBotSummonPolicy::request,"Disabled module accepted summon");PlayerBotConfig.ENABLED=true;
  PlayerBotConfig.SUMMON_ENABLED=false;rejected(PlayerBotSummonPolicy::request,"Disabled summon accepted request");PlayerBotConfig.SUMMON_ENABLED=true;
  var owner=actor(1999980000);var group=(Group)unsafe.allocateInstance(Group.class);owner.group=group;owner.map=(WorldMapInstance)unsafe.allocateInstance(com.aionemu.gameserver.world.WorldMap2DInstance.class);
  var party=new ArrayList<PlayerBotSession>();var members=new ArrayList<Player>();members.add(owner);
  for(int i=1;i<=5;i++){var bot=actor(owner.id+i);bot.owner=owner.id;bot.group=group;party.add(session(owner,bot));members.add(bot);}group.players=members;
  PlayerBotSummonPolicy.preflight(party);checks++;
  for(PlayerBotSession s:party)for(Actor a:new Actor[]{owner,(Actor)s.bot()}){
   a.casting=true;check(PlayerBotRecovery.ready(s),"Recall cancels companion casting; owner casting does not move the owner");a.casting=false;
   a.trading=true;check(PlayerBotRecovery.ready(s),"Recall cancels native companion exchange");a.trading=false;
   a.looting=true;check(PlayerBotRecovery.ready(s),"Recall closes native companion loot");a.looting=false;
   a.tasks.item=true;check(PlayerBotRecovery.ready(s),"Recall cancels native companion item channel");a.tasks.item=false;
   a.tasks.interaction=true;check(PlayerBotRecovery.ready(s),"Recall cancels native companion interaction");a.tasks.interaction=false;
   a.tasks.combat=true;for(var peer:party)check(PlayerBotRecovery.ready(peer)==(a!=owner),"Only owner combat blocks manual recall");a.tasks.combat=false;
  }
  var last=(Actor)party.getLast().bot();last.casting=true;PlayerBotSummonPolicy.preflight(party);checks++;last.casting=false;
  last.owner++;check(!PlayerBotRecovery.ready(party.getLast()),"Foreign ownership blocked");last.owner--;
  last.group=null;check(!PlayerBotRecovery.ready(party.getLast()),"Different party blocked");last.group=group;
  owner.dead=true;check(!PlayerBotRecovery.ready(party.getFirst()),"Dead owner blocked");owner.dead=false;
  owner.online=false;check(!PlayerBotRecovery.ready(party.getFirst()),"Offline owner blocked");owner.online=true;
  owner.spawned=false;check(!PlayerBotRecovery.ready(party.getFirst()),"Unspawned owner blocked");owner.spawned=true;
  owner.map=null;check(!PlayerBotRecovery.ready(party.getFirst()),"Missing native instance blocked");owner.map=(WorldMapInstance)unsafe.allocateInstance(com.aionemu.gameserver.world.WorldMap2DInstance.class);
  field(party.getFirst(),"closing",true);check(!PlayerBotRecovery.ready(party.getFirst()),"Closing session blocked");field(party.getFirst(),"closing",false);
  last.dead=true;check(PlayerBotRecovery.ready(party.getLast()),"Dead companion may use native recovery");last.dead=false;
  rejected(()->PlayerBotSummonPolicy.preflight(List.of()),"Empty party blocked");
 }
 static Action action(String name){return new Action(){public String name(){return name;}public boolean isUseful(){return true;}public boolean isPossible(){return true;}public boolean execute(){return true;}};}
 static void priorities(){
  for(Order order:Order.values())check(PlayerBotFollowIntent.traveling(order,false,true,true,false)==(order==Order.FOLLOW || order==Order.PASSIVE),"Explicit orders preserved");
  check(!PlayerBotFollowIntent.traveling(Order.FOLLOW,true,true,true,false),"Combat role positioning preserved");
  check(!PlayerBotFollowIntent.traveling(Order.FOLLOW,false,false,true,false),"Idle buffs still available");
  check(!PlayerBotFollowIntent.traveling(Order.FOLLOW,false,true,false,false),"Dead owner blocks follow");
  check(!PlayerBotFollowIntent.traveling(Order.FOLLOW,false,true,true,true),"Incapacitation blocks follow");
  var engine=new PlayerBotEngine();var plan=new PlayerBotStrategyComposition.Plan(State.NON_COMBAT);
  plan.defaults("buff",action("optional buff"),NORMAL,State.NON_COMBAT);plan.defaults("follow",action("follow"),PlayerBotFollowIntent.priority(true),State.NON_COMBAT);plan.tick(engine,20);
  check(engine.getLastAction().equals("follow"),"Actual engine prioritizes moving formation over idle buff");
  plan.defaults("heal",action("emergency support"),EMERGENCY,State.NON_COMBAT);plan.tick(engine,20);
  check(engine.getLastAction().equals("emergency support"),"Emergency support wins over travel");
  check(PlayerBotFollowIntent.priority(false)==DEFAULT,"Stopped follow retains idle priority");
  PlayerBotConfig.TICK_MS=2000;check(PlayerBotFollowIntent.lifetime()>=6000,"Follow intent survives configured decision interval");PlayerBotConfig.TICK_MS=400;
 }
 static void intent()throws Exception{
  var owner=actor(1999980010);var bot=actor(1999980011);owner.mover.setInMove(true);bot.mover.setInMove(true);bot.mover.setNewDirection(10,20,0,(byte)0);
  var goal=new PlayerBotNavigation.Point(10,20,0);
  try{
   check(PlayerBotFollowIntent.goal(bot)==null,"Missing resolved geometry cannot trigger party lookup");
   PlayerBotFollowIntent.remember(owner,bot,0,goal);
   var cached=PlayerBotFollowIntent.goal(bot);
   check(cached.x()==10 && cached.y()==20,"Movement resolves cached geometry without native account/party/world state");
   PlayerBotFollowIntent.bind(owner,bot,0,goal,List.of());check(PlayerBotFollowIntent.active(bot),"Admitted direct intent retains movement between decisions");
   bot.mover.setNewDirection(11,20,0,(byte)0);check(!PlayerBotFollowIntent.active(bot),"Another action's target is never refreshed");
   PlayerBotFollowIntent.bind(owner,bot,0,goal,List.of());check(!PlayerBotFollowIntent.active(bot),"Detour waypoint cannot bind direct following");
   bot.mover.setNewDirection(10,20,0,(byte)0);PlayerBotFollowIntent.bind(owner,bot,0,goal,List.of());owner.mover.setInMove(false);check(!PlayerBotFollowIntent.active(bot),"Owner stop restores normal arrival/stop");owner.mover.setInMove(true);
   PlayerBotFollowIntent.clear(bot);check(!PlayerBotFollowIntent.active(bot),"Stop/cast/navigation clears intent");
  }finally{PlayerBotFollowIntent.forget(bot);}
  check(PlayerBotFollowIntent.goal(bot)==null,"Dismissal releases cached owner geometry");
 }
 static void trajectories(){
  for(String shape:PlayerBotFormationLayout.NAMES)for(double leader:new double[]{6,12,30})for(double natural:new double[]{1,4,8})for(int slot=0;slot<5;slot++){
   double angle=0,x=-15,y=0,error=0,steady=0,last=0;int stops=0;
   for(int tick=0;tick<750;tick++){
    double t=tick*.2,ox=t<75?t*leader:75*leader,oy=t<75?0:(t-75)*leader;
    angle=PlayerBotFormation.turn(angle,t<75?0:Math.PI/2,.2);
    var goal=PlayerBotFormationLayout.point((float)ox,(float)oy,0,angle,slot,5,shape);
    double d=Math.hypot(goal.x()-x,goal.y()-y),step=Math.min(d,natural*PlayerBotFormation.speed(natural,leader,d,true)*.2);
    if(d>0){x+=(goal.x()-x)*step/d;y+=(goal.y()-y)*step/d;}
    last=Math.hypot(goal.x()-x,goal.y()-y);
    if(tick>100){error=Math.max(error,last);if(t<75 || t>85)steady=Math.max(steady,last);if(step<.001)stops++;}
   }
   check(stops==0,"Moving production layout must keep progressing: "+shape+"/"+slot);
   check(error<8,"Turn recovery stays bounded: "+shape+"/"+slot+"="+error);
   check(steady<1 && last<1,"No permanent formation lag after turning: "+shape+"/"+slot+"="+steady);
  }
  check(PlayerBotFormation.speed(1,30,2,true)>=30,"Leader matching has no class-speed ratio ceiling");
  check(PlayerBotFormation.speed(4,12,0,true)==3,"At slot, match leader speed");
  check(PlayerBotFormation.speed(4,12,100,true)<=4.5,"Catch-up is bounded to 150 percent of leader/native baseline");
  check(PlayerBotFormation.speed(0,12,10,true)==1,"Invalid native speed safe");
 }
 public static void main(String[] args)throws Exception{
  var f=Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);unsafe=(Unsafe)f.get(null);
  summons();priorities();intent();trajectories();
  System.out.println("OK: "+checks+" production summon, native config, arbitration, direct-intent and 180 formation trajectory checks; gameplay pending user");
 }
}
