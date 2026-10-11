package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import java.nio.file.*;
import java.io.StringReader;
import javax.xml.bind.JAXBContext;
import com.aionemu.gameserver.configs.main.CraftConfig;
import com.aionemu.gameserver.dataholders.*;
import com.aionemu.gameserver.model.*;
import com.aionemu.gameserver.model.gameobjects.player.*;
import com.aionemu.gameserver.model.items.storage.*;
import com.aionemu.gameserver.model.skill.*;
import com.aionemu.gameserver.model.templates.QuestTemplate;
import com.aionemu.gameserver.questEngine.model.*;
import com.aionemu.gameserver.services.QuestService;
import static com.aionemu.gameserver.services.playerbot.PlayerBotQuestPrerequisites.*;

/** Native JAXB condition/state objects; no registered actor, World, database or ID factory. */
public final class PlayerBotQuestPrerequisitesCheck {
 private static int checks;
 private static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 private static final JAXBContext JAXB;
 static {try{JAXB=JAXBContext.newInstance(QuestsData.class);}catch(Exception failure){throw new ExceptionInInitializerError(failure);}}
 private static QuestTemplate template(int id,String attributes,String content)throws Exception {
  return ((QuestsData)JAXB.createUnmarshaller().unmarshal(new StringReader("<quests><quest id=\""+id+"\" minlevel_permitted=\"1\" max_repeat_count=\"1\" "+attributes+">"+content+"</quest></quests>"))).getQuestById(id);
 }
 private static String finished(int... ids){var out=new StringBuilder("<start_conditions>");for(int id:ids)out.append("<finished quest_id=\"").append(id).append("\"/>");return out.append("</start_conditions>").toString();}
 private static String reward(int id,int group){return "<start_conditions><finished quest_id=\""+id+"\" reward=\""+group+"\"/></start_conditions>";}
 private static QuestState saved(int id,QuestStatus status,int count,Integer reward){return new QuestState(id,status,0,0,count,null,reward,null);}
 private static final class Graph implements View {
  final Map<Integer,QuestTemplate> templates=new HashMap<>();final Map<Integer,QuestState> states=new HashMap<>();final Map<Integer,List<Blocker>> eligibility=new HashMap<>(),capabilities=new HashMap<>();int title;
  Graph add(int id,String content)throws Exception{templates.put(id,PlayerBotQuestPrerequisitesCheck.template(id,"",content));return this;}
  Graph add(int id,String attributes,String content)throws Exception{templates.put(id,PlayerBotQuestPrerequisitesCheck.template(id,attributes,content));return this;}
  public QuestTemplate template(int id){return templates.get(id);}public QuestState state(int id){return states.get(id);}
  public List<Blocker> eligibility(int id){return eligibility.getOrDefault(id,List.of());}public List<Blocker> capability(int id){return capabilities.getOrDefault(id,List.of());}public int title(){return title;}
  void unavailable(int id,String code){capabilities.put(id,List.of(new Blocker(id,code,false,"Native prerequisite "+id+" has no executable capability")));}
 }
 private static boolean blocked(Plan plan,String code){return !plan.reachable() && plan.blockers().stream().anyMatch(b->b.code().equals(code));}
 private static void graphs()throws Exception {
  var graph=new Graph();for(int id=1;id<=97;id++)graph.add(id,id==97?"":finished(id+1));
  var plan=plan(graph,1);check(plan.reachable() && plan.chain().size()==96,"Complete native chain exceeds the former 32-entry truncation");
  check(plan.chain().getFirst()==97 && plan.chain().getLast()==2 && plan.direct().equals(List.of(2)),"Native dependencies are deepest first and direct edges exclude ancestors");
  check(plan.starters().equals(List.of(97)),"Only genuinely fulfilled native leaf requirements are starter destinations");
  for(int id=2;id<97;id++)check(plan.steps().get(id).goal()==Goal.FINISHED,"Native chain step "+id+" requires actual completion");
  check(graph.states.isEmpty(),"Finite graph discovery never fabricates saved quest progress");
  graph=new Graph().add(1,finished(2)+finished(3,4)).add(2,finished(5)).add(3,"").add(4,"").add(5,"");graph.unavailable(5,"NATIVE_HANDLER_MISSING");
  plan=plan(graph,1);check(plan.reachable() && plan.direct().equals(List.of(3,4)) && plan.chain().equals(List.of(3,4)),"Choose reachable two-edge branch over a shorter immediate branch whose ancestor is unsupported");
  graph=new Graph().add(1,finished(2)+finished(3)).add(2,finished(4,5,6)).add(3,"").add(4,"").add(5,"").add(6,"");
  check(plan(graph,1).direct().equals(List.of(3)),"Compare complete chain cost rather than equal immediate branch length");
  graph=new Graph().add(1,reward(2,0)+finished(3)).add(2,"<rewards/><rewards/>").add(3,"");graph.states.put(2,saved(2,QuestStatus.COMPLETE,1,1));
  plan=plan(graph,1);check(plan.reachable() && plan.direct().equals(List.of(3)),"Completed wrong saved reward permanently blocks only its native alternative");
  graph.templates.put(1,template(1,"",reward(2,0)));plan=plan(graph,1);
  check(blocked(plan,"SAVED_REWARD_CHOICE") && plan.blockers().stream().anyMatch(b->b.permanent() && b.reason().contains("saved group=1")),"Nonrepeatable wrong saved reward reports exact permanent blocker");
  graph.states.put(2,saved(2,QuestStatus.COMPLETE,1,null));check(plan(graph,1).blockers().stream().anyMatch(b->b.code().equals("SAVED_REWARD_CHOICE") && b.reason().contains("saved group=null")),"Missing saved reward group cannot be repaired with fake choice mutation");
  graph.states.clear();plan=plan(graph,1);check(plan.reachable() && plan.requiredRewards().equals(Map.of(2,0)),"Future legitimate native reward-group requirement is returned for handler-driven selection");
  graph.states.put(2,saved(2,QuestStatus.REWARD,0,1));check(blocked(plan(graph,1),"COMMITTED_REWARD_CHOICE") && plan(graph,1).blockers().stream().anyMatch(Blocker::permanent),"Already committed nonrepeatable native REWARD group cannot be rewritten at turn-in");graph.states.clear();
  graph.templates.put(1,template(1,"",reward(2,2)));check(blocked(plan(graph,1),"REWARD_GROUP_UNAVAILABLE"),"Undeclared reward group is an explicit impossible native dependency");
  graph=new Graph().add(1,finished(2));graph.templates.put(2,template(2,"","<rewards/><rewards/>"));
  var repeatXml="<quests><quest id=\"2\" minlevel_permitted=\"1\" max_repeat_count=\"2\"><rewards/><rewards/></quest></quests>";
  graph.templates.put(2,((QuestsData)JAXB.createUnmarshaller().unmarshal(new StringReader(repeatXml))).getQuestById(2));graph.states.put(2,saved(2,QuestStatus.COMPLETE,1,0));
  plan=plan(graph,1);check(plan.reachable() && plan.steps().get(2).completions()==2,"Finite repeat prerequisite requires the native maximum completion count, not first completion");
  graph.states.put(2,saved(2,QuestStatus.COMPLETE,2,0));check(plan(graph,1).chain().isEmpty(),"Native finite repeat exhaustion satisfies finished condition only at exact count");
  graph.states.put(2,saved(2,QuestStatus.COMPLETE,3,0));check(blocked(plan(graph,1),"REPEAT_EXHAUSTED"),"Saved completion count beyond native maximum remains visible and unchanged");
  repeatXml=repeatXml.replace("max_repeat_count=\"2\"","max_repeat_count=\"255\"");graph.templates.put(2,((QuestsData)JAXB.createUnmarshaller().unmarshal(new StringReader(repeatXml))).getQuestById(2));
  graph.states.put(2,saved(2,QuestStatus.COMPLETE,1,0));check(plan(graph,1).chain().isEmpty(),"Native unlimited repeat does not require 255 completions");
  graph=new Graph().add(1,"<start_conditions><acquired>2</acquired></start_conditions>").add(2,finished(3)).add(3,"");plan=plan(graph,1);
  check(plan.reachable() && plan.direct().equals(List.of(2)) && plan.steps().get(2).goal()==Goal.ACQUIRED && plan.steps().get(3).goal()==Goal.FINISHED,"Acquired prerequisites preserve native acceptance goal and its actual deeper finished prerequisites");
  graph.states.put(2,saved(2,QuestStatus.COMPLETE,1,null));check(plan(graph,1).chain().isEmpty(),"Native acquired predicate includes actual COMPLETE saved status");
  graph.states.put(2,saved(2,QuestStatus.LOCKED,0,null));check(plan(graph,1).chain().size()==2,"Native LOCKED state cannot count as acquired");
  graph=new Graph().add(1,finished(2)+"<start_conditions><unfinished>3</unfinished></start_conditions>").add(2,"").add(3,"");graph.states.put(3,saved(3,QuestStatus.COMPLETE,1,null));
  check(blocked(plan(graph,1),"FORBIDDEN_COMPLETION"),"False native nonfinished condition group reports saved completion blocker");
  graph.templates.put(1,template(1,"",finished(2)+finished(4)+"<start_conditions><unfinished>3</unfinished></start_conditions>"));graph.add(4,"");
  check(plan(graph,1).reachable() && new HashSet<>(plan(graph,1).direct()).equals(Set.of(2,4)),"Mirror actual native group threshold: two finished groups can compensate a false nonfinished group");
  graph=new Graph().add(1,finished(2)+"<start_conditions><noacquired>3</noacquired></start_conditions>").add(2,"").add(3,"");graph.states.put(3,saved(3,QuestStatus.START,0,null));
  check(blocked(plan(graph,1),"FORBIDDEN_ACTIVE"),"Noacquired active guard is visible without invented abandonment");
  graph.states.put(3,saved(3,QuestStatus.COMPLETE,1,null));check(plan(graph,1).reachable(),"Native noacquired permits COMPLETE status");
  graph=new Graph().add(1,finished(2)+"<start_conditions><required_title>7</required_title></start_conditions>").add(2,"");check(blocked(plan(graph,1),"REQUIRED_TITLE"),"Displayed title gate is preserved");graph.title=7;check(plan(graph,1).reachable(),"Actually displayed native required title admits the same prerequisite plan");
  graph=new Graph().add(1,finished(2)).add(2,finished(1));check(blocked(plan(graph,1),"CYCLE_OR_CONFLICT"),"Unseeded native prerequisite cycle cannot invent completion");
  graph.add(2,finished(1)+finished(3)).add(3,"");plan=plan(graph,1);check(plan.reachable() && plan.chain().equals(List.of(3,2)),"Native OR escape opens a real finite path around a dependency cycle");
  graph=new Graph().add(1,finished(2,3)).add(2,reward(4,0)+reward(4,1)).add(3,reward(4,1)).add(4,"<rewards/><rewards/>");plan=plan(graph,1);
  check(plan.reachable() && plan.requiredRewards().equals(Map.of(4,1)) && new HashSet<>(plan.direct()).equals(Set.of(2,3)),"Retain deeper reward alternatives until shared AND requirements select a compatible branch");
  check(plan.chain().size()==3 && plan.chain().getFirst()==4,"Shared deeper prerequisite occurs once and precedes both dependents");
  graph.templates.put(2,template(2,"",reward(4,0)));check(blocked(plan(graph,1),"CYCLE_OR_CONFLICT"),"Conflicting planned native reward groups never become fabricated simultaneous choices");
  graph=new Graph().add(1,finished(2)+"<start_conditions><unfinished>2</unfinished></start_conditions>").add(2,"");check(blocked(plan(graph,1),"CYCLE_OR_CONFLICT"),"Planned completion cannot invalidate a required unfinished group");
  graph=new Graph().add(1,finished(2,3)).add(2,"<start_conditions><unfinished>3</unfinished></start_conditions>").add(3,"");plan=plan(graph,1);
  check(plan.reachable() && plan.chain().equals(List.of(2,3)) && plan.starters().equals(List.of(2)),"Ancestor negative condition applies at its own native acceptance: finishA beforeB, then complete root requiring both");
  graph=new Graph().add(1,finished(2)+"<start_conditions><acquired>3</acquired></start_conditions>").add(2,"<start_conditions><noacquired>3</noacquired></start_conditions>").add(3,"");plan=plan(graph,1);
  check(plan.reachable() && plan.chain().equals(List.of(2,3)) && plan.starters().equals(List.of(2)),"Native ancestor noacquiredB permits laterB acquisition afterA completes");
  graph.states.put(3,saved(3,QuestStatus.COMPLETE,1,null));check(plan(graph,1).reachable(),"Ancestor noacquired permits an actually COMPLETEB as native predicate specifies");
  graph=new Graph().add(1,finished(2,3)).add(2,"<start_conditions><unfinished>3</unfinished></start_conditions>").add(3,"");graph.states.put(3,saved(3,QuestStatus.COMPLETE,1,null));
  check(blocked(plan(graph,1),"FORBIDDEN_COMPLETION") && plan(graph,1).blockers().stream().anyMatch(Blocker::permanent),"Actually completed nonrepeatableB permanently blocks earlierA native unfinished condition");
  String repeatB="<quests><quest id=\"3\" minlevel_permitted=\"1\" max_repeat_count=\"255\"/></quests>";graph.templates.put(3,((QuestsData)JAXB.createUnmarshaller().unmarshal(new StringReader(repeatB))).getQuestById(3));
  check(blocked(plan(graph,1),"FORBIDDEN_COMPLETION") && plan(graph,1).blockers().stream().noneMatch(Blocker::permanent),"Repeatable COMPLETEB reports temporary actual-status blocker until a genuine native repeat is accepted; discovery never fakes a reset");
  graph=new Graph().add(1,finished(2)+finished(3)).add(2,"").add(3,"");
  for(var code:List.of("RACE","CLASS","GENDER","MIN_LEVEL","MAX_LEVEL","PROFESSION","NATIVE_HANDLER_MISSING")) {
   graph.eligibility.put(2,List.of(new Blocker(2,code,true,"Actual native actor cannot accept branch 2: "+code)));
   check(plan(graph,1).direct().equals(List.of(3)),"Full native branch reachability excludes actual actor gate "+code);
  }
  graph.eligibility.clear();graph.unavailable(2,"NATIVE_HANDLER_MISSING");graph.unavailable(3,"CURRENT_STAGE_UNSUPPORTED");plan=plan(graph,1);
  check(blocked(plan,"NATIVE_HANDLER_MISSING") && blocked(plan,"CURRENT_STAGE_UNSUPPORTED"),"All unavailable alternative branch capabilities are reported precisely");
  graph=new Graph().add(1,finished(999));check(blocked(plan(graph,1),"MISSING_TEMPLATE"),"Missing native prerequisite template is visible");
  boolean immutable=false;try{plan.chain().add(999);}catch(UnsupportedOperationException expected){immutable=true;}check(immutable,"Native prerequisite result is immutable");
  graph=new Graph();for(int id=1;id<=80;id++)graph.add(id,id==80?"":finished(id+1).repeat(24));
  long began=System.nanoTime();plan=plan(graph,1);long elapsed=(System.nanoTime()-began)/1000000;
  check(plan.reachable() && plan.chain().size()==79 && plan.starters().equals(List.of(80)),"Twenty-four equivalent OR groups across eighty levels retain the complete native graph");
  check(elapsed<10000,"Equivalent native OR alternatives are deduplicated without exponential branch expansion ("+elapsed+"ms)");
  graph=new Graph().add(1,finished(2)).add(2,finished(3)+finished(3,4)).add(3,"").add(4,"");
  check(plan(graph,1).chain().equals(List.of(3,2)),"Safe dominance removes identical-reward goal supersets without arbitrary branch caps");
 }
 static final class Bot extends Player {
  QuestStateList quests=new QuestStateList();PlayerSkillList skills=new PlayerSkillList();Race race=Race.ELYOS;Gender gender=Gender.MALE;PlayerClass clazz=PlayerClass.GLADIATOR;int level=30;
  Bot(){super(null,null);}
  @Override public QuestStateList getQuestStateList(){return quests;}@Override public PlayerSkillList getSkillList(){return skills;}
  @Override public Race getRace(){return race;}@Override public Gender getGender(){return gender;}@Override public PlayerClass getPlayerClass(){return clazz;}@Override public byte getLevel(){return (byte)level;}
 }
 private static void nativeAdmission()throws Exception {
  var unsafeClass=Class.forName("sun.misc.Unsafe");var field=unsafeClass.getDeclaredField("theUnsafe");field.setAccessible(true);var unsafe=field.get(null);var bot=(Bot)unsafeClass.getMethod("allocateInstance",Class.class).invoke(unsafe,Bot.class);
  bot.quests=new QuestStateList();bot.skills=new PlayerSkillList();bot.race=Race.ELYOS;bot.gender=Gender.MALE;bot.clazz=PlayerClass.GLADIATOR;bot.level=30;
  var oldQuest=DataManager.QUEST_DATA;var oldXml=DataManager.XML_QUESTS;
  try {
   String xml="<quests><quest id=\"1\" minlevel_permitted=\"29\" max_repeat_count=\"1\" race_permitted=\"ASMODIANS\" maxlevel_permitted=\"29\" combineskill=\"40002\" combine_skillpoint=\"399\"><class_permitted>RANGER</class_permitted><gender_permitted>FEMALE</gender_permitted></quest>"
    +"<quest id=\"2\" minlevel_permitted=\"31\" max_repeat_count=\"1\"/>"
    +"<quest id=\"3\" minlevel_permitted=\"1\" max_repeat_count=\"1\" combineskill=\"-1\" combine_skillpoint=\"100\"/>"
    +"<quest id=\"4\" minlevel_permitted=\"1\" max_repeat_count=\"1\" combineskill=\"40002\" combine_skillpoint=\"100\" category=\"TASK\"/></quests>";
   DataManager.QUEST_DATA=(QuestsData)JAXB.createUnmarshaller().unmarshal(new StringReader(xml));DataManager.XML_QUESTS=(XMLQuests)JAXBContext.newInstance(XMLQuests.class).createUnmarshaller().unmarshal(new StringReader("<quest_scripts/>"));
   var blockers=admission(bot,1);for(var code:List.of("RACE","CLASS","GENDER","MAX_LEVEL","PROFESSION"))check(blockers.stream().anyMatch(b->b.code().equals(code)),"Actual native actor admission reports "+code);
   check(admission(bot,2).stream().anyMatch(b->b.code().equals("MIN_LEVEL")),"Actual native actor minimum-level admission remains unmet");
   bot.skills=new PlayerSkillList(List.of(new PlayerSkillEntry(40002,150,0,com.aionemu.gameserver.model.gameobjects.Persistable.PersistentState.UPDATED)));
   check(admission(bot,3).isEmpty(),"Native any-profession requirement uses genuine learned skills instead of skill -1");
   check(admission(bot,4).stream().anyMatch(b->b.code().equals("PROFESSION")),"Native TASK forty-point overqualification rule is preserved");
   bot.skills=new PlayerSkillList(List.of(new PlayerSkillEntry(40002,140,0,com.aionemu.gameserver.model.gameobjects.Persistable.PersistentState.UPDATED)));
   check(admission(bot,4).isEmpty(),"Native TASK exact forty-point boundary remains admissible");
   check(bot.quests.getAllQuestState().isEmpty() && bot.skills.getSkillLevel(40002)==140,"Native admission discovery never mutates quest/skill progress");
   var nativeXml=Files.readString(Path.of("game-server/data/static_data/quest_data/quest_data.xml"));var selected=new StringBuilder("<quests>");
   for(int id:List.of(1007,1913,1914)){
    var matcher=java.util.regex.Pattern.compile("<quest\\s+id=\""+id+"\"[^>]*>.*?</quest>",java.util.regex.Pattern.DOTALL).matcher(nativeXml);if(!matcher.find())throw new AssertionError("Native reward-chain quest absent: "+id);selected.append(matcher.group());
   }selected.append("</quests>");DataManager.QUEST_DATA=(QuestsData)JAXB.createUnmarshaller().unmarshal(new StringReader(selected.toString()));
   var nativeGraph=new Graph();DataManager.QUEST_DATA.getQuestTemplates().forEach(t->nativeGraph.templates.put(t.getId(),t));bot.quests.addQuest(1007,saved(1007,QuestStatus.COMPLETE,1,0));nativeGraph.states.put(1007,bot.quests.getQuestState(1007));
   for(int id:List.of(1913,1914))nativeGraph.eligibility.put(id,admission(bot,id));
   check(plan(nativeGraph,1913).reachable() && plan(nativeGraph,1913).chain().isEmpty(),"Maintained1913 accepts actual completed1007 native reward group0");
   check(plan(nativeGraph,1913).reachable()==QuestService.checkStartConditions(bot,1913,false),"Maintained1913 planner agrees with actual native start-condition service");
   check(blocked(plan(nativeGraph,1914),"SAVED_REWARD_CHOICE") && blocked(plan(nativeGraph,1914),"CLASS"),"Maintained1914 reports both immutable1007 group0-vs1 mismatch and actual Gladiator class gate");
   check(bot.quests.getQuestState(1007).getRewardGroup()==0 && bot.quests.getQuestState(1007).getCompleteCount()==1 && bot.quests.getAllQuestState().size()==1,"Maintained reward-chain discovery preserves actual saved reward/count/state");
  }finally{DataManager.QUEST_DATA=oldQuest;DataManager.XML_QUESTS=oldXml;}
 }
 private static void catalogBenchmark()throws Exception {
  var catalog=(QuestsData)JAXB.createUnmarshaller().unmarshal(Path.of("game-server/data/static_data/quest_data/quest_data.xml").toFile());var graph=new Graph();catalog.getQuestTemplates().forEach(t->graph.templates.put(t.getId(),t));
  check(graph.templates.size()>8000,"Performance fixture reads the entire maintained native quest catalog");
  var ids=graph.templates.keySet().stream().sorted().toList();long began=System.nanoTime();int reachable=0,blocked=0;
  // All native roots, not a sampled prefix. Actor-only eligibility is covered in
  // the separate native-service fixture; this exercises the complete finite graph.
  for(int id:ids){var plan=plan(graph,id);if(plan.reachable())reachable++;else blocked++;}
  long elapsed=(System.nanoTime()-began)/1000000;check(reachable+blocked==ids.size(),"Every maintained native root resolves or reports blockers without catalog truncation");
  check(elapsed<60000,"Complete native prerequisite catalog remains bounded in offline benchmark ("+elapsed+"ms)");
  System.out.println("CATALOG: "+ids.size()+" native roots, reachable="+reachable+", blocked="+blocked+", elapsedMs="+elapsed);
 }
 public static void main(String[] args)throws Exception {int oldMaster=CraftConfig.MAX_MASTER_CRAFTING_SKILLS;try{CraftConfig.MAX_MASTER_CRAFTING_SKILLS=1;graphs();nativeAdmission();catalogBenchmark();}finally{CraftConfig.MAX_MASTER_CRAFTING_SKILLS=oldMaster;}System.out.println("OK: "+checks+" native prerequisite/branch/choice/actor checks; no World/DB/IDs");}
}
