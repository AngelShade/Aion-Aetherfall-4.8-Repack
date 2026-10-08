package ai.instance.rakes;

import java.util.*;
import java.util.concurrent.FutureTask;

/** Calls captured callbacks without starting a scheduler or spawning an actor. */
public final class SteelRakeTasksCheck {
 private static int checks;
 private static void check(boolean value,String label){if(!value)throw new AssertionError(label);checks++;}
 public static void main(String[] args){
  List<Runnable> callbacks=new ArrayList<>();List<FutureTask<Void>> futures=new ArrayList<>();
  boolean[] alive={true};int[] calls={0};
  SteelRakeTasks tasks=new SteelRakeTasks(()->alive[0],(callback,delay)->{check(delay>=0,"valid delay");callbacks.add(callback);var f=new FutureTask<Void>(callback,null);futures.add(f);return f;});
  tasks.later(()->calls[0]++,100);callbacks.getFirst().run();check(calls[0]==1,"active phase executes");
  tasks.later(()->calls[0]++,100);tasks.reset();check(futures.stream().allMatch(FutureTask::isCancelled),"reset cancels owned tasks");callbacks.get(1).run();check(calls[0]==1,"already queued old callback suppressed");
  tasks.later(()->calls[0]++,100);alive[0]=false;callbacks.get(2).run();check(calls[0]==1,"dead or despawned owner suppressed");
  alive[0]=true;tasks.reset();tasks.later(()->{calls[0]++;tasks.later(()->calls[0]++,20);},10);callbacks.get(3).run();callbacks.get(4).run();check(calls[0]==3,"current phase can reschedule safely");
  tasks.reset();callbacks.get(4).run();check(calls[0]==3,"chained callback cannot survive reset");
  System.out.println("OK: "+checks+" encounter callback checks; no live actors");
 }
}
