/* PlayerbotAI::UpdateAI/GetReactDelay, mod-playerbots
 * 037c01418b5d01506917a3db9b44fd56ac5f965c. GPL-2.0-or-later; AUTHORS.md.
 * Native Aion uses one completion-scheduled runner, not WoW world sessions. */
package com.aionemu.gameserver.services.playerbot;

import java.util.*;

/** Per-character reaction deadlines. A batch yield must not delay every remaining bot. */
final class PlayerBotDecisionSchedule {
 record Ready<T>(T value,int id,long due,boolean controlled) {}
 /** Capture mutable actor state once. TimSort comparisons must never read combat timers. */
 static <T> List<Ready<T>> ready(Collection<T> actors,java.util.function.ToIntFunction<T> id,
   java.util.function.ToLongFunction<T> due,java.util.function.Predicate<T> controlled,long now) {
  var snapshot=new ArrayList<Ready<T>>();
  for(var actor:actors){long deadline=due.applyAsLong(actor);if(deadline<=now)snapshot.add(new Ready<>(actor,id.applyAsInt(actor),deadline,controlled.test(actor)));}
  snapshot.sort(Comparator.comparing((Ready<T> r)->!r.controlled()).thenComparingLong(Ready::due).thenComparingInt(Ready::id));
  return List.copyOf(snapshot);
 }
 private final Map<Integer,Long> completed=new java.util.concurrent.ConcurrentHashMap<>();
 static long reaction(boolean clientMaster,boolean combat,boolean resting,int randomIdleMultiplier) {
  if(clientMaster)return 100;
  if(combat)return 500;
  return 100L*Math.max(resting?20:10,Math.min(resting?200:30,randomIdleMultiplier));
 }
 long due(int id,long reaction){var last=completed.get(id);return last==null?0:last+reaction;}
 void complete(int id,long now){completed.put(id,now);}
 void retain(Set<Integer> ids){completed.keySet().retainAll(ids);}
 static long delay(long now,long earliest,long maximum){return Math.max(1,Math.min(Math.max(1,maximum),earliest-now));}
 static long maximum(int configured,boolean running){return running?25:Math.min(400,Math.max(25,configured));}
}
