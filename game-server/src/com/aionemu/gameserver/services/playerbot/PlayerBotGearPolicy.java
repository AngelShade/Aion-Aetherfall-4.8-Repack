/* Equipment initialization, incremental replacement and item-use ordering adapted
 * from mod-playerbots PlayerbotFactory.cpp, EquipAction.cpp, BuyAction.cpp and
 * ItemUsageValue.cpp at 037c01418b5d01506917a3db9b44fd56ac5f965c.
 * GPL-2.0-or-later; attribution: third-party/playerbots/AUTHORS.md.
 * Native Aion item, mastery, binding, vendor and loot rules remain authoritative. */
package com.aionemu.gameserver.services.playerbot;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.PlayerClass;
import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.model.TaskId;
import com.aionemu.gameserver.model.gameobjects.*;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.items.ItemSlot;
import com.aionemu.gameserver.model.templates.item.bonuses.StatBonusType;
import com.aionemu.gameserver.model.templates.item.*;
import com.aionemu.gameserver.model.templates.item.enums.*;
import com.aionemu.gameserver.model.templates.tradelist.TradeNpcType;
import com.aionemu.gameserver.model.trade.TradeList;
import com.aionemu.gameserver.services.DialogService;
import com.aionemu.gameserver.services.TradeService;
import com.aionemu.gameserver.services.item.ItemService;
import com.aionemu.gameserver.services.item.ItemActionService;
import com.aionemu.gameserver.skillengine.effect.WeaponDualEffect;
import com.aionemu.gameserver.utils.ChatUtil;
import com.aionemu.gameserver.utils.PositionUtil;
import com.aionemu.gameserver.world.geo.GeoService;
import static com.aionemu.gameserver.services.playerbot.PlayerBotEngine.*;
import com.aionemu.gameserver.services.playerbot.PlayerBotRules.Role;

public final class PlayerBotGearPolicy {
 public enum Mode { EARNED, STARTER, GENERATED }
 public enum Profile { AUTO, TANK, HEALER, DAMAGE, SUPPORT }
 public enum Rolls { PASS, UPGRADES, ALL }
 record Settings(Mode mode,Profile profile,ItemQuality quality,int level,double threshold,String weapon,boolean vendors,Rolls rolls) {
  Settings {
   Objects.requireNonNull(mode);Objects.requireNonNull(profile);Objects.requireNonNull(quality);Objects.requireNonNull(rolls);
   if(quality==ItemQuality.JUNK || level<0 || level>65 || !Double.isFinite(threshold) || threshold<1 || threshold>2)
    throw new IllegalArgumentException("Use an item level of 0–65 and an upgrade ratio of 1.0–2.0.");
   if(!weapon.equals("AUTO") && !WEAPONS.contains(weapon))throw new IllegalArgumentException("Unknown weapon preference.");
  }
  Settings withWeapon(String choice){return new Settings(mode,profile,quality,level,threshold,choice,vendors,rolls);}
 }
 static final Set<String> WEAPONS=Set.of("SWORD","DAGGER","MACE","ORB","SPELLBOOK","POLEARM","STAFF","BOW","HARP","GUN","CANNON","KEYBLADE","2H_SWORD");
 static final Settings DEFAULT=new Settings(Mode.EARNED,Profile.AUTO,ItemQuality.LEGEND,0,1.1,"AUTO",false,Rolls.UPGRADES);
 /** Ticket recruits have no policy choices: earned gear plus catch-up up to their permanent rarity, whatever an older save held. */
 static final Settings RECRUIT=new Settings(Mode.EARNED,Profile.AUTO,ItemQuality.LEGEND,0,1.1,"AUTO",true,Rolls.UPGRADES);
 /** A preset's saved slot points at one owned item; the template still names it after the item is gone. */
 record Reference(int object,int template) {}
 static final class State {
  final PlayerBotSession session;
  Settings settings=DEFAULT;
  String build=PlayerBotStigmaBuilds.AUTO;
  final Set<Integer> generated=new HashSet<>();
  final Set<Long> starterSlots=new HashSet<>();
  final Map<String,Reference> references=new ConcurrentHashMap<>();
  final Map<String,String> goals=new ConcurrentHashMap<>();
  volatile String goalsFor="";
  volatile long pinned,pinnedUntil,nextGoals;
  long next;
  State(PlayerBotSession session) {
   this.session=session;
   if(session.world()) {
    settings=new Settings(Mode.EARNED,Profile.AUTO,ItemQuality.LEGEND,0,1.1,"AUTO",true,Rolls.UPGRADES);
    try {var p=PlayerBotMetadata.load(session.bot().getAccount().getId(),session.bot().getObjectId(),"gear",path());
     for(String id:p.getProperty("generated","").split(","))if(!id.isBlank())generated.add(Integer.parseInt(id));
     loadLoadouts(PlayerBotMetadata.load(session.bot().getAccount().getId(),session.bot().getObjectId(),"loadouts",null));
    }catch(Exception e){throw new IllegalStateException("Cannot load world gear provenance",e);}
    return;
   }
   try {
    Properties p=PlayerBotMetadata.load(session.owner().getAccount().getId(),session.bot().getObjectId(),"gear",path());
    if(!p.isEmpty()) {
    if(Integer.parseInt(p.getProperty("account"))!=session.owner().getAccount().getId() || Integer.parseInt(p.getProperty("character"))!=session.bot().getObjectId())
     throw new IOException("Companion equipment settings owner mismatch");
    settings=parse(p.getProperty("mode"),p.getProperty("profile"),p.getProperty("quality"),p.getProperty("level"),p.getProperty("threshold"),p.getProperty("weapon"),p.getProperty("vendors"),p.getProperty("rolls"));
    for(String id:p.getProperty("generated","").split(","))if(!id.isBlank())generated.add(Integer.parseInt(id));
    for(String slot:p.getProperty("starterSlots","").split(","))if(!slot.isBlank())starterSlots.add(Long.parseLong(slot));
    build=p.getProperty("build",PlayerBotStigmaBuilds.AUTO);
    }
    if(session.recruit())settings=RECRUIT;
    if(session.generated())loadLoadouts(PlayerBotMetadata.load(session.owner().getAccount().getId(),session.bot().getObjectId(),"loadouts",null));
   }catch(Exception error){throw new IllegalStateException("Cannot load companion equipment settings",error);}
  }
  private void loadLoadouts(Properties p) {
   for(String key:p.stringPropertyNames())
    if(key.startsWith("ref.")){var f=p.getProperty(key).split(":");references.put(key.substring(4),new Reference(Integer.parseInt(f[0]),Integer.parseInt(f[1])));}
    else if(key.startsWith("goal."))goals.put(key.substring(5),p.getProperty(key));
   goalsFor=p.getProperty("goalsFor","");
  }
  void saveLoadouts() {
   // A world bot keeps its own record, even when this state was first built while it was borrowed.
   int account=(session.world()?session.bot():session.owner()).getAccount().getId();
   Properties p=new Properties();p.setProperty("account",Integer.toString(account));p.setProperty("character",Integer.toString(session.bot().getObjectId()));
   references.forEach((key,r)->p.setProperty("ref."+key,r.object+":"+r.template));goals.forEach((key,goal)->p.setProperty("goal."+key,goal));
   if(!goalsFor.isEmpty())p.setProperty("goalsFor",goalsFor);
   try {PlayerBotMetadata.save(account,session.bot().getObjectId(),"loadouts",p);}
   catch(IOException error){throw new IllegalStateException("Cannot queue companion loadout metadata",error);}
  }
  Path path(){return Path.of("config","playerbots","gear-character-"+session.bot().getObjectId()+".properties");}
  void save() {
   int account=session.bot().getAccount().getId();
   Properties p=new Properties();p.setProperty("account",Integer.toString(account));p.setProperty("character",Integer.toString(session.bot().getObjectId()));
   Settings s=settings;p.setProperty("mode",s.mode.name());p.setProperty("profile",s.profile.name());p.setProperty("quality",s.quality.name());p.setProperty("level",Integer.toString(s.level));
   p.setProperty("build",build);p.setProperty("threshold",Double.toString(s.threshold));p.setProperty("weapon",s.weapon);p.setProperty("vendors",Boolean.toString(s.vendors));p.setProperty("rolls",s.rolls.name());
   p.setProperty("generated",generated.stream().sorted().map(String::valueOf).collect(java.util.stream.Collectors.joining(",")));
   p.setProperty("starterSlots",starterSlots.stream().sorted().map(String::valueOf).collect(java.util.stream.Collectors.joining(",")));
   try {PlayerBotMetadata.save(account,session.bot().getObjectId(),"gear",p);}
   catch(IOException error){throw new IllegalStateException("Cannot queue companion gear metadata",error);}
  }
 }
 private static final Map<Integer,State> STATES=new ConcurrentHashMap<>();
 static State state(PlayerBotSession session){return STATES.computeIfAbsent(session.bot().getObjectId(),id->new State(session));}
 /** The saved policy, with a chosen build's weapon in place of AUTO so every equip, upgrade and roll path agrees on it. */
 static Settings settings(Player bot) {
  State s=STATES.get(bot.getObjectId());if(s==null)return DEFAULT;
  var build=stigmaBuild(bot);
  return build==null || build.weapon()==null || !s.settings.weapon.equals("AUTO") ? s.settings : s.settings.withWeapon(build.weapon());
 }
 static void close(PlayerBotSession session){STATES.remove(session.bot().getObjectId());}
 static boolean generated(Player bot,Item item){State s=STATES.get(bot.getObjectId());return s!=null && s.generated.contains(item.getObjectId());}
 static Settings parse(String mode,String profile,String quality,String level,String threshold,String weapon,String vendors,String rolls) {
  if(!"true".equals(vendors) && !"false".equals(vendors))throw new IllegalArgumentException("Vendor equipment must be true or false.");
  return new Settings(Mode.valueOf(mode),Profile.valueOf(profile),ItemQuality.valueOf(quality),Integer.parseInt(level),Double.parseDouble(threshold),weapon,Boolean.parseBoolean(vendors),Rolls.valueOf(rolls));
 }
 public static void configure(PlayerBotSession session,String mode,String profile,String quality,String level,String threshold,String weapon,String vendors,String rolls) {
  if(!session.generated())throw new IllegalArgumentException("Automatic equipment policies apply only to Temporary Bots. Your alt's equipment and build remain yours.");
  if(session.recruit())throw new IllegalArgumentException("A ticket recruit always manages its own equipment, up to its permanent gear rarity.");
  Settings selected=parse(mode,profile,quality,level,threshold,weapon,vendors,rolls);
  synchronized(session) {
   if(session.closing())throw new IllegalArgumentException("Companion is saving/dismissing.");
   State s=state(session);Settings before=s.settings;s.settings=selected;
   try{s.save();}catch(RuntimeException error){s.settings=before;throw error;}
   s.next=0;
   PlayerBotQuestSync.notice(session.bot(),"Equipment policy: "+selected.mode+", "+selected.profile+", "+selected.weapon+". Generated gear is limited to "+selected.quality+" and level "+targetLevel(session,selected)+"; gameplay loot is retained.","gear-policy",0);
  }
 }
 /** The Stigma build of this bot: a world bot's own, or the one chosen for a Temporary Bot. Null for automatic Stigmas (also when its class has no such build). */
 static PlayerBotStigmaBuilds.Build stigmaBuild(Player bot){if(bot.isWorldBot())return PlayerBotStigmaBuilds.world(bot.getPlayerClass(),bot.getObjectId());State s=STATES.get(bot.getObjectId());return s==null || s.build.equals(PlayerBotStigmaBuilds.AUTO) ? null : PlayerBotStigmaBuilds.find(bot.getPlayerClass(),s.build);}
 public static String configureBuild(PlayerBotSession session,String key) {
  if(!session.generated())throw new IllegalArgumentException("Stigma builds apply only to Temporary Bots. Your alt's Stigmas remain yours.");
  var build=PlayerBotStigmaBuilds.find(session.bot().getPlayerClass(),key);
  if(!key.equals(PlayerBotStigmaBuilds.AUTO) && build==null)throw new IllegalArgumentException("Choose a Stigma build for this class.");
  synchronized(session) {
   if(session.closing())throw new IllegalArgumentException("Companion is saving/dismissing.");
   State s=state(session);String before=s.build;s.build=key;
   try{s.save();}catch(RuntimeException error){s.build=before;throw error;}
  }
  state(session).pinnedUntil=0;state(session).nextGoals=0;
  return session.bot().getName()+(build==null ? " will choose Stigmas automatically for its role." : " will use the "+build.name()+" Stigma build out of combat.");
 }
 private static String stagePrefix(String build,int stage){return build+"."+stage+".";}
 static String stagePrefix(Player bot,State s){return stagePrefix(s.build,PlayerBotStigmaBuilds.stage(bot.getLevel()));}
 static Item worn(Player bot,ItemSlot slot) {
  return bot.getEquipment().getEquippedItems().stream().filter(i->(i.getEquipmentSlot() & slot.getSlotIdMask())!=0 && (slot!=ItemSlot.SUB_HAND || !i.getItemTemplate().isTwoHandWeapon())).findFirst().orElse(null);
 }
 static Item owned(Player bot,int object){Item item=bot.getInventory().getItemByObjId(object);return item!=null ? item : bot.getEquipment().getEquippedItemByObjId(object);}
 static boolean wearable(Player bot,Item item,ItemSlot slot) {
  var t=item.getItemTemplate();Settings s=settings(bot);
  return item.isIdentified() && !PlayerBotRoadMerchants.merchandise(bot,item) && eligible(bot,t) && (s.weapon.equals("AUTO") || allowedWeapon(t,s,Role.MELEE))
   && (t.isTwoHandWeapon() ? slot==ItemSlot.MAIN_HAND : slots(bot,t).contains(slot.getSlotIdMask()));
 }
 /** Items the chosen preset saved for this level stage that the bot can wear now, by equipment mask.
  * A missing or currently illegal item is left out, so its slot takes the normal owned or catch-up fallback. */
 static Map<Long,Item> saved(Player bot) {
  State s=STATES.get(bot.getObjectId());if(s==null || s.references.isEmpty() || stigmaBuild(bot)==null)return Map.of();
  var result=new LinkedHashMap<Long,Item>();String prefix=stagePrefix(bot,s);long pinned=0;
  for(var slot:PlayerBotTemporary.GEAR) {
   var reference=s.references.get(prefix+slot.name());Item item=reference==null ? null : owned(bot,reference.object());
   if(item==null || !wearable(bot,item,slot) || slot==ItemSlot.SUB_HAND && result.containsKey(ItemSlot.MAIN_OR_SUB.getSlotIdMask()))continue;
   long mask=item.getItemTemplate().isTwoHandWeapon() ? ItemSlot.MAIN_OR_SUB.getSlotIdMask() : slot.getSlotIdMask();
   result.put(mask,item);pinned|=mask|(item.isEquipped() ? item.getEquipmentSlot() : 0);
  }
  s.pinned=pinned;s.pinnedUntil=System.currentTimeMillis()+1000;
  return result;
 }
 /** Slots held by saved items. Gain comparisons run for thousands of templates, so this is cached for a second. */
 static long pinned(Player bot) {
  State s=STATES.get(bot.getObjectId());if(s==null || s.references.isEmpty())return 0;
  if(System.currentTimeMillis()>=s.pinnedUntil)saved(bot);
  return s.pinned;
 }
 /** Any preset of this bot saves this item, so automatic retirement and extraction leave it alone. */
 static boolean referenced(Player bot,Item item){State s=STATES.get(bot.getObjectId());return s!=null && s.references.values().stream().anyMatch(r->r.object()==item.getObjectId());}
 /** The bot is working toward this item for its chosen preset. */
 static int goalCount(Player bot){State s=STATES.get(bot.getObjectId());return s==null?0:s.goals.size();}
 /** Every monster source of the open goal items, including goals saved with a recipe/quest first. */
 static List<Integer> goalDrops(Player bot) {
  return goalSources(bot).values().stream().flatMap(Collection::stream).distinct().toList();
 }
 static Map<Integer,List<Integer>> goalSources(Player bot) {
  State s=STATES.get(bot.getObjectId());return s==null?Map.of():PlayerBotGoalHunts.sources(s.goals.values(),bot.getRace());
 }
 static boolean goal(Player bot,ItemTemplate t){State s=STATES.get(bot.getObjectId());return s!=null && t!=null && s.goals.values().stream().anyMatch(g->g.startsWith(t.getTemplateId()+":"));}
 private static State loadoutOwner(PlayerBotSession session) {
  if(!session.generated())throw new IllegalArgumentException("Saved equipment applies only to Temporary Bots. Your alt's equipment remains yours.");
  if(session.closing())throw new IllegalArgumentException("Companion is saving/dismissing.");
  if(stigmaBuild(session.bot())==null)throw new IllegalArgumentException("Choose one of the four builds first. Automatic has no saved equipment.");
  return state(session);
 }
 private static void replaceStage(State s,String prefix,Map<String,Reference> stage) {
  var before=Map.copyOf(s.references);s.references.keySet().removeIf(k->k.startsWith(prefix));s.references.putAll(stage);s.pinnedUntil=0;s.nextGoals=0;
  try{s.saveLoadouts();}catch(RuntimeException error){s.references.clear();s.references.putAll(before);throw error;}
 }
 /** References what is worn now to the chosen preset at this level stage. No item is copied, and one item may serve several presets. */
 public static String saveLoadout(PlayerBotSession session) {
  synchronized(session) {
   State s=loadoutOwner(session);Player bot=session.bot();String prefix=stagePrefix(bot,s);var stage=new HashMap<String,Reference>();
   for(var slot:PlayerBotTemporary.GEAR){Item item=worn(bot,slot);if(item!=null)stage.put(prefix+slot.name(),new Reference(item.getObjectId(),item.getItemId()));}
   if(stage.isEmpty())throw new IllegalArgumentException(bot.getName()+" is not wearing anything to save.");
   replaceStage(s,prefix,stage);
   int from=PlayerBotStigmaBuilds.stage(bot.getLevel());
   return "Saved "+stage.size()+" worn items to "+stigmaBuild(bot).name()+" for levels "+from+"–"+PlayerBotStigmaBuilds.stageEnd(from)+". Nothing was copied.";
  }
 }
 public static String clearLoadout(PlayerBotSession session) {
  synchronized(session) {
   State s=loadoutOwner(session);Player bot=session.bot();replaceStage(s,stagePrefix(bot,s),Map.of());
   int from=PlayerBotStigmaBuilds.stage(bot.getLevel());
   return "Cleared saved equipment for levels "+from+"–"+PlayerBotStigmaBuilds.stageEnd(from)+". Those slots use the best owned or catch-up gear, and the items stay with "+bot.getName()+".";
  }
 }
 /** The owner's manual equip replaces what the preset saved for that slot, so automatic equipping never undoes it. */
 static void manualEquip(PlayerBotSession session,Item item) {
  State s=STATES.get(session.bot().getObjectId());if(s==null || s.references.isEmpty() || stigmaBuild(session.bot())==null)return;
  String prefix=stagePrefix(session.bot(),s);boolean changed=false;
  for(var slot:PlayerBotTemporary.GEAR)if((slot.getSlotIdMask() & item.getEquipmentSlot())!=0 && s.references.containsKey(prefix+slot.name())){s.references.put(prefix+slot.name(),new Reference(item.getObjectId(),item.getItemId()));changed=true;}
  if(changed){s.pinnedUntil=0;s.nextGoals=0;s.saveLoadouts();}
 }
 static void validateLoadouts(Map<String,String> map) {
  for(var e:map.entrySet()) {
   String key=e.getKey(),value=e.getValue();String[] part=key.split("\\."),f=value.split(":");
   if(key.equals("account") || key.equals("character"))continue;
   if(key.equals("goalsFor")){if(!value.matches("[a-z0-9_]{1,24}\\.[0-9]{1,2}") || !PlayerBotStigmaBuilds.STAGES.contains(Integer.parseInt(value.substring(value.indexOf('.')+1))))throw new IllegalArgumentException("Invalid goal stage");continue;}
   if(part[0].equals("ref")) {
    if(part.length!=4 || !part[1].matches("[a-z0-9_]{1,24}") || !PlayerBotStigmaBuilds.STAGES.contains(Integer.parseInt(part[2])) || !PlayerBotTemporary.GEAR.contains(ItemSlot.valueOf(part[3]))
     || f.length!=2 || Integer.parseInt(f[0])<=0 || Integer.parseInt(f[1])<=0)throw new IllegalArgumentException("Invalid saved equipment reference");
   } else if(part[0].equals("goal")) {
    if(part.length!=2 || !PlayerBotTemporary.GEAR.contains(ItemSlot.valueOf(part[1])) || f.length!=3 || Integer.parseInt(f[0])<=0 || !PlayerBotLoadouts.KINDS.contains(f[1]) || Integer.parseInt(f[2])<0)throw new IllegalArgumentException("Invalid acquisition goal");
   } else throw new IllegalArgumentException("Unknown loadout key");
  }
 }
 static Map<String,Object> snapshot(PlayerBotSession session) {
  State state=state(session);Settings s=state.settings;
  var builds=new ArrayList<Map<String,Object>>();
  for(var b:PlayerBotStigmaBuilds.forClass(session.bot().getPlayerClass()))
   builds.add(Map.of("key",b.key(),"name",b.name(),"text",b.text(),"stigmas",b.groups().stream().map(PlayerBotStigmaBuilds::groupName).toList(),
    "savedStages",PlayerBotStigmaBuilds.STAGES.stream().filter(from->state.references.keySet().stream().anyMatch(k->k.startsWith(stagePrefix(b.key(),from)))).count()));
  var result=new HashMap<String,Object>();result.put("stigmaBuild",state.build);result.put("stigmaBuilds",builds);
  if(session.generated() && stigmaBuild(session.bot())!=null)result.put("loadout",PlayerBotLoadouts.snapshot(session,state));
  result.putAll(Map.of("gearMode",s.mode.name(),"gearProfile",s.profile.name(),"gearQuality",s.quality.name(),"gearLevel",s.level,"gearThreshold",s.threshold,
   "gearWeapon",s.weapon,"gearVendors",s.vendors,"gearRolls",s.rolls.name(),"gearTargetLevel",targetLevel(session,s)));
  return result;
 }
 static int targetLevel(PlayerBotSession session,Settings s){int limit=Math.min(session.bot().getLevel(),session.owner().getLevel());return s.level==0 ? limit : Math.min(limit,s.level);}
 static Role role(Player bot,Role fallback) {
  return switch(settings(bot).profile) {
   case AUTO -> fallback;case TANK -> Role.TANK;case HEALER -> Role.HEALER;case SUPPORT -> Role.SUPPORT;
   case DAMAGE -> switch(bot.getPlayerClass()){case RANGER, MAGE, SORCERER, SPIRIT_MASTER, ENGINEER, GUNNER, ARTIST, BARD -> Role.RANGED;default -> Role.MELEE;};
  };
 }
 static Role role(Player bot){State s=STATES.get(bot.getObjectId());return role(bot,s==null ? PlayerBotRules.roleFor(bot.getPlayerClass()) : s.session.combatRole());}
 static boolean improvement(double score,double current,double ratio){return Double.isFinite(score) && Double.isFinite(current) && Double.isFinite(ratio) && ratio>=1 && score-current>1 && (current<=0 || score+1e-9>=current*ratio);}
 /** Buying retains the shared appearance-family exclusion; held/earned gear uses eligible. */
 static boolean purchasable(Player bot,ItemTemplate t) {
  return !com.aionemu.gameserver.services.ItemEligibility.isAppearanceOnly(t) && eligible(bot,t);
 }
 static boolean eligible(Player bot,ItemTemplate t) {
  if(!PlayerBotCombatGear.usable(t) || t.getItemSlot()==0 || ItemSlot.isStigma(t.getItemSlot()) || !(t.isWeapon() || t.isArmor()) || !t.isClassSpecific(bot.getPlayerClass()))return false;
  int required=t.getRequiredLevel(bot.getPlayerClass()),max=t.getMaxLevelRestrict(bot.getPlayerClass());
  if(required<0 || required>bot.getLevel() || max>0 && bot.getLevel()>max || t.getRace()!=Race.PC_ALL && t.getRace()!=bot.getRace())return false;
  var limits=t.getUseLimits();
  if(limits.getGenderPermitted()!=null && limits.getGenderPermitted()!=bot.getGender() || !limits.verifyRank(bot.getAbyssRank().getRank().getId())
   || limits.getGuildLevelPermitted()>(bot.getLegion()==null ? 0 : bot.getLegion().getLegionLevel()) || t.hasWorldRestrictions() && !t.isItemRestrictedToWorld(bot.getWorldId()))return false;
  if(!PlayerBotBuildRules.armorAllowed(t.getItemGroup(),bot.getPlayerClass(),bot.getSkillList()::isSkillPresent))return false;
  if(t.isWeapon() && settings(bot).weapon.equals("AUTO") && !PlayerBotBuildRules.weaponAllowed(t.getItemGroup(),bot.getPlayerClass(),Role.MELEE) && !PlayerBotBuildRules.weaponAllowed(t.getItemGroup(),bot.getPlayerClass(),Role.TANK))return false;
  var mastery=DataManager.SKILL_DATA.getMasterySkills(t.getItemGroup());
  return mastery.isEmpty() || mastery.stream().anyMatch(bot.getSkillList()::isSkillPresent);
 }
 static boolean allowedWeapon(ItemTemplate t,Settings s,Role role) {
  if(!t.isWeapon())return true;
  if(!s.weapon.equals("AUTO"))return t.getItemGroup().name().equals(s.weapon.equals("2H_SWORD") ? "GREATSWORD" : s.weapon);
  return role!=Role.TANK || !t.isTwoHandWeapon() || t.getItemGroup()==ItemGroup.KEYBLADE;
 }
 static List<Long> slots(Player bot,ItemTemplate t) {
  if(t.isTwoHandWeapon())return List.of(ItemSlot.MAIN_OR_SUB.getSlotIdMask());
  if(t.isOneHandWeapon())return WeaponDualEffect.hasDualWieldEffect(bot) ? List.of(ItemSlot.MAIN_HAND.getSlotIdMask(),ItemSlot.SUB_HAND.getSlotIdMask()) : List.of(ItemSlot.MAIN_HAND.getSlotIdMask());
  if(Long.bitCount(t.getItemSlot())>1 && t.getItemGroup()!=ItemGroup.EARRING && t.getItemGroup()!=ItemGroup.RING)return List.of();
  return Arrays.stream(ItemSlot.getSlotsFor(t.getItemSlot())).filter(s->s!=ItemSlot.MAIN_OFF_HAND && s!=ItemSlot.SUB_OFF_HAND && s!=ItemSlot.POWER_SHARD_LEFT && s!=ItemSlot.POWER_SHARD_RIGHT).map(ItemSlot::getSlotIdMask).toList();
 }
 static double gain(Player bot,ItemTemplate t,double score,long slot,Role role) {
  if((pinned(bot) & slot)!=0)return -Double.MAX_VALUE;
  var replaced=bot.getEquipment().getEquippedItems().stream().filter(i->(i.getEquipmentSlot() & slot)!=0).toList();
  // Do not dismantle two-handed weapons to populate only their off-hand.
  if(slot==ItemSlot.SUB_HAND.getSlotIdMask() && replaced.stream().anyMatch(i->i.getItemTemplate().isTwoHandWeapon()))return -Double.MAX_VALUE;
  if(!t.isTwoHandWeapon() && replaced.stream().anyMatch(i->Long.bitCount(i.getEquipmentSlot())>1) && slot!=ItemSlot.MAIN_HAND.getSlotIdMask())return -Double.MAX_VALUE;
  double current=replaced.stream().mapToDouble(i->score(bot,i,role)).sum();
  return improvement(score,current,settings(bot).threshold) ? score-current : -Double.MAX_VALUE;
 }
 static double score(Player bot,ItemTemplate t,Role role) {
  return score(bot,t,role,stigmaBuild(bot));
 }
 static double score(Player bot,ItemTemplate t,Role role,PlayerBotStigmaBuilds.Build build) {
  double result=PlayerBotEquipment.score(t,role,bot.getPlayerClass(),build==null ? null : build.gear());
  // PlayerbotFactory prefers the class's armor type after checking native mastery.
  String group=t.getItemGroup().name();String preferred=switch(bot.getPlayerClass()) {
   case WARRIOR,GLADIATOR,TEMPLAR,RIDER -> "PL_";case PRIEST,CLERIC,CHANTER -> "CH_";
   case SCOUT,ASSASSIN,RANGER,ENGINEER,GUNNER -> "LT_";default -> "RB_";
  };
  if(group.startsWith(preferred))result*=3;
  if(role==Role.TANK && t.getItemGroup()==ItemGroup.SHIELD)result*=3;
  return result;
 }
 static double score(Player bot,Item item,Role role) {
  double result=variantScore(bot,item.getItemTemplate(),role,item.getEnchantLevel(),item.getTempering());
  boolean magical=switch(bot.getPlayerClass()){case PRIEST,CLERIC,MAGE,SORCERER,SPIRIT_MASTER,ENGINEER,GUNNER,RIDER,ARTIST,BARD -> true;default -> false;};
  List<com.aionemu.gameserver.model.stats.calc.functions.StatFunction> bonuses=new ArrayList<>();
  if(item.getBonusStatsId()!=0) {
   var bonus=DataManager.ITEM_RANDOM_BONUSES.getTemplate(StatBonusType.INVENTORY,item.getItemTemplate().getStatBonusSetId(),item.getBonusStatsId());
   if(bonus!=null && bonus.getModifiers()!=null)bonuses.addAll(bonus.getModifiers());
  }
  for(var stone:item.getItemStones())if(stone.getModifiers()!=null)bonuses.addAll(stone.getModifiers());
  for(var stone:item.getFusionStones())if(stone.getModifiers()!=null)bonuses.addAll(stone.getModifiers());
  if(item.getFusionedItemTemplate()!=null) {
   var fusion=item.getFusionedItemTemplate();if(fusion.getModifiers()!=null)bonuses.addAll(fusion.getModifiers());
   if(item.getFusionedItemBonusStatsId()!=0){var bonus=DataManager.ITEM_RANDOM_BONUSES.getTemplate(StatBonusType.INVENTORY,fusion.getStatBonusSetId(),item.getFusionedItemBonusStatsId());if(bonus!=null && bonus.getModifiers()!=null)bonuses.addAll(bonus.getModifiers());}
  }
  var build=stigmaBuild(bot);
  for(var modifier:bonuses)if(!modifier.hasConditions())result+=modifier.getValue()*PlayerBotEquipment.weight(modifier.getName(),role,magical,build==null ? null : build.gear());
  return result;
 }
 static double variantScore(Player bot,ItemTemplate t,Role role,int enchant,int tempering) {
  double result=score(bot,t,role)+enchant*2;
  if(tempering<=0)return result;
  boolean magical=switch(bot.getPlayerClass()){case PRIEST,CLERIC,MAGE,SORCERER,SPIRIT_MASTER,ENGINEER,GUNNER,RIDER,ARTIST,BARD -> true;default -> false;};
  var build=stigmaBuild(bot);var strategy=build==null?null:build.gear();
  if(t.getItemGroup()==ItemGroup.PLUME) {
   var attack="TSHIRT_PHYSICAL".equals(t.getTemperingName())?com.aionemu.gameserver.model.stats.container.StatEnum.PHYSICAL_ATTACK:com.aionemu.gameserver.model.stats.container.StatEnum.BOOST_MAGICAL_SKILL;
   int boost=attack==com.aionemu.gameserver.model.stats.container.StatEnum.PHYSICAL_ATTACK?com.aionemu.gameserver.model.stats.container.PlumStatEnum.PLUM_PHISICAL_ATTACK.getBoostValue():com.aionemu.gameserver.model.stats.container.PlumStatEnum.PLUM_BOOST_MAGICAL_SKILL.getBoostValue();
   result+=tempering*(boost*PlayerBotEquipment.weight(attack,role,magical,strategy)+com.aionemu.gameserver.model.stats.container.PlumStatEnum.PLUM_HP.getBoostValue()*PlayerBotEquipment.weight(com.aionemu.gameserver.model.stats.container.StatEnum.MAXHP,role,magical,strategy));
  } else if(DataManager.TEMPERING_DATA!=null) {
   var levels=DataManager.TEMPERING_DATA.getTemplates(t);var stats=levels==null?null:levels.get(tempering);
   if(stats!=null)for(var stat:stats)result+=stat.getValue()*PlayerBotEquipment.weight(stat.getStat(),role,magical,strategy);
  }
  return result;
 }
 static List<PlayerBotEquipment.Upgrade> upgrades(Player bot,Role requested) {
  Role role=role(bot,requested);Settings s=settings(bot);List<PlayerBotEquipment.Upgrade> result=new ArrayList<>();
  // A saved preset item goes on first. Saved slots accept nothing else, so gain() rejects them below.
  saved(bot).forEach((slot,item)->{if(!item.isEquipped())result.add(new PlayerBotEquipment.Upgrade(item,slot,Double.MAX_VALUE));});
  for(Item item:bot.getInventory().getItems()) {
   var t=item.getItemTemplate();if(item.isEquipped() || PlayerBotRoadMerchants.merchandise(bot,item) || !item.isIdentified() || !eligible(bot,t) || !allowedWeapon(t,s,role))continue;
   double score=score(bot,item,role);
   for(long slot:slots(bot,t)){double gain=gain(bot,t,score,slot,role);if(gain>1)result.add(new PlayerBotEquipment.Upgrade(item,slot,gain));}
  }
  return result.stream().sorted(Comparator.comparingDouble(PlayerBotEquipment.Upgrade::gain).reversed().thenComparingInt(u->u.item().getObjectId()).thenComparingLong(PlayerBotEquipment.Upgrade::slot)).toList();
 }
 static boolean equip(PlayerBotSession session,PlayerBotEquipment.Upgrade upgrade) {
  Player bot=session.bot();Item item=upgrade.item();
  if(PlayerBotRoadMerchants.merchandise(bot,item) || bot.getController().isInCombat() || session.owner().getController().isInCombat() || !eligible(bot,item.getItemTemplate()))return false;
  if(!item.isIdentified()){bot.getObserveController().notifyItemuseObservers(item);ItemActionService.identifyItem(bot,item);return true;}
  // Native bind-on-equip needs a sheathed weapon, a standing pose and a still 5 s cast, and a
  // following or resting companion failed one of them almost every time (recruited alts never
  // equipped bind-on-equip gear). Bots commit the same result directly: bound, then equipped.
  boolean binding=item.getItemTemplate().isSoulBound() && !item.isSoulBound();
  if(binding){item.setSoulBound(true);com.aionemu.gameserver.services.item.ItemPacketService.updateItemAfterInfoChange(bot,item);}
  boolean equipped=bot.getEquipment().equipItem(item.getObjectId(),upgrade.slot())!=null;
  if(!equipped && binding){item.setSoulBound(false);com.aionemu.gameserver.services.item.ItemPacketService.updateItemAfterInfoChange(bot,item);}
  if(equipped)PlayerBotQuestSync.notice(bot,(binding ? "I bound and equipped " : "I equipped ")+ChatUtil.item(item.getItemId())+" for my "+settings(bot).profile+" equipment profile.","gear:"+item.getObjectId(),5000);
  return equipped;
 }
 static int rewardChoice(Player bot,Role requested,List<com.aionemu.gameserver.model.templates.quest.QuestItems> choices) {
  if(choices.isEmpty())return -1;Role role=role(bot,requested);Settings s=settings(bot);int selected=-2;double best=-Double.MAX_VALUE;
  for(int index=0;index<choices.size();index++) {
   var t=DataManager.ITEM_DATA.getItemTemplate(choices.get(index).getItemId());if(t==null || !t.isClassSpecific(bot.getPlayerClass()) || t.getRequiredLevel(bot.getPlayerClass())<0
    || t.getRace()!=Race.PC_ALL && t.getRace()!=bot.getRace() || t.getUseLimits().getGenderPermitted()!=null && t.getUseLimits().getGenderPermitted()!=bot.getGender())continue;
   double value=0;
   if(eligible(bot,t) && allowedWeapon(t,s,role))for(long slot:slots(bot,t))value=Math.max(value,gain(bot,t,score(bot,t,role),slot,role));
   // When no choice improves equipped gear, choose the most useful future gear,
   // then normal vendor value. Never preserve weapon type with an arbitrary bonus.
   if(value<=0)value=allowedWeapon(t,s,role) && t.getItemSlot()!=0 ? score(bot,t,role)/1000000 : Math.min(t.getPrice(),1000000)/1000000000.0;
   if(goal(bot,t))value=Double.MAX_VALUE;
   if(value>best){best=value;selected=index;}
  }
  return selected;
 }
 static boolean roll(Player bot,ItemTemplate t) {
  Settings s=settings(bot);if(t==null || s.rolls==Rolls.PASS || bot.getInventory().isFull(t.getExtraInventoryId()))return false;
  State st=STATES.get(bot.getObjectId());Role role=role(bot,st==null ? PlayerBotRules.roleFor(bot.getPlayerClass()) : st.session.combatRole());
  if(!eligible(bot,t) || !allowedWeapon(t,s,role))return false;
  if(goal(bot,t))return true;
  double value=score(bot,t,role);
  for(long slot:slots(bot,t))if(gain(bot,t,value,slot,role)>1) {
   boolean alreadyOwned=bot.getInventory().getItems().stream().anyMatch(i->!i.isEquipped() && i.isIdentified() && eligible(bot,i.getItemTemplate())
    && allowedWeapon(i.getItemTemplate(),s,role) && slots(bot,i.getItemTemplate()).contains(slot) && score(bot,i,role)>=value);
   if(!alreadyOwned)return true;
  }
  return false;
 }
 static Trigger trigger(PlayerBotSession session,PlayerBotNavigation navigation) {
  State s=state(session);Action action=new Action(){public String name(){return "acquire companion equipment";}public boolean isUseful(){return available(session,s);}public boolean isPossible(){return available(session,s);}public boolean execute(){return perform(session,navigation,s);}};
  return new Trigger(()->available(session,s),action,()->PlayerBotFocus.priority(session,"gear",DEFAULT_PRIORITY,false));
 }
 private static final double DEFAULT_PRIORITY=PlayerBotEngine.DEFAULT+1;
 static boolean available(PlayerBotSession session,State s) {
  Player bot=session.bot(),owner=session.owner();
  return !session.closing() && (s.settings.mode!=Mode.EARNED || s.settings.vendors || unidentified(session)!=null) && System.currentTimeMillis()>=s.next && !bot.isDead() && !owner.isDead()
   && !bot.isFlying() && !owner.isFlying() && !bot.isCasting() && !bot.isLooting() && !bot.getController().hasScheduledTask(TaskId.ITEM_USE)
   && !bot.getController().isInCombat() && !owner.getController().isInCombat() && !owner.getMoveController().isInMove() && PositionUtil.isInRange(bot,owner,40)
   && upgrades(bot,session.combatRole()).isEmpty() && !bot.getInventory().isFull();
 }
 record Candidate(ItemTemplate item,long slot,double gain) {}
 static Item unidentified(PlayerBotSession session) {
  Player bot=session.bot();Settings s=settings(bot);Role role=role(bot,session.combatRole());
  return bot.getInventory().getItems().stream().filter(i->!i.isEquipped() && !i.isIdentified() && eligible(bot,i.getItemTemplate()) && allowedWeapon(i.getItemTemplate(),s,role)).findFirst().orElse(null);
 }
 static boolean generatedTemplate(Player bot,ItemTemplate t,Settings s,int level) {
  return eligible(bot,t) && allowedWeapon(t,s,role(bot,PlayerBotRules.roleFor(bot.getPlayerClass()))) && t.getItemType()==ItemType.NORMAL && t.getAcquisition()==null
   && t.getExpireTime()==0 && t.getExtraInventoryId()<=0 && t.getItemQuality()!=null && t.getItemQuality()!=ItemQuality.JUNK && t.getItemQuality().getQualityId()<=s.quality.getQualityId()
   && t.getRequiredLevel(bot.getPlayerClass())<=level && t.getLevel()<=level && t.getUseLimits().getMinRank()==1 && t.getUseLimits().getGuildLevelPermitted()==0
   && t.getItemGroup()!=ItemGroup.WING && t.getItemGroup()!=ItemGroup.PLUME && !t.hasLimitOne() && !t.hasWorldRestrictions() && PlayerBotCatchUpPool.admitted(t);
 }
 static Candidate candidate(PlayerBotSession session,State s) {
  Player bot=session.bot();Role role=role(bot,session.combatRole());int level=targetLevel(session,s.settings);Candidate best=null;
  Settings settings=settings(bot);
  for(ItemTemplate t:PlayerBotCatchUpPool.forClass(bot.getPlayerClass())) {
   if(!generatedTemplate(bot,t,settings,level))continue;
   for(long slot:slots(bot,t)) {
    if(s.settings.mode==Mode.STARTER && (s.starterSlots.contains(slot) || bot.getEquipment().getEquippedItems().stream().anyMatch(i->(i.getEquipmentSlot() & slot)!=0)))continue;
    if(bot.getInventory().getItemCountByItemId(t.getTemplateId())>0)continue;
    double gain=gain(bot,t,score(bot,t,role),slot,role);
    if(gain>1 && (best==null || gain>best.gain || gain==best.gain && t.getTemplateId()<best.item.getTemplateId()))best=new Candidate(t,slot,gain);
   }
  }
  return best;
 }
 static boolean perform(PlayerBotSession session,PlayerBotNavigation navigation,State s) {
  if(!available(session,s))return false;Player bot=session.bot();
  Item unknown=unidentified(session);
  if(unknown!=null){navigation.stop();s.next=System.currentTimeMillis()+6000;bot.getObserveController().notifyItemuseObservers(unknown);ItemActionService.identifyItem(bot,unknown);
   PlayerBotQuestSync.notice(bot,"I am identifying "+ChatUtil.item(unknown.getItemId())+" before comparing its equipment stats.","gear-identify",0);return true;}
  if(s.settings.mode!=Mode.EARNED) {
   Candidate c=candidate(session,s);
   if(c!=null) {
    navigation.stop();s.next=System.currentTimeMillis()+5000;
    List<Item> created=new ArrayList<>();
    long left=ItemService.addItem(bot,c.item.getTemplateId(),1,false,new ItemService.ItemUpdatePredicate(){@Override public boolean changeItem(Item item){
     // Queue provenance with native inventory; both commit in the same checkpoint.
     s.generated.add(item.getObjectId());s.starterSlots.add(c.slot);s.save();created.add(item);return true;
    }});
    if(left!=0 || created.isEmpty())return false;
    PlayerBotQuestSync.notice(bot,"I generated "+ChatUtil.item(c.item.getTemplateId())+" ("+s.settings.mode+", up to "+s.settings.quality+", level "+targetLevel(session,s.settings)+"). My previous equipment stays in my inventory.","gear-generated",0);
    equip(session,new PlayerBotEquipment.Upgrade(created.getFirst(),c.slot,c.gain));return true;
   }
  }
  if(s.settings.vendors && buy(session,navigation,s))return true;
  s.next=System.currentTimeMillis()+60000;return false;
 }
 record Purchase(Npc npc,ItemTemplate item,long price,double gain) {}
 static boolean buy(PlayerBotSession session,PlayerBotNavigation navigation,State s) {
  Player bot=session.bot(),owner=session.owner();Role role=role(bot,session.combatRole());var care=PlayerBotQuestSync.state(session);
  String day=LocalDate.now().toString();if(!care.spentDay.equals(day)){care.spentDay=day;care.spent=0;care.save();}
  long allowed=PlayerBotCare.allowance(bot.getInventory().getKinah(),care.reserve,care.dailyBudget,care.spent);List<Purchase> choices=new ArrayList<>();
  owner.getKnownList().forEachNpc(npc->{
   if(npc.isDead() || !npc.isSpawned() || bot.isEnemy(npc) || npc.getWorldId()!=bot.getWorldId() || npc.getInstanceId()!=bot.getInstanceId()
    || !PositionUtil.isInRange(owner,npc,40) || !DialogService.isInteractionAllowed(bot,npc))return;
   var catalog=DataManager.TRADE_LIST_DATA.getTradeListTemplate(npc.getNpcId());if(catalog==null || catalog.getTradeNpcType()!=TradeNpcType.NORMAL)return;
   for(var tab:catalog.getTradeTablist()) {
    var goods=DataManager.GOODSLIST_DATA.getGoodsListById(tab.getId());if(goods==null || goods.getItemIdList()==null || goods.getLegionLevel()>(bot.getLegion()==null ? 0 : bot.getLegion().getLegionLevel()))continue;
    for(int id:goods.getItemIdList()) {
     var t=DataManager.ITEM_DATA.getItemTemplate(id);if(!purchasable(bot,t) || !allowedWeapon(t,settings(bot),role) || t.getAcquisition()!=null || bot.getInventory().getItemCountByItemId(id)>0)continue;
     double gain=goal(bot,t) ? Double.MAX_VALUE/2 : slots(bot,t).stream().mapToDouble(slot->gain(bot,t,score(bot,t,role),slot,role)).max().orElse(0);if(gain<=1)continue;
     TradeList list=new TradeList(npc.getObjectId());list.addItem(id,1);
     if(list.calculateBuyListPrice(bot,catalog.getSellPriceRate()) && list.getRequiredKinah()>0 && list.getRequiredKinah()<=allowed)choices.add(new Purchase(npc,t,list.getRequiredKinah(),gain));
    }
   }
  });
  Purchase chosen=choices.stream().max(Comparator.comparingDouble((Purchase p)->p.gain/p.price).thenComparingInt(p->-p.item.getTemplateId())).orElse(null);
  if(chosen==null)return false;
  if(!PositionUtil.isInTalkRange(bot,chosen.npc) || !GeoService.getInstance().canSee(bot,chosen.npc))return navigation.approach(chosen.npc,2);
  if(!available(session,s) || !DialogService.isInteractionAllowed(bot,chosen.npc))return false;
  var catalog=DataManager.TRADE_LIST_DATA.getTradeListTemplate(chosen.npc.getNpcId());TradeList list=new TradeList(chosen.npc.getObjectId());list.addItem(chosen.item.getTemplateId(),1);
  if(!list.calculateBuyListPrice(bot,catalog.getSellPriceRate()) || list.getRequiredKinah()!=chosen.price || chosen.price>PlayerBotCare.allowance(bot.getInventory().getKinah(),care.reserve,care.dailyBudget,care.spent))return false;
  long before=bot.getInventory().getKinah();care.spent+=chosen.price;care.save();boolean success=false;
  try{success=TradeService.performBuyFromShop(chosen.npc,bot,list);return success;}
  finally{care.spent-=chosen.price;care.spent+=Math.max(0,before-bot.getInventory().getKinah());care.save();s.next=System.currentTimeMillis()+10000;
   if(success){PlayerBotQuestSync.notice(bot,"I bought an equipment upgrade, "+ChatUtil.item(chosen.item.getTemplateId())+", for "+chosen.price+" of my own Kinah.","gear-buy",0);PlayerBotQuestSync.returnToOwner(bot);}}
 }
 private PlayerBotGearPolicy() {}
}
