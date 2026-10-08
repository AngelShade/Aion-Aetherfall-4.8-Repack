package com.aionemu.gameserver.services.playerbot;

import java.nio.file.*;
import java.util.*;
import com.aionemu.commons.database.DatabaseFactory;
import com.aionemu.commons.configs.DatabaseConfig;
import com.aionemu.commons.configuration.ConfigurableProcessor;
import com.aionemu.commons.utils.PropertiesUtils;
import com.aionemu.gameserver.dao.PlayerBotRepositoryDAO;

/** Read-only postinstall use of production loaders; never starts GameServer or creates actors. */
public final class PlayerBotRepositoryReadCheck {
 public static void main(String[] args){try{run(args);}catch(Exception e){System.err.println("Native repository read verification failed: "+e.getClass().getSimpleName()+"; no credentials printed");System.exit(1);}System.exit(0);}
 static void run(String[] args)throws Exception {
  Path server=Path.of(args[0]),report=Path.of(args[1]);
  ((ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).setLevel(ch.qos.logback.classic.Level.OFF);
  var config=PropertiesUtils.load(server.resolve("config/network/database.properties").toString());config.putAll(PropertiesUtils.load(server.resolve("config/mygs.properties").toString()));ConfigurableProcessor.process(config,DatabaseConfig.class);DatabaseConfig.DATABASE_CONNECTIONS_MAX=3;DatabaseFactory.init();
  var lines=new ArrayList<String>();
  try(var c=DatabaseFactory.getConnection()){
   c.setReadOnly(true);var accounts=new ArrayList<Integer>();
   try(var s=c.prepareStatement("SELECT account_id FROM playerbot_saved_parties ORDER BY account_id");var r=s.executeQuery()){while(r.next())accounts.add(r.getInt(1));}
   for(int account:accounts){var document=PlayerBotRepository.load(account);lines.add("account="+account+" savedVisible="+document.saved().size()+" presets="+document.presets().size());for(int id:document.saved())if(PlayerBotRepository.removed(account,id))throw new AssertionError("Removed bot exposed in bookmarks");for(var p:document.presets())for(var m:p.members())if(m.temporary()&&PlayerBotRepository.removed(account,m.id()))throw new AssertionError("Removed bot exposed in preset");}
   int removed=0;try(var s=c.prepareStatement("SELECT account_id,player_id FROM playerbot_removed ORDER BY player_id");var r=s.executeQuery()){while(r.next()){if(!PlayerBotRepository.removed(r.getInt(1),r.getInt(2)))throw new AssertionError("Archive marker lost");PlayerBotRepositoryDAO.roster(c,r.getInt(1),r.getInt(2));removed++;}}
   lines.add("OK: "+accounts.size()+" account preset loaders; "+removed+" removed bots remain native-roster quarantined; read-only production loaders; no actors/IDs");
  }Files.write(report,lines);System.out.println(lines.getLast());
 }
}
