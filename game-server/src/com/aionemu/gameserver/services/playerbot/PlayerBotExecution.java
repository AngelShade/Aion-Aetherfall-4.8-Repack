/* Pinned PlayerbotWorldThreadProcessor: admit under a short lock, execute outside it.
 * Aion adaptation uses bounded serial decision lanes, never concurrent party decisions. */
package com.aionemu.gameserver.services.playerbot;

import java.util.concurrent.ConcurrentHashMap;

/** Prevent a checkpoint and decision from working on the same native actor concurrently. */
final class PlayerBotExecution {
 record Work(String operation,long started) {}
 private final ConcurrentHashMap<Integer,Work> active=new ConcurrentHashMap<>();
 boolean claim(int id,String operation,long now){return active.putIfAbsent(id,new Work(operation,now))==null;}
 void complete(int id){active.remove(id);}
 boolean busy(int id){return active.containsKey(id);}
 boolean slow(int id,long now,long threshold){var work=active.get(id);return work!=null && now-work.started()>threshold;}
 String health(int id,long now){var work=active.get(id);return work==null?null:work.operation()+" running for "+Math.max(0,now-work.started())+"ms";}
}
