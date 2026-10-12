/* PlayerbotAI::UpdateAI/GetReactDelay, mod-playerbots
 * 037c01418b5d01506917a3db9b44fd56ac5f965c. GPL-2.0-or-later; AUTHORS.md.
 * Native Aion uses one completion-scheduled runner, not WoW world sessions. */
package com.aionemu.gameserver.services.playerbot;

import java.util.*;

/** Per-character reaction deadlines. A batch yield must not delay every remaining bot. */
final class PlayerBotDecisionSchedule {
 record Ready<T>(T value,int id,long due,boolean controlled) {}
 /** One scheduler observation; a full lane owns no readiness objects or sort work. */
 static final class Pass<T,L> {
  private final Map<L,List<Ready<T>>> ready=new LinkedHashMap<>();
  private final Map<L,Long> earliest=new LinkedHashMap<>();
  private final boolean saturated;
  private int lateCount;
  private T oldestActor;
  private int oldestId;
  private long oldestDue=Long.MAX_VALUE;
  private boolean oldestControlled;
  private boolean finished;
  Pass(boolean saturated){this.saturated=saturated;}
  void observe(T actor,int id,long due,boolean controlled,boolean active,L lane,int available,long now,boolean reportLag) {
   if(finished)throw new IllegalStateException("Scheduler pass already finished");
   if(available>0) {
    earliest.merge(lane,due,Math::min);
    if(due<=now)ready.computeIfAbsent(lane,key->new ArrayList<>()).add(new Ready<>(actor,id,due,controlled));
   }
   if(reportLag && active && due>0 && now-due>=5000) {
    lateCount++;
    if(oldestActor==null || due<oldestDue || due==oldestDue && (controlled && !oldestControlled || controlled==oldestControlled && id<oldestId)){
     oldestActor=actor;oldestId=id;oldestDue=due;oldestControlled=controlled;
    }
   }
  }
  void finish() {
   if(finished)return;
   ready.replaceAll((lane,actors)->{
    actors.sort(Comparator.comparing((Ready<T> r)->!r.controlled()).thenComparingLong(Ready::due).thenComparingInt(Ready::id));
    return List.copyOf(actors);
   });
   if(ready.size()>1) {
    // Preserve the original global Ready ordering between at most three lanes,
    // including controlled-party admission ahead of independent work.
    Comparator<Ready<T>> order=Comparator.comparing((Ready<T> r)->!r.controlled()).thenComparingLong(Ready::due).thenComparingInt(Ready::id);
    var lanes=new ArrayList<>(ready.entrySet());lanes.sort(Comparator.comparing(e->e.getValue().getFirst(),order));
    ready.clear();for(var lane:lanes)ready.put(lane.getKey(),lane.getValue());
   }
   finished=true;
  }
  Map<L,List<Ready<T>>> ready(){if(!finished)throw new IllegalStateException("Scheduler pass not finished");return Collections.unmodifiableMap(ready);}
  long earliest(java.util.function.Predicate<L> available) {
   long result=Long.MAX_VALUE;
   for(var entry:earliest.entrySet())if(available.test(entry.getKey()))result=Math.min(result,entry.getValue());
   return result;
  }
  boolean saturated(){return saturated;}
  int lateCount(){return lateCount;}
  Ready<T> oldest(){return lateCount==0?null:new Ready<>(oldestActor,oldestId,oldestDue,oldestControlled);}
 }
 /** Capture mutable actor state once. TimSort comparisons must never read combat timers. */
 static <T> List<Ready<T>> ready(Collection<T> actors,java.util.function.ToIntFunction<T> id,
   java.util.function.ToLongFunction<T> due,java.util.function.Predicate<T> controlled,long now) {
  var snapshot=new ArrayList<Ready<T>>();
  for(var actor:actors){long deadline=due.applyAsLong(actor);if(deadline<=now)snapshot.add(new Ready<>(actor,id.applyAsInt(actor),deadline,controlled.test(actor)));}
  snapshot.sort(Comparator.comparing((Ready<T> r)->!r.controlled()).thenComparingLong(Ready::due).thenComparingInt(Ready::id));
  return List.copyOf(snapshot);
 }
 private final Map<Integer,Long> completed=new java.util.concurrent.ConcurrentHashMap<>();
 /** Disjoint slices retain deadline order within each bounded independent worker. */
 static <T> List<List<T>> batches(List<T> ready,int slots) {
  if(slots<=0 || ready.isEmpty())return List.of();
  var result=new ArrayList<List<T>>();for(int i=0;i<Math.min(slots,ready.size());i++)result.add(new ArrayList<>());
  for(int i=0;i<ready.size();i++)result.get(i%result.size()).add(ready.get(i));
  return result.stream().map(List::copyOf).toList();
 }
 private record Deadline(long reaction,long due) {}
 private final Map<Integer,Deadline> observed=new java.util.concurrent.ConcurrentHashMap<>();
 static long reaction(boolean clientMaster,boolean combat,boolean resting,int randomIdleMultiplier) {
  if(clientMaster)return 100;
  if(combat)return 500;
  return 100L*Math.max(resting?20:10,Math.min(resting?200:30,randomIdleMultiplier));
 }
 long due(int id,long reaction){var last=completed.get(id);return last==null?0:last+reaction;}
 /** A newly urgent actor starts its shorter wait when observed, not retroactively at its last idle tick. */
 long due(int id,long reaction,long now){
  return observed.compute(id,(key,previous)->{
   if(previous==null)return new Deadline(reaction,due(id,reaction));
   if(previous.reaction()==reaction)return previous;
   return new Deadline(reaction,reaction<previous.reaction() ? Math.min(previous.due(),now+reaction) : due(id,reaction));
  }).due();
 }
 void complete(int id,long now){completed.put(id,now);observed.remove(id);}
 void retain(Set<Integer> ids){completed.keySet().retainAll(ids);observed.keySet().retainAll(ids);}
 static long delay(long now,long earliest,long maximum){return Math.max(1,Math.min(Math.max(1,maximum),earliest-now));}
 static long maximum(int configured,boolean running){return running?25:Math.min(400,Math.max(25,configured));}
}
