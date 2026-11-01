package com.aionemu.gameserver.services.playerbot;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import com.aionemu.gameserver.dao.InventoryDAO;
import com.aionemu.gameserver.model.Gender;
import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.gameobjects.player.PlayerAppearance;

/** What the pull's native 3D view needs: the same model id and 66 look bytes SM_PLAYER_INFO sends, plus the visible gear. */
final class PlayerBotRecruitLook {
 private record Shown(int character,int model,String look,String gear) {}
 // The pull reads this on every state refresh, and only the newest recruit is ever shown.
 private static final Map<Integer,Shown> LATEST=new ConcurrentHashMap<>();

 static void add(Player owner,int character,String race,String gender,Map<String,Object> last)throws IOException {
  int account=owner.getAccount().getId();
  var shown=LATEST.get(account);
  if(shown==null || shown.character()!=character)LATEST.put(account,shown=load(owner,character,Race.valueOf(race),Gender.valueOf(gender)));
  last.put("model",shown.model());last.put("look",shown.look());last.put("items",shown.gear());
 }
 private static Shown load(Player owner,int character,Race race,Gender gender)throws IOException {
  var appearance=PlayerBotCompanionLooks.settled(owner,gender,character);
  String gear=InventoryDAO.loadVisibleEquipment(character).stream()
   .filter(item->item.itemId()<102100000 || item.itemId()>=102200000) // A key would switch the view to the robot.
   .limit(16).map(item->item.color()==null?Integer.toString(item.itemId()):String.format(Locale.ROOT,"%d:%06x",item.itemId(),item.color()&0xFFFFFF))
   .collect(Collectors.joining(","));
  return new Shown(character,100000+race.getRaceId()*2+gender.getGenderId(),HexFormat.of().formatHex(record(appearance)),gear);
 }
 /** SM_PLAYER_INFO's appearance block through its last reserved byte: the client stores exactly these bytes. */
 static byte[] record(PlayerAppearance a) {
  var out=ByteBuffer.allocate(66).order(ByteOrder.LITTLE_ENDIAN);
  out.putInt(a.getSkinRGB()).putInt(a.getHairRGB()).putInt(a.getEyeRGB()).putInt(a.getLipRGB());
  int[] rest={a.getFace(),a.getHair(),a.getDeco(),a.getTattoo(),a.getFaceContour(),a.getExpression(),6,
   a.getJawLine(),a.getForehead(),a.getEyeHeight(),a.getEyeSpace(),a.getEyeWidth(),a.getEyeSize(),a.getEyeShape(),a.getEyeAngle(),
   a.getBrowHeight(),a.getBrowAngle(),a.getBrowShape(),a.getNose(),a.getNoseBridge(),a.getNoseWidth(),a.getNoseTip(),
   a.getCheek(),a.getLipHeight(),a.getMouthSize(),a.getLipSize(),a.getSmile(),a.getLipShape(),a.getChinHeight(),a.getCheekBones(),a.getEarShape(),a.getHeadSize(),
   a.getNeck(),a.getNeckLength(),a.getShoulderSize(),a.getTorso(),a.getChest(),a.getWaist(),a.getHips(),a.getArmThickness(),a.getHandSize(),a.getLegThickness(),
   a.getFootSize(),a.getFacialRate(),0,a.getArmLength(),a.getLegLength(),a.getShoulders(),a.getFaceShape(),0};
  for(int value:rest)out.put((byte)value);
  return out.array();
 }
 private PlayerBotRecruitLook() {}
}
