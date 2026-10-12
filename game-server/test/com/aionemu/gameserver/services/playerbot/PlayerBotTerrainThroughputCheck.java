package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import java.util.function.LongSupplier;

/** Delayed native actor callbacks under measured terrain debt; no World, tasks or IDs. */
public final class PlayerBotTerrainThroughputCheck {
 private static int checks;
 private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 private static final class Clock implements LongSupplier {
  long now;
  @Override public long getAsLong(){return now;}
 }
 private static Object field(Object o,String name)throws Exception {
  var f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);
 }
 private static void delayedCallbacks(int count,boolean mixed)throws Exception {
  var clock=new Clock();var quota=new PlayerBotNavigationBudget(clock,500,3,2);
  Object[] actor=new Object[count];int[] turns=new int[count],band=new int[count];
  var decisions=new PlayerBotDecisionSchedule();
  for(int i=0;i<count;i++){
   actor[i]=new Object();decisions.complete(i,0);
   band[i]=mixed ? i<20 ? 2 : i<100 ? 1 : 0 : 0;
   decisions.due(i,band[i]==2?100:1000,0);quota.request(actor[i],band[i]);
  }
  long end=12_000_000_000L;
  // Production derives due time from last completion and current reaction.
  // Only nominated pending actors receive100ms; ordinary idle actors receive1s.
  // Each scheduler pass observes the whole population, then dispatches due actors.
  while(clock.now<end){
   boolean[] due=new boolean[count];
   for(int i=0;i<count;i++)due[i]=decisions.due(i,band[i]==2 || quota.nominated(actor[i])?100:1000,clock.now/1_000_000)<=clock.now/1_000_000;
   for(int i=0;i<count;i++)if(due[i]){
    // Four native AI lanes each perform1ms of nonterrain decision work. This
    // deterministic interleaving advances wall time250us per delivered actor;
    // native combat/quest checks run even when terrain admission is denied.
    clock.now+=250_000;
    var lease=quota.begin(actor[i],band[i]);
    if(lease!=null){clock.now+=500_000;lease.close();turns[i]++;quota.request(actor[i],band[i]);}
    decisions.complete(i,clock.now/1_000_000);
   }
   clock.now+=1_000_000;
  }
  long charged=(long)field(quota,"chargedNanos");
  System.out.println("Delayed callbacks: actors="+count+", mixed="+mixed+", workMsPerSecond="+charged/(double)clock.now*1000+", minimumTurns="+Arrays.stream(turns).min().orElseThrow());
  check(charged<=clock.now*.5+6_000_000,"Actual charged work preserves500ms/s and6ms maximum burst");
  check(charged>=clock.now*.30,"Pending ready work consumes at least300ms/s instead of waiting for two future callbacks");
  check(Arrays.stream(turns).min().orElseThrow()>=2,"Every retained actor receives repeated work despite callback phase/order");
  if(mixed){
   double[] average=new double[3];int[] size=new int[3];
   for(int i=0;i<count;i++){average[band[i]]+=turns[i];size[band[i]]++;}
   for(int i=0;i<3;i++)average[i]/=size[i];
   System.out.println("Average turns per actor: world="+average[0]+", near="+average[1]+", controlled="+average[2]);
   check(average[2]>average[0],"Ready controlled callbacks retain a larger share than distant world work");
   check(average[1]>=average[0],"Near wakeups retain preference while all world callbacks make progress");
  }
 }
 private static void admission()throws Exception {
  var clock=new Clock();var quota=new PlayerBotNavigationBudget(clock,500,3,2);
  Object missing=new Object(),ready=new Object(),other=new Object(),third=new Object();
  quota.request(missing,2);quota.request(ready,0);quota.request(other,1);quota.request(third,0);
  check(quota.nominated(missing),"Controlled advisory wakeup remains preferred");
  var a=quota.begin(ready,0);
  check(a!=null,"Absent controlled callback cannot reserve an actual worker against ready world work");
  var b=quota.begin(other,1);
  check(b!=null && quota.begin(third,0)==null,"Only two actual native leases may run even with many advisory wakeups");
  quota.cancel(ready);check(quota.waiting(ready),"Cancellation never releases a running worker before settlement");
  clock.now+=20_000_000;a.close();b.close();
  check((long)field(quota,"chargedNanos")==40_000_000,"Both overlapping overruns are charged in full");
  check(quota.begin(third,0)==null,"Ready callback cannot bypass measured overshoot debt");
  quota.cancel(missing);check(!quota.nominated(missing),"Cancellation invalidates even cached advisory wakeups");
  clock.now+=62_000_000;var c=quota.begin(third,0);
  check(c!=null,"Debt repayment lets the current ready callback resume without another actor's delivery");
  clock.now+=500_000;c.close();
  check((double)field(quota,"credit")<=6_000_000,"Actual refunds retain the original tiny burst ceiling");
  var ageClock=new Clock();var fair=new PlayerBotNavigationBudget(ageClock,500,3,2);
  Object[] high=new Object[100];Object low=new Object();
  fair.request(low,0);ageClock.now+=2_100_000_000L;
  for(int i=0;i<high.length;i++){high[i]=new Object();fair.request(high[i],2);}
  check(fair.nominated(low),"Older world work retains an advisory share among continuously ready controlled intents");
  int awake=0;for(Object token:high)if(fair.nominated(token))awake++;
  check(awake>2,"Reaction wakeups span upcoming work, independently of simultaneous lease count");
  var lease=fair.begin(high[0],2);check(lease!=null,"A controlled callback still consumes actual native capacity");
  ageClock.now+=500_000;lease.close();
  check(fair.nominated(low),"Ready urgent work cannot erase the older world wakeup");
  var absentClock=new Clock();var absent=new PlayerBotNavigationBudget(absentClock,500,3,2);
  Object gone=new Object(),tail=new Object();absent.request(gone,0);absent.request(tail,0);
  check(absent.nominated(gone),"Pending actor initially has an advisory wakeup");
  absentClock.now+=5_001_000_000L;
  check(!absent.nominated(gone),"Abandoned advisory nomination expires without retaining native capacity");
  check(absent.begin(tail,0)!=null,"Another ready actor immediately consumes an available native lease");
 }
 public static void main(String[] args)throws Exception {
  delayedCallbacks(600,false);delayedCallbacks(600,true);admission();
  System.out.println("OK: "+checks+" delayed-callback terrain throughput and fairness checks");
 }
 private PlayerBotTerrainThroughputCheck(){}
}
