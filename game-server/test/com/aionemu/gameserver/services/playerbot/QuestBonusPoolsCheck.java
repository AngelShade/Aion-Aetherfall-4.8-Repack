package com.aionemu.gameserver.services.playerbot;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;

import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.dataholders.ItemData;
import com.aionemu.gameserver.dataholders.ItemGroupsData;
import com.aionemu.gameserver.dataholders.QuestsData;
import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.model.templates.QuestTemplate;
import com.aionemu.gameserver.model.templates.itemgroups.BonusItemGroup;
import com.aionemu.gameserver.model.templates.quest.QuestBonuses;
import com.aionemu.gameserver.model.templates.rewards.BonusType;
import com.aionemu.gameserver.model.templates.rewards.CraftItem;
import com.aionemu.gameserver.model.templates.rewards.CraftRecipe;
import com.aionemu.gameserver.model.templates.rewards.CraftReward;

import jakarta.xml.bind.JAXBContext;

/** RECIPE, MATERIAL, FORTRESS, GOODS, ISLAND, RIFT, WINTER and BOSS quest bonuses pay from real pools, and crafting tasks keep their trade and skill band. */
public final class QuestBonusPoolsCheck {
 static int checks;
 static void check(boolean result,String text){checks++;if(!result)throw new AssertionError(text);}
 static void set(Object target,Class<?> owner,String name,Object value)throws Exception{Field f=owner.getDeclaredField(name);f.setAccessible(true);f.set(target,value);}
 static CraftRecipe recipe(int skill,int level)throws Exception{var r=new CraftRecipe();set(r,CraftReward.class,"skill",skill);set(r,CraftRecipe.class,"level",level);return r;}
 static CraftItem material(int skill,int min,int max)throws Exception{var r=new CraftItem();set(r,CraftReward.class,"skill",skill);set(r,CraftItem.class,"minLevel",min);set(r,CraftItem.class,"maxLevel",max);return r;}
 static QuestTemplate quest(BonusType type,int level,int skill,int point)throws Exception{
  var q=new QuestTemplate();var b=new QuestBonuses();set(b,QuestBonuses.class,"type",type);set(b,QuestBonuses.class,"level",level);set(q,QuestTemplate.class,"bonus",b);
  set(q,QuestTemplate.class,"combineskill",skill);set(q,QuestTemplate.class,"combineSkillpoint",point);return q;
 }
 static boolean matches(Class<?> owner,Object entry,QuestTemplate quest)throws Exception{Method m=owner.getDeclaredMethod("matchesQuest",QuestTemplate.class);m.setAccessible(true);return (boolean)m.invoke(entry,quest);}
 static long pool(List<BonusItemGroup> groups,Race race,QuestTemplate quest){return groups.stream().flatMap(g->g.getItems().stream()).filter(i->i.matches(race,quest)).count();}
 public static void main(String[] args)throws Exception {
  check(matches(CraftRecipe.class,recipe(40002,60),quest(BonusType.RECIPE,20,0,0)),"a weaponsmith design fits the RECIPE bonus");
  check(matches(CraftRecipe.class,recipe(40010,100),quest(BonusType.RECIPE,20,0,0)),"another trade's design fits too");
  check(matches(CraftItem.class,material(40002,10,99),quest(BonusType.MATERIAL,20,0,0)),"any material fits the MATERIAL bonus");
  var task=quest(BonusType.TASK,20,40002,60);
  check(matches(CraftRecipe.class,recipe(40002,60),task),"a task keeps a design of its trade and band");
  check(!matches(CraftRecipe.class,recipe(40003,60),task),"a task refuses another trade's design");
  check(!matches(CraftRecipe.class,recipe(40002,99),task),"a task refuses a design above its skill point");
  check(matches(CraftItem.class,material(40002,10,99),task),"a task keeps a material of its trade and band");
  check(!matches(CraftItem.class,material(40003,10,99),task),"a task refuses another trade's material");

  var data=Path.of(args[0],"data","static_data");
  SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI).newSchema(data.resolve("items/item_groups.xsd").toFile()).newValidator().validate(new StreamSource(data.resolve("items/item_groups.xml").toFile()));
  check(true,"item_groups.xml still fits its schema");
  DataManager.ITEM_DATA=(ItemData)JAXBContext.newInstance(ItemData.class).createUnmarshaller().unmarshal(data.resolve("items/item_templates.xml").toFile());
  var groups=(ItemGroupsData)JAXBContext.newInstance(ItemGroupsData.class).createUnmarshaller().unmarshal(data.resolve("items/item_groups.xml").toFile());
  var quests=(QuestsData)JAXBContext.newInstance(QuestsData.class).createUnmarshaller().unmarshal(data.resolve("quest_data/quest_data.xml").toFile());

  var chances=new HashMap<Integer,Float>();
  for(var entry:groups.getFortressGroups().get(0).getItems()) {
   check(DataManager.ITEM_DATA.getItemTemplate(entry.getId())!=null,"fortress reward "+entry.getId()+" is a real item");
   check(entry.getCount()>=1,"fortress reward "+entry.getId()+" pays something");
   chances.merge(((com.aionemu.gameserver.model.templates.rewards.IdLevelReward)entry).getLevel(),entry.getChance(),Float::sum);
  }
  check(chances.size()==3,"fortress pool has three level bands");
  chances.forEach((level,sum)->check(Math.abs(sum-100)<0.01,"fortress band "+level+" adds up to 100, got "+sum));

  for(var pool:List.of(groups.getGoodsGroups(),groups.getIslandGroups(),groups.getRiftGroups(),groups.getWinterGroups(),groups.getBossGroups().subList(0,1))) {
   var bands=new HashMap<Integer,Float>();
   for(var entry:pool.get(0).getItems()) {
    check(DataManager.ITEM_DATA.getItemTemplate(entry.getId())!=null,"reward "+entry.getId()+" is a real item");
    check(entry.getCount()>=1,"reward "+entry.getId()+" pays something");
    bands.merge(((com.aionemu.gameserver.model.templates.rewards.IdLevelReward)entry).getLevel(),entry.getChance(),Float::sum);
   }
   bands.forEach((level,sum)->check(Math.abs(sum-100)<0.01,"band "+level+" of "+pool.get(0).getBonusType()+" adds up to 100, got "+sum));
  }
  check(groups.getBossGroups().size()==2 && groups.getBossGroups().get(0).getChance()==98 && groups.getBossGroups().get(1).getChance()==2,"BOSS pays medals 98 to 2 over General armour");
  check(groups.getBossGroups().stream().flatMap(g->g.getItems().stream()).noneMatch(i->DataManager.ITEM_DATA.getItemTemplate(i.getId()).getLevel()==16),"BOSS no longer offers level 16 gear");

  for(int level:List.of(40,50))for(var pc:List.of(com.aionemu.gameserver.model.PlayerClass.GLADIATOR,com.aionemu.gameserver.model.PlayerClass.TEMPLAR,com.aionemu.gameserver.model.PlayerClass.ASSASSIN,com.aionemu.gameserver.model.PlayerClass.RANGER,com.aionemu.gameserver.model.PlayerClass.SORCERER,com.aionemu.gameserver.model.PlayerClass.SPIRIT_MASTER,com.aionemu.gameserver.model.PlayerClass.CLERIC,com.aionemu.gameserver.model.PlayerClass.CHANTER))
   check(groups.getBossGroups().get(1).getItems().stream().map(i->DataManager.ITEM_DATA.getItemTemplate(i.getId())).anyMatch(t->t.getLevel()==level && t.isArmor() && t.isClassSpecific(pc)),pc+" has General armour at level "+level);

  int recipe=0,material=0,fortress=0,goods=0,island=0,rift=0,winter=0,boss=0;
  for(QuestTemplate q:quests.getQuestTemplates()) {
   if(q.getBonus()==null)continue;
   switch(q.getBonus().getType()) {
    case RECIPE->{recipe++;check(q.getId()==3017,"3017 is the RECIPE quest");
     check(pool(groups.getRecipeGroups(),Race.ELYOS,q)==55,"3017 pays from 55 Elyos designs, got "+pool(groups.getRecipeGroups(),Race.ELYOS,q));}
    case MATERIAL->{material++;check(q.getId()==4009,"4009 is the MATERIAL quest");
     check(pool(groups.getMaterialGroups(),Race.ASMODIANS,q)>0,"4009 pays Asmodian materials");}
    case FORTRESS->{fortress++;Race race=q.getRacePermitted();
     check(pool(groups.getFortressGroups(),race,q)>0,"fortress quest "+q.getId()+" has rewards at bonus level "+q.getBonus().getLevel());}
    case GOODS->{goods++;check(pool(groups.getGoodsGroups(),q.getRacePermitted(),q)==5,"GOODS quest "+q.getId()+" pays five supplies");}
    case ISLAND->{island++;check(pool(groups.getIslandGroups(),q.getRacePermitted(),q)==3,"ISLAND quest "+q.getId()+" pays three medals");}
    case RIFT->{rift++;check(q.getBonus().getLevel()==q.getMinlevelPermitted()/10*10,"RIFT quest "+q.getId()+" pays its own level band, got "+q.getBonus().getLevel());
     check(pool(groups.getRiftGroups(),q.getRacePermitted(),q)==3,"RIFT quest "+q.getId()+" pays three supplies");}
    case WINTER->{winter++;check(pool(groups.getWinterGroups(),q.getRacePermitted(),q)==1,"WINTER quest "+q.getId()+" pays the swimsuit");}
    case BOSS->{boss++;Race race=q.getRacePermitted();
     check(groups.getBossGroups().get(0).getItems().stream().filter(i->i.matches(race,q)).count()==3,"BOSS quest "+q.getId()+" pays three medal rolls");
     long armour=groups.getBossGroups().get(1).getItems().stream().filter(i->i.matches(race,q)).count();
     check(armour==34,"BOSS quest "+q.getId()+" offers its level's General armour for its race, got "+armour);}
    default->{}
   }
  }
  check(recipe==1 && material==1 && fortress==46,"expected quests found: "+recipe+" "+material+" "+fortress);
  check(goods==6 && island==2 && rift==8 && winter==2 && boss==4,"expected quests found: "+goods+" "+island+" "+rift+" "+winter+" "+boss);
  System.out.println("OK: "+checks+" quest bonus pool checks");
 }
 private QuestBonusPoolsCheck(){}
}
