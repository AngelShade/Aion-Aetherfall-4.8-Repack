/* RandomPlayerbotMgr::UpdateAIInternal (loginBots batch while botLoading is empty) and
 * PlayerbotHolder::AddPlayerBot (asynchronous login query holders), mod-playerbots
 * 037c01418b5d01506917a3db9b44fd56ac5f965c. GPL-2.0-or-later; third-party/playerbots/AUTHORS.md.
 * Aion has no query holders: each character load runs whole on a login worker instead. */
package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

/** Pure batch-login decisions plus the login pool; world entry stays under the service monitor. */
final class PlayerBotLoginLane {
 /** Most logins in one batch: the validated per_interval budget range. */
 static final int MAX_BATCH=120;
 /** Database connections left for saves, the market and web windows while a batch loads. */
 static final int POOL_RESERVE=12;
 private static final int MAX_WORKERS=256;

 static int budget(int perInterval){return Math.max(1,Math.min(MAX_BATCH,perInterval));}

 /** 0 = one worker per CPU core. Bounded only by the database pool, never by a fixed small cap. */
 static int workers(int configured,int processors,int pool) {
  if(configured<0)throw new IllegalArgumentException("World-bot login workers must be 0 (automatic) or more");
  int wanted=configured==0?Math.max(1,processors):configured;
  return Math.max(1,Math.min(Math.min(wanted,MAX_WORKERS),pool-POOL_RESERVE));
 }

 /** The next login batch: due, idle residents in candidate order, within budget and both rooms. */
 static List<Integer> batch(List<Integer> pending,Predicate<Integer> global,Set<Integer> active,Set<Integer> retiring,Set<Integer> inFlight,
                            Map<Integer,Long> retries,long now,int budget,int worldRoom,int globalRoom) {
  var batch=new ArrayList<Integer>();var seen=new HashSet<Integer>();
  for(int id:pending) {
   if(batch.size()>=budget || worldRoom<=0)break;
   if(!seen.add(id) || active.contains(id) || retiring.contains(id) || inFlight.contains(id) || retries.getOrDefault(id,0L)>now)continue;
   boolean automatic=global.test(id);
   if(automatic && globalRoom<=0)continue;
   batch.add(id);worldRoom--;if(automatic)globalRoom--;
  }
  return List.copyOf(batch);
 }

 static ExecutorService executor(int workers) {
  var number=new AtomicInteger();
  var pool=new ThreadPoolExecutor(workers,workers,60,TimeUnit.SECONDS,new LinkedBlockingQueue<>(),job->{
   var thread=new Thread(job,"PlayerbotLogin-"+number.incrementAndGet());thread.setDaemon(true);return thread;});
  pool.allowCoreThreadTimeOut(true);
  return pool;
 }
 static int size(ExecutorService executor){return executor instanceof ThreadPoolExecutor pool?pool.getMaximumPoolSize():0;}

 /** Completed world logins in the last minute, for the population report. */
 static final class Rate {
  private final ConcurrentLinkedDeque<Long> times=new ConcurrentLinkedDeque<>();
  void record(long now){times.addLast(now);trim(now);}
  int perMinute(long now){trim(now);return times.size();}
  private void trim(long now){for(Long first;(first=times.peekFirst())!=null && first<now-60000;)times.pollFirst();}
 }
 private PlayerBotLoginLane() {}
}
