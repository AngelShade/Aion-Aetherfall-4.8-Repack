package com.aionemu.gameserver.services.playerbot;

import java.io.IOException;
import java.util.*;
import com.aionemu.gameserver.model.gameobjects.player.Player;

/** Stage protected generated IDs before the native player row exists, then join its first inventory checkpoint. */
public final class PlayerBotCreationMetadata {
 private static final Map<Integer,Map<String,String>> PENDING=new HashMap<>();
 static synchronized void queue(Player player,Properties properties)throws IOException {
  var map=PlayerBotMetadata.values(properties);PlayerBotMetadata.validate(player.getAccount().getId(),player.getObjectId(),"gear",map);
  if(PENDING.putIfAbsent(player.getObjectId(),map)!=null)throw new IOException("Generated creation provenance already pending");
 }
 static synchronized void prepare(Player player)throws IOException {
  var pending=PENDING.get(player.getObjectId());if(pending==null)return;
  int account=player.getAccount().getId(),character=player.getObjectId();
  var properties=PlayerBotMetadata.load(account,character,"gear",java.nio.file.Path.of("config/playerbots/gear-character-"+character+".properties"));
  if(!properties.isEmpty())throw new IOException("New Temporary Bot unexpectedly has existing gear metadata; provenance retained");
  PlayerBotMetadata.save(account,character,"gear",PlayerBotMetadata.properties(pending));
  PENDING.remove(character); // Native metadata cache owns the pending snapshot until its transaction commits.
 }
 private PlayerBotCreationMetadata(){}
}
