import java.lang.instrument.Instrumentation;
import java.nio.file.*;
import java.util.*;
import com.aionemu.gameserver.services.CentralMarketService;
import com.aionemu.gameserver.world.World;

/** Read-only browsing timings on a connected character; no sessions or account data exported. */
public class CentralMarketTimingAgent {
 public static void agentmain(String output, Instrumentation instrumentation) {
  Thread probe=new Thread(() -> {
   try {
    var player=World.getInstance().getAllPlayers().stream().filter(p->p.isOnline()).findFirst().orElse(null);
    StringBuilder result=new StringBuilder();
    var field=CentralMarketService.class.getDeclaredField("LOCK");field.setAccessible(true);Object lock=field.get(null);
    long maximum=0,total=0;
    for(int sample=0;sample<80;sample++) {
     long start=System.nanoTime();
     if(lock instanceof java.util.concurrent.locks.Lock fair) { fair.lock();fair.unlock(); }
     else synchronized(lock) {}
     long elapsed=(System.nanoTime()-start)/1_000_000;
     total+=elapsed;maximum=Math.max(maximum,elapsed);Thread.sleep(75);
    }
    result.append("market_lock_wait_ms average=").append(total/80.0).append(" maximum=").append(maximum).append('\n');
    if(player==null) { Files.writeString(Path.of(output),result+"No connected player for authenticated browsing timings.\n");return; }
    for(String section:List.of("catalog","detail","activity","full")) {
     List<Long> samples=new ArrayList<>();
     for(int i=0;i<4;i++) {
      long start=System.nanoTime();
      CentralMarketService.snapshot(player,Map.of("section",section,"variant","100000096:0:0"));
      samples.add((System.nanoTime()-start)/1_000_000);
     }
     result.append(section).append(" browsing_ms=").append(samples).append('\n');
    }
    Files.writeString(Path.of(output),result);
   } catch(Exception e) { try {Files.writeString(Path.of(output),"Timing failed: "+e.getClass().getSimpleName()+": "+e.getMessage()+"\n");}catch(Exception ignored){} }
  },"central-market-read-timing");probe.setDaemon(true);probe.start();
 }
}
