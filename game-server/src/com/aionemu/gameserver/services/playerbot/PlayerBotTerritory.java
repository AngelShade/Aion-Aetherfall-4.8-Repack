package com.aionemu.gameserver.services.playerbot;

import java.util.Map;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.world.WorldMapType;
import com.aionemu.gameserver.world.WorldType;

/** Aion 4.8 homeland pairs, not combat permissions. Shared maps retain native camp sides. */
final class PlayerBotTerritory {
 private static final Map<Integer,Integer> PAIRS=Map.ofEntries(
  pair(WorldMapType.POETA,WorldMapType.ISHALGEN),pair(WorldMapType.ISHALGEN,WorldMapType.POETA),
  pair(WorldMapType.VERTERON,WorldMapType.ALTGARD),pair(WorldMapType.ALTGARD,WorldMapType.VERTERON),
  pair(WorldMapType.ELTNEN,WorldMapType.MORHEIM),pair(WorldMapType.MORHEIM,WorldMapType.ELTNEN),
  pair(WorldMapType.HEIRON,WorldMapType.BELUSLAN),pair(WorldMapType.BELUSLAN,WorldMapType.HEIRON),
  pair(WorldMapType.THEOBOMOS,WorldMapType.BRUSTHONIN),pair(WorldMapType.BRUSTHONIN,WorldMapType.THEOBOMOS),
  pair(WorldMapType.INGGISON,WorldMapType.GELKMAROS),pair(WorldMapType.GELKMAROS,WorldMapType.INGGISON),
  pair(WorldMapType.CYGNEA,WorldMapType.ENSHAR),pair(WorldMapType.ENSHAR,WorldMapType.CYGNEA),
  pair(WorldMapType.IDIAN_DEPTHS_LIGHT,WorldMapType.IDIAN_DEPTHS_DARK),pair(WorldMapType.IDIAN_DEPTHS_DARK,WorldMapType.IDIAN_DEPTHS_LIGHT),
  pair(WorldMapType.SANCTUM,WorldMapType.PANDAEMONIUM),pair(WorldMapType.PANDAEMONIUM,WorldMapType.SANCTUM));
 private static Map.Entry<Integer,Integer> pair(WorldMapType a,WorldMapType b){return Map.entry(a.getId(),b.getId());}
 static int counterpart(int map){return PAIRS.getOrDefault(map,0);}
 static Race homeland(int id) {
  var map=DataManager.WORLD_MAPS_DATA.getTemplate(id);if(map==null)return Race.NONE;
  if(map.getWorldType()==WorldType.ELYSEA)return Race.ELYOS;
  if(map.getWorldType()==WorldType.ASMODAE)return Race.ASMODIANS;
  if(id==WorldMapType.INGGISON.getId() || id==WorldMapType.CYGNEA.getId() || id==WorldMapType.IDIAN_DEPTHS_LIGHT.getId())return Race.ELYOS;
  if(id==WorldMapType.GELKMAROS.getId() || id==WorldMapType.ENSHAR.getId() || id==WorldMapType.IDIAN_DEPTHS_DARK.getId())return Race.ASMODIANS;
  return Race.NONE;
 }
 private PlayerBotTerritory(){}
}
