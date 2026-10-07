import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.regex.*;
import com.aionemu.commons.database.DatabaseFactory;
import com.aionemu.commons.configs.DatabaseConfig;
import com.aionemu.commons.configuration.ConfigurableProcessor;
import com.aionemu.commons.utils.PropertiesUtils;
import com.aionemu.gameserver.dao.PlayerBotMetadataDAO;
import com.aionemu.gameserver.services.playerbot.PlayerBotMetadata;

/** Offline schema/import tool. No GameServer initialization, actors, IDs, or secret output. */
public final class PlayerBotMetadataMigration {
 record Legacy(Path path,int account,int character,String section,Properties values) {}
 static List<Legacy> read(Path server)throws Exception {
  var rows=new ArrayList<Legacy>();var pattern=Pattern.compile("(care|gear)-character-([0-9]+)\\.properties");
  try(var paths=Files.list(server.resolve("config/playerbots"))) {
   for(var path:paths.sorted().toList()) {
    var match=pattern.matcher(path.getFileName().toString());if(!match.matches())continue;
    Properties p=new Properties();try(var in=Files.newInputStream(path)){p.load(in);}
    int account=Integer.parseInt(p.getProperty("account")),character=Integer.parseInt(match.group(2));
    var values=new HashMap<String,String>();for(String key:p.stringPropertyNames())values.put(key,p.getProperty(key));
    PlayerBotMetadata.validate(account,character,match.group(1),values);rows.add(new Legacy(path,account,character,match.group(1),p));
   }
  }
  return rows;
 }
 public static void main(String[] args) {
  try{run(args);}catch(Exception e){System.err.println("Metadata migration failed: "+e.getClass().getSimpleName()+". No credentials printed; keep server stopped and inspect the database/configuration.");System.exit(1);}
  System.exit(0);
 }
 static void run(String[] args)throws Exception {
  if(args.length!=4 || !Set.of("files","verify","apply").contains(args[0]))throw new IllegalArgumentException("mode,server,schema,report required");
  boolean apply=args[0].equals("apply");Path server=Path.of(args[1]).toAbsolutePath(),schema=Path.of(args[2]),report=Path.of(args[3]);
  var legacy=read(server);
  if(args[0].equals("files")){Files.writeString(report,"OK: "+legacy.size()+" legacy care/gear files parsed and validated; no database connection or file replacement\n");System.out.println("OK: "+legacy.size()+" legacy files validated offline; no database connection");return;}
  // Do not let the pool's diagnostic configuration print connection credentials.
  ((ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).setLevel(ch.qos.logback.classic.Level.OFF);
  Properties config=PropertiesUtils.load(server.resolve("config/network/database.properties").toString());
  Properties overrides=PropertiesUtils.load(server.resolve("config/mygs.properties").toString());config.putAll(overrides);
  ConfigurableProcessor.process(config,DatabaseConfig.class);DatabaseConfig.DATABASE_CONNECTIONS_MAX=1;DatabaseFactory.init();
  List<String> lines=new ArrayList<>();lines.add("Mode="+args[0]+" legacyFiles="+legacy.size());
  try(var c=DatabaseFactory.getConnection()) {
   // Check every file's character/account before DDL/import. No invented IDs or accounts.
   for(var row:legacy)PlayerBotMetadataDAO.owner(c,row.account,row.character);
   if(apply){try(var statement=c.createStatement()){statement.execute(Files.readString(schema).replaceAll("(?m)^--.*$",""));}}
   else {lines.add("OK: database reachable; legacy file identities verified; no schema/data write");Files.write(report,lines);return;}
   c.setAutoCommit(false);int imported=0,existing=0;
   try {
    for(var row:legacy) {
     if(PlayerBotMetadata.importLegacy(c,row.account,row.character,row.section,row.values))imported++;else existing++;
     var stored=PlayerBotMetadataDAO.load(c,row.account,row.character,row.section);
     if(stored==null)throw new SQLException("Migration missing metadata row");
     PlayerBotMetadata.validate(row.account,row.character,row.section,stored.values());
     lines.add("character="+row.character+" section="+row.section+" revision="+stored.revision());
    }
    Files.writeString(Path.of(report.toString()+".commit-attempted"),"Commit boundary reached; do not assume rollback on lost acknowledgement");
    c.commit();
   }catch(Exception e){c.rollback();throw e;}
   lines.add("OK: imported="+imported+" existingDatabaseRowsPreserved="+existing+"; legacy files retained; one import transaction committed");
   Files.write(report,lines);
  }
  System.out.println("OK: metadata "+(apply?"migration committed":"preflight verified")+"; private character metadata only; no server/client startup");
 }
}
