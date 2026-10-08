package com.aionemu.gameserver.services.playerbot;

import java.io.IOException;
import java.nio.file.*;
import java.util.Properties;
import com.aionemu.commons.database.DatabaseFactory;
import com.aionemu.gameserver.dao.PlayerBotMetadataDAO;

/** Human-owner formation preferences have no companion checkpoint: commit explicit commands through the native DAO. */
final class PlayerBotMetadataConfiguration {
 static Properties load(int account,int character,String section,Path legacy)throws IOException {
  try(var connection=DatabaseFactory.getConnection()) {
   var row=PlayerBotMetadataDAO.load(connection,account,character,section);
   Properties p=new Properties();
   if(row!=null)p.putAll(row.values());
   else if(Files.exists(legacy))try(var input=Files.newInputStream(legacy)){p.load(input);}
   if(!p.isEmpty())PlayerBotMetadata.validate(account,character,section,PlayerBotMetadata.values(p));
   return p;
  }catch(java.sql.SQLException e){throw new IOException("Cannot load owner formation metadata",e);}
 }
 static void save(int account,int character,String section,Properties p)throws IOException {
  var values=PlayerBotMetadata.values(p);PlayerBotMetadata.validate(account,character,section,values);
  try(var connection=DatabaseFactory.getConnection()) {
   connection.setAutoCommit(false);
   try {
    var previous=PlayerBotMetadataDAO.load(connection,account,character,section);
    if(previous==null || !previous.values().equals(values))
     PlayerBotMetadataDAO.write(connection,account,character,section,previous==null?0:previous.revision(),values);
    connection.commit();
   }catch(java.sql.SQLException | RuntimeException e){try{connection.rollback();}catch(java.sql.SQLException rollback){e.addSuppressed(rollback);}throw e;}
  }catch(java.sql.SQLException e){throw new IOException("Cannot commit owner formation metadata",e);}
 }
 private PlayerBotMetadataConfiguration() {}
}
