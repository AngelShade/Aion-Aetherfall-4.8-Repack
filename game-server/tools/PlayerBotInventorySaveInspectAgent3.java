import java.lang.instrument.Instrumentation;
import java.nio.file.*;
import java.util.*;
import com.sun.tools.attach.VirtualMachine;
import com.aionemu.commons.database.DatabaseFactory;
import com.aionemu.gameserver.world.World;
import com.aionemu.gameserver.services.playerbot.PlayerBotService;
/** Read-only comparison. Never changes item state, database rows or companions. */
public class PlayerBotInventorySaveInspectAgent3 {
 public static void agentmain(String argument,Instrumentation ignored)throws Exception {
  var lines=new ArrayList<String>();
  try(var connection=DatabaseFactory.getConnection();var query=connection.prepareStatement("SELECT item_owner,item_id,item_location FROM inventory WHERE item_unique_id=?")) {
   var f=PlayerBotService.class.getDeclaredField("sessions");f.setAccessible(true);var sessions=(Map<Integer,com.aionemu.gameserver.services.playerbot.PlayerBotSession>)f.get(PlayerBotService.getInstance());lines.add("SESSION_COUNT "+sessions.size());try(var s=connection.prepareStatement("SELECT id,name,account_id FROM players WHERE id=191098");var r=s.executeQuery()){lines.add("ROW_OWNER "+(r.next()? r.getInt(1)+" name="+r.getString(2)+" account="+r.getInt(3):"missing"));}query.setInt(1,191267);try(var row=query.executeQuery()){lines.add("EXISTING 191267 "+(row.next()? "owner="+row.getInt(1)+" template="+row.getInt(2)+" location="+row.getInt(3):"missing"));}for(var session:List.copyOf(sessions.values()))synchronized(session) {
    var bot=session.bot();lines.add("BOT "+bot.getName()+" id="+bot.getObjectId());
    for(var item:bot.getAllItems())if(item.getPersistentState()==com.aionemu.gameserver.model.gameobjects.Persistable.PersistentState.NEW) {
     query.setInt(1,item.getObjectId());try(var row=query.executeQuery()){
      lines.add("NEW item="+item.getObjectId()+" template="+item.getItemId()+" location="+item.getItemLocation()+" count="+item.getItemCount()+" database="+(row.next() ? "owner="+row.getInt(1)+" template="+row.getInt(2)+" location="+row.getInt(3) : "missing"));
     }
    }
   }
  }
  Files.write(Path.of(argument),lines,StandardOpenOption.CREATE_NEW);
 }
 public static void main(String[] args)throws Exception{var vm=VirtualMachine.attach(args[0]);try{vm.loadAgent(Path.of(args[1]).toAbsolutePath().toString(),Path.of(args[2]).toAbsolutePath().toString());}finally{vm.detach();}}
}


