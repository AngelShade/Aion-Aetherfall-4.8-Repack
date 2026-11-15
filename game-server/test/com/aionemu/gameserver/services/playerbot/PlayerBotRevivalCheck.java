package com.aionemu.gameserver.services.playerbot;
import java.lang.reflect.*;
import java.util.Map;
import com.aionemu.gameserver.instance.handlers.InstanceHandler;
import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.gameobjects.state.CreatureState;
import com.aionemu.gameserver.model.instance.InstanceProgressionType;
import com.aionemu.gameserver.model.instance.instancescore.DredgionInstanceScore;
import com.aionemu.gameserver.model.stats.container.PlayerLifeStats;
import com.aionemu.gameserver.controllers.movement.PlayerBotMoveController;
import com.aionemu.gameserver.world.WorldMap2DInstance;
import com.aionemu.gameserver.world.WorldMapInstance;
import com.aionemu.gameserver.world.WorldPosition;
/** Check the actual overlapping native stance masks without a database or world. */
public class PlayerBotRevivalCheck {
 static final class ShipBot extends Player {
  WorldMapInstance instance;
  WorldPosition position;
  boolean dead=true;
  ShipBot(){super(null,null);}
  @Override public WorldPosition getPosition(){return position;}
  @Override public WorldMapInstance getWorldMapInstance(){return instance;}
  @Override public boolean isInInstance(){return true;}
  @Override public boolean isSpawned(){return true;}
  @Override public boolean isDead(){return dead;}
  @Override public boolean isPlayerBot(){return true;}
  @Override public boolean canUseRebirthRevive(){return false;}
 }
 static void set(Object o,String name,Object value)throws Exception {
  for(Class<?> c=o.getClass();c!=null;c=c.getSuperclass())try{var f=c.getDeclaredField(name);f.setAccessible(true);f.set(o,value);return;}catch(NoSuchFieldException e){}
  throw new NoSuchFieldException(name);
 }
 public static void main(String[] ignored)throws Exception {
  var f=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);var unsafe=(sun.misc.Unsafe)f.get(null);
  Player p=(Player)unsafe.allocateInstance(Player.class);PlayerLifeStats life=(PlayerLifeStats)unsafe.allocateInstance(PlayerLifeStats.class);
  set(p,"objectId",1999999999);set(p,"lifeStats",life);set(life,"currentHp",100);set(p,"moveController",unsafe.allocateInstance(PlayerBotMoveController.class));
  int checks=0;
  for(int stance:new int[]{1,5,3,12,13,11,513}){
   p.setState(stance);int before=p.getState();
   if(!PlayerBotRevival.ready(p)||p.getState()!=before)throw new AssertionError("Living stance mistaken for corpse: "+stance);
   checks++;
  }
  p.setState(CreatureState.DEAD,true);if(PlayerBotRevival.ready(p))throw new AssertionError("Living stale corpse did not enter recovery");checks++;PlayerBotRevival.close(p);
  p.setState(CreatureState.FLOATING_CORPSE,true);if(PlayerBotRevival.ready(p))throw new AssertionError("Living floating corpse did not enter recovery");checks++;PlayerBotRevival.close(p);
  p.setState(CreatureState.DEAD,true);set(life,"currentHp",0);if(PlayerBotRevival.ready(p))throw new AssertionError("Actual death did not pause");checks++;PlayerBotRevival.close(p);
  set(life,"currentHp",100);p.setState(CreatureState.ACTIVE.getId());
  checks+=shipRevival(unsafe,p);
  System.out.println("OK: "+checks+" native stance, death delay, recovery and Dredgion instance revive checks.");
 }

 static int shipRevival(sun.misc.Unsafe unsafe,Player owner)throws Exception {
  var bot=(ShipBot)unsafe.allocateInstance(ShipBot.class);
  set(bot,"objectId",1999999998);bot.dead=true;
  set(bot,"moveController",new PlayerBotMoveController(bot));
  bot.instance=(WorldMapInstance)unsafe.allocateInstance(WorldMap2DInstance.class);
  bot.position=new WorldPosition(300440000){@Override public WorldMapInstance getWorldMapInstance(){return bot.instance;}};
  var score=new DredgionInstanceScore(4500,2500,3750,Race.ASMODIANS);
  int[] revives={0};
  var handler=(InstanceHandler)Proxy.newProxyInstance(InstanceHandler.class.getClassLoader(),new Class<?>[]{InstanceHandler.class},(proxy,method,args)->switch(method.getName()) {
   case "getInstanceScore" -> score;
   case "allowInstanceRevive" -> true;
   case "onReviveEvent" -> {revives[0]++;bot.dead=false;PlayerBotRevival.beginRecovery(bot);yield true;}
   default -> throw new AssertionError(method.getName());
  });
  set(bot.instance,"instanceHandler",handler);
  var session=(PlayerBotSession)unsafe.allocateInstance(PlayerBotSession.class);
  set(session,"bot",bot);set(session,"owner",owner);
  try {
   if(PlayerBotRecovery.needsRegroup(session))throw new AssertionError("Dredgion death requested automatic Summon");
   score.setInstanceProgressionType(InstanceProgressionType.START_PROGRESS);
   if(PlayerBotRevival.ready(bot) || revives[0]!=0)throw new AssertionError("Dredgion death skipped its animation delay");
   var states=PlayerBotRevival.class.getDeclaredField("STATES");states.setAccessible(true);
   var state=(PlayerBotRevival.State)((Map<?,?>)states.get(null)).get(bot.getObjectId());
   state.death=System.currentTimeMillis()-PlayerBotRevival.DEATH_DELAY;
   for(var phase:new InstanceProgressionType[]{InstanceProgressionType.PREPARING,InstanceProgressionType.END_PROGRESS}) {
    score.setInstanceProgressionType(phase);
    if(PlayerBotRevival.ready(bot) || revives[0]!=0)throw new AssertionError("Unexpected instance revive during "+phase);
   }
   score.setInstanceProgressionType(InstanceProgressionType.START_PROGRESS);
   if(PlayerBotRevival.ready(bot) || bot.isDead() || revives[0]!=1)throw new AssertionError("Active Dredgion did not dispatch the native instance revive");
   if(PlayerBotRevival.ready(bot) || revives[0]!=1)throw new AssertionError("Native resurrection must finish once before decisions resume");
   return 6;
  }finally{PlayerBotRevival.close(bot);}
 }
}
