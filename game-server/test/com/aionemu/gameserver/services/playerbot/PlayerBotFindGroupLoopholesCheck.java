package com.aionemu.gameserver.services.playerbot;

import java.nio.file.Files;
import java.nio.file.Path;

/** Owner 8 Nov: once the lead passed to the human and a fight began, no more bots came to the Find Group party.
 * A human-led party keeps recruiting through fights, the poster's death and a companion leader; accepting travel
 * mid-fight keeps the human's seat. No World/SQL. */
public final class PlayerBotFindGroupLoopholesCheck {
 static int checks;
 static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}

 public static void main(String[] args) throws Exception {
  var root=args.length>0?Path.of(args[0]):Path.of(".");
  rules();
  wiring(root);
  System.out.println("OK: "+checks+" Find Group loophole checks; no World/SQL/tasks");
 }
 static void rules() {
  check(PlayerBotFindGroup.inviterReady(true,false),"A living party leader admits recruits, fighting or not");
  check(!PlayerBotFindGroup.inviterReady(true,true),"A dead leader cannot invite natively");
  check(!PlayerBotFindGroup.inviterReady(false,false),"A leader in a loading screen waits");
  check(PlayerBotFindGroup.heldThrough(true,true,true),"A dead poster in its human's party keeps the listing");
  check(!PlayerBotFindGroup.heldThrough(false,true,true),"A retired poster ends it");
  check(!PlayerBotFindGroup.heldThrough(true,false,true),"The human going offline ends it");
  check(!PlayerBotFindGroup.heldThrough(true,true,false),"The human leaving ends it");
  // Relog (owner 8 Nov, "Yes, fix relog").
  check(PlayerBotFindGroup.waits(true,false,false,true,false),"A field party waits for its disconnected leader");
  check(!PlayerBotFindGroup.waits(false,false,false,true,false),"Dungeon runs keep their own ending");
  check(!PlayerBotFindGroup.waits(true,true,false,true,false),"A finished request does not wait");
  check(!PlayerBotFindGroup.waits(true,false,true,true,false),"An online leader is not waited for");
  check(!PlayerBotFindGroup.waits(true,false,false,false,false),"A human who left the party is not waited for");
  check(!PlayerBotFindGroup.waits(true,false,false,true,true),"Another human in the party takes over instead");
  long w=PlayerBotFindGroup.RELOG_WAIT_MS;
  check(PlayerBotFindGroup.relogWaitOver(1000,1000+w+1) && !PlayerBotFindGroup.relogWaitOver(1000,1000+w) && !PlayerBotFindGroup.relogWaitOver(0,Long.MAX_VALUE),"The wait ends after its time, never unset");
  check(w==300_000,"The party waits five minutes");
  check(!PlayerBotFindGroup.keeps(false,false,true,true),"Help done with the human online: the bots leave the party (no ghost party)");
  check(PlayerBotFindGroup.keeps(false,true,true,false),"A relogging human keeps even a single bot");
  check(!PlayerBotFindGroup.keeps(true,true,true,true),"Another online human takes over instead");
  check(PlayerBotFindGroup.keeps(false,false,false,true),"The human left: the bots stay together and carry on");
  check(!PlayerBotFindGroup.keeps(false,false,false,false),"A lone bot with nobody to wait for leaves");
  check(PlayerBotFindGroup.holdsRelog(true,true,false,true,1000,1000+w),"A dungeon run holds for its relogging human");
  check(!PlayerBotFindGroup.holdsRelog(false,true,false,true,1000,1000),"Field parties use the party wait instead");
  check(!PlayerBotFindGroup.holdsRelog(true,false,false,true,1000,1000),"Bots outside the instance do not hold");
  check(!PlayerBotFindGroup.holdsRelog(true,true,true,true,1000,1000),"A finished run does not hold");
  check(!PlayerBotFindGroup.holdsRelog(true,true,false,false,1000,1000),"A human who left the party is not held for");
  check(!PlayerBotFindGroup.holdsRelog(true,true,false,true,1000,1000+w+1),"The hold ends after five minutes");
 }
 static void wiring(Path root) throws Exception {
  var s=Files.readString(root.resolve("src/com/aionemu/gameserver/services/playerbot/PlayerBotFindGroup.java")).replace("\r\n","\n");
  check(s.contains("|| !(free(leader) || forming(post)) || !posterReady(post,leader) || !socialIdle(member)"),"Recruits check the poster through posterReady");
  check(!s.contains("|| !socialIdle(leader) ||"),"The poster's fight no longer blocks a human-led party");
  check(s.contains("if(post.controller==null)return socialIdle(leader);"),"An autonomous poster still forms its party between fights");
  check(s.contains("&& leadsFor(team.getLeaderObject(),post));"),"forming accepts the leaders of the post");
  check(s.contains("return actor!=null && actor.owner()==post.controller;"),"A companion the human gave the lead keeps recruiting");
  check(s.contains("!resident(post.bot) && !runUnderway(post) && !heldByHuman(post)) { retire(post); return; }"),"The poster's death no longer retires a human-led listing");
  check(s.contains("if(responder.isDead() || responder.getController().isInCombat()){busy(post,responder,serial);return;}"),"Accepting travel mid-fight keeps the seat");
  check(!s.contains("isInCombat()){PlayerGroupService.removePlayer(responder);return;}"),"The mid-fight removal is gone");
  check(s.contains("post.tpOffers=Math.max(0,post.tpOffers-1);"),"A mid-fight accept is not a refusal");
  check(s.contains("if (post.tpOffers<2) {\n   grace(post,human,serial,"),"Refusals keep their two-offer limit");
  int grace=s.indexOf("private static void grace(");
  check(grace>0 && s.indexOf("PlayerGroupService.removePlayer(human);\n  }),120,TimeUnit.SECONDS);",grace)>grace,"The grace window still ends after 2 minutes");
  check(s.contains("if(responder.isDead() || responder.getController().isInCombat()){whisper(meeting.bot,responder,"),"The entrance meeting already waits through fights");
  var service=Files.readString(root.resolve("src/com/aionemu/gameserver/services/playerbot/PlayerBotService.java"));
  int back=service.indexOf("var back=PlayerBotFindGroup.relogged(session.owner(),session.bot());");
  int hold=service.indexOf("if(PlayerBotFindGroup.holdsForRelog(session.owner(),session.bot(),admission))session.holdForRelog();");
  check(back>0 && hold>back && service.indexOf("else session.endWorldControl();",hold)>hold,"A quick relog hands the bots over before a dungeon hold or the end of control");
  check(Files.readString(root.resolve("src/com/aionemu/gameserver/services/playerbot/PlayerBotHiring.java")).contains(
   "!PlayerBotFindGroup.freeHelpControl(session.owner(),session.bot()) && !PlayerBotFindGroup.holdsForRelog(session.owner(),session.bot(),now);"),"A held run is not released, at logout or later");
  var session=Files.readString(root.resolve("src/com/aionemu/gameserver/services/playerbot/PlayerBotSession.java"));
  check(session.contains("if(!endingWorldControl && PlayerBotWorldActivity.allowed(this)"),"A holding bot stays awake and fights back");
  check(session.contains("if (!autonomous() && !detached() && !owner.isOnline()) return false;") && session.split("if \\(detached\\(\\)\\) return false;",-1).length==3,"A holding bot neither leaves control nor follows the offline owner");
  check(s.contains("if(!human.isOnline() && memberOf(bot,human.getObjectId())) {"),"The entrance wait survives a relog");
  check(s.contains("if(MEETINGS.replace(bot.getObjectId(),meeting,moved))"),"And moves to the new login");
  var enter=Files.readString(root.resolve("src/com/aionemu/gameserver/services/player/PlayerEnterWorldService.java"));
  int login=enter.indexOf("PlayerGroupService.onPlayerLogin(player);"),hook=enter.indexOf("PlayerBotFindGroup.reconnected(player);");
  check(login>0 && hook>login,"The relog hook runs after the native group reconnect");
  check(s.contains("!scope.human.isOnline() && members.stream().anyMatch(m->m.getObjectId()==human),"),"Only an offline helper is waited for");
  check(s.contains("post.away=previous.getObjectId();post.awaySince=System.currentTimeMillis();post.controller=null;withdraw(post);"),"The post is kept, reserved and unlisted");
  check(s.contains("if(relogWaitOver(post.awaySince,System.currentTimeMillis()) && leaveParty(post)){post.away=0;post.awaySince=0;retire(post);}"),"After the wait the bots leave");
  check(s.contains("if(!post.controller.isOnline() && member(post,post.controller.getObjectId()))return;"),"A disconnected leader's post is not dropped");
  check(s.contains("welcomeBack(post,human,false); // adopt gives the lead back\n  offerTeleport(post,human);"),"The returning human gets the bots back like a fresh application");
  check(s.contains("noneMatch(m->!m.isPlayerBot() && m.isOnline());"),"An offline member does not block reopening");
  check(s.contains("|| session!=null && session.world() && post.awaySince>0;"),"A waiting party survives its poster's death");
 }
 private PlayerBotFindGroupLoopholesCheck(){}
}
