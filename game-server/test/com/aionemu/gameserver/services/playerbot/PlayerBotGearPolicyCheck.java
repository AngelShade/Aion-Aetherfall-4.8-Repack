package com.aionemu.gameserver.services.playerbot;

import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import sun.misc.Unsafe;
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
import com.aionemu.gameserver.services.playerbot.PlayerBotRules.Role;
import com.aionemu.gameserver.utils.stats.AbyssRankEnum;

/** Native item/slot selection and persistent policy fixtures; no live accounts or grants. */
public final class PlayerBotGearPolicyCheck {
 private static int checks;
 private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
 private static void field(Object object,String name,Object value)throws Exception{for(Class<?> type=object.getClass();type!=null;type=type.getSuperclass())try{Field f=type.getDeclaredField(name);f.setAccessible(true);f.set(object,value);return;}catch(NoSuchFieldException ignored){}throw new NoSuchFieldException(name);}
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
 private static class Companion extends Player {
  int id;PlayerClass pc;byte level;Cube cube;Outfit outfit;AbyssRank rank;PlayerSkillList skills;
  Companion(){super(null,null);}
  @Override public int getObjectId(){return id;}
  @Override public PlayerClass getPlayerClass(){return pc;}
  @Override public byte getLevel(){return level;}
  @Override public Race getRace(){return Race.ELYOS;}
  @Override public Gender getGender(){return Gender.MALE;}
  @Override public int getWorldId(){return 110010000;}
  @Override public boolean isSpawned(){return false;}
  @Override public Storage getInventory(){return cube;}
  @Override public Equipment getEquipment(){return outfit;}
  @Override public AbyssRank getAbyssRank(){return rank;}
  @Override public PlayerSkillList getSkillList(){return skills;}
 }
 private static ItemTemplate template(int id,ItemGroup group,int level,ItemQuality quality)throws Exception {
  ItemTemplate t=new ItemTemplate();field(t,"itemId",id);field(t,"itemGroup",group);field(t,"level",level);field(t,"itemQuality",quality);
  field(t,"useLimits",new ItemUseLimits());field(t,"weaponStats",new WeaponStats());field(t,"maxTuneCount",0);return t;
 }
 private static Item weapon(int object,int id,int damage)throws Exception {
  ItemTemplate t=template(id,ItemGroup.SWORD,60,ItemQuality.LEGEND);WeaponStats w=t.getWeaponStats();field(w,"minDamage",damage);field(w,"maxDamage",damage);
  return new Item(object,t,1,false,0);
 }
 private static PlayerBotGearPolicy.Settings setting(String mode,String profile,String quality,String level,String ratio,String weapon,String vendor,String rolls) {
  return PlayerBotGearPolicy.parse(mode,profile,quality,level,ratio,weapon,vendor,rolls);
 }
 public static void main(String[] args)throws Exception {
  Field f=Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);Unsafe unsafe=(Unsafe)f.get(null);
  Companion bot=(Companion)unsafe.allocateInstance(Companion.class);bot.id=1999999001;bot.pc=PlayerClass.TEMPLAR;bot.level=65;bot.cube=new Cube();bot.outfit=new Outfit();bot.skills=new PlayerSkillList();
  bot.rank=(AbyssRank)unsafe.allocateInstance(AbyssRank.class);field(bot.rank,"rank",AbyssRankEnum.GRADE9_SOLDIER);
  var stats=(com.aionemu.gameserver.model.stats.container.PlayerGameStats)unsafe.allocateInstance(com.aionemu.gameserver.model.stats.container.PlayerGameStats.class);bot.setGameStats(stats);
  var skillsBefore=DataManager.SKILL_DATA;var itemsBefore=DataManager.ITEM_DATA;
  Path path=Path.of("config/playerbots/gear-character-1999999001.properties");check(!Files.exists(path),"Fixture must not replace an existing gear file");
  try {
   DataManager.SKILL_DATA=new SkillData();
   PlayerBotSession session=(PlayerBotSession)unsafe.allocateInstance(PlayerBotSession.class);field(session,"owner",bot);field(session,"bot",bot);field(session,"role",Role.TANK);
   // getAccount is native and reads the player field; use an isolated account object.
   var account=new com.aionemu.gameserver.model.account.Account(1999999001);field(bot,"playerAccount",account);
   var state=PlayerBotGearPolicy.state(session);
   check(state.settings.mode()==PlayerBotGearPolicy.Mode.EARNED && !state.settings.vendors(),"No generated gear or spending without a per-bot choice");
   for(var mode:PlayerBotGearPolicy.Mode.values())for(var profile:PlayerBotGearPolicy.Profile.values())for(var quality:ItemQuality.values())if(quality!=ItemQuality.JUNK) {
    var selected=setting(mode.name(),profile.name(),quality.name(),"60","1.1","AUTO","false","UPGRADES");check(selected.mode()==mode && selected.profile()==profile && selected.quality()==quality,"All native quality/profile/acquisition combinations are usable");
   }
   for(String ratio:List.of("NaN","Infinity","0.99","2.01"))try{setting("EARNED","AUTO","LEGEND","0",ratio,"AUTO","false","PASS");throw new AssertionError("Invalid ratio accepted");}catch(IllegalArgumentException expected){check(true,"Reject non-finite/unbounded ratio");}
   try{setting("EARNED","AUTO","LEGEND","66","1.1","AUTO","false","PASS");throw new AssertionError("Unbounded level accepted");}catch(IllegalArgumentException expected){check(true,"Generation has a native level ceiling");}
   check(!PlayerBotGearPolicy.improvement(109,100,1.1) && PlayerBotGearPolicy.improvement(111,100,1.1),"Small changes do not churn gear below configured ratio");
   check(PlayerBotGearPolicy.improvement(2,0,1.1) && !PlayerBotGearPolicy.improvement(Double.NaN,0,1.1),"Empty equipment accepts real upgrades only");
   state.settings=setting("GENERATED","TANK","UNIQUE","60","1.2","SWORD","true","PASS");state.generated.add(123);state.starterSlots.add(1L);state.save();
   var reloaded=new PlayerBotGearPolicy.State(session);check(reloaded.settings.equals(state.settings) && reloaded.generated.contains(123) && reloaded.starterSlots.contains(1L),"Modes, thresholds and generation history survive recruitment/restart");
   check(PlayerBotGearPolicy.targetLevel(session,state.settings)==60,"Explicit generation level cap applies");bot.level=40;check(PlayerBotGearPolicy.targetLevel(session,state.settings)==40,"Generation never exceeds native character level");bot.level=65;
   state.settings=PlayerBotGearPolicy.DEFAULT;
   Item weak=weapon(1,100001,20),strong=weapon(2,100002,50);bot.cube.contents.add(strong);
   check(PlayerBotGearPolicy.upgrades(bot,Role.TANK).getFirst().item()==strong,"Empty main hand selects useful inventory weapon");
   weak.setEquipped(true);weak.setEquipmentSlot(ItemSlot.MAIN_HAND.getSlotIdMask());bot.outfit.contents.add(weak);
   check(PlayerBotGearPolicy.upgrades(bot,Role.TANK).getFirst().gain()>1,"Whole native displaced equipment is compared");
   check(PlayerBotGearPolicy.slots(bot,strong.getItemTemplate()).equals(List.of(1L)),"Native dual wield mastery is required for off-hand weapons");
   field(stats,"skillEfficiency",1f);check(PlayerBotGearPolicy.slots(bot,strong.getItemTemplate()).equals(List.of(1L,2L)),"Native dual wield allows comparison of both hands");field(stats,"skillEfficiency",0f);
   check(PlayerBotGearPolicy.improvement(110,100,1.1),"Exact ten-percent upgrade is accepted despite floating point rounding");
   check(PlayerBotGearPolicy.roll(bot,strong.getItemTemplate()),"Useful native party loot is rolled for");
   state.settings=setting("EARNED","AUTO","LEGEND","0","1.1","AUTO","false","PASS");check(!PlayerBotGearPolicy.roll(bot,strong.getItemTemplate()),"Per-bot passing preference applies");state.settings=PlayerBotGearPolicy.DEFAULT;
   ItemTemplate wrongRace=template(100003,ItemGroup.PL_TORSO,60,ItemQuality.UNIQUE);field(wrongRace,"race",Race.ASMODIANS);check(!PlayerBotGearPolicy.eligible(bot,wrongRace),"Opposite-faction equipment cannot be selected");
   ItemTemplate high=template(100004,ItemGroup.PL_TORSO,70,ItemQuality.UNIQUE);check(!PlayerBotGearPolicy.generatedTemplate(bot,high,state.settings,65),"Items above chosen level cannot be generated");
   ItemTemplate great=template(100005,ItemGroup.GREATSWORD,60,ItemQuality.LEGEND);check(!PlayerBotGearPolicy.allowedWeapon(great,state.settings,Role.TANK),"Auto tank profile preserves shield-capable setup");
   state.settings=setting("EARNED","DAMAGE","LEGEND","0","1.1","2H_SWORD","false","UPGRADES");check(PlayerBotGearPolicy.allowedWeapon(great,state.settings,Role.MELEE),"Explicit greatsword choice maps to native group");
   check(!PlayerBotGearPolicy.allowedWeapon(strong.getItemTemplate(),state.settings,Role.MELEE),"Weapon preference filters unwanted choices");state.settings=PlayerBotGearPolicy.DEFAULT;
   DataManager.ITEM_DATA=new ItemData(){@Override public ItemTemplate getItemTemplate(int id){return id==100001 ? weak.getItemTemplate() : strong.getItemTemplate();}};
   check(PlayerBotGearPolicy.rewardChoice(bot,Role.TANK,List.of(new QuestItems(100001,1),new QuestItems(100002,1)))==1,"Quest choice uses real improvement instead of old weapon-type bonus");
   DataManager.SKILL_DATA=new SkillData(){@Override public Set<Integer> getMasterySkills(ItemGroup group){return Set.of(1999999999);}};
   check(!PlayerBotGearPolicy.eligible(bot,strong.getItemTemplate()),"Missing native mastery blocks gear");
   System.out.println("OK: "+checks+" equipment acquisition/profile/native item/persistence checks; no live players, items or Kinah changed");
  }finally{DataManager.SKILL_DATA=skillsBefore;DataManager.ITEM_DATA=itemsBefore;Files.deleteIfExists(path);}
 }
 private PlayerBotGearPolicyCheck(){}
}
