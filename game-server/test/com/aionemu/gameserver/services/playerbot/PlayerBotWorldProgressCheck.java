package com.aionemu.gameserver.services.playerbot;
import java.io.File;
import java.util.*;
import javax.xml.bind.JAXBContext;
import sun.misc.Unsafe;
import com.aionemu.gameserver.dataholders.*;
import com.aionemu.gameserver.model.*;
import com.aionemu.gameserver.controllers.PlayerController;
import com.aionemu.gameserver.model.gameobjects.player.*;
import com.aionemu.gameserver.questEngine.model.*;

/** Native XP arithmetic/callback and real appearance catalog checks; isolated data, no registered actors. */
public final class PlayerBotWorldProgressCheck {
 static int checks;static void check(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
 static class Data extends PlayerCommonData {Actor actor;Data(){super(1);}@Override public Player getPlayer(){return actor;}}
 static class Controller extends PlayerController {int oldLevel,newLevel,calls;@Override public void onLevelChange(int old,int level){oldLevel=old;newLevel=level;calls++;}}
 static class Actor extends Player {
  Data data;Controller controller;QuestStateList quests;
  Actor(){super(null,null);}
  @Override public boolean isWorldBot(){return true;}
  @Override public boolean isPlayerBot(){return true;}
  @Override public boolean isOnline(){return false;}
  @Override public PlayerController getController(){return controller;}
  @Override public PlayerCommonData getCommonData(){return data;}
  @Override public QuestStateList getQuestStateList(){return quests;}
 }
 public static void main(String[]args)throws Exception{
  var u=Unsafe.class.getDeclaredField("theUnsafe");u.setAccessible(true);Unsafe unsafe=(Unsafe)u.get(null);
  var old=DataManager.PLAYER_EXPERIENCE_TABLE;
  try{
   var table=(PlayerExperienceTable)JAXBContext.newInstance(PlayerExperienceTable.class).createUnmarshaller().unmarshal(new File("game-server/data/static_data/player_experience_table.xml"));
   DataManager.PLAYER_EXPERIENCE_TABLE=table;
   for(int quest:List.of(1006,2008)){
    var a=(Actor)unsafe.allocateInstance(Actor.class);a.data=new Data();a.data.setPlayerClass(PlayerClass.WARRIOR);a.controller=new Controller();a.quests=new QuestStateList();a.data.actor=a;
    a.data.setExp(0);check(a.data.getLevel()==1 && a.controller.newLevel==1,"Native XP initializes a level-one resident");
    a.data.setExp(table.getStartExpForLevel(5));check(a.data.getLevel()==5 && a.controller.oldLevel==1 && a.controller.newLevel==5,"Earned levels reach native controller callback without online flag");
    a.data.setExp(table.getStartExpForLevel(20));check(a.data.getLevel()==9 && a.data.getExp()==table.getStartExpForLevel(10),"Offline headless identity cannot bypass native Ascension cap");
    int before=a.controller.calls;a.data.setExp(a.data.getExp());check(a.controller.calls==before,"Unchanged XP does not emit another callback");
    a.data.setPlayerClass(PlayerClass.GLADIATOR);check(!a.data.updateDaeva(),"Subclass alone cannot complete Ascension");
    a.quests.addQuest(quest,new QuestState(quest,QuestStatus.COMPLETE));check(a.data.updateDaeva(),"Native completion of either racial Ascension unlocks progression");
    a.data.setExp(table.getStartExpForLevel(20));check(a.data.getLevel()==20 && a.controller.newLevel==20,"Earned post-Ascension XP resumes native progression");
   }
  }finally{DataManager.PLAYER_EXPERIENCE_TABLE=old;}
  var cosmetics=(CosmeticItemsData)JAXBContext.newInstance(CosmeticItemsData.class).createUnmarshaller().unmarshal(new File("game-server/data/static_data/cosmetic_items/cosmetic_items.xml"));
  var base=new PlayerAppearance();base.setFace(0);base.setHair(0);base.setHeight(1.01f);base.setShoulders(123);base.setVoice(3);base.setEyeRGB(567);
  for(Race race:List.of(Race.ELYOS,Race.ASMODIANS))for(Gender gender:Gender.values()){
   var allowed=cosmetics.getTemplates().stream().filter(t->t.getRace()==race && ("ALL".equals(t.getGenderPermitted()) || gender.name().equals(t.getGenderPermitted()))).toList();
   Set<String> appearances=new HashSet<>();boolean valid=true,stable=true,preserved=true;
   for(int id=1;id<=64;id++){
    var a=PlayerBotWorldLooks.create(base,race,gender,id,cosmetics.getTemplates());var repeat=PlayerBotWorldLooks.create(base,race,gender,id,cosmetics.getTemplates());
    appearances.add(a.getFace()+":"+a.getHair()+":"+a.getHairRGB()+":"+a.getSkinRGB());
    for(var entry:Map.of("face_type",a.getFace(),"hair_type",a.getHair(),"hair_color",a.getHairRGB(),"face_color",a.getSkinRGB()).entrySet())
     valid &= allowed.stream().anyMatch(t->t.getType().equals(entry.getKey()) && t.getId()==entry.getValue() && (entry.getKey().equals("face_type") || !t.getCosmeticName().startsWith("test_")));
    stable &= a.getFace()==repeat.getFace() && a.getHair()==repeat.getHair() && a.getHairRGB()==repeat.getHairRGB() && a.getSkinRGB()==repeat.getSkinRGB();
    preserved &= a!=base && a.getHeight()==base.getHeight() && a.getShoulders()==123 && a.getVoice()==3 && a.getEyeRGB()==567;
   }
   check(valid,"All sampled model IDs/colors exist for exact race/gender, excluding artificial test colors");
   check(stable,"Creation retries choose the same appearance for an existing native identity");
   check(preserved,"Template shape/height/voice retained in independent copies");
   check(appearances.size()>48,"Population has varied face/hair/skin combinations");
  }
  check(base.getFace()==0 && base.getHair()==0 && base.getHeight()==1.01f,"Population generation never changes the owner's appearance template");
  var fallback=PlayerBotWorldLooks.create(base,Race.ELYOS,Gender.MALE,1,List.of());check(fallback!=base && fallback.getHeight()==base.getHeight() && fallback.getFace()==base.getFace(),"Missing cosmetic catalog preserves valid template rather than inventing model IDs");
  System.out.println("OK: "+checks+" native world XP/Ascension and catalog appearance checks; no world/DB/IDs");
 }
}
