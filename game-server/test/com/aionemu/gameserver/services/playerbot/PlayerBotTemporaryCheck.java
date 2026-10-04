package com.aionemu.gameserver.services.playerbot;
import java.lang.reflect.*;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.templates.item.ItemQuality;
import sun.misc.Unsafe;
public final class PlayerBotTemporaryCheck {
 public static void main(String[] args)throws Exception{
  Field field=Unsafe.class.getDeclaredField("theUnsafe");field.setAccessible(true);var u=(Unsafe)field.get(null);var alt=(Player)u.allocateInstance(Player.class);var session=(PlayerBotSession)u.allocateInstance(PlayerBotSession.class);
  PlayerBotTemporary.tick(session,true); // generated=false: returns before reading or changing any character state.
  if(PlayerBotTemporary.managed(alt))throw new AssertionError("An owned alt became managed");
  try{session.setAutoGear(true);throw new AssertionError("Alt automatic equipment enabled");}catch(IllegalArgumentException expected){}
  try{PlayerBotGearPolicy.configure(session,"GENERATED","TANK","MYTHIC","65","1.0","AUTO","false","UPGRADES");throw new AssertionError("Alt generated gear enabled");}catch(IllegalArgumentException expected){}
  for(int level=1;level<=65;level++){
   int expected=level<20 ? 0 : level/10*10;if(PlayerBotTemporary.tier(level)!=expected)throw new AssertionError("Tier at "+level);
   if(PlayerBotTemporary.regularSlots(level)!=(level<20 ? 0 : level<30 ? 1 : level<40 ? 2 : 3))throw new AssertionError("Regular socket cap");
   if(PlayerBotTemporary.advancedSlots(level)!=(level<45 ? 0 : level<50 ? 1 : level<55 ? 2 : 3))throw new AssertionError("Advanced socket cap");
  }
  if(PlayerBotTemporary.quality(20)!=ItemQuality.LEGEND || PlayerBotTemporary.quality(30)!=ItemQuality.UNIQUE || PlayerBotTemporary.quality(40)!=ItemQuality.EPIC || PlayerBotTemporary.quality(60)!=ItemQuality.MYTHIC)throw new AssertionError("Native quality progression");
  System.out.println("OK: 200 Temporary Bot tier/socket boundaries and owned-alt automatic-management rejection checks");
 }
}
