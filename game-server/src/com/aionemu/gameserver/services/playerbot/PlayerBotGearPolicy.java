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
import com.aionemu.gameserver.network.aion.serverpackets.SM_QUESTION_WINDOW;
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
 }
 static final Set<String> WEAPONS=Set.of("SWORD","DAGGER","MACE","ORB","SPELLBOOK","POLEARM","STAFF","BOW","HARP","GUN","CANNON","KEYBLADE","2H_SWORD");
 static final Settings DEFAULT=new Settings(Mode.EARNED,Profile.AUTO,ItemQuality.LEGEND,0,1.1,"AUTO",false,Rolls.UPGRADES);
 static final class State {
  final PlayerBotSession session;
  Settings settings=DEFAULT;
  final Set<Integer> generated=new HashSet<>();
  final Set<Long> starterSlots=new HashSet<>();
  long next;
  State(PlayerBotSession session) {
   this.session=session;
   try {
    Properties p=PlayerBotMetadata.load(session.owner().getAccount().getId(),session.bot().getObjectId(),"gear",path());
    if(!p.isEmpty()) {
    if(Integer.parseInt(p.getProperty("account"))!=session.owner().getAccount().getId() || Integer.parseInt(p.getProperty("character"))!=session.bot().getObjectId())
     throw new IOException("Companion equipment settings owner mismatch");
    settings=parse(p.getProperty("mode"),p.getProperty("profile"),p.getProperty("quality"),p.getProperty("level"),p.getProperty("threshold"),p.getProperty("weapon"),p.getProperty("vendors"),p.getProperty("rolls"));
    for(String id:p.getProperty("generated","").split(","))if(!id.isBlank())generated.add(Integer.parseInt(id));
    for(String slot:p.getProperty("starterSlots","").split(","))if(!slot.isBlank())starterSlots.add(Long.parseLong(slot));
    }
   }catch(Exception error){throw new IllegalStateException("Cannot load companion equipment settings",error);}
  }
  Path path(){return Path.of("config","playerbots","gear-character-"+session.bot().getObjectId()+".properties");}
  void save() {
   Properties p=new Properties();p.setProperty("account",Integer.toString(session.owner().getAccount().getId()));p.setProperty("character",Integer.toString(session.bot().getObjectId()));
   Settings s=settings;p.setProperty("mode",s.mode.name());p.setProperty("profile",s.profile.name());p.setProperty("quality",s.quality.name());p.setProperty("level",Integer.toString(s.level));
   p.setProperty("threshold",Double.toString(s.threshold));p.setProperty("weapon",s.weapon);p.setProperty("vendors",Boolean.toString(s.vendors));p.setProperty("rolls",s.rolls.name());
   p.setProperty("generated",generated.stream().sorted().map(String::valueOf).collect(java.util.stream.Collectors.joining(",")));
   p.setProperty("starterSlots",starterSlots.stream().sorted().map(String::valueOf).collect(java.util.stream.Collectors.joining(",")));
   try {PlayerBotMetadata.save(session.owner().getAccount().getId(),session.bot().getObjectId(),"gear",p);}
   catch(IOException error){throw new IllegalStateException("Cannot queue companion gear metadata",error);}
  }
 }
 private static final Map<Integer,State> STATES=new ConcurrentHashMap<>();
 static State state(PlayerBotSession session){return STATES.computeIfAbsent(session.bot().getObjectId(),id->new State(session));}
 static Settings settings(Player bot){State s=STATES.get(bot.getObjectId());return s==null ? DEFAULT : s.settings;}
 static void close(PlayerBotSession session){STATES.remove(session.bot().getObjectId());}
 static boolean generated(Player bot,Item item){State s=STATES.get(bot.getObjectId());return s!=null && s.generated.contains(item.getObjectId());}
 static Settings parse(String mode,String profile,String quality,String level,String threshold,String weapon,String vendors,String rolls) {
  if(!"true".equals(vendors) && !"false".equals(vendors))throw new IllegalArgumentException("Vendor equipment must be true or false.");
  return new Settings(Mode.valueOf(mode),Profile.valueOf(profile),ItemQuality.valueOf(quality),Integer.parseInt(level),Double.parseDouble(threshold),weapon,Boolean.parseBoolean(vendors),Rolls.valueOf(rolls));
 }
 public static void configure(PlayerBotSession session,String mode,String profile,String quality,String level,String threshold,String weapon,String vendors,String rolls) {
  if(!session.generated())throw new IllegalArgumentException("Automatic equipment policies apply only to Temporary Bots. Your alt's equipment and build remain yours.");
  Settings selected=parse(mode,profile,quality,level,threshold,weapon,vendors,rolls);
  synchronized(session) {
   if(session.closing())throw new IllegalArgumentException("Companion is saving/dismissing.");
   State s=state(session);Settings before=s.settings;s.settings=selected;
   try{s.save();}catch(RuntimeException error){s.settings=before;throw error;}
   s.next=0;
   PlayerBotQuestSync.notice(session.bot(),"Equipment policy: "+selected.mode+", "+selected.profile+", "+selected.weapon+". Generated gear is limited to "+selected.quality+" and level "+targetLevel(session,selected)+"; gameplay loot is retained.","gear-policy",0);
  }
 }
 static Map<String,Object> snapshot(PlayerBotSession session) {
  Settings s=state(session).settings;
  return Map.of("gearMode",s.mode.name(),"gearProfile",s.profile.name(),"gearQuality",s.quality.name(),"gearLevel",s.level,"gearThreshold",s.threshold,
   "gearWeapon",s.weapon,"gearVendors",s.vendors,"gearRolls",s.rolls.name(),"gearTargetLevel",targetLevel(session,s));
 }
 static int targetLevel(PlayerBotSession session,Settings s){int limit=Math.min(session.bot().getLevel(),session.owner().getLevel());return s.level==0 ? limit : Math.min(limit,s.level);}
 static Role role(Player bot,Role fallback) {
  return switch(settings(bot).profile) {
   case AUTO -> fallback;case TANK -> Role.TANK;case HEALER -> Role.HEALER;case SUPPORT -> Role.SUPPORT;
   case DAMAGE -> switch(bot.getPlayerClass()){case RANGER, MAGE, SORCERER, SPIRIT_MASTER, ENGINEER, GUNNER, ARTIST, BARD -> Role.RANGED;default -> Role.MELEE;};
  };
 }
 static boolean improvement(double score,double current,double ratio){return Double.isFinite(score) && Double.isFinite(current) && Double.isFinite(ratio) && ratio>=1 && score-current>1 && (current<=0 || score+1e-9>=current*ratio);}
 static boolean eligible(Player bot,ItemTemplate t) {
  if(t==null || PlayerBotAppearance.enabled() && PlayerBotAppearance.costume(t) || t.getItemSlot()==0 || ItemSlot.isStigma(t.getItemSlot()) || !(t.isWeapon() || t.isArmor()) || !t.isClassSpecific(bot.getPlayerClass()))return false;
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
  var replaced=bot.getEquipment().getEquippedItems().stream().filter(i->(i.getEquipmentSlot() & slot)!=0).toList();
  // Do not dismantle two-handed weapons to populate only their off-hand.
  if(slot==ItemSlot.SUB_HAND.getSlotIdMask() && replaced.stream().anyMatch(i->i.getItemTemplate().isTwoHandWeapon()))return -Double.MAX_VALUE;
  if(!t.isTwoHandWeapon() && replaced.stream().anyMatch(i->Long.bitCount(i.getEquipmentSlot())>1) && slot!=ItemSlot.MAIN_HAND.getSlotIdMask())return -Double.MAX_VALUE;
  double current=replaced.stream().mapToDouble(i->score(bot,i,role)).sum();
  return improvement(score,current,settings(bot).threshold) ? score-current : -Double.MAX_VALUE;
 }
 static double score(Player bot,ItemTemplate t,Role role) {
  double result=PlayerBotEquipment.score(t,role,bot.getPlayerClass());
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
  double result=score(bot,item.getItemTemplate(),role)+item.getEnchantLevel()*2;
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
  for(var modifier:bonuses)if(!modifier.hasConditions())result+=modifier.getValue()*PlayerBotEquipment.weight(modifier.getName(),role,magical);
  return result;
 }
 static List<PlayerBotEquipment.Upgrade> upgrades(Player bot,Role requested) {
  Role role=role(bot,requested);Settings s=settings(bot);List<PlayerBotEquipment.Upgrade> result=new ArrayList<>();
  for(Item item:bot.getInventory().getItems()) {
   var t=item.getItemTemplate();if(item.isEquipped() || !item.isIdentified() || !eligible(bot,t) || !allowedWeapon(t,s,role))continue;
   double score=score(bot,item,role);
   for(long slot:slots(bot,t)){double gain=gain(bot,t,score,slot,role);if(gain>1)result.add(new PlayerBotEquipment.Upgrade(item,slot,gain));}
  }
  return result.stream().sorted(Comparator.comparingDouble(PlayerBotEquipment.Upgrade::gain).reversed().thenComparingInt(u->u.item().getObjectId()).thenComparingLong(PlayerBotEquipment.Upgrade::slot)).toList();
 }
 static boolean equip(PlayerBotSession session,PlayerBotEquipment.Upgrade upgrade) {
  Player bot=session.bot();Item item=upgrade.item();
  if(bot.getController().isInCombat() || session.owner().getController().isInCombat() || !eligible(bot,item.getItemTemplate()))return false;
  if(!item.isIdentified()){bot.getObserveController().notifyItemuseObservers(item);ItemActionService.identifyItem(bot,item);return true;}
  boolean equipped=bot.getEquipment().equipItem(item.getObjectId(),upgrade.slot())!=null;
  if(!equipped && item.getItemTemplate().isSoulBound() && !item.isSoulBound())
   equipped=bot.getResponseRequester().respond(SM_QUESTION_WINDOW.STR_SOUL_BOUND_ITEM_DO_YOU_WANT_SOUL_BOUND,1);
  if(equipped)PlayerBotQuestSync.notice(bot,(item.isEquipped() ? "I equipped " : "I am binding ")+ChatUtil.item(item.getItemId())+" for my "+settings(bot).profile+" equipment profile.","gear:"+item.getObjectId(),5000);
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
   if(value>best){best=value;selected=index;}
  }
  return selected;
 }
 static boolean roll(Player bot,ItemTemplate t) {
  Settings s=settings(bot);if(t==null || s.rolls==Rolls.PASS || bot.getInventory().isFull(t.getExtraInventoryId()))return false;
  if(s.rolls==Rolls.ALL)return true;State st=STATES.get(bot.getObjectId());Role role=role(bot,st==null ? PlayerBotRules.roleFor(bot.getPlayerClass()) : st.session.combatRole());
  if(!eligible(bot,t) || !allowedWeapon(t,s,role))return false;
  for(long slot:slots(bot,t))if(gain(bot,t,score(bot,t,role),slot,role)>1)return true;
  return false;
 }
 static Trigger trigger(PlayerBotSession session,PlayerBotNavigation navigation) {
  State s=state(session);Action action=new Action(){public String name(){return "acquire companion equipment";}public boolean isUseful(){return available(session,s);}public boolean isPossible(){return available(session,s);}public boolean execute(){return perform(session,navigation,s);}};
  return new Trigger(()->available(session,s),action,()->DEFAULT_PRIORITY);
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
   && t.getItemGroup()!=ItemGroup.WING && t.getItemGroup()!=ItemGroup.PLUME && !t.hasLimitOne() && !t.hasWorldRestrictions();
 }
 static Candidate candidate(PlayerBotSession session,State s) {
  Player bot=session.bot();Role role=role(bot,session.combatRole());int level=targetLevel(session,s.settings);Candidate best=null;
  for(ItemTemplate t:DataManager.ITEM_DATA.getItemTemplates()) {
   if(!generatedTemplate(bot,t,s.settings,level))continue;
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
     var t=DataManager.ITEM_DATA.getItemTemplate(id);if(!eligible(bot,t) || !allowedWeapon(t,s.settings,role) || t.getAcquisition()!=null || bot.getInventory().getItemCountByItemId(id)>0)continue;
     double gain=slots(bot,t).stream().mapToDouble(slot->gain(bot,t,score(bot,t,role),slot,role)).max().orElse(0);if(gain<=1)continue;
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
