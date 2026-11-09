package com.aionemu.gameserver.services.playerbot;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import com.aionemu.gameserver.configs.main.PlayerBotConfig;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.services.playerbot.PlayerBotLeanings.Leaning;

/**
 * Some players drop you right before the hand-in and keep the credit (owner play-test 8 Nov: "players do
 * that in mmos all the time"). A lifelong scoundrel trait from a fixed per-character hash, tilted by the
 * bot's leaning (PvP-leaning twice as likely, questers half), like the saved leanings and growth traits.
 * When a scoundrel's quest objectives fill, it may pull it once, a few seconds later: a cheeky party
 * line, then the native kick if it leads, or it leaves alone and hands in solo. The helper hears why there
 * is no reward and cannot rejoin that bot for a while. Its own hook: help_kick_percent = 0 switches it off.
 */
public final class PlayerBotHelpKick {
 /** How long the kicked helper is refused by that bot (Find Group invite, apply or its party). */
 static final long MEMORY_MS=15*60_000L;
 static final String[] LINES={
  "Thanks for the help! Bye :)",
  "gg, solo turn-in time. Credit's mine, sorry!",
  "Oops, wrong button... jk. Thanks though!",
  "lol you did great. I'll take it from here.",
  "Nothing personal, I need this one for myself. Cya!"};
 private static final Map<Long,Long> KICKED=new ConcurrentHashMap<>();

 /** Stable for a character: the same id, leaning and percent always give the same answer. */
 static boolean scoundrel(int character,Leaning leaning,int percent) {
  if(percent<=0)return false;
  double tilt=leaning==Leaning.PVP?2:leaning==Leaning.QUESTER?0.5:1;
  long h=(character^0x5DEECE66DL)*0x9E3779B97F4A7C15L;h^=h>>>31;h*=0xBF58476D1CE4E5B9L;h^=h>>>29;
  return Math.floorMod(h,1000L)<Math.min(1000L,Math.round(Math.min(100,percent)*10*tilt));
 }
 /** roll is 0..99. */
 static boolean rolls(int percent,int roll){return roll<Math.max(0,Math.min(100,percent));}
 static long key(int bot,int human){return ((long)bot<<32)|(human&0xffffffffL);}
 static void remember(int bot,int human,long now){KICKED.put(key(bot,human),now+MEMORY_MS);}
 /** This bot dropped this helper within the last {@link #MEMORY_MS}. */
 public static boolean kicked(int bot,int human){return kicked(bot,human,System.currentTimeMillis());}
 static boolean kicked(int bot,int human,long now) {
  Long until=KICKED.get(key(bot,human));
  if(until==null)return false;
  if(now>=until){KICKED.remove(key(bot,human),until);return false;}
  return true;
 }
 static String line(int bot,int salt){return LINES[Math.floorMod(bot*31+salt,LINES.length)];}
 /** PlayerBotHelpObjectives calls this once, when every hunt/item counter of a quest request fills. */
 static void objectivesDone(Player bot,Player human) {
  if(bot==null || human==null || human.isPlayerBot() || !PlayerBotConfig.FIND_GROUP_ENABLED)return;
  if(!scoundrel(bot.getObjectId(),PlayerBotLeanings.of(bot),PlayerBotConfig.HELP_KICK_SCOUNDREL_PERCENT))return;
  var random=ThreadLocalRandom.current();
  if(!rolls(PlayerBotConfig.HELP_KICK_PERCENT,random.nextInt(100)))return;
  // Long enough to read "All objectives done!", always before the in-place finish 10 s after it.
  PlayerBotFindGroup.ninja(bot,human,3_000+random.nextInt(5_000),line(bot.getObjectId(),random.nextInt(LINES.length)));
 }
 private PlayerBotHelpKick(){}
}
