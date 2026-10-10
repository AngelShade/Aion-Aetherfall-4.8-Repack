package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import com.aionemu.gameserver.dataholders.*;
import com.aionemu.gameserver.model.Race;

/** Uses the production JAXB result only. Never initializes World, actors, geometry or a database. */
public final class PopulationCatalogBuildCheck {
 public static void check(StaticData data) {
  DataManager.WORLD_MAPS_DATA=data.worldMapsData;DataManager.NPC_DATA=data.npcData;
  DataManager.SPAWNS_DATA=data.spawnsData;DataManager.TRIBE_RELATIONS_DATA=data.tribeRelationsData;
  DataManager.TELEPORTER_DATA=data.teleporterData;DataManager.TELELOCATION_DATA=data.teleLocationData;DataManager.PLAYER_INITIAL_DATA=data.playerInitialData;
  var locations=PlayerBotWorldPlacement.locations();
  if(locations.isEmpty())throw new AssertionError("No native population locations");
  var report=new TreeMap<String,SortedSet<Integer>>();
  for(var p:locations) {
   var map=DataManager.WORLD_MAPS_DATA.getTemplate(p.map());
   if(!PlayerBotWorldPlacement.mapEligible(map,p.race(),Set.of()))throw new AssertionError("Population includes excluded map "+p.map());
   report.computeIfAbsent(p.race()+":"+p.map(),k->new TreeSet<>()).addAll(java.util.stream.IntStream.rangeClosed(p.minimum(),p.maximum()).boxed().toList());
  }
  for(var race:List.of(Race.ELYOS,Race.ASMODIANS))for(int level=1;level<=65;level++) {
   var selected=PlayerBotWorldPlacement.select(locations,race,level,Set.of(),Set.of(),false,0,new Random(level));
   if(selected.isEmpty())
    throw new AssertionError("No native placement for "+race+" level "+level);
   if(level>5 && selected.stream().anyMatch(p->p.hub() || p.city()))throw new AssertionError("Field intent selected a service camp for "+race+" level "+level);
   if(selected.stream().anyMatch(p->p.map()==120080000 || p.map()==110070000))throw new AssertionError("Arena lobby selected for field intent");
  }
  System.out.println("OK: 130 native faction/level field selections exclude arena lobbies; advanced selections contain only monster camps");
  // Upstream puts native race starting positions in the preferred hub cache for
  // levels 1..5. Merely retaining them in the ordinary catalog is insufficient:
  // a nonempty service-hub tier would exclude them before live floor checks.
  for(var race:List.of(Race.ELYOS,Race.ASMODIANS)) {
   var start=DataManager.PLAYER_INITIAL_DATA.getSpawnLocation(race);
   for(int level=1;level<=5;level++) {
    var selected=PlayerBotWorldPlacement.select(locations,race,level,Set.of(),Set.of(),false,0,new Random(level));
    if(selected.stream().noneMatch(p->p.map()==start.getMapId() && p.x()==start.getX() && p.y()==start.getY() && p.z()==start.getZ()))
     throw new AssertionError("Native starting position omitted from preferred destinations for "+race+" level "+level);
   }
  }
  System.out.println("OK: all 10 faction/starter-level selections retain their native starting position before live validation");
  com.aionemu.gameserver.configs.main.PlayerBotConfig.POPULATION_MAPS=Set.of();
  var heiron=new PlayerBotPopulationFocus.Snapshot(List.of(new PlayerBotPopulationFocus.Anchor(210040000,1000,1000,100)),900);
  var camps=PlayerBotWorldPlacement.focusChoices(Race.ELYOS,43,heiron,new Random(1));
  if(camps.isEmpty() || camps.stream().anyMatch(p->p.map()!=210040000 || p.hub() || p.city()))throw new AssertionError("Level-43 Heiron focus has no native monster camps");
  if(!PlayerBotWorldPlacement.focusChoices(Race.ASMODIANS,43,heiron,new Random(1)).isEmpty())throw new AssertionError("Focus bypassed faction home restrictions");
  var capital=new PlayerBotPopulationFocus.Snapshot(List.of(new PlayerBotPopulationFocus.Anchor(110010000,1000,1000,100)),900);
  if(PlayerBotWorldPlacement.focusChoices(Race.ELYOS,43,capital,new Random(1)).isEmpty())throw new AssertionError("Capital focus omitted native RPG/service residents");
  for(var race:List.of(Race.ELYOS,Race.ASMODIANS))for(int level=1;level<=65;level++) {
   var fields=PlayerBotWorldPlacement.focusChoices(race,level,heiron,new Random(1));
   if(fields.stream().anyMatch(p->p.hub() || p.city()))throw new AssertionError("Field focus fell back to a service hub for bot level "+level);
   for(var demand:List.of(heiron,capital))if(PlayerBotWorldPlacement.focusCompatible(race,level,demand)
    !=!PlayerBotWorldPlacement.focusChoices(race,level,demand,new Random(1)).isEmpty())throw new AssertionError("Cached compatibility differs from native placement");
  }
  System.out.println("OK: 260 native faction/bot-level compatibility decisions agree with camp selection; spatial demand has no human level");
  System.out.println("OK: level-43 Heiron focus has "+camps.size()+" native monster camps; enemy faction excluded; native capital RPG destinations retained");
  System.out.println("OK: population catalog has "+locations.size()+" native destinations; all 130 faction/level combinations covered; no world/DB/IDs");
  try {
   var method=PlayerBotWorldTravel.class.getDeclaredMethod("edges");method.setAccessible(true);
   @SuppressWarnings("unchecked") var graph=(List<PlayerBotWorldTravel.Edge>)method.invoke(null);
   if(graph.isEmpty())throw new AssertionError("Native transport graph is empty");
   var gates=new java.util.concurrent.atomic.AtomicInteger();long began=System.nanoTime();long decisions=0;
   // Real native catalog, deterministic gate callback. No actor/quest/cost services are executed.
   for(int pass=0;pass<60;pass++) {
    var start=locations.get(pass%locations.size());
    var routes=PlayerBotWorldTravel.routes(start.map(),graph,e->{gates.incrementAndGet();return e.location()%7!=0;});
    var distance=new PlayerBotWorldTravel.Distances(start.map(),start.x(),start.y(),start.z(),routes);
    for(var place:locations){double d=distance.distance(new PlayerBotWorldTravel.Place(place.map(),1,place.x(),place.y(),place.z()));if(Double.isNaN(d))throw new AssertionError("NaN travel score");decisions++;}
   }
   if(gates.get()!=60*graph.size())throw new AssertionError("Repeated permission work per destination");
   System.out.println("OK: native travel planning benchmark: "+graph.size()+" edges; "+decisions+" destination scores; "+gates+" permission checks; "+((System.nanoTime()-began)/1_000_000)+"ms; 60 planning passes; no World/DB/IDs");
  }catch(ReflectiveOperationException e){throw new AssertionError(e);}
  report.forEach((map,levels)->System.out.println("Population map "+map+" levels "+levels));
 }
}
