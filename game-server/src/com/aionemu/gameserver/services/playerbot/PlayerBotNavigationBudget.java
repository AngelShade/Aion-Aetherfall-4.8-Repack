package com.aionemu.gameserver.services.playerbot;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.function.LongSupplier;

/** Aion terrain adapter: charge actual work rather than imposing a population-wide
 * fixed turn rate. Native probes may overrun a slice; that debt delays later work. */
final class PlayerBotNavigationBudget {
 enum Priority { WORLD, NEAR, CONTROLLED }
 private static final long AGE=2_000_000_000L, EXPIRE=5_000_000_000L;
 private static final class Request {
  final WeakReference<Object> token;final long queued,sequence;
  int priority;long nominated;boolean hasNomination,cancelled;
  Request(Object token,int priority,long now,long sequence){this.token=new WeakReference<>(token);this.priority=priority;queued=now;this.sequence=sequence;}
 }
 private final LongSupplier clock;
 private final long sliceNanos,burstNanos;
 private final int workers;
 private final double rate;
 private final List<ArrayDeque<Request>> queues=List.of(new ArrayDeque<>(),new ArrayDeque<>(),new ArrayDeque<>());
 private final Map<Object,Request> waiting=new WeakHashMap<>();
 private final IdentityHashMap<Object,Lease> running=new IdentityHashMap<>();
 private long lastRefill,sequence,chargedNanos,overruns;
 private long typicalNanos;
 private long wakeupObserved;
 private boolean wakeupDirty=true;
 private Set<Request> wakeups=Set.of();
 private double credit;
 private int priorityStreak;

 PlayerBotNavigationBudget(int budgetMs,int sliceMs,int workers){this(System::nanoTime,budgetMs,sliceMs,workers);}
 PlayerBotNavigationBudget(LongSupplier clock,int budgetMs,int sliceMs,int workers){
  if(clock==null || budgetMs<1 || budgetMs>1000 || workers<1 || workers>6 || sliceMs<1 || sliceMs>50)
   throw new IllegalArgumentException("Terrain budget requires 1..1000 ms/s, 1..6 workers and 1..50 ms slices");
  this.clock=clock;this.workers=workers;sliceNanos=sliceMs*1_000_000L;burstNanos=sliceNanos*workers;
  rate=budgetMs/1000d;lastRefill=clock.getAsLong();credit=burstNanos;typicalNanos=sliceNanos;
 }
 synchronized void request(Object token,int priority){request(token,priority,clock.getAsLong());}
 void request(Object token,Priority priority){request(token,priority.ordinal());}
 synchronized boolean nominated(Object token){
  if(!waiting.containsKey(token))return false;
  long now=clock.getAsLong();refill(now);
  if(credit<0)return false;
  // Wake enough independent actors to cover the existing100ms AI reaction.
  // Wakeups consume neither credit nor workers: begin() owns actual admission.
  // Using measured cost avoids underfilling when a native step is much cheaper
  // than its maximum slice. Overshoot debt remains authoritative below.
  // A population scheduler reads many tokens in one observation. Cache request
  // identities briefly, never actors/native state, and invalidate on queue work.
  if(wakeupDirty || now-wakeupObserved>=1_000_000L){
   // Include the work itself in that horizon: during a callback batch, its
   // admitted work consumes part of elapsed time before the next observation.
   // At100% allowance the entire pending set may need wakeups; leases still
   // impose the unchanged concurrency and measured-cost boundaries.
   int count=rate==1 ? waiting.size() : (int)Math.min(waiting.size(),Math.max(workers,Math.ceil(rate*100_000_000L/(typicalNanos*(1-rate)))));
   wakeups=new HashSet<>(nominees(now,count));wakeupObserved=now;wakeupDirty=false;
  }
  return wakeups.contains(waiting.get(token));
 }
 synchronized boolean waiting(Object token){return waiting.containsKey(token)||running.containsKey(token);}
 Lease begin(Object token,Priority priority){return begin(token,priority.ordinal());}
 private void request(Object token,int priority,long now){
  if(token==null)throw new IllegalArgumentException("Missing terrain intent");
  if(running.containsKey(token))return;
  int band=Math.max(0,Math.min(2,priority));Request old=waiting.get(token);
  if(old!=null && expired(old,now)){old.cancelled=true;waiting.remove(token);old=null;}
  if(old==null){old=new Request(token,band,now,sequence++);waiting.put(token,old);queues.get(band).addLast(old);wakeupDirty=true;}
  else if(old.priority!=band){queues.get(old.priority).remove(old);old.priority=band;queues.get(band).addLast(old);wakeupDirty=true;}
 }
 synchronized boolean isTurn(Object token){
  // Most actors have no pending terrain intent. They must not allocate or
  // nominate somebody else's queue merely to classify their reaction delay.
  if(!waiting.containsKey(token))return false;
  long now=clock.getAsLong();refill(now);
  for(Request request:nominees(now))if(request.token.get()==token)return true;
  return false;
 }
 synchronized Lease begin(Object token,int priority){
  long now=clock.getAsLong();refill(now);request(token,priority,now);
  if(running.containsKey(token))return null;
  // Nomination asks the actor scheduler to wake an aged/important intent; it
  // must not reserve a native worker for an actor whose callback is still100ms
  // away. This callback is ready now. Charge it only when an actual worker and
  // an entire slice are available, regardless of another actor's wakeup rank.
  if(running.size()>=workers || credit<sliceNanos)return null;
  Request selected=waiting.get(token);
  if(selected==null)return null;
  Request highest=null;for(var q:queues){
   while(!q.isEmpty()){
    Request head=q.peekFirst();Object queuedToken=head.token.get();
    if(head.cancelled || queuedToken==null || expired(head,now)){
     q.removeFirst();if(queuedToken!=null && waiting.get(queuedToken)==head)waiting.remove(queuedToken);
    }else{if(highest==null || head.priority>highest.priority)highest=head;break;}
   }
  }
  if(highest!=null && selected.priority<highest.priority)priorityStreak=0;else priorityStreak++;
  queues.get(selected.priority).remove(selected);waiting.remove(token);selected.cancelled=true;
  wakeupDirty=true;
  credit-=sliceNanos;Lease lease=new Lease(token,now);running.put(token,lease);return lease;
 }
 synchronized void cancel(Object token){Request request=waiting.remove(token);if(request!=null){request.cancelled=true;wakeupDirty=true;}}
 private boolean expired(Request r,long now){return r.hasNomination && now-r.nominated>EXPIRE;}
 private void refill(long now){if(now>lastRefill){credit=Math.min(burstNanos,credit+(now-lastRefill)*rate);lastRefill=now;}}
 private List<Request> nominees(long now){
  int slots=Math.min(workers-running.size(),(int)Math.max(0,Math.floor(credit/sliceNanos)));
  return nominees(now,slots);
 }
 private List<Request> nominees(long now,int slots){
  if(slots<=0)return List.of();
  var heads=new ArrayList<List<Request>>(3);
  for(var queue:queues){
   var list=new ArrayList<Request>(slots);var iterator=queue.iterator();
   while(iterator.hasNext()&&list.size()<slots){Request r=iterator.next();Object token=r.token.get();
    if(r.cancelled||token==null||expired(r,now)){iterator.remove();if(token!=null&&waiting.get(token)==r)waiting.remove(token);}
    else list.add(r);
   }heads.add(list);
  }
  int[] cursor=new int[3];int streak=priorityStreak;var chosen=new ArrayList<Request>(slots);
  while(chosen.size()<slots){
   Request high=null,oldestLower=null;
   for(int band=2;band>=0;band--)if(cursor[band]<heads.get(band).size()){
    Request candidate=heads.get(band).get(cursor[band]);
    if(high==null)high=candidate;
    else if(oldestLower==null || candidate.queued<oldestLower.queued || candidate.queued==oldestLower.queued&&candidate.sequence<oldestLower.sequence)oldestLower=candidate;
   }
   if(high==null)break;
   boolean background=oldestLower!=null&&(streak>=4||streak>=1&&now-oldestLower.queued>=AGE);
   Request next=background?oldestLower:high;streak=background?0:streak+1;cursor[next.priority]++;
   if(!next.hasNomination){next.nominated=now;next.hasNomination=true;}chosen.add(next);
  }
  return chosen;
 }
 final class Lease implements AutoCloseable {
  private final Object token;private final long started;private boolean closed;
  private Lease(Object token,long started){this.token=token;this.started=started;}
  long deadlineNanos(){return started+sliceNanos;}
  @Override public void close(){synchronized(PlayerBotNavigationBudget.this){
   if(closed)return;closed=true;long now=clock.getAsLong(),spent=Math.max(0,now-started);refill(now);
   running.remove(token);credit=Math.min(burstNanos,credit+sliceNanos-spent);chargedNanos+=spent;
   typicalNanos=Math.max(1,typicalNanos+(spent-typicalNanos)/16);
   wakeupDirty=true;
   if(spent>sliceNanos)overruns++;
  }}
 }
}
