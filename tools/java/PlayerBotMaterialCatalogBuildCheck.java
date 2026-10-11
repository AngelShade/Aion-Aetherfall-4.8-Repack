package com.aionemu.gameserver.services.playerbot;

import java.lang.reflect.*;
import java.util.*;
import com.aionemu.gameserver.dataholders.*;
import com.aionemu.gameserver.model.templates.spawns.*;

/** Actual complete production JAXB catalog, plus malformed native input isolation.
 * This operates on the isolated static-data result; no World, actors, IDs or SQL. */
public final class PlayerBotMaterialCatalogBuildCheck {
 private static Object field(Object object,String name)throws Exception {var f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
 @SuppressWarnings("unchecked") private static int entries(Object catalog,String name)throws Exception{return ((Map<Integer,List<?>>)field(catalog,name)).values().stream().mapToInt(List::size).sum();}
 @SuppressWarnings("unchecked") public static void check(StaticData data)throws Exception {
  DataManager.WORLD_MAPS_DATA=data.worldMapsData;DataManager.SPAWNS_DATA=data.spawnsData;DataManager.NPC_DATA=data.npcData;
  DataManager.CUSTOM_NPC_DROP=data.customNpcDrop;DataManager.GLOBAL_DROP_DATA=data.globalDropData;DataManager.GATHERABLE_DATA=data.gatherableData;
  DataManager.ITEM_DATA=data.itemData;DataManager.ZONE_DATA=data.zoneData;DataManager.GLOBAL_EXCLUSION_DATA=data.globalExclusionData;
  var factory=PlayerBotQuestMaterials.class.getDeclaredMethod("catalog");factory.setAccessible(true);
  var cache=PlayerBotQuestMaterials.class.getDeclaredField("CATALOG");cache.setAccessible(true);
  var original=cache.get(null);SpawnGroup group=null;SpawnTemplate invalid=null;
  try {
   cache.set(null,null);var first=factory.invoke(null);int sites=entries(first,"sites"),gathers=entries(first,"gatherPlaces");
   if(sites==0 || gathers==0)throw new AssertionError("Complete native catalog lost field or gathering destinations");
   for(var map:data.worldMapsData)for(var candidate:data.spawnsData.getSpawnsByWorldId(map.getMapId())) {
    if(map.isInstance() || candidate.isTemporarySpawn() || candidate.getHandlerType()!=null || candidate.getEventTemplate()!=null || candidate.getSpawnTemplates().isEmpty())continue;
    if(data.npcData.getNpcTemplate(candidate.getNpcId())!=null){group=candidate;break;}
   }
   if(group==null)throw new AssertionError("No real native spawn group for malformed-input check");
   var actual=group.getSpawnTemplates().getFirst();
   invalid=new SpawnTemplate(group,actual.getX(),actual.getY(),Float.NaN,actual.getHeading(),0,null,0,0,null);
   // The native SpawnTemplate constructor already registers itself in its group.
   cache.set(null,null);
   var second=factory.invoke(null);
   if(entries(second,"sites")!=sites || entries(second,"gatherPlaces")!=gathers)throw new AssertionError("Malformed native spot aborted or changed valid material destinations");
   if(factory.invoke(null)!=second)throw new AssertionError("Invalid native spot caused repeated catalog rebuilds");
   for(String name:List.of("sites","gatherPlaces"))for(var list:((Map<?,List<?>>)field(second,name)).values())for(var value:list) {
    var place=name.equals("sites")?field(value,"place"):value;
    for(String coordinate:List.of("x","y","z"))if(!Float.isFinite((Float)field(place,coordinate)))throw new AssertionError("Nonfinite destination entered immutable catalog");
   }
   System.out.println("OK: complete native material catalog: "+sites+" monster sites / "+gathers+" gather destinations; malformed native spawn isolation preserves every valid destination and cache");
  }finally{if(group!=null && invalid!=null)group.getSpawnTemplates().remove(invalid);cache.set(null,original);}
 }
}
