package com.aionemu.gameserver.services.playerbot;

import com.aionemu.gameserver.configs.main.PlayerBotConfig;

/** Saved Temporary Bot capacity is independent of native active party slots. */
public final class PlayerBotCreationPolicy {
 private PlayerBotCreationPolicy() {}
 public static int limit() { return Math.max(0, PlayerBotConfig.GENERATED_MAX_PER_ACCOUNT); }
 public static boolean room(int created) { return created >= 0 && created < limit(); }
}
