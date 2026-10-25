/* Corpse looting adapted from mod-playerbots 037c01418b5d01506917a3db9b44fd56ac5f965c:
 * DropTargetAction, LootNonCombatStrategy, LootObjectStack and LootAction.
 * GPL-2.0-or-later; upstream attribution: third-party/playerbots/AUTHORS.md.
 * Aion retains native drop ownership, distribution, quest visibility and inventory custody. */
package com.aionemu.gameserver.services.playerbot;

import java.util.*;

import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.items.ItemMask;
import com.aionemu.gameserver.model.items.storage.StorageType;
import com.aionemu.gameserver.model.templates.item.ItemTemplate;
import com.aionemu.gameserver.model.drop.DropItem;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.DropNpc;
import com.aionemu.gameserver.world.World;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.services.drop.*;
import com.aionemu.gameserver.utils.PositionUtil;
import com.aionemu.gameserver.world.geo.GeoService;

/** Normal loot rights and inventory rules; never spends a character's money on bids. */
public final class PlayerBotLoot {
	static final float LOOT_DISTANCE=15, SIGHT_DISTANCE=100, INTERACTION_DISTANCE=3;
 static final long LOOT_DELAY=1000;
 static final double SELECT_PRIORITY=6, MOVE_PRIORITY=7, OPEN_PRIORITY=8;
 private final PlayerBotLootMemory<Npc> available=new PlayerBotLootMemory<>();
 private Npc selected,combatTarget;
 private int world,instance;
 private long nextScan;
 private volatile boolean pending;
 boolean pending(){return pending;}
 void clear(){available.clear();selected=combatTarget=null;pending=false;nextScan=0;}
 void track(Npc target,long now){
  if(target!=null && target!=combatTarget){combatTarget=target;available.add(target.getObjectId(),target,now);}
 }
 /** AttackAction remembers a newly chosen target. DropTargetAction records its corpse before noncombat.
  * Often/AddAllLoot reconciles nearby corpses; neither path grants loot rights. */
 void refresh(Player owner,Player bot,long now){
  if(world!=bot.getWorldId() || instance!=bot.getInstanceId() || bot.isDead()){
   clear();world=bot.getWorldId();instance=bot.getInstanceId();
  }
  if(bot.isDead())return;
  available.prune(now);
  if(combatTarget!=null && combatTarget.isDead()){
   available.add(combatTarget.getObjectId(),combatTarget,now);combatTarget=null;
  }
  if(now>=nextScan){
   nextScan=now+2000;
   if(java.util.concurrent.ThreadLocalRandom.current().nextInt(5)==0)bot.getKnownList().forEachNpc(npc->{
    if(npc.isDead() && npc.isSpawned() && npc.getWorldId()==world && npc.getInstanceId()==instance
     && PositionUtil.isInRange(bot,npc,SIGHT_DISTANCE,false))available.add(npc.getObjectId(),npc,now);
   });
  }
  target(owner,bot,now);pending=selected!=null || nearest(owner,bot,now)!=null;
 }
 Npc target(Player owner,Player bot,long now){
  if(selected!=null && (!possible(bot,selected) || owner!=bot && !PositionUtil.isInRange(owner,selected,LOOT_DISTANCE,false)))selected=null;
  return selected;
 }
 boolean select(Player owner,Player bot,long now){
  selected=nearest(owner,bot,now);pending=selected!=null;return selected!=null;
 }
 void discard(Npc npc){available.remove(npc.getObjectId());if(selected==npc)selected=null;pending=false;}
 private Npc nearest(Player owner,Player bot,long now){
  return available.entries(now).stream().map(PlayerBotLootMemory.Entry::value)
   .filter(npc->PositionUtil.isInRange(bot,npc,LOOT_DISTANCE,false)
    && (owner==bot || PositionUtil.isInRange(owner,npc,LOOT_DISTANCE,false)) && possible(bot,npc))
   .min(Comparator.comparingDouble((Npc npc)->PositionUtil.getDistance(bot,npc)).thenComparingInt(Npc::getObjectId)).orElse(null);
 }
 boolean possible(Player bot,Npc npc){
  return npc!=null && npc.isDead() && npc.isSpawned() && npc.getWorldId()==bot.getWorldId() && npc.getInstanceId()==bot.getInstanceId()
   && World.getInstance().findVisibleObject(npc.getObjectId())==npc && bot.getKnownList().sees(npc)
   && Math.abs(bot.getZ()-npc.getZ())<=INTERACTION_DISTANCE && !eligible(bot,npc).isEmpty();
 }
	/** Read-only native current-stage objective requirements, also used to permit
	 * human Need votes for quest items despite ordinary class/equipment limits. */
	public static boolean needsObjective(Player player, int itemId) {
		long owned = player.getInventory().getItemCountByItemId(itemId);
		for (var quest : player.getQuestStateList().getUncompletedQuests())
			if (PlayerBotQuestCraftingDriver.requirements(player, quest.getQuestId()).getOrDefault(itemId, 0L) > owned) return true;
		return false;
	}
	void passRoll(Player bot) {
		var distribution = DropDistributionService.getInstance();
		for (var roll : distribution.getPendingRolls(bot)) {
			int vote = roll.getRollType() == 2 ? vote(bot, DataManager.ITEM_DATA.getItemTemplate(roll.getItemId())) : DropDistributionService.PASS;
			distribution.handleRollOrBid(bot, roll.getRollType(), vote, 0,
				roll.getItemId(), roll.getNpcId(), roll.getIndex());
		}
	}
	/** LootRollAction/ItemUsageValue at pinned 037c014: usable upgrades Need,
	 * useful supplies/vendor items Greed, unusable/unique/full-bag items Pass.
	 * User-requested differences: required native quest objectives also Need;
	 * Greed is enabled here rather than copying WoW's default disabled option. */
	private int vote(Player bot, ItemTemplate template) {
		if (template == null || PlayerBotGearPolicy.settings(bot).rolls() == PlayerBotGearPolicy.Rolls.PASS
			|| bot.getInventory().isFull(template.getExtraInventoryId())) return DropDistributionService.PASS;
		int id = template.getTemplateId();
		if (template.hasLimitOne() && (bot.getInventory().getItemCountByItemId(id) > 0
			|| bot.getEquipment().getEquippedItems().stream().anyMatch(i -> i.getItemId() == id)
			|| bot.getStorage(StorageType.REGULAR_WAREHOUSE.getId()).getFirstItemByItemId(id) != null)) return DropDistributionService.PASS;
		long owned = bot.getInventory().getItemCountByItemId(id);
		if (needsObjective(bot, id)) return DropDistributionService.NEED;
		if (PlayerBotGearPolicy.roll(bot, template)) return DropDistributionService.NEED;
		boolean supplies = PlayerBotSupplies.available(bot, template) && owned < Math.max(1, template.getMaxStackCount());
		boolean vendor = (template.getMask() & ItemMask.SELLABLE) != 0 && template.getPrice() > 0;
		return supplies || vendor || PlayerBotGearPolicy.settings(bot).rolls() == PlayerBotGearPolicy.Rolls.ALL
			? DropDistributionService.GREED : DropDistributionService.PASS;
	}
 /** Compatibility path for companions; autonomous actors use separate select/move/open stages. */
 Npc choose(Player owner,Player bot){
  long now=System.currentTimeMillis();refresh(owner,bot,now);
  // Party companions keep their established every-decision discovery and owner leash.
  var candidates=new ArrayList<Npc>();
  bot.getKnownList().forEachNpc(npc->{if(PositionUtil.isInRange(owner,npc,LOOT_DISTANCE,false) && possible(bot,npc))candidates.add(npc);});
  return candidates.stream().min(Comparator.comparingDouble((Npc npc)->PositionUtil.getDistance(bot,npc)).thenComparingInt(Npc::getObjectId)).orElse(null);
 }
 boolean collect(Player bot,Npc npc){
  if(bot.isDead() || bot.isCasting() || !possible(bot,npc) || !PositionUtil.isInRange(bot,npc,INTERACTION_DISTANCE,false)
   || !GeoService.getInstance().canSee(bot,npc))return false;
  var registrations=DropRegistrationService.getInstance();var drop=registrations.getDropRegistrationMap().get(npc.getObjectId());
  if(drop==null)return false;
  var service=DropService.getInstance();boolean opened=false;
  if(drop.isAllowedToLoot(bot)){
   service.requestDropList(bot,npc.getObjectId());
   if(!bot.isLooting() || bot.getLootingNpcOid()!=npc.getObjectId())return false;
   opened=true;
  }
  try{
   // Opening starts native rolls. Re-read their state and only claim allowed/owned slots.
   for(var item:eligible(bot,npc)){
    if(!npc.isSpawned() || npc.getWorldId()!=bot.getWorldId() || npc.getInstanceId()!=bot.getInstanceId()
     || !PositionUtil.isInRange(bot,npc,INTERACTION_DISTANCE,false) || !GeoService.getInstance().canSee(bot,npc))break;
    if(item.isDistributeItem()){
     var winner=item.getWinningPlayer();
     if(winner!=null && winner.getObjectId()==bot.getObjectId())service.requestDropItem(bot,npc.getObjectId(),item.getIndex(),false);
    }else if(!rollRequired(drop,item,DataManager.ITEM_DATA.getItemTemplate(item.getDropTemplate().getItemId())))
     service.requestDropItem(bot,npc.getObjectId(),item.getIndex(),true);
   }
  }finally{if(opened && bot.isLooting() && bot.getLootingNpcOid()==npc.getObjectId())service.closeDropList(bot,npc.getObjectId());}
  available.remove(npc.getObjectId());if(selected==npc)selected=null;pending=false;
  return true;
 }
 private List<DropItem> eligible(Player bot,Npc npc){
  var registrations=DropRegistrationService.getInstance();var drop=registrations.getDropRegistrationMap().get(npc.getObjectId());
  var items=registrations.getCurrentDropMap().get(npc.getObjectId());
  if(drop==null || items==null || drop.isBeingLooted() && drop.getLootingPlayer()!=bot)return List.of();
  List<DropItem> snapshot;synchronized(items){snapshot=List.copyOf(items);}
  var result=new ArrayList<DropItem>();
  for(var item:snapshot){
   var template=DataManager.ITEM_DATA.getItemTemplate(item.getDropTemplate().getItemId());if(template==null)continue;
   var winner=item.isDistributeItem() ? item.getWinningPlayer() : null;
   var rules=drop.getLootGroupRules();boolean queued=!item.isFreeForAll() && rules!=null && rules.containDropItem(item);
   var slot=new PlayerBotLootPolicy.Slot(item.canViewDropItem(bot.getObjectId()),item.getCount(),drop.isAllowedToLoot(bot),
    item.isDistributeItem(),winner!=null && winner.getObjectId()==bot.getObjectId(),queued,rollRequired(drop,item,template),
    allowedItem(bot,template),canStore(bot,template,item.getCount()));
   if(PlayerBotLootPolicy.work(slot)!=PlayerBotLootPolicy.Work.NONE)result.add(item);
  }
  return result;
 }
 /** Read-only counterpart of native canDistribute; invoking it during planning would start a roll. */
 static boolean rollRequired(DropNpc drop,DropItem item,ItemTemplate template){
  var rules=drop.getLootGroupRules();return !item.isQuestDrop() && template!=null && rules!=null && PlayerBotLootPolicy.rollRequired(template.isKinah(),item.isFreeForAll(),
   item.isDistributeItem(),drop.getInRangePlayers().size(),rules.getAutodistributionId(),rules.getQualityRule(template.getItemQuality()));
 }
 private static boolean allowedItem(Player bot,ItemTemplate template){
  if(template.isKinah())return true;
  if(template.hasLimitOne() && (bot.getInventory().getItemCountByItemId(template.getTemplateId())>0
   || bot.getEquipment().getEquippedItems().stream().anyMatch(i->i.getItemId()==template.getTemplateId())
   || bot.getStorage(StorageType.REGULAR_WAREHOUSE.getId()).getFirstItemByItemId(template.getTemplateId())!=null))return false;
  // Aion ItemUsage: quest/usable equipment/supplies/materials and ordinary vendor goods.
  return template.getPrice()>0 || template.getActions()!=null && template.getActions().getItemActions().stream().anyMatch(a->a instanceof com.aionemu.gameserver.model.templates.item.actions.QuestStartAction)
   || needsObjective(bot,template.getTemplateId()) || PlayerBotCare.worldQuestItem(bot,template.getTemplateId())
   || PlayerBotGearPolicy.roll(bot,template) || PlayerBotSupplies.available(bot,template)
   || Set.of("QUEST","RECIPE","MANASTONE","SPECIAL_MANASTONE","ENCHANTMENT","FLUX","BALIC_MATERIAL","RAWHIDE","SOULSTONE","GATHERABLE","GATHERABLE_BONUS","DROP_MATERIAL","COINS","MEDALS").contains(template.getItemGroup().name());
 }
 static boolean canStore(Player bot,ItemTemplate template,long count){
  var inventory=bot.getInventory();var stacks=inventory.getItemsByItemId(template.getTemplateId());
  return PlayerBotLootPolicy.canStore(template.isKinah(),inventory.isFull(template.getExtraInventoryId()),bot.isWorldBot(),
   template.getExtraInventoryId()>0,inventory.getLimit(),inventory.getFreeSlots(),template.isStackable(),template.getMaxStackCount(),
   stacks.stream().map(i->i.getItemCount()).toList(),count);
 }
}
