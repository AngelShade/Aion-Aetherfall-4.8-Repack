package com.aionemu.gameserver.services.playerbot;

import com.aionemu.gameserver.configs.main.PlayerBotConfig;
import com.aionemu.gameserver.services.playerbot.PlayerBotGrowth.CombatRank;

/** Shared instance challenge: native scaled demand against the party that actually fought. Every number is tunable in config/playerbots/features.properties. */
public final class PlayerBotChallenge {
 private static double clamp(double value,double min,double max) { return Math.max(min,Math.min(max,value)); }
 public static double levelFactor(int member,int boss) {
  return clamp(1+PlayerBotConfig.TICKET_LEVEL_STEP*(member-boss),PlayerBotConfig.TICKET_LEVEL_MIN,PlayerBotConfig.TICKET_LEVEL_MAX);
 }
 public static double gearFactor(double score,double expected) {
  return expected<=0?1:clamp(score/expected,PlayerBotConfig.TICKET_GEAR_MIN,PlayerBotConfig.TICKET_GEAR_MAX);
 }
 public static double rankFactor(CombatRank rank) { return rank==null?1:1-rank.mistakePercent()/100d; }
 public static double challenge(double partyStrength,int maxPlayers,double demand) {
  return partyStrength<=0?0:demand/(partyStrength/Math.max(1,maxPlayers));
 }
 public static int tickets(double challenge) {
  return challenge>=PlayerBotConfig.TICKET_CHALLENGE_THREE?3:challenge>=PlayerBotConfig.TICKET_CHALLENGE_TWO?2:challenge>=PlayerBotConfig.TICKET_CHALLENGE_ONE?1:0;
 }
 /** Native non-trivial hunt admission, independent of the character level cap and XP boosts. Scaled stats remain an explicit input. */
 public static boolean practice(int highestLevel,int npcLevel,double challenge,double demand) {
  return challenge>=PlayerBotConfig.TICKET_CHALLENGE_ONE && (demand>1 || com.aionemu.gameserver.utils.stats.XPRewardEnum.xpRewardFrom(npcLevel-highestLevel)>0);
 }
 private PlayerBotChallenge() {}
}
