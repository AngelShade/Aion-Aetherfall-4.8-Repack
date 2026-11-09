package com.aionemu.gameserver.services.playerbot;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/** Owner play-test 8 Nov (point 5): several help requests at once, one native wrapper quest per slot. Pure rules plus wiring. */
public final class PlayerBotHelpSlotsCheck {
 static int checks;
 static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}

 public static void main(String[] args) throws Exception {
  rules();
  wiring(args.length>0?Path.of(args[0]):Path.of("."));
  System.out.println("OK: "+checks+" help slot checks; no World/SQL/tasks");
 }
 static void rules() {
  check(PlayerBotHelpObjectives.HELP_QUESTS.length==3 && PlayerBotHelpObjectives.HELP_QUESTS[0]==PlayerBotHelpObjectives.HELP_QUEST,"Three slots; the first keeps the original 14996");
  check(PlayerBotHelpObjectives.slotOf(14996)==0 && PlayerBotHelpObjectives.slotOf(14998)==1 && PlayerBotHelpObjectives.slotOf(14999)==2,"Each wrapper quest is one slot");
  check(PlayerBotHelpObjectives.slotOf(14997)==-1 && !PlayerBotHelpObjectives.helpQuest(14997),"Road Bounty (14997) is not a help slot");
  check(PlayerBotHelpObjectives.freeSlot(Set.of(),3)==0 && PlayerBotHelpObjectives.freeSlot(Set.of(0),3)==1 && PlayerBotHelpObjectives.freeSlot(Set.of(0,2),3)==1,"The lowest free slot is used");
  check(PlayerBotHelpObjectives.freeSlot(Set.of(0,1,2),3)==-1,"All slots taken: no fourth request");
  check(PlayerBotHelpObjectives.freeSlot(Set.of(0),1)==-1 && PlayerBotHelpObjectives.freeSlot(Set.of(),1)==0,"A limit of 1 keeps the old single-request rule");
  check(PlayerBotHelpObjectives.freeSlot(Set.of(),9)==0 && PlayerBotHelpObjectives.freeSlot(Set.of(0,1,2),9)==-1,"Never more slots than wrapper quests");
  long now=1_000_000;
  check(PlayerBotHelpObjectives.firstEntry(null,now) && PlayerBotHelpObjectives.firstEntry(now-PlayerBotHelpObjectives.ENTER_ONCE_MS,now),"A login is handled");
  check(!PlayerBotHelpObjectives.firstEntry(now-1000,now),"The second call of the same login (another wrapper's handler) does nothing: no reward is settled twice");
  // Tracker lines under the help row (point 5b): one counter per line, the count in the tracker's yellow.
  var c=new PlayerBotHelpObjectives.Contract();c.botName="Garasa";c.need="[Group] Temptations";c.quest=1234;
  c.objectives=java.util.List.of(new PlayerBotHelpObjectives.Counter("stage","Native stage","STAGE",0,0,2,-1),new PlayerBotHelpObjectives.Counter("item:1","Alluring Temptress","ITEM",1,0,3,5),
   new PlayerBotHelpObjectives.Counter("item:2","Enticing <b>Temptress","ITEM",2,0,2,3),new PlayerBotHelpObjectives.Counter("turnin","Bot turn-in","TURNIN",0,0,0,1));
  String lines=PlayerBotHelpObjectives.tracker(c,"ACTIVE");
  check(lines.equals("Alluring Temptress <font color=\"#ffd84a\">(3/5)</font><br>Enticing &lt;b&gt;Temptress <font color=\"#ffd84a\">(2/3)</font>"),
   "Each counter on its own line, names escaped, no stage or turn-in: "+lines);
  check(PlayerBotHelpObjectives.tracker(c,"COMPLETE").endsWith("Reward ready</font>"),"A finished request says so under its title");
  var done=new PlayerBotHelpObjectives.Contract();done.quest=1;done.botName="G";done.objectives=java.util.List.of(new PlayerBotHelpObjectives.Counter("item:1","X","ITEM",1,0,5,5));
  check(PlayerBotHelpObjectives.tracker(done,"ACTIVE").endsWith("All done - completing</font>"),"The few seconds before it completes are shown");
 }
 static void wiring(Path root) throws Exception {
  var src=root.resolve("src/com/aionemu/gameserver");
  var help=Files.readString(src.resolve("services/playerbot/PlayerBotHelpObjectives.java"));
  var dao=Files.readString(src.resolve("dao/PlayerBotHelpDAO.java"));
  var http=Files.readString(src.resolve("services/PlayerBotHttpService.java"));
  var delete=Files.readString(src.resolve("network/aion/clientpackets/CM_DELETE_QUEST.java"));
  var quests=Files.readString(root.resolve("data/static_data/quest_data/quest_data.xml"));
  var js=Files.readString(root.resolve("config/playerbots/media/bots.js"));
  var features=Files.readString(root.resolve("config/playerbots/features.properties"));
  check(dao.contains("KEY help_account (active_account), UNIQUE KEY help_requester (active_requester)"),"New tables allow several requests per account, one per bot");
  check(dao.contains("INDEX_NAME='help_account'") && dao.contains("ALTER TABLE playerbot_help_requests DROP INDEX help_account, ADD KEY help_account (active_account)"),
   "An existing table is migrated once from the one-request index");
  check(dao.contains("public static java.util.List<Row> active(Connection c,int account)") && dao.contains("if(existing.size()>=maxActive)throw new IllegalArgumentException"),
   "SQL lists every active request and refuses past the limit");
  check(help.contains("var used=new HashSet<Integer>();for(var b:active)used.add(b.slot);") && help.contains("contract.slot=slot;"),"A new request takes a free slot, saved with it");
  check(help.contains("slot=Math.max(0,committed.slot);") && help.contains("public int slot;"),"Requests saved before slots read back as slot 0");
  check(help.contains("int quest=questOf(b);") && help.contains("else QuestService.abandonQuest(human,quest);"),"Each request shows and hides its own wrapper quest");
  check(help.contains("q.setStatus(QuestStatus.COMPLETE);q.setQuestVar(0);q.setFlags(0);") && help.contains("hide(b,claim);") && help.contains("hide(b,saved.status().equals(\"CLAIMED\"));"),
   "A finished request closes its wrapper as completed, not abandoned (no 'failed' before 'complete')");
  check(help.contains("if(!firstEntry(ENTERED.put(human.getObjectId(),now),now))return;") && help.contains("for(var b:bindings(human)) {"),"Login handles every request, once");
  check(help.contains("for(int quest:HELP_QUESTS)if(!shownQuests.contains(quest) && helping(human,quest))QuestService.abandonQuest(human,quest);"),"A wrapper with no request behind it is cleared");
  check(help.contains("public static String card(Player human,int slot)") && help.contains("public static String journal(Player human,String operation,int slot)"),"Card and journal buttons work per slot");
  check(help.contains("public static String action(Player human,String operation,String key)") && help.contains("public static void abandonFromJournal(Player human,int quest)"),
   "The web controls pick a request by key; native Abandon closes that wrapper's request");
  check(help.contains("var b=human==null || human.isPlayerBot()?null:BY_REQUESTER.get(bot.getObjectId());"),"Leaving a party is matched to that bot's request");
  check(http.contains("int slot = slot(helpQuery.get(\"slot\"));") && http.contains("PlayerBotHelpObjectives.journal(helper, helpValues.getOrDefault(\"op\", \"\"), slot(helpValues.get(\"slot\")))"),
   "The native client names the slot for its card and buttons");
  check(http.contains("state.put(\"helpRequests\", PlayerBotHelpObjectives.snapshots(owner));") && http.contains("state.put(\"helpMax\", PlayerBotHelpObjectives.maxActive());")
   && http.contains("PlayerBotHelpObjectives.action(owner,op.substring(4),values.getOrDefault(\"key\",\"\"))"),"The Help requests window gets every request, the limit and keyed actions");
  check(delete.contains("PlayerBotHelpObjectives.helpQuest(questId)") && delete.contains("abandonFromJournal(player, questId)"),"Native Abandon works on every wrapper quest");
  check(quests.contains("<quest id=\"14998\" name=\"Help Request\" nameId=\"1801385\"") && quests.contains("<quest id=\"14999\" name=\"Help Request\" nameId=\"1801387\""),
   "Server quest data has the two extra wrappers");
  check(Files.exists(root.resolve("data/handlers/quest/aetherfall/_14998HelpRequest.java")) && Files.exists(root.resolve("data/handlers/quest/aetherfall/_14999HelpRequest.java")),
   "Each wrapper has its native handler");
  check(js.contains("mines.forEach(function (mine) {") && js.contains("{ action:'helpclaim', key:mine.key }") && js.contains("!c.eligible || full"),
   "The web window lists every request with its own controls and accepts until the limit");
  check(features.contains("gameserver.playerbots.social.findgroup.help_max_active = "),"The limit is a setting");
  check(help.contains("public static String card2(Player human,int slot)") && http.contains("\"card2\".equals(format) ? PlayerBotHelpObjectives.card2(helper, slot)"),
   "card2 carries the tracker lines for the native client; card stays for older cores");
 }
 private PlayerBotHelpSlotsCheck(){}
}
