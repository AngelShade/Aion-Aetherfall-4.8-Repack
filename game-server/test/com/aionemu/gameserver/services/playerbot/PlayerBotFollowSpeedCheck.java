package com.aionemu.gameserver.services.playerbot;
import java.lang.reflect.*;
import java.util.Map;
import sun.misc.Unsafe;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.stats.container.*;
import com.aionemu.gameserver.model.stats.calc.*;
import com.aionemu.gameserver.controllers.PlayerController;
import com.aionemu.gameserver.controllers.movement.PlayerBotMoveController;
import com.aionemu.gameserver.network.aion.serverpackets.SM_EMOTION;
import com.aionemu.gameserver.model.EmotionType;
public final class PlayerBotFollowSpeedCheck {
 static class Stats extends PlayerGameStats {Stat2 speed,attack;Stats(){super(null);}@Override public Stat2 getMovementSpeed(){return speed;}@Override public Stat2 getAttackSpeed(){return attack;}}
 static class Bot extends Player {int id;Stats stats;PlayerController controller;Bot(){super(null,null);}@Override public int getObjectId(){return id;}@Override public PlayerGameStats getGameStats(){return stats;}@Override public PlayerController getController(){return controller;}}
 static class Controller extends PlayerController {boolean combat;@Override public boolean isInCombat(){return combat;}}
 static void field(Object object,String name,Object value)throws Exception{for(Class<?> type=object.getClass();type!=null;type=type.getSuperclass())try{var f=type.getDeclaredField(name);f.setAccessible(true);f.set(object,value);return;}catch(NoSuchFieldException e){}throw new NoSuchFieldException(name);}
 @SuppressWarnings("unchecked") public static void main(String[] args)throws Exception {
  Field f=Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);var u=(Unsafe)f.get(null);var bot=(Bot)u.allocateInstance(Bot.class);bot.id=1900000000;bot.stats=(Stats)u.allocateInstance(Stats.class);field(bot.stats,"owner",bot);bot.stats.speed=new AdditionStat(StatEnum.SPEED,6000,bot);bot.stats.attack=new AdditionStat(StatEnum.ATTACK_SPEED,1000,bot);bot.controller=new Controller();field(bot,"playerBotOwnerId",123);var movement=new PlayerBotMoveController(bot);field(bot,"moveController",movement);movement.setInMove(true);
  Field values=PlayerBotFollowSpeed.class.getDeclaredField("VALUES");values.setAccessible(true);var map=(Map<Integer,Object>)values.get(null);var type=Class.forName(PlayerBotFollowSpeed.class.getName()+"$Value");var ctor=type.getDeclaredConstructors()[0];ctor.setAccessible(true);
  try {
   map.put(bot.id,ctor.newInstance(2.25,13.5f,System.currentTimeMillis()+10000));
   if(bot.getGameStats().getMovementSpeedFloat()!=13.5f)throw new AssertionError("Server movement does not use the advertised catch-up speed");
   SM_EMOTION packet=new SM_EMOTION(bot,EmotionType.CHANGE_SPEED);Field speed=SM_EMOTION.class.getDeclaredField("speed");speed.setAccessible(true);if(speed.getFloat(packet)!=13.5f)throw new AssertionError("Native client speed packet differs from server travel speed");
   field(bot,"playerBotOwnerId",0);if(bot.getGameStats().getMovementSpeedFloat()!=6)throw new AssertionError("Human speed changed");field(bot,"playerBotOwnerId",123);
   ((Controller)bot.controller).combat=true;if(bot.getGameStats().getMovementSpeedFloat()!=6)throw new AssertionError("Combat retains catch-up assistance");((Controller)bot.controller).combat=false;
   movement.setInMove(false);if(bot.getGameStats().getMovementSpeedFloat()!=6)throw new AssertionError("Stopped bot retains assisted speed");movement.setInMove(true);
   map.put(bot.id,ctor.newInstance(2.25,13.5f,System.currentTimeMillis()-1));if(bot.getGameStats().getMovementSpeedFloat()!=6)throw new AssertionError("Expired following assistance persists");
   System.out.println("OK: 6 native server/client speed-packet equality, human/combat/stop/expiry restoration checks");
  }finally{map.remove(bot.id);}
 }
}
