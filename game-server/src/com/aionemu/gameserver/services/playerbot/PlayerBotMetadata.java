/* AI value persistence purpose adapted from PlayerbotRepository.cpp at
 * 037c01418b5d01506917a3db9b44fd56ac5f965c. GPL-2.0-or-later.
 * Native Aion character custody/checkpoint transaction; no WoW database/opcodes. */
package com.aionemu.gameserver.services.playerbot;

import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import com.aionemu.commons.database.DatabaseFactory;
import com.aionemu.gameserver.dao.PlayerBotMetadataDAO;

/** Cache only in AI/settings actions; checkpoint writes share native progress/inventory commit. */
public final class PlayerBotMetadata {
 record Key(int character,String section) {}
 static final class Entry {
  final int account;long revision;Map<String,String> current,committed;
  Entry(int account,long revision,Map<String,String> current,Map<String,String> committed){this.account=account;this.revision=revision;this.current=current;this.committed=committed;}
 }
 public record Pending(int account,int character,String section,long revision,Map<String,String> values) {public Pending{values=Map.copyOf(values);}}
 private static final Map<Key,Entry> CACHE=new HashMap<>();
 static Map<String,String> values(Properties p){var map=new TreeMap<String,String>();for(String key:p.stringPropertyNames())map.put(key,p.getProperty(key));return Map.copyOf(map);}
 static Properties properties(Map<String,String> map){var p=new Properties();p.putAll(map);return p;}
 public static boolean validAccount(int account){return PlayerBotPopulationPolicy.account(account);}
 public static void validate(int account,int character,String section,Map<String,String> map)throws IOException {
  try {
   if(!validAccount(account) || character<=0 || !Set.of("care","gear","preferences","behavior","formation","spacing","party-rewards","quest-completion","looks","pvp","population","growth","quest-practice","road-duty","loadouts","focus").contains(section))throw new IllegalArgumentException("Invalid section/owner");
   if(Integer.parseInt(map.get("account"))!=account || Integer.parseInt(map.get("character"))!=character)throw new IllegalArgumentException("Owner mismatch");
   if(section.equals("focus")) {
    PlayerBotFocus.validate(map);
   } else if(section.equals("road-duty")) {
    if(!PlayerBotPopulationPolicy.serverAccount(account))throw new IllegalArgumentException("Road careers require a generated global resident");
    PlayerBotRoadDuty.Career.valueOf(map.getOrDefault("career","NONE"));PlayerBotRoadDuty.Rank.valueOf(map.getOrDefault("rank","PEDDLER"));
    if(!Set.of("true","false").contains(map.getOrDefault("suspended","false")))throw new IllegalArgumentException("Invalid road suspension");
    int point=Integer.parseInt(map.getOrDefault("point","0")),direction=Integer.parseInt(map.getOrDefault("direction","1"));
    if(point<0 || point>1023 || Math.abs(direction)!=1 || Long.parseLong(map.getOrDefault("restockAt","0"))<0)throw new IllegalArgumentException("Invalid road progress");
    if(Integer.parseInt(map.getOrDefault("visitor","0"))<0)throw new IllegalArgumentException("Invalid road visitor");
    if(map.getOrDefault("route","").length()>192)throw new IllegalArgumentException("Invalid road route id");
    // Warden service additions are optional so all pre-beat road-duty rows remain readable.
    for(String key:List.of("stopUntil","escortUntil","waitUntil","distressAt"))
     if(Long.parseLong(map.getOrDefault(key,"0"))<0)throw new IllegalArgumentException("Invalid road timer");
    for(String key:List.of("escortWarned","fairWarned"))if(!Set.of("true","false").contains(map.getOrDefault(key,"false")))throw new IllegalArgumentException("Invalid Warden warning flag");
    if(map.containsKey("escortStartX") || map.containsKey("escortStartY") || map.containsKey("escortStartZ"))
     for(String key:List.of("escortStartX","escortStartY","escortStartZ"))if(!Float.isFinite(Float.parseFloat(map.get(key))))throw new IllegalArgumentException("Invalid escort origin");
    for(var e:map.entrySet())if(e.getKey().startsWith("stock.")) {
     var origin=e.getValue().split(":");if(Integer.parseInt(e.getKey().substring(6))<=0 || origin.length!=2
      || Integer.parseInt(origin[0])<=0 || Integer.parseInt(origin[1])<=0)throw new IllegalArgumentException("Invalid merchant provenance");
    }
   } else if(section.equals("growth")) {
    PlayerBotGrowth.validate(account,map);
   } else if(section.equals("quest-practice")) {
    for(var e:map.entrySet())if(e.getKey().startsWith("quest."))if(Integer.parseInt(e.getKey().substring(6))<=0 || !e.getValue().equals("1"))throw new IllegalArgumentException("Invalid quest practice mark");
    if(!map.getOrDefault("help","").matches("([qn][0-9]+:[0-9]+:[0-9]+(,|$))*"))throw new IllegalArgumentException("Invalid help claim");
   } else if(section.equals("population")) {
    if(!PlayerBotPopulationPolicy.serverAccount(account))throw new IllegalArgumentException("Population management requires a generated global account");
    for(String key:List.of("randomizeAt","levelPlayedMillis","playedCheckAt","lastLevel","pendingLevel","deadUntil","reviveAt"))
     if(Long.parseLong(map.getOrDefault(key,"0"))<0)throw new IllegalArgumentException("Invalid population event");
    for(String key:List.of("lastLevel","pendingLevel"))if(Integer.parseInt(map.getOrDefault(key,"0"))>65)throw new IllegalArgumentException("Invalid population level");
    if(!Set.of("true","false").contains(map.getOrDefault("pendingFull","false")))throw new IllegalArgumentException("Invalid population factory mode");
    if(!Set.of("true","false").contains(map.getOrDefault("pendingField","false"))
     || !Set.of("","bracket","reset","refresh","revive","teleport").contains(map.getOrDefault("pendingReason","")))throw new IllegalArgumentException("Invalid population factory intent");
   } else if(section.equals("pvp")) {
    for(String key:List.of("nextRift","visitUntil"))if(Long.parseLong(map.getOrDefault(key,"0"))<0)throw new IllegalArgumentException("Invalid PvP timer");
   } else if(section.equals("looks")) {
    if(!Set.of("2","3","4").contains(map.get("version")))throw new IllegalArgumentException("Invalid companion appearance version");
   } else if(section.equals("care")) {
    for(String key:List.of("enchant","salvage","partySync","levelTogether"))if(map.containsKey(key) && !Set.of("true","false").contains(map.get(key)))throw new IllegalArgumentException("Invalid care flag");
    for(String key:List.of("reserve","dailyBudget","spent"))if(map.containsKey(key) && Long.parseLong(map.get(key))<0)throw new IllegalArgumentException("Invalid budget");
    ids(map.getOrDefault("skipped",""));ids(map.getOrDefault("approved",""));
    for(var e:map.entrySet())if(e.getKey().startsWith("together.")){if(Integer.parseInt(e.getKey().substring(9))<=0 || Integer.parseInt(e.getValue())<0)throw new IllegalArgumentException("Invalid quest witness");}
   } else if(section.equals("gear")) {
    PlayerBotGearPolicy.parse(map.get("mode"),map.get("profile"),map.get("quality"),map.get("level"),map.get("threshold"),map.get("weapon"),map.get("vendors"),map.get("rolls"));
    ids(map.getOrDefault("generated",""));for(String slot:map.getOrDefault("starterSlots","").split(","))if(!slot.isBlank() && Long.parseLong(slot)<=0)throw new IllegalArgumentException("Invalid starter slot");
    if(!map.getOrDefault("build","AUTO").matches("AUTO|[a-z0-9_]{1,24}"))throw new IllegalArgumentException("Invalid Stigma build");
   } else if(section.equals("loadouts")) {
    PlayerBotGearPolicy.validateLoadouts(map);
   } else if(section.equals("preferences")) {
    PlayerBotRules.Role.valueOf(map.get("role"));
    for(String flag:List.of("area","supplies","gear","loot","questing"))if(map.containsKey(flag) && !Set.of("true","false").contains(map.get(flag)))throw new IllegalArgumentException("Invalid preference flag");
    for(String flag:List.of("area","supplies","gear"))if(!map.containsKey(flag))throw new IllegalArgumentException("Missing preference flag");
   } else if(section.equals("behavior")) {
    if(!Set.of("true","false").contains(map.getOrDefault("questCombat","true")))throw new IllegalArgumentException("Invalid quest combat flag");
   } else if(section.equals("formation")) {
    PlayerBotFormationLayout.parse(map.get("formation"));
   } else if(section.equals("spacing")) {
    PlayerBotSpacing.validate(Float.parseFloat(map.get("follow")),Float.parseFloat(map.get("attack")));
   } else if(section.equals("quest-completion")) {
    ids(map.getOrDefault("blocked",""));
    for(var e:map.entrySet())if(e.getKey().startsWith("quest.")) {
     String[] key=e.getKey().split("\\."),counts=e.getValue().split(":");
     if(key.length!=3 || counts.length!=2 || Integer.parseInt(key[1])<=0 || Integer.parseInt(key[2])<=0
      || Integer.parseInt(counts[0])<0 || Integer.parseInt(counts[1])<0)throw new IllegalArgumentException("Invalid shared quest progress");
    }
   } else if(section.equals("party-rewards")) {
    for(var e:map.entrySet())if(e.getKey().startsWith("npc."))if(Integer.parseInt(e.getKey().substring(4))<=0 || Integer.parseInt(e.getValue())<=0)throw new IllegalArgumentException("Invalid reward NPC witness");
   }
  }catch(RuntimeException e){throw new IOException("Invalid bot "+section+" metadata for character "+character,e);}
 }
 private static void ids(String text){for(String id:text.split(","))if(!id.isBlank() && Integer.parseInt(id)<=0)throw new IllegalArgumentException("Invalid metadata ID");}
 static Properties load(int account,int character,String section,Path legacy)throws IOException {
  Key key=new Key(character,section);
  synchronized(PlayerBotMetadata.class){Entry cached=CACHE.get(key);if(cached!=null)return loaded(account,cached);}
  // SQL and legacy files are read outside the monitor every active bot's reads and saves share.
  Entry e;
  try(var c=DatabaseFactory.getConnection()) {
   var row=PlayerBotMetadataDAO.load(c,account,character,section);
   Map<String,String> initial;long revision;
   if(row!=null){initial=row.values();revision=row.revision();validate(account,character,section,initial);}
   else if(legacy!=null && Files.exists(legacy)) {
    var p=new Properties();try(var in=Files.newInputStream(legacy)){p.load(in);}initial=values(p);validate(account,character,section,initial);revision=0;
   } else {initial=Map.of();revision=0;}
   e=new Entry(account,revision,initial,revision==0?Map.of():initial);
  }catch(SQLException ex){throw new IOException("Cannot load bot metadata from database; recruitment refused rather than defaulting",ex);}
  // A concurrent loader or save wins; never replace its queued changes.
  synchronized(PlayerBotMetadata.class){Entry cached=CACHE.putIfAbsent(key,e);return loaded(account,cached==null?e:cached);}
 }
 private static Properties loaded(int account,Entry e)throws IOException {
  if(e.account!=account)throw new IOException("Cached bot metadata owner mismatch");
  return properties(e.current);
 }
 static synchronized void save(int account,int character,String section,Properties p)throws IOException {
  var map=values(p);validate(account,character,section,map);Entry e=CACHE.get(new Key(character,section));
  if(e==null || e.account!=account)throw new IOException("Bot metadata was not loaded for this owner");
  if(section.equals("growth"))try{PlayerBotGrowth.validateUpdate(e.current,map);}
  catch(RuntimeException ex){throw new IOException("Cannot replace immutable bot growth traits",ex);}
  e.current=map; // equality with committed handles repeated identical saves; no file or SQL here.
 }
 /** Read an existing actor snapshot without loading SQL or creating a dirty section. */
 static synchronized Map<String,String> cached(int account,int character,String section)throws IOException {
  Entry entry=CACHE.get(new Key(character,section));if(entry==null)return null;
  if(entry.account!=account)throw new IOException("Cached bot metadata owner mismatch");
  return entry.current;
 }
 public static synchronized List<Pending> pending(int account,int character)throws SQLException {
  var rows=new ArrayList<Pending>();
  for(var pair:CACHE.entrySet())if(pair.getKey().character==character) {
   Entry e=pair.getValue();if(e.account!=account)throw new SQLException("Checkpoint bot metadata owner mismatch");
   if(!e.current.equals(e.committed))rows.add(new Pending(account,character,pair.getKey().section,e.revision,e.current));
  }
  rows.sort(Comparator.comparing(Pending::section));return List.copyOf(rows);
 }
 public static void store(Connection c,List<Pending> rows)throws SQLException {
  for(var row:rows)PlayerBotMetadataDAO.write(c,row.account,row.character,row.section,row.revision,row.values);
 }
 public static synchronized void committed(List<Pending> rows) {
  for(var row:rows) {
   Entry e=CACHE.get(new Key(row.character,row.section));
   if(e==null || e.account!=row.account || e.revision!=row.revision)throw new IllegalStateException("Bot metadata commit/cache mismatch");
   e.revision++;e.committed=row.values; // later queued changes remain dirty after this snapshot commits.
  }
 }
 public static synchronized void release(int account,int character)throws SQLException {
  if(!pending(account,character).isEmpty())throw new SQLException("Unsaved bot metadata cannot be discarded");
  CACHE.entrySet().removeIf(e->e.getKey().character==character && e.getValue().account==account);
 }
 /** Rehydrate a discarded, unpublished generated factory attempt from SQL on its next load.
  * Pending reset intent was checkpointed before normal logout. Never discard a live actor's work. */
 static synchronized void reloadUnpublished(int account,int character)throws SQLException {
  if(!PlayerBotPopulationPolicy.serverAccount(account) || !PlayerBotLease.isReserved(character)
   || com.aionemu.gameserver.world.World.getInstance().isInWorld(character)
   || com.aionemu.gameserver.services.player.PlayerLeaveWorldService.isLeavingWorld(character))
   throw new SQLException("Population factory reload requires exclusive unpublished character custody");
  CACHE.entrySet().removeIf(e->e.getKey().character==character && e.getValue().account==account);
 }
 /** Idempotent offline migration; existing database values always win over retained legacy files. */
 public static boolean importLegacy(Connection c,int account,int character,String section,Properties p)throws IOException,SQLException {
  var map=values(p);validate(account,character,section,map);
  var row=PlayerBotMetadataDAO.load(c,account,character,section);
  if(row!=null){validate(account,character,section,row.values());return false;}
  PlayerBotMetadataDAO.write(c,account,character,section,0,map);return true;
 }
 private PlayerBotMetadata() {}
}
