import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** Read-only evidence for a reported failed generated companion. Never prints credentials. */
public class PlayerBotCreationInspect {
 public static void main(String[] args)throws Exception {
  Path root=Path.of(args[0]);Properties config=new Properties();
  for(String relative:List.of("config/network/database.properties","config/mygs.properties"))try(var input=Files.newInputStream(root.resolve(relative))){config.load(input);}
  String url=config.getProperty("database.url").replace("${gameserver.timezone}",config.getProperty("gameserver.timezone","UTC"));
  try(Connection c=DriverManager.getConnection(url,config.getProperty("database.user"),config.getProperty("database.password"))) {
   int id=Integer.parseInt(args[1]);
   try(var s=c.prepareStatement("SELECT b.player_id,b.account_id,b.ready,p.name,p.exp,p.player_class FROM playerbot_roster b LEFT JOIN players p ON p.id=b.player_id WHERE b.player_id=?")) {
    s.setInt(1,id);try(var r=s.executeQuery()){while(r.next())System.out.println("PENDING: id="+r.getInt(1)+" account="+r.getInt(2)+" ready="+r.getBoolean(3)+" playerName="+r.getString(4)+" exp="+r.getObject(5)+" class="+r.getString(6));}
   }
   for(String query:List.of("SELECT COUNT(*) FROM inventory WHERE item_owner=?","SELECT COUNT(*) FROM player_skills WHERE player_id=?","SELECT COUNT(*) FROM player_recipes WHERE player_id=?"))try(var s=c.prepareStatement(query)){s.setInt(1,id);try(var r=s.executeQuery()){r.next();System.out.println(query.split(" FROM ")[1].split(" WHERE")[0]+" rows="+r.getInt(1));}}
   try(var s=c.createStatement();var r=s.executeQuery("SELECT COUNT(*) FROM players WHERE online=1")){r.next();System.out.println("Online human character rows="+r.getInt(1));}
  }
 }
}
