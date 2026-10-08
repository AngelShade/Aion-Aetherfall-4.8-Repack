package ai.instance.rakes;

import java.util.*;
import java.util.concurrent.Future;
import java.util.function.BooleanSupplier;
import java.util.function.BiFunction;
import com.aionemu.gameserver.ai.NpcAI;
import com.aionemu.gameserver.utils.ThreadPoolManager;

/** Actor-owned callbacks and reset use one lock order; old generations cannot execute. */
final class SteelRakeTasks {
 private final BooleanSupplier alive;
 private final BiFunction<Runnable,Long,Future<?>> schedule;
 private final Object gate;
 private final List<Future<?>> tasks=new ArrayList<>();
 private long generation;
 SteelRakeTasks(NpcAI ai){this(()->ai.getOwner().isSpawned()&&!ai.isDead(),(action,delay)->ThreadPoolManager.getInstance().schedule(action,delay),ai);}
 SteelRakeTasks(BooleanSupplier alive,BiFunction<Runnable,Long,Future<?>> schedule){this(alive,schedule,new Object());}
 private SteelRakeTasks(BooleanSupplier alive,BiFunction<Runnable,Long,Future<?>> schedule,Object gate){this.alive=alive;this.schedule=schedule;this.gate=gate;}
 void later(Runnable action,long delay){synchronized(gate){
  long expected=generation;tasks.removeIf(Future::isDone);
  tasks.add(schedule.apply(()->{synchronized(gate){if(expected==generation && alive.getAsBoolean())action.run();}},delay));
 }}
 void reset(){synchronized(gate){generation++;for(var task:tasks)task.cancel(false);tasks.clear();}}
}
