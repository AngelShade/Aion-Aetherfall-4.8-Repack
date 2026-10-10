package com.aionemu.gameserver.services.playerbot;

import java.util.*;

/** Stagger complete native transactions; one scheduled callback saves at most one character. */
final class PlayerBotCheckpointSchedule {
 private final Map<Integer, Long> deadlines = new HashMap<>();
 static long interval(int seconds) { return Math.max(30L, seconds) * 1000L; }
 Integer next(Set<Integer> active, long now, long interval) {
  return next(active,now,interval,id->true);
 }
 Integer next(Set<Integer> active,long now,long interval,java.util.function.IntPredicate available) {
  deadlines.keySet().retainAll(active);
  for (int id : active) deadlines.computeIfAbsent(id, key -> now + Math.max(25L,
   Integer.toUnsignedLong(Integer.rotateLeft(key * 0x9E3779B9, 13)) % interval));
  return deadlines.entrySet().stream().filter(e -> e.getValue() <= now && available.test(e.getKey()))
   .min(Comparator.comparingLong((Map.Entry<Integer, Long> e) -> e.getValue()).thenComparingInt(Map.Entry::getKey))
   .map(Map.Entry::getKey).orElse(null);
 }
 void complete(int id, long now, long interval) {
  if (deadlines.containsKey(id)) deadlines.put(id, now + interval);
 }
 long delay(long now) {
  long due = deadlines.values().stream().mapToLong(Long::longValue).min().orElse(now + 1000);
  return Math.max(25L, Math.min(1000L, due - now));
 }
}
