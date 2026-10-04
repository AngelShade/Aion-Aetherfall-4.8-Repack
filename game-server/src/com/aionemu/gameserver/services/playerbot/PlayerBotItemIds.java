package com.aionemu.gameserver.services.playerbot;

import com.aionemu.commons.database.DatabaseFactory;
import com.aionemu.gameserver.utils.idfactory.IDFactory;

/** Never reuse a persisted item/character ID even when an obsolete release cleared its bit. */
public final class PlayerBotItemIds {
 public static int nextId() {
  try (var connection=DatabaseFactory.getConnection();var query=connection.prepareStatement(
   "SELECT item_unique_id FROM inventory WHERE item_unique_id=? UNION ALL SELECT id FROM players WHERE id=? LIMIT 1")) {
   for(int attempt=0;attempt<10000;attempt++) {
    int id=IDFactory.getInstance().nextId();query.setInt(1,id);query.setInt(2,id);
    try(var row=query.executeQuery()) {if(!row.next())return id;}
    // nextId already reserves this ID. Do not release the persisted foreign row.
    org.slf4j.LoggerFactory.getLogger(PlayerBotItemIds.class).warn("Retained persisted object ID {} instead of allocating a colliding item",id);
   }
   throw new IllegalStateException("Too many stale item IDs; allocation stopped safely");
  }catch(java.sql.SQLException error){throw new IllegalStateException("Cannot verify item ID custody; allocation stopped safely",error);}
 }
 private PlayerBotItemIds() {}
}
