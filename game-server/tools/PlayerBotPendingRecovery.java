import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** Remove only proven empty, failed creation reservations; never deletes a saved character. */
public class PlayerBotPendingRecovery {
 public static void main(String[] args)throws Exception {
  Path server=Path.of(args[0]).toAbsolutePath().normalize(),backup=Path.of(args[1]).toAbsolutePath().normalize();
  if(!backup.startsWith(server.resolve("backups")) || Files.exists(backup))throw new IllegalArgumentException("Use a fresh deployment backup directory");
  Properties config=new Properties();for(String relative:List.of("config/network/database.properties","config/mygs.properties"))try(var input=Files.newInputStream(server.resolve(relative))){config.load(input);}
  String url=config.getProperty("database.url").replace("${gameserver.timezone}",config.getProperty("gameserver.timezone","UTC"));
  List<String> originals=new ArrayList<>(),undo=new ArrayList<>();
  try(Connection connection=DriverManager.getConnection(url,config.getProperty("database.user"),config.getProperty("database.password"))) {
   connection.setAutoCommit(false);
   try {
    for(int id:new int[]{15835,20529}) {
     try(var s=connection.prepareStatement("SELECT account_id,ready,created_at FROM playerbot_roster WHERE player_id=? FOR UPDATE")) {
      s.setInt(1,id);try(var r=s.executeQuery()) {
       if(!r.next())continue;
       int account=r.getInt(1);boolean ready=r.getBoolean(2);String created=r.getTimestamp(3).toString();
       if(account!=1 || ready)throw new IllegalStateException("Reported reservation no longer pending for the expected account");
       originals.add("{\"id\":"+id+",\"account\":"+account+",\"ready\":false,\"createdAt\":\""+created+"\"}");
       undo.add("INSERT INTO playerbot_roster (player_id,account_id,ready,created_at) VALUES ("+id+","+account+",FALSE,'"+created+"');");
      }
     }
     for(String query:List.of("SELECT COUNT(*) FROM players WHERE id=?","SELECT COUNT(*) FROM inventory WHERE item_owner=?","SELECT COUNT(*) FROM player_skills WHERE player_id=?","SELECT COUNT(*) FROM player_recipes WHERE player_id=?"))try(var s=connection.prepareStatement(query)){s.setInt(1,id);try(var r=s.executeQuery()){r.next();if(r.getInt(1)!=0)throw new IllegalStateException("Partial character data exists; refusing to clear id "+id);}}
    }
    Files.createDirectories(backup);
    Files.writeString(backup.resolve("manifest.json"),"{\"feature\":\"playerbot-empty-pending-recovery\",\"deployment\":\""+server.toString().replace("\\","\\\\")+"\",\"rows\":["+String.join(",",originals)+"]}");
    Files.write(backup.resolve("restore-reservations.sql"),undo);
    for(String original:originals) {
     int id=Integer.parseInt(original.split(":")[1].split(",")[0]);
     try(var s=connection.prepareStatement("DELETE FROM playerbot_roster WHERE player_id=? AND account_id=1 AND ready=FALSE AND NOT EXISTS (SELECT 1 FROM players WHERE id=?)")){s.setInt(1,id);s.setInt(2,id);if(s.executeUpdate()!=1)throw new SQLException("Pending reservation changed");}
    }
    connection.commit();Files.writeString(backup.resolve("installed.json"),"{\"removedEmptyReservations\":"+originals.size()+",\"savedCharactersChanged\":0}");
    System.out.println("OK: cleared "+originals.size()+" empty failed reservations (15835/20529); no saved character, equipment, skills or recipes changed. Backup: "+backup);
   } catch(Exception error){connection.rollback();throw error;}
  }
 }
}
