package com.aionemu.gameserver.services.playerbot;

import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.security.MessageDigest;
import com.aionemu.commons.database.DatabaseFactory;
import com.aionemu.commons.configs.DatabaseConfig;
import com.aionemu.commons.configuration.ConfigurableProcessor;
import com.aionemu.commons.utils.PropertiesUtils;

/** Offline native data import only. No GameServer/World/ID initialization or character writes. */
public final class PlayerBotPopulationMigration {
 public static void main(String[] args) {
  try{run(args);}catch(Exception e){System.err.println("Population migration refused: "+e.getClass().getSimpleName()+"; retain files and keep components stopped; no credentials printed");System.exit(1);}System.exit(0);
 }
 static void run(String[] args) throws Exception {
  if(args.length!=4 || !Set.of("verify","apply","retire").contains(args[0]))throw new IllegalArgumentException("mode server schema report required");
  String mode=args[0];Path server=Path.of(args[1]),schema=Path.of(args[2]),report=Path.of(args[3]),directory=server.resolve("config/playerbots");
  var snapshot=PlayerBotPopulationLegacy.snapshot(directory);var lines=new ArrayList<String>();
  for(String name:PlayerBotPopulationLegacy.FILES){Path file=directory.resolve(name);lines.add(name+"|"+(Files.exists(file)?hash(Files.readAllBytes(file)):"absent"));}
  String digest=hash(String.join("\n",lines).getBytes(java.nio.charset.StandardCharsets.UTF_8));
  ((ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).setLevel(ch.qos.logback.classic.Level.OFF);
  var config=PropertiesUtils.load(server.resolve("config/network/database.properties").toString());config.putAll(PropertiesUtils.load(server.resolve("config/mygs.properties").toString()));
  ConfigurableProcessor.process(config,DatabaseConfig.class);DatabaseConfig.DATABASE_CONNECTIONS_MAX=3;DatabaseFactory.init();
  try(var c=DatabaseFactory.getConnection()) {
   if(mode.equals("verify")) {
    c.setReadOnly(true);PlayerBotPopulationRepository.validate(c,snapshot);
   }else if(mode.equals("apply")) {
    try(var s=c.createStatement()){for(String sql:Files.readString(schema).replaceAll("(?m)^--.*$","").split(";"))if(!sql.isBlank())s.execute(sql);}
    c.setAutoCommit(false);
    try {
     PlayerBotPopulationRepository.importSnapshot(c,snapshot,digest);
     Files.writeString(Path.of(report+".commit-attempted"),"SQL commit attempted; verify on a separate connection before file retirement\n");c.commit();
    }catch(Exception e){try{c.rollback();}catch(SQLException rollback){e.addSuppressed(rollback);}throw e;}
   }else {
    c.setReadOnly(true);c.setAutoCommit(false);c.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
    PlayerBotPopulationRepository.revision(c,false);var stored=PlayerBotPopulationRepository.read(c);PlayerBotPopulationRepository.validate(c,stored);
    if(!PlayerBotPopulationRepository.same(snapshot,stored))throw new SQLException("Independent database read differs from legacy snapshot");c.commit();
   }
  }
  Files.write(report,lines);
  System.out.println("OK: population "+mode+"; residents="+snapshot.residents().size()+"; accounts="+(snapshot.server().size()+snapshot.manual().size())+"; members="+java.util.stream.Stream.concat(snapshot.server().values().stream(),snapshot.manual().values().stream()).mapToInt(p->p.characters().size()).sum()+"; events="+snapshot.schedule().events().size()+"; target="+snapshot.schedule().target()+"; no character/progress/ID mutation");
 }
 static String hash(byte[] bytes) throws Exception {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
}
