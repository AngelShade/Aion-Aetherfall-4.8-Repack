package com.aionemu.gameserver.services.playerbot;

import java.io.IOException;
import java.util.*;
import com.aionemu.gameserver.model.gameobjects.player.Player;

/** Stage generated provenance/traits before the player row exists; join its first native inventory checkpoint. */
public final class PlayerBotCreationMetadata {
 private static final Map<Integer,Map<String,Map<String,String>>> PENDING=new HashMap<>();
 static synchronized Map<String,String> preview(int character,String section) {
  var sections=PENDING.get(character);return sections==null?null:sections.get(section);
 }
 static synchronized void discard(int character) {PENDING.remove(character);}
 static synchronized void storeCreation(java.sql.Connection c,Player player)throws java.sql.SQLException {
  var sections=PENDING.get(player.getObjectId());if(sections==null || !sections.containsKey("growth"))throw new java.sql.SQLException("Paid creation traits missing");
  for(var section:sections.entrySet())com.aionemu.gameserver.dao.PlayerBotMetadataDAO.write(c,player.getAccount().getId(),player.getObjectId(),section.getKey(),0,section.getValue());
 }
 static void queue(Player player,Properties properties)throws IOException {queue(player,"gear",properties);}
 static synchronized void queue(Player player,String section,Properties properties)throws IOException {
  if(!Set.of("gear","growth").contains(section))throw new IOException("Unsupported generated creation metadata section");
  var map=PlayerBotMetadata.values(properties);PlayerBotMetadata.validate(player.getAccount().getId(),player.getObjectId(),section,map);
  var sections=PENDING.computeIfAbsent(player.getObjectId(),id->new TreeMap<>());
  var previous=sections.putIfAbsent(section,map);
  if(previous!=null && !previous.equals(map))throw new IOException("Generated creation "+section+" metadata already pending");
 }
 static synchronized void prepare(Player player)throws IOException {
  var pending=PENDING.get(player.getObjectId());if(pending==null)return;
  int account=player.getAccount().getId(),character=player.getObjectId();
  // Check every section before handing any to the mutable checkpoint cache. An identical
  // staged section is accepted on retry if an earlier handoff failed part way through.
  for(var section:pending.entrySet()) {
   PlayerBotMetadata.validate(account,character,section.getKey(),section.getValue());
   var legacy=section.getKey().equals("gear")?java.nio.file.Path.of("config/playerbots/gear-character-"+character+".properties"):null;
   var properties=PlayerBotMetadata.load(account,character,section.getKey(),legacy);
   if(!properties.isEmpty() && !PlayerBotMetadata.values(properties).equals(section.getValue()))
    throw new IOException("New generated bot unexpectedly has different "+section.getKey()+" metadata; pending provenance retained");
  }
  for(var section:pending.entrySet())PlayerBotMetadata.save(account,character,section.getKey(),PlayerBotMetadata.properties(section.getValue()));
  PENDING.remove(character); // Native metadata cache retains dirty snapshots through transaction rollback/retry.
 }
 private PlayerBotCreationMetadata(){}
}
