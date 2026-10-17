package com.aionemu.gameserver.services.playerbot;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import com.aionemu.commons.utils.Json;

/** Read-only, offline migration decoder. Runtime repositories never consult these files. */
final class PlayerBotPopulationLegacy {
 static final List<String> FILES=List.of("world-bots.properties","world-server-population.json","world-population-events.json","world-population.json");
 static String read(Path path) throws IOException {
  if(Files.size(path)>16777216)throw new IOException("Population legacy file exceeds import limit");
  return Files.readString(path);
 }
 static Map<Integer,PlayerBotWorldRoster.Resident> residents(Path path) throws IOException {
  var result=new TreeMap<Integer,PlayerBotWorldRoster.Resident>();
  if(!Files.exists(path))return result;
  var p=new Properties(){@Override public synchronized Object put(Object key,Object value){if(containsKey(key))throw new IllegalArgumentException("Duplicate residence identity");return super.put(key,value);}};
  try {
   if(Files.size(path)>16777216)throw new IOException("Legacy roster exceeds import limit");
   try(var in=Files.newInputStream(path)){p.load(in);}
   for(String key:p.stringPropertyNames()) {
    var v=p.getProperty(key).split(",",-1);if(v.length!=7 || !v[6].equals("1"))throw new IllegalArgumentException("Unknown residence fields/version");
    var r=new PlayerBotWorldRoster.Resident(Integer.parseInt(v[0]),Integer.parseInt(key),Integer.parseInt(v[1]),Float.parseFloat(v[2]),Float.parseFloat(v[3]),Float.parseFloat(v[4]),Byte.parseByte(v[5]));result.put(r.character(),r);
   }
  }catch(RuntimeException e){throw new IOException("Invalid legacy world roster; retain original",e);}
  return result;
 }
 static Map<Integer,PlayerBotWorldPopulation.Pool> pools(Path path) throws IOException {
  var result=new TreeMap<Integer,PlayerBotWorldPopulation.Pool>();if(!Files.exists(path))return result;
  try{var rows=Json.readList(read(path),PlayerBotWorldPopulation.Pool.class);if(rows==null)throw new IllegalArgumentException("Null pools");for(var p:rows)if(result.put(p.account(),p)!=null)throw new IllegalArgumentException("Duplicate population account");}
  catch(RuntimeException e){throw new IOException("Invalid legacy population pools; retain original",e);}return result;
 }
 static PlayerBotPopulationSchedule.State schedule(Path path) throws IOException {
  if(!Files.exists(path))return new PlayerBotPopulationSchedule.State(1,0,0,Map.of());
  try{var state=Json.readLegacyFile(read(path),PlayerBotPopulationSchedule.State.class);if(state==null)throw new IllegalArgumentException("Null schedule");return state;}
  catch(RuntimeException e){throw new IOException("Invalid legacy population schedule; retain original",e);}
 }
 static PlayerBotPopulationRepository.Snapshot snapshot(Path directory) throws IOException {
  try{return new PlayerBotPopulationRepository.Snapshot(residents(directory.resolve(FILES.get(0))),pools(directory.resolve(FILES.get(1))),pools(directory.resolve(FILES.get(3))),schedule(directory.resolve(FILES.get(2))));}
  catch(RuntimeException e){throw new IOException("Legacy population relationships invalid; retain originals",e);}
 }
 private PlayerBotPopulationLegacy(){}
}
