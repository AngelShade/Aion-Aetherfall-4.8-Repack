package com.aionemu.gameserver.services.playerbot;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Find Group runs the player can follow: briefing, named boss notices and what the native ending paid. */
public final class PlayerBotRunBriefingCheck {
 static int checks;
 static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}

 public static void main(String[] args) throws Exception {
  check(PlayerBotRunBriefing.names(List.of("Kromede","Vallakhan","Taki","Sedna"),2).equals("Kromede, Vallakhan and 2 more"),"Long boss lists are shortened");
  check(PlayerBotRunBriefing.names(List.of("A","A","B"),6).equals("A, B") && PlayerBotRunBriefing.names(List.of(),6).isEmpty(),"Repeated names once; none, nothing");
  check(PlayerBotRunBriefing.rewardLine(1,984).equals("You earned 1 Recruitment Ticket! Tickets now: 984."),"An earned ticket shows the new balance");
  check(PlayerBotRunBriefing.rewardLine(2,-1).equals("You earned 2 Recruitment Tickets!"),"Without a balance the earning is still shown");
  check(PlayerBotRunBriefing.rewardLine(0,984).contains("counted as easy"),"A run that pays nothing says why");
  var root=args.length>0?Path.of(args[0]):Path.of(".");var src=root.resolve("src/com/aionemu/gameserver");
  var briefing=Files.readString(src.resolve("services/playerbot/PlayerBotRunBriefing.java"));
  var rewards=Files.readString(src.resolve("services/playerbot/PlayerBotRunRewards.java"));
  var findGroup=Files.readString(src.resolve("services/playerbot/PlayerBotFindGroup.java"));
  var npc=Files.readString(src.resolve("controllers/NpcController.java"));
  var service=Files.readString(src.resolve("services/playerbot/PlayerBotService.java"));
  var config=Files.readString(root.resolve("config/playerbots/features.properties"));
  check(findGroup.contains("if(post.need.instance())PlayerBotRunBriefing.joined(inviter,invited,post.need);"),"Joining a dungeon run briefs the player");
  check(briefing.contains("Named bosses in here: ") && briefing.contains("Reward: up to 3 Recruitment Tickets") && briefing.contains("ChatType.BRIGHT_YELLOW_CENTER"),
   "The briefing names the bosses and the reward, with a centre-screen notice");
  check(npc.contains("PlayerBotRunBriefing.npcDied(owner); // tells, never throws") && briefing.contains("if(RUNS.isEmpty())return;"),"Every death is checked cheaply; only briefed runs do any work");
  check(rewards.contains("PlayerBotRunBriefing.rewarded(player,Math.max(0,tickets),label);") && rewards.contains("pay(instance,PlayerBotConfig.TICKET_PVP_WIN,winners,\"Battle\")"),
   "Run and battleground payouts are shown to the player who earned them");
  check(rewards.contains("PlayerBotRunBriefing.unrewarded(p,\"You were not in its fights") && briefing.contains("if(tickets<=0 && !briefed)return;"),
   "Unpaid endings are explained only after a briefed Find Group run, never nagging solo dungeons");
  check(service.contains("lines.add(\"Find Group runs: \"+PlayerBotRunBriefing.stats());") && config.contains("gameserver.playerbots.social.findgroup.run_briefing = "),"Reported and switchable");
  System.out.println("OK: "+checks+" run briefing checks; no World/SQL/tasks");
 }
 private PlayerBotRunBriefingCheck(){}
}
