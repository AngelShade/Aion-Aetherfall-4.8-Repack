package com.aionemu.gameserver.services.playerbot;

import java.io.StringReader;
import java.nio.file.Path;
import java.util.*;
import javax.xml.bind.JAXBContext;

import com.aionemu.commons.configuration.Property;
import com.aionemu.gameserver.configs.main.PlayerBotConfig;
import com.aionemu.gameserver.dataholders.*;
import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.gameobjects.state.CreatureVisualState;
import com.aionemu.gameserver.model.templates.world.WorldMapTemplate;
import com.aionemu.gameserver.model.templates.zone.*;

/** Actual native zone geometry and player snapshots; no World, SQL, native IDs or spawning. */
public final class PlayerBotPlayerZoneCheck {
 private static int checks;
 private static void check(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
 private static final class Human extends Player {
  boolean bot,online,spawned,staff,hidden;int map,instance;float x,y,z;
  private Human(){super(null,null);}
  @Override public boolean isPlayerBot(){return bot;}
  @Override public boolean isOnline(){return online;}
  @Override public boolean isSpawned(){return spawned;}
  @Override public boolean isStaff(){return staff;}
  @Override public boolean isInVisualState(CreatureVisualState state){return hidden && state==CreatureVisualState.HIDE20;}
  @Override public int getWorldId(){return map;}
  @Override public int getInstanceId(){return instance;}
  @Override public float getX(){return x;}
  @Override public float getY(){return y;}
  @Override public float getZ(){return z;}
 }
 private static ZoneInfo rectangle(String name,String type,int priority,float min,float max)throws Exception {
  String xml="<zone mapid='210010000' name='"+name+"' zone_type='"+type+"' priority='"+priority+"'><points bottom='0' top='100'>"
   +"<point x='"+min+"' y='0'/><point x='"+max+"' y='0'/><point x='"+max+"' y='100'/><point x='"+min+"' y='100'/></points></zone>";
  return new ZoneInfo(JAXBContext.newInstance(ZoneTemplate.class).createUnmarshaller()
   .unmarshal(new javax.xml.transform.stream.StreamSource(new StringReader(xml)),ZoneTemplate.class).getValue());
 }
 public static void main(String[] args)throws Exception {
  Path source=Path.of(args[0]);
  var maps=(WorldMapsData)JAXBContext.newInstance(WorldMapsData.class).createUnmarshaller().unmarshal(source.resolve("data/static_data/world_maps.xml").toFile());
  var open=maps.getTemplate(210010000);
  check(PlayerBotPlayerZones.eligible(false,true,true,false,1,open),"Online real player on main open-world map participates");
  check(!PlayerBotPlayerZones.eligible(true,true,true,false,1,open),"Bots cannot concentrate around other bots");
  check(!PlayerBotPlayerZones.eligible(false,false,true,false,1,open),"Logged-out player excluded");
  check(!PlayerBotPlayerZones.eligible(false,true,false,false,1,open),"Loading/unspawned player excluded");
  check(!PlayerBotPlayerZones.eligible(false,true,true,true,1,open),"Hidden staff observer excluded");
  check(!PlayerBotPlayerZones.eligible(false,true,true,false,2,open),"Different instance excluded even on matching map");
  check(!PlayerBotPlayerZones.eligible(false,true,true,false,1,null),"Unknown map excluded");
  var dungeon=maps.iterator();WorldMapTemplate instance=null;while(dungeon.hasNext()){var m=dungeon.next();if(m.isInstance()){instance=m;break;}}
  check(instance!=null && !PlayerBotPlayerZones.eligible(false,true,true,false,1,instance),"Native instance template excluded");
  var west=rectangle("WEST","SUB",20,0,50);var east=rectangle("EAST","SUB",20,50,100);
  var overlay=rectangle("WEATHER","WEATHER",1,0,100);var quest=rectangle("QUEST","ITEM_USE",1,0,100);
  var zones=List.of(west,east,overlay,quest);
  var westKey=new PlayerBotPlayerZones.Zone(210010000,"WEST");var eastKey=new PlayerBotPlayerZones.Zone(210010000,"EAST");
  check(PlayerBotPlayerZones.resolve(zones,210010000,10,10,10).equals(westKey),"Weather/quest overlays do not replace geographic area");
  check(PlayerBotPlayerZones.resolve(zones,210010000,90,10,10).equals(eastKey),"Separate areas on same map resolved separately");
  check(PlayerBotPlayerZones.resolve(zones,210010000,10,10,150).name().isEmpty(),"Native height bounds apply");
  check(PlayerBotPlayerZones.resolve(zones,210010000,200,10,10).equals(new PlayerBotPlayerZones.Zone(210010000,"")),"Unmapped terrain has explicit map-local fallback key");
  var overlap=rectangle("SMALL","SUB",10,0,30);
  check(PlayerBotPlayerZones.resolve(List.of(west,overlap),210010000,10,10,10).name().equals("SMALL"),"Overlap uses native ascending priority");
  var tie=rectangle("AAA","SUB",20,0,50);
  check(PlayerBotPlayerZones.resolve(List.of(west,tie),210010000,10,10,10).name().equals("AAA"),"Equal priority uses native name hash ordering");
  var a=new PlayerBotWorldPlacement.Location(Race.ELYOS,210010000,10,10,10,1,5,false,true);
  var b=new PlayerBotWorldPlacement.Location(Race.ELYOS,210010000,90,10,10,1,5,false,true);
  var c=new PlayerBotWorldPlacement.Location(Race.ELYOS,210010000,20,20,10,1,5,false,true);
  var other=new PlayerBotWorldPlacement.Location(Race.ASMODIANS,220010000,10,10,10,1,5,false,true);
  var choices=PlayerBotWorldPlacement.select(List.of(a,b,c,other),Race.ELYOS,3,Set.of(),Set.of(210010000),true,0,new Random(1));
  check(choices.size()==3,"Existing map-only concentration reproduces both unrelated map areas");
  java.util.function.Function<PlayerBotWorldPlacement.Location,PlayerBotPlayerZones.Zone> resolve=p->PlayerBotPlayerZones.resolve(zones,p.map(),p.x(),p.y(),p.z());
  var concentrated=PlayerBotPlayerZones.prefer(choices,Set.of(westKey),true,resolve);
  check(concentrated.size()==2 && concentrated.contains(a) && concentrated.contains(c),"Occupied zone restricts actual eligible placement choices");
  check(PlayerBotPlayerZones.prefer(choices,Set.of(eastKey),true,resolve).equals(List.of(b)),"Player changing zones changes the next placement snapshot");
  check(PlayerBotPlayerZones.prefer(choices,Set.of(westKey,eastKey),true,resolve).size()==3,"Several real-player zones remain available");
  check(PlayerBotPlayerZones.prefer(choices,Set.of(),true,resolve).equals(choices),"No online player preserves worldwide behavior");
  check(PlayerBotPlayerZones.prefer(choices,Set.of(new PlayerBotPlayerZones.Zone(220010000,"WEST")),true,resolve).equals(choices),"Matching zone name on different map cannot hijack placement");
  check(PlayerBotPlayerZones.prefer(choices,Set.of(westKey),false,resolve).equals(choices),"Disabling preference retains worldwide choices");
  check(PlayerBotPlayerZones.prefer(List.of(),Set.of(westKey),true,resolve).isEmpty(),"No eligible destination remains empty");
  var city=new PlayerBotWorldPlacement.Location(Race.ELYOS,110010000,10,10,10,10,65,true,true);
  check(PlayerBotPlayerZones.prefer(List.of(city),Set.of(westKey),true,resolve).equals(List.of(city)),"Upstream banker/city branch remains separate");
  check(PlayerBotWorldPlacement.select(List.of(a,other),Race.ASMODIANS,3,Set.of(),Set.of(),false,0,new Random(1)).equals(List.of(other)),"Asmodian candidates retain native faction eligibility");
  check(PlayerBotWorldPlacement.select(List.of(a,other),Race.ELYOS,65,Set.of(),Set.of(),false,0,new Random(1)).isEmpty(),"Concentration cannot bypass destination level bands");
  check(PlayerBotWorldPlacement.select(List.of(a,other),Race.ELYOS,3,Set.of(220010000),Set.of(),false,0,new Random(1)).isEmpty(),"Concentration cannot bypass explicit map restriction");
  var picked=new HashSet<PlayerBotWorldPlacement.Location>();
  for(int seed=0;seed<100;seed++) {
   var shuffled=PlayerBotWorldPlacement.select(List.of(a,b,c),Race.ELYOS,3,Set.of(),Set.of(),false,0,new Random(seed*719L));
   var local=PlayerBotPlayerZones.prefer(shuffled,Set.of(westKey),true,resolve);
   check(local.size()==2 && !local.contains(b),"Each seeded ordering retains only the occupied zone");picked.add(local.getFirst());
  }
  check(picked.equals(Set.of(a,c)),"Preference preserves shuffled camp spreading within player zone");
  var f=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);var unsafe=(sun.misc.Unsafe)f.get(null);
  var previousMaps=DataManager.WORLD_MAPS_DATA;var previousZones=DataManager.ZONE_DATA;
  try {
   DataManager.WORLD_MAPS_DATA=maps;
   for(int map:List.of(210010000,220010000)) {
    var nativeZones=(ZoneData)JAXBContext.newInstance(ZoneData.class).createUnmarshaller().unmarshal(source.resolve("data/static_data/zones/zones_"+map+".xml").toFile());
    DataManager.ZONE_DATA=nativeZones;
    ZoneInfo area=nativeZones.getZones(map).stream().filter(z->z.getZoneTemplate().getZoneType()==ZoneClassName.SUB).findFirst().orElseThrow();
    var points=area.getZoneTemplate().getPoints();
    float x=(float)points.getPoints().stream().mapToDouble(Point2D::getX).average().orElseThrow();
    float y=(float)points.getPoints().stream().mapToDouble(Point2D::getY).average().orElseThrow();
    float z=(points.getBottom()+points.getTop())/2;
    check(area.getArea().isInside3D(x,y,z),"Actual faction starter-area centroid lies in native polygon");
    var human=(Human)unsafe.allocateInstance(Human.class);human.map=map;human.instance=1;human.online=true;human.spawned=true;human.x=x;human.y=y;human.z=z;
    var captured=PlayerBotPlayerZones.occupied(Arrays.asList(null,human));
    check(captured.equals(Set.of(PlayerBotPlayerZones.resolve(nativeZones.getZones(map),map,x,y,z))),"Actual online snapshot resolves native starter zone for each faction");
    human.bot=true;check(PlayerBotPlayerZones.occupied(List.of(human)).isEmpty(),"All bot kinds excluded from actual snapshot");human.bot=false;
    human.online=false;check(PlayerBotPlayerZones.occupied(List.of(human)).isEmpty(),"Actual logout removes anchor");human.online=true;
    human.spawned=false;check(PlayerBotPlayerZones.occupied(List.of(human)).isEmpty(),"Actual loading removes anchor");human.spawned=true;
    human.instance=2;check(PlayerBotPlayerZones.occupied(List.of(human)).isEmpty(),"Actual different instance removes anchor");human.instance=1;
    human.staff=true;check(PlayerBotPlayerZones.occupied(List.of(human)).equals(captured),"Visible staff playing normally anchor native Aion population");
    human.hidden=true;check(PlayerBotPlayerZones.occupied(List.of(human)).isEmpty(),"Actual hidden staff observer cannot concentrate population");
   }
  }finally{DataManager.WORLD_MAPS_DATA=previousMaps;DataManager.ZONE_DATA=previousZones;}
  check(PlayerBotConfig.class.getDeclaredField("POPULATION_CONCENTRATE").getAnnotation(Property.class).defaultValue().equals("true"),"Requested player-zone mode enabled by source default");
  System.out.println("OK: "+checks+" native player-zone eligibility, geometry, fallback and spreading checks; no world/DB/IDs");
 }
}
