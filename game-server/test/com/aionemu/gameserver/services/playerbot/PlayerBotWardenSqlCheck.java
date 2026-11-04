package com.aionemu.gameserver.services.playerbot;

import java.nio.file.*;
import java.sql.*;
import com.aionemu.gameserver.configs.main.LegionConfig;
import com.aionemu.gameserver.dao.*;
import com.aionemu.gameserver.model.Race;

/** Real native SQL transactions. Hard restricted to a fresh isolated database on port 33907. */
public final class PlayerBotWardenSqlCheck {
 private static int checks;
 private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 private static long scalar(Connection c,String sql)throws SQLException{try(var s=c.createStatement();var r=s.executeQuery(sql)){r.next();return r.getLong(1);}}
 private static void execute(Connection c,String sql)throws SQLException{try(var s=c.createStatement()){s.executeUpdate(sql);}}
 private static PlayerBotLegionRepository.CompanyRequest request(int actor,int map,int id,String name,int maximum) {
  var race=actor==104?Race.ASMODIANS:Race.ELYOS;
  return new PlayerBotLegionRepository.CompanyRequest(PlayerBotPopulationPolicy.account(race,0),actor,race,"Scratch"+actor,map,id,name,maximum,16,(byte)220,(byte)190,(byte)80);
 }
 private static void companies(Connection c)throws Exception {
  execute(c,"INSERT INTO legions (id,name) VALUES (500,'Scratch Pool')");
  execute(c,"INSERT INTO legion_members (legion_id,player_id,`rank`) VALUES (500,100,'BRIGADE_GENERAL'),(500,101,'LEGIONARY')");
  var first=PlayerBotLegionRepository.commitCompany(c,request(100,210020000,9001,"Eltnen Road Wardens",2));
  check(first.created() && first.changed() && first.legion()==9001,"Pool Warden moves to native company");
  check(scalar(c,"SELECT COUNT(*) FROM legion_members WHERE legion_id=500 AND `rank`='BRIGADE_GENERAL' AND player_id=101")==1,"Leaving pool leader promotes a retained native member");
  check(scalar(c,"SELECT legion_id FROM legion_members WHERE player_id=100")==9001,"SQL owns committed Warden custody");
  var repeat=PlayerBotLegionRepository.commitCompany(c,request(100,210020000,9002,"Eltnen Road Wardens",2));
  check(!repeat.changed() && !repeat.created() && repeat.legion()==9001,"Restart/retry adopts existing SQL membership idempotently");
  var second=PlayerBotLegionRepository.commitCompany(c,request(102,210020000,9002,"Eltnen Road Wardens",2));check(!second.created() && second.legion()==9001,"Company fills before opening overflow");
  var overflow=PlayerBotLegionRepository.commitCompany(c,request(103,210020000,9003,"Eltnen Road Wardens",2));check(overflow.created() && overflow.legion()==9003,"Configured size opens overflow");
  check(scalar(c,"SELECT COUNT(*) FROM legions WHERE id=9003 AND name='Eltnen Second Company'")==1,"Overflow native name persisted");
  check(PlayerBotLegionRepository.snapshot(c).guilds().stream().noneMatch(g->g.id()==9001 || g.id()==9003),"Generated pool snapshot ignores all company legions");
  check(PlayerBotLegionRepository.snapshot(c).guilds().stream().anyMatch(g->g.id()==500),"Existing generated pool remains present");
  var moved=PlayerBotLegionRepository.commitCompany(c,request(100,210040000,9004,"Heiron Road Wardens",2));check(moved.legion()==9004,"A changed beat region moves company custody");
  check(scalar(c,"SELECT COUNT(*) FROM legion_members WHERE legion_id=9001 AND player_id=102 AND `rank`='BRIGADE_GENERAL'")==1,"Old company retains native leadership");
  execute(c,"INSERT INTO legions (id,name) VALUES (600,'Conflict Road Wardens')");
  int before=(int)scalar(c,"SELECT COUNT(*) FROM playerbot_warden_companies");
  try{PlayerBotLegionRepository.commitCompany(c,request(101,210050000,9005,"Conflict Road Wardens",2));throw new AssertionError("Name collision committed");}
  catch(PlayerBotLegionRepository.TransactionFailure expected){check(expected.allocationReusable,"Known rollback releases unused allocation");}
  check(scalar(c,"SELECT COUNT(*) FROM playerbot_warden_companies")==before && scalar(c,"SELECT legion_id FROM legion_members WHERE player_id=101")==500,"Collision rolls back company registry and old membership together");
  execute(c,"INSERT INTO legions (id,name) VALUES (700,'Human Protected')");execute(c,"INSERT INTO legion_members (legion_id,player_id,`rank`) VALUES (700,104,'BRIGADE_GENERAL'),(700,1,'LEGIONARY')");
  c.commit();
  try{PlayerBotLegionRepository.commitCompany(c,request(104,220030000,9006,"Altgard Road Wardens",2));throw new AssertionError("Protected legion stolen");}
  catch(PlayerBotLegionRepository.TransactionFailure expected){checks++;}
  check(scalar(c,"SELECT legion_id FROM legion_members WHERE player_id=104")==700,"Human legion custody retained");
  PlayerBotLegionRepository.releaseCompany(c,request(103,210020000,9003,"Eltnen Road Wardens",2).account(),103,"Scratch103",9003);
  check(scalar(c,"SELECT COUNT(*) FROM legion_members WHERE player_id=103")==0,"Reserve loses SQL company membership");
  PlayerBotLegionRepository.releaseCompany(c,request(103,210020000,9003,"Eltnen Road Wardens",2).account(),103,"Scratch103",9003);checks++;
  PlayerBotLegionRepository.releaseCompany(c,request(102,210020000,9001,"Eltnen Road Wardens",2).account(),102,"Scratch102",9001);
  check(scalar(c,"SELECT COUNT(*) FROM legion_members WHERE player_id=102")==0,"Reserve leader release is idempotent and retains actor");
  check(scalar(c,"SELECT COUNT(*) FROM playerbot_roster WHERE player_id IN (102,103) AND ready=TRUE")==2,"Company release preserves ready native population identities");
  try{PlayerBotLegionRepository.releaseCompany(c,request(104,220030000,9006,"Altgard Road Wardens",2).account(),104,"Scratch104",700);throw new AssertionError("Protected reserve legion released");}
  catch(SQLException expected){checks++;}
  check(scalar(c,"SELECT legion_id FROM legion_members WHERE player_id=104")==700,"Reserve release cannot remove human-legion membership");
  var successor=PlayerBotLegionRepository.commitCompany(c,request(101,210040000,9007,"Heiron Road Wardens",2));
  check(successor.legion()==9004,"Reserve-release successor uses existing regional company");
  PlayerBotLegionRepository.releaseCompany(c,request(100,210040000,9004,"Heiron Road Wardens",2).account(),100,"Scratch100",9004);
  check(scalar(c,"SELECT COUNT(*) FROM legion_members WHERE legion_id=9004 AND player_id=101 AND `rank`='BRIGADE_GENERAL'")==1,"Reserve leader release promotes retained native company member");
 }
 private static void travellers(Connection c)throws Exception {
  c.setAutoCommit(false);
  check(PlayerBotWardenDAO.escort(c,1,100,1000,900000,600000)==0,"Escort start reserves SQL traveller slot");c.commit();
  var restored=PlayerBotWardenDAO.currentEscort(c,100,1001);check(restored!=null && restored.player()==1 && restored.until()==901000,"SQL restores escort after metadata checkpoint loss");c.commit();
  check(PlayerBotWardenDAO.escort(c,2,100,1002,900000,600000)==901000,"A second traveller cannot reserve the same Warden after restart");c.commit();
  check(PlayerBotWardenDAO.currentEscort(c,100,901000)==null,"Expired bot reservation no longer occupies Warden slot");c.commit();
  check(PlayerBotWardenDAO.escort(c,1,102,2000,900000,600000)>2000,"One escort per human across separate Wardens");c.commit();
  PlayerBotWardenDAO.ended(c,1,102,2000,600000);c.commit();check(scalar(c,"SELECT bot_id FROM playerbot_warden_travellers WHERE player_id=1")==100,"Other Warden cannot release custody");
  PlayerBotWardenDAO.ended(c,1,100,2000,600000);c.commit();check(PlayerBotWardenDAO.escort(c,1,102,601999,900000,600000)==602000,"End starts full ten-minute cooldown");c.commit();
  check(PlayerBotWardenDAO.escort(c,1,102,602000,900000,600000)==0,"Cooldown boundary allows next escort");c.commit();
  check(PlayerBotWardenDAO.rescue(c,1,1000,900000),"First rescue reserved");c.commit();check(!PlayerBotWardenDAO.rescue(c,1,2000,900000),"Rescue cooldown survives SQL reread");c.commit();
  check(PlayerBotWardenDAO.rescue(c,1,901000,900000),"Rescue cooldown boundary");c.commit();
  check(PlayerBotWardenDAO.distress(c,1,1000,30000),"Human distress response admitted");c.commit();check(!PlayerBotWardenDAO.distress(c,1,1001,30000),"Human distress is rate limited");c.commit();
  PlayerBotWardenDAO.death(c,1,123456);c.commit();check(PlayerBotWardenDAO.death(c,1)==123456,"Monster death evidence retained in SQL");
 }
 private static void tickets(Connection c)throws Exception {
  c.setAutoCommit(true);PlayerBotRecruitmentDAO.prepare(c);c.setAutoCommit(false);
  for(int bounty=1;bounty<=3;bounty++){check(PlayerBotRecruitmentDAO.helped(c,1,"road-bounty:1:"+bounty,3,1)==bounty,"SQL bounty count "+bounty);c.commit();check(PlayerBotRecruitmentDAO.wallet(c,1).tickets()==(bounty==3?1:0),"Only third bounty pays a ticket");}
  check(PlayerBotRecruitmentDAO.helped(c,1,"road-bounty:1:3",3,1)==0,"Duplicate completion is idempotent");c.commit();
  check(PlayerBotRecruitmentDAO.wallet(c,1).tickets()==1,"Retry never duplicates ticket");
  for(int help=1;help<=3;help++){check(PlayerBotRecruitmentDAO.helped(c,1,"help-scratch:"+help,3,1)==help,"Existing help counter stays independent");c.commit();}
  check(PlayerBotRecruitmentDAO.wallet(c,1).tickets()==2,"Existing help rewards still work");
 }
 public static void main(String[] args)throws Exception {
  if(!PlayerBotWardenSqlCheck.class.desiredAssertionStatus() || args.length!=2 || !args[0].matches("jdbc:mysql://127\\.0\\.0\\.1:33907/warden_expansion_check(?:_[0-9]+)?"))throw new IllegalArgumentException("Use -ea and the isolated port 33907 scratch database only");
  PlayerBotWardenExpansionCheck.defaults(LegionConfig.class);
  var properties=new java.util.Properties();properties.setProperty("user","root");properties.setProperty("password","");properties.setProperty("sslMode","DISABLED");
  try(var c=DriverManager.getConnection(args[0],properties)) {
   try(var statement=c.createStatement();var result=statement.executeQuery("SELECT @@port,@@datadir")) {
    result.next();var expected=Path.of(args[1]).toRealPath().getParent().resolve("sql-scratch").toRealPath();
    check(result.getInt(1)==33907 && Path.of(result.getString(2)).toRealPath().equals(expected),"Native SQL server is the retained isolated TEMP data directory");
   }
   check(scalar(c,"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()")==0,"Scratch schema must be empty");
   for(String sql:Files.readString(Path.of(args[1])).split(";"))if(!sql.isBlank())execute(c,sql);
   PlayerBotWardenDAO.prepare(c);
   for(int id:new int[]{1,2,100,101,102,103,104}) {
    var race=id==104?Race.ASMODIANS:Race.ELYOS;int account=id<100?id:PlayerBotPopulationPolicy.account(race,0);
    execute(c,"INSERT INTO players (id,name,account_id,account_name,x,y,z,heading,world_id,gender,race,player_class) VALUES ("+id+",'Scratch"+id+"',"+account+",'scratch',0,0,0,0,210020000,'MALE','"+race+"','TEMPLAR')");
    if(id>=100)execute(c,"INSERT INTO playerbot_roster (player_id,account_id,ready) VALUES ("+id+","+account+",TRUE)");
   }
   companies(c);travellers(c);tickets(c);
  }
  System.out.println("OK: "+checks+" real isolated MariaDB Warden transaction checks");
 }
}
