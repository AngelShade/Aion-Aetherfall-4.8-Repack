package com.aionemu.gameserver.services.playerbot;

import com.aionemu.gameserver.geoEngine.models.GeoMap;

/** Sight transparency does not remove physical support. WALK-only meshes are not floors. */
final class PlayerBotGroundSupport {
 static float floor(GeoMap map,int instance,float x,float y,float top,float bottom){
  return map.getPlayerBotGroundZ(x,y,top,bottom,instance);
 }
 private PlayerBotGroundSupport(){}
}
