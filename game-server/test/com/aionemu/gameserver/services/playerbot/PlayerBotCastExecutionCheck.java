package com.aionemu.gameserver.services.playerbot;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import com.aionemu.gameserver.controllers.movement.*;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.skillengine.model.Skill;
import static com.aionemu.gameserver.services.playerbot.PlayerBotEngine.*;
import static com.aionemu.gameserver.services.playerbot.PlayerBotRules.*;
import sun.misc.Unsafe;

/** Actual production entrypoints on world-free actors; no DB, task registration, IDs or native casts. */
public final class PlayerBotCastExecutionCheck {
 static Unsafe unsafe;static int checks;
 static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
 static void set(Object value,String name,Object next)throws Exception {
  for(Class<?> type=value.getClass();type!=null;type=type.getSuperclass())try{
   var field=type.getDeclaredField(name);field.setAccessible(true);field.set(value,next);return;
  }catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(name);
 }
 static final class MovementProbe extends AssertionError {}
 static final class Actor extends Player {
  Skill casting;PlayerBotMoveController mover;int movementQueries;Control controller;
  Actor(){super(null,null);}
  @Override public boolean isCasting(){return casting!=null;}
  @Override public boolean isDead(){return false;}
  @Override public Skill getCastingSkill(){return casting;}
  @Override public PlayerMoveController getMoveController(){return mover;}
  @Override public com.aionemu.gameserver.controllers.PlayerController getController(){return controller;}
  @Override public float getX(){return 0;}@Override public float getY(){return 0;}@Override public float getZ(){return 0;}
  @Override public boolean isSpawned(){movementQueries++;throw new MovementProbe();}
  @Override public boolean isInState(com.aionemu.gameserver.model.gameobjects.state.CreatureState state){return false;}
  @Override public com.aionemu.gameserver.model.account.Account getAccount(){throw new AssertionError("Unchanged order reached persistence; no DB allowed in fixture");}
 }
 static final class Control extends com.aionemu.gameserver.controllers.PlayerController {
  int cancellations;
  @Override public void cancelCurrentSkill(com.aionemu.gameserver.model.gameobjects.Creature attacker){cancellations++;((Actor)getOwner()).casting=null;}
 }
 static Actor actor()throws Exception{
  var bot=(Actor)unsafe.allocateInstance(Actor.class);bot.casting=(Skill)unsafe.allocateInstance(Skill.class);bot.mover=new PlayerBotMoveController(bot);bot.controller=new Control();bot.controller.setOwner(bot);return bot;
 }
 static PlayerBotSession session(Actor bot,PlayerBotPreferences.Values values)throws Exception {
  var session=(PlayerBotSession)unsafe.allocateInstance(PlayerBotSession.class);
  set(session,"bot",bot);set(session,"owner",bot);set(session,"engine",new PlayerBotEngine());set(session,"order",Order.FOLLOW);
  set(session,"role",values.role());set(session,"areaSkills",values.area());set(session,"consumables",values.supplies());
  set(session,"autoGear",values.gear());set(session,"autoLoot",values.loot());set(session,"questing",values.questing());
  set(session,"navigation",new PlayerBotNavigation(bot));set(session,"pets",new PlayerBotPets());
  set(session,"nextDecision",987654321L);return session;
 }
 static void movement()throws Exception {
  for(boolean casting:new boolean[]{true,false})for(boolean started:new boolean[]{true,false})for(boolean moving:new boolean[]{true,false}){
   var bot=actor();if(!casting)bot.casting=null;
   set(bot.mover,"started",new AtomicBoolean(started));bot.mover.setInMove(moving);
   boolean admitted=false;try{bot.mover.moveToDestination();}catch(MovementProbe expected){admitted=true;}
   check(admitted==(!casting&&started&&moving),"Only current moving, noncasting task may reach movement: "+casting+"/"+started+"/"+moving);
   check(bot.mover.isInMove()==moving,"Rejected stale callback changes no movement state");
   check(!bot.mover.hasFailed(),"Rejected stale callback is not a movement error");
   check((bot.casting!=null)==casting,"Scheduled mover preserves current native cast");
  }
 }
 static void orders()throws Exception {
  var values=new PlayerBotPreferences.Values(Role.RANGED,false,true,false,true,true);
  for(Order order:Order.values()) {
   var bot=actor();var session=session(bot,values);set(session,"order",order);Skill before=bot.casting;
   session.order(order);
   check(bot.casting==before,"Repeated "+order+" preserves active cast");
   check((long)field(session,"nextDecision")==987654321L,"Repeated "+order+" preserves native action lock");
  }
  var bot=actor();var session=session(bot,values);set(session,"closing",true);
  try{session.order(Order.FOLLOW);throw new AssertionError("Closing session accepted command");}catch(IllegalArgumentException expected){check(true,"Closing guard survives");}
  set(session,"closing",false);try{session.order(null);throw new AssertionError("Null command accepted");}catch(NullPointerException expected){check(true,"Null order rejected before mutation");}
  var statesField=PlayerBotPartyBehavior.class.getDeclaredField("STATES");statesField.setAccessible(true);
  @SuppressWarnings("unchecked") var states=(Map<Integer,PlayerBotPartyBehavior.State>)statesField.get(null);
  for(String reason:List.of("changed order","explicit attack","mission")){
   var controlled=actor();var commanded=session(controlled,values);
   if(reason.equals("explicit attack"))set(commanded,"commandedTarget",123);
   if(reason.equals("mission"))set(commanded,"mission",unsafe.allocateInstance(PlayerBotMission.class));
   var state=(PlayerBotPartyBehavior.State)unsafe.allocateInstance(PlayerBotPartyBehavior.State.class);
   var previous=states.put(controlled.getObjectId(),state);
   try{
    commanded.order(reason.equals("changed order")?Order.PASSIVE:Order.FOLLOW);
    check(controlled.controller.cancellations==1 && controlled.casting==null,"Explicit "+reason+" still preempts current action once");
    check((long)field(commanded,"nextDecision")==0L,"Explicit "+reason+" allows a fresh decision");
   }finally{if(previous==null)states.remove(controlled.getObjectId());else states.put(controlled.getObjectId(),previous);}
  }
 }
 static Object field(Object object,String name)throws Exception{var f=PlayerBotSession.class.getDeclaredField(name);f.setAccessible(true);return f.get(object);}
 record Chain(String name,List<String> continuers) implements PlayerBotStrategyComposition.ContinuingAction {
  public boolean isUseful(){return true;}public boolean isPossible(){return true;}public boolean execute(){return true;}
 }
 static Trigger trigger(Action action,double priority){return new Trigger(()->true,action,()->priority);}
 static void prime(PlayerBotEngine engine){engine.tick(State.COMBAT,List.of(new Strategy("fixture",List.of(trigger(new Chain("opener",List.of("successor")),40)))),List.of(),8);}
 static String next(PlayerBotEngine engine){engine.tick(State.COMBAT,List.of(new Strategy("fixture",List.of(trigger(new Chain("filler",List.of()),10),trigger(new Chain("successor",List.of()),5)))),List.of(),8);return engine.getLastAction();}
 static void preferences()throws Exception {
  var initial=new PlayerBotPreferences.Values(Role.RANGED,false,true,false,true,true);
  var bot=actor();var session=session(bot,initial);var engine=(PlayerBotEngine)field(session,"engine");
  var apply=PlayerBotSession.class.getDeclaredMethod("applyPreferences",PlayerBotPreferences.Values.class);apply.setAccessible(true);
  prime(engine);apply.invoke(session,initial);
  check(next(engine).equals("successor"),"Unchanged preferences preserve actual engine skill-chain continuer");
  var changes=List.of(new PlayerBotPreferences.Values(Role.SUPPORT,false,true,false,true,true),
   new PlayerBotPreferences.Values(Role.RANGED,true,true,false,true,true),new PlayerBotPreferences.Values(Role.RANGED,false,false,false,true,true),
   new PlayerBotPreferences.Values(Role.RANGED,false,true,true,true,true),new PlayerBotPreferences.Values(Role.RANGED,false,true,false,false,true),
   new PlayerBotPreferences.Values(Role.RANGED,false,true,false,true,false));
  for(var changed:changes){
   apply.invoke(session,initial);prime(engine);Skill before=bot.casting;apply.invoke(session,changed);
   check(next(engine).equals("filler"),"Changed preference invalidates only future arbitration: "+changed);
   check(bot.casting==before,"Preference application preserves current native cast");
   check((long)field(session,"nextDecision")==987654321L,"Preference application preserves current cast lock");
  }
 }
 static void monitor()throws Exception {
  var bot=actor();var session=session(bot,new PlayerBotPreferences.Values(Role.RANGED,false,true,false,true,true));
  var type=Class.forName(PlayerBotSession.class.getName()+"$CastAction");
  var ctor=type.getDeclaredConstructor(PlayerBotSession.class,PlayerBotSkills.Entry.class,com.aionemu.gameserver.model.gameobjects.Creature.class,List.class);ctor.setAccessible(true);
  // Invalid skill is a deliberate planning probe. It must be inspected only AFTER admission acquires the mover monitor.
  Action action=(Action)ctor.newInstance(session,null,bot,List.of());
  var entering=new CountDownLatch(1);var complete=new CountDownLatch(1);var failure=new java.util.concurrent.atomic.AtomicReference<Throwable>();
  Thread worker=new Thread(()->{entering.countDown();try{action.execute();}catch(Throwable error){failure.set(error);}finally{complete.countDown();}},"offline-cast-admission");
  synchronized(bot.mover){worker.start();check(entering.await(1,TimeUnit.SECONDS),"Cast admission worker entered");
   long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(1);
   while(worker.getState()!=Thread.State.BLOCKED && complete.getCount()!=0 && System.nanoTime()<until)Thread.onSpinWait();
   check(worker.getState()==Thread.State.BLOCKED && complete.getCount()==1,"Actual CastAction cannot inspect/execute while a mover step owns admission");
  }
  check(complete.await(1,TimeUnit.SECONDS),"Cast admission releases monitor after failure");
  check(failure.get() instanceof NullPointerException,"Invalid probe never starts a native cast");
  check(bot.casting!=null,"Admission failure retains previous native cast");
 }
 public static void main(String[] args)throws Exception {
  var f=Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);unsafe=(Unsafe)f.get(null);
  var cases=args.length==0?List.of("movement","orders","preferences","monitor"):List.of(args);
  for(String name:cases)switch(name){case "movement"->movement();case "orders"->orders();case "preferences"->preferences();case "monitor"->monitor();default->throw new IllegalArgumentException(name);}
  System.out.println("OK: "+checks+" core cast-execution checks; no native casts, world/DB/IDs or service startup");
 }
}
