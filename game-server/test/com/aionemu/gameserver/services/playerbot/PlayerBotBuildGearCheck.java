package com.aionemu.gameserver.services.playerbot;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import sun.misc.Unsafe;
import com.aionemu.gameserver.dataholders.*;
import com.aionemu.gameserver.model.*;
import com.aionemu.gameserver.model.gameobjects.*;
import com.aionemu.gameserver.model.gameobjects.player.*;
import com.aionemu.gameserver.model.items.*;
import com.aionemu.gameserver.model.items.storage.*;
import com.aionemu.gameserver.model.skill.*;
import com.aionemu.gameserver.model.stats.calc.functions.*;
import com.aionemu.gameserver.model.stats.container.StatEnum;
import com.aionemu.gameserver.model.templates.item.*;
import com.aionemu.gameserver.model.templates.item.enums.*;
import com.aionemu.gameserver.model.templates.stats.ModifiersTemplate;
import com.aionemu.gameserver.services.LootCodexService;
import com.aionemu.gameserver.services.playerbot.PlayerBotRules.Role;
import com.aionemu.gameserver.utils.stats.AbyssRankEnum;

/** Build goals through the real source cache and inventory upgrade paths, with no runtime, account or database. Run from game-server. */
public final class PlayerBotBuildGearCheck {
 private static int checks;
 private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
 private static void exact(double actual,double expected,String message){check(Double.doubleToLongBits(actual)==Double.doubleToLongBits(expected),message+": "+actual+" != "+expected);}
 private static void field(Object object,String name,Object value)throws Exception{for(Class<?> type=object.getClass();type!=null;type=type.getSuperclass())try{Field f=type.getDeclaredField(name);f.setAccessible(true);f.set(object,value);return;}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(name);}
 private static Field statik(Class<?> type,String name)throws Exception{Field f=type.getDeclaredField(name);f.setAccessible(true);return f;}
 private static class Cube extends PlayerStorage {
  List<Item> contents=new ArrayList<>();Cube(){super(null,StorageType.CUBE);}
  @Override public List<Item> getItems(){return contents;}
  @Override public long getItemCountByItemId(int id){return contents.stream().filter(i->i.getItemId()==id).mapToLong(Item::getItemCount).sum();}
  @Override public boolean isFull(){return false;}
 }
 private static class Outfit extends Equipment {
  List<Item> contents=new ArrayList<>();Outfit(){super(null);}
  @Override public List<Item> getEquippedItems(){return contents;}
 }
 private static class Bot extends Player {
  int id;PlayerClass pc;Cube cube;Outfit outfit;AbyssRank rank;PlayerSkillList skills;
  Bot(){super(null,null);}
  @Override public int getObjectId(){return id;}
  @Override public PlayerClass getPlayerClass(){return pc;}
  @Override public byte getLevel(){return 60;}
  @Override public Race getRace(){return Race.ELYOS;}
  @Override public Gender getGender(){return Gender.MALE;}
  @Override public int getWorldId(){return 110010000;}
  @Override public boolean isSpawned(){return false;}
  @Override public Storage getInventory(){return cube;}
  @Override public Equipment getEquipment(){return outfit;}
  @Override public AbyssRank getAbyssRank(){return rank;}
  @Override public PlayerSkillList getSkillList(){return skills;}
 }
 private static ItemTemplate template(int id,ItemGroup group,StatFunction... functions)throws Exception {
  var t=new ItemTemplate();field(t,"itemId",id);field(t,"name","Level 60 gear fixture "+id);field(t,"itemGroup",group);field(t,"level",60);field(t,"itemQuality",ItemQuality.LEGEND);
  field(t,"useLimits",new ItemUseLimits());field(t,"weaponStats",new WeaponStats());field(t,"maxTuneCount",0);
  var mods=new ModifiersTemplate();field(mods,"modifiers",List.of(functions));field(t,"modifiers",mods);return t;
 }
 private static StatFunction add(StatEnum stat,int value){return new StatAddFunction(stat,value,false);}
 private static void strategies() {
  int count=0;
  for(var pc:PlayerClass.values())for(var build:PlayerBotStigmaBuilds.forClass(pc)) {
   count++;check(build.gear()!=null && !build.groups().isEmpty(),pc+" "+build.key()+" has a parsed strategy and skills");
   int emphases=0;for(var stat:StatEnum.values()){double weight=build.gear().weight(stat,Double.NaN);if(!Double.isNaN(weight)){check(Double.isFinite(weight) && weight>0,"Valid native stat weight "+stat);emphases++;}}
   check(emphases>=3 && emphases<=4,"Small strategy for "+pc+" "+build.key());
  }
  check(count==44,"All 44 builds load");
  for(var pc:PlayerClass.values()) {
   var builds=PlayerBotStigmaBuilds.forClass(pc);if(builds.isEmpty()){check(PlayerBotStigmaBuilds.world(pc,1)==null,pc+" with no builds stays automatic");continue;}
   var seen=new java.util.HashSet<String>();
   for(int id=100000;id<101000;id++) {
    var picked=PlayerBotStigmaBuilds.world(pc,id);check(picked!=null && picked==PlayerBotStigmaBuilds.world(pc,id),pc+" world bot "+id+" keeps its build");
    check(!picked.key().contains("pvp") && !picked.name().contains("PvP"),pc+" world bot takes no PvP build, got "+picked.key());seen.add(picked.key());
   }
   long pve=builds.stream().filter(b->!b.key().contains("pvp") && !b.name().contains("PvP")).count();
   check(pve>=2 && seen.size()==pve,pc+" world bots spread over all "+pve+" PvE builds, got "+seen);
  }
  for(String invalid:new String[]{null,"","NOT_A_STAT:5","MAXHP:NaN","MAXHP:Infinity","MAXHP:0","MAXHP:-1","MAXHP:1,MAXHP:2","MAXHP","MAXHP:1,"}) {
   try{PlayerBotStigmaBuilds.gear("CHECK.invalid",invalid);throw new AssertionError("Accepted "+invalid);}
   catch(IllegalStateException expected){check(expected.getMessage().contains("CHECK.invalid"),"Bad strategy fails loudly with its build id");}
  }
 }
 // Frozen pre-strategy weights and arithmetic: compare bits, including otherwise unweighted native stats.
 private static double oldWeight(StatEnum stat,Role role,boolean magical) {
  return switch(stat.name()) {
   case "MAXHP"->role==Role.TANK ? 0.5 : 0.15;case "MAXMP"->role==Role.HEALER ? 0.15 : 0.05;
   case "BOOST_HATE"->role==Role.TANK ? 5 : -1;case "HEAL_BOOST"->role==Role.HEALER ? 2 : role==Role.SUPPORT ? 1 : 0;
   case "BLOCK","PARRY","PHYSICAL_DEFENSE","MAGICAL_RESIST","MAGICAL_DEFEND"->role==Role.TANK ? 1 : 0.25;
   case "PHYSICAL_ATTACK","MAX_DAMAGES","MIN_DAMAGES"->magical ? 0 : 2;
   case "BOOST_MAGICAL_SKILL","MAGICAL_ATTACK"->magical ? 1 : 0;case "PHYSICAL_CRITICAL"->magical ? 0 : 0.75;
   case "MAGICAL_CRITICAL"->magical ? 1 : 0;case "PHYSICAL_ACCURACY"->magical ? 0 : 0.3;case "MAGICAL_ACCURACY"->magical ? 0.3 : 0;
   case "ATTACK_SPEED"->4;case "BOOST_CASTING_TIME"->magical ? 4 : 0;default->0;
  };
 }
 private static boolean oldMagical(PlayerClass pc){return Set.of(PlayerClass.PRIEST,PlayerClass.CLERIC,PlayerClass.MAGE,PlayerClass.SORCERER,PlayerClass.SPIRIT_MASTER,PlayerClass.ENGINEER,PlayerClass.GUNNER,PlayerClass.RIDER,PlayerClass.ARTIST,PlayerClass.BARD).contains(pc);}
 private static double oldScore(ItemTemplate t,Role role,PlayerClass pc) {
  var w=t.getWeaponStats();boolean magical=role==Role.HEALER || oldMagical(pc);double score=t.getLevel()*0.1;
  if(t.isWeapon())score+=magical ? w.getBoostMagicalSkill()*0.7+w.getMagicalAccuracy()*0.15 : w.getMeanDamage()*3+w.getCritical()*0.3+w.getPhysicalAccuracy()*0.15;
  if(t.getModifiers()!=null)for(var m:t.getModifiers())if(!m.hasConditions())score+=m.getValue()*oldWeight(m.getName(),role,magical);
  return score;
 }
 private static double oldPolicy(ItemTemplate t,Role role,PlayerClass pc) {
  double score=oldScore(t,role,pc);String preferred=switch(pc){case WARRIOR,GLADIATOR,TEMPLAR,RIDER->"PL_";case PRIEST,CLERIC,CHANTER->"CH_";case SCOUT,ASSASSIN,RANGER,ENGINEER,GUNNER->"LT_";default->"RB_";};
  if(t.getItemGroup().name().startsWith(preferred))score*=3;if(role==Role.TANK && t.getItemGroup()==ItemGroup.SHIELD)score*=3;return score;
 }
 private static void automatic(Bot bot,PlayerBotGearPolicy.State state)throws Exception {
  for(var role:Role.values())for(boolean magical:new boolean[]{false,true})for(var stat:StatEnum.values())exact(PlayerBotEquipment.weight(stat,role,magical),oldWeight(stat,role,magical),"Frozen AUTO weight "+stat);
  var functions=new ArrayList<StatFunction>();for(var stat:StatEnum.values())functions.add(add(stat,stat.ordinal()+1));
  functions.add(add(StatEnum.MAXHP,99999).withConditions(new com.aionemu.gameserver.skillengine.condition.Conditions()));
  var templates=new ArrayList<ItemTemplate>();
  for(var group:List.of(ItemGroup.SWORD,ItemGroup.ORB,ItemGroup.SHIELD,ItemGroup.PL_TORSO,ItemGroup.CH_TORSO,ItemGroup.LT_TORSO,ItemGroup.RB_TORSO)) {
   var t=template(100010+templates.size(),group,functions.toArray(StatFunction[]::new));var w=t.getWeaponStats();
   field(w,"minDamage",117);field(w,"maxDamage",238);field(w,"critical",50);field(w,"physicalAccuracy",811);field(w,"magicalAccuracy",366);field(w,"boostMagicalSkill",603);field(w,"parry",702);templates.add(t);
  }
  PlayerClass before=bot.pc;
  for(var pc:PlayerClass.values())for(var role:Role.values())for(var t:templates) {
   bot.pc=pc;state.build="AUTO";double expected=oldScore(t,role,pc),policy=oldPolicy(t,role,pc);
   exact(PlayerBotEquipment.score(t,role,pc),expected,"Frozen class/role template");exact(PlayerBotGearPolicy.score(bot,t,role),policy,"AUTO policy unchanged");
   state.build="missing_build";exact(PlayerBotGearPolicy.score(bot,t,role),policy,"No chosen build unchanged");
   Item item=new Item(1,t,1,false,0);field(item,"enchantLevel",7);
   exact(PlayerBotEquipment.score(item,role,pc),expected+14,"Legacy enchant unchanged");exact(PlayerBotGearPolicy.score(bot,item,role),policy+14,"Policy enchant unchanged");
  }
  bot.pc=before;state.build="AUTO";
 }
 private static void sourceIndex(List<ItemTemplate> items)throws Exception {
  // Reachable container sources exercise acquisitions() without starting the full static-data loader.
  Class<?> source=Class.forName(LootCodexService.class.getName()+"$Source"),index=Class.forName(LootCodexService.class.getName()+"$Index");
  var constructor=source.getDeclaredConstructors()[0];constructor.setAccessible(true);
  var sources=new HashMap<Integer,List<Object>>();int box=100099;
  for(var t:items)if(t.getTemplateId()!=box)sources.put(t.getTemplateId(),List.of(constructor.newInstance("item",box,"Earned equipment chest","Equipment reward",100f,1L,1L)));
  sources.put(box,List.of(constructor.newInstance("npc",210001,"Dungeon boss","Drop",100f,1L,1L)));
  var data=index.getDeclaredConstructors()[0];data.setAccessible(true);
  statik(LootCodexService.class,"index").set(null,data.newInstance(DataManager.ITEM_DATA,DataManager.CUSTOM_NPC_DROP,DataManager.GLOBAL_DROP_DATA,DataManager.SPAWNS_DATA,items,List.of(),sources,Map.of(),Map.of(),Map.of(),Map.of(),Map.of(),Map.of()));
 }
 private static void goals(Bot bot,PlayerBotGearPolicy.State state)throws Exception {
  bot.pc=PlayerClass.TEMPLAR;state.settings=PlayerBotGearPolicy.DEFAULT;
  // Equal-level plate: one defensive roll, one offensive roll, at normal native stat magnitudes.
  var guard=template(100001,ItemGroup.PL_TORSO,add(StatEnum.MAXHP,400),add(StatEnum.BLOCK,100),add(StatEnum.PARRY,90));
  var damage=template(100002,ItemGroup.PL_TORSO,add(StatEnum.MAXHP,200),add(StatEnum.PHYSICAL_ATTACK,30),add(StatEnum.PHYSICAL_CRITICAL,50),add(StatEnum.PHYSICAL_ACCURACY,80));
  var box=template(100099,ItemGroup.NONE);var items=List.of(guard,damage,box);
  DataManager.ITEM_DATA=new ItemData(){@Override public Collection<ItemTemplate> getItemTemplates(){return items;}@Override public ItemTemplate getItemTemplate(int id){return items.stream().filter(t->t.getTemplateId()==id).findFirst().orElse(null);}};
  sourceIndex(items);check(LootCodexService.warm(),"Isolated native source index ready");
  state.build="tank";var tank=PlayerBotLoadouts.targets(bot,Role.TANK);
  check(tank.get(ItemSlot.TORSO).item()==guard,"Guardian chooses defensive plate");
  check(!tank.get(ItemSlot.TORSO).sources().isEmpty(),"Goal has a source through the real acquisition path");
  state.build="dps";var dps=PlayerBotLoadouts.targets(bot,Role.TANK);
  check(dps.get(ItemSlot.TORSO).item()==damage,"Crusader at the same class, stage and role chooses offensive plate");
  check(dps!=tank,"Builds do not share their cached map");
  state.build="tank";check(PlayerBotLoadouts.targets(bot,Role.TANK)==tank,"Switching back reuses only that build's plan");
  state.build="dps";check(PlayerBotLoadouts.targets(bot,Role.TANK)==dps,"Second build retains its own cache entry");
  for(String build:List.of("tank","dps")) {
   state.build=build;var plan=PlayerBotLoadouts.targets(bot,Role.TANK);var goal=plan.get(ItemSlot.TORSO).item();var rejected=goal==guard ? damage : guard;
   Item worn=new Item(11,rejected,1,false,0),wanted=new Item(12,goal,1,false,0);worn.setEquipped(true);worn.setEquipmentSlot(ItemSlot.TORSO.getSlotIdMask());bot.outfit.contents.add(worn);
   check(PlayerBotLoadouts.plan(state.session,state,plan).stream().anyMatch(r->r.slot()==ItemSlot.TORSO && r.goal()!=null && r.goal().item()==goal),"Goal gap agrees with wear gain for "+build);
   bot.cube.contents.add(wanted);
   for(var choices:List.of(PlayerBotGearPolicy.upgrades(bot,Role.TANK),PlayerBotGear.upgrades(bot,Role.TANK),PlayerBotEquipment.upgrades(bot,Role.TANK)))check(choices.size()==1 && choices.getFirst().item()==wanted,"Every wear entry point accepts the goal for "+build);
   check(PlayerBotBuildRules.sortStarter(List.of(guard,damage),bot).getFirst()==goal,"Starter sorting agrees for "+build);
   bot.cube.contents.clear();bot.outfit.contents.clear();wanted.setEquipped(true);wanted.setEquipmentSlot(ItemSlot.TORSO.getSlotIdMask());bot.outfit.contents.add(wanted);worn.setEquipped(false);bot.cube.contents.add(worn);
   check(PlayerBotGearPolicy.upgrades(bot,Role.TANK).isEmpty() && PlayerBotGear.upgrades(bot,Role.TANK).isEmpty(),"Goal is never swapped back to rejected gear for "+build);
   bot.cube.contents.clear();bot.outfit.contents.clear();
  }
  state.build="tank";var almost=template(100006,ItemGroup.PL_TORSO,add(StatEnum.MAXHP,399),add(StatEnum.BLOCK,100),add(StatEnum.PARRY,90));
  Item near=new Item(20,almost,1,false,0);near.setEquipped(true);near.setEquipmentSlot(ItemSlot.TORSO.getSlotIdMask());bot.outfit.contents.add(near);
  // A distinct template just three score points behind the goal must not create an unwearable gap.
  var copy=template(100003,ItemGroup.PL_TORSO,add(StatEnum.MAXHP,400),add(StatEnum.BLOCK,100),add(StatEnum.PARRY,90));
  check(PlayerBotLoadouts.plan(state.session,state,Map.of(ItemSlot.TORSO,new PlayerBotLoadouts.Target(copy,List.of()))).stream().noneMatch(r->r.goal()!=null),"Goals honor the same upgrade ratio as wearing");
  bot.outfit.contents.clear();
  var threat=template(100007,ItemGroup.PL_TORSO,add(StatEnum.BOOST_HATE,10));var defense=template(100008,ItemGroup.PL_TORSO,add(StatEnum.PHYSICAL_DEFENSE,25));
  state.build="dps";field(state.session,"role",Role.MELEE);
  check(PlayerBotGearPolicy.score(bot,defense,Role.MELEE)>PlayerBotGearPolicy.score(bot,threat,Role.MELEE) && PlayerBotBuildRules.sortStarter(List.of(threat,defense),bot).getFirst()==defense,"Chosen starter sorting uses the bot's combat role rather than the class default");
  state.build="AUTO";check(PlayerBotBuildRules.sortStarter(List.of(threat,defense),bot).getFirst()==threat,"AUTO starter sorting keeps its original class role");
  state.build="tank";field(state.session,"role",Role.TANK);
  var conditional=add(StatEnum.BLOCK,9999).withConditions(new com.aionemu.gameserver.skillengine.condition.Conditions());
  var base=template(100004,ItemGroup.PL_TORSO,add(StatEnum.BLOCK,20));var withCondition=template(100005,ItemGroup.PL_TORSO,add(StatEnum.BLOCK,20),conditional);
  exact(PlayerBotGearPolicy.score(bot,withCondition,Role.TANK),PlayerBotGearPolicy.score(bot,base,Role.TANK),"Conditional template modifiers remain excluded");
  Item enhanced=new Item(30,base,1,false,0);field(enhanced,"enchantLevel",5);field(enhanced,"fusionedItemTemplate",withCondition);
  exact(PlayerBotGearPolicy.score(bot,enhanced,Role.TANK),PlayerBotGearPolicy.score(bot,base,Role.TANK)+10+20*5,"Fusion bonuses use strategy, skip conditions and retain enchant handling");
 }
 @SuppressWarnings("unchecked") public static void main(String[] args)throws Exception {
  com.aionemu.commons.configuration.ConfigurableProcessor.process(new Properties(),com.aionemu.gameserver.configs.main.PlayerBotConfig.class,com.aionemu.gameserver.configs.main.PlayerBotMerchantConfig.class);
  Field f=Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);Unsafe unsafe=(Unsafe)f.get(null);
  Bot bot=(Bot)unsafe.allocateInstance(Bot.class);bot.id=1999999031;bot.pc=PlayerClass.TEMPLAR;bot.cube=new Cube();bot.outfit=new Outfit();bot.skills=new PlayerSkillList();
  bot.rank=(AbyssRank)unsafe.allocateInstance(AbyssRank.class);field(bot.rank,"rank",AbyssRankEnum.GRADE9_SOLDIER);bot.setGameStats((com.aionemu.gameserver.model.stats.container.PlayerGameStats)unsafe.allocateInstance(com.aionemu.gameserver.model.stats.container.PlayerGameStats.class));
  var session=(PlayerBotSession)unsafe.allocateInstance(PlayerBotSession.class);field(session,"owner",bot);field(session,"bot",bot);field(session,"role",Role.TANK);
  var state=(PlayerBotGearPolicy.State)unsafe.allocateInstance(PlayerBotGearPolicy.State.class);field(state,"session",session);field(state,"settings",PlayerBotGearPolicy.DEFAULT);field(state,"build","AUTO");field(state,"references",new ConcurrentHashMap<>());field(state,"goals",new ConcurrentHashMap<>());
  var states=(Map<Integer,PlayerBotGearPolicy.State>)statik(PlayerBotGearPolicy.class,"STATES").get(null);states.put(bot.id,state);
  var skills=DataManager.SKILL_DATA;var items=DataManager.ITEM_DATA;var index=statik(LootCodexService.class,"index").get(null);
  try {
   DataManager.SKILL_DATA=new SkillData();strategies();automatic(bot,state);goals(bot,state);
   field(bot,"worldBot",true);check(PlayerBotGearPolicy.stigmaBuild(bot)==PlayerBotStigmaBuilds.world(PlayerClass.TEMPLAR,bot.id) && PlayerBotGearPolicy.stigmaBuild(bot)!=null,"A world bot uses its own build");
   state.build="tank";check(PlayerBotGearPolicy.stigmaBuild(bot)==PlayerBotStigmaBuilds.world(PlayerClass.TEMPLAR,bot.id),"A world bot ignores a saved companion build");state.build="AUTO";field(bot,"worldBot",false);check(PlayerBotGearPolicy.stigmaBuild(bot)==null,"A Temporary Bot on AUTO stays automatic");
   states.remove(bot.id);exact(PlayerBotGearPolicy.score(bot,template(100030,ItemGroup.SWORD),Role.TANK),6,"Bot without policy retains AUTO score");
   System.out.println("OK: "+checks+" build gear strategy/cache/AUTO/wear checks; no runtime, account or database used");
  }finally{states.remove(bot.id);DataManager.SKILL_DATA=skills;DataManager.ITEM_DATA=items;statik(LootCodexService.class,"index").set(null,index);}
 }
 private PlayerBotBuildGearCheck(){}
}
