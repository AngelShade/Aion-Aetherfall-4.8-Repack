/* RandomPlayerbotMgr::FindEvent/SetEventValue/AssignAccountTypes and PlayerbotsDatabase,
 * mod-playerbots 037c01418b5d01506917a3db9b44fd56ac5f965c. GPL-2.0-or-later.
 * Aion signed account pools, absolute millisecond deadlines and native custody are retained. */
package com.aionemu.gameserver.services.playerbot;

import java.io.IOException;
import java.sql.*;
import java.util.*;
import com.aionemu.commons.utils.Json;
import com.aionemu.commons.database.DatabaseFactory;
import com.aionemu.gameserver.model.*;
import com.aionemu.gameserver.model.gameobjects.player.PlayerAppearance;

/** Load once, commit only changed rows, publish cache only after a native SQL commit. */
final class PlayerBotPopulationRepository implements PlayerBotPopulationStorage {
 interface Connections { Connection open() throws SQLException; }
 record Snapshot(Map<Integer,PlayerBotWorldRoster.Resident> residents,
                 Map<Integer,PlayerBotWorldPopulation.Pool> server,
                 Map<Integer,PlayerBotWorldPopulation.Pool> manual,
                 PlayerBotPopulationSchedule.State schedule) {
  Snapshot {
   residents=Map.copyOf(residents);server=Map.copyOf(server);manual=Map.copyOf(manual);
   var identities=new HashMap<Integer,Integer>();
   for(var entry:residents.entrySet())if(entry.getKey()!=entry.getValue().character())throw new IllegalArgumentException("Residence key mismatch");
   for(boolean global:List.of(false,true))for(var entry:(global?server:manual).entrySet()) {
    var p=entry.getValue();if(entry.getKey()!=p.account() || global!=PlayerBotPopulationPolicy.serverAccount(p.account()))throw new IllegalArgumentException("Population account scope mismatch");
    for(int id:p.characters())if(identities.put(id,p.account())!=null)throw new IllegalArgumentException("Character belongs to multiple population pools");
   }
   for(var r:residents.values())if(identities.containsKey(r.character()) && identities.get(r.character())!=r.account())throw new IllegalArgumentException("Resident pool owner mismatch");
   if(schedule==null || server.values().stream().mapToInt(p->p.characters().size()).sum()>PlayerBotPopulationPolicy.MAX_RESERVE)throw new IllegalArgumentException("Population reserve exceeds native limit");
   for(int id:schedule.events().keySet())if(!identities.containsKey(id) || !PlayerBotPopulationPolicy.serverAccount(identities.get(id)))throw new IllegalArgumentException("Event lacks its server population identity");
  }
 }
 private final Connections connections;
 private volatile Snapshot cached;
 private long revision;
 PlayerBotPopulationRepository(){this(DatabaseFactory::getConnection);}
 PlayerBotPopulationRepository(Connections connections){this.connections=Objects.requireNonNull(connections);}
 private Snapshot load() throws IOException {
  if(cached!=null)return cached;
  try(var c=connections.open()) {
   c.setReadOnly(true);c.setAutoCommit(false);
   // Read state and all tables in one repeatable-read snapshot.
   c.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
   long nextRevision=revision(c,false);var next=read(c);c.commit();revision=nextRevision;cached=next;return next;
  }catch(SQLException|RuntimeException e){throw new IOException("Cannot load native population repository; no empty-pool fallback",e);}
 }
 static long revision(Connection c,boolean lock) throws SQLException {
  try(var s=c.prepareStatement("SELECT revision,import_digest FROM playerbot_population_state WHERE id=1"+(lock?" FOR UPDATE":""));var r=s.executeQuery()) {
   if(!r.next() || r.getLong(1)<0 || r.getString(2)==null || !r.getString(2).matches("[0-9a-f]{64}"))throw new SQLException("Population import is not committed; run tools/migrate-playerbot-population.py offline");
   return r.getLong(1);
  }
 }
 static Snapshot read(Connection c) throws SQLException {
  int version,target;long until;
  try(var s=c.prepareStatement("SELECT version,target,target_until FROM playerbot_population_state WHERE id=1");var r=s.executeQuery()) {
   if(!r.next())throw new SQLException("Native population state missing");version=r.getInt(1);target=r.getInt(2);until=r.getLong(3);
  }
  var members=new HashMap<Integer,List<Integer>>();
  try(var s=c.prepareStatement("SELECT account_id,player_id,ordinal FROM playerbot_population_members ORDER BY account_id,ordinal");var r=s.executeQuery()) {
   while(r.next()) {var ids=members.computeIfAbsent(r.getInt(1),id->new ArrayList<>());if(r.getInt(3)!=ids.size())throw new SQLException("Population member order has gaps");ids.add(r.getInt(2));}
  }
  var server=new TreeMap<Integer,PlayerBotWorldPopulation.Pool>();var manual=new TreeMap<Integer,PlayerBotWorldPopulation.Pool>();
  try(var s=c.prepareStatement("SELECT account_id,scope,name,membership,created,race,gender,appearance,desired FROM playerbot_population_accounts");var r=s.executeQuery()) {
   while(r.next()) {
    int account=r.getInt(1);String scope=r.getString(2);if(!Set.of("server","manual").contains(scope))throw new SQLException("Unknown population scope");
    var pool=new PlayerBotWorldPopulation.Pool(account,r.getString(3),r.getByte(4),r.getLong(5),Race.valueOf(r.getString(6)),Gender.valueOf(r.getString(7)),Json.read(r.getString(8),PlayerAppearance.class),r.getInt(9),members.getOrDefault(account,List.of()));
    (scope.equals("server")?server:manual).put(account,pool);members.remove(account);
   }
  }
  if(!members.isEmpty())throw new SQLException("Population members lack their native account");
  var residents=new TreeMap<Integer,PlayerBotWorldRoster.Resident>();
  try(var s=c.prepareStatement("SELECT player_id,account_id,map_id,x,y,z,heading FROM playerbot_world_residents");var r=s.executeQuery()) {
   while(r.next()){var resident=new PlayerBotWorldRoster.Resident(r.getInt(2),r.getInt(1),r.getInt(3),r.getFloat(4),r.getFloat(5),r.getFloat(6),r.getByte(7));residents.put(resident.character(),resident);}
  }
  var events=new TreeMap<Integer,PlayerBotPopulationSchedule.Events>();
  try(var s=c.prepareStatement("SELECT player_id,present_until,offline_until,teleport_at,placed_level FROM playerbot_population_events");var r=s.executeQuery()) {
   while(r.next())events.put(r.getInt(1),new PlayerBotPopulationSchedule.Events(r.getLong(2),r.getLong(3),r.getLong(4),r.getInt(5)));
  }
  return new Snapshot(residents,server,manual,new PlayerBotPopulationSchedule.State(version,target,until,events));
 }
 interface Work { void run(Connection c) throws SQLException; }
 private void commit(Snapshot next,Work work) throws IOException {
  load();long expected=revision;
  try(var c=connections.open()) {
   c.setAutoCommit(false);
   try {
    if(revision(c,true)!=expected)throw new SQLException("Population repository changed outside this cache; refusing stale write");
    work.run(c);
    try(var s=c.prepareStatement("UPDATE playerbot_population_state SET revision=revision+1 WHERE id=1 AND revision=?")){s.setLong(1,expected);if(s.executeUpdate()!=1)throw new SQLException("Population revision lost");}
    c.commit();revision=expected+1;cached=next;
   }catch(SQLException|RuntimeException e){try{c.rollback();}catch(SQLException rollback){e.addSuppressed(rollback);}throw e;}
  }catch(SQLException|RuntimeException e){cached=null;throw new IOException("Population SQL commit failed; native identities retained; cache will reload",e);}
 }
 public synchronized Map<Integer,PlayerBotWorldRoster.Resident> residents() throws IOException {return load().residents();}
 public synchronized Map<Integer,PlayerBotWorldPopulation.Pool> pools(boolean server) throws IOException {return server?load().server():load().manual();}
 public synchronized PlayerBotPopulationSchedule.State schedule() throws IOException {return load().schedule();}
 /** Decision readers never wait for SQL or observe an uncommitted replacement. */
 public PlayerBotPopulationSchedule.State cachedSchedule() {var snapshot=cached;return snapshot==null?null:snapshot.schedule();}
 public synchronized void residents(Map<Integer,PlayerBotWorldRoster.Resident> next) throws IOException {
  var old=load();var snapshot=new Snapshot(next,old.server(),old.manual(),old.schedule());if(old.residents().equals(next))return;
  commit(snapshot,c->{
   for(int id:old.residents().keySet())if(!next.containsKey(id))try(var s=c.prepareStatement("DELETE FROM playerbot_world_residents WHERE player_id=? AND account_id=?")){s.setInt(1,id);s.setInt(2,old.residents().get(id).account());s.executeUpdate();}
   for(var r:next.values())if(!r.equals(old.residents().get(r.character()))) {validateResident(c,r);writeResident(c,r);}
  });
 }
 public synchronized void pool(boolean server,PlayerBotWorldPopulation.Pool next) throws IOException {
  var old=load();var pools=new TreeMap<>(server?old.server():old.manual());var previous=pools.put(next.account(),next);
  var snapshot=new Snapshot(old.residents(),server?pools:old.server(),server?old.manual():pools,old.schedule());
  if(previous!=null && (!next.characters().containsAll(previous.characters()) || !next.characters().subList(0,previous.characters().size()).equals(previous.characters())))throw new IOException("Population intent IDs/order cannot be removed or rerolled");
  if(previous!=null && samePool(previous,next))return;
  commit(snapshot,c->{writePool(c,server,next);for(int i=previous==null?0:previous.characters().size();i<next.characters().size();i++){validateIdentity(c,next.account(),next.characters().get(i),false);writeMember(c,next.account(),next.characters().get(i),i);}});
 }
 public synchronized void schedule(PlayerBotPopulationSchedule.State next) throws IOException {
  var old=load();if(old.schedule().equals(next))return;
  commit(new Snapshot(old.residents(),old.server(),old.manual(),next),c->{
   try(var s=c.prepareStatement("UPDATE playerbot_population_state SET version=?,target=?,target_until=? WHERE id=1")){s.setInt(1,next.version());s.setInt(2,next.target());s.setLong(3,next.targetUntil());s.executeUpdate();}
   for(int id:old.schedule().events().keySet())if(!next.events().containsKey(id))try(var s=c.prepareStatement("DELETE FROM playerbot_population_events WHERE player_id=?")){s.setInt(1,id);s.executeUpdate();}
   for(var e:next.events().entrySet())if(!e.getValue().equals(old.schedule().events().get(e.getKey()))) {validateEvent(c,old,e.getKey());writeEvent(c,e.getKey(),e.getValue());}
  });
 }
 public synchronized void event(int id,PlayerBotPopulationSchedule.Events next) throws IOException {
  var old=load();var events=new TreeMap<>(old.schedule().events());events.put(id,next);
  var state=new PlayerBotPopulationSchedule.State(1,old.schedule().target(),old.schedule().targetUntil(),events);
  if(next.equals(old.schedule().events().get(id)))return;
  commit(new Snapshot(old.residents(),old.server(),old.manual(),state),c->{validateEvent(c,old,id);writeEvent(c,id,next);});
 }
 static boolean samePool(PlayerBotWorldPopulation.Pool a,PlayerBotWorldPopulation.Pool b){return Json.write(a).equals(Json.write(b));}
 static void validateIdentity(Connection c,int account,int id,boolean ready) throws SQLException {
  // A member is persisted BEFORE reserve/new-player insertion. Preserve that pending intent.
  if(!PlayerBotPopulationPolicy.account(account) || id<=0)throw new SQLException("Invalid population identity");
  boolean roster=false,player=false;
  try(var s=c.prepareStatement("SELECT account_id,ready FROM playerbot_roster WHERE player_id=?")) {
   s.setInt(1,id);try(var r=s.executeQuery()){if(r.next()){roster=true;if(r.getInt(1)!=account || ready && !r.getBoolean(2))throw new SQLException("Population roster owner/readiness mismatch");}}
  }
  try(var s=c.prepareStatement("SELECT account_id FROM players WHERE id=?")) {
   s.setInt(1,id);try(var r=s.executeQuery()){if(r.next()){player=true;if(r.getInt(1)!=account)throw new SQLException("Population native player owner mismatch");}}
  }
  if(ready && (!roster || !player) || player && !roster)throw new SQLException("Population identity lacks its native custody record");
 }
 static void validateResident(Connection c,PlayerBotWorldRoster.Resident r) throws SQLException {validateIdentity(c,r.account(),r.character(),true);}
 static void validateEvent(Connection c,Snapshot snapshot,int id) throws SQLException {
  var pool=snapshot.server().values().stream().filter(p->p.characters().contains(id)).findFirst().orElseThrow(()->new SQLException("Population event lacks its server pool"));
  validateIdentity(c,pool.account(),id,false);
 }
 static void validate(Connection c,Snapshot snapshot) throws SQLException {
  for(var pools:List.of(snapshot.server(),snapshot.manual()))for(var pool:pools.values())for(int id:pool.characters())validateIdentity(c,pool.account(),id,false);
  for(var r:snapshot.residents().values())validateResident(c,r);
  for(int id:snapshot.schedule().events().keySet())validateEvent(c,snapshot,id);
 }
 static boolean same(Snapshot a,Snapshot b) {
  if(!a.residents().equals(b.residents()) || !a.schedule().equals(b.schedule()))return false;
  for(var pair:List.of(List.of(a.server(),b.server()),List.of(a.manual(),b.manual()))) {
   if(!pair.get(0).keySet().equals(pair.get(1).keySet()))return false;
   for(int account:pair.get(0).keySet())if(!samePool(pair.get(0).get(account),pair.get(1).get(account)))return false;
  }return true;
 }
 static boolean imported(Connection c) throws SQLException {
  try(var s=c.prepareStatement("SELECT id FROM playerbot_population_state");var r=s.executeQuery()){if(!r.next())return false;if(r.getInt(1)!=1 || r.next())throw new SQLException("Unexpected population singleton state");return true;}
 }
 /** Caller owns the offline transaction. Existing imports are verified, never overwritten. */
 static void importSnapshot(Connection c,Snapshot snapshot,String digest) throws SQLException {
  if(c.getAutoCommit() || !digest.matches("[0-9a-f]{64}"))throw new SQLException("Validated offline import transaction required");
  validate(c,snapshot);
  if(imported(c)) {
   revision(c,true);
   if(!same(read(c),snapshot))throw new SQLException("Existing native population differs; retain legacy files for review");
   try(var s=c.prepareStatement("SELECT import_digest FROM playerbot_population_state WHERE id=1");var r=s.executeQuery()){if(!r.next() || !digest.equals(r.getString(1)))throw new SQLException("Legacy population digest changed after import");}
   return;
  }
  for(String table:List.of("playerbot_population_accounts","playerbot_population_members","playerbot_world_residents","playerbot_population_events"))
   try(var s=c.prepareStatement("SELECT 1 FROM "+table+" LIMIT 1");var r=s.executeQuery()){if(r.next())throw new SQLException("Partial unmarked population import; refuse overwrite");}
  for(boolean global:List.of(false,true))for(var pool:(global?snapshot.server():snapshot.manual()).values()) {
   writePool(c,global,pool);for(int i=0;i<pool.characters().size();i++)writeMember(c,pool.account(),pool.characters().get(i),i);
  }
  for(var r:snapshot.residents().values())writeResident(c,r);
  for(var event:snapshot.schedule().events().entrySet())writeEvent(c,event.getKey(),event.getValue());
  try(var s=c.prepareStatement("INSERT INTO playerbot_population_state (id,version,target,target_until,revision,import_digest) VALUES (1,?,?,?,0,?)")) {
   s.setInt(1,snapshot.schedule().version());s.setInt(2,snapshot.schedule().target());s.setLong(3,snapshot.schedule().targetUntil());s.setString(4,digest);s.executeUpdate();
  }
  if(!same(read(c),snapshot))throw new SQLException("Population import did not round-trip exactly");
 }
 static void writePool(Connection c,boolean server,PlayerBotWorldPopulation.Pool p) throws SQLException {
  try(var s=c.prepareStatement("INSERT INTO playerbot_population_accounts (account_id,scope,name,membership,created,race,gender,appearance,desired) VALUES (?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE name=VALUES(name),membership=VALUES(membership),created=VALUES(created),race=VALUES(race),gender=VALUES(gender),appearance=VALUES(appearance),desired=VALUES(desired)")) {
   s.setInt(1,p.account());s.setString(2,server?"server":"manual");s.setString(3,p.name());s.setByte(4,p.membership());s.setLong(5,p.created());s.setString(6,p.race().name());s.setString(7,p.gender().name());s.setString(8,Json.write(p.appearance()));s.setInt(9,p.desired());s.executeUpdate();
  }
 }
 static void writeMember(Connection c,int account,int id,int ordinal) throws SQLException {
  try(var s=c.prepareStatement("INSERT INTO playerbot_population_members (player_id,account_id,ordinal) VALUES (?,?,?)")){s.setInt(1,id);s.setInt(2,account);s.setInt(3,ordinal);s.executeUpdate();}
 }
 static void writeResident(Connection c,PlayerBotWorldRoster.Resident r) throws SQLException {
  try(var s=c.prepareStatement("INSERT INTO playerbot_world_residents (player_id,account_id,map_id,x,y,z,heading) VALUES (?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE account_id=VALUES(account_id),map_id=VALUES(map_id),x=VALUES(x),y=VALUES(y),z=VALUES(z),heading=VALUES(heading)")) {
   s.setInt(1,r.character());s.setInt(2,r.account());s.setInt(3,r.map());s.setFloat(4,r.x());s.setFloat(5,r.y());s.setFloat(6,r.z());s.setByte(7,r.heading());s.executeUpdate();
  }
 }
 static void writeEvent(Connection c,int id,PlayerBotPopulationSchedule.Events e) throws SQLException {
  try(var s=c.prepareStatement("INSERT INTO playerbot_population_events (player_id,present_until,offline_until,teleport_at,placed_level) VALUES (?,?,?,?,?) ON DUPLICATE KEY UPDATE present_until=VALUES(present_until),offline_until=VALUES(offline_until),teleport_at=VALUES(teleport_at),placed_level=VALUES(placed_level)")) {
   s.setInt(1,id);s.setLong(2,e.presentUntil());s.setLong(3,e.offlineUntil());s.setLong(4,e.teleportAt());s.setInt(5,e.placedLevel());s.executeUpdate();
  }
 }
}
