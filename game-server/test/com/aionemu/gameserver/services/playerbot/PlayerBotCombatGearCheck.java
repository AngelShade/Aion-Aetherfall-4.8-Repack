package com.aionemu.gameserver.services.playerbot;

import java.nio.file.*;
import java.util.*;
import java.lang.reflect.*;
import javax.xml.bind.JAXBContext;
import javax.xml.stream.*;
import sun.misc.Unsafe;
import com.aionemu.gameserver.configs.main.PlayerBotConfig;
import com.aionemu.gameserver.dataholders.*;
import com.aionemu.gameserver.model.*;
import com.aionemu.gameserver.model.gameobjects.player.*;
import com.aionemu.gameserver.model.items.ItemSlot;
import com.aionemu.gameserver.model.skill.PlayerSkillList;
import com.aionemu.gameserver.model.templates.item.*;
import com.aionemu.gameserver.model.templates.item.enums.ItemGroup;
import com.aionemu.gameserver.services.playerbot.PlayerBotRules.Role;
import com.aionemu.gameserver.utils.stats.AbyssRankEnum;

/** Actual maintained native XML and production selectors; no World, DB, new items or native IDs. */
public final class PlayerBotCombatGearCheck {
 static int checks;
 static void check(boolean ok,String reason){checks++;if(!ok)throw new AssertionError(reason);}
 static void field(Object object,String name,Object value)throws Exception{for(Class<?> c=object.getClass();c!=null;c=c.getSuperclass())try{var f=c.getDeclaredField(name);f.setAccessible(true);f.set(object,value);return;}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(name);}
 static class Actor extends Player {
  PlayerClass pc;PlayerSkillList skills=new PlayerSkillList();AbyssRank rank;
  Actor(){super(null,null);}
  @Override public int getObjectId(){return 1999998200;}
  @Override public PlayerClass getPlayerClass(){return pc;}
  @Override public byte getLevel(){return 65;}
  @Override public Race getRace(){return Race.ELYOS;}
  @Override public Gender getGender(){return Gender.MALE;}
  @Override public int getWorldId(){return 210040000;}
  @Override public PlayerSkillList getSkillList(){return skills;}
  @Override public AbyssRank getAbyssRank(){return rank;}
 }
 static Map<Integer,ItemTemplate> templates(Path xml)throws Exception {
  var selected=Set.of(110101731,125003945,110000018,110000025);var result=new HashMap<Integer,ItemTemplate>();
  var factory=XMLInputFactory.newFactory();factory.setProperty(XMLInputFactory.SUPPORT_DTD,false);factory.setProperty("javax.xml.stream.isSupportingExternalEntities",false);
  var unmarshaller=JAXBContext.newInstance(ItemTemplate.class).createUnmarshaller();
  try(var stream=Files.newInputStream(xml)){
   var reader=factory.createXMLStreamReader(stream);
   while(reader.hasNext()){
    if(reader.getEventType()==XMLStreamConstants.START_ELEMENT && reader.getLocalName().equals("item_template") && selected.contains(Integer.parseInt(reader.getAttributeValue(null,"id")))){
     var item=unmarshaller.unmarshal(reader,ItemTemplate.class).getValue();result.put(item.getTemplateId(),item);if(result.size()==selected.size())break;
    }else reader.next();
   }
   reader.close();
  }
  check(result.keySet().equals(selected),"All actual skin and combat XML records loaded");return result;
 }
 public static void main(String[] args)throws Exception {
  var items=templates(Path.of(args[0],"data/static_data/items/item_templates.xml"));
  var f=Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);var unsafe=(Unsafe)f.get(null);
  var actor=(Actor)unsafe.allocateInstance(Actor.class);actor.pc=PlayerClass.SORCERER;actor.skills=new PlayerSkillList();actor.rank=(AbyssRank)unsafe.allocateInstance(AbyssRank.class);field(actor.rank,"rank",AbyssRankEnum.GRADE9_SOLDIER);
  var before=DataManager.ITEM_DATA;var skills=DataManager.SKILL_DATA;boolean appearance=PlayerBotConfig.APPEARANCE_ENABLED;
  try {
   DataManager.SKILL_DATA=new SkillData();
   for(boolean enabled:new boolean[]{false,true}){
    PlayerBotConfig.APPEARANCE_ENABLED=enabled;
    for(int id:List.of(110101731,125003945,110000025)){
     var item=items.get(id);check(PlayerBotCombatGear.skinOnly(item),"Actual appearance-only item classified: "+id);
     check(!PlayerBotGearPolicy.eligible(actor,item),"Automatic combat eligibility excludes skin independently of transmog");
     check(!PlayerBotGenerated.eligible(item,actor),"Common starter selection excludes skin");
    }
    var real=items.get(110000018);check(!PlayerBotCombatGear.skinOnly(real),"Actual stat-bearing costume-group armor remains combat gear");
    check(PlayerBotGearPolicy.eligible(actor,real),"Native class/rank/mastery checks retain actual armor");
    DataManager.ITEM_DATA=new ItemData(){@Override public Collection<ItemTemplate> getItemTemplates(){return items.values();}};
    var choice=PlayerBotTemporary.class.getDeclaredMethod("choice",Player.class,Role.class,ItemSlot.class);choice.setAccessible(true);
    check(choice.invoke(null,actor,Role.RANGED,ItemSlot.TORSO)==real,"Production tier selection chooses combat armor over Chic skin");
    check(choice.invoke(null,actor,Role.RANGED,ItemSlot.HELMET)==null,"Production selection refuses Chic veil even if it is the only head candidate");
   }
   ItemTemplate weapon=new ItemTemplate();field(weapon,"itemGroup",ItemGroup.SWORD);var w=new WeaponStats();field(w,"minDamage",10);field(weapon,"weaponStats",w);
   check(!PlayerBotCombatGear.skinOnly(weapon),"Actual weapon damage preserves valid equipment");
   check(!PlayerBotCombatGear.skinOnly(null),"Null template not a cosmetic claim");
   System.out.println("OK: "+checks+" native cosmetic combat-gear selection checks; no World/DB/item creation/ID allocation");
  }finally{DataManager.ITEM_DATA=before;DataManager.SKILL_DATA=skills;PlayerBotConfig.APPEARANCE_ENABLED=appearance;}
 }
}
