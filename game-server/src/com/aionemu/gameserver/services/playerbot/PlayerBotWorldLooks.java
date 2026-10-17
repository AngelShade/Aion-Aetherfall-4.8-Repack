package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import java.util.function.IntConsumer;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.*;
import com.aionemu.gameserver.model.gameobjects.player.PlayerAppearance;
import com.aionemu.gameserver.model.templates.cosmeticitems.CosmeticItemTemplate;

/** Creation-only variation from race/gender native model IDs and natural palettes; persisted by native character creation. */
final class PlayerBotWorldLooks {
 static PlayerAppearance neutral(){
  var p=new PlayerAppearance();p.setFace(1);p.setHair(1);p.setHeightScale(1.0f);p.setEyeRGB(0x504030);p.setLipRGB(0xB87870);
  p.setFaceContour(128);
  p.setExpression(128);
  p.setJawLine(128);
  p.setFaceShape(128);
  p.setForehead(128);
  p.setEyeHeight(128);
  p.setEyeSpace(128);
  p.setEyeWidth(128);
  p.setEyeSize(128);
  p.setEyeShape(128);
  p.setEyeAngle(128);
  p.setBrowHeight(128);
  p.setBrowAngle(128);
  p.setBrowShape(128);
  p.setNose(128);
  p.setNoseBridge(128);
  p.setNoseWidth(128);
  p.setNoseTip(128);
  p.setCheek(128);
  p.setLipHeight(128);
  p.setMouthSize(128);
  p.setLipSize(128);
  p.setSmile(128);
  p.setLipShape(128);
  p.setChinHeight(128);
  p.setCheekBones(128);
  p.setEarShape(128);
  p.setHeadSize(128);
  p.setNeck(128);
  p.setNeckLength(128);
  p.setShoulders(128);
  p.setShoulderSize(128);
  p.setTorso(128);
  p.setChest(128);
  p.setWaist(128);
  p.setHips(128);
  p.setArmThickness(128);
  p.setArmLength(128);
  p.setHandSize(128);
  p.setLegThickness(128);
  p.setLegLength(128);
  p.setFootSize(128);
  p.setFacialRate(128);
  return p;
 }
 static PlayerAppearance create(PlayerAppearance base,Race race,Gender gender,int character){
  return create(base,race,gender,character,DataManager.COSMETIC_ITEMS_DATA.getTemplates());
 }
 static PlayerAppearance create(PlayerAppearance base,Race race,Gender gender,int character,Collection<CosmeticItemTemplate> catalog){
  PlayerAppearance result=base.copy();var random=new Random(0x61494F4E4CL ^ (long)character*0x9E3779B97F4A7C15L);
  var permitted=catalog.stream().filter(t->t.getRace()==race && ("ALL".equals(t.getGenderPermitted()) || gender.name().equals(t.getGenderPermitted()))).toList();
  choose(permitted,"face_type",random,result::setFace,false);
  choose(permitted,"hair_type",random,result::setHair,true);
  choose(permitted,"hair_color",random,result::setHairRGB,true);
  choose(permitted,"face_color",random,result::setSkinRGB,true);
  // Preserve validated body sliders, height, eyes, makeup and voice from the creation template.
  // Uncatalogued indices and test-only red/blue color fixtures are never invented/selected.
  return result;
 }
 private static void choose(List<CosmeticItemTemplate> catalog,String type,Random random,IntConsumer setter,boolean excludeTests){
  var ids=catalog.stream().filter(t->type.equals(t.getType()) && (!excludeTests || !t.getCosmeticName().startsWith("test_")))
   .mapToInt(CosmeticItemTemplate::getId).filter(id->type.endsWith("_type")?id>=0 && id<=255:id>=0 && id<=0xFFFFFF).distinct().sorted().toArray();
  if(ids.length>0)setter.accept(ids[random.nextInt(ids.length)]);
 }
 private PlayerBotWorldLooks(){}
}
