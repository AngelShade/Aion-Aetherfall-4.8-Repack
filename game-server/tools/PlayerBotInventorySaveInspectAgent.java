import java.lang.instrument.Instrumentation;
import java.nio.file.*;
import java.util.*;
import com.sun.tools.attach.VirtualMachine;
import com.aionemu.commons.database.DatabaseFactory;
import com.aionemu.gameserver.world.World;
import com.aionemu.gameserver.services.playerbot.PlayerBotService;
/** Read-only comparison. Never changes item state, database rows or companions. */
public class PlayerBotInventorySaveInspectAgent {
 public static void agentmain(String argument,Instrumentation ignored)throws Exception {
  var lines=new ArrayList<String>();
  try(var connection=DatabaseFactory.getConnection();var query=connection.prepareStatement("SELECT item_owner,item_id,item_location FROM inventory WHERE item_unique_id=?")) {
   for(var owner:World.getInstance().getAllPlayers())if(!owner.isPlayerBot())for(var session:PlayerBotService.getInstance().companions(owner))synchronized(session) {
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
