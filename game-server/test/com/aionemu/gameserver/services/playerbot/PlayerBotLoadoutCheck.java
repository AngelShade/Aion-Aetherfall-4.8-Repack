package com.aionemu.gameserver.services.playerbot;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import sun.misc.Unsafe;
import com.aionemu.gameserver.configs.Config;
import com.aionemu.gameserver.dataholders.*;
import com.aionemu.gameserver.model.*;
import com.aionemu.gameserver.model.gameobjects.*;
import com.aionemu.gameserver.model.gameobjects.player.*;
import com.aionemu.gameserver.model.items.*;
import com.aionemu.gameserver.model.items.storage.*;
import com.aionemu.gameserver.model.skill.*;
import com.aionemu.gameserver.model.templates.item.*;
import com.aionemu.gameserver.model.templates.item.enums.*;
import com.aionemu.gameserver.model.templates.quest.QuestItems;
import com.aionemu.gameserver.services.LootCodexService;
import com.aionemu.gameserver.services.playerbot.PlayerBotGearPolicy.Reference;
import com.aionemu.gameserver.services.playerbot.PlayerBotRules.Role;
import com.aionemu.gameserver.utils.stats.AbyssRankEnum;

/** Preset equipment rules on fixtures, no live accounts or database. Run from game-server with "static" to add the real item data. */
public final class PlayerBotLoadoutCheck {
 private static int checks;
 private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
 private static void field(Object object,String name,Object value)throws Exception{for(Class<?> type=object.getClass();type!=null;type=type.getSuperclass())try{Field f=type.getDeclaredField(name);f.setAccessible(true);f.set(object,value);return;}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(name);}
 private static class Cube extends PlayerStorage {
  List<Item> contents=new ArrayList<>();Cube(){super(null,StorageType.CUBE);}
  @Override public List<Item> getItems(){return contents;}
  @Override public Item getItemByObjId(int id){return contents.stream().filter(i->i.getObjectId()==id).findFirst().orElse(null);}
  @Override public long getItemCountByItemId(int id){return contents.stream().filter(i->i.getItemId()==id).mapToLong(Item::getItemCount).sum();}
  @Override public boolean isFull(){return false;}
 }
 private static class Outfit extends Equipment {
  List<Item> contents=new ArrayList<>();Outfit(){super(null);}
  @Override public List<Item> getEquippedItems(){return contents;}
  @Override public Item getEquippedItemByObjId(int id){return contents.stream().filter(i->i.getObjectId()==id).findFirst().orElse(null);}
 }
 /** All armor masteries, so the class's own armor type is the one chosen. */
 private static PlayerSkillList learned() {
  var entries=new ArrayList<PlayerSkillEntry>();
  for(String armor:List.of("PL_","CH_","LT_","RB_"))for(int id:DataManager.SKILL_DATA.getMasterySkills(ItemGroup.valueOf(armor+"TORSO")))entries.add(new PlayerSkillEntry(id,1,0,Persistable.PersistentState.NOACTION));
  return new PlayerSkillList(entries);
 }
 private static class Companion extends Player {
  int id;PlayerClass pc;byte level;Race race;Cube cube;Outfit outfit;AbyssRank rank;PlayerSkillList skills;
  Companion(){super(null,null);}
  @Override public int getObjectId(){return id;}
  @Override public PlayerClass getPlayerClass(){return pc;}
  @Override public byte getLevel(){return level;}
  @Override public Race getRace(){return race;}
  @Override public Gender getGender(){return Gender.MALE;}
  @Override public int getWorldId(){return 110010000;}
  @Override public boolean isSpawned(){return false;}
  @Override public Storage getInventory(){return cube;}
  @Override public Equipment getEquipment(){return outfit;}
  @Override public AbyssRank getAbyssRank(){return rank;}
  @Override public PlayerSkillList getSkillList(){return skills;}
 }
 private static ItemTemplate template(int id,ItemGroup group,ItemQuality quality)throws Exception {
  ItemTemplate t=new ItemTemplate();field(t,"itemId",id);field(t,"itemGroup",group);field(t,"level",60);field(t,"itemQuality",quality);
  field(t,"useLimits",new ItemUseLimits());field(t,"weaponStats",new WeaponStats());field(t,"maxTuneCount",0);return t;
 }
 private static Item weapon(int object,int id,int damage,ItemQuality quality)throws Exception {
  ItemTemplate t=template(id,ItemGroup.SWORD,quality);WeaponStats w=t.getWeaponStats();field(w,"minDamage",damage);field(w,"maxDamage",damage);
  return new Item(object,t,1,false,0);
 }
 private static Companion companion(Unsafe unsafe,PlayerClass pc,int level,Race race)throws Exception {
  Companion bot=(Companion)unsafe.allocateInstance(Companion.class);bot.id=1999999002;bot.pc=pc;bot.level=(byte)level;bot.race=race;bot.cube=new Cube();bot.outfit=new Outfit();bot.skills=new PlayerSkillList();
  bot.rank=(AbyssRank)unsafe.allocateInstance(AbyssRank.class);field(bot.rank,"rank",AbyssRankEnum.GRADE9_SOLDIER);
  bot.setGameStats((com.aionemu.gameserver.model.stats.container.PlayerGameStats)unsafe.allocateInstance(com.aionemu.gameserver.model.stats.container.PlayerGameStats.class));
  return bot;
 }
 private static void rejected(Map<String,String> base,String key,String value)throws Exception {
  var map=new HashMap<>(base);map.put(key,value);
  try{PlayerBotMetadata.validate(7,12001,"loadouts",map);throw new AssertionError("Accepted "+key+"="+value);}catch(java.io.IOException expected){check(true,"Rejected");}
 }
 private static void stages() {
  for(int level=1;level<=65;level++) {
   int expected=level<10 ? 1 : level<40 ? level/10*10 : level<60 ? level/5*5 : level<65 ? 60 : 65;
   int stage=PlayerBotStigmaBuilds.stage(level),end=PlayerBotStigmaBuilds.stageEnd(stage);
   check(stage==expected && stage<=level && level<=end,"Stage boundary at "+level);
  }
  check(PlayerBotStigmaBuilds.STAGES.size()==10 && PlayerBotStigmaBuilds.stageEnd(65)==65 && PlayerBotStigmaBuilds.stageEnd(1)==9,"Ten stages end at the level cap");
 }
 private static void metadata()throws Exception {
  var good=Map.of("account","7","character","12001","ref.tank.45.MAIN_HAND","501:100001","goal.TORSO","100002:recipe:9","goalsFor","tank.45");
  PlayerBotMetadata.validate(7,12001,"loadouts",good);check(true,"A saved document loads");
  rejected(good,"ref.tank.46.MAIN_HAND","501:100001");rejected(good,"ref.tank.45.STIGMA1","501:100001");rejected(good,"ref.TANK.45.TORSO","501:100001");
  rejected(good,"ref.tank.45.TORSO","0:100001");rejected(good,"ref.tank.45.TORSO","501");rejected(good,"goal.TORSO","100002:gift:9");rejected(good,"goalsFor","tank.46");rejected(good,"copy.tank","1");
 }
 private static void rules(Unsafe unsafe)throws Exception {
  Companion bot=companion(unsafe,PlayerClass.TEMPLAR,65,Race.ELYOS);
  var skills=DataManager.SKILL_DATA;var states=PlayerBotGearPolicy.class.getDeclaredField("STATES");states.setAccessible(true);@SuppressWarnings("unchecked")var map=(Map<Integer,PlayerBotGearPolicy.State>)states.get(null);
  try {
   DataManager.SKILL_DATA=new SkillData();
   var session=(PlayerBotSession)unsafe.allocateInstance(PlayerBotSession.class);field(session,"owner",bot);field(session,"bot",bot);field(session,"role",Role.TANK);
   var state=(PlayerBotGearPolicy.State)unsafe.allocateInstance(PlayerBotGearPolicy.State.class);field(state,"session",session);field(state,"settings",PlayerBotGearPolicy.DEFAULT);field(state,"build","tank");
   field(state,"references",new ConcurrentHashMap<String,Reference>());field(state,"goals",new ConcurrentHashMap<String,String>());field(state,"generated",new HashSet<Integer>());field(state,"starterSlots",new HashSet<Long>());
   map.put(bot.id,state);
   String main="tank.65.MAIN_HAND";long hand=ItemSlot.MAIN_HAND.getSlotIdMask();
   Item weak=weapon(1,100001,20,ItemQuality.COMMON),strong=weapon(2,100002,50,ItemQuality.MYTHIC);bot.cube.contents.add(strong);
   check(PlayerBotGearPolicy.upgrades(bot,Role.TANK).getFirst().item()==strong,"With nothing saved the strongest owned item is the fallback");

   weak.setEquipped(true);weak.setEquipmentSlot(hand);bot.outfit.contents.add(weak);
   state.references.put(main,new Reference(1,100001));state.pinnedUntil=0;
   check(PlayerBotGearPolicy.saved(bot).get(hand)==weak && PlayerBotGearPolicy.pinned(bot)==hand,"A worn saved item holds its slot");
   check(PlayerBotGearPolicy.upgrades(bot,Role.TANK).isEmpty(),"A stronger bag item does not displace a saved slot");
   check(PlayerBotGearPolicy.gain(bot,strong.getItemTemplate(),1000,hand,Role.TANK)<0 && !PlayerBotGearPolicy.roll(bot,strong.getItemTemplate()),"Generation, shops and rolls see the slot as taken");
   check(bot.cube.contents.contains(strong),"The stronger item stays owned");

   bot.outfit.contents.clear();weak.setEquipped(false);weak.setEquipmentSlot(0);bot.cube.contents.add(weak);strong.setEquipped(true);strong.setEquipmentSlot(hand);bot.cube.contents.remove(strong);bot.outfit.contents.add(strong);state.pinnedUntil=0;
   var first=PlayerBotGearPolicy.upgrades(bot,Role.TANK).getFirst();check(first.item()==weak && first.slot()==hand,"Switching presets equips the actual saved item, even over a stronger worn one");
   check(bot.cube.contents.size()==1 && bot.outfit.contents.size()==1,"Planning a switch creates and removes nothing");

   for(var quality:ItemQuality.values())if(quality!=ItemQuality.JUNK) {
    Item any=weapon(10+quality.ordinal(),100100+quality.ordinal(),5,quality);bot.cube.contents.add(any);state.references.put(main,new Reference(any.getObjectId(),any.getItemId()));state.pinnedUntil=0;
    check(PlayerBotGearPolicy.saved(bot).get(hand)==any,"Rarity never refuses a saved item: "+quality);bot.cube.contents.remove(any);
   }

   state.references.put(main,new Reference(99,100001));state.pinnedUntil=0;
   check(PlayerBotGearPolicy.saved(bot).isEmpty() && PlayerBotGearPolicy.pinned(bot)==0,"A missing item leaves its slot to the normal fallback");
   ItemTemplate wrong=template(100003,ItemGroup.SWORD,ItemQuality.UNIQUE);field(wrong,"race",Race.ASMODIANS);Item illegal=new Item(3,wrong,1,false,0);bot.cube.contents.add(illegal);state.references.put(main,new Reference(3,100003));state.pinnedUntil=0;
   check(PlayerBotGearPolicy.saved(bot).isEmpty(),"An item the bot cannot legally wear is never pinned");bot.cube.contents.remove(illegal);

   state.references.clear();state.references.put("tank.45.MAIN_HAND",new Reference(1,100001));state.pinnedUntil=0;
   check(PlayerBotGearPolicy.saved(bot).isEmpty(),"Another level stage does not apply here");
   bot.level=47;state.pinnedUntil=0;check(PlayerBotGearPolicy.saved(bot).containsKey(hand),"The item returns at its own stage");bot.level=65;
   state.references.clear();state.references.put("dps.65.MAIN_HAND",new Reference(1,100001));state.pinnedUntil=0;
   check(PlayerBotGearPolicy.saved(bot).isEmpty(),"Another preset does not apply here");

   state.references.put("tank.65.MAIN_HAND",new Reference(1,100001));state.references.put("pack.60.MAIN_HAND",new Reference(1,100001));
   check(PlayerBotGearPolicy.referenced(bot,weak) && !PlayerBotGearPolicy.referenced(bot,strong),"One item serves several presets with no copy and is protected");
   check(!PlayerBotCare.disposable(weak,PlayerBotGearPolicy.referenced(bot,weak),false,false,false),"Extraction leaves referenced items alone");

   Item great=new Item(4,template(100004,ItemGroup.GREATSWORD,ItemQuality.EPIC),1,false,0);bot.cube.contents.add(great);
   state.references.clear();state.references.put("tank.65.MAIN_HAND",new Reference(4,100004));state.references.put("tank.65.SUB_HAND",new Reference(1,100001));state.pinnedUntil=0;
   check(great.getItemTemplate().isTwoHandWeapon() && PlayerBotGearPolicy.saved(bot).size()==1 && PlayerBotGearPolicy.saved(bot).get(ItemSlot.MAIN_OR_SUB.getSlotIdMask())==great,"A saved two-handed weapon covers both hands");
   check((PlayerBotGearPolicy.pinned(bot) & ItemSlot.SUB_HAND.getSlotIdMask())!=0,"Nothing is planned for the covered off hand");

   state.references.clear();state.pinnedUntil=0;bot.outfit.contents.clear();bot.cube.contents.clear();
   Item owned=weapon(5,100005,500,ItemQuality.MYTHIC);owned.setEquipped(true);owned.setEquipmentSlot(hand);bot.outfit.contents.add(owned);
   ItemTemplate wanted=weapon(6,100006,20,ItemQuality.EPIC).getItemTemplate();
   check(!PlayerBotGearPolicy.roll(bot,wanted),"Without a goal a weak item is not rolled for");
   state.goals.put("MAIN_HAND","100006:recipe:9");check(PlayerBotGearPolicy.goal(bot,wanted) && PlayerBotGearPolicy.roll(bot,wanted),"A recorded goal is rolled for");
   ItemTemplate other=weapon(7,100007,30,ItemQuality.EPIC).getItemTemplate();var items=DataManager.ITEM_DATA;
   DataManager.ITEM_DATA=new ItemData(){@Override public ItemTemplate getItemTemplate(int id){return id==100006 ? wanted : other;}};
   try{check(PlayerBotGearPolicy.rewardChoice(bot,Role.TANK,List.of(new QuestItems(100007,1),new QuestItems(100006,1)))==1,"A recorded goal is chosen as a quest reward");}finally{DataManager.ITEM_DATA=items;}
  }finally{DataManager.SKILL_DATA=skills;map.remove(bot.id);}
 }
 private static void data(Unsafe unsafe)throws Exception {
  DataManager.getInstance();
  long start=System.currentTimeMillis();while(!LootCodexService.warm() && System.currentTimeMillis()-start<300000)Thread.sleep(200);
  check(LootCodexService.warm(),"The source index builds");
  var kinds=new TreeMap<String,Integer>();int targets=0,empty=0;var missing=new TreeMap<String,Integer>();
  var classes=List.of(PlayerClass.GLADIATOR,PlayerClass.TEMPLAR,PlayerClass.ASSASSIN,PlayerClass.RANGER,PlayerClass.SORCERER,PlayerClass.SPIRIT_MASTER,PlayerClass.CLERIC,PlayerClass.CHANTER,PlayerClass.RIDER,PlayerClass.GUNNER,PlayerClass.BARD);
  for(var pc:classes)for(var race:List.of(Race.ELYOS,Race.ASMODIANS))for(int stage:PlayerBotStigmaBuilds.STAGES)for(var role:new HashSet<>(List.of(PlayerBotRules.roleFor(pc),pc==PlayerClass.GLADIATOR || pc==PlayerClass.TEMPLAR ? Role.TANK : PlayerBotRules.roleFor(pc)))) {
   Companion bot=companion(unsafe,pc,stage,race);bot.skills=learned();
   var plan=PlayerBotLoadouts.targets(bot,role);int top=PlayerBotStigmaBuilds.stageEnd(stage);
   for(var slot:PlayerBotTemporary.GEAR)if(!plan.containsKey(slot) && slot!=ItemSlot.SUB_HAND){empty++;missing.merge("stage "+stage,1,Integer::sum);}
   for(var e:plan.entrySet()) {
    var t=e.getValue().item();targets++;String label=pc+" "+race+" "+stage+" "+e.getKey()+" "+t.getTemplateId();
    check(!t.isTestItem() && !t.getName().toLowerCase(Locale.ROOT).contains("balance test") && t.isClassSpecific(pc) && (t.getRace()==Race.PC_ALL || t.getRace()==race),"Target is legal: "+label);
    check(t.getRequiredLevel(pc)>=0 && t.getRequiredLevel(pc)<=top && t.getLevel()<=top,"Target fits its stage: "+label);
    for(var source:e.getValue().sources())if(source.kind().equals("quest"))check(DataManager.QUEST_DATA.getQuestById(source.id()).getCategory()!=com.aionemu.gameserver.model.templates.quest.QuestCategory.EVENT,"Event quests are not planned: "+label);
    check(!e.getValue().sources().isEmpty(),"Target has a verified source: "+label);
    for(var source:e.getValue().sources())check(PlayerBotLoadouts.KINDS.contains(source.kind()) && !source.name().contains("Test Item") && !source.name().contains("_"),"Goal source is real and readable: "+label+" "+source);
    kinds.merge(e.getValue().sources().getFirst().kind(),1,Integer::sum);
   }
  }
  check(targets>0,"Targets were produced");
  for(int stage:List.of(20,45,60)) {
   Companion bot=companion(unsafe,PlayerClass.TEMPLAR,stage,Race.ELYOS);bot.skills=learned();var plan=PlayerBotLoadouts.targets(bot,Role.TANK);
   for(var slot:PlayerBotTemporary.GEAR){var t=plan.get(slot);System.out.println("  Templar "+stage+" "+slot+": "+(t==null ? "none" : t.item().getName()+" ["+t.item().getItemQuality()+", "+t.sources().getFirst().kind()+": "+t.sources().getFirst().name()+"]"));}
  }
  System.out.println("Targets "+targets+", first source "+kinds+", slots with no verified target "+empty+" "+missing);
 }
 public static void main(String[] args)throws Exception {
  Field f=Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);Unsafe unsafe=(Unsafe)f.get(null);
  com.aionemu.commons.configuration.transformers.PropertyTransformers.register(new com.aionemu.gameserver.services.cron.CronExpressionTransformer());
  Config.load();
  // Static data starts pool threads that would outlive a failed check.
  try {
   stages();metadata();rules(unsafe);
   if(args.length>0 && args[0].equals("static"))data(unsafe);
   System.out.println("OK: "+checks+" preset equipment checks; nothing was granted, and no account or database was used");
  }catch(Throwable failure){failure.printStackTrace();System.exit(1);}
  System.exit(0);
 }
 private PlayerBotLoadoutCheck(){}
}
