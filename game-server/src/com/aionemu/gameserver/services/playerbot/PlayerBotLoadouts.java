package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.Gender;
import com.aionemu.gameserver.model.PlayerClass;
import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.model.gameobjects.Item;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.items.ItemSlot;
import com.aionemu.gameserver.model.templates.item.ItemTemplate;
import com.aionemu.gameserver.model.templates.item.enums.ItemGroup;
import com.aionemu.gameserver.services.LootCodexService;
import com.aionemu.gameserver.services.playerbot.PlayerBotRules.Role;

/** Compares a preset's level stage with what the bot holds, shows each gap and records a goal from a verified source.
 * A target only guides acquisition: nothing here creates items, and held gear that scores higher is never replaced for it.
 * References and their protection stay in PlayerBotGearPolicy; fallback equipment stays in PlayerBotTemporary. */
final class PlayerBotLoadouts {
 static final Set<String> KINDS=Set.of("quest","recipe","vendor","exchange","npc","item");
 /** SAVED worn from the preset, HELD saved but not legal now, MISSING saved item gone, GAP target not yet held, AUTO nothing to do. */
 enum Status {SAVED,HELD,MISSING,GAP,AUTO}
 record Target(ItemTemplate item,List<LootCodexService.Acquisition> sources) {}
 record Row(ItemSlot slot,Item worn,String saved,Status status,Target goal) {}
 private record Key(PlayerClass pc,Race race,Gender gender,Role role,String weapon,boolean dual,String armor,int stage,PlayerBotStigmaBuilds.Build build) {}
 private static final Map<Key,Map<ItemSlot,Target>> TARGETS=new ConcurrentHashMap<>();
 private static volatile Object loaded;

 /** The best verified item reachable by the end of a stage, cached with its scoring strategy and native restrictions. */
 static Map<ItemSlot,Target> targets(Player bot,Role role) {
  if(loaded!=DataManager.ITEM_DATA){TARGETS.clear();loaded=DataManager.ITEM_DATA;}
  var key=new Key(bot.getPlayerClass(),bot.getRace(),bot.getGender(),role,PlayerBotGearPolicy.settings(bot).weapon(),com.aionemu.gameserver.skillengine.effect.WeaponDualEffect.hasDualWieldEffect(bot),
   PlayerBotBuildRules.armor(bot.getPlayerClass(),bot.getSkillList()::isSkillPresent),PlayerBotStigmaBuilds.stage(bot.getLevel()),PlayerBotGearPolicy.stigmaBuild(bot));
  return TARGETS.computeIfAbsent(key,k->compute(bot,k));
 }
 private static boolean listed(ItemTemplate t,Key k,int top) {
  if(!PlayerBotCombatGear.usable(t) || com.aionemu.gameserver.services.ItemEligibility.isAppearanceOnly(t) || !(t.isWeapon() || t.isArmor()) || t.getItemSlot()==0 || ItemSlot.isStigma(t.getItemSlot()) || !t.isClassSpecific(k.pc))return false;
  int required=t.getRequiredLevel(k.pc),max=t.getMaxLevelRestrict(k.pc);var limits=t.getUseLimits();
  return required>=0 && required<=top && t.getLevel()<=top && (max==0 || max>=k.stage) && (t.getRace()==Race.PC_ALL || t.getRace()==k.race) && (limits.getGenderPermitted()==null || limits.getGenderPermitted()==k.gender)
   && t.getExpireTime()==0 && !t.hasWorldRestrictions() && limits.getMinRank()==1 && limits.getGuildLevelPermitted()==0 && t.getItemGroup()!=ItemGroup.WING && t.getItemGroup()!=ItemGroup.PLUME
   && (!t.getItemGroup().name().matches("(PL|CH|LT|RB)_(TORSO|GLOVE|SHOULDER|PANTS|SHOES)") || t.getItemGroup().name().startsWith(k.armor));
 }
 private static boolean fits(ItemTemplate t,ItemSlot slot,Key k,ItemGroup weapon) {
  if(slot==ItemSlot.MAIN_HAND)return t.isWeapon() && t.getItemGroup()==weapon;
  if(slot==ItemSlot.SUB_HAND)return PlayerBotTemporary.offHandShield(k.pc,k.role) ? t.getItemGroup()==ItemGroup.SHIELD : k.dual && t.isOneHandWeapon() && t.getItemGroup()==weapon;
  return !t.isWeapon() && t.getItemGroup()!=ItemGroup.SHIELD && (t.getItemSlot() & slot.getSlotIdMask())!=0 && (Long.bitCount(t.getItemSlot())==1 || t.getItemGroup()==ItemGroup.EARRING || t.getItemGroup()==ItemGroup.RING);
 }
 private static Map<ItemSlot,Target> compute(Player bot,Key k) {
  int top=PlayerBotStigmaBuilds.stageEnd(k.stage);var best=new EnumMap<ItemSlot,Target>(ItemSlot.class);var scores=new EnumMap<ItemSlot,Double>(ItemSlot.class);
  ItemGroup weapon=k.weapon.equals("AUTO") ? PlayerBotTemporary.preferredWeapon(k.pc,k.role) : ItemGroup.valueOf(k.weapon.equals("2H_SWORD") ? "GREATSWORD" : k.weapon);
  for(var t:DataManager.ITEM_DATA.getItemTemplates()) {
   if(!listed(t,k,top))continue;
   double value=PlayerBotGearPolicy.score(bot,t,k.role,k.build);List<LootCodexService.Acquisition> sources=null;
   for(var slot:PlayerBotTemporary.GEAR) {
    Double held=scores.get(slot);
    if(!fits(t,slot,k,weapon) || held!=null && (value<held || value==held && t.getTemplateId()>best.get(slot).item().getTemplateId()))continue;
    if(sources==null)sources=LootCodexService.acquisitions(t.getTemplateId(),k.race);
    if(!sources.isEmpty()){best.put(slot,new Target(t,sources));scores.put(slot,value);}
   }
  }
  // A two-handed target leaves no off-hand to plan for.
  var main=best.get(ItemSlot.MAIN_HAND);if(main!=null && main.item().isTwoHandWeapon())best.remove(ItemSlot.SUB_HAND);
  return Map.copyOf(best);
 }
 private static boolean holds(Player bot,ItemTemplate t) {
  return bot.getInventory().getItemCountByItemId(t.getTemplateId())>0 || bot.getEquipment().getEquippedItems().stream().anyMatch(i->i.getItemId()==t.getTemplateId());
 }
 private static String name(int template){var t=DataManager.ITEM_DATA.getItemTemplate(template);return t==null ? "Item "+template : t.getName();}
 /** One row per slot of the stage. targets is empty until the source index is ready, which only hides GAP rows. */
 static List<Row> plan(PlayerBotSession session,PlayerBotGearPolicy.State s,Map<ItemSlot,Target> targets) {
  Player bot=session.bot();Role role=PlayerBotGearPolicy.role(bot,session.combatRole());String prefix=PlayerBotGearPolicy.stagePrefix(bot,s);var wearing=PlayerBotGearPolicy.saved(bot).values();var rows=new ArrayList<Row>();
  boolean twoHand=wearing.stream().anyMatch(i->i.getItemTemplate().isTwoHandWeapon());
  for(var slot:PlayerBotTemporary.GEAR) {
   Item worn=PlayerBotGearPolicy.worn(bot,slot);var main=PlayerBotGearPolicy.worn(bot,ItemSlot.MAIN_HAND);
   if(slot==ItemSlot.SUB_HAND && (twoHand || main!=null && main.getItemTemplate().isTwoHandWeapon()))continue;
   var reference=s.references.get(prefix+slot.name());
   if(reference!=null) {
    Item held=PlayerBotGearPolicy.owned(bot,reference.object());String saved=name(reference.template());
    if(held!=null && wearing.contains(held))rows.add(new Row(slot,worn,saved,Status.SAVED,null));
    else if(held!=null)rows.add(new Row(slot,worn,saved,Status.HELD,null));
    else {
     // Prefer the exact item that was saved when it can be obtained again.
     var template=DataManager.ITEM_DATA.getItemTemplate(reference.template());
     var sources=template!=null && LootCodexService.warm() ? LootCodexService.acquisitions(reference.template(),bot.getRace()) : List.<LootCodexService.Acquisition>of();
     rows.add(new Row(slot,worn,saved,Status.MISSING,sources.isEmpty() ? null : new Target(template,sources)));
    }
    continue;
   }
   var target=targets.get(slot);
   boolean covered=target==null || holds(bot,target.item()) || PlayerBotGearPolicy.gain(bot,target.item(),PlayerBotGearPolicy.score(bot,target.item(),role),
    target.item().isTwoHandWeapon() ? ItemSlot.MAIN_OR_SUB.getSlotIdMask() : slot.getSlotIdMask(),role)<=1;
   rows.add(new Row(slot,worn,null,covered ? Status.AUTO : Status.GAP,covered ? null : target));
  }
  return rows;
 }
 private static final java.util.concurrent.atomic.AtomicLong WORLD_RUNS=new java.util.concurrent.atomic.AtomicLong(),WORLD_NANOS=new java.util.concurrent.atomic.AtomicLong(),WORLD_MAX=new java.util.concurrent.atomic.AtomicLong();
 /** World bots share one measured path, so the population's cost can be read from worldstats. */
 private static long worldWindow;
 private static int worldBudget;
 private static synchronized boolean admitWorld(long now) {
  if(now-worldWindow>=1000){worldWindow=now;worldBudget=0;}
  return worldBudget++<Math.clamp(com.aionemu.gameserver.configs.main.PlayerBotEconomyConfig.WORLD_GOALS_PER_SECOND,1,20);
 }
 static void refreshWorld(PlayerBotSession session) {
  long now=System.currentTimeMillis();
  if(session.closing() || now<PlayerBotGearPolicy.state(session).nextGoals
   || !com.aionemu.gameserver.configs.main.PlayerBotEconomyConfig.ECONOMY_ENABLED
   || !com.aionemu.gameserver.configs.main.PlayerBotEconomyConfig.WORLD_GOALS_ENABLED || !admitWorld(now))return;
  long begin=System.nanoTime();
  try{refreshGoals(session);}
  finally{long spent=System.nanoTime()-begin;WORLD_RUNS.incrementAndGet();WORLD_NANOS.addAndGet(spent);WORLD_MAX.accumulateAndGet(spent,Math::max);}
 }
 static String worldStats() {
  long runs=WORLD_RUNS.get();
  return "goal work runs="+runs+", average="+(runs==0?0:WORLD_NANOS.get()/runs/1000)+"us, slowest="+WORLD_MAX.get()/1000+"us, total="+WORLD_NANOS.get()/1000000+"ms";
 }
 /** Keeps the recorded goals in step with the stage. A goal survives dismissal because it commits with the bot's checkpoint. */
 static void refresh(PlayerBotSession session) {
  // Temporary maintenance and the session tick both reach here. All autonomous
  // callers must share the measured/rate-limited owner before advancing nextGoals.
  if(session.autonomous()){refreshWorld(session);return;}
  refreshGoals(session);
 }
 private static void refreshGoals(PlayerBotSession session) {
  var s=PlayerBotGearPolicy.state(session);long now=System.currentTimeMillis();if(session.closing() || now<s.nextGoals)return;
  Player bot=session.bot();var build=PlayerBotGearPolicy.stigmaBuild(bot);var next=new TreeMap<String,String>();String owner="";
  // World bots have no chosen build, but they still want the best gear of their stage. There are hundreds, so they look less often.
  boolean world=session.autonomous() && com.aionemu.gameserver.configs.main.PlayerBotEconomyConfig.ECONOMY_ENABLED && com.aionemu.gameserver.configs.main.PlayerBotEconomyConfig.WORLD_GOALS_ENABLED;
  if(build!=null || world) {
   if(!LootCodexService.warm()){s.nextGoals=now+5000;return;}
   owner=(build!=null?build.key():"world")+"."+PlayerBotStigmaBuilds.stage(bot.getLevel());
   for(var row:plan(session,s,targets(bot,PlayerBotGearPolicy.role(bot,session.combatRole()))))if(row.goal()!=null) {
    var source=row.goal().sources().getFirst();next.put(row.slot().name(),row.goal().item().getTemplateId()+":"+source.kind()+":"+source.id());
   }
  }
  s.nextGoals=now+(world?Math.max(60,com.aionemu.gameserver.configs.main.PlayerBotEconomyConfig.WORLD_GOALS_INTERVAL_SECONDS)*1000L:60000);
  if(next.equals(new TreeMap<>(s.goals)) && owner.equals(s.goalsFor))return;
  boolean added=!s.goals.keySet().containsAll(next.keySet());
  s.goals.clear();s.goals.putAll(next);s.goalsFor=owner;s.saveLoadouts();
  if(added && build!=null)PlayerBotQuestSync.notice(bot,"My "+build.name()+" equipment for levels "+PlayerBotStigmaBuilds.stage(bot.getLevel())+"–"+PlayerBotStigmaBuilds.stageEnd(PlayerBotStigmaBuilds.stage(bot.getLevel()))+" has "+next.size()+" open goals. I am wearing my best legal gear meanwhile.","gear-goal",300000);
 }
 private static String label(ItemSlot slot) {
  return switch(slot){case MAIN_HAND->"Main hand";case SUB_HAND->"Off hand";case TORSO->"Chest";case SHOULDER->"Shoulders";case GLOVES->"Gloves";case PANTS->"Legs";case BOOTS->"Boots";case HELMET->"Head";
   case NECKLACE->"Necklace";case WAIST->"Belt";case EARRINGS_LEFT->"Earring";case EARRINGS_RIGHT->"Earring";case RING_LEFT->"Ring";case RING_RIGHT->"Ring";default->slot.name();};
 }
 private static String source(LootCodexService.Acquisition a) {
  return switch(a.kind()){case "quest"->"Quest: ";case "recipe"->"Craft: ";case "vendor"->"Shop: ";case "exchange"->"Exchange: ";case "npc"->"Drop: ";default->"Container: ";}+a.name();
 }
 static Map<String,Object> snapshot(PlayerBotSession session,PlayerBotGearPolicy.State s) {
  Player bot=session.bot();boolean ready=LootCodexService.warm();int stage=PlayerBotStigmaBuilds.stage(bot.getLevel());
  var rows=new ArrayList<Map<String,Object>>();
  for(var row:plan(session,s,ready ? targets(bot,PlayerBotGearPolicy.role(bot,session.combatRole())) : Map.of())) {
   var map=new HashMap<String,Object>();map.put("slot",label(row.slot()));map.put("worn",row.worn()==null ? "" : row.worn().getItemTemplate().getName());map.put("status",row.status().name());
   if(row.saved()!=null)map.put("saved",row.saved());
   if(row.goal()!=null) {
    var t=row.goal().item();var first=row.goal().sources().getFirst();map.put("target",t.getName());map.put("source",source(first));map.put("sourceDetail",first.detail());
    map.put("moreSources",row.goal().sources().size()-1);map.put("market",t.isTradeable());
    if(t.getRequiredLevel(bot.getPlayerClass())>bot.getLevel())map.put("usableAt",(int)t.getRequiredLevel(bot.getPlayerClass()));
   }
   rows.add(map);
  }
  var stages=new ArrayList<Map<String,Object>>();
  for(int from:PlayerBotStigmaBuilds.STAGES){String prefix=s.build+"."+from+".";stages.add(Map.of("from",from,"to",PlayerBotStigmaBuilds.stageEnd(from),"current",from==stage,"saved",s.references.keySet().stream().filter(k->k.startsWith(prefix)).count()));}
  return Map.of("build",PlayerBotGearPolicy.stigmaBuild(bot).name(),"from",stage,"to",PlayerBotStigmaBuilds.stageEnd(stage),"rows",rows,"stages",stages,"ready",ready);
 }
 private PlayerBotLoadouts() {}
}
