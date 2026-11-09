package com.aionemu.gameserver.services.playerbot;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import com.aionemu.gameserver.services.playerbot.PlayerBotHelpObjectives.Counter;

/** Find Group help the owner can follow: join accepts, chat for every step, rewards settle themselves, bot parties stay rejoinable. */
public final class PlayerBotHelpTrackingCheck {
 static int checks;
 static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}

 public static void main(String[] args) throws Exception {
  lines();
  wiring(args.length>0?Path.of(args[0]):Path.of("."));
  System.out.println("OK: "+checks+" help tracking checks; no World/SQL/tasks");
 }

 static Counter item(long current){return new Counter("item:1","Clodworm Hide","ITEM",1,0,current,10);}
 static Counter stage(long current){return new Counter("stage","Native stage","STAGE",0,0,current,-1);}
 static Counter turnin(long current){return new Counter("turnin","Bot turn-in","TURNIN",0,0,current,1);}
 static void lines() {
  check(PlayerBotHelpObjectives.progressLine(List.of(item(3),stage(1),turnin(0)),List.of(item(4),stage(2),turnin(0))).equals("Clodworm Hide 4/10"),
   "Only visible counters that moved are reported, and the internal quest step is never shown");
  check(PlayerBotHelpObjectives.progressLine(List.of(item(4),turnin(0)),List.of(item(4),turnin(0))).isEmpty(),"Nothing moved, nothing said");
  check(PlayerBotHelpObjectives.progressLine(List.of(item(10),turnin(0)),List.of(item(10),turnin(1))).equals("Companion turns in the quest 1/1"),"The hand-in is reported in plain words");
  check(PlayerBotHelpObjectives.progressLine(null,List.of(item(2))).equals("Clodworm Hide 2/10"),"A first report lists the counter");
  check(PlayerBotHelpObjectives.objectivesDone(List.of(item(10),stage(3),turnin(0))),"Full hunt and item counters mean only the bot's hand-in remains");
  check(!PlayerBotHelpObjectives.objectivesDone(List.of(item(9),turnin(0))),"Not yet");
  check(!PlayerBotHelpObjectives.objectivesDone(List.of(stage(3),turnin(0))),"A request with no countable objective is never reported done early");
  check(PlayerBotHelpObjectives.rewardLine(3,3,1,4).equals("You earned 1 Recruitment Ticket! Tickets now: 4."),"Every third help pays a ticket and says the balance");
  check(PlayerBotHelpObjectives.rewardLine(4,3,1,4).equals("Help 1/3 toward your next Recruitment Ticket. Tickets now: 4."),"Between tickets the progress is shown");
  check(PlayerBotHelpObjectives.rewardLine(6,3,2,9).startsWith("You earned 2 Recruitment Tickets!"),"Plural tickets read naturally");
  check(PlayerBotHelpObjectives.rewardLine(1,0,1,0).contains("switched off"),"Switched-off rewards are said plainly");
 }

 static void wiring(Path root) throws Exception {
  var src=root.resolve("src/com/aionemu/gameserver/services/playerbot");
  var help=Files.readString(src.resolve("PlayerBotHelpObjectives.java"));
  var findGroup=Files.readString(src.resolve("PlayerBotFindGroup.java"));
  var service=Files.readString(src.resolve("PlayerBotService.java"));
  var dao=Files.readString(root.resolve("src/com/aionemu/gameserver/dao/PlayerBotRecruitmentDAO.java"));
  check(findGroup.contains("try{PlayerBotHelpObjectives.accept(inviter,invited,post.need);}") && findGroup.contains("\"[Help] Not tracked: \"+refused.getMessage()"),
   "Joining a field help post accepts its tracked contract, and a refusal says why");
  check(help.contains("introduce(b,bot,human);") && help.contains("\"You are helping \"+c.botName") && help.contains("ChatType.BRIGHT_YELLOW_CENTER"),
   "Accepting announces what to do, where, when it ends and the reward, with a centre-screen notice");
  check(help.contains("if(saved.status().equals(\"ACTIVE\"))announce(b,previous);") && help.contains("\"All objectives done! Stay in \""),"Every committed step is announced, and the hand-in phase too");
  check(help.contains("settle(b);}") && help.contains("case \"COMPLETE\"->") && help.contains("close(b,true)") && help.contains("EmotionId.THANK.id()"),
   "A completed request pays its reward at once; the bot thanks the helper with a native emote");
  check(help.contains("case \"RESOLVED\"->") && help.contains("case \"CANCELLED\"->") && help.contains("case \"REVISED\"->"),
   "Requests that end without reward close themselves with the reason, so the account's one active slot is never stuck");
  check(help.contains("if(!b.row.status().equals(\"ACTIVE\"))settle(b);"),"A request that ended while the helper was away settles at the next login");
  check(service.contains("boolean keep=PlayerBotFindGroup.keepsParty(bot);") && service.contains("if(!keep)PlayerBotTeams.remove(bot);") && service.contains("PlayerBotHelpObjectives.left(previous.owner(),bot);"),
   "When the human leaves, the bot party stays together and the human is told how to rejoin");
  check(findGroup.contains("if (controller==bot && reopens(post)) { post.controller=null; return; }") && findGroup.contains("!post.need.instance()"),
   "The field help listing stays open for rejoining; instance runs keep their own ending");
  check(dao.contains("public static long helps(Connection c,int account)"),"Ticket progress is read from the real help ledger");
 }
 private PlayerBotHelpTrackingCheck(){}
}
