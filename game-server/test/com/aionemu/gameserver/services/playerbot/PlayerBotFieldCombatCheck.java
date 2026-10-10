package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import sun.misc.Unsafe;
import com.aionemu.gameserver.controllers.PlayerController;
import com.aionemu.gameserver.controllers.attack.AggroList;
import com.aionemu.gameserver.controllers.effect.EffectController;
import com.aionemu.gameserver.model.PlayerClass;
import com.aionemu.gameserver.model.gameobjects.*;
import com.aionemu.gameserver.model.gameobjects.state.CreatureState;
import com.aionemu.gameserver.world.knownlist.KnownList;
import static com.aionemu.gameserver.services.playerbot.PlayerBotEngine.*;
import static com.aionemu.gameserver.services.playerbot.PlayerBotRules.*;

/** Production pull action, native combat timer and post-kill arbitration on isolated actors.
 * No native casts, World, SQL, registration or native character/object ID allocation. */
public final class PlayerBotFieldCombatCheck {
 static int checks;static Unsafe unsafe;
 static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 static class Clock extends PlayerController {
  long last;
  @Override public long getLastCombatTime(){return last;}
  @Override public void onTargetChanged(VisibleObject oldTarget,VisibleObject newTarget){}
 }
 static class Actor extends PlayerBotClericCheck.Actor {
  Clock control;KnownList seen;
  boolean dead,flying,looting,trading,resting,attack=true;
  @Override public boolean isWorldBot(){return true;}
  @Override public com.aionemu.gameserver.model.account.Account getAccount(){return new com.aionemu.gameserver.model.account.Account(1999999081);}
  @Override public boolean isDead(){return dead;}
  @Override public boolean isFlying(){return flying;}
  @Override public boolean isLooting(){return looting;}
  @Override public boolean isTrading(){return trading;}
  @Override public boolean canAttack(){return attack;}
  @Override public boolean isInState(CreatureState state){return state==CreatureState.RESTING && resting;}
  @Override public PlayerController getController(){return control;}
  @Override public KnownList getKnownList(){return seen;}
  @Override public boolean isEnemy(Creature c){return c instanceof Enemy;}
 }
 static class Enemy extends PlayerBotQuestObjectivesCheck.Contact {
  boolean dead,flag;
  @Override public boolean isDead(){return dead;}
  @Override public boolean isFlag(){return flag;}
  @Override public Creature getMaster(){return this;}
 }
 static Action action(String name,boolean useful,boolean executes){return new Action(){
  public String name(){return name;}public boolean isUseful(){return useful;}
  public boolean isPossible(){return true;}public boolean execute(){return executes;}
 };}
 static String choose(Action pull,Action loot,Action heal) {
  var plan=new PlayerBotStrategyComposition.Plan(State.NON_COMBAT);var engine=new PlayerBotEngine();
  plan.triggers("loot",State.NON_COMBAT).add(new Trigger(()->true,loot,()->DEFAULT+2));
  plan.triggers("recovery",State.NON_COMBAT).add(new Trigger(()->true,heal,()->HIGH));
  plan.triggers("field pull",State.NON_COMBAT).add(new Trigger(()->true,pull,()->DEFAULT));
  plan.defaults("travel",action("travel",true,true),DEFAULT-2,State.NON_COMBAT);
  check(plan.tick(engine,64),"Native strategy arbitration chooses a useful next action");return engine.getLastAction();
 }
 public static void main(String[] args)throws Exception {
  var f=Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);unsafe=(Unsafe)f.get(null);
  // Previously these live resource bands permitted neither a fresh pull nor rest.
  for(double[] resources:new double[][]{{80,100},{100,50},{80,50},{70,60},{85,40}}) {
   check(!PlayerBotRules.shouldRest(resources[0],resources[1],false,false,false,0),"Reproduce the previous rest/pull dead band");
   check(PlayerBotFieldCombat.recover(resources[0],resources[1],false,false),"Every blocked field resource band has an active recovery path");
   check(!PlayerBotFieldCombat.ready(resources[0],resources[1],false),"Unsafe fresh pulls remain refused");
   check(!PlayerBotFieldCombat.recover(resources[0],resources[1],false,true),"Actual combat never schedules sitting recovery");
  }
  check(PlayerBotFieldCombat.ready(85,60,false),"Existing entry thresholds retained");
  check(!PlayerBotFieldCombat.ready(90,80,true),"Ongoing recovery retains its native hysteresis");
  check(PlayerBotFieldCombat.ready(95,95,true),"Recovered field actor may stand and resume hunting");
  for(double[] resources:new double[][]{{0,100},{Double.NaN,100},{100,Double.NaN},{100,-1},{100,Double.POSITIVE_INFINITY}})
   check(!PlayerBotFieldCombat.ready(resources[0],resources[1],false),"Dead/invalid resource snapshots fail closed");
  var bot=(Actor)unsafe.allocateInstance(Actor.class);bot.spawned=true;bot.attack=true;bot.pc=PlayerClass.GLADIATOR;
  bot.life=(PlayerBotSorcererCheck.Life)unsafe.allocateInstance(PlayerBotSorcererCheck.Life.class);bot.life.hp=bot.life.mp=1000;
  bot.control=new Clock();bot.control.last=System.currentTimeMillis();
  var npc=(Enemy)unsafe.allocateInstance(Enemy.class);npc.id=1999999001;npc.template=213360;npc.world=npc.instance=1;npc.x=5;npc.spawned=true;
  PlayerBotQuestObjectivesCheck.field(npc,"aggroList",new AggroList(npc));
  PlayerBotQuestObjectivesCheck.field(npc,"effectController",new EffectController(npc));
  bot.seen=new KnownList(bot){@Override public boolean sees(VisibleObject o){return o==npc;}};
  var session=(PlayerBotSession)unsafe.allocateInstance(PlayerBotSession.class);
  PlayerBotQuestObjectivesCheck.field(session,"owner",bot);PlayerBotQuestObjectivesCheck.field(session,"bot",bot);
  PlayerBotQuestObjectivesCheck.field(session,"order",Order.FOLLOW);PlayerBotQuestObjectivesCheck.field(session,"engine",new PlayerBotEngine());
  PlayerBotQuestObjectivesCheck.field(session,"questing",true);
  var pull=session.fieldPullAction(npc,List.of(bot));
  check(bot.control.isInCombat(),"Actual Aion controller retains its ten-second combat timer after a kill");
  check(pull.isUseful(),"Production fresh pull is independent of the NPC interaction timer");
  check(choose(pull,action("loot",true,true),action("heal",false,true)).equals("loot"),"Earned loot precedes an optional fresh pull");
  check(bot.getTarget()==null,"Loot arbitration did not prematurely commit a new enemy");
  check(choose(pull,action("loot",true,false),action("heal",false,true)).equals(pull.name()),"Failed loot does not permanently prevent the next eligible fight");
  check(bot.getTarget()==npc,"Production action commits the selected native actor");
  var explicit=PlayerBotSession.class.getDeclaredMethod("explicitTarget",Npc.class);explicit.setAccessible(true);
  check((boolean)explicit.invoke(session,npc),"Next decision retains the committed field target");
  npc.x=30;check((boolean)explicit.invoke(session,npc),"A selected monster moving beyond acquisition range remains retained inside the native leash");
  npc.x=46;check(!(boolean)explicit.invoke(session,npc),"A field target beyond the native leash is released");npc.x=5;
  check(session.reactionDelay()==500,"Committed field pull uses the existing combat reaction");
  check(bot.control.isInCombat(),"Selecting a pull never clears or fabricates native combat state");
  bot.control.last=0;PlayerBotQuestObjectivesCheck.field(session,"questTarget",0);
  check(choose(pull,action("loot",false,true),action("heal",true,true)).equals("heal"),"Necessary recovery remains above optional offense");
  check(!bot.control.isInCombat(),"Action planning never creates a combat timer without a native attack");
  for(Order order:new Order[]{Order.PASSIVE,Order.STAY,Order.GUARD}) {
   PlayerBotQuestObjectivesCheck.field(session,"order",order);check(!pull.execute(),"Fresh field pull respects order "+order);
  }
  PlayerBotQuestObjectivesCheck.field(session,"order",Order.FOLLOW);
  for(String blocked:new String[]{"dead","flying","looting","trading"}) {
   var value=Actor.class.getDeclaredField(blocked);value.setBoolean(bot,true);check(!pull.execute(),"Native field pull respects "+blocked);value.setBoolean(bot,false);
  }
  bot.attack=false;check(!pull.execute(),"Crowd control prevents a fresh pull");bot.attack=true;
  bot.life.hp=800;check(!pull.execute(),"Changed HP is rechecked at execution");bot.life.hp=1000;
  npc.dead=true;check(!pull.execute(),"A killed candidate is not recommitted");npc.dead=false;
  npc.flag=true;check(!pull.execute(),"Native territory flags remain scenery");npc.flag=false;
  npc.world=2;check(!pull.execute(),"Cross-map candidates cannot be attacked");npc.world=1;
  npc.x=50;check(!pull.execute(),"Existing admission range is retained");npc.x=5;
  PlayerBotQuestObjectivesCheck.field(session,"closing",true);check(!pull.execute(),"Closing/custody lifecycle blocks pull commit");
  PlayerBotQuestObjectivesCheck.field(session,"closing",false);
  var owner=(Actor)unsafe.allocateInstance(Actor.class);PlayerBotQuestObjectivesCheck.field(session,"owner",owner);
  check(!pull.execute(),"Private or human-controlled companions keep their existing initiation policy");
  var states=PlayerBotPartyBehavior.class.getDeclaredField("STATES");states.setAccessible(true);((Map<?,?>)states.get(null)).clear();
  System.out.println("OK: "+checks+" field post-kill/recovery/native timer/loot arbitration and production pull checks; no casts/World/DB/IDs");
 }
}
