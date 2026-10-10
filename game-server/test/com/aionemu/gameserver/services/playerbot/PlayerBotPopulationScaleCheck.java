package com.aionemu.gameserver.services.playerbot;

import java.nio.file.*;
import java.util.*;
import java.io.IOException;
import com.aionemu.gameserver.model.Race;

/** Production policy and real temporary-file round trips at scale; no game/DB/native ID allocation. */
public final class PlayerBotPopulationScaleCheck {
 private static int checks;
 private static void check(boolean result,String why){checks++;if(!result)throw new AssertionError(why);}
 interface Attempt{void run()throws Exception;}
 private static void refused(Attempt attempt)throws Exception{
  try{attempt.run();throw new AssertionError("Out-of-range scale accepted");}catch(IllegalArgumentException|IOException expected){checks++;}
 }
 private static PlayerBotPopulationPolicy.Settings settings(int min,int max,int budget){
  return new PlayerBotPopulationPolicy.Settings(min,max,budget,50,1,65,10,65,true,1,false,true,600,28800,600,28800,1800,7200,3600,18000,35);
 }
 public static void main(String[] args)throws Exception {
  if(args.length>0 && args[0].equals("baseline")){
   refused(()->settings(2800,3000,120));
   refused(()->new PlayerBotPopulationSchedule.State(1,3000,1,Map.of()));
   refused(()->PlayerBotPopulationRotation.poolCapacity(3000,3000,true,2,23));
   System.out.println("OK: "+checks+" installed 1000-target/4000-reserve baseline barriers reproduced; no world/DB/IDs");return;
  }
  check(settings(2800,3000,120).maximum()==3000,"Requested configuration admitted");
  check(settings(0,3000,1).minimum()==0,"Zero minimum remains valid");
  refused(()->settings(2800,3001,120));refused(()->settings(3001,3000,120));
  refused(()->settings(-1,3000,120));refused(()->settings(2800,3000,0));refused(()->settings(2800,3000,121));
  for(int maximum:List.of(1000,2800,3000))for(int capacity:List.of(1,3,23,100))for(int multiplier:List.of(1,2,4)){
   int required=maximum*multiplier;
   int expected=Math.min(12000,((required+capacity-1)/capacity)*capacity);
   check(PlayerBotPopulationRotation.poolCapacity(maximum,maximum,multiplier>1,multiplier,capacity)==expected,"Pinned factory arithmetic at requested scale");
  }
  check(PlayerBotPopulationRotation.poolCapacity(3000,1000,true,2,23)==2001,"Native world limit retains bounded reserve basis");
  check(PlayerBotPopulationRotation.poolCapacity(3000,3000,true,4,23)==12000,"Final partial account cannot exceed persistence ceiling");
  check(PlayerBotPopulationRotation.poolCapacity(3000,3000,false,4,23)==3013,"Permanent mode keeps native account rounding");
  check(PlayerBotPopulationRotation.plan(3000,3000,true,2,23,3000,2000,2800,2800,120).admissionSlots()==120,"120 is a candidate budget");
  var full=PlayerBotPopulationRotation.plan(3000,3000,true,2,23,3000,2000,3000,3000,120);
  check(full.admissionSlots()==0 && full.provisionSlots()==1,"Full active world continues finite reserve provisioning");
  check(PlayerBotPopulationRotation.plan(3000,3000,true,2,23,3000,6003,2800,2990,120).admissionSlots()==10,"Manual residents consume actual world capacity");
  check(PlayerBotPopulationRotation.plan(3000,3000,true,2,23,3000,6003,3000,3000,120).provisionSlots()==0,"Complete reserve creates no replacement IDs");
  Set<Integer> accounts=new HashSet<>();boolean valid=true;
  for(int shard=0;shard<12000;shard++)for(Race race:List.of(Race.ELYOS,Race.ASMODIANS)){
   int id=PlayerBotPopulationPolicy.account(race,shard);valid &= id<0 && PlayerBotPopulationPolicy.serverAccount(id) && accounts.add(id);
   if(shard<2048)valid &= id==-1000000-2*shard-(race==Race.ELYOS?0:1);
  }
  check(valid && accounts.size()==24000,"Full one-character, one-faction reserve fits; original account IDs remain unchanged");
  check(!PlayerBotPopulationPolicy.serverAccount(-1024000) && !PlayerBotPopulationPolicy.serverAccount(-999999),"Both namespace boundaries reject foreign accounts");
  refused(()->PlayerBotPopulationPolicy.account(Race.ELYOS,12000));refused(()->PlayerBotPopulationPolicy.account(Race.ASMODIANS,-1));
  var candidates=new ArrayList<PlayerBotPopulationPolicy.Candidate>();
  for(int id=1;id<=6003;id++)candidates.add(new PlayerBotPopulationPolicy.Candidate(id,id%2==0?Race.ELYOS:Race.ASMODIANS));
  var admitted=PlayerBotPopulationPolicy.admit(candidates,120,50,new Random(42));
  check(admitted.size()==120 && new HashSet<>(admitted).size()==120,"Large saved reserve retains unique bounded admission");
  check(admitted.stream().filter(id->id%2==0).count()==60,"Both factions retain balanced admissions");
  Map<Integer,PlayerBotPopulationSchedule.Events> events=new TreeMap<>();
  for(int id=1;id<=12000;id++)events.put(id,new PlayerBotPopulationSchedule.Events(id,2L*id,3L*id,1+id%65));
  var maximum=new PlayerBotPopulationSchedule.State(1,3000,7200000,events);
  check(maximum.events().size()==12000 && maximum.target()==3000,"Maximum persisted target and rotation reserve admitted");
  var excess=new TreeMap<>(events);excess.put(12001,new PlayerBotPopulationSchedule.Events(0,0,0,1));
  refused(()->new PlayerBotPopulationSchedule.State(1,3000,1,excess));
  refused(()->new PlayerBotPopulationSchedule.State(1,3001,1,events));
  var directory=Files.createTempDirectory(Path.of("."),"population-scale-");
  try{
   Path file=directory.resolve("events.json");var store=PlayerBotPopulationTestStorage.schedule(file);
   var old=new PlayerBotPopulationSchedule.State(1,805,12345,Map.of(99,events.get(99)));
   store.save(old);check(PlayerBotPopulationTestStorage.schedule(file).load().equals(old),"Old version-1 events load without migration or reset");
   store.save(maximum);check(PlayerBotPopulationTestStorage.schedule(file).load().equals(maximum),"All 12000 native event records survive real JSON save/load");
   var reloaded=PlayerBotPopulationTestStorage.schedule(file);reloaded.event(99,new PlayerBotPopulationSchedule.Events(10,20,30,40));
   var next=PlayerBotPopulationTestStorage.schedule(file).load();
   check(next.events().size()==12000 && next.events().get(100).equals(events.get(100)) && next.target()==3000,"Single-event update retains every unrelated saved event");
   String before=com.alibaba.fastjson2.JSON.toJSONString(store.load());Files.writeString(file,before.replace("\"target\":3000","\"target\":3001"));
   String invalid=Files.readString(file);refused(()->PlayerBotPopulationLegacy.schedule(file));
   check(Files.readString(file).equals(invalid),"Invalid persisted target remains visible and is not rewritten");
  }finally{try(var paths=Files.walk(directory)){for(var p:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}}
  System.out.println("OK: "+checks+" 3000-world/12000-reserve/120-admission policy and persistence checks; no world/DB/native IDs");
 }
}
