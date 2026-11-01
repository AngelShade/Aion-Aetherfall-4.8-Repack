package com.aionemu.gameserver.services.playerbot;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.TreeMap;
import com.aionemu.commons.utils.Json;

/** What an account has really done with companions, for Journey chapters. Read-only: tickets and bots belong to the account. */
public final class PlayerBotJourneyEvidence {
 public record Facts(long tickets,long recruits,long saved,long helped,long runs,long promoted) {}
 private PlayerBotJourneyEvidence() {}

 public static Facts read(Connection c,int account)throws SQLException {
  long tickets,runs;
  try(var s=prepare(c,"SELECT COALESCE(SUM(ticket_delta),0),COUNT(*) FROM playerbot_ticket_receipts WHERE account_id=? AND kind='INSTANCE'",account);var r=s.executeQuery()) {
   r.next();tickets=r.getLong(1);runs=r.getLong(2);
  }
  long recruits=count(c,"SELECT COUNT(*) FROM playerbot_recruitment_operations WHERE account_id=? AND state='COMPLETED'",account);
  long saved=count(c,"SELECT COUNT(*) FROM playerbot_metadata WHERE account_id=? AND section='loadouts' AND settings LIKE '%\"ref.%'",account);
  long helped=0,promoted=0;
  try(var s=prepare(c,"SELECT settings FROM playerbot_metadata WHERE account_id=? AND section='quest-practice' AND settings LIKE '%\"help\"%'",account);var r=s.executeQuery()) {
   while(r.next())for(String claim:values(r.getString(1)).getOrDefault("help","").split(","))if(!claim.isBlank())helped++;
  }
  try(var s=prepare(c,"SELECT settings FROM playerbot_metadata WHERE account_id=? AND section='growth' AND settings LIKE '%GACHA%'",account);var r=s.executeQuery()) {
   while(r.next()) {
    var profile=PlayerBotGrowth.decode(values(r.getString(1)));
    if(profile.traitsAssigned() && profile.combatRank().ordinal()>profile.initialCombatRank().ordinal())promoted++;
   }
  }
  return new Facts(tickets,recruits,saved,helped,runs,promoted);
 }
 private static PreparedStatement prepare(Connection c,String sql,int account)throws SQLException {
  var s=c.prepareStatement(sql);s.setInt(1,account);return s;
 }
 private static long count(Connection c,String sql,int account)throws SQLException {
  try(var s=prepare(c,sql,account);ResultSet r=s.executeQuery()){r.next();return r.getLong(1);}
 }
 private static TreeMap<String,String> values(String settings) {
  var map=new TreeMap<String,String>();
  for(var e:Json.read(settings).entrySet())map.put(e.getKey(),e.getValue().getAsString());
  return map;
 }
}
