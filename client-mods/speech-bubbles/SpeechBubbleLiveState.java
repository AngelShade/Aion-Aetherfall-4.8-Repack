import java.lang.instrument.Instrumentation;
import java.nio.file.*;
import java.util.*;

/** Read-only diagnosis of negotiated styles on online players. */
public class SpeechBubbleLiveState {
 public static void agentmain(String output, Instrumentation instrumentation) throws Exception {
  ClassLoader loader=null;
  for(Class<?> c:instrumentation.getAllLoadedClasses())
   if(c.getName().equals("com.aionemu.gameserver.GameServer")){loader=c.getClassLoader();break;}
  if(loader==null)throw new IllegalStateException("Not GameServer");
  Class<?> world=Class.forName("com.aionemu.gameserver.world.World",false,loader);
  Object instance=world.getMethod("getInstance").invoke(null);
  Collection<?> players=(Collection<?>)world.getMethod("getAllPlayers").invoke(instance);
  StringBuilder result=new StringBuilder();
  for(Object player:players) {
   Object settings=player.getClass().getMethod("getPlayerSettings").invoke(player);
   Object con=player.getClass().getMethod("getClientConnection").invoke(player);
   result.append("Player ID=").append(player.getClass().getMethod("getObjectId").invoke(player))
    .append(" style=").append(settings.getClass().getMethod("getSpeechBubbleStyle").invoke(settings))
    .append(" negotiated=").append(con.getClass().getMethod("isSpeechBubbleClient").invoke(con)).append('\n');
  }
  Files.writeString(Path.of(output),result.toString());
 }
}
