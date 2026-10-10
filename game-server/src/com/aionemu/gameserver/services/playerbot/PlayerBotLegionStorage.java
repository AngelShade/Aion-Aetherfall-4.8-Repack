/* GuildBankAction::Execute/MoveFromCharToBank, mod-playerbots
 * 037c01418b5d01506917a3db9b44fd56ac5f965c. GPL-2.0-or-later;
 * third-party/playerbots/AUTHORS.md. Native Aion legion deposit adapter. */
package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import com.aionemu.gameserver.GameServer;
import com.aionemu.gameserver.configs.main.LegionConfig;
import com.aionemu.gameserver.dao.InventoryDAO;
import com.aionemu.gameserver.model.DialogAction;
import com.aionemu.gameserver.model.gameobjects.*;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.items.storage.*;
import com.aionemu.gameserver.model.team.legion.*;
import com.aionemu.gameserver.services.DialogService;
import com.aionemu.gameserver.services.item.ItemRestrictionService;
import com.aionemu.gameserver.utils.PositionUtil;
import com.aionemu.gameserver.world.geo.GeoService;

/** Requested whole-stack deposits only, matching the pinned action. No automatic donations. */
public final class PlayerBotLegionStorage {
 record Deposit(PlayerBotSession session, Legion legion, PlayerBotWarehouse.Move move) {}
 static boolean rights(Player player) {
  return player.isLegionMember() && (player.getLegionMember().hasRights(LegionPermissionsMask.WH_DEPOSIT)
   || player.getLegionMember().hasRights(LegionPermissionsMask.WH_WITHDRAWAL));
 }
 static boolean membership(Player owner,Player bot) {
  return owner.getLegion()!=null && bot.getLegion()==owner.getLegion() && rights(owner) && rights(bot);
 }
 static boolean keeper(Player owner,Player bot,Npc npc) {
  return !npc.isDead() && npc.isSpawned() && npc.getWorldId()==bot.getWorldId() && npc.getInstanceId()==bot.getInstanceId()
   && npc.getObjectTemplate().canInteract() && npc.getObjectTemplate().supportsAction(DialogAction.OPEN_LEGION_WAREHOUSE)
   && !owner.isEnemy(npc) && !bot.isEnemy(npc) && owner.canSee(npc) && bot.canSee(npc)
   && PositionUtil.isInTalkRange(owner,npc) && PositionUtil.isInTalkRange(bot,npc)
   && DialogService.isInteractionAllowed(owner,npc) && DialogService.isInteractionAllowed(bot,npc)
   && GeoService.getInstance().canSee(owner,npc) && GeoService.getInstance().canSee(bot,npc);
 }
 static String blocker(PlayerBotSession session) {
  if(!PlayerBotWarehouse.privateOwner(session))return "Legion deposits require your own companion; invited world bots retain custody.";
  if(session.closing())return "This companion is waiting for dismissal or custody verification.";
  if(GameServer.isShuttingDownSoon())return "Legion storage is unavailable during shutdown.";
  if(!com.aionemu.gameserver.configs.main.PlayerBotConfig.ENABLED)return "Companions are disabled.";
  if(!LegionConfig.LEGION_WAREHOUSE)return "Native legion warehouses are disabled.";
  Player owner=session.owner(),bot=session.bot();
  if(!membership(owner,bot))return "Both characters must belong to the same legion and have native warehouse deposit access.";
  Legion legion=bot.getLegion();
  if(legion.isDisbanding())return "The legion is disbanding.";
  if(LegionWarehouse.custodyHeld(legion.getLegionId()))return "Legion storage is held for custody verification.";
  if(!owner.isOnline() || !owner.isSpawned() || !bot.isSpawned() || !bot.isPlaying())return "Recruit this companion before using legion storage.";
  if(!PlayerBotWarehouse.idle(owner) || !PlayerBotWarehouse.idle(bot))return "Both characters must be alive, idle and on the ground, outside combat, trade, casting, looting and item use.";
  if(owner.getWorldId()!=bot.getWorldId() || owner.getInstanceId()!=bot.getInstanceId() || owner.getRace()!=bot.getRace()
   || !PlayerBotTeams.same(owner,bot))return "Your companion must share your party or alliance and map.";
  int user=legion.getLegionWarehouse().getCurrentUser();
  if(user!=0 && user!=bot.getObjectId())return "The legion warehouse is in use. Close its native window before depositing.";
  boolean[] found={false};bot.getKnownList().forEachNpc(n->{if(!found[0] && keeper(owner,bot,n))found[0]=true;});
  return found[0]?"":"Bring both characters within talking distance of the same accessible legion warehouse keeper.";
 }
 static boolean eligible(Item item) {
  return item!=null && item.getObjectId()>0 && item.getItemCount()>0 && !item.isEquipped()
   && !item.getItemTemplate().isKinah() && item.isStorableInLegWarehouse();
 }
 static PlayerBotWarehouse.Move plan(Player bot,int object) {
  var cube=bot.getInventory();var destination=bot.getStorage(3);Item item=cube.getItemByObjId(object);
  if(destination==null || !eligible(item) || item.getItemLocation()!=0)throw new IllegalArgumentException("Choose an eligible unequipped whole stack from this companion's cube.");
  if(ItemRestrictionService.isItemRestrictedTo(bot,item,StorageType.LEGION_WAREHOUSE))throw new IllegalArgumentException("Native legion item or rank restrictions prevent this deposit.");
  return new PlayerBotWarehouse.Move(bot,item,cube,destination,PlayerBotWarehouse.freeSlot(destination,item),item.getItemCount());
 }
 public static String deposit(Player owner,String name,int object) {
  var service=PlayerBotService.getInstance();{var session=service.find(owner,name);synchronized(session) {
   synchronized(LegionWarehouse.CUSTODY_LOCK) {
    if(!service.current(session))throw new IllegalArgumentException("This companion has left; refresh before retrying.");
   String reason=blocker(session);if(!reason.isEmpty())throw new IllegalArgumentException(reason);
    var legion=session.bot().getLegion();var warehouse=legion.getLegionWarehouse();
    if(!warehouse.setInUse(session.bot().getObjectId()))throw new IllegalArgumentException("The legion warehouse is in use.");
    boolean held=false;
    try {
     var request=new Deposit(session,legion,plan(session.bot(),object));session.bot().getMoveController().abortMove();
     try {PlayerBotLegionStorageStore.commit(request);}
     catch(PlayerBotWarehouseStore.Held error) {
      held=true;LegionWarehouse.holdCustody(legion.getLegionId());InventoryDAO.quarantineMarketInventory(session.bot().getObjectId());session.markClosing();
      org.slf4j.LoggerFactory.getLogger(PlayerBotLegionStorage.class).error("Legion {} / companion {} / item {} custody held; identities retained",legion.getLegionId(),session.bot().getObjectId(),object,error);
      throw new IllegalStateException("Legion deposit needs custody verification. The companion and legion storage are held; see the server log.",error);
     }catch(java.sql.SQLException error){throw new IllegalStateException("Legion deposit rolled back; the stack remains in its original custody.",error);}
     return session.bot().getName()+": deposited "+request.move().item().getItemTemplate().getName()+" x"+request.move().count()+" (obj="+object+") into "+legion.getName()+" legion storage.";
    } finally {if(!held)warehouse.unsetInUse(session.bot().getObjectId());}
   }
  }}
 }
 public static List<String> list(Player owner,String name,int page) {
  var service=PlayerBotService.getInstance();{var session=service.find(owner,name);synchronized(session) {
   synchronized(LegionWarehouse.CUSTODY_LOCK) {
    if(!service.current(session))throw new IllegalArgumentException("This companion has left; refresh before retrying.");
   String reason=blocker(session);if(!reason.isEmpty())throw new IllegalArgumentException(reason);
    var warehouse=session.bot().getLegion().getLegionWarehouse();var items=sorted(warehouse);int pages=Math.max(1,(items.size()+19)/20);
    if(page<1 || page>pages)throw new IllegalArgumentException("Legion storage page must be 1.."+pages+".");
    var lines=new ArrayList<String>();lines.add(session.bot().getLegion().getName()+" legion storage: "+warehouse.size()+"/"+warehouse.getLimit()+"; page "+page+"/"+pages);
    items.stream().skip((page-1L)*20).limit(20).forEach(i->lines.add("obj="+i.getObjectId()+" item="+i.getItemId()+" x"+i.getItemCount()+" "+i.getItemTemplate().getName()));return List.copyOf(lines);
   }
  }}
 }
 private static List<Item> sorted(IStorage storage){return storage.getItems().stream().sorted(Comparator.comparingInt(Item::getObjectId)).toList();}
 static void snapshot(PlayerBotSession session,Map<String,Object> state) {
  synchronized(LegionWarehouse.CUSTODY_LOCK) {
   String reason=blocker(session);var legion=PlayerBotWarehouse.privateOwner(session) && membership(session.owner(),session.bot())?session.bot().getLegion():null;
   state.put("legionStorageAvailable",reason.isEmpty());state.put("legionStorageReason",reason);state.put("legionStorageName",legion==null?"":legion.getName());
   state.put("legionStorageLimit",legion==null?0:legion.getLegionWarehouse().getLimit());
   state.put("legionStorage",legion==null || LegionWarehouse.custodyHeld(legion.getLegionId())?List.of():sorted(legion.getLegionWarehouse()).stream()
    .map(i->Map.of("id",i.getObjectId(),"itemId",i.getItemId(),"name",i.getItemTemplate().getName(),"count",i.getItemCount())).toList());
  }
 }
 private PlayerBotLegionStorage(){}
}
