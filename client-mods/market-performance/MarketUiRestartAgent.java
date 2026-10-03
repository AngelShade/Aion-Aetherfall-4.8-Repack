import java.lang.instrument.Instrumentation;
import java.nio.file.*;
import com.sun.tools.attach.VirtualMachine;
import com.aionemu.gameserver.GameServer;
import com.aionemu.gameserver.world.World;

/** Use native shutdown/save; refuse to interrupt anyone beyond the user's Baby. */
public final class MarketUiRestartAgent {
    public static void agentmain(String argument,Instrumentation instrumentation)throws Exception {
        Path receipt=Path.of(argument);Files.writeString(receipt,"START: authorized market/shop UI restart\n",StandardOpenOption.CREATE_NEW);
        try {
            var type=Class.forName("com.aionemu.gameserver.services.MarketplaceService");
            var page=type.getDeclaredMethod("page",String.class);page.setAccessible(true);
            String html=(String)page.invoke(null,"FIXTURE-ONLY");
            if(!html.contains("marketplace.css?v=15")||!html.contains("marketplace.js?v=2'"))throw new IllegalStateException("Unexpected live UI versions");
            var players=World.getInstance().getAllPlayers();
            if(players.stream().anyMatch(p->p.getObjectId()!=9403))throw new IllegalStateException("Other players are online; restart refused");
            if(GameServer.isShutdownScheduled())throw new IllegalStateException("A shutdown is already scheduled");
            Files.writeString(receipt,"OK: world players="+players.size()+"; native graceful shutdown requested with exit 0, delay 15.\n",StandardOpenOption.APPEND);
            GameServer.initShutdown(0,15);
        } catch(Throwable error) {Files.writeString(receipt,"ERROR: "+error+"\n",StandardOpenOption.APPEND);throw error;}
    }
    public static void main(String[] args)throws Exception {
        VirtualMachine vm=VirtualMachine.attach(args[0]);
        try{vm.loadAgent(Path.of(args[1]).toAbsolutePath().toString(),Path.of(args[2]).toAbsolutePath().toString());}finally{vm.detach();}
    }
}
