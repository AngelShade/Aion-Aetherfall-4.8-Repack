import java.lang.instrument.Instrumentation;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import com.sun.tools.attach.VirtualMachine;
import com.aionemu.commons.database.DatabaseFactory;
import com.aionemu.gameserver.GameServer;
import com.aionemu.gameserver.world.World;

/** Zero-player graceful stop and read-only post-start checks for the authorized pass installation. */
public class SeasonPassDeploymentAgent {
 public static void agentmain(String argument,Instrumentation instrumentation)throws Exception {
  if(Arrays.stream(instrumentation.getAllLoadedClasses()).noneMatch(c->c.getName().equals("com.aionemu.gameserver.GameServer")))throw new IllegalStateException("Expected Aion GameServer.");
  String[] args=argument.split("\\|",-1);if(args.length!=2)throw new IllegalArgumentException("mode|receipt");
  Path receipt=Path.of(args[1]);Files.writeString(receipt,"START: "+args[0]+"\n",StandardOpenOption.CREATE_NEW);
  try{
   int online=World.getInstance().getAllPlayers().size();
   try(Connection connection=DatabaseFactory.getConnection();Statement statement=connection.createStatement();ResultSet rows=statement.executeQuery("SELECT COUNT(*) FROM players WHERE online=1")){
    rows.next();int databaseOnline=rows.getInt(1);
    Files.writeString(receipt,"PLAYERS: world="+online+" database="+databaseOnline+"\n",StandardOpenOption.APPEND);
    if(args[0].equals("stop") && (online!=0 || databaseOnline!=0))throw new IllegalStateException("Wait for zero players before deployment.");
   }
   if(args[0].equals("stop")){
    Files.writeString(receipt,"OK: zero players; requested native graceful shutdown, exit 0, delay 5.\n",StandardOpenOption.APPEND);
    GameServer.initShutdown(0,5);return;
   }
   Class<?> service=Class.forName("com.aionemu.gameserver.services.SeasonPassService");
   var ready=service.getDeclaredField("ready");ready.setAccessible(true);
   if(!ready.getBoolean(null))throw new IllegalStateException("Season Pass is not ready.");
   var season=service.getDeclaredField("season");season.setAccessible(true);Object configured=season.get(null);
   Files.writeString(receipt,"READY: "+configured+"\n",StandardOpenOption.APPEND);
   try(Connection connection=DatabaseFactory.getConnection();PreparedStatement statement=connection.prepareStatement("SELECT i.item_id,i.item_count,i.item_location FROM mail m JOIN inventory i ON i.item_unique_id=m.attached_item_id AND i.item_owner=m.mail_recipient_id WHERE m.mail_recipient_id=9403 AND m.mail_message LIKE ?")){
    statement.setString(1,"[BOXTEST-20261003-16]%");Set<Integer> ids=new HashSet<>();
    try(ResultSet rows=statement.executeQuery()){while(rows.next()){
     if(rows.getLong(2)!=1 || rows.getInt(3)!=127 || !ids.add(rows.getInt(1)))throw new IllegalStateException("Test mail needs inspection.");
    }}
    Files.writeString(receipt,"BABY TEST MAIL: persisted attachments="+ids.size()+" IDs="+new TreeSet<>(ids)+"\n",StandardOpenOption.APPEND);
   }
   Files.writeString(receipt,"SUCCESS: running Season Pass ready; read-only deployment verification.\n",StandardOpenOption.APPEND);
  }catch(Throwable error){Files.writeString(receipt,"ERROR: "+error+"\n",StandardOpenOption.APPEND);throw error;}
 }
 public static void main(String[] args)throws Exception {
  VirtualMachine vm=VirtualMachine.attach(args[0]);
  try{vm.loadAgent(Path.of(args[1]).toAbsolutePath().toString(),args[2]+"|"+Path.of(args[3]).toAbsolutePath());}finally{vm.detach();}
 }
}
