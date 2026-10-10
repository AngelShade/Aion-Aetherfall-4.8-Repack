package com.aionemu.gameserver.services.playerbot;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import sun.misc.Unsafe;

/** Exercises real shutdown coordination without constructing World, characters, DB or native IDs. */
public final class PlayerBotShutdownCheck {
 static int checks;
 static void check(boolean condition,String why){checks++;if(!condition)throw new AssertionError(why);}
 static void set(Object target,String name,Object value)throws Exception {
  var field=target.getClass().getDeclaredField(name);field.setAccessible(true);field.set(target,value);
 }
 static boolean stopping(PlayerBotService service)throws Exception {
  var field=PlayerBotService.class.getDeclaredField("shuttingDown");field.setAccessible(true);return field.getBoolean(service);
 }
 public static void main(String[] args)throws Exception {
  var queued=new PlayerBotBackgroundWork();var queue=new java.util.ArrayList<Runnable>();var calls=new java.util.concurrent.atomic.AtomicInteger();
  try {queued.awaitCompletion();throw new AssertionError("Drain without closed admission");}
  catch(IllegalStateException e){check(true,"Drain requires closed admission");}
  check(queued.submit(queue::add,calls::incrementAndGet),"Queued work admitted");queued.close();queue.removeFirst().run();queued.awaitCompletion();
  check(calls.get()==0 && !queued.busy(),"Queued creation is discarded before native allocation");
  check(!queued.submit(queue::add,calls::incrementAndGet),"Closed queue never reopens");
  queued.close();queued.awaitCompletion();check(true,"Repeated drain is safe");
  var field=Unsafe.class.getDeclaredField("theUnsafe");field.setAccessible(true);var unsafe=(Unsafe)field.get(null);
  var service=(PlayerBotService)unsafe.allocateInstance(PlayerBotService.class);
  var worker=new PlayerBotBackgroundWork();set(service,"backgroundWork",worker);set(service,"sessions",new ConcurrentHashMap<>());
  for(String lane:java.util.List.of("controlledWork","autonomousWork","maintenanceWork","checkpointWork"))set(service,lane,new PlayerBotBackgroundWork());
  var entered=new CountDownLatch(1);var release=new CountDownLatch(1);var finished=new CountDownLatch(1);
  var acquiredMonitor=new AtomicBoolean();var pool=Executors.newSingleThreadExecutor();
  var shutdown=new Thread(()->{try{service.shutdown();}finally{finished.countDown();}},"offline-shutdown-check");
  try {
   check(worker.submit(pool,()->{entered.countDown();try{release.await();}catch(InterruptedException e){throw new AssertionError(e);}
    synchronized(service){acquiredMonitor.set(true);}}),"Running native work admitted");
   check(entered.await(3,TimeUnit.SECONDS),"Background work starts");shutdown.start();
   long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);while(!stopping(service) && System.nanoTime()<until)Thread.sleep(1);
   check(stopping(service),"Shutdown closes admission before waiting");
   check(!worker.submit(pool,()->{throw new AssertionError("New work during shutdown");}),"No new work during shutdown");
   check(!finished.await(150,TimeUnit.MILLISECONDS),"Shutdown waits for running native cleanup");
   shutdown.interrupt();check(!finished.await(150,TimeUnit.MILLISECONDS),"Interrupt cannot discard pending native work");
   release.countDown();check(finished.await(3,TimeUnit.SECONDS),"Shutdown completes after native work finishes");
   check(acquiredMonitor.get(),"Drain does not hold service monitor needed by native completion");
   check(!worker.busy(),"No native worker remains at shutdown completion");
   check(shutdown.isInterrupted(),"Shutdown preserves interruption after safe cleanup");
  } finally {release.countDown();shutdown.join(3000);pool.shutdown();check(pool.awaitTermination(3,TimeUnit.SECONDS),"Offline executor terminates");}
  System.out.println("OK: "+checks+" shutdown coordination checks; no World/DB/native IDs/casts");
 }
}
