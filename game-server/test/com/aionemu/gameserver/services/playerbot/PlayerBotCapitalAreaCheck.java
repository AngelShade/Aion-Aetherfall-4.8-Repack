package com.aionemu.gameserver.services.playerbot;

import java.nio.file.Path;
import java.util.*;
import jakarta.xml.bind.JAXBContext;
import com.aionemu.gameserver.dataholders.ZoneData;
import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.model.templates.zone.ZoneInfo;

/** Actual Sanctum native 3D areas and focused camp ordering; no World/IDs/SQL/tasks. */
public final class PlayerBotCapitalAreaCheck {
 private static int checks;
 private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 private static PlayerBotWorldPlacement.Location camp(int map,float x,float y,float z){return new PlayerBotWorldPlacement.Location(Race.ELYOS,map,x,y,z,10,65,true,true);}
 private static PlayerBotPopulationFocus.Snapshot focus(float z){return new PlayerBotPopulationFocus.Snapshot(List.of(new PlayerBotPopulationFocus.Anchor(110010000,1855.6659f,1518.5562f,z)),900);}
 private static List<PlayerBotWorldPlacement.Location> prefer(List<PlayerBotWorldPlacement.Location> choices,PlayerBotPopulationFocus.Snapshot focus,Map<Integer,Integer> counts,Map<Integer,List<ZoneInfo>> zones)throws Exception {
  // On installed055 this is the exact original destination ordering seam.
  // The corrected candidate uses the actual maintained placement entrypoint.
  try{return invoke(choices,focus,counts,zones);}catch(NoSuchMethodException legacy){return focus.prefer(choices,counts);}
 }
 @SuppressWarnings("unchecked") private static List<PlayerBotWorldPlacement.Location> invoke(List<PlayerBotWorldPlacement.Location> choices,PlayerBotPopulationFocus.Snapshot focus,Map<Integer,Integer> counts,Map<Integer,List<ZoneInfo>> zones)throws Exception {
  var m=PlayerBotWorldPlacement.class.getDeclaredMethod("preferPlayerAreas",List.class,PlayerBotPopulationFocus.Snapshot.class,Map.class,Map.class);m.setAccessible(true);
  return (List<PlayerBotWorldPlacement.Location>)m.invoke(null,choices,focus,counts,zones);
 }
 public static void main(String[] args)throws Exception {
  var source=Path.of(args[0]);var data=(ZoneData)JAXBContext.newInstance(ZoneData.class).createUnmarshaller().unmarshal(source.resolve("data/static_data/zones/zones_110010000.xml").toFile());
  var zones=Map.of(110010000,List.copyOf(data.getZones(110010000)));
  var upper=camp(110010000,1855.6659f,1518.5562f,805.5178f);var lower=camp(110010000,1857.7472f,1557.3876f,590.00854f);
  var upperZone=PlayerBotPlayerZones.resolve(zones.get(110010000),upper.map(),upper.x(),upper.y(),upper.z());
  var lowerZone=PlayerBotPlayerZones.resolve(zones.get(110010000),lower.map(),lower.x(),lower.y(),lower.z());
  check(upperZone.name().equals("PROTECTORS_HALL_110010000"),"Recorded805 floor is the native Protectors Hall, not malformed elevation");
  check(lowerZone.name().equals("ARTISANS_HALL_110010000"),"Recorded590 floor belongs to separate native Artisans Hall");
  check(!upperZone.equals(lowerZone),"Same capital and nearby XY cannot merge distinct native 3D areas");
  var lowerFocus=focus(590.1f);var upperFocus=focus(805.5178f);var original=List.of(upper,lower);
  check(lowerFocus.nearby(upper.map(),upper.x(),upper.y(),upper.z()) && lowerFocus.nearby(lower.map(),lower.x(),lower.y(),lower.z()),"Reproduce both stacked halls in the installed broad radius tier");
  var ordered=prefer(original,lowerFocus,Map.of(),zones);
  check(ordered.getFirst().equals(lower),"Focused admission prefers actual player native area over shuffled disconnected capital layer");
  check(ordered.size()==2 && new HashSet<>(ordered).equals(new HashSet<>(original)),"All other safely validated camps remain fallback candidates");
  check(prefer(List.of(lower,upper),upperFocus,Map.of(),zones).getFirst().equals(upper),"Player in Protectors Hall still prefers its real upper area; no downward flattening");
  var offMap=camp(210010000,500,500,100);
  check(prefer(List.of(offMap,upper,lower),lowerFocus,Map.of(),zones).equals(ordered),"Native area ranking retains strict focused-map admission");
  var two=new PlayerBotPopulationFocus.Snapshot(List.of(new PlayerBotPopulationFocus.Anchor(110010000,1855.6659f,1518.5562f,590.1f),new PlayerBotPopulationFocus.Anchor(210010000,500,500,100)),900);
  check(prefer(List.of(upper,lower,offMap),two,Map.of(110010000,500,210010000,10),zones).getFirst().equals(offMap),"Least populated player map retains priority over a populated map's exact area");
  check(prefer(List.of(upper,lower,offMap),two,Map.of(110010000,10,210010000,500),zones).getFirst().equals(lower),"Native area priority applies inside the map-demand tier");
  check(prefer(original,lowerFocus,Map.of(),Map.of()).equals(lowerFocus.prefer(original,Map.of())),"Missing zone metadata preserves previous geographic safe fallback");
  var unmapped=new PlayerBotPopulationFocus.Snapshot(List.of(new PlayerBotPopulationFocus.Anchor(110010000,10,10,1)),900);
  check(prefer(original,unmapped,Map.of(),zones).equals(unmapped.prefer(original,Map.of())),"Unmapped player geography cannot invent a match or suppress camps");
  var empty=new PlayerBotPopulationFocus.Snapshot(List.of(),900);
  check(prefer(original,empty,Map.of(),zones).equals(original),"No live player preserves worldwide ordering and capital residents");
  check(prefer(List.of(upper),lowerFocus,Map.of(),zones).equals(List.of(upper)),"No compatible native area does not remove the remaining safe fallback");
  check(prefer(List.of(),lowerFocus,Map.of(),zones).isEmpty(),"Absent candidates cannot fabricate a native camp");
  try{ordered.clear();throw new AssertionError("Mutable placement ordering");}catch(UnsupportedOperationException expected){check(true,"Published preference is immutable");}
  for(int seed=0;seed<100;seed++){
   var shuffled=new ArrayList<>(original);Collections.shuffle(shuffled,new Random(seed));var next=prefer(shuffled,lowerFocus,Map.of(),zones);
   check(next.getFirst().equals(lower) && next.contains(upper),"Shuffling never aliases stacked areas or removes safe fallback");
  }
  System.out.println("OK: "+checks+" actual native capital-area, elevation, map-demand and preserved-fallback checks; no World/IDs/SQL/tasks");
 }
 private PlayerBotCapitalAreaCheck(){}
}
