import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.Pattern;
import com.aionemu.commons.database.DatabaseFactory;
import com.aionemu.commons.configs.DatabaseConfig;
import com.aionemu.commons.configuration.ConfigurableProcessor;
import com.aionemu.commons.utils.PropertiesUtils;
import com.aionemu.gameserver.dao.PlayerBotMetadataDAO;
import com.aionemu.gameserver.services.playerbot.PlayerBotMetadata;

/** Read-only legacy retirement preflight. Missing native metadata refuses retirement. */
public final class PlayerBotMetadataRetirement {
 public static void main(String[] args) {
  try {
   if(args.length!=2)throw new IllegalArgumentException("server,report required");
   Path server=Path.of(args[0]),directory=server.resolve("config/playerbots");
   ((ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).setLevel(ch.qos.logback.classic.Level.OFF);
   var config=PropertiesUtils.load(server.resolve("config/network/database.properties").toString());
   config.putAll(PropertiesUtils.load(server.resolve("config/mygs.properties").toString()));
   ConfigurableProcessor.process(config,DatabaseConfig.class);DatabaseConfig.DATABASE_CONNECTIONS_MAX=1;DatabaseFactory.init();
   var pattern=Pattern.compile("(?:(care|gear|behavior|formation|spacing|party-rewards)-)?character-([0-9]+)\\.properties");
   var lines=new ArrayList<String>();int newer=0;
   try(var c=DatabaseFactory.getConnection();var files=Files.list(directory)) {
    c.setReadOnly(true);
    for(Path file:files.sorted().toList()) {
     var match=pattern.matcher(file.getFileName().toString());if(!match.matches())continue;
     byte[] bytes=Files.readAllBytes(file);var properties=new Properties();
     try(var input=new java.io.ByteArrayInputStream(bytes)){properties.load(input);}
     int account=Integer.parseInt(properties.getProperty("account")),character=Integer.parseInt(match.group(2));
     String section=match.group(1)==null?"preferences":match.group(1);
     var legacy=new TreeMap<String,String>();for(String key:properties.stringPropertyNames())legacy.put(key,properties.getProperty(key));
     PlayerBotMetadata.validate(account,character,section,legacy);
     var stored=PlayerBotMetadataDAO.load(c,account,character,section);
     if(stored==null || stored.revision()<1)throw new IllegalStateException("Legacy metadata not migrated; retirement refused");
     PlayerBotMetadata.validate(account,character,section,stored.values());
     boolean equal=stored.values().equals(legacy);if(!equal)newer++;
     // Existing database rows are authoritative. Archive stale legacy input; never overwrite the DB.
     lines.add(file.getFileName()+"|"+HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))+"|"+stored.revision()+"|"+equal);
    }
   }
   Files.write(Path.of(args[1]),lines);
   System.out.println("OK: "+lines.size()+" legacy files have valid native owned metadata; "+newer+" differ from authoritative database snapshots; read-only preflight");
  }catch(Exception e){System.err.println("Metadata retirement refused: "+e.getClass().getSimpleName()+"; no credentials printed");System.exit(1);}
  System.exit(0);
 }
}
