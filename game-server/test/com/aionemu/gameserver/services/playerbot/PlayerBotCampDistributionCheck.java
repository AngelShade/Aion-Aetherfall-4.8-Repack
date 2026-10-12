package com.aionemu.gameserver.services.playerbot;

import java.lang.reflect.*;
import java.nio.file.Path;
import java.util.*;
import javax.xml.bind.JAXBContext;
import com.aionemu.gameserver.dataholders.ZoneData;
import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.model.templates.zone.ZoneInfo;
import com.aionemu.gameserver.services.playerbot.PlayerBotNavigation.Point;

/** Actual fresh placement index and native area metadata; no World/IDs/SQL/spawning. */
public final class PlayerBotCampDistributionCheck {
 private static int checks;
 private static Constructor<?> constructor,position;
 private static Method preference,clear;
 private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 private static void initialize()throws Exception {
  try {
   var type=Class.forName("com.aionemu.gameserver.services.playerbot.PlayerBotCampDistribution");
   constructor=type.getDeclaredConstructor(Collection.class);constructor.setAccessible(true);
   var pose=Class.forName(type.getName()+"$Position");position=pose.getDeclaredConstructor(int.class,float.class,float.class,float.class);position.setAccessible(true);
   preference=type.getDeclaredMethod("prefer",List.class,Map.class);preference.setAccessible(true);
   clear=type.getDeclaredMethod("clear",int.class,float.class,float.class,float.class);clear.setAccessible(true);
  }catch(ClassNotFoundException original){constructor=null;} // Original admission uses its shuffled native preferences without occupancy.
 }
 private static Object pose(int map,float x,float y,float z)throws Exception{return constructor==null?new Point(x,y,z):position.newInstance(map,x,y,z);}
 private static Object index(List<Object> poses)throws Exception{return constructor==null?null:constructor.newInstance(poses);}
 @SuppressWarnings("unchecked") private static List<PlayerBotWorldPlacement.Location> prefer(Object index,List<PlayerBotWorldPlacement.Location> choices,Map<Integer,Integer> counts)throws Exception{return index==null?choices:(List<PlayerBotWorldPlacement.Location>)preference.invoke(index,choices,counts);}
 private static boolean clear(Object index,int map,Point p)throws Exception{return index==null || (boolean)clear.invoke(index,map,p.x(),p.y(),p.z());}
 private static Point point(PlayerBotWorldPlacement.Location p){return new Point(p.x(),p.y(),p.z());}
 private static PlayerBotWorldPlacement.Location camp(int map,float x,float y,float z){return new PlayerBotWorldPlacement.Location(Race.ELYOS,map,x,y,z,10,65,true,true);}
 private static void nativeAreas(Path source)throws Exception {
  var data=(ZoneData)JAXBContext.newInstance(ZoneData.class).createUnmarshaller().unmarshal(source.resolve("data/static_data/zones/zones_110010000.xml").toFile());var zones=Map.of(110010000,List.copyOf(data.getZones(110010000)));
  var upper=camp(110010000,1875.9481f,1530.2068f,805.5178f);var lower=camp(110010000,1857.7472f,1557.3876f,590.00854f);
  var focus=new PlayerBotPopulationFocus.Snapshot(List.of(new PlayerBotPopulationFocus.Anchor(110010000,1855.6659f,1518.5562f,805.5178f)),900);
  var old=PlayerBotWorldPlacement.preferPlayerAreas(List.of(upper,lower),focus,Map.of(),zones);
  check(old.getFirst().equals(upper),"Native player-area preference alone reproduces priority of the captured dense Hall layer");
  var poses=new ArrayList<Object>();for(int i=0;i<67;i++)poses.add(pose(110010000,1875.9481f+(i%7)*.1f,1530.2068f+(i/7)*.1f,805.5178f));
  var ordered=prefer(index(poses),old,Map.of());
  check(ordered.getFirst().equals(lower),"Fresh occupancy spreads admissions beyond an already crowded preferred native area; original ordering repeats it");
  check(ordered.size()==2 && ordered.contains(upper),"Dense native camps remain eligible fallback rather than lowering the population target");
  check(PlayerBotPlayerZones.resolve(zones.get(110010000),110010000,upper.x(),upper.y(),upper.z()).name().equals("PROTECTORS_HALL_110010000"),"Spreading preserves the actual upper floor and native zone");
  check(PlayerBotPlayerZones.resolve(zones.get(110010000),110010000,lower.x(),lower.y(),lower.z()).name().equals("ARTISANS_HALL_110010000"),"Spreading preserves lower native area without flattening elevation");
  check(clear(index(poses),110010000,point(lower)),"Occupancy on another floor cannot falsely occupy a native lower landing");
 }
 private static void regions()throws Exception {
  var busy=camp(1,49,20,0);var edge=camp(1,51,20,0);var free=camp(1,160,20,0);var otherMap=camp(2,51,20,0);
  var poses=new ArrayList<Object>();poses.add(pose(1,49,20,0));
  var indexed=index(poses);
  check(prefer(indexed,List.of(edge,free),Map.of()).getFirst().equals(free),"50m neighborhood spans grid boundaries instead of moving a cluster two metres");
  check(prefer(indexed,List.of(free,otherMap),Map.of(1,0,2,50)).getFirst().equals(free),"Existing least-populated-player-map demand remains first");
  check(prefer(indexed,List.of(free,otherMap),Map.of(1,50,2,0)).getFirst().equals(otherMap),"Unrelated map occupancy cannot override global focus distribution");
  var close=camp(1,55,20,0);var farther=camp(1,90,20,0);
  check(prefer(indexed,List.of(close,farther),Map.of()).getFirst().equals(farther),"Equal regional load prefers more room between actual bot bodies");
  check(!clear(indexed,1,point(busy)) && clear(indexed,2,point(otherMap)),"Landing overlap is map-local and rejects exact repeated body placement");
  check(!clear(indexed,1,new Point(49.9f,20,0)) && clear(indexed,1,new Point(50,20,0)),"One-metre landing clearance has a strict native coordinate boundary");
  check(clear(indexed,1,new Point(49,20,805)),"Vertically separate supported areas never inherit another floor's body occupancy");
  poses.clear();check(prefer(index(poses),List.of(close,free),Map.of()).getFirst().equals(close),"Departure uses fresh live occupancy and preserves shuffled native ties");
  var stable=index(List.of());var original=List.of(close,free);check(prefer(stable,original,Map.of()).equals(original),"No occupancy leaves native area/radius camp preference unchanged");
  try{prefer(stable,original,Map.of()).clear();throw new AssertionError("Mutable ordering");}catch(UnsupportedOperationException expected){check(true,"Published preferred camps are immutable");}
  check(prefer(stable,List.of(),Map.of()).isEmpty(),"No native candidates cannot fabricate a camp");
  var restrictive=new PlayerBotWorldPlacement.Location(Race.ELYOS,1,20,20,0,12,20,false,false);
  check(PlayerBotWorldPlacement.suitable(restrictive,Race.ELYOS,12,Set.of(1)) && !PlayerBotWorldPlacement.suitable(restrictive,Race.ELYOS,65,Set.of(1)),"Existing bot-level band stays authoritative before regional ranking");
  check(!PlayerBotWorldPlacement.suitable(restrictive,Race.ASMODIANS,15,Set.of(1)) && !PlayerBotWorldPlacement.suitable(restrictive,Race.ELYOS,15,Set.of(2)),"Faction and owner map exclusions cannot be relaxed by empty-region demand");
 }
 private static void admission()throws Exception {
  var camps=new ArrayList<PlayerBotWorldPlacement.Location>();for(int y=0;y<10;y++)for(int x=0;x<10;x++)camps.add(camp(1,10+x*100,10+y*100,0));
  var poses=new ArrayList<Object>();int[] admitted=new int[100];
  for(int i=0;i<1500;i++) {
   var indexed=index(poses);var ordered=prefer(indexed,camps,Map.of());Point selected=null;
   for(var p:ordered)if(clear(indexed,1,point(p))){selected=point(p);break;}
   if(selected==null)for(var p:ordered){
    final Object current=indexed;
    selected=PlayerBotCampLanding.nearby(point(p),new PlayerBotCampLanding.Ground(){public float height(float x,float y,float z){return 0;}public boolean safe(Point candidate){try{return clear(current,1,candidate);}catch(Exception e){throw new IllegalStateException(e);}}},()->false);
    if(selected!=null)break;
   }
   check(selected!=null,"Existing bounded native-camp neighborhood can admit the full requested1500 without a regional population cap");
   check(clear(indexed,1,selected),"Every sequential admission avoids already occupied bot bodies");
   int region=Math.round((selected.x()-10)/100)+10*Math.round((selected.y()-10)/100);admitted[region]++;poses.add(pose(1,selected.x(),selected.y(),selected.z()));
  }
  int min=Arrays.stream(admitted).min().orElseThrow(),max=Arrays.stream(admitted).max().orElseThrow();
  check(Arrays.stream(admitted).sum()==1500 && min==15 && max==15,"Repeated admission distributes all1500 across100 candidate regions instead of reusing one anchor");
 }
 public static void main(String[] args)throws Exception{initialize();nativeAreas(Path.of(args[0]));regions();admission();System.out.println("OK: "+checks+" native-area occupancy, region fairness, body clearance and full-target admission checks; no World/IDs/SQL/spawning");}
 private PlayerBotCampDistributionCheck(){}
}
