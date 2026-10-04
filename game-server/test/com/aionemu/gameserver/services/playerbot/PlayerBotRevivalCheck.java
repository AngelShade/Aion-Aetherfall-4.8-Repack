package com.aionemu.gameserver.services.playerbot;
import java.lang.reflect.*;
import java.util.Map;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.gameobjects.state.CreatureState;
import com.aionemu.gameserver.model.stats.container.PlayerLifeStats;
import com.aionemu.gameserver.controllers.movement.PlayerBotMoveController;
/** Check the actual overlapping native stance masks without a database or world. */
public class PlayerBotRevivalCheck {
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
  System.out.println("OK: "+checks+" actual native stance checks; living ground/air looting and shop/rest states preserved, corpse/death recovery pauses and cleanup pass.");
 }
}
