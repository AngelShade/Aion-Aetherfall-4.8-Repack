import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.regex.Pattern;
import com.alibaba.fastjson2.JSON;
import com.aionemu.commons.database.DatabaseFactory;
import com.aionemu.commons.configs.DatabaseConfig;
import com.aionemu.commons.configuration.ConfigurableProcessor;
import com.aionemu.commons.utils.PropertiesUtils;
import com.aionemu.gameserver.dao.PlayerBotRepositoryDAO;
import com.aionemu.gameserver.services.playerbot.PlayerBotRepository;

/** Offline owned native repository import/read-only retirement proof, no actors or ID changes. */
public final class PlayerBotRepositoryMigration {
 record Legacy(Path path,int account,int character,String text){}
 static List<Legacy> read(Path server)throws Exception {
  var rows=new ArrayList<Legacy>();
  for(String directory:List.of("saved-parties","removed")){
   var pattern=Pattern.compile(directory.equals("removed")?"character-([0-9]+)\\.json":"account-([0-9]+)\\.json");
   try(var paths=Files.list(server.resolve("config/playerbots/"+directory))){
    for(var file:paths.sorted().toList()){
     var match=pattern.matcher(file.getFileName().toString());if(!match.matches())throw new IllegalArgumentException("Unexpected repository file");
     if(Files.size(file)>(directory.equals("removed")?4096:524288))throw new IllegalArgumentException("Repository file too large");
     String text=Files.readString(file);var json=JSON.parseObject(text);int account=json.getIntValue("account");int character=directory.equals("removed")?Integer.parseInt(match.group(1)):0;
     if(character==0 && account!=Integer.parseInt(match.group(1)))throw new IllegalArgumentException("Account filename mismatch");
     rows.add(new Legacy(file,account,character,text));
    }
   }
  }return rows;
 }
 public static void main(String[] args){try{run(args);}catch(Exception e){System.err.println("Repository migration refused: "+e.getClass().getSimpleName()+"; no credentials printed; keep server/client off");System.exit(1);}System.exit(0);}
 static void run(String[] args)throws Exception {
  String mode=args[0];if(!Set.of("verify","apply","retire").contains(mode))throw new IllegalArgumentException("Invalid mode");
  Path server=Path.of(args[1]),schema=Path.of(args[2]),report=Path.of(args[3]);var legacy=read(server);
  ((ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).setLevel(ch.qos.logback.classic.Level.OFF);
  var config=PropertiesUtils.load(server.resolve("config/network/database.properties").toString());config.putAll(PropertiesUtils.load(server.resolve("config/mygs.properties").toString()));
  ConfigurableProcessor.process(config,DatabaseConfig.class);DatabaseConfig.DATABASE_CONNECTIONS_MAX=3;DatabaseFactory.init();
  var lines=new ArrayList<String>();
  try(var c=DatabaseFactory.getConnection()){
   for(var row:legacy)if(row.character==0)PlayerBotRepository.validateParties(c,row.account,row.text);else PlayerBotRepository.validateRemoved(c,row.account,row.character,row.text);
   if(mode.equals("verify")){Files.writeString(report,"OK: "+legacy.size()+" native owned repository files; read-only identity/preflight\n");return;}
   if(mode.equals("apply")){
    try(var s=c.createStatement()){for(String statement:Files.readString(schema).replaceAll("(?m)^--.*$","").split(";"))if(!statement.isBlank())s.execute(statement);}
    c.setAutoCommit(false);
   }else c.setReadOnly(true);
   int imported=0;
   try{
    for(var row:legacy){
     String stored=row.character==0?PlayerBotRepositoryDAO.parties(c,row.account):PlayerBotRepositoryDAO.removed(c,row.account,row.character);
     if(stored==null){
      if(!mode.equals("apply"))throw new SQLException("Unmigrated repository file");
      if(row.character==0)PlayerBotRepositoryDAO.parties(c,row.account,row.text);else PlayerBotRepositoryDAO.removed(c,row.account,row.character,row.text);imported++;
      stored=row.character==0?PlayerBotRepositoryDAO.parties(c,row.account):PlayerBotRepositoryDAO.removed(c,row.account,row.character);
     }
     if(!JSON.parseObject(stored).equals(JSON.parseObject(row.text)))throw new SQLException("Stored repository differs; retain files for review");
     if(row.character==0)PlayerBotRepository.validateParties(c,row.account,stored);else PlayerBotRepository.validateRemoved(c,row.account,row.character,stored);
     String relative=server.resolve("config/playerbots").relativize(row.path).toString().replace('\\','/');
     String hash=HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(row.path)));lines.add(relative+"|"+hash);
    }
    if(mode.equals("apply")){Files.writeString(Path.of(report+".commit-attempted"),"Commit boundary; uncertain acknowledgement requires review");c.commit();}
   }catch(Exception e){if(mode.equals("apply"))c.rollback();throw e;}
   Files.write(report,lines);System.out.println("OK: "+legacy.size()+" exact owned repository snapshots; imported="+imported+"; mode="+mode);
  }
 }
}
