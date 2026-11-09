package com.aionemu.gameserver.services.playerbot;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import com.aionemu.gameserver.controllers.movement.PlayerBotMoveController;

/** 8 Nov live fixes: sleepers park at own service points, companion speed never drifts from the client, movement trace. */
public final class PlayerBotSleepMotionCheck {
 static int checks;
 static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}

 public static void main(String[] args) throws Exception {
  check(PlayerBotMoveController.traceNames(" Aleridora, bob ,,").equals(Set.of("aleridora","bob")),"Trace names are trimmed, case-free and blanks ignored");
  check(PlayerBotMoveController.traceNames("").isEmpty() && PlayerBotMoveController.traceNames(null).isEmpty(),"An empty setting traces nobody");
  check(PlayerBotWorldPlacement.HUB_PARK_RADIUS>=8 && PlayerBotWorldPlacement.HUB_PARK_RADIUS<=30,"Beside a service point means a few metres, not the whole outpost");
  wiring(args.length>0?Path.of(args[0]):Path.of("."));
  System.out.println("OK: "+checks+" sleep and motion checks; no World/SQL/tasks");
 }

 static void wiring(Path root) throws Exception {
  var src=root.resolve("src/com/aionemu/gameserver/services/playerbot");
  var session=Files.readString(src.resolve("PlayerBotSession.java"));
  var placement=Files.readString(src.resolve("PlayerBotWorldPlacement.java"));
  var safety=Files.readString(src.resolve("PlayerBotCampSafety.java"));
  var offscreen=Files.readString(src.resolve("PlayerBotOffscreenTurnIns.java"));
  var speed=Files.readString(src.resolve("PlayerBotFollowSpeed.java"));
  var mover=Files.readString(root.resolve("src/com/aionemu/gameserver/controllers/movement/PlayerBotMoveController.java"));
  var navigation=Files.readString(root.resolve("config/playerbots/navigation.properties"));
  var population=Files.readString(root.resolve("config/playerbots/population-management.properties"));
  check(session.contains("(hubs?!PlayerBotWorldPlacement.atHub(bot):PlayerBotCampSafety.dangerNow(bot))") && session.contains("var safe=hubs?PlayerBotWorldPlacement.nearestHub(bot):null;boolean hub=safe!=null;")
   && session.contains("if(safe==null)safe=PlayerBotWorldPlacement.nearestSafe(bot,bot.getLevel());"),"A sleeper moves to an own service point, falling back to the nearest safe camp");
  check(session.contains("&& !PlayerBotRoadDuty.assigned(bot) && !PlayerBotOffscreenTurnIns.talking(bot.getObjectId(),System.currentTimeMillis());") && offscreen.contains("static boolean talking(int bot,long now)"),
   "Road careers keep their routes and a bot about to hand a quest in is not moved away");
  check(session.contains("!PlayerBotWorldPlacement.nearHuman(safe.map(),safe.x(),safe.y(),safe.z())") && session.contains("if(hub)PlayerBotCampSafety.HUB_PARKS.incrementAndGet();"),
   "Moves stay unseen and only real moves are counted");
  check(placement.contains("static Location nearestHub(Player bot)") && placement.contains("guard.valid(x,y,z) && !occupiedLanding(p.map(),x,y,z)") && placement.contains("p.race()==race && p.hub()"),
   "Hub spots use the camp landing guard, never stack bots and only use the bot's own faction");
  check(safety.contains("static void sampleParkedAttack(Player bot)") && safety.contains("PARKED_SAMPLES.get() >= 40") && session.contains("PlayerBotCampSafety.sampleParkedAttack(bot);")
   && safety.contains("(to own service points "),"Attacks that still reach sleepers are sampled with the attacker, and hub moves are reported");
  check(speed.contains("else VALUES.put(player.getObjectId(),new Value(natural>0?old.advertised()/natural:ratio,old.advertised(),System.currentTimeMillis()+600));"),
   "Below the resend threshold the server keeps the speed the client was told, so companions do not snap back");
  check(mover.contains("protected void setAndSendStartMove(com.aionemu.gameserver.model.gameobjects.Creature creature){trace(\"start packet\");")
   && mover.contains("protected void setAndSendStopMove(com.aionemu.gameserver.model.gameobjects.Creature creature){trace(\"stop packet\");")
   && mover.contains("if(traced())trace((walkBlocked?\"abort (step blocked)\":\"abort\")+\" from \"+caller(6));"),"Every start, stop and abort of a traced bot is logged, aborts with their caller");
  check(navigation.contains("gameserver.playerbots.navigation.trace_moves =") && population.contains("gameserver.playerbots.management.camp_safety.park_in_hubs = true"),"Both settings are documented");
 }
 private PlayerBotSleepMotionCheck(){}
}
