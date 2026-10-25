/* PlayerbotGuildMgr::{AssignToGuild,CreateGuild,ValidateGuildCache},
 * mod-playerbots 037c01418b5d01506917a3db9b44fd56ac5f965c, GPL-2.0-or-later.
 * Native Aion legion/member/emblem/history transaction; see AUTHORS.md. */
package com.aionemu.gameserver.services.playerbot;

import java.sql.*;
import java.util.*;
import com.aionemu.gameserver.configs.main.LegionConfig;
import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.model.team.legion.LegionRank;

/** No shadow roster: native membership and leadership are authoritative, including offline members. */
final class PlayerBotLegionRepository {
 record Guild(int id,String name,Race race,int level,int members,boolean protectedMembers,boolean disbanding) {
  boolean eligible(Race requested,int maximum){return race==requested && !protectedMembers && !disbanding && members>0 && members<Math.min(maximum,capacity(level));}
 }
 record Snapshot(List<Guild> guilds,Set<String> names) {
  Snapshot{guilds=List.copyOf(guilds);names=Set.copyOf(names);}
 }
 record Request(int account,int character,Race race,String characterName,int target,int allocated,String name,LegionRank rank,
                int emblem,byte red,byte green,byte blue,int maximumGuilds,int maximumMembers) {}
 record Assignment(int legion,boolean created,boolean changed) {}
 static final class TransactionFailure extends SQLException {
  private static final long serialVersionUID=1L;
  final boolean allocationReusable;
  TransactionFailure(Throwable cause,boolean reusable){super("Native generated legion transaction failed; SQL membership remains authoritative",cause);allocationReusable=reusable;}
 }
 static Assignment commit(Connection c,Request request)throws SQLException {
  boolean commitAttempted=false;
  try{c.setAutoCommit(false);var result=assign(c,request);commitAttempted=true;c.commit();return result;}
  catch(SQLException|RuntimeException e) {
   boolean reusable=false;try{c.rollback();reusable=!commitAttempted;}catch(SQLException rollback){e.addSuppressed(rollback);}
   throw new TransactionFailure(e,reusable);
  }
 }
 static int membership(Connection c,int character)throws SQLException {
  try(var s=c.prepareStatement("SELECT legion_id FROM legion_members WHERE player_id=?")){s.setInt(1,character);try(var r=s.executeQuery()){return r.next()?r.getInt(1):0;}}
 }
 static int capacity(int level){return switch(level){case 1->LegionConfig.LEGION_LEVEL1_MAX_MEMBERS;case 2->LegionConfig.LEGION_LEVEL2_MAX_MEMBERS;
  case 3->LegionConfig.LEGION_LEVEL3_MAX_MEMBERS;case 4->LegionConfig.LEGION_LEVEL4_MAX_MEMBERS;case 5->LegionConfig.LEGION_LEVEL5_MAX_MEMBERS;
  case 6->LegionConfig.LEGION_LEVEL6_MAX_MEMBERS;case 7->LegionConfig.LEGION_LEVEL7_MAX_MEMBERS;case 8->LegionConfig.LEGION_LEVEL8_MAX_MEMBERS;default->0;};}
 static Snapshot snapshot(Connection c)throws SQLException {
  var guilds=new ArrayList<Guild>();var names=new HashSet<String>();
  try(var s=c.prepareStatement("SELECT name FROM legions");var r=s.executeQuery()){while(r.next())names.add(r.getString(1).toLowerCase(Locale.ROOT));}
  String query="SELECT l.id,l.name,l.level,l.disband_time,h.race,COUNT(m.player_id),"
   +"SUM(CASE WHEN p.account_id NOT BETWEEN -1023999 AND -1000000 OR b.player_id IS NULL OR b.ready=FALSE OR b.account_id<>p.account_id OR x.player_id IS NOT NULL OR p.race<>h.race THEN 1 ELSE 0 END) "
   +"FROM legions l JOIN legion_members leader ON leader.legion_id=l.id AND leader.rank='BRIGADE_GENERAL' "
   +"JOIN players h ON h.id=leader.player_id JOIN legion_members m ON m.legion_id=l.id JOIN players p ON p.id=m.player_id "
   +"LEFT JOIN playerbot_roster b ON b.player_id=p.id LEFT JOIN playerbot_removed x ON x.player_id=p.id WHERE h.account_id BETWEEN -1023999 AND -1000000 "
   +"GROUP BY l.id,l.name,l.level,l.disband_time,h.race ORDER BY l.id";
  try(var s=c.prepareStatement(query);var r=s.executeQuery()) {
   while(r.next())guilds.add(new Guild(r.getInt(1),r.getString(2),Race.valueOf(r.getString(5)),r.getInt(3),r.getInt(6),r.getInt(7)>0,r.getInt(4)!=0));
  }
  return new Snapshot(guilds,names);
 }
 static Assignment assign(Connection c,Request request)throws SQLException {
  if(c.getAutoCommit() || !PlayerBotPopulationPolicy.serverAccount(request.account()) || request.character()<=0
   || request.maximumGuilds()<1 || request.maximumGuilds()>100 || request.maximumMembers()<1 || request.maximumMembers()>240
   || request.race()==null || request.rank()==null || request.target()<0 || request.target()==0 && (request.allocated()<=0 || request.name()==null || request.emblem()<0 || request.emblem()>49)
   || request.target()>0 && request.rank()==LegionRank.BRIGADE_GENERAL)throw new SQLException("Invalid generated legion transaction");
  // Lock native character custody before examining membership or creating shared state.
  try(var s=c.prepareStatement("SELECT p.account_id,p.race,p.name,b.account_id,b.ready,x.player_id FROM players p JOIN playerbot_roster b ON b.player_id=p.id LEFT JOIN playerbot_removed x ON x.player_id=p.id WHERE p.id=? FOR UPDATE")) {
   s.setInt(1,request.character());try(var r=s.executeQuery()) {
    if(!r.next() || r.getInt(1)!=request.account() || r.getInt(4)!=request.account() || !r.getBoolean(5)
     || r.getObject(6)!=null || !r.getString(2).equals(request.race().name()) || !r.getString(3).equals(request.characterName()))throw new SQLException("Generated legion actor custody changed");
   }
  }
  try(var s=c.prepareStatement("SELECT legion_id FROM legion_members WHERE player_id=? FOR UPDATE")) {
   s.setInt(1,request.character());try(var r=s.executeQuery()){if(r.next())return new Assignment(r.getInt(1),false,false);}
  }
  int id=request.target();boolean created=id==0;
  if(created) {
   try(var s=c.prepareStatement("SELECT COUNT(*) FROM legion_members m JOIN players p ON p.id=m.player_id WHERE m.rank='BRIGADE_GENERAL' AND p.account_id BETWEEN -1023999 AND -1000000");var r=s.executeQuery()) {
    if(!r.next() || r.getInt(1)>=request.maximumGuilds())return new Assignment(0,false,false);
   }
   if(capacity(1)<1)return new Assignment(0,false,false);
   id=request.allocated();
   try(var s=c.prepareStatement("INSERT INTO legions (id,name) VALUES (?,?)")){s.setInt(1,id);s.setString(2,request.name());if(s.executeUpdate()!=1)throw new SQLException("Generated legion insert failed");}
   try(var s=c.prepareStatement("INSERT INTO legion_emblems (legion_id,emblem_id,color_a,color_r,color_g,color_b,emblem_type,emblem_data) VALUES (?,?,?,?,?,?,'DEFAULT',?)")) {
    s.setInt(1,id);s.setInt(2,request.emblem());s.setByte(3,(byte)255);s.setByte(4,request.red());s.setByte(5,request.green());s.setByte(6,request.blue());s.setBytes(7,new byte[0]);
    if(s.executeUpdate()!=1)throw new SQLException("Generated legion emblem insert failed");
   }
  }else {
   int level;
   try(var s=c.prepareStatement("SELECT level,disband_time FROM legions WHERE id=? FOR UPDATE")) {
    s.setInt(1,id);try(var r=s.executeQuery()){if(!r.next() || r.getInt(2)!=0)return new Assignment(0,false,false);level=r.getInt(1);}
   }
   int count=0,leaders=0;
   try(var s=c.prepareStatement("SELECT p.account_id,p.race,m.rank,b.account_id,b.ready,x.player_id FROM legion_members m JOIN players p ON p.id=m.player_id LEFT JOIN playerbot_roster b ON b.player_id=p.id LEFT JOIN playerbot_removed x ON x.player_id=p.id WHERE m.legion_id=? FOR UPDATE")) {
    s.setInt(1,id);try(var r=s.executeQuery()){while(r.next()) {
     count++;if(!PlayerBotPopulationPolicy.serverAccount(r.getInt(1)) || r.getInt(4)!=r.getInt(1) || !r.getBoolean(5) || r.getObject(6)!=null || !request.race().name().equals(r.getString(2)))return new Assignment(0,false,false);
     if(LegionRank.BRIGADE_GENERAL.name().equals(r.getString(3)))leaders++;
    }}
   }
   if(count==0 || leaders!=1 || count>=Math.min(request.maximumMembers(),capacity(level)))return new Assignment(0,false,false);
  }
  try(var s=c.prepareStatement("INSERT INTO legion_members (legion_id,player_id,`rank`) VALUES (?,?,?)")) {
   s.setInt(1,id);s.setInt(2,request.character());s.setString(3,(created?LegionRank.BRIGADE_GENERAL:request.rank()).name());
   if(s.executeUpdate()!=1)throw new SQLException("Generated legion member insert failed");
  }
  if(created)history(c,id,"CREATE","");history(c,id,"JOIN",request.characterName());
  return new Assignment(id,created,true);
 }
 private static void history(Connection c,int id,String action,String name)throws SQLException {
  try(var s=c.prepareStatement("INSERT INTO legion_history (legion_id,history_type,name,description) VALUES (?,?,?,'')")) {
   s.setInt(1,id);s.setString(2,action);s.setString(3,name);if(s.executeUpdate()!=1)throw new SQLException("Generated legion history insert failed");
  }
 }
 private PlayerBotLegionRepository(){}
}
