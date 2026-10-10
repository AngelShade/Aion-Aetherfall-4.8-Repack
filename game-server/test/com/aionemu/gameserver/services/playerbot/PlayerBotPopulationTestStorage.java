package com.aionemu.gameserver.services.playerbot;

import java.nio.file.Path;
import java.util.*;
import com.alibaba.fastjson2.JSON;

/** Isolated durable-store substitute for policy suites; production SQL has separate JDBC tests. */
final class PlayerBotPopulationTestStorage implements PlayerBotPopulationStorage {
 private static final Map<Path,PlayerBotPopulationTestStorage> STORES=new HashMap<>();
 static PlayerBotPopulationTestStorage at(Path path){return STORES.computeIfAbsent(path.toAbsolutePath(),p->new PlayerBotPopulationTestStorage());}
 static PlayerBotWorldPopulation population(Path path){return new PlayerBotWorldPopulation(at(path),true);}
 static PlayerBotWorldRoster roster(Path path){return new PlayerBotWorldRoster(at(path));}
 static PlayerBotPopulationSchedule schedule(Path path){return new PlayerBotPopulationSchedule(at(path));}
 private Map<Integer,PlayerBotWorldRoster.Resident> residents=Map.of();
 private final Map<Integer,String> pools=new TreeMap<>();
 private String schedule=JSON.toJSONString(new PlayerBotPopulationSchedule.State(1,0,0,Map.of()));
 public Map<Integer,PlayerBotWorldRoster.Resident> residents(){return residents;}
 public void residents(Map<Integer,PlayerBotWorldRoster.Resident> next){residents=Map.copyOf(next);}
 public Map<Integer,PlayerBotWorldPopulation.Pool> pools(boolean server){var result=new TreeMap<Integer,PlayerBotWorldPopulation.Pool>();pools.forEach((id,text)->result.put(id,JSON.parseObject(text,PlayerBotWorldPopulation.Pool.class)));return result;}
 public void pool(boolean server,PlayerBotWorldPopulation.Pool next){pools.put(next.account(),JSON.toJSONString(next));}
 public PlayerBotPopulationSchedule.State schedule(){return JSON.parseObject(schedule,PlayerBotPopulationSchedule.State.class);}
 public void schedule(PlayerBotPopulationSchedule.State next){schedule=JSON.toJSONString(next);}
 public void event(int id,PlayerBotPopulationSchedule.Events next){var old=schedule();var events=new TreeMap<>(old.events());events.put(id,next);schedule(new PlayerBotPopulationSchedule.State(1,old.target(),old.targetUntil(),events));}
}
