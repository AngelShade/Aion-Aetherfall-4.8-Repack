import java.lang.instrument.Instrumentation;
import java.lang.reflect.InvocationTargetException;

/** Offline entrypoint checks: no attach, world actors, SQL connection or ID allocation. */
public final class PlayerBotFixtureGuardCheck {
 public static void main(String[] args) throws Exception {
  int checks=0;
  for(String name:new String[]{"PlayerBotPresetPersistenceCheckAgent","PlayerBotPresetPersistenceCheckAgent2","PlayerBotPresetPersistenceCheckAgent4"}) {
   Class<?> agent=Class.forName(name);
   for(String entry:new String[]{"main","agentmain"}) {
    try {
     if(entry.equals("main"))agent.getMethod(entry,String[].class).invoke(null,(Object)new String[0]);
     else agent.getMethod(entry,String.class,Instrumentation.class).invoke(null,null,null);
     throw new AssertionError("Retired fixture remained executable: "+name+"."+entry);
    } catch(InvocationTargetException result) {
     if(!(result.getCause() instanceof IllegalStateException reason) || !reason.getMessage().startsWith("Retired")
      || !reason.getMessage().contains("use PlayerBotPresetPersistenceCheckAgent5"))throw result;
     checks++;
    }
   }
  }
  System.out.println("OK: "+checks+" rebuilt retired fixture CLI/agent entrypoints reject before native/database/ID access; do not run historical compiled JARs");
 }
}
