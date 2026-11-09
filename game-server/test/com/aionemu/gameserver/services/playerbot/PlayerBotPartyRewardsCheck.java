package com.aionemu.gameserver.services.playerbot;

import java.nio.file.Files;
import java.nio.file.Path;

import com.aionemu.gameserver.model.team.common.service.PlayerTeamDistributionService;

/** Owner 8 Nov ("i gain no DP anymore"): helper bots 10+ levels above a human zeroed the human's party XP and DP
 * through the native 10-level rule. Humans are measured against the highest human member; bots keep the native rule. */
public final class PlayerBotPartyRewardsCheck {
 static int checks;
 static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}

 public static void main(String[] args) throws Exception {
  var root=args.length>0?Path.of(args[0]):Path.of(".");
  rules();
  wiring(root);
  System.out.println("OK: "+checks+" party reward checks; no World/SQL/tasks");
 }
 static void rules() {
  check(!PlayerTeamDistributionService.tooLow(false,50,65,50),"A human with a Lv65 helper bot keeps its rewards");
  check(PlayerTeamDistributionService.tooLow(true,50,65,50),"A bot keeps the native rule against the whole party");
  check(PlayerTeamDistributionService.tooLow(false,40,65,50),"A human 10 levels below the highest human still gets nothing");
  check(!PlayerTeamDistributionService.tooLow(false,41,65,50),"Nine levels below the highest human is rewarded");
  check(!PlayerTeamDistributionService.tooLow(false,55,55,55),"The highest member is rewarded");
  check(PlayerTeamDistributionService.tooLow(true,40,50,0),"A bots-only party keeps the native rule");
 }
 static void wiring(Path root) throws Exception {
  var s=Files.readString(root.resolve("src/com/aionemu/gameserver/model/team/common/service/PlayerTeamDistributionService.java"));
  check(s.contains("calculateExperienceReward(filteredStats.rewardLevel(), owner)"),"The XP base is the highest human level when a human is in the party");
  check(s.contains("if (tooLow(member.isPlayerBot(), member.getLevel(), filteredStats.highestLevel, filteredStats.highestHumanLevel)) {"),"XP and DP use the human-aware rule");
  check(!s.contains("filteredStats.highestLevel - member.getLevel() >= 10"),"The bot-blind rule is gone");
  check(s.contains("if (!member.isPlayerBot() && member.getLevel() > highestHumanLevel)"),"Only humans set the human level");
  check(s.contains("return highestHumanLevel > 0 ? highestHumanLevel : highestLevel;"),"A bots-only party keeps the native XP base");
  check(s.contains("registerDrop(owner, mostDamagePlayer, filteredStats.highestLevel, filteredStats.players)"),"Drops keep the native highest level");
 }
 private PlayerBotPartyRewardsCheck(){}
}
