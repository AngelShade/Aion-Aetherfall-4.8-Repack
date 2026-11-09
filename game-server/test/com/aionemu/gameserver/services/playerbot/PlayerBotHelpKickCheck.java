package com.aionemu.gameserver.services.playerbot;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import com.aionemu.gameserver.services.playerbot.PlayerBotLeanings.Leaning;

/** Owner play-test 8 Nov: an honest hand-in always pays (the party was broken up before the reward check),
 * and the deliberate "ninja kick" some bots pull before the hand-in. Pure maths plus wiring; no World/SQL. */
public final class PlayerBotHelpKickCheck {
 static int checks;
 static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}

 public static void main(String[] args) throws Exception {
  race();
  trait();
  memory();
  wiring(args.length>0?Path.of(args[0]):Path.of("."));
  System.out.println("OK: "+checks+" help hand-in and kick checks; no World/SQL/tasks");
 }
 static void race() {
  check(PlayerBotHelpObjectives.earned(true,true),"Helped near the objective and present at the hand-in: rewarded");
  check(!PlayerBotHelpObjectives.earned(true,false),"Absent at the hand-in (left or kicked): no reward");
  check(!PlayerBotHelpObjectives.earned(false,true),"Present but never helped near the objective: no reward");
  check(!PlayerBotHelpObjectives.together(null,null,true),"Missing characters are never together");
  // Owner play-test: a controlled quest giver follows the leader and never hands in, so the help finishes in place.
  check(PlayerBotHelpObjectives.finishable(true,false,1234,true,true,false,true),"10 s after the last objective, in the party: finished in place");
  check(!PlayerBotHelpObjectives.finishable(true,false,1234,true,true,true,true),"A kicked helper is never finished in place");
  check(!PlayerBotHelpObjectives.finishable(true,false,1234,true,true,false,false),"A helper who left keeps the old rule: rejoin before the bot hands in");
  check(!PlayerBotHelpObjectives.finishable(true,true,1234,true,true,false,true) && !PlayerBotHelpObjectives.finishable(true,false,0,true,true,false,true)
   && !PlayerBotHelpObjectives.finishable(true,false,1234,false,true,false,true) && !PlayerBotHelpObjectives.finishable(true,false,1234,true,false,false,true),
   "Never twice, never for a target hunt, never once closed, never before every counter is full");
  check(PlayerBotHelpObjectives.FINISH_DELAY_MS==10_000,"The owner's 10 seconds");
 }
 static void trait() {
  int none=0,base=0,pvp=0,quester=0;
  for(int id=1;id<=20000;id++){
   if(PlayerBotHelpKick.scoundrel(id,null,0))none++;
   if(PlayerBotHelpKick.scoundrel(id,Leaning.HUNTER,15))base++;
   if(PlayerBotHelpKick.scoundrel(id,Leaning.PVP,15))pvp++;
   if(PlayerBotHelpKick.scoundrel(id,Leaning.QUESTER,15))quester++;
  }
  check(none==0,"0 percent: nobody is the type");
  check(base>2400 && base<3600,"About 15% of bots are the type: "+base+"/20000");
  check(pvp>5400 && pvp<6600,"PvP-leaning bots are twice as likely: "+pvp);
  check(quester>1100 && quester<1900,"Questers half as likely: "+quester);
  check(PlayerBotHelpKick.scoundrel(4242,Leaning.HUNTER,15)==PlayerBotHelpKick.scoundrel(4242,Leaning.HUNTER,15),"The trait is fixed for life");
  int all=0;for(int id=1;id<=500;id++)if(PlayerBotHelpKick.scoundrel(id,Leaning.PVP,100))all++;
  check(all==500,"100 percent caps at everyone");
  check(PlayerBotHelpKick.rolls(40,39) && !PlayerBotHelpKick.rolls(40,40) && !PlayerBotHelpKick.rolls(0,0) && PlayerBotHelpKick.rolls(100,99),"The per-hand-in roll is a plain percent");
  var seen=new HashSet<String>();for(int s=0;s<PlayerBotHelpKick.LINES.length;s++)seen.add(PlayerBotHelpKick.line(77,s));
  check(seen.size()==PlayerBotHelpKick.LINES.length,"Every cheeky line can come up");
 }
 static void memory() {
  long now=1_000_000;
  PlayerBotHelpKick.remember(11,22,now);
  check(PlayerBotHelpKick.kicked(11,22,now+1),"The kicked helper is remembered");
  check(!PlayerBotHelpKick.kicked(22,11,now+1) && !PlayerBotHelpKick.kicked(11,23,now+1),"Only that bot and that helper");
  check(!PlayerBotHelpKick.kicked(11,22,now+PlayerBotHelpKick.MEMORY_MS),"Forgiven after a while");
  check(!PlayerBotHelpKick.kicked(11,22,now+1),"An expired memory is dropped");
 }
 static void wiring(Path root) throws Exception {
  var help=Files.readString(root.resolve("src/com/aionemu/gameserver/services/playerbot/PlayerBotHelpObjectives.java"));
  var group=Files.readString(root.resolve("src/com/aionemu/gameserver/services/playerbot/PlayerBotFindGroup.java"));
  var config=Files.readString(root.resolve("config/playerbots/features.properties"));
  int record=help.indexOf("b.handedInTogether=together(b.helper,b.requester,PlayerBotTeams.same(bot,b.helper));"),finished=help.indexOf("b.finished=true;String claim=");
  check(record>0 && finished>record,"Presence is recorded at the native hand-in, before anything can break the party up");
  check(help.contains("earned(b.helperParticipated,done?b.handedInTogether:"),"The later checkpoint reads that record for hand-ins");
  check(help.contains("if(b.handedInTogether)PlayerBotFindGroup.handedIn(bot);") && group.contains("scope.graceUntil=System.currentTimeMillis()+RUN_FAREWELL_MS;\n }".replace("\n",group.contains("\r\n")?"\r\n":"\n")),
   "An honest hand-in keeps the party for the farewell window");
  check(group.contains("System.currentTimeMillis()<scope.graceUntil"),"Control honours that window");
  check(help.contains("PlayerBotHelpKick.objectivesDone(b.requester,b.helper);"),"The kick is rolled once, at All objectives done");
  var kick=Files.readString(root.resolve("src/com/aionemu/gameserver/services/playerbot/PlayerBotHelpKick.java"));
  var service=Files.readString(root.resolve("src/com/aionemu/gameserver/services/playerbot/PlayerBotService.java"));
  check(help.contains("ThreadPoolManager.getInstance().schedule(()->finishInPlace(b),FINISH_DELAY_MS);"),"The in-place finish is scheduled at All objectives done");
  check(kick.contains("PlayerBotFindGroup.ninja(bot,human,3_000+random.nextInt(5_000),"),"A scoundrel acts 3-8 s after, always before the 10 s finish");
  check(help.contains("boolean done=turnedIn || b.remoteDone;") && help.contains("earned(b.helperParticipated,done?b.handedInTogether:"),"The checkpoint pays an in-place finish like a hand-in");
  // Owner play-test (point 3): a done request survived a relog and could never finish without the bot.
  check(help.contains("commitFinished(b);") && help.contains("var saved=store(con,p);con.commit();committed(p,saved);") && !service.contains("checkpointSoon"),
   "The finish commits from the helper's side: no bot online, no bot checkpoint needed");
  check(help.contains("c.status=earned(b.helperParticipated,b.handedInTogether)?\"COMPLETE\":\"RESOLVED\";"),"The helper-side finish still pays only real help");
  check(help.contains("else if(b.quest>0 && objectivesDone(b.committed.objectives)){") && help.contains("schedule(()->finishInPlace(restored,true),LOGIN_FINISH_MS);"),
   "A login with the objectives already done finishes the request");
  check(help.contains("{b.doneTogether=b.active();") && help.contains("atLogin || b.doneTogether || b.active()"),"In the party when the last objective filled, or now: either counts");
  check(help.contains(".filter(PlayerBotHelpObjectives::listed).map(PlayerBotHelpObjectives::shownCounter)") && !help.contains("finishes when the companion turns in its quest"),
   "The Help requests window shows no turn-in counter and states the new rule");
  check(help.contains("PlayerBotGrowth.learnHelp(member,claim);"),"Companions still earn their Learning XP, keyed like the real hand-in so it never pays twice");
  check(group.contains("|| !PlayerBotHelpObjectives.awaitingHandIn(bot,human))return;"),"It never fires after the hand-in or for another helper");
  check(group.contains("if(group.isLeader(bot))PlayerGroupService.banPlayer(human,bot);") && group.contains("else PlayerGroupService.removePlayer(bot);"),
   "A leader kicks natively; otherwise the bot leaves alone");
  check(group.contains("if (PlayerBotHelpKick.kicked(post.bot.getObjectId(),player.getObjectId())) { say(post,player,\"Nah, I'm good.")
   && group.contains("if(PlayerBotHelpKick.kicked(inviter.getObjectId(),invited.getObjectId())){PlayerGroupService.removePlayer(invited);return;}"),"The kicked helper cannot get back in");
  check(help.contains("if(PlayerBotHelpKick.kicked(bot.getObjectId(),human.getObjectId()))return;"),"A kicked helper is not told to rejoin for the reward");
  check(help.contains("dropped you right before the hand-in and kept the credit"),"The no-reward line says what happened");
  check(config.contains("gameserver.playerbots.social.findgroup.help_kick_scoundrel_percent = ") && config.contains("gameserver.playerbots.social.findgroup.help_kick_percent = "),"Both chances are settings");
 }
 private PlayerBotHelpKickCheck(){}
}
