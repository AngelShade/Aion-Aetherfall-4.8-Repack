package com.aionemu.gameserver.services.playerbot;

import java.nio.file.Files;
import java.nio.file.Path;
import com.aionemu.gameserver.services.instance.InstanceScaler;

/** Dungeon difficulty (owner 8 Nov: harder than the scaled open world). Pure maths plus wiring; no World/SQL/tasks. */
public final class InstanceDifficultyCheck {
 static int checks;
 static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 static boolean near(float a,float b){return Math.abs(a-b)<1e-6;}

 public static void main(String[] args) throws Exception {
  check(near(InstanceScaler.multiplier(false,0.5f,3f),3f),"With party scaling off, the difficulty alone applies");
  check(near(InstanceScaler.multiplier(true,0.5f,3f),1.5f),"With scaling on, both multiply");
  check(near(InstanceScaler.multiplier(false,1f,1f),1f),"Native stays native");
  check(near(InstanceScaler.multiplier(false,1f,50f),10f) && near(InstanceScaler.multiplier(false,1f,0f),0.1f) && near(InstanceScaler.multiplier(false,1f,Float.NaN),1f),
   "A bad setting is clamped to 0.1..10, never zero, infinite or NaN");
  var root=args.length>0?Path.of(args[0]):Path.of(".");
  var scaler=Files.readString(root.resolve("src/com/aionemu/gameserver/services/instance/InstanceScaler.java"));
  var config=Files.readString(root.resolve("config/main/instance.properties"));
  check(scaler.contains("return (INSTANCE_SCALING_ENABLE || difficulty()) && instance.getMaxPlayers() > 1"),"Difficulty works for group dungeons even with party scaling off");
  check(scaler.contains("multiplier(INSTANCE_SCALING_ENABLE, calculateMultiplier(instance, INSTANCE_SCALING_HP_FLOOR") && scaler.contains("multiplier(INSTANCE_SCALING_ENABLE, calculateMultiplier(instance, INSTANCE_SCALING_DMG_FLOOR"),
   "HP and attack both carry the difficulty, and the run reward demand follows");
  check(config.contains("gameserver.instance.difficulty.hp_multiplier = ") && config.contains("gameserver.instance.difficulty.damage_multiplier = "),"Both settings are documented");
  System.out.println("OK: "+checks+" dungeon difficulty checks; no World/SQL/tasks");
 }
 private InstanceDifficultyCheck(){}
}
