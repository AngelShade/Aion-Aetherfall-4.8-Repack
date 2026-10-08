package com.aionemu.gameserver.services.playerbot;

/** Pure encounter decisions; no world actors, services, IDs or database. */
public final class PlayerBotSteelRakeCheck {
 private static int checks;
 private static void check(boolean value,String label){if(!value)throw new AssertionError(label);checks++;}
 public static void main(String[] args){
  check(PlayerBotSteelRake.feeder(false,false)==0,"no unnecessary interaction");
  check(PlayerBotSteelRake.feeder(true,false)==701386,"hunger uses food");
  check(PlayerBotSteelRake.feeder(false,true)==701387,"thirst uses water");
  check(PlayerBotSteelRake.feeder(true,true)==701386,"one device per decision");
  for(String name:new String[]{"brasseyegrogget","gunnerkoakoa","tamer_anikiki","engineerlahulahu","golden_eye_mantutu","unrelated"}){
   check(!PlayerBotSteelRake.shielded(name,false,false,false),"normal offense "+name);
   check(PlayerBotSteelRake.shielded(name,true,false,false)==name.equals("brasseyegrogget"),"captain phase "+name);
   check(PlayerBotSteelRake.shielded(name,false,true,false)==name.equals("gunnerkoakoa"),"gunner phase "+name);
   check(PlayerBotSteelRake.shielded(name,false,false,true)==name.equals("tamer_anikiki"),"tamer phase "+name);
  }
  System.out.println("OK: "+checks+" Steel Rake policy checks; gameplay acceptance pending");
 }
}
