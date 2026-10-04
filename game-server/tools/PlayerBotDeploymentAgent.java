import java.lang.instrument.Instrumentation;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import com.sun.tools.attach.VirtualMachine;
import com.aionemu.commons.database.DatabaseFactory;
import com.aionemu.gameserver.GameServer;
import com.aionemu.gameserver.world.World;

/** Authorized install: read-only status/runtime checks and zero-player native graceful shutdown. */
public class PlayerBotDeploymentAgent {
 public static void agentmain(String argument, Instrumentation instrumentation) throws Exception {
  if (Arrays.stream(instrumentation.getAllLoadedClasses()).noneMatch(c -> c.getName().equals("com.aionemu.gameserver.GameServer"))) throw new IllegalStateException("Expected Aion GameServer");
  String[] args = argument.split("\\|",-1); if (args.length != 2 || !Set.of("inspect","stop","verify").contains(args[0])) throw new IllegalArgumentException("mode|receipt");
  Path receipt = Path.of(args[1]); Files.writeString(receipt,"START: " + args[0] + "\n",StandardOpenOption.CREATE_NEW);
  try {
   Files.writeString(receipt,"SERVER: " + Path.of(System.getProperty("user.dir")).toRealPath() + "\nJAR: " + GameServer.class.getProtectionDomain().getCodeSource().getLocation() + "\n",StandardOpenOption.APPEND);
   int online = World.getInstance().getAllPlayers().size();
   try (Connection connection = DatabaseFactory.getConnection(); Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM players WHERE online=1")) {
    rows.next(); int databaseOnline = rows.getInt(1);
    Files.writeString(receipt,"PLAYERS: world=" + online + " database=" + databaseOnline + "\n",StandardOpenOption.APPEND);
    if (args[0].equals("stop") && (online != 0 || databaseOnline != 0)) throw new IllegalStateException("Wait for all characters to finish normal logout before deployment");
   }
   if (args[0].equals("stop")) {
    Files.writeString(receipt,"OK: zero players; requested native graceful shutdown, exit 0, delay 5.\n",StandardOpenOption.APPEND);
    GameServer.initShutdown(0,5); return;
   }
   if (args[0].equals("verify")) {
    Class<?> config = Class.forName("com.aionemu.gameserver.configs.main.PlayerBotConfig");
    if (!config.getField("ENABLED").getBoolean(null)) throw new IllegalStateException("Companions disabled");
    Class<?> service = Class.forName("com.aionemu.gameserver.services.playerbot.PlayerBotService");
    if (service.getMethod("getInstance").invoke(null) == null) throw new IllegalStateException("Companion service unavailable");
    Class<?> roster = Class.forName("com.aionemu.gameserver.services.playerbot.PlayerBotRoster");
    if (!(Boolean) roster.getMethod("available").invoke(null)) throw new IllegalStateException("Companion roster schema unavailable");
    Class.forName("com.aionemu.gameserver.services.PlayerBotHttpService");
    Class<?> commands = Class.forName("com.aionemu.gameserver.utils.chathandlers.ChatProcessor");
    Object commandProcessor = commands.getMethod("getInstance").invoke(null);
    if (!(Boolean) commands.getMethod("isCommandExists",String.class).invoke(commandProcessor,".bot")) throw new IllegalStateException(".bot command was not loaded");
    var configClasses = Class.forName("com.aionemu.gameserver.configs.Config").getMethod("getClasses").invoke(null);
    if (!((List<?>)configClasses).contains(config)) throw new IllegalStateException("Companion config is not registered");
    Class.forName("com.aionemu.gameserver.services.SeasonPassService");
    Files.writeString(receipt,"RUNTIME: companion config/service/roster/HTTP and .bot command loaded; existing Season Pass present.\n",StandardOpenOption.APPEND);
   }
   Files.writeString(receipt,"SUCCESS: " + args[0] + " (no character rows changed).\n",StandardOpenOption.APPEND);
  } catch (Throwable error) {
   Files.writeString(receipt,"ERROR: " + error + "\n",StandardOpenOption.APPEND); throw error;
  }
 }
 public static void main(String[] args) throws Exception {
  VirtualMachine vm = VirtualMachine.attach(args[0]);
  try { vm.loadAgent(Path.of(args[1]).toAbsolutePath().toString(),args[2]+"|"+Path.of(args[3]).toAbsolutePath()); } finally { vm.detach(); }
 }
}
