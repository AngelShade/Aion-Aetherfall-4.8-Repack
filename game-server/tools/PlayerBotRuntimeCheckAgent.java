import java.lang.instrument.Instrumentation;
import java.nio.file.*;
import com.sun.tools.attach.VirtualMachine;
import com.aionemu.gameserver.configs.Config;
import com.aionemu.gameserver.configs.main.PlayerBotConfig;
import com.aionemu.gameserver.services.playerbot.*;
import com.aionemu.gameserver.utils.chathandlers.ChatProcessor;

/** Read-only native verification: command aliases include their chat prefix. */
public class PlayerBotRuntimeCheckAgent {
 public static void agentmain(String argument,Instrumentation ignored) throws Exception {
  Path receipt=Path.of(argument);Files.writeString(receipt,"START: installed companion runtime verification\n",StandardOpenOption.CREATE_NEW);
  try {
   if (!Config.getClasses().contains(PlayerBotConfig.class) || !PlayerBotConfig.ENABLED || !PlayerBotConfig.GENERATED_ENABLED) throw new IllegalStateException("Companion configuration not enabled");
   if (PlayerBotService.getInstance()==null || !PlayerBotRoster.available()) throw new IllegalStateException("Companion service or roster unavailable");
   var command=ChatProcessor.getInstance().getCommandList().stream().filter(c->c.getAliasWithPrefix().equals(".bot")).findFirst().orElseThrow(()->new IllegalStateException(".bot not loaded"));
   if(command.getLevel()!=0) throw new IllegalStateException(".bot must be available to normal players");
   Files.writeString(receipt,"OK: companion config registered/enabled; both generated modes enabled; roster table available; service and normal-player .bot command loaded.\nSUCCESS: native runtime verified; no player data changed.\n",StandardOpenOption.APPEND);
  } catch(Throwable error) { Files.writeString(receipt,"ERROR: "+error+"\n",StandardOpenOption.APPEND);throw error; }
 }
 public static void main(String[] args) throws Exception {
  VirtualMachine vm=VirtualMachine.attach(args[0]);try { vm.loadAgent(Path.of(args[1]).toAbsolutePath().toString(),Path.of(args[2]).toAbsolutePath().toString()); } finally { vm.detach(); }
 }
}
