package com.aionemu.gameserver.services.playerbot;

import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.*;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import com.aionemu.gameserver.dataholders.*;
import com.aionemu.gameserver.model.templates.QuestTemplate;
import com.aionemu.gameserver.model.templates.quest.*;
import com.aionemu.gameserver.questEngine.handlers.models.*;
import com.aionemu.gameserver.questEngine.model.*;

public final class PlayerBotProfessionQualificationCheck {
 static int checks;
 static void check(boolean ok,String reason){checks++;if(!ok)throw new AssertionError(reason);}
 static void set(Object target,String name,Object value)throws Exception {for(Class<?> type=target.getClass();type!=null;type=type.getSuperclass())try{Field f=type.getDeclaredField(name);f.setAccessible(true);f.set(target,value);return;}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(name);}
 static QuestTemplate template(Element element)throws Exception {
  var t=new QuestTemplate();set(t,"id",Integer.parseInt(element.getAttribute("id")));var collection=new CollectItems();
  var nodes=element.getElementsByTagName("collect_item");for(int i=0;i<nodes.getLength();i++){var e=(Element)nodes.item(i);var item=new CollectItem();set(item,"itemId",Integer.parseInt(e.getAttribute("item_id")));set(item,"count",Integer.parseInt(e.getAttribute("count")));collection.getCollectItem().add(item);}
  var conditions=new ArrayList<XMLStartCondition>();var starts=element.getElementsByTagName("start_conditions");
  for(int i=0;i<starts.getLength();i++){var c=new XMLStartCondition();var finished=new ArrayList<FinishedQuestCond>();var prerequisites=((Element)starts.item(i)).getElementsByTagName("finished");
   for(int j=0;j<prerequisites.getLength();j++){var e=(Element)prerequisites.item(j);var f=new FinishedQuestCond();set(f,"questId",Integer.parseInt(e.getAttribute("quest_id")));finished.add(f);}set(c,"finished",finished);conditions.add(c);}
  set(t,"startConds",conditions);if(element.hasAttribute("race_permitted"))set(t,"racePermitted",com.aionemu.gameserver.model.Race.valueOf(element.getAttribute("race_permitted")));
  if(element.hasAttribute("combineskill"))set(t,"combineskill",Integer.parseInt(element.getAttribute("combineskill")));
  if(element.hasAttribute("combine_skillpoint"))set(t,"combineSkillpoint",Integer.parseInt(element.getAttribute("combine_skillpoint")));
  set(t,"collectItems",collection);return t;
 }
 static QuestState state(int quest,int stage){var s=new QuestState(quest,QuestStatus.START);s.setQuestVarById(0,stage);return s;}
 static final class Ledger implements PlayerBotProfessionQualification.Access {
  QuestState quest;Map<Integer,Long> items=new HashMap<>();long money=100,spent,allowed=100;int charged,consumed,completed,skill=399;boolean background=true,finish=true;
  public boolean background(){return background;}
  public QuestState state(int id){return quest;}
  public long held(int id){return items.getOrDefault(id,0L);}
  public long kinah(){return money;}
  public long allowance(){return allowed;}
  public boolean accept(int id){quest=new QuestState(id,QuestStatus.START);return true;}
  public boolean pay(long amount){money-=amount;if(amount>0)charged++;return true;}
  public boolean consume(int id,long amount){if(held(id)<amount)return false;items.put(id,held(id)-amount);consumed++;return true;}
  public void paid(long amount){spent+=amount;}
  public boolean complete(PlayerBotProfessionQualification.Requirement r){if(!finish)return false;quest.setQuestVar(0);quest.setStatus(QuestStatus.COMPLETE);if(r.reward()>0)skill=r.reward();completed++;return true;}
  Ledger restart(){var next=new Ledger();next.items=new HashMap<>(items);next.money=money;next.spent=spent;next.allowed=allowed;next.charged=charged;next.consumed=consumed;next.completed=completed;next.skill=skill;
   if(quest!=null)next.quest=new QuestState(quest.getQuestId(),quest.getStatus(),quest.getQuestVars().getQuestVars(),quest.getFlags(),quest.getCompleteCount(),quest.getNextRepeatTime(),quest.getRewardGroup(),quest.getLastCompleteTime());return next;}
 }
 public static void main(String[] args)throws Exception {
  var root=Path.of(args[0]);var document=DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(root.resolve("data/static_data/quest_data/quest_data.xml").toFile());
  var templates=new HashMap<Integer,QuestTemplate>();var nodes=document.getElementsByTagName("quest");
  var ids=Set.of(1972,1973,2972,2973,19000,19002,29000,29002,19008,29008,19038,29038,19057,29057);
  for(int i=0;i<nodes.getLength();i++){var e=(Element)nodes.item(i);int id=Integer.parseInt(e.getAttribute("id"));if(ids.contains(id) || id==1941 || id==1006)templates.put(id,template(e));}
  for(int id:ids) {
   var required=PlayerBotProfessionQualification.requirement(templates.get(id),null,40002,0,null);
   check(required.readable(),"native qualification collection has a verified adapter: "+id);
   check(!required.items().isEmpty(),"native item cost is required: "+id);
   var nativeItems=new TreeMap<Integer,Long>();for(var item:templates.get(id).getCollectItems().getCollectItem())nativeItems.merge(item.getItemId(),(long)item.getCount(),Math::addExact);
   check(required.items().equals(nativeItems),"initial collection exactly matches native data: "+id);
  }
  for(int quest:List.of(19038,29038))for(int stage=0;stage<12;stage++) {
   var required=PlayerBotProfessionQualification.requirement(templates.get(quest),null,40001,0,state(quest,stage));
   check(required.items().size()==4-stage/3,"cooking excludes products already turned in: "+stage);
   check(required.kinah()==6500L*(4-stage/3-(stage%3==2?1:0)),"cooking charges every remaining unpaid native design: "+stage);
  }
  for(int quest:List.of(19057,29057))for(int stage=0;stage<3;stage++) {
   var required=PlayerBotProfessionQualification.requirement(templates.get(quest),null,40010,0,state(quest,stage));
   check(required.kinah()==(stage==1?0:167500),"construction preserves the existing paid stage: "+stage);
  }
  var paidStage=state(19038,11);paidStage.setStatus(QuestStatus.REWARD);var reward=PlayerBotProfessionQualification.requirement(templates.get(19038),null,40001,0,paidStage);
  check(reward.items().isEmpty() && reward.kinah()==0,"native REWARD state never pays objectives twice");
  var unknown=new QuestTemplate();set(unknown,"id",999999);check(!PlayerBotProfessionQualification.requirement(unknown,null,40002,0,null).readable(),"unknown native requirement stays blocked");
  check(!PlayerBotProfessionQualification.requirement(templates.get(19038),null,40001,0,state(19038,12)).readable(),"unmapped cooking stage stays blocked");
  var required=new PlayerBotProfessionQualification.Requirement(1941,40002,400,Map.of(182206911,1L),25,true,"");
  var ledger=new Ledger();ledger.items.put(182206911,1L);var before=ledger.restart();
  check(PlayerBotProfessionQualification.finish(ledger,required),"real completion path charges and finishes");
  check(ledger.money==75 && ledger.spent==25 && ledger.held(182206911)==0,"native items and exact Kinah charged once");
  check(ledger.quest.getStatus()==QuestStatus.COMPLETE && ledger.quest.getCompleteCount()==1 && ledger.skill==400,"native completion and skill survive together");
  check(!PlayerBotProfessionQualification.finish(ledger,required),"same actor cannot complete again");
  var restarted=ledger.restart();check(!PlayerBotProfessionQualification.finish(restarted,required),"committed restart cannot complete again");
  check(restarted.charged==1 && restarted.consumed==1 && restarted.completed==1 && restarted.money==75,"committed restart never charges twice");
  check(PlayerBotProfessionQualification.finish(before,required),"crash before atomic checkpoint resumes original uncharged state");
  check(before.charged==1 && before.consumed==1 && before.completed==1 && before.money==75,"uncommitted restart performs only one durable payment");
  var waiting=new Ledger();waiting.items.put(182206911,1L);waiting.finish=false;check(!PlayerBotProfessionQualification.finish(waiting,required),"deferred native reward keeps paid REWARD state");
  check(waiting.quest.getStatus()==QuestStatus.REWARD && waiting.money==75,"paid objectives persist while reward waits");
  var resumed=waiting.restart();check(PlayerBotProfessionQualification.finish(resumed,required),"restart resumes native paid reward");
  check(resumed.charged==1 && resumed.consumed==1 && resumed.completed==1 && resumed.spent==25,"paid reward restart cannot charge a second time");
  for(int failure=0;failure<4;failure++) {
   var blocked=new Ledger();blocked.items.put(182206911,1L);
   if(failure==0)blocked.money=24;if(failure==1)blocked.allowed=24;if(failure==2)blocked.items.clear();if(failure==3)blocked.background=false;
   check(!PlayerBotProfessionQualification.finish(blocked,required),"missing funds/budget/items/background context remain blocked: "+failure);
   check(blocked.spent==0 && blocked.charged==0 && blocked.completed==0 && blocked.quest==null,"blocked qualification cannot charge, accept or complete: "+failure);
  }
  for(String name:List.of("_19038MasterCooksPotential.java","_29038MasterCooksPotential.java")) {
   var text=Files.readString(root.resolve("data/handlers/quest/crafting/"+name));check(text.split("tryDecreaseKinah\\("+PlayerBotProfessionQualification.COOKING_FEE+"\\)").length-1==4,"cooking fee adapter equals all four native paid stages");
  }
  for(String name:List.of("_19057MasterConstructorsPotential.java","_29057MasterConstructorsPotential.java")) {
   var text=Files.readString(root.resolve("data/handlers/quest/crafting/"+name));check(text.contains("decreaseKinah("+PlayerBotProfessionQualification.CONSTRUCTION_FEE+")"),"canonical construction fee equals its native paid alternative");
  }
  var persistence=Files.readString(root.resolve("src/com/aionemu/gameserver/services/playerbot/PlayerBotPersistence.java"));
  check(persistence.contains("InventoryDAO.storeCompanionInventory(connection, bot)") && persistence.contains("saveProgress(connection, bot, home)") && persistence.contains("PlayerBotMetadata.store(connection,metadata)") && persistence.contains("connection.commit()"),"inventory, native progress and trade ledgers share the existing atomic checkpoint");
  check(Files.readString(root.resolve("src/com/aionemu/gameserver/services/playerbot/PlayerBotProfessions.java")).contains("boolean useful=s.qualification!=null && plan.questId()==s.qualification.quest() ||"),"required qualification products remain useful after skill-cap practice stops");
  var previousQuests=DataManager.QUEST_DATA;var previousModels=DataManager.XML_QUESTS;
  try {
   var data=new QuestsData();Field field=QuestsData.class.getDeclaredField("questTemplates");field.setAccessible(true);var nativeQuests=(Map<Integer,QuestTemplate>)field.get(data);
   var models=new XMLQuests();var nativeModels=new ArrayList<XMLQuest>();var finals=new HashSet<Integer>(Set.of(19001,19003,29001,29003));
   try(var scripts=Files.list(root.resolve("data/static_data/quest_script_data"))){for(var file:scripts.filter(p->p.toString().endsWith(".xml")).toList()){
    var script=DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file.toFile());
    for(String tag:List.of("crafting_rewards","item_collecting")){var entries=script.getElementsByTagName(tag);
     for(int i=0;i<entries.getLength();i++){var e=(Element)entries.item(i);int id=Integer.parseInt(e.getAttribute("id"));XMLQuest model=tag.equals("crafting_rewards")?new CraftingRewardsData():new ItemCollectingData();set(model,"id",id);
      if(model instanceof CraftingRewardsData){set(model,"skillId",Integer.parseInt(e.getAttribute("skill_id")));set(model,"levelReward",Integer.parseInt(e.getAttribute("level_reward")));finals.add(id);}nativeModels.add(model);
     }
    }
   }}
   check(finals.size()==46 && finals.contains(1941),"all native final qualifiers are indexed from shipped script data");
   var elements=new HashMap<Integer,Element>();for(int i=0;i<nodes.getLength();i++){var e=(Element)nodes.item(i);elements.put(Integer.parseInt(e.getAttribute("id")),e);}
   var pending=new ArrayDeque<>(finals);var chain=new HashSet<Integer>();while(!pending.isEmpty()){
    int id=pending.removeFirst();if(!chain.add(id))continue;var t=template(elements.get(id));nativeQuests.put(id,t);
    for(var condition:t.getXMLStartConditions())if(condition.getFinishedPreconditions()!=null)for(var prerequisite:condition.getFinishedPreconditions())pending.add(prerequisite.getQuestId());
   }
   nativeQuests.put(1006,templates.get(1006));models.setData(nativeModels);DataManager.QUEST_DATA=data;DataManager.XML_QUESTS=models;PlayerBotProfessionQualification.prepare();
   for(int id:chain){check(PlayerBotProfessionCatalog.qualification(id),"factory reset recognizes the entire earned native chain: "+id);
    check(PlayerBotProfessionQualification.requirement(nativeQuests.get(id),models.getQuest(id),40002,finals.contains(id)?400:0,null).readable(),"every shipped qualification dependency has a readable native requirement: "+id);
   }
   check(!PlayerBotProfessionCatalog.qualification(1006),"ordinary unrelated quest stays outside qualification reset retention");
  }finally{DataManager.QUEST_DATA=previousQuests;DataManager.XML_QUESTS=previousModels;}
  System.out.println("OK: "+checks+" dormant qualification checks");
 }
 private PlayerBotProfessionQualificationCheck(){}
}
