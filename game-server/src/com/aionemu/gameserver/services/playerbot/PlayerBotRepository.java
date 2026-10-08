package com.aionemu.gameserver.services.playerbot;

import java.io.IOException;
import java.sql.*;
import java.util.*;
import com.alibaba.fastjson2.*;
import com.aionemu.commons.database.DatabaseFactory;
import com.aionemu.gameserver.dao.*;
import com.aionemu.gameserver.services.playerbot.PlayerBotRules.*;

/** Account-level explicit commands commit native documents; bot builds/progress stay in their existing DAOs. */
public final class PlayerBotRepository {
 static PlayerBotPresets.Document decode(int account,String text)throws IOException {
  try {
   if(text.length()>524288)throw new IllegalArgumentException("Saved party document too large");
   var json=JSON.parseObject(text);if(json.getIntValue("version")!=1 || json.getIntValue("account")!=account)throw new IllegalArgumentException("Saved party ownership/version mismatch");
   var saved=new HashSet<Integer>();for(Object id:json.getJSONArray("saved"))saved.add(Integer.parseInt(id.toString()));
   var presets=new ArrayList<PlayerBotPresets.Preset>();for(Object raw:json.getJSONArray("presets")){
    var p=(JSONObject)raw;var members=new ArrayList<PlayerBotPresets.Member>();
    for(Object item:p.getJSONArray("members")){
     var m=(JSONObject)item;String temporary=m.getString("temporary");if(!Set.of("true","false").contains(temporary))throw new IllegalArgumentException("Invalid companion kind");
     members.add(new PlayerBotPresets.Member(m.getIntValue("id"),m.getString("name"),Boolean.parseBoolean(temporary),Role.valueOf(m.getString("role")),Order.valueOf(m.getString("order"))));
    }presets.add(new PlayerBotPresets.Preset(p.getString("id"),p.getString("name"),members));
   }return new PlayerBotPresets.Document(account,saved,presets);
  }catch(RuntimeException e){throw new IOException("Invalid saved-party document; data preserved",e);}
 }
 static String encode(PlayerBotPresets.Document d){return JSON.toJSONString(Map.of("version",1,"account",d.account(),"saved",new TreeSet<>(d.saved()),"presets",d.presets()),JSONWriter.Feature.PrettyFormat);}
 public static void validateParties(Connection c,int account,String text)throws IOException,SQLException {
  var d=decode(account,text);PlayerBotRepositoryDAO.account(c,account);
  for(int id:d.saved())PlayerBotRepositoryDAO.roster(c,account,id);
  for(var p:d.presets())for(var m:p.members()){
   PlayerBotMetadataDAO.owner(c,account,m.id());
   if(m.temporary())PlayerBotRepositoryDAO.roster(c,account,m.id());
   else try(var s=c.prepareStatement("SELECT player_id FROM playerbot_roster WHERE player_id=?")){
    s.setInt(1,m.id());try(var r=s.executeQuery()){if(r.next())throw new SQLException("Owned-alt preset kind does not match native roster");}
   }
  }
 }
 public static void validateRemoved(Connection c,int account,int character,String text)throws IOException,SQLException {
  validateMarker(account,character,text);PlayerBotRepositoryDAO.roster(c,account,character);
 }
 static void validateMarker(int account,int character,String text)throws IOException {
  try{var j=JSON.parseObject(text);if(text.length()>4096 || j.getIntValue("version")!=1 || j.getIntValue("account")!=account || j.getIntValue("id")!=character || j.getString("name")==null || j.getLongValue("removedAt")<=0)throw new IllegalArgumentException("Invalid archive marker");}
  catch(RuntimeException e){throw new IOException("Archived companion ownership/version mismatch",e);}
 }
 static PlayerBotPresets.Document load(int account)throws IOException {
  try(var c=DatabaseFactory.getConnection()){
   String text=PlayerBotRepositoryDAO.parties(c,account);
   if(text==null)return new PlayerBotPresets.Document(account,Set.of(),List.of());
   validateParties(c,account,text);return PlayerBotRosterRemoval.prune(decode(account,text));
  }catch(SQLException e){throw new IOException("Cannot load native saved parties",e);}
 }
 static void save(PlayerBotPresets.Document document)throws IOException {
  String text=encode(document);
  try(var c=DatabaseFactory.getConnection()){
   c.setAutoCommit(false);try{validateParties(c,document.account(),text);PlayerBotRepositoryDAO.parties(c,document.account(),text);c.commit();}
   catch(SQLException|IOException|RuntimeException e){try{c.rollback();}catch(SQLException rollback){e.addSuppressed(rollback);}throw e;}
  }catch(SQLException e){throw new IOException("Cannot commit native saved parties",e);}
 }
 static boolean removed(int account,int character)throws IOException {
  try(var c=DatabaseFactory.getConnection()){
   String text=PlayerBotRepositoryDAO.removed(c,account,character);if(text==null)return false;
   validateRemoved(c,account,character,text);return true;
  }catch(SQLException e){throw new IOException("Cannot verify native removed-roster marker",e);}
 }
 static void archive(int account,int character,String name)throws IOException {
  String text=JSON.toJSONString(Map.of("version",1,"account",account,"id",character,"name",name,"removedAt",System.currentTimeMillis()));
  try(var c=DatabaseFactory.getConnection()){
   c.setAutoCommit(false);try{validateRemoved(c,account,character,text);String old=PlayerBotRepositoryDAO.removed(c,account,character);if(old==null)PlayerBotRepositoryDAO.removed(c,account,character,text);else validateRemoved(c,account,character,old);c.commit();}
   catch(SQLException|IOException|RuntimeException e){try{c.rollback();}catch(SQLException rollback){e.addSuppressed(rollback);}throw e;}
  }catch(SQLException e){throw new IOException("Cannot commit native removed-roster marker",e);}
 }
 private PlayerBotRepository(){}
}
