package com.aionemu.gameserver.services.playerbot;

/** Compatibility entrypoint now tests the native DAO-backed gear/care cache. */
public final class PlayerBotGearSettingsCheck {
 public static void main(String[] args)throws Exception {
  if(args.length!=0)throw new IllegalArgumentException("Gear replacement fixtures are obsolete; runtime metadata now uses the database. Use PlayerBotMetadataCheck.");
  PlayerBotMetadataCheck.main(args);
 }
}
