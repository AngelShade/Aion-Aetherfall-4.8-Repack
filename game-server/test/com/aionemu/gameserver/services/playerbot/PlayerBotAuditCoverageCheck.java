package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import java.nio.file.Path;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import com.aionemu.gameserver.questEngine.handlers.models.*;
import com.aionemu.gameserver.dataholders.*;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.gameobjects.player.QuestStateList;
import com.aionemu.gameserver.questEngine.model.*;
import java.io.StringReader;
import jakarta.xml.bind.JAXBContext;

/** Native sparse destination and declared dialog regressions; no World/DB/actors. */
public final class PlayerBotAuditCoverageCheck {
 static class Actor extends Player {
  QuestStateList quests;boolean world;
  Actor(){super(null,null);}
  @Override public QuestStateList getQuestStateList(){return quests;}
  @Override public boolean isPlayerBot(){return true;}
  @Override public boolean isWorldBot(){return world;}
  @Override public int getObjectId(){return 1999976111;}
 }
 static int checks;
 static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 static void field(Object o,String name,Object value)throws Exception {var f=o.getClass().getDeclaredField(name);f.setAccessible(true);f.set(o,value);}
 public static void main(String[] args)throws Exception {
  var first=new PlayerBotWorldTravel.Place(1,2,-256,0,1);
  var second=new PlayerBotWorldTravel.Place(1,2,256,0,1);
  var otherMap=new PlayerBotWorldTravel.Place(3,2,-256,0,1);
  var points=List.of(first,first,second,otherMap);
  var catalog=new PlayerBotWorldTravel.Catalog(points);
  check(catalog.all().size()==3,"Exact duplicate destinations are removed, other maps retained");
  check(catalog.near(1,-256,0,1,0).equals(List.of(first)),"Zero-radius query retains exact negative-cell point");
  check(new HashSet<>(catalog.near(1,0,0,1,256)).equals(Set.of(first,second)),"Boundary points from adjacent cells retained");
  check(catalog.near(3,-256,0,1,1).equals(List.of(otherMap)),"Spatial query never mixes maps");
  check(catalog.near(1,0,0,1000,10).isEmpty(),"Different floors retain vertical distance safety");
  var distances=new PlayerBotWorldTravel.Distances(1,-256,0,1,Map.of());
  check(distances.distance(first)==0 && distances.distance(second)==512,"Nearest exact ground point remains scoreable");
  check(!Double.isFinite(distances.distance(otherMap)),"Unreachable map cannot be scored as local");
  var doc=DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(Path.of(args[0],"data/static_data/spawns/Npcs/210040000_Heiron.xml").toFile());
  var groups=doc.getElementsByTagName("spawn");
  for(int npc:new int[]{212360,212363,211247}) {
   var all=new ArrayList<PlayerBotWorldTravel.Place>();
   for(int i=0;i<groups.getLength();i++) {var group=(Element)groups.item(i);if(!group.getAttribute("npc_id").equals(""+npc))continue;
    var spots=group.getElementsByTagName("spot");for(int j=0;j<spots.getLength();j++){var spot=(Element)spots.item(j);if(spot.getAttribute("aerial_spawn").equals("true"))continue;
     all.add(new PlayerBotWorldTravel.Place(210040000,npc,Float.parseFloat(spot.getAttribute("x")),Float.parseFloat(spot.getAttribute("y")),Float.parseFloat(spot.getAttribute("z"))));}
   }
   var index=new PlayerBotWorldTravel.Catalog(all);
   check(index.all().size()>32 && new HashSet<>(index.all()).equals(new HashSet<>(all)),"All native Heiron points retained for NPC "+npc);
   var last=all.getLast();check(index.near(last.map(),last.x(),last.y(),last.z(),1).contains(last),"Sparse point after old cutoff remains discoverable for "+npc);
   var brute=all.stream().filter(p->dist(last,p)<=900*900).collect(java.util.stream.Collectors.toSet());
   check(new HashSet<>(index.near(last.map(),last.x(),last.y(),last.z(),900)).equals(brute),"Spatial index agrees with full native catalog for "+npc);
  }
  var model=new ReportToData();field(model,"startDialogId",2022);
  check(PlayerBotQuestMetadata.startPages(model).contains(2022),"Declared native acceptance page is admitted");
  check(PlayerBotQuestMetadata.startPages(model).containsAll(Set.of(4,1011,4762)),"Existing native standard acceptance retained");
  check(!PlayerBotQuestMetadata.startPages(new Object()).contains(2022),"Unreviewed custom handler cannot borrow another model's action");
  check(PlayerBotQuestMetadata.coverage(null).contains("adapter required"),"Custom Java coverage gap is explicit");
  check(PlayerBotQuestMetadata.coverage(new XmlQuestData()).contains("subset"),"XML conversation support is not mislabeled full quest coverage");
  check(PlayerBotQuestMetadata.coverage(new MonsterHuntData()).equals("native hunt/accept/reward"),"Supported native hunt family is identified");
  nativeBranches();
  System.out.println("OK: "+checks+" native destination/dialog coverage checks; no World/DB/IDs");
 }
 static void nativeBranches()throws Exception {
  var f=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);var u=(sun.misc.Unsafe)f.get(null);
  var bot=(Actor)u.allocateInstance(Actor.class);bot.quests=new QuestStateList();bot.world=true;
  var oldQ=DataManager.QUEST_DATA;var oldX=DataManager.XML_QUESTS;
  try {
   DataManager.QUEST_DATA=(QuestsData)JAXBContext.newInstance(QuestsData.class).createUnmarshaller().unmarshal(new StringReader("<quests><quest id='900103' name='Native branch' category='QUEST'><rewards exp='10'/></quest></quests>"));
   String xml="<quest_scripts><xml_quest id='900103' start_npc_ids='203000'><on_talk_event ids='203000'><conditions operate='AND'><quest_status value='START' op='EQUAL'/></conditions><var value='0'><npc id='203000'>"
    +"<dialog id='60001'><operations><set_quest_var var_id='0' value='1'/></operations></dialog>"
    +"<dialog id='60002'><operations><set_quest_var var_id='0' value='2'/></operations></dialog>"
    +"<dialog id='60003'><operations><set_quest_var var_id='0' value='3'/><set_quest_status status='REWARD'/></operations></dialog>"
    +"<dialog id='60004'><operations><set_quest_var var_id='0' value='4'/><start_quest id='900104'/></operations></dialog>"
    +"</npc></var></on_talk_event></xml_quest></quest_scripts>";
   DataManager.XML_QUESTS=(XMLQuests)JAXBContext.newInstance(XMLQuests.class).createUnmarshaller().unmarshal(new StringReader(xml));
   var q=new QuestState(900103,QuestStatus.START);bot.quests.addQuest(900103,q);
   check(PlayerBotQuestConversations.steps(null,bot,900103).isEmpty(),"Ownerless private actor cannot invent a choice witness");
   bot.world=false;check(PlayerBotQuestConversations.steps(bot,bot,900103).isEmpty(),"Private actor cannot choose multiple story branches itself");bot.world=true;
   var selected=PlayerBotQuestConversations.steps(bot,bot,900103);
   check(selected.size()==1 && Set.of(60001,60002).contains(selected.getFirst().action()),"Independent actor chooses only a declared safe intermediate action");
   check(q.getQuestVarById(0)==0 && q.getStatus()==QuestStatus.START,"Planning neither changes progress nor awards completion");
   var nativeModel=DataManager.XML_QUESTS.getQuest(900103);
   @SuppressWarnings("unchecked") var talks=(List<com.aionemu.gameserver.questEngine.handlers.models.xmlQuest.events.OnTalkEvent>)(List<?>)PlayerBotQuestConversations.list(nativeModel,"onTalkEvents");
   var handler=new com.aionemu.gameserver.questEngine.handlers.template.XmlQuest(900103,List.of(203000),null,talks,List.of());
   var npc=(PlayerBotConversationCheck.Contact)u.allocateInstance(PlayerBotConversationCheck.Contact.class);npc.id=203000;
   var env=new QuestEnv(npc,bot,900103);env.setDialogActionId(selected.getFirst().action());
   check(handler.onDialogEvent(env),"Actual native XML handler authorizes the selected action");
   check(q.getStatus()==QuestStatus.START && Set.of(1,2).contains(q.getQuestVarById(0)),"Native operation advances exactly one intermediate branch");
   check(!handler.onDialogEvent(env),"Native stage condition rejects repeating the previous branch");
   check(PlayerBotQuestConversations.steps(bot,bot,900103).isEmpty(),"Planner has no stale conversation after native progress");
   q.setQuestVarById(0,0);q.setStatus(QuestStatus.REWARD);
   check(!handler.onDialogEvent(env) && q.getQuestVarById(0)==0,"Native quest-status conditions remain authoritative");
   check(PlayerBotQuestConversations.steps(bot,bot,900103).isEmpty(),"Reward state cannot enter intermediate conversation planning");
  }finally{DataManager.QUEST_DATA=oldQ;DataManager.XML_QUESTS=oldX;}
 }
 static double dist(PlayerBotWorldTravel.Place a,PlayerBotWorldTravel.Place b){return Math.pow(a.x()-b.x(),2)+Math.pow(a.y()-b.y(),2)+Math.pow(a.z()-b.z(),2);}
}
