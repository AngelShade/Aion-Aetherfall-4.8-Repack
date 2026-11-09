package com.aionemu.gameserver.services.playerbot;

import java.nio.file.Files;
import java.nio.file.Path;

/** PvP-6: duels with bots through the native duel flow. Pure accept rules plus wiring; no World/SQL/tasks. */
public final class PlayerBotDuelsCheck {
 static int checks;
 static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}

 public static void main(String[] args) throws Exception {
  check(PlayerBotDuels.free(false,false,false,80,false),"A healthy idle bot is free to duel");
  check(!PlayerBotDuels.free(true,false,false,80,false) && !PlayerBotDuels.free(false,true,false,80,false) && !PlayerBotDuels.free(false,false,true,80,false)
   && !PlayerBotDuels.free(false,false,false,60,false) && !PlayerBotDuels.free(false,false,false,80,true),"Dead, fighting, busy, hurt or already dueling bots are not free");
  check(PlayerBotDuels.accepts(true,false,69,70) && !PlayerBotDuels.accepts(true,false,70,70),"An ordinary bot accepts at the configured chance");
  check(PlayerBotDuels.accepts(true,true,99,0),"A PVP-leaning bot always accepts when free");
  check(!PlayerBotDuels.accepts(false,true,0,100),"A bot that is not free never accepts");
  check(PlayerBotDuels.accepts(true,false,0,150) && !PlayerBotDuels.accepts(true,false,0,-5),"The chance is clamped to 0..100");
  check(PlayerBotDuels.ASK_RANGE<=8 && PlayerBotDuels.SCAN_RANGE<=30 && PlayerBotDuels.WALK_MS>=10000 && PlayerBotDuels.PLAN_MS>=5000,"A challenger is picked nearby, walks over and asks face to face, or gives up");
  check(PlayerBotDuels.faceToFace(1.2,true) && !PlayerBotDuels.faceToFace(1.2,false) && !PlayerBotDuels.faceToFace(5,true) && PlayerBotDuels.ASK_RANGE<=1.5f && PlayerBotDuels.WALK_TO<PlayerBotDuels.ASK_RANGE,"The question only comes in plain sight, from a few metres away (owner play test: no requests through walls)");
  check(PlayerBotDuels.purse(30,150)==4500 && PlayerBotDuels.purse(0,150)==150 && PlayerBotDuels.purse(30,-5)==0,"The purse grows with level and a bad setting pays nothing");
  long now=10_000_000L;
  check(PlayerBotDuels.waiting(now,now+1,true,1,false),"After asking, the bot waits in front of the player (owner play test: it walked off at once)");
  check(!PlayerBotDuels.waiting(now,now+1,true,1,true),"The duel starting ends the wait");
  check(!PlayerBotDuels.waiting(now,now,true,1,false) && !PlayerBotDuels.waiting(now,now+1,false,1,false) && !PlayerBotDuels.waiting(now,now+1,true,PlayerBotDuels.LEAVE_RANGE+1,false),
   "No answer in time, the player gone or walked away: the bot stops waiting");
  check(PlayerBotDuels.paid(9,10,0,now) && !PlayerBotDuels.paid(10,10,0,now),"Paid duels stop at the daily limit");
  check(!PlayerBotDuels.paid(0,10,now-60000,now) && PlayerBotDuels.paid(0,10,now-PlayerBotDuels.SAME_BOT_MS,now),"The same bot pays again only after 30 minutes");
  wiring(args.length>0?Path.of(args[0]):Path.of("."));
  System.out.println("OK: "+checks+" duel checks; no World/SQL/tasks");
 }

 static void wiring(Path root) throws Exception {
  var src=root.resolve("src/com/aionemu/gameserver/services");
  var duel=Files.readString(src.resolve("DuelService.java"));
  var pvp=Files.readString(src.resolve("playerbot/PlayerBotPvp.java"));
  var service=Files.readString(src.resolve("playerbot/PlayerBotService.java"));
  var duels=Files.readString(src.resolve("playerbot/PlayerBotDuels.java"));
  var config=Files.readString(root.resolve("config/playerbots/pvp.properties"));
  check(duel.contains("com.aionemu.gameserver.services.playerbot.PlayerBotDuels.requested(requester, targetPlayer);") && duel.indexOf("PlayerBotDuels.requested") > duel.indexOf("putRequest(SM_QUESTION_WINDOW.STR_DUEL_DO_YOU_ACCEPT_REQUEST"),
   "A bot is told of a duel only after the native question is registered");
  check(duel.contains("com.aionemu.gameserver.services.playerbot.PlayerBotDuels.ended(winner, loser);"),"Duel endings reach the bot for its emote");
  check(duels.contains("respond(SM_QUESTION_WINDOW.STR_DUEL_DO_YOU_ACCEPT_REQUEST,1)") && duels.contains("respond(SM_QUESTION_WINDOW.STR_DUEL_DO_YOU_ACCEPT_REQUEST,0)"),
   "Bots answer through the native question, so the native duel start, countdown and draw rules apply");
  check(duels.contains("EmotionId.SALUTE") && duels.contains("EmotionId.NO") && duels.contains("EmotionId.VICTORY") && duels.contains("EmotionId.THANK"),
   "Accepting, declining, winning and losing each show a native emote");
  check(pvp.contains("target instanceof Player duelist && com.aionemu.gameserver.services.DuelService.getInstance().isDueling(bot,duelist)"),"The duel opponent is an explicit target, so the bot really fights");
  check(pvp.contains("if(!continuing && !duel && !GeoService.getInstance().canSee(bot,target))continue;"),"A duel opponent behind a wall is still fought: the bot walks round to them");
  check(duels.contains("navigation.approach(human,WALK_TO)") && duels.contains("\", fancy a duel?\"") && Files.readString(src.resolve("playerbot/PlayerBotSession.java")).contains("PlayerBotDuels.approach(this,navigation,now)"),"The challenger walks up, says so in chat and only then asks");
  check(duels.contains("if(asked!=null)return await(bot,asked,navigation,now);") && duels.contains("navigation.stop();bot.setTarget(human);face(bot,human);return true;"),
   "While waiting it stands facing the player and takes no other task");
  check(duels.contains("bot.getResponseRequester().respond(com.aionemu.gameserver.network.aion.serverpackets.SM_QUESTION_WINDOW.STR_DUEL_DO_YOU_WITHDRAW_REQUEST,1);"),
   "Giving up withdraws through the native question, closing the player's window");
  int reject=duel.indexOf("rejectDuelRequest(requester, responder);"),told=duel.indexOf("com.aionemu.gameserver.services.playerbot.PlayerBotDuels.declined(requester, responder);");
  check(reject>0 && told>reject && told-reject<120,"A no from the player reaches the waiting bot, right after the native rejection");
  check(service.contains("PlayerBotDuels.plan(sessions.values(),System.currentTimeMillis());") && service.contains("PlayerBotDuels.stats()"),"Bots may challenge, and duels are reported");
  check(duels.contains("if(winner!=null && loser!=null && !winner.isPlayerBot() && loser.isWorldBot())reward(winner,loser);")
   && duels.contains("human.getInventory().increaseKinah(kinah);") && duels.contains("AbyssPointsService.addAp(human,ap);"),"A human who beats a world bot is paid through the native Kinah and AP services");
  for(var key:new String[]{"duels.enable","duels.accept_percent","duels.challenge_minutes","duels.reward_kinah_per_level","duels.reward_ap","duels.rewards_per_day"})check(config.contains("gameserver.playerbots.pvp."+key+" = "),"Documented setting "+key);
 }
 private PlayerBotDuelsCheck(){}
}
