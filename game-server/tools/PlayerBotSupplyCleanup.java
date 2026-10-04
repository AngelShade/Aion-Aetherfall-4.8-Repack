package com.aionemu.gameserver.services.playerbot;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import com.aionemu.gameserver.dataholders.DataManager;

/** Revert only the earlier auto-generated, soul-bound special/test stacks on the four Temporary actors. */
public final class PlayerBotSupplyCleanup {
 static List<Map<String,String>> rows(Connection c,String sql)throws Exception {var out=new ArrayList<Map<String,String>>();try(var s=c.createStatement();var r=s.executeQuery(sql)){while(r.next()){var row=new LinkedHashMap<String,String>();for(int i=1;i<=r.getMetaData().getColumnCount();i++)row.put(r.getMetaData().getColumnLabel(i),r.getString(i));out.add(row);}}return out;}
 static String quote(String s){return s==null ? "NULL" : "'"+s.replace("\\","\\\\").replace("'","''")+"'";}
 static String restore(String table,Map<String,String> row){return "INSERT INTO "+table+" ("+String.join(",",row.keySet())+") VALUES ("+row.values().stream().map(PlayerBotSupplyCleanup::quote).collect(java.util.stream.Collectors.joining(","))+");";}
 public static void main(String[] args)throws Exception {
  Path server=Path.of(args[0]).toAbsolutePath(),backup=Path.of(args[1]).toAbsolutePath();if(!backup.startsWith(server.resolve("backups")) || Files.exists(backup))throw new IllegalArgumentException("Fresh deployment backup required");
  PlayerBotSuppliesCatalogCheck.main(new String[]{server.resolve("data/static_data").toString()});
  var config=new Properties();for(String rel:List.of("config/network/database.properties","config/mygs.properties"))try(var in=Files.newInputStream(server.resolve(rel))){config.load(in);}
  String url=config.getProperty("database.url").replace("${gameserver.timezone}",config.getProperty("gameserver.timezone","UTC"));
  try(var c=DriverManager.getConnection(url,config.getProperty("database.user"),config.getProperty("database.password"))) {
   c.setAutoCommit(false);try {
    var removed=new ArrayList<Map<String,String>>();var effects=new ArrayList<Map<String,String>>();var skills=new HashMap<Integer,Set<Integer>>();
    var candidates=rows(c,"SELECT i.* FROM inventory i JOIN players p ON p.id=i.item_owner JOIN playerbot_roster b ON b.player_id=p.id WHERE b.account_id=1 AND b.ready=TRUE AND p.name IN ('MagicDps','Healeru','Templaru','LeMuse') AND i.item_location=0 AND i.is_equipped=0 AND i.is_soul_bound=1 AND i.item_count BETWEEN 1 AND 20 AND i.enchant=0 AND i.tempering=0 AND i.fusioned_item=0 AND i.item_skin=i.item_id FOR UPDATE");
    for(var row:candidates) {
     var t=DataManager.ITEM_DATA.getItemTemplate(Integer.parseInt(row.get("item_id")));if(t==null || PlayerBotSupplyCatalog.ordinary(t) || !PlayerBotSupplies.safe(PlayerBotSupplies.skill(t)))continue;
     int unique=Integer.parseInt(row.get("item_unique_id")),owner=Integer.parseInt(row.get("item_owner"));
     try(var s=c.prepareStatement("SELECT COUNT(*) FROM item_stones WHERE item_unique_id=?")){s.setInt(1,unique);try(var r=s.executeQuery()){r.next();if(r.getInt(1)!=0)throw new IllegalStateException("Unexpected stones on generated supply "+unique);}}
     removed.add(row);skills.computeIfAbsent(owner,id->new HashSet<>()).add(t.getActions().getSkillUseAction().getSkillId());
    }
    for(var entry:skills.entrySet())for(int skill:entry.getValue())effects.addAll(rows(c,"SELECT * FROM player_effects WHERE player_id="+entry.getKey()+" AND skill_id="+skill+" FOR UPDATE"));
    Files.createDirectory(backup);Files.writeString(backup.resolve("inventory-before.json"),com.alibaba.fastjson2.JSON.toJSONString(removed));Files.writeString(backup.resolve("effects-before.json"),com.alibaba.fastjson2.JSON.toJSONString(effects));
    var undo=new ArrayList<String>();for(var row:removed)undo.add(restore("inventory",row));for(var row:effects)undo.add(restore("player_effects",row));Files.write(backup.resolve("restore.sql"),undo);
    for(var row:removed)try(var s=c.prepareStatement("DELETE FROM inventory WHERE item_unique_id=? AND item_owner=? AND item_id=? AND is_soul_bound=1 AND is_equipped=0 AND item_location=0 AND item_count=?")){s.setInt(1,Integer.parseInt(row.get("item_unique_id")));s.setInt(2,Integer.parseInt(row.get("item_owner")));s.setInt(3,Integer.parseInt(row.get("item_id")));s.setLong(4,Long.parseLong(row.get("item_count")));if(s.executeUpdate()!=1)throw new SQLException("Generated stack changed");}
    for(var entry:skills.entrySet())for(int skill:entry.getValue())try(var s=c.prepareStatement("DELETE FROM player_effects WHERE player_id=? AND skill_id=?")){s.setInt(1,entry.getKey());s.setInt(2,skill);s.executeUpdate();}
    c.commit();String result="OK: reverted "+removed.size()+" auto-generated special/test stacks and "+effects.size()+" associated saved effects on four dedicated Temporary Bots; human/alt, gear, skills, quests and shared storage untouched.";
    Files.writeString(backup.resolve("installed.json"),"{\"feature\":\"playerbot-supply-refinement-cleanup\",\"privateStacks\":"+removed.size()+",\"effects\":"+effects.size()+"}");Files.writeString(backup.resolve("runtime-verification.txt"),result);System.out.println(result);
   }catch(Exception e){c.rollback();throw e;}
  }
 }
}
