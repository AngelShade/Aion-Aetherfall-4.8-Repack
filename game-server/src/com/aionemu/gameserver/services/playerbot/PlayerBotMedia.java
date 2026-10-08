package com.aionemu.gameserver.services.playerbot;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/** Packaged native companion interface; no mutable settings directory required. */
public final class PlayerBotMedia {
 public static String read(String name)throws IOException {
  if(!Set.of("bots.html","bots.css","bots.js").contains(name))throw new IOException("Invalid companion media");
  try(var in=PlayerBotMedia.class.getResourceAsStream("/playerbots/media/"+name)){
   if(in==null)throw new IOException("Companion interface resource missing");
   return new String(in.readAllBytes(),StandardCharsets.UTF_8);
  }
 }
 private PlayerBotMedia(){}
}
