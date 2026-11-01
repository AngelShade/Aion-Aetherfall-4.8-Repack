package com.aionemu.gameserver.services.playerbot;

import java.lang.reflect.*;
import java.util.*;
import sun.misc.Unsafe;
import com.aionemu.gameserver.configs.Config;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.*;
import com.aionemu.gameserver.model.gameobjects.Item;
import com.aionemu.gameserver.model.gameobjects.player.*;
import com.aionemu.gameserver.model.items.storage.*;
import com.aionemu.gameserver.model.templates.item.ItemTemplate;
import com.aionemu.gameserver.model.templates.item.enums.ItemGroup;
import com.aionemu.gameserver.services.StigmaService;
import com.aionemu.gameserver.services.playerbot.PlayerBotRules.SkillKind;
import com.aionemu.gameserver.skillengine.condition.*;
import com.aionemu.gameserver.skillengine.model.SkillTemplate;

/** Runs the 44 builds through the real stone plan and the real Vision choice on real static data, for both races. Run from game-server. */
public final class PlayerBotVisionCheck {
 private static int checks;
 private static final List<String> FAILURES=new ArrayList<>();
 private static void check(boolean value,String message){checks++;if(!value)FAILURES.add(message);}
 private static class Cube extends PlayerStorage {
  Cube(){super(null,StorageType.CUBE);}
  @Override public List<Item> getItems(){return List.of();}
 }
 private static class Outfit extends Equipment {
  List<Item> contents=new ArrayList<>();Outfit(){super(null);}
  @Override public List<Item> getEquippedItemsAllStigma(){return contents;}
  @Override public List<Item> getEquippedItemsByItemId(int id){return contents.stream().filter(i->i.getItemId()==id).toList();}
 }
 private static class Bot extends Player {
  PlayerClass pc;byte level;Race race;Cube cube=null;Outfit outfit;
  Bot(){super(null,null);}
  @Override public PlayerClass getPlayerClass(){return pc;}
  @Override public byte getLevel(){return level;}
  @Override public Race getRace(){return race;}
  @Override public Storage getInventory(){return cube;}
  @Override public Equipment getEquipment(){return outfit;}
 }
 private static Bot bot(Unsafe unsafe,PlayerClass pc,int level,Race race)throws Exception {
  Bot bot=(Bot)unsafe.allocateInstance(Bot.class);bot.pc=pc;bot.level=(byte)level;bot.race=race;bot.cube=new Cube();bot.outfit=new Outfit();return bot;
 }
 /** The same skill filter as PlayerBotSkills.read: only these reach the AI. */
 private static boolean castable(SkillTemplate t){return !t.isPassive() && !t.isProvoked() && t.getProperties()!=null && PlayerBotSkills.classify(t)!=SkillKind.UNSUPPORTED;}
 @SuppressWarnings("unchecked") private static boolean weaponFits(SkillTemplate t,String weapon)throws Exception {
  if(weapon==null || t.getStartconditions()==null)return true;
  Field f=WeaponCondition.class.getDeclaredField("itemGroups");f.setAccessible(true);
  for(var c:t.getStartconditions().getConditions())if(c instanceof WeaponCondition w) {
   var allowed=(Collection<?>)f.get(w);
   if(allowed.stream().noneMatch(g->g.toString().equals(weapon)))return false;
  }
  return true;
 }
 public static void main(String[] args)throws Exception {
  Field f=Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);Unsafe unsafe=(Unsafe)f.get(null);
  com.aionemu.commons.configuration.transformers.PropertyTransformers.register(new com.aionemu.gameserver.services.cron.CronExpressionTransformer());
  Config.load();
  try {
   DataManager.getInstance();
   Method vision=StigmaService.class.getDeclaredMethod("getLinkedStigmaLearnSkill",Player.class);vision.setAccessible(true);
   var classes=List.of(PlayerClass.GLADIATOR,PlayerClass.TEMPLAR,PlayerClass.ASSASSIN,PlayerClass.RANGER,PlayerClass.SORCERER,PlayerClass.SPIRIT_MASTER,PlayerClass.CLERIC,PlayerClass.CHANTER,PlayerClass.RIDER,PlayerClass.GUNNER,PlayerClass.BARD);
   var chosen=new TreeMap<String,Integer>();int builds=0;var sixes=new HashMap<PlayerClass,Set<Set<Integer>>>();
   for(var pc:classes) {
    check(PlayerBotStigmaBuilds.forClass(pc).size()==4,pc+" keeps four builds");
    for(var build:PlayerBotStigmaBuilds.forClass(pc)) {
     builds++;
     for(var race:List.of(Race.ELYOS,Race.ASMODIANS)) {
      String label=pc+"."+build.key()+" "+race;
      for(int level:List.of(20,30,40,45,50,55,65)) {
       Bot actor=bot(unsafe,pc,level,race);
       var plan=PlayerBotTemporary.stigmaPlan(actor,PlayerBotRules.roleFor(pc),build);
       int slots=PlayerBotTemporary.regularSlots(level)+PlayerBotTemporary.advancedSlots(level);
       check(plan.size()==slots,label+" fills all "+slots+" slots at "+level+", got "+plan.size());
       check(plan.values().stream().allMatch(t->PlayerBotStigmaBuilds.priority(build,t)!=Integer.MAX_VALUE),label+" fills every slot from its own list at "+level);
       if(level<55)continue;
       for(var stone:plan.values())actor.outfit.contents.add((Item)unsafe.allocateInstance(ItemOf.class));
       int i=0;for(var stone:plan.values())((ItemOf)actor.outfit.contents.get(i++)).id=stone.getTemplateId();
       int id=(Integer)vision.invoke(null,actor);
       var learn=DataManager.SKILL_TREE_DATA.getSkillsForSkill(id,pc,race,level);
       check(!learn.isEmpty(),label+" Vision "+id+" is learnable at "+level);
       for(var entry:learn) {
        var t=DataManager.SKILL_DATA.getSkillTemplate(entry.getSkillId());
        check(castable(t),label+" Vision "+t.getName()+" can be cast by the AI");
        check(weaponFits(t,build.weapon()),label+" Vision "+t.getName()+" fits weapon "+build.weapon());
       }
       if(level==65) {
        chosen.merge(DataManager.SKILL_DATA.getSkillTemplate(id).getName(),race==Race.ELYOS ? 1 : 0,Integer::sum);
        var worn=new TreeSet<Integer>();plan.values().forEach(t->worn.add(t.getTemplateId()));
        if(race==Race.ELYOS)check(sixes.computeIfAbsent(pc,c->new HashSet<>()).add(worn),label+" wears a different six stones from its class's other builds");
       }
      }
      for(var stone:DataManager.ITEM_DATA.getItemTemplates())if(stone.isStigma() && !stone.isTestItem() && stone.getStigma().isChargeable() && stone.isClassSpecific(pc) && PlayerBotStigmaBuilds.priority(build,stone)!=Integer.MAX_VALUE && (stone.getRace()==Race.PC_ALL || stone.getRace()==race))
       for(String group:stone.getStigma().getGainSkillGroups()) if(build.groups().contains(group)) {
        boolean any=false,fits=true;
        for(var t:DataManager.SKILL_DATA.getSkillTemplatesByGroup(group)){any|=castable(t);fits&=weaponFits(t,build.weapon());}
        check(any,label+" lists "+group+", which the AI can cast");
        check(fits,label+" lists "+group+", which fits weapon "+build.weapon());
       }
     }
    }
   }
   check(builds==44,"44 builds, got "+builds);
   System.out.println("Vision skills at level 65 (Elyos builds): "+chosen);
   if(!FAILURES.isEmpty()){FAILURES.forEach(System.out::println);System.out.println(FAILURES.size()+" failures of "+checks);System.exit(1);}
   System.out.println("OK: "+checks+" Vision checks; nothing was granted, and no account or database was used");
  }catch(Throwable failure){failure.printStackTrace();System.exit(1);}
  System.exit(0);
 }
 private static class ItemOf extends Item {
  int id;ItemOf(){super(0,null,1,false,0);}
  @Override public int getItemId(){return id;}
  @Override public ItemTemplate getItemTemplate(){return DataManager.ITEM_DATA.getItemTemplate(id);}
  @Override public boolean isStigmaChargeable(){return true;}
 }
 private PlayerBotVisionCheck(){}
}
