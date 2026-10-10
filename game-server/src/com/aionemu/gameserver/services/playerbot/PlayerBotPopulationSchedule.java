package com.aionemu.gameserver.services.playerbot;

import java.io.IOException;
import java.util.*;

/** Persisted upstream-style target/add/logout/teleport events. Crash recovery never rerolls saved characters. */
final class PlayerBotPopulationSchedule {
 record Events(long presentUntil,long offlineUntil,long teleportAt,int placedLevel) {
  boolean pendingOffline(long now){return presentUntil==0 && offlineUntil>now;}
  Events {if(presentUntil<0 || offlineUntil<0 || teleportAt<0 || placedLevel<0 || placedLevel>65)throw new IllegalArgumentException("Invalid population event");}
 }
 record State(int version,int target,long targetUntil,Map<Integer,Events> events) {
  State {if(version!=1 || target<0 || target>PlayerBotPopulationPolicy.MAX_TARGET || targetUntil<0 || events==null || events.size()>PlayerBotPopulationPolicy.MAX_RESERVE
   || events.keySet().stream().anyMatch(id->id<=0))throw new IllegalArgumentException("Invalid population schedule");events=Map.copyOf(events);}
 }
 private final PlayerBotPopulationStorage repository;
 PlayerBotPopulationSchedule(PlayerBotPopulationStorage repository){this.repository=repository;}
 /** Binary compatibility; no file reads/writes. */
 PlayerBotPopulationSchedule(java.nio.file.Path path){this(new PlayerBotPopulationRepository());if(!path.getFileName().toString().equals("world-population-events.json"))throw new IllegalArgumentException("Use native population storage");}
 synchronized State load()throws IOException {
  return repository.schedule();
 }
 synchronized void save(State next)throws IOException {
  repository.schedule(next);
 }
 synchronized void event(int id,Events next)throws IOException {repository.event(id,next);}
 /** Pinned UpdateAIInternal validates bot_count before GetBots, even during saved-login initialization. */
 synchronized State reconcile(long now,PlayerBotPopulationPolicy.Settings settings,boolean enabled,java.util.random.RandomGenerator random)throws IOException {
  State state=load();
  if(enabled && (now>=state.targetUntil() || state.target()<settings.minimum() || state.target()>settings.maximum())) {
   save(new State(1,PlayerBotPopulationPolicy.between(random,settings.minimum(),settings.maximum()),
    PlayerBotPopulationPolicy.deadline(now,random,settings.targetMin(),settings.targetMax()),state.events()));
   state=load();
  }
  return state;
 }
 private PlayerBotPopulationSchedule(){throw new AssertionError();}
}
