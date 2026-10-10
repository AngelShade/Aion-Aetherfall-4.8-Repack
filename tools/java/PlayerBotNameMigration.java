package com.aionemu.gameserver.services.playerbot;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import java.security.MessageDigest;
import com.aionemu.commons.database.DatabaseFactory;
import com.aionemu.commons.configs.DatabaseConfig;
import com.aionemu.commons.configuration.ConfigurableProcessor;
import com.aionemu.commons.utils.PropertiesUtils;
import com.aionemu.gameserver.configs.main.NameConfig;
import com.aionemu.gameserver.model.Gender;

/** Authorized offline name-only migration. No World, actor, GameServer or ID allocator. */
public final class PlayerBotNameMigration {
 record Change(int id,int account,String oldName,String name) {}
 static final String[] TABLES={"playerbot_roster","playerbot_population_accounts","playerbot_population_members",
  "playerbot_world_residents","playerbot_population_events","playerbot_population_state"};
 public static void main(String[] args) {
  try{run(args);}catch(Exception e){System.err.println("Bot naming migration refused: "+e.getClass().getSimpleName()+"; keep GameServer stopped and retain recovery; no credentials printed");System.exit(1);}System.exit(0);
 }
 static void run(String[] args)throws Exception {
  if(args.length!=3 || !Set.of("plan","apply","verify").contains(args[0]))throw new IllegalArgumentException("mode server output required");
  String mode=args[0];Path server=Path.of(args[1]),out=Path.of(args[2]);Files.createDirectories(out);
  ((ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).setLevel(ch.qos.logback.classic.Level.OFF);
  var config=PropertiesUtils.load(server.resolve("config/network/database.properties").toString());
  config.putAll(PropertiesUtils.load(server.resolve("config/main/name.properties").toString()));
  config.putAll(PropertiesUtils.load(server.resolve("config/mygs.properties").toString()));
  ConfigurableProcessor.process(config,DatabaseConfig.class,NameConfig.class);DatabaseConfig.DATABASE_CONNECTIONS_MAX=3;DatabaseFactory.init();
  try(var c=DatabaseFactory.getConnection()) {
   try(var s=c.prepareStatement("SELECT TABLE_NAME,ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ('players','old_names')");var r=s.executeQuery()) {
    var tables=new HashSet<String>();while(r.next()){if(!"InnoDB".equalsIgnoreCase(r.getString(2)))throw new SQLException("Name migration requires transactional native tables");tables.add(r.getString(1));}
    if(!tables.equals(Set.of("players","old_names")))throw new SQLException("Native name tables missing");
   }
   c.setReadOnly(!mode.equals("apply"));c.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);c.setAutoCommit(false);
   if(mode.equals("plan"))plan(c,out);
   else if(mode.equals("apply"))apply(c,out);
   else verify(c,out);
  }
 }
 static void plan(Connection c,Path out)throws Exception {
  if(Files.exists(out.resolve("names.tsv")))throw new IllegalStateException("Do not overwrite an existing migration plan");
  var changes=new ArrayList<Change>();var occupied=new HashSet<String>();
  try(var s=c.prepareStatement("SELECT name FROM players UNION ALL SELECT old_name FROM old_names WHERE renamed_date > NOW() - INTERVAL ? DAY AND ? > 0")) {
   s.setInt(1,NameConfig.RESERVE_OLD_NAME_DAYS);s.setInt(2,NameConfig.RESERVE_OLD_NAME_DAYS);
   try(var r=s.executeQuery()){while(r.next())occupied.add(r.getString(1).toLowerCase(Locale.ROOT));}
  }
  try(var s=c.prepareStatement("SELECT p.id,p.account_id,p.name,p.gender,m.account_id,r.account_id,r.ready "
   +"FROM players p LEFT JOIN playerbot_population_members m ON m.player_id=p.id "
   +"LEFT JOIN playerbot_population_accounts a ON a.account_id=m.account_id "
   +"LEFT JOIN playerbot_roster r ON r.player_id=p.id WHERE a.account_id IS NOT NULL ORDER BY p.id");var r=s.executeQuery()) {
   while(r.next()) {
    int id=r.getInt(1),account=r.getInt(2);String old=r.getString(3);
    if(!PlayerBotNames.legacyWorld(id,account,old,(Integer)r.getObject(5),(Integer)r.getObject(6),r.getBoolean(7)))continue;
    String name=PlayerBotNames.choose(id,Gender.valueOf(r.getString(4)),n->occupied.contains(n.toLowerCase(Locale.ROOT)));
    occupied.add(name.toLowerCase(Locale.ROOT));changes.add(new Change(id,account,old,name));
   }
  }
  var before=proof(c,Map.of(),Set.of());var after=proof(c,renameMap(changes),Set.of());
  // Backup is a review/recovery artifact only, never a runtime identity store.
  var rollback=new StringBuilder("-- Offline PB-REPAIR-NAMES-017 recovery; review and stop GameServer before use.\nSTART TRANSACTION;\n");
  for(var x:changes)rollback.append("UPDATE players SET name='").append(x.oldName()).append("' WHERE id=").append(x.id()).append(" AND account_id=").append(x.account()).append(" AND name='").append(x.name()).append("';\n");
  Files.writeString(out.resolve("restore-names.sql"),rollback.toString());
  Files.write(out.resolve("names.tsv"),changes.stream().map(x->x.id()+"\t"+x.account()+"\t"+x.oldName()+"\t"+x.name()).toList());
  Files.write(out.resolve("before.txt"),before);Files.write(out.resolve("expected.txt"),after);c.commit();
  System.out.println("OK: bot names plan; "+changes.size()+" exact legacy world characters; native SQL read only");
 }
 static List<Change> changes(Path out)throws Exception {
  var list=new ArrayList<Change>();var ids=new HashSet<Integer>();var names=new HashSet<String>();
  for(String line:Files.readAllLines(out.resolve("names.tsv"))) {
   String[] p=line.split("\t",-1);if(p.length!=4)throw new IllegalArgumentException("Invalid reviewed rename row");
   var x=new Change(Integer.parseInt(p[0]),Integer.parseInt(p[1]),p[2],p[3]);
   if(!ids.add(x.id()) || !names.add(x.name().toLowerCase(Locale.ROOT)))throw new IllegalArgumentException("Duplicate reviewed rename");list.add(x);
  }
  return list;
 }
 static Map<Integer,String> renameMap(List<Change> list) {
  var map=new HashMap<Integer,String>();for(var x:list)map.put(x.id(),x.name());return map;
 }
 static void apply(Connection c,Path out)throws Exception {
  if(Files.exists(out.resolve("commit-attempted.txt")))throw new IllegalStateException("Commit previously attempted; independently verify, never replay");
  if(!proof(c,Map.of(),Set.of()).equals(Files.readAllLines(out.resolve("before.txt"))))throw new SQLException("Native state changed since reviewed plan");
  var changes=changes(out);var history=new TreeSet<Long>();
  try {
   for(var x:changes)history.add(PlayerBotNames.rename(c,x.id(),x.account(),x.oldName(),x.name()));
   if(!proof(c,Map.of(),history).equals(Files.readAllLines(out.resolve("expected.txt"))))throw new SQLException("Name-only transaction verification failed");
   Files.write(out.resolve("history-ids.txt"),history.stream().map(Object::toString).toList());
   var rollback=new StringBuilder(Files.readString(out.resolve("restore-names.sql")));
   for(long id:history)rollback.append("DELETE FROM old_names WHERE id=").append(id).append(";\n");
   rollback.append("COMMIT;\n");Files.writeString(out.resolve("restore-committed.sql"),rollback);
   Files.writeString(out.resolve("commit-attempted.txt"),"Native name-only commit attempted; independent verification required.\n");
   c.commit();
  }catch(Exception e){try{c.rollback();}catch(SQLException rollback){e.addSuppressed(rollback);}throw e;}
  System.out.println("OK: bot names apply; "+changes.size()+" names and native old_names entries committed; native IDs/progress unchanged");
 }
 static void verify(Connection c,Path out)throws Exception {
  var ids=new TreeSet<Long>();for(String line:Files.readAllLines(out.resolve("history-ids.txt")))ids.add(Long.parseLong(line));
  if(!proof(c,Map.of(),ids).equals(Files.readAllLines(out.resolve("expected.txt"))))throw new SQLException("Independent native verification differs");
  var changes=changes(out);if(ids.size()!=changes.size())throw new SQLException("History count differs");
  var wanted=new HashSet<String>();for(var x:changes)wanted.add(x.id()+"\t"+x.oldName()+"\t"+x.name());
  try(var s=c.prepareStatement("SELECT id,player_id,old_name,new_name FROM old_names ORDER BY id");var r=s.executeQuery()) {
   while(r.next())if(ids.contains(r.getLong(1)) && !wanted.remove(r.getInt(2)+"\t"+r.getString(3)+"\t"+r.getString(4)))throw new SQLException("Native history differs");
  }
  if(!wanted.isEmpty())throw new SQLException("Native history missing");c.commit();
  Files.writeString(out.resolve("verified.txt"),"OK: "+changes.size()+" names independently verified; all other player columns and population identities/state preserved\n");
  System.out.print(Files.readString(out.resolve("verified.txt")));
 }
 static List<String> proof(Connection c,Map<Integer,String> rename,Set<Long> ignoreHistory)throws Exception {
  var result=new ArrayList<String>();
  result.add("players|"+digest(c,"SELECT * FROM players ORDER BY id",rename,Set.of()));
  result.add("old_names|"+digest(c,"SELECT * FROM old_names ORDER BY id",Map.of(),ignoreHistory));
  for(String table:TABLES)result.add(table+"|"+digest(c,"SELECT * FROM "+table+" ORDER BY 1",Map.of(),Set.of()));
  return result;
 }
 static String digest(Connection c,String sql,Map<Integer,String> rename,Set<Long> ignore)throws Exception {
  var hash=MessageDigest.getInstance("SHA-256");
  try(var s=c.prepareStatement(sql);var r=s.executeQuery()) {
   var meta=r.getMetaData();int id=r.findColumn(sql.contains("FROM players ")?"id":sql.contains("FROM old_names ")?"id":meta.getColumnLabel(1));
   for(int i=1;i<=meta.getColumnCount();i++)feed(hash,meta.getColumnLabel(i));
   while(r.next()) {
    if(ignore.contains(r.getLong(id)))continue;
    for(int i=1;i<=meta.getColumnCount();i++) {
     String value=meta.getColumnLabel(i).equals("name") && rename.containsKey(r.getInt(id))?rename.get(r.getInt(id)):r.getString(i);
     feed(hash,value);
    }
   }
  }
  return HexFormat.of().formatHex(hash.digest());
 }
 static void feed(MessageDigest hash,String value) {
  if(value==null){hash.update(new byte[]{-1,-1,-1,-1});return;}
  byte[] bytes=value.getBytes(StandardCharsets.UTF_8);hash.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.length).array());hash.update(bytes);
 }
}
