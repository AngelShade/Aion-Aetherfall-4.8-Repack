package com.aionemu.gameserver.services.playerbot;

import com.aionemu.gameserver.configs.main.PlayerBotConfig;

/** Saved Temporary Bot capacity is independent of native active party slots. */
public final class PlayerBotCreationPolicy {
 private PlayerBotCreationPolicy() {}
 public static int limit() { return Math.max(0, PlayerBotConfig.GENERATED_MAX_PER_ACCOUNT); }
 public static boolean room(int created) { return created >= 0 && created < limit(); }
 public static boolean recruitmentAllowed(boolean generated,boolean ticketRecruit) {
  return !generated || ticketRecruit || PlayerBotConfig.GENERATED_ENABLED;
 }
 public static void requireRecruitment(boolean generated,boolean ticketRecruit) {
  if(!recruitmentAllowed(generated,ticketRecruit))
   throw new IllegalArgumentException("Temporary Bot recruitment is disabled by this server. Ticket Recruits and account characters remain available.");
 }
}
