package com.aionemu.gameserver.services.playerbot;

import java.nio.file.Files;
import java.nio.file.Path;
import com.aionemu.gameserver.services.playerbot.PlayerBotLeanings.Activity;
import com.aionemu.gameserver.services.playerbot.PlayerBotLeanings.Leaning;

/** Phase 4: leanings tilt the hunt, trade and market timers and the presence picks. Pure rules plus wiring. */
public final class PlayerBotLeaningsCheck {
 static int checks;
 static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 static boolean near(double a,double b){return Math.abs(a-b)<1e-9;}

 public static void main(String[] args) throws Exception {
  pacing();
  picks();
  wiring(args.length>0?Path.of(args[0]):Path.of("."));
  System.out.println("OK: "+checks+" leaning checks; no World/SQL/tasks");
 }

 static void pacing() {
  check(near(PlayerBotLeanings.pace(null,Activity.HUNT,40,40),1),"No leaning, usual pace");
  check(near(PlayerBotLeanings.pace(Leaning.HUNTER,Activity.HUNT,9,40),1),"Below level 10 everyone plays alike");
  check(near(PlayerBotLeanings.pace(Leaning.HUNTER,Activity.HUNT,30,40),0.4),"Hunters hunt 2.5 times as often");
  check(near(PlayerBotLeanings.pace(Leaning.QUESTER,Activity.HUNT,30,40),2.5),"Questers wait longer between gear hunts");
  check(near(PlayerBotLeanings.pace(Leaning.TRADE,Activity.TRADE,30,40),0.4) && near(PlayerBotLeanings.pace(Leaning.TRADER,Activity.MARKET,30,40),0.4),
   "Trade bots work and traders shop more often");
  check(near(PlayerBotLeanings.pace(Leaning.PVP,Activity.HUNT,30,40),1) && near(PlayerBotLeanings.pace(Leaning.HUNTER,Activity.MARKET,30,40),1)
   && near(PlayerBotLeanings.pace(Leaning.TRADER,Activity.TRADE,30,40),1),"Anything else keeps the usual pace");
  check(near(PlayerBotLeanings.pace(Leaning.HUNTER,Activity.HUNT,30,5),0.1) && near(PlayerBotLeanings.pace(Leaning.QUESTER,Activity.HUNT,30,100),1),
   "Focus is clamped to 10..100, and 100 switches pacing off");
 }

 static void picks() {
  check(PlayerBotPresencePicks.fit(3,Leaning.QUESTER,20,false)==6,"A quester's local quest work counts double");
  check(PlayerBotPresencePicks.fit(3,Leaning.TRADER,20,true)==5 && PlayerBotPresencePicks.fit(3,Leaning.TRADER,20,false)==3,"A trader scores for a town in the area");
  check(PlayerBotPresencePicks.fit(3,Leaning.HUNTER,20,true)==3 && PlayerBotPresencePicks.fit(3,Leaning.QUESTER,5,false)==3 && PlayerBotPresencePicks.fit(0,null,30,true)==0,
   "Other leanings, low levels and no leaning keep the Phase 1 score");
 }

 static void wiring(Path root) throws Exception {
  var src=root.resolve("src/com/aionemu/gameserver/services/playerbot");
  var activities=Files.readString(src.resolve("PlayerBotWorldActivities.java"));
  var professions=Files.readString(src.resolve("PlayerBotProfessions.java"));
  var market=Files.readString(src.resolve("PlayerBotMarket.java"));
  var service=Files.readString(src.resolve("PlayerBotService.java"));
  var pvp=Files.readString(root.resolve("config/playerbots/pvp.properties"));
  var economy=Files.readString(root.resolve("config/playerbots/economy.properties"));
  check(activities.contains("*PlayerBotLeanings.pace(bot,PlayerBotLeanings.Activity.HUNT));") && activities.contains("PlayerBotLeanings.did(bot,PlayerBotLeanings.Activity.HUNT);"),"The gear-hunt timer follows the leaning and hunts are counted");
  check(professions.contains("s.nextAwake=now+(long)(PlayerBotEconomyConfig.PROFESSIONS_WORK_INTERVAL_SECONDS*1000L*PlayerBotLeanings.pace(s.bot,PlayerBotLeanings.Activity.TRADE));")
   && professions.contains("PROFESSIONS_BACKGROUND_INTERVAL_SECONDS*1000L*PlayerBotLeanings.pace(s.bot,PlayerBotLeanings.Activity.TRADE)"),"Awake and offscreen trade turns follow the same leaning");
  check(market.contains("*PlayerBotLeanings.pace(bot,PlayerBotLeanings.Activity.MARKET))") && market.contains("PlayerBotLeanings.did(bot,PlayerBotLeanings.Activity.MARKET);"),"Market visits follow the leaning and are counted");
  check(service.contains("PlayerBotPresencePicks.fit(k.bot(),areas.get(k))") && service.contains("lines.add(\"Leanings: \"+PlayerBotLeanings.activity());"),"Presence picks use the leaning and the report shows activity per leaning");
  check(pvp.contains("gameserver.playerbots.pvp.leaning_focus_percent = ") && economy.contains("ahead of its next quest step"),"The focus setting is documented and the hunt text matches the code");
 }
 private PlayerBotLeaningsCheck(){}
}
