package com.aionemu.gameserver.services.playerbot;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import com.aionemu.gameserver.services.playerbot.PlayerBotHelpObjectives.Contract;
import com.aionemu.gameserver.services.playerbot.PlayerBotHelpObjectives.Counter;

/** The help request inside the native quest journal and tracker (owner 8 Nov): counters in the
 * tracker, the full card in the journal and the journal's own buttons. Pure text plus wiring. */
public final class PlayerBotHelpJournalCheck {
 static int checks;
 static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}

 public static void main(String[] args) throws Exception {
  text();
  wiring(args.length>0?Path.of(args[0]):Path.of("."));
  System.out.println("OK: "+checks+" help journal checks; no World/SQL/tasks");
 }

 static Contract contract(){
  var c=new Contract();c.botName="Garasa";c.need="[Group] Temptations";c.quest=1234;c.map=210020000;c.x=1234.6f;c.y=567.2f;c.z=100;
  c.objectives=List.of(new Counter("stage","Native stage","STAGE",0,0,2,-1),new Counter("item:1","Alluring Temptress","ITEM",1,0,3,5),
   new Counter("item:2","Enticing Temptress","ITEM",2,0,3,3),new Counter("turnin","Bot turn-in","TURNIN",0,0,0,1));
  return c;
 }
 static void text() {
  var c=contract();
  String step=PlayerBotHelpObjectives.step(c,"ACTIVE");
  check(step.equals("Alluring Temptress 3/5, Enticing Temptress 3/3"),"The tracker step is every counter, without repeating the title or the bot's own hand-in: "+step);
  check(PlayerBotHelpObjectives.step(c,"COMPLETE").endsWith(" - reward ready"),"A finished request says its reward is waiting");
  var none=new Contract();none.need="target";none.objectives=List.of(new Counter("stage","Native stage","STAGE",0,0,1,-1));
  check(!PlayerBotHelpObjectives.step(none,"ACTIVE").isEmpty(),"A request with no counter still has a step line for the native row");
  check(PlayerBotHelpObjectives.rewardPreview(0,1,1).equals("1 Recruitment Ticket"),"One ticket per help is said plainly, not as help 1/1");
  check(PlayerBotHelpObjectives.rewardPreview(5,1,2).equals("2 Recruitment Tickets"),"Plural");
  check(PlayerBotHelpObjectives.rewardPreview(0,3,1).equals("Help 1/3 toward 1 Recruitment Ticket"),"Several helps per ticket count up");
  check(PlayerBotHelpObjectives.rewardPreview(2,3,1).equals("1 Recruitment Ticket (help 3/3)"),"The help that completes the set names the ticket");
  check(PlayerBotHelpObjectives.rewardPreview(0,0,1).startsWith("Help rewards are switched off"),"Disabled rewards are not promised");
  String page=PlayerBotHelpObjectives.page(c,"ACTIVE","Eltnen",new long[]{0,7},1,1);
  check(page.startsWith("<html><body text=\"#302405\" topmargin=\"5\" leftmargin=\"15\">") && page.endsWith("</body></html>"),"A whole page with the native quest page's body colour and margins");
  check(page.contains("<font font_xml=\" v3_quest_title\" > ") && page.contains("<font font_xml=\"v3_quest_subtitle\">Basic Reward</font>"),"Native quest title and reward heading");
  check(page.contains("v5_quest_working_bullet\" width=\"28\" height=\"21\"></button></td><td align=\"left\" valign=\"top\"><font font_xml=\"v3_quest_list\"><p align=\"left\">Alluring Temptress (3/5)</p>")
   && page.contains("v5_quest_finished_bullet\" width=\"28\" height=\"21\"></button></td><td align=\"left\" valign=\"top\"><font font_xml=\"v3_quest_list\"><p align=\"left\"><font color=\"#6F6F64\">Enticing Temptress (3/3)</font>")
   && !page.contains("hands in the quest"),"Each objective is a native step row, finished ones greyed; the hand-in no longer gates the helper");
  int steps=page.indexOf("</table>"),locate=page.indexOf("<button href=\"af_help_location\" font_xml=\"v3_quest_button\" skin=\"v5_quest_button\" width=\"73\" height=\"26\" disabled=\"false\">Locate</button>"),track=page.indexOf("<button href=\"af_help_track\"");
  check(steps>0 && locate>steps && track>locate && page.indexOf("Basic Reward")>track,"Locate and Track sit right under the objectives, where a native quest's Locate does");
  check(!page.contains("Collect reward</button>"),"No reward button in the page: Collect reward is the journal's own");
  var ranged=contract();ranged.minLevel=20;ranged.maxLevel=25;var single=contract();single.minLevel=30;single.maxLevel=30;
  check(PlayerBotHelpObjectives.levels(ranged).equals("Level 20-25 ") && PlayerBotHelpObjectives.levels(single).equals("Level 30 ") && PlayerBotHelpObjectives.levels(c).isEmpty(),"The head names the level range like a native quest");
  check(!page.contains("Native stage"),"The internal quest stage is never shown");
  check(page.contains("Where: Eltnen near (1234, 567)."),"The location is named");
  check(page.contains("1 Recruitment Ticket (you have 7)</font>"),"The reward and the current balance are shown");
  check(page.contains("stay in the party until then"),"While in progress it says how it finishes");
  var odd=contract();odd.botName="Ga<b>rasa";
  check(PlayerBotHelpObjectives.page(odd,"ACTIVE","Eltnen",null,1,1).contains("Ga&lt;b&gt;rasa") && !PlayerBotHelpObjectives.page(odd,"ACTIVE","Eltnen",null,1,1).contains("<b>"),"Names cannot inject markup");
  check(page.contains("[Group] Temptations") && !page.contains("[%"),"Brackets stay readable but never form a native template token");
  check(PlayerBotHelpObjectives.escape("a<b>&[%dic:X]\nz").equals("a&lt;b&gt;&amp;[ %dic:X] z"),"Markup, template tokens and new lines are neutralised");
  String done=PlayerBotHelpObjectives.page(c,"COMPLETE","",null,1,1);
  check(done.contains("Press Collect reward at the bottom of the quest journal") && done.contains("Where: this map") && done.contains(">1 Recruitment Ticket</font>")
   && !done.contains("v5_quest_working_bullet"),"A finished request points at the journal's Collect reward, every step finished; missing map and ledger stay readable");
  var nowhere=contract();nowhere.map=0;
  check(PlayerBotHelpObjectives.page(nowhere,"ACTIVE","",null,1,1).contains("height=\"26\" disabled=\"true\">Locate</button>"),"Locate is greyed out when there is no place to mark");
  var all=contract();all.objectives=List.of(new Counter("item:1","Alluring Temptress","ITEM",1,0,5,5),new Counter("turnin","Bot turn-in","TURNIN",0,0,0,1));
  check(PlayerBotHelpObjectives.page(all,"ACTIVE","Eltnen",null,1,1).contains("All objectives done. Stay in the party; it completes in a few seconds."),"The short wait before it completes is explained");
 }
 static void wiring(Path root) throws Exception {
  var help=Files.readString(root.resolve("src/com/aionemu/gameserver/services/playerbot/PlayerBotHelpObjectives.java"));
  var http=Files.readString(root.resolve("src/com/aionemu/gameserver/services/PlayerBotHttpService.java"));
  check(http.contains("\"card\".equals(format) ? PlayerBotHelpObjectives.card(helper, slot)"),"The native client reads the card format, per help slot");
  check(http.contains("\"journal\".equals(format) ? title + \"\\n\" + PlayerBotHelpObjectives.progress(helper)"),"An older client still gets its two journal lines");
  check(http.contains("path.equals(\"/market/companions/help/action\")") && http.contains("!method.equals(\"POST\") || !\"1\".equals(exchange.getRequestHeaders().getFirst(\"X-Aetherfall-Native\"))"),
   "Journal buttons are native POSTs only; a web page cannot send them");
  check(http.contains("synchronized (helper) { outcome = PlayerBotHelpObjectives.journal(helper"),"One journal action at a time per character, like the Companions actions");
  check(help.contains("case \"claim\"->{") && help.contains("action(human,\"claim\",b.row.key())"),"Collect reward goes through the same audited close, which refuses unconfirmed requests");
  check(help.contains("}catch(IllegalArgumentException e){tell(human,e.getMessage(),false);return e.getMessage();}"),"A refused button says why in chat");
  check(help.contains("\" Reward: \"+rewardPreview(progress[0],needed,PlayerBotEconomyConfig.TICKET_HELP_AMOUNT)"),"The joining message names the reward the same way");
 }
 private PlayerBotHelpJournalCheck(){}
}
