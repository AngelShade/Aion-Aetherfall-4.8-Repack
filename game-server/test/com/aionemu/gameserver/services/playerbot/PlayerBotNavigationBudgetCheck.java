package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import java.util.function.LongSupplier;

/** Measured-cost admission under a fake monotonic clock, without threads or World. */
public final class PlayerBotNavigationBudgetCheck {
 private static int checks;
 private static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);checks++;}
 static final class Clock implements LongSupplier {long now;@Override public long getAsLong(){return now;}void ms(long value){now+=value*1_000_000;}}
 static Object field(Object o,String name)throws Exception{var f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
 private static void population(int count)throws Exception{
  var clock=new Clock();var budget=new PlayerBotNavigationBudget(clock,500,3,2);
  Object[] tokens=new Object[count];int[] turns=new int[count];for(int i=0;i<count;i++){tokens[i]=new Object();budget.request(tokens[i],0);}
  int served=0,maxSkew=0;
  while(served<count*3&&clock.now<15_000_000_000L){
   for(int i=0;i<count;i++)if(turns[i]<3&&budget.isTurn(tokens[i])){
    var lease=budget.begin(tokens[i],0);if(lease==null)continue;
    check(lease.deadlineNanos()==clock.now+3_000_000,"lease deadline retains the configured native slice");
    clock.now+=500_000;lease.close();turns[i]++;served++;if(turns[i]<3)budget.request(tokens[i],0);
   }
   maxSkew=Math.max(maxSkew,Arrays.stream(turns).max().orElseThrow()-Arrays.stream(turns).min().orElseThrow());clock.ms(1);
  }
  check(served==count*3,"every retained search earns three turns within15s at500us native cost for population"+count);
  check(maxSkew<=1,"FIFO retained work does not monopolize cheap turns population"+count);
  check((long)field(budget,"chargedNanos")==count*3*500_000L,"every native work cost is charged population"+count);
  check((long)field(budget,"chargedNanos")<=clock.now*.5+6_000_000,"aggregate cost remains inside elapsed rate plus tiny burst population"+count);
 }
 public static void main(String[] args)throws Exception{
  for(int count:new int[]{250,1000,3000})population(count);
  var clock=new Clock();var budget=new PlayerBotNavigationBudget(clock,500,3,2);Object a=new Object(),b=new Object(),c=new Object();
  budget.request(a,0);budget.request(b,0);budget.request(c,0);
  check(budget.waiting(a)&&budget.waiting(c)&&!budget.waiting(new Object()),"passive waiting reports queued commitments without selecting work");
  check(budget.isTurn(a)&&budget.isTurn(b)&&!budget.isTurn(c),"both bounded worker nominations are available without fixed100ms spacing");
  var first=budget.begin(a,0);var second=budget.begin(b,0);
  check(first!=null&&second!=null&&budget.begin(c,0)==null,"running leases never exceed configured concurrency");
  check(budget.begin(a,2)==null,"one actor cannot own overlapping leases");
  budget.cancel(a);check(((Map<?,?>)field(budget,"running")).size()==2,"cancelling intent cannot release in-flight work early");
  check(budget.waiting(a),"passive waiting retains work until its native lease settles");
  clock.ms(20);first.close();second.close();
  check(!budget.waiting(a)&&budget.waiting(c),"settled and still queued commitments have distinct passive status");
  check((long)field(budget,"chargedNanos")==40_000_000,"overlapping leases charge each actual elapsed cost");
  check((long)field(budget,"overruns")==2,"each native probe overshoot is recorded");
  check(!budget.isTurn(c)&&budget.begin(c,0)==null,"overshoot debt prevents further admission");
  first.close();second.close();check((long)field(budget,"chargedNanos")==40_000_000,"finally settlement is idempotent");
  clock.ms(61);check(!budget.isTurn(c),"partial debt repayment does not borrow another native slice");
  clock.ms(1);check(budget.isTurn(c),"elapsed replenishment repays measured debt without losing queued work");
  var next=budget.begin(c,0);check(next!=null,"repaid queued actor enters native work");clock.now+=100_000;next.close();
  check((double)field(budget,"credit")<=6_000_000,"unused slice refund cannot accumulate a large CPU burst");
  clock.ms(10000);budget.isTurn(new Object());check((double)field(budget,"credit")<=6_000_000,"idle time cannot accumulate credit beyond configured tiny burst");

  var priorityClock=new Clock();var fair=new PlayerBotNavigationBudget(priorityClock,500,3,1);Object urgent=new Object();Object[] background=new Object[250];
  for(int i=0;i<background.length;i++){background[i]=new Object();fair.request(background[i],0);}fair.request(urgent,2);
  check(fair.isTurn(urgent),"controlled urgent work overtakes distant world requests");
  int worlds=0,urgents=0,streak=0,maxStreak=0;
  for(int turn=0;turn<5000&&worlds<background.length;turn++){
   Object winner=null;int band=0;
   if(fair.isTurn(urgent)){winner=urgent;band=2;}
   else for(Object token:background)if(fair.isTurn(token)){winner=token;break;}
   if(winner==null){priorityClock.ms(1);continue;}
   var lease=fair.begin(winner,band);if(lease==null)continue;priorityClock.now+=500_000;lease.close();
   if(winner==urgent){urgents++;streak++;maxStreak=Math.max(maxStreak,streak);fair.request(urgent,2);}else{worlds++;streak=0;}
  }
  check(worlds==250&&urgents>0,"continuous urgent work leaves background searches a real reserved share");
  check(maxStreak<=4,"higher priority cannot starve queued background work");
  check(priorityClock.now<5_000_000_000L,"bounded fair shares complete cheap background turns promptly");
  var ageClock=new Clock();var aged=new PlayerBotNavigationBudget(ageClock,500,3,1);Object low=new Object(),high=new Object();aged.request(low,0);ageClock.ms(2100);aged.request(high,2);
  var highLease=aged.begin(high,2);check(highLease!=null,"aging retains an urgent share");ageClock.now+=500_000;highLease.close();aged.request(high,2);
  ageClock.ms(1);check(aged.isTurn(low),"aged world work follows one urgent turn instead of another urgent burst");

  var expiryClock=new Clock();var expiry=new PlayerBotNavigationBudget(expiryClock,500,3,1);Object abandoned=new Object(),tail=new Object();expiry.request(abandoned,0);expiry.request(tail,0);
  check(expiry.isTurn(abandoned),"oldest live actor receives nomination");expiryClock.ms(5001);
  check(expiry.isTurn(tail),"abandoned nomination expires while next valid intent remains");
  expiry.cancel(tail);check(!expiry.isTurn(tail),"cancelled intent cannot retain a nomination");
  expiry.request(abandoned,0);check(expiry.isTurn(abandoned),"expired actor may safely register a new commitment");
  for(int workers:new int[]{1,2,6}){
   var t=new Clock();var scaled=new PlayerBotNavigationBudget(t,500,3,workers);List<PlayerBotNavigationBudget.Lease> leases=new ArrayList<>();
   for(int i=0;i<workers;i++)leases.add(scaled.begin(new Object(),2));
   check(leases.stream().allMatch(Objects::nonNull)&&scaled.begin(new Object(),2)==null,"configured concurrency boundary retained for"+workers);
   t.ms(1);leases.forEach(PlayerBotNavigationBudget.Lease::close);check(((Map<?,?>)field(scaled,"running")).isEmpty(),"all native worker leases drain after completion");
  }
  for(int[] bad:new int[][]{{0,3,2},{1001,3,2},{500,3,0},{500,3,7},{500,0,2},{500,51,2}})
   try{new PlayerBotNavigationBudget(new Clock(),bad[0],bad[1],bad[2]);throw new AssertionError("invalid policy admitted");}catch(IllegalArgumentException expected){checks++;}
  System.out.println("OK: "+checks+" elapsed terrain budget, priority, retained fairness, debt and lease lifecycle checks");
 }
}
