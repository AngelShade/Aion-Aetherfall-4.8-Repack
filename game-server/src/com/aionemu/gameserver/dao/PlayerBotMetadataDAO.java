package com.aionemu.gameserver.dao;

import java.sql.*;
import java.util.*;
import com.alibaba.fastjson2.JSON;

/** Native prepared SQL, supplied transaction, and optimistic checkpoint ownership. */
public final class PlayerBotMetadataDAO {
 public record Row(long revision,Map<String,String> values) {public Row{values=Map.copyOf(values);}}
 public static void owner(Connection c,int account,int character)throws SQLException {
  if(account<=0 || character<=0)throw new SQLException("Invalid bot metadata owner");
  try(var s=c.prepareStatement("SELECT account_id FROM players WHERE id=?")) {
   s.setInt(1,character);try(var r=s.executeQuery()){if(!r.next() || r.getInt(1)!=account)throw new SQLException("Bot metadata character/account mismatch");}
  }
 }
 public static Row load(Connection c,int account,int character,String section)throws SQLException {
  owner(c,account,character);
  try(var s=c.prepareStatement("SELECT account_id,revision,settings FROM playerbot_metadata WHERE player_id=? AND section=?")) {
   s.setInt(1,character);s.setString(2,section);
   try(var r=s.executeQuery()) {
    if(!r.next())return null;
    if(r.getInt(1)!=account)throw new SQLException("Stored bot metadata owner mismatch");
    try {
     var json=JSON.parseObject(r.getString(3));var map=new TreeMap<String,String>();
     if(json==null)throw new IllegalArgumentException("Null metadata");
     for(var e:json.entrySet()){if(!(e.getValue() instanceof String value))throw new IllegalArgumentException("Non-string metadata");map.put(e.getKey(),value);}
     long revision=r.getLong(2);if(revision<=0)throw new IllegalArgumentException("Invalid metadata revision");
     return new Row(revision,map);
    }catch(RuntimeException e){throw new SQLException("Invalid stored bot metadata",e);}
   }
  }
 }
 public static void write(Connection c,int account,int character,String section,long revision,Map<String,String> values)throws SQLException {
  if(c.getAutoCommit())throw new SQLException("Bot metadata requires checkpoint transaction");
  owner(c,account,character);String payload=JSON.toJSONString(new TreeMap<>(values));
  if(revision==0) {
   try(var s=c.prepareStatement("INSERT INTO playerbot_metadata (player_id,account_id,section,revision,settings) VALUES (?,?,?,1,?)")) {
    s.setInt(1,character);s.setInt(2,account);s.setString(3,section);s.setString(4,payload);s.executeUpdate();
   }
  } else {
   try(var s=c.prepareStatement("UPDATE playerbot_metadata SET settings=?,revision=revision+1 WHERE player_id=? AND account_id=? AND section=? AND revision=?")) {
    s.setString(1,payload);s.setInt(2,character);s.setInt(3,account);s.setString(4,section);s.setLong(5,revision);
    if(s.executeUpdate()!=1)throw new SQLException("Bot metadata checkpoint revision changed");
   }
  }
 }
 private PlayerBotMetadataDAO() {}
}
