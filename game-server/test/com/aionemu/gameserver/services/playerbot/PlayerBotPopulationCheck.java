package com.aionemu.gameserver.services.playerbot;

import java.nio.file.*;
import java.util.*;
import java.io.IOException;
import com.aionemu.gameserver.model.*;
import com.aionemu.gameserver.model.templates.world.WorldMapTemplate;
import jakarta.xml.bind.JAXBContext;
import java.io.StringReader;

/** Seeded population policy, actual event/pool persistence and native map guards; no world/DB/IDs. */
public final class PlayerBotPopulationCheck {
 static int checks;
 static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 interface Attempt{void run()throws Exception;}
 static void refused(Attempt a)throws Exception{try{a.run();throw new AssertionError("Invalid state admitted");}catch(IllegalArgumentException|IOException e){checks++;}}
 static PlayerBotPopulationPolicy.Settings settings(int low,int high,int minChance,int maxChance,boolean random,boolean sync){
  return new PlayerBotPopulationPolicy.Settings(40,60,12,50,low,high,minChance,maxChance,random,7,sync,false,600,28800,600,28800,1800,7200,3600,18000,25);
 }
 static WorldMapTemplate map(String attributes)throws Exception{
  return (WorldMapTemplate)JAXBContext.newInstance(WorldMapTemplate.class).createUnmarshaller().unmarshal(new StringReader("<map id='210030000' cName='check' world_size='4096' water_level='16' death_level='0' "+attributes+"/>"));
 }
 public static void main(String[]args)throws Exception {
  var s=settings(1,65,10,10,true,false);var random=new Random(91871);var levels=new HashSet<Integer>();int ones=0,max=0;
  for(int i=0;i<10000;i++){int n=PlayerBotPopulationPolicy.level(s,1,random);levels.add(n);if(n==1)ones++;if(n==65)max++;}
  check(levels.size()==65,"Global initialization covers all native levels");check(ones>950 && ones<1450 && max>950 && max<1450,"Upstream min/max probability bands preserved");
  check(PlayerBotPopulationPolicy.level(settings(1,65,0,100,true,false),1,random)==65,"Maximum probability");
  check(PlayerBotPopulationPolicy.level(settings(1,65,100,0,true,false),65,random)==1,"Minimum probability");
  check(PlayerBotPopulationPolicy.level(settings(1,65,0,100,true,true),10,random)==13,"Player level sync adds three and respects upper limit");
  check(PlayerBotPopulationPolicy.level(settings(20,65,0,100,true,true),1,random)==20,"Sync never falls below configured minimum");
  check(PlayerBotPopulationPolicy.level(settings(1,65,0,0,false,false),65,random)==7,"Fixed starting-level mode");
  refused(()->settings(0,65,0,0,true,false));refused(()->settings(20,10,0,0,true,false));refused(()->settings(1,66,0,0,true,false));refused(()->settings(1,65,60,50,true,false));
  Set<Integer> accounts=new HashSet<>();for(int i=0;i<2048;i++)for(Race race:List.of(Race.ELYOS,Race.ASMODIANS))accounts.add(PlayerBotPopulationPolicy.account(race,i));
  check(accounts.size()==4096 && accounts.stream().allMatch(PlayerBotPopulationPolicy::serverAccount),"Disjoint unique bounded headless identities");
  for(int id:List.of(0,-1,-999999,-1024000,Integer.MIN_VALUE))check(!PlayerBotPopulationPolicy.account(id),"Arbitrary nonpositive account refused");
  check(PlayerBotPopulationPolicy.account(1) && PlayerBotPopulationPolicy.account(Integer.MAX_VALUE),"Existing player accounts remain valid");
  refused(()->PlayerBotPopulationPolicy.account(Race.DRAKAN,0));refused(()->PlayerBotPopulationPolicy.account(Race.ELYOS,12000));
  var candidates=new ArrayList<PlayerBotPopulationPolicy.Candidate>();for(int id=1;id<=100;id++)candidates.add(new PlayerBotPopulationPolicy.Candidate(id,id<=50?Race.ELYOS:Race.ASMODIANS));
  for(int percent:List.of(0,10,50,90,100)){
   var admitted=PlayerBotPopulationPolicy.admit(candidates,20,percent,random);
   check(admitted.size()==20 && new HashSet<>(admitted).size()==20,"Bounded unique admission");
   check(admitted.stream().filter(id->id<=50).count()==percent/5,"Configured faction ratio");
  }
  check(PlayerBotPopulationPolicy.admit(candidates.stream().filter(c->c.race()==Race.ELYOS).toList(),20,0,random).size()==20,"Upstream final Alliance phase fills a depleted faction");
  check(PlayerBotPopulationPolicy.admit(candidates,0,50,random).isEmpty(),"Full capacity causes no admissions");
  check(PlayerBotPopulationPolicy.admit(candidates,20,50,new Random(1)).equals(PlayerBotPopulationPolicy.admit(candidates,20,50,new Random(1))),"Seeded candidate ordering reproducible");
  check(!PlayerBotPopulationPolicy.admit(candidates,20,50,new Random(1)).equals(PlayerBotPopulationPolicy.admit(candidates,20,50,new Random(2))),"Different batches do not select only first characters/classes");
  long deadline=PlayerBotPopulationPolicy.deadline(100,random,30,30);check(deadline==30100,"Timer uses seconds and retains exact expiry");
  // Recording JDBC exercises the actual custody DAOs, without connecting to a database.
  var db=new PlayerBotMetadataCheck.Jdbc();db.account=-1000000;
  com.aionemu.gameserver.dao.PlayerBotMetadataDAO.owner(db.connection(),-1000000,91);
  check(db.queries==1,"Reserved namespace still verifies exact native player owner");
  var values=PlayerBotMetadataCheck.care(-1000000,91);PlayerBotMetadata.validate(-1000000,91,"care",values);
  check(values.get("account").equals("-1000000"),"Native metadata validator accepts only reserved identity with matching document");
  db.stored=values;com.aionemu.gameserver.dao.PlayerBotRepositoryDAO.roster(db.connection(),-1000000,91);
  check(db.queries==2,"Reserved roster identity still uses exact native ownership query");
  db.account=7;
  try{com.aionemu.gameserver.dao.PlayerBotMetadataDAO.owner(db.connection(),-1000000,91);throw new AssertionError("Foreign player admitted");}catch(java.sql.SQLException e){check(e.getMessage().contains("mismatch"),"Headless identity never bypasses player custody");}
  try{com.aionemu.gameserver.dao.PlayerBotRepositoryDAO.roster(db.connection(),-1000000,91);throw new AssertionError("Foreign roster admitted");}catch(java.sql.SQLException e){check(e.getMessage().contains("owned"),"Headless identity never bypasses roster custody");}
  int before=db.queries;try{com.aionemu.gameserver.dao.PlayerBotMetadataDAO.owner(db.connection(),-1,91);throw new AssertionError("Arbitrary identity admitted");}catch(java.sql.SQLException e){check(db.queries==before,"Unreserved negative IDs rejected before SQL");}
  var neutral=PlayerBotWorldLooks.neutral();check(neutral.getHeightScale()==1.0f && neutral.getShoulders()==128 && neutral.getVoice()==0,"Native normal-height/neutral-body base, not zero-sized characters");
  var dir=Files.createTempDirectory(Path.of("."),"population-check-");
  try {
   var file=dir.resolve("events.json");var schedule=PlayerBotPopulationTestStorage.schedule(file);check(schedule.load().events().isEmpty(),"First schedule empty without allocating characters");
   var event=new PlayerBotPopulationSchedule.Events(123456,0,234567,42);
   schedule.save(new PlayerBotPopulationSchedule.State(1,51,345678,Map.of(91,event)));schedule.event(92,new PlayerBotPopulationSchedule.Events(0,987654,876543,65));
   var restored=PlayerBotPopulationTestStorage.schedule(file).load();check(restored.target()==51 && restored.targetUntil()==345678 && restored.events().get(91).equals(event),"Actual serializer retains target and per-character timers across restart");
   check(restored.events().get(92).placedLevel()==65 && restored.events().get(92).offlineUntil()==987654,"Existing placement and offline state retained");
   schedule.event(91,new PlayerBotPopulationSchedule.Events(0,99999,event.teleportAt(),event.placedLevel()));
   check(schedule.load().events().get(91).placedLevel()==42,"Presence departure does not reset level/placement/progress");
   refused(()->new PlayerBotPopulationSchedule.Events(-1,0,0,1));refused(()->new PlayerBotPopulationSchedule.Events(0,0,0,66));refused(()->new PlayerBotPopulationSchedule.State(2,1,1,Map.of()));
   Files.writeString(file,"{bad");refused(()->PlayerBotPopulationLegacy.schedule(file));check(Files.readString(file).equals("{bad"),"Invalid schedule is retained, never rerolled");
   var poolFile=dir.resolve("pools.json");var pool=new PlayerBotWorldPopulation.Pool(-1000000,"world-population",(byte)0,1,Race.ELYOS,Gender.MALE,PlayerBotWorldLooks.neutral(),20,List.of(91));
   check(PlayerBotWorldPopulation.planned(new PlayerBotWorldPopulation.Pool(7,"existing",(byte)0,1,Race.ELYOS,Gender.MALE,PlayerBotWorldLooks.neutral(),20,List.of(91)),91)==PlayerBotWorldPopulation.playerClass(91),"Existing player-provisioned specialization unchanged");
   var classPool=pool;var classes=new HashSet<PlayerClass>();
   for(int id=100;id<120;id++){classPool=classPool.append(id);classes.add(PlayerBotWorldPopulation.planned(classPool,id));}
   check(classes.size()==11,"Every native specialization provisioned despite item ID allocation between characters");
   check(PlayerBotWorldPopulation.planned(classPool,110)==PlayerBotWorldPopulation.planned(classPool.desired(20),110),"Specialization stable through pool reload");
   refused(()->PlayerBotWorldPopulation.planned(pool,999));
   PlayerBotPopulationTestStorage.population(poolFile).put(pool);check(PlayerBotPopulationTestStorage.population(poolFile).load().get(-1000000).characters().equals(List.of(91)),"Headless pool survives actual JSON round trip");
   var roster=PlayerBotPopulationTestStorage.roster(dir.resolve("roster.properties"));var r=new PlayerBotWorldRoster.Resident(-1000000,91,210030000,12,23,34,(byte)0);roster.save(Map.of(91,r));check(roster.load().get(91).equals(r),"Native signed account identity retained by residency persistence");
  }finally{try(var paths=Files.walk(dir)){for(var p:paths.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}}
  var elyos=map("world_type='ELYSEA'");var asmo=map("world_type='ASMODAE'");var shared=map("world_type='BALAUREA'");
  check(PlayerBotWorldPlacement.mapEligible(elyos,Race.ELYOS,Set.of()),"Own open-world map");check(!PlayerBotWorldPlacement.mapEligible(elyos,Race.ASMODIANS,Set.of()),"Opposing home territory refused");
  check(PlayerBotWorldPlacement.mapEligible(asmo,Race.ASMODIANS,Set.of()) && !PlayerBotWorldPlacement.mapEligible(asmo,Race.ELYOS,Set.of()),"Asmodian symmetry");
  for(Race race:List.of(Race.ELYOS,Race.ASMODIANS))check(PlayerBotWorldPlacement.mapEligible(shared,race,Set.of()),"Shared world map available to each faction");
  check(!PlayerBotWorldPlacement.mapEligible(shared,Race.ELYOS,Set.of(1)),"Explicit map allowlist honored");
  for(String type:List.of("world_type='NONE'","world_type='PRISON'","world_type='PANESTERRA'","world_type='ELYSEA' instance='true'","world_type='ELYSEA' prison='true'"))check(!PlayerBotWorldPlacement.mapEligible(map(type),Race.ELYOS,Set.of()),"Unadapted siege/test/instance/prison map excluded");
  check(PlayerBotWorldPlacement.ground(elyos,100,100,20,20),"Finite native ground");
  for(float z:List.of(Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY,15f,30f,-1f))check(!PlayerBotWorldPlacement.ground(elyos,100,100,20,z),"Missing/flooded/death/wrong floor refused");
  check(!PlayerBotWorldPlacement.ground(elyos,5000,100,20,20),"Out-of-world coordinates refused");
  var hub=new PlayerBotWorldPlacement.Location(Race.ELYOS,2,100,100,20,20,35,false,true);
  var grind=new PlayerBotWorldPlacement.Location(Race.ELYOS,3,100,100,20,20,35,false,false);
  var city=new PlayerBotWorldPlacement.Location(Race.ELYOS,4,100,100,20,10,65,true,true);
  var opposite=new PlayerBotWorldPlacement.Location(Race.ASMODIANS,2,100,100,20,20,35,false,true);
  var locations=List.of(hub,grind,city,opposite);
  check(PlayerBotWorldPlacement.select(locations,Race.ELYOS,25,Set.of(),Set.of(),false,0,random).equals(List.of(grind)),"Field preference chooses monster camps ahead of service hubs");
  check(PlayerBotWorldPlacement.select(locations,Race.ELYOS,25,Set.of(),Set.of(),false,100,random).equals(List.of(city)),"City probability uses banker destinations");
  check(PlayerBotWorldPlacement.select(locations,Race.ELYOS,5,Set.of(),Set.of(),false,100,random).isEmpty(),"Starting bot cannot enter high-level locations");
  check(PlayerBotWorldPlacement.select(locations,Race.ASMODIANS,25,Set.of(),Set.of(),false,0,random).equals(List.of(opposite)),"Destination faction suitability");
  check(PlayerBotWorldPlacement.select(locations,Race.ELYOS,25,Set.of(3),Set.of(),false,0,random).equals(List.of(grind)),"Configured map restriction retains suitable grind fallback");
  var other=new PlayerBotWorldPlacement.Location(Race.ELYOS,5,100,100,20,20,35,false,true);
  check(PlayerBotWorldPlacement.select(List.of(hub,other),Race.ELYOS,25,Set.of(),Set.of(5),true,0,random).equals(List.of(other)),"Optional player map concentration");
  check(PlayerBotWorldPlacement.select(List.of(hub,other),Race.ELYOS,25,Set.of(),Set.of(8),true,0,random).size()==2,"No suitable player map falls back to global locations");
  System.out.println("OK: "+checks+" global population policy/persistence/native-map checks; no world/DB/IDs");
 }
}
