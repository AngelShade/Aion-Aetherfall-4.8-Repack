package com.aionemu.gameserver.services.playerbot;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Phase 1: residents keep local work, sleeping bots turn finished quests in unseen, presence picks bots with local work. */
public final class PlayerBotLocalWorkCheck {
 static int checks;
 static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}

 public static void main(String[] args) throws Exception {
  ranking();
  rationing();
  wiring(args.length>0?Path.of(args[0]):Path.of("."));
  System.out.println("OK: "+checks+" local work checks; no World/SQL/tasks");
 }

 static void ranking() {
  var ranked=PlayerBotPresencePicks.rank(List.of("a","b","c","d"),s->switch(s){case "b"->2;case "d"->2;case "c"->1;default->0;});
  check(ranked.equals(List.of("b","d","c","a")),"Most local work first; equal scores keep the shuffled order: "+ranked);
  check(PlayerBotPresencePicks.rank(List.<String>of(),s->1).isEmpty(),"No eligible bot, no pick");
  check(PlayerBotPresencePicks.rank(List.of("x","y"),s->0).equals(List.of("x","y")),"Without local work the random order stands");
  check(PlayerBotPresencePicks.localWork(null,null)==0,"No player area, no score");
 }

 static void rationing() {
  long now=7_000_000L;int granted=0;
  for(int i=0;i<10;i++)if(PlayerBotOffscreenTurnIns.slot(now))granted++;
  check(granted==PlayerBotOffscreenTurnIns.TURNS_PER_SECOND,"Turn-ins are rationed per second server-wide: "+granted);
  check(PlayerBotOffscreenTurnIns.slot(now+1000),"The next second opens new turns");
  check(PlayerBotOffscreenTurnIns.INTERVAL_MS>=60_000 && PlayerBotOffscreenTurnIns.TALK_DELAY_MS<PlayerBotOffscreenTurnIns.INTERVAL_MS,"A bot looks at most every few minutes and talks soon after moving");
 }

 static void wiring(Path root) throws Exception {
  var src=root.resolve("src/com/aionemu/gameserver/services/playerbot");
  var activities=Files.readString(src.resolve("PlayerBotWorldActivities.java"));
  var service=Files.readString(src.resolve("PlayerBotService.java"));
  var session=Files.readString(src.resolve("PlayerBotSession.java"));
  var quests=Files.readString(src.resolve("PlayerBotQuests.java"));
  var turnIns=Files.readString(src.resolve("PlayerBotOffscreenTurnIns.java"));
  check(activities.contains("boolean nearbyResident=PlayerBotPlayerPresence.resident(bot);") && !activities.contains("quest==0 && PlayerBotPlayerPresence.resident(bot)"),
   "1.1: accepted quests and rewards of a resident stay in the player area, like new work");
  check(session.contains("PlayerBotOffscreenTurnIns.due(this,System.currentTimeMillis())") && session.contains("if(worldDormant)PlayerBotOffscreenTurnIns.turn(this,System.currentTimeMillis());"),
   "1.2: a sleeping bot wakes for its turn-in and runs it in the dormant turn");
  check(quests.contains("(!mission && !offscreen && !PlayerBotQuestObjectives.job(bot,job))") && quests.contains("return interact(bot, bot, new Job(npc, quest, true), role, false, true);"),
   "1.2: the offscreen turn-in uses the same native dialog, reward choice and checks as an awake bot");
  check(turnIns.contains("PlayerBotWorldActivity.allowed(session) || PlayerBotPlayerPresence.resident(bot)") && turnIns.contains("PlayerBotWorldActivity.occupiedMap(npc.getWorldId())"),
   "1.2: only when no human is on the bot's map or the reward NPC's map");
  check(turnIns.contains("PlayerBotWorldPlacement.placeAt(session,landing)") && turnIns.contains("isPlayerBotLanding"),
   "1.2: the unseen move uses the normal placement guards on standable ground");
  check(turnIns.contains("PlayerBotQuests.ordinary(template)") && turnIns.contains("PlayerBotQuestExchange.exchange(quest)"),
   "1.2: only ordinary quests, never exchanges or story missions");
  check(service.contains("PlayerBotPresencePicks.rank(eligible") && service.contains("PlayerBotPresencePicks.picked(localWork)"),
   "1.3: presence moves the eligible bot with the most local work and counts real placements");
  check(service.contains("\"Local work: enabled=\""),"The report shows Phase 1 counters");
 }
}
