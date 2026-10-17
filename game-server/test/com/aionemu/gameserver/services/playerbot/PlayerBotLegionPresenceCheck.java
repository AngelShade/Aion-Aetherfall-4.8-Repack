package com.aionemu.gameserver.services.playerbot;

import java.lang.reflect.*;
import java.nio.*;
import java.sql.*;
import java.util.*;
import javax.sql.DataSource;
import sun.misc.Unsafe;
import com.aionemu.commons.database.DatabaseFactory;
import com.aionemu.gameserver.model.PlayerClass;
import com.aionemu.gameserver.model.gameobjects.player.*;
import com.aionemu.gameserver.model.team.legion.*;
import com.aionemu.gameserver.network.aion.AionConnection;
import com.aionemu.gameserver.network.aion.serverpackets.SM_LEGION_UPDATE_MEMBER;
import com.aionemu.gameserver.services.LegionService;

/** Real native cache, lifecycle and wire projection on inert actors and recording JDBC. */
public final class PlayerBotLegionPresenceCheck {
 static int checks;static Unsafe unsafe;
 static void check(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
 static class Actor extends Player {
  PlayerCommonData data;boolean bot,spawned;
  Actor(){super(null,null);}
  @Override public PlayerCommonData getCommonData(){return data;}
  @Override public int getObjectId(){return data.getPlayerObjId();}
  @Override public boolean isPlayerBot(){return bot;}
  @Override public boolean isSpawned(){return spawned;}
  @Override public boolean isOnline(){return false;}
  @Override public com.aionemu.gameserver.world.knownlist.KnownList getKnownList(){return new com.aionemu.gameserver.world.knownlist.KnownList(this);}
  @Override public String getName(boolean tag){return data.getName();}
 }
 static class Guild extends Legion {
  Guild(){super(700,"Synthetic Legion");}
  @Override public List<Player> getOnlinePlayers(){return List.of();}
  @Override public List<LegionMember> getMembers(){throw new AssertionError("Headless admission must not load a nonexistent recipient roster");}
 }
 static Actor actor(int id,boolean bot)throws Exception {
  Actor a=(Actor)unsafe.allocateInstance(Actor.class);a.bot=bot;a.spawned=true;
  a.data=new PlayerCommonData(id);a.data.setName("Synthetic"+id);a.data.setPlayerClass(PlayerClass.CHANTER);
  a.data.setMapId(210040000);a.data.setLastOnline(new Timestamp(1234000));
  a.data.setQuestExpands(2);a.data.setWhBonusExpands(1);return a;
 }
 static ByteBuffer packet(LegionMember member)throws Exception {
  var packet=new SM_LEGION_UPDATE_MEMBER(member);var buffer=ByteBuffer.allocate(128).order(ByteOrder.LITTLE_ENDIAN);packet.setBuf(buffer);
  var method=SM_LEGION_UPDATE_MEMBER.class.getDeclaredMethod("writeImpl",AionConnection.class);method.setAccessible(true);method.invoke(packet,new Object[]{null});return buffer;
 }
 static class Jdbc {
  List<String> writes=new ArrayList<>();Map<Integer,Object> progress;boolean stopAtLife;
  Connection connection(){return PlayerBotMetadataCheck.proxy(Connection.class,(p,m,a)->{
   if(m.getName().equals("getAutoCommit"))return true;
   if(m.getName().equals("prepareStatement"))return statement((String)a[0]);
   return PlayerBotMetadataCheck.zero(m.getReturnType());
  });}
  PreparedStatement statement(String sql)throws SQLException {
   if(stopAtLife && sql.startsWith("INSERT INTO player_life_stats"))throw new SQLException("intentional fixture stop after native progress");
   var params=new HashMap<Integer,Object>();return PlayerBotMetadataCheck.proxy(PreparedStatement.class,(p,m,a)->{
    if(m.getName().equals("getConnection"))return connection();
    if(m.getName().startsWith("set")){params.put((Integer)a[0],a[1]);return null;}
    if(m.getName().equals("executeQuery"))return PlayerBotMetadataCheck.proxy(ResultSet.class,(r,rm,ra)->PlayerBotMetadataCheck.zero(rm.getReturnType()));
    if(m.getName().equals("execute") || m.getName().equals("executeUpdate")){
     writes.add(sql);if(sql.startsWith("UPDATE players SET exp="))progress=new HashMap<>(params);
     return m.getName().equals("execute")?true:1;
    }
    return PlayerBotMetadataCheck.zero(m.getReturnType());
   });
  }
 }
 static void progress(Actor actor,Jdbc db)throws Exception {
  db.stopAtLife=true;var method=PlayerBotPersistence.class.getDeclaredMethod("saveProgress",Connection.class,Player.class,PlayerBotPersistence.Home.class);method.setAccessible(true);
  try{method.invoke(null,db.connection(),actor,new PlayerBotPersistence.Home(210040000,1,2,3,(byte)4,0));throw new AssertionError("Missing fixture stop");}
  catch(InvocationTargetException e){check(e.getCause() instanceof SQLException && e.getCause().getMessage().contains("intentional fixture stop"),"Native players progress executed before deliberate fixture stop");}
  check(Objects.equals(db.progress.get(12),actor.data.getLastOnline()) && db.progress.get(15).equals(actor.getObjectId()),"Native SQL persists exact actor logout timestamp and character ID");
  check(db.progress.get(13).equals(2)&&db.progress.get(14).equals(1)&&db.writes.stream().anyMatch(s->s.contains("quest_expands=?,wh_bonus_expands=?")),"Quest cube and warehouse rewards persist with native companion progress");
  check(db.writes.stream().anyMatch(s->s.contains("last_online=COALESCE(?,last_online)")),"Null native timestamp preserves existing SQL value");
  check(db.progress.get(6).equals(1f) && db.progress.get(7).equals(2f) && db.progress.get(8).equals(3f),"Pre-recruitment home coordinates still persist unchanged");
 }
 public static void main(String[] args)throws Exception {
  var f=Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);unsafe=(Unsafe)f.get(null);
  var source=DatabaseFactory.class.getDeclaredField("dataSource");source.setAccessible(true);Object prior=source.get(null);var db=new Jdbc();
  source.set(null,PlayerBotMetadataCheck.proxy(DataSource.class,(p,m,a)->m.getName().equals("getConnection")?db.connection():PlayerBotMetadataCheck.zero(m.getReturnType())));
  try {
   for(int id:List.of(41,42)) {
    Actor bot=actor(id,true);Guild guild=new Guild();LegionMember member=new LegionMember(id,guild);bot.setLegionMember(member);
    member.setPlayerData(bot.data);check(!member.isOnline(),"Saved offline native data remains offline");
    PlayerBotLegionPresence.entered(bot);
    check(member.isOnline(),"Spawned companion or independent bot enters native legion presence");
    check(!bot.isOnline() && !bot.data.isOnline(),"Presence does not fabricate a client or human login flag");
    var wire=packet(member);check(wire.getInt(0)==id && wire.get(11)==1 && wire.getInt(12)==0,"Actual legion update wire marks active bot online");
    bot.data.setMapId(220010000);PlayerBotLegionPresence.changed(bot);
    check(member.getWorldId()==220010000 && packet(member).getInt(7)==220010000,"Native map update keeps active bot presence and destination");
    check(db.writes.isEmpty(),"Entry and map update introduce no persistence queries");
    long before=System.currentTimeMillis();bot.spawned=false;PlayerBotLegionPresence.left(bot);
    check(!member.isOnline() && bot.data.getLastOnline().getTime()>=before,"Despawn immediately publishes offline with real departure time");
    wire=packet(member);check(wire.get(11)==0 && wire.getInt(12)==bot.data.getLastOnline().getTime()/1000,"Actual wire gives offline timestamp even while save lease remains held");
    check(db.writes.stream().anyMatch(s->s.startsWith("UPDATE legion_members")),"Departure retains native member persistence path");
    check(member.getRank()==LegionRank.VOLUNTEER && member.getObjectId()==id && bot.getLegion()==guild,"Presence retains native membership, rank and ID");
    progress(bot,db);db.writes.clear();db.stopAtLife=false;
    PlayerBotLegionPresence.entered(bot);check(!member.isOnline(),"A despawned save-held actor cannot become online");
    bot.spawned=true;PlayerBotLegionPresence.entered(bot);check(member.isOnline(),"Same identity becomes online on later reuse");
   }
   Actor invited=actor(49,true);Guild inviteGuild=new Guild();
   var add=LegionService.class.getDeclaredMethod("addLegionMember",Legion.class,Player.class,LegionRank.class);add.setAccessible(true);
   add.invoke(LegionService.getInstance(),inviteGuild,invited,LegionRank.VOLUNTEER);
   check(invited.getLegionMember().isOnline() && packet(invited.getLegionMember()).get(11)==1,"Actual native invitation admission marks spawned headless member online");
   check(db.writes.stream().anyMatch(s->s.startsWith("INSERT INTO legion_members")),"Actual admission retains native SQL membership creation");
   var predicate=Arrays.stream(Legion.class.getDeclaredMethods()).filter(m->m.getName().startsWith("lambda$getOnlinePlayers$") && m.getReturnType()==boolean.class).findFirst().orElseThrow();predicate.setAccessible(true);
   invited.spawned=false;check(!(boolean)predicate.invoke(null,invited),"Actual online-member filter excludes a despawned save-held bot still in World");
   invited.spawned=true;check((boolean)predicate.invoke(null,invited),"Actual online-member filter retains spawned bots");
   Actor human=actor(50,false);check((boolean)predicate.invoke(null,human),"Actual filter leaves resident human handling unchanged");LegionMember member=new LegionMember(50,new Guild());human.setLegionMember(member);
   human.data.setOnline(true);human.spawned=false;member.setPlayerData(human);check(member.isOnline(),"Native human online data unchanged during spawn transition");
   human.data.setOnline(false);human.spawned=true;member.setPlayerData(human);check(!member.isOnline(),"Human native offline flag remains authoritative");
   Actor noGuild=actor(60,true);PlayerBotLegionPresence.entered(noGuild);noGuild.spawned=false;PlayerBotLegionPresence.left(noGuild);
   check(noGuild.data.getLastOnline().getTime()>1234000 && !noGuild.isLegionMember(),"Nonmember departure records native last-online without creating membership");
   noGuild.data.setLastOnline(null);progress(noGuild,db);
   System.out.println("OK: "+checks+" native legion residency/lifecycle/wire/SQL checks; no World/native IDs/production database");
  }finally{source.set(null,prior);}
 }
}
