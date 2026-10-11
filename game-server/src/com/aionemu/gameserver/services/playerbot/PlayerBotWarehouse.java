/* BankAction::Execute/ExecuteBank/Deposit/Withdraw/ListItems and InventoryAction,
 * mod-playerbots 037c01418b5d01506917a3db9b44fd56ac5f965c. GPL-2.0-or-later;
 * third-party/playerbots/AUTHORS.md. Native Aion character storage adapter. */
package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import com.aionemu.gameserver.GameServer;
import com.aionemu.gameserver.model.DialogAction;
import com.aionemu.gameserver.model.TaskId;
import com.aionemu.gameserver.model.gameobjects.Item;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.items.storage.IStorage;
import com.aionemu.gameserver.model.items.storage.StorageType;
import com.aionemu.gameserver.services.DialogService;
import com.aionemu.gameserver.services.item.ItemMoveService;
import com.aionemu.gameserver.services.item.ItemRestrictionService;
import com.aionemu.gameserver.utils.PositionUtil;
import com.aionemu.gameserver.world.geo.GeoService;
import com.aionemu.gameserver.dao.InventoryDAO;
import org.slf4j.LoggerFactory;

/** Master-requested private banking. One exact whole stack; no shared storage, gifts or identity allocation. */
public final class PlayerBotWarehouse {
 record Move(Player bot, Item item, IStorage source, IStorage destination, short slot, long count) {}

 static boolean privateOwner(PlayerBotSession session) {
  return !session.world() && session.owner()!=session.bot() && session.bot().isPlayerBot()
   && session.bot().getPlayerBotOwnerId()==session.owner().getObjectId()
   && session.bot().getAccount().getId()==session.owner().getAccount().getId();
 }
 static boolean idle(Player player) {
  return !player.isDead() && !player.isCasting() && !player.isLooting() && !player.isTrading()
   && !player.isFlying() && !player.isInAnyHide() && !player.getController().isInCombat()
   && !player.getController().hasScheduledTask(TaskId.ITEM_USE);
 }
 static boolean banker(Player owner, Player bot, Npc npc) {
  return !npc.isDead() && npc.isSpawned() && npc.getWorldId()==bot.getWorldId() && npc.getInstanceId()==bot.getInstanceId()
   && npc.getObjectTemplate().canInteract() && npc.getObjectTemplate().supportsAction(DialogAction.DEPOSIT_CHAR_WAREHOUSE)
   && !bot.isEnemy(npc) && !owner.isEnemy(npc) && bot.canSee(npc) && owner.canSee(npc)
   && PositionUtil.isInTalkRange(bot,npc) && PositionUtil.isInTalkRange(owner,npc)
   && DialogService.isInteractionAllowed(bot,npc) && DialogService.isInteractionAllowed(owner,npc)
   && GeoService.getInstance().canSee(bot,npc) && GeoService.getInstance().canSee(owner,npc);
 }
 static String blocker(PlayerBotSession session) {
  if(!privateOwner(session))return "Private warehouse access belongs to your own companion; invited world bots keep their own items.";
  if(session.closing())return "The companion is waiting for dismissal or custody verification.";
  Player owner=session.owner(), bot=session.bot();
  if(GameServer.isShuttingDownSoon())return "Warehouse access is unavailable during shutdown.";
  if(!owner.isOnline() || !owner.isSpawned() || !bot.isSpawned() || !bot.isPlaying())return "Recruit this companion before using its warehouse.";
  if(!idle(owner) || !idle(bot))return "Use the warehouse while both characters are alive, idle, on the ground and outside combat, casting, trade, looting and item use.";
  if(owner.getWorldId()!=bot.getWorldId() || owner.getInstanceId()!=bot.getInstanceId() || owner.getRace()!=bot.getRace()
   || !PlayerBotTeams.same(owner,bot))return "Your companion must be in your current party or alliance on the same map.";
  if(bot.getStorage(StorageType.REGULAR_WAREHOUSE.getId())==null)return "The companion's private warehouse has not loaded.";
  boolean[] found={false};bot.getKnownList().forEachNpc(n->{if(!found[0] && banker(owner,bot,n))found[0]=true;});
  return found[0]?"":"Bring both characters within talking distance of the same accessible warehouse keeper.";
 }
 static boolean bankable(Item item) {
  return item!=null && item.getObjectId()>0 && item.getItemCount()>0 && !item.isEquipped()
   && !item.getItemTemplate().isKinah() && item.isStorableInWarehouse();
 }
 static short freeSlot(IStorage storage) {
  return freeSlot(storage,null);
 }
 static int slotLimit(IStorage storage,Item incoming) {
  return storage.getStorageType()==StorageType.CUBE && incoming!=null && incoming.getItemTemplate().getExtraInventoryId()>0
   ? storage.getStorageType().getSpecialLimit():storage.getLimit();
 }
 static boolean sameSlots(IStorage storage,Item first,Item second) {
  return storage.getStorageType()!=StorageType.CUBE || (first.getItemTemplate().getExtraInventoryId()>0)==(second!=null && second.getItemTemplate().getExtraInventoryId()>0);
 }
 static short freeSlot(IStorage storage,Item incoming) {
  if(storage==null || storage.isFull())throw new IllegalArgumentException("The destination storage is full.");
  if(storage.getStorageType()==StorageType.CUBE && incoming!=null && incoming.getItemTemplate().getExtraInventoryId()>0
   && storage instanceof com.aionemu.gameserver.model.items.storage.Storage nativeStorage && nativeStorage.isFullSpecialCube())
   throw new IllegalArgumentException("The destination special cube is full.");
  Set<Long> occupied=new HashSet<>();for(Item item:storage.getItems())if(sameSlots(storage,item,incoming))occupied.add(item.getEquipmentSlot());
  for(int slot=0;slot<Math.min(slotLimit(storage,incoming),Short.MAX_VALUE+1);slot++)if(!occupied.contains((long)slot))return (short)slot;
  throw new IllegalArgumentException("The destination storage has no free native slot.");
 }
 static Move plan(Player bot, boolean deposit, int object) {
  IStorage source=bot.getStorage(deposit?0:1), destination=bot.getStorage(deposit?1:0);
  if(source==null || destination==null)throw new IllegalArgumentException("The companion's private storage has not loaded.");
  Item item=source.getItemByObjId(object);
  if(item==null || item.getObjectId()<=0 || item.getItemCount()<=0 || item.isEquipped() || item.getItemTemplate().isKinah()
   || item.getItemLocation()!=source.getStorageType().getId())throw new IllegalArgumentException("Choose an unequipped item object from this companion's "+(deposit?"cube.":"private warehouse."));
  if(ItemRestrictionService.isItemRestrictedFrom(bot,item,source.getStorageType())
   || ItemRestrictionService.isItemRestrictedTo(bot,item,destination.getStorageType()))throw new IllegalArgumentException("Native item restrictions prevent this warehouse move.");
  return new Move(bot,item,source,destination,freeSlot(destination,item),item.getItemCount());
 }
 static void project(Move move) {
  ItemMoveService.moveItem(move.bot(),move.item().getObjectId(),(byte)move.source().getStorageType().getId(),
   (byte)move.destination().getStorageType().getId(),move.slot());
  verifyProjection(move);
  // Only the native slot/location changed. Leave subsequent quest callback changes dirty.
 }
 static void verifyProjection(Move move) {
  if(move.source().getItemByObjId(move.item().getObjectId())!=null
   || move.destination().getItemByObjId(move.item().getObjectId())!=move.item()
   || move.item().getItemCount()!=move.count() || move.item().getItemLocation()!=move.destination().getStorageType().getId()
   || move.item().getEquipmentSlot()!=move.slot() || move.item().isEquipped())
   throw new IllegalStateException("Committed warehouse move could not be projected exactly");
 }
 /** Mirrors native exchange lock order; a request moves one stack, never a population batch. */
 public static String move(Player owner,String name,boolean deposit,int object) {
  var service=PlayerBotService.getInstance();
  {
   var session=service.find(owner,name);
   synchronized(session) {
    if(!service.current(session))throw new IllegalArgumentException("This companion has left; refresh before retrying.");
   String reason=blocker(session);if(!reason.isEmpty())throw new IllegalArgumentException(reason);
    Move move=plan(session.bot(),deposit,object);session.bot().getMoveController().abortMove();
    try {PlayerBotWarehouseStore.commit(move);}
    catch(PlayerBotWarehouseStore.Held error) {
     InventoryDAO.quarantineMarketInventory(session.bot().getObjectId());session.markClosing();
     LoggerFactory.getLogger(PlayerBotWarehouse.class).error("Warehouse custody held for bot {} item {}; all identities retained",session.bot().getObjectId(),object,error);
     throw new IllegalStateException("Warehouse persistence needs custody verification. This companion is held; see the server log.",error);
    }catch(java.sql.SQLException error){throw new IllegalStateException("Warehouse move was rolled back; the item remains in its original storage.",error);}
    return session.bot().getName()+": "+(deposit?"stored ":"withdrew ")+move.item().getItemTemplate().getName()+" x"+move.count()+" (obj="+object+").";
   }
  }
 }
 public static List<String> list(Player owner,String name,int page) {
  var service=PlayerBotService.getInstance();{var session=service.find(owner,name);synchronized(session) {
   if(!service.current(session))throw new IllegalArgumentException("This companion has left; refresh before retrying.");
   String reason=blocker(session);if(!reason.isEmpty())throw new IllegalArgumentException(reason);
   var storage=session.bot().getStorage(1);var items=sorted(storage);int pages=Math.max(1,(items.size()+19)/20);
   if(page<1 || page>pages)throw new IllegalArgumentException("Warehouse page must be 1.."+pages+".");
   var lines=new ArrayList<String>();lines.add(session.bot().getName()+" private warehouse: "+storage.size()+"/"+storage.getLimit()+" slots; page "+page+"/"+pages);
   items.stream().skip((page-1L)*20).limit(20).forEach(i->lines.add("obj="+i.getObjectId()+" item="+i.getItemId()+" x"+i.getItemCount()+" "+i.getItemTemplate().getName()));return List.copyOf(lines);
  }}
 }
 private static List<Item> sorted(IStorage storage){return storage.getItems().stream().sorted(Comparator.comparingInt(Item::getObjectId)).toList();}
 /** Read-only private roster view; writes and chat lists recheck the banker at request time. No SQL/persistence. */
 static void snapshot(PlayerBotSession session,Map<String,Object> state) {
  boolean own=privateOwner(session);IStorage storage=own?session.bot().getStorage(1):null;String reason=blocker(session);
  state.put("warehouseAvailable",reason.isEmpty());state.put("warehouseReason",reason);
  state.put("warehouseLimit",storage==null?0:storage.getLimit());
  state.put("warehouse",storage==null?List.of():sorted(storage).stream().map(PlayerBotItemView::stored).toList());
 }
 private PlayerBotWarehouse(){}
}
