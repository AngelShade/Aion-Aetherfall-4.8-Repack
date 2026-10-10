package com.aionemu.gameserver.services.playerbot;

import java.sql.*;
import java.util.*;
import com.aionemu.gameserver.configs.main.LegionConfig;
import com.aionemu.gameserver.dao.InventoryDAO;
import com.aionemu.gameserver.model.gameobjects.*;
import com.aionemu.gameserver.model.gameobjects.Persistable.PersistentState;
import com.aionemu.gameserver.model.items.*;
import com.aionemu.gameserver.model.items.storage.*;
import com.aionemu.gameserver.model.team.legion.*;
import sun.misc.Unsafe;

/** Real production planning/custody and native storage projection with synthetic JDBC, no World/DB/IDs. */
public final class PlayerBotLegionStorageCheck {
 static int checks;
 static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 static class Actor extends PlayerBotWarehouseCheck.Actor {
  @Override public Storage getStorage(int location){return location==3 && getLegion()!=null?new LegionStorageProxy(getLegion().getLegionWarehouse(),this):location==0?cube:location==1?warehouse:null;}
 }
 static Actor actor(int id)throws Exception {
  Actor a=(Actor)PlayerBotTradeCheck.unsafe.allocateInstance(Actor.class);a.id=id;a.companion=id!=1;a.owner=1;
  var equipment=com.aionemu.gameserver.model.gameobjects.player.Player.class.getDeclaredField("equipment");equipment.setAccessible(true);equipment.set(a,new com.aionemu.gameserver.model.gameobjects.player.Equipment(a));
  a.cube=new PlayerStorage(null,StorageType.CUBE);a.account=PlayerBotTradeCheck.actor(1,false).account;a.tasks=new PlayerBotTradeCheck.Tasks();return a;
 }
 static Item item(int id,boolean allowed) {
  return new Item(id,new com.aionemu.gameserver.model.templates.item.ItemTemplate(){@Override public int getTemplateId(){return 164000001;}},17,false,0){@Override public int getItemMask(){return allowed?ItemMask.STORABLE_IN_LWH:0;}};
 }
 static class Jdbc extends PlayerBotWarehouseCheck.Jdbc {
  Legion legion;boolean missingLegion,badPermissions,badMember,badRank;List<long[]> shared=new ArrayList<>();
  Jdbc(Legion l){legion=l;}
  @Override PreparedStatement statement(String sql) {
   if(!sql.startsWith("SELECT deputy_permission") && !sql.startsWith("SELECT legion_id") && !sql.startsWith("SELECT item_unique_id,item_id,item_count"))return super.statement(sql);
   return PlayerBotTradeCheck.proxy(PreparedStatement.class,(p,m,args)->{
    if(m.getName().equals("executeQuery")) {
     boolean permissions=sql.startsWith("SELECT deputy_permission"),member=sql.startsWith("SELECT legion_id");
     List<long[]> data=permissions?(missingLegion?List.of():List.of(new long[]{legion.getDeputyPermission()+(badPermissions?1:0),legion.getCenturionPermission(),legion.getLegionaryPermission(),legion.getVolunteerPermission(),legion.getDisbandTime()}))
      :member?List.of(new long[]{legion.getLegionId()+(badMember?1:0)}):shared;int[] index={-1};
     return PlayerBotTradeCheck.proxy(ResultSet.class,(r,rm,ra)->switch(rm.getName()) {
      case "next" -> ++index[0]<data.size();
      case "getInt" -> (int)data.get(index[0])[(Integer)ra[0]-1];
      case "getLong" -> data.get(index[0])[(Integer)ra[0]-1];
      case "getBoolean" -> data.get(index[0])[(Integer)ra[0]-1]!=0;
      case "getString" -> badRank?"VOLUNTEER":"BRIGADE_GENERAL";
      default -> PlayerBotTradeCheck.zero(rm.getReturnType());
     });
    }
    return PlayerBotTradeCheck.zero(m.getReturnType());
   });
  }
 }
 static void refusal(Jdbc db,PlayerBotLegionStorage.Deposit request,String reason)throws Exception {
  try {PlayerBotLegionStorageStore.transfer(db.connection(),request);throw new AssertionError("Accepted "+reason);}
  catch(SQLException e){check(e.getMessage().contains(reason),"Expected custody refusal: "+e);}
  check(db.writes.isEmpty(),"Refusal cannot transfer custody");
 }
 static void accessRefusal(Jdbc db,PlayerBotLegionStorage.Deposit request,String reason)throws Exception {
  try {PlayerBotLegionStorageStore.access(db.connection(),request);throw new AssertionError("Accepted "+reason);}
  catch(SQLException e){check(e.getMessage().contains(reason),"Expected persisted access refusal: "+e);}
 }
 public static void main(String[] args)throws Exception {
  var f=Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);PlayerBotTradeCheck.unsafe=(Unsafe)f.get(null);LegionConfig.LEGION_WAREHOUSE=true;
  var owner=actor(1);var bot=actor(2);bot.account=owner.account;var legion=new Legion(700,"Offline legion");
  for(var a:List.of(owner,bot)){var member=new LegionMember(a.id,legion);member.setRank(LegionRank.BRIGADE_GENERAL);a.setLegionMember(member);}
  var session=PlayerBotWarehouseCheck.session(owner,bot);
  check(PlayerBotLegionStorage.membership(owner,bot),"Owner and companion share actual legion and rights");
  bot.getLegionMember().setRank(LegionRank.VOLUNTEER);check(!PlayerBotLegionStorage.membership(owner,bot),"Native volunteer deposit rights refused");bot.getLegionMember().setRank(LegionRank.BRIGADE_GENERAL);
  owner.getLegionMember().setRank(LegionRank.VOLUNTEER);check(!PlayerBotLegionStorage.membership(owner,bot),"Owner cannot delegate absent warehouse rights");owner.getLegionMember().setRank(LegionRank.BRIGADE_GENERAL);
  var foreign=new LegionMember(2,new Legion(701,"Foreign"));foreign.setRank(LegionRank.BRIGADE_GENERAL);var original=bot.getLegionMember();bot.setLegionMember(foreign);
  check(!PlayerBotLegionStorage.membership(owner,bot),"Foreign legion refused");bot.setLegionMember(original);
  var stack=item(501,true);stack.setEquipmentSlot(3);stack.setEnchantLevel(8);stack.setPackCount(2);stack.setPersistentState(PersistentState.UPDATED);bot.cube.onLoadHandler(stack);
  check(PlayerBotLegionStorage.eligible(stack),"Native legion storage mask allows stack");check(!PlayerBotLegionStorage.eligible(item(502,false)),"No legion mask refused");
  check(!PlayerBotLegionStorage.eligible(null),"No phantom item");stack.setEquipped(true);check(!PlayerBotLegionStorage.eligible(stack),"Equipped item refused");stack.setEquipped(false);
  check(!PlayerBotLegionStorage.eligible(PlayerBotWarehouseCheck.item(503,182400001,50,true)),"Kinah excluded from item deposit");
  var wh=legion.getLegionWarehouse();check(wh.setInUse(1),"Human native lease acquired");check(!wh.setInUse(2),"Companion cannot steal native lease");check(!wh.unsetInUse(2),"Foreign close cannot release lease");wh.unsetInUse(1);wh.setInUse(2);
  var slotItem=item(550,true);slotItem.setItemLocation(3);slotItem.setEquipmentSlot(0);wh.onLoadHandler(slotItem);
  check(PlayerBotLegionStorage.plan(bot,501).slot()==1,"Existing shared slot retained");wh.remove(slotItem);
  wh.updateLimit(2);check(wh.getLimit()==40,"Native legion expansion capacity respected");wh.updateLimit(0);
  for(int slot=0;slot<wh.getLimit();slot++){var filler=item(600+slot,true);filler.setItemLocation(3);filler.setEquipmentSlot(slot);wh.onLoadHandler(filler);}
  try{PlayerBotLegionStorage.plan(bot,501);throw new AssertionError("accepted full warehouse");}catch(IllegalArgumentException e){check(e.getMessage().contains("full"),"Full shared storage refuses deposit before SQL");}
  for(Item i:new ArrayList<>(wh.getItems()))wh.remove(i);
  var move=PlayerBotLegionStorage.plan(bot,501);var request=new PlayerBotLegionStorage.Deposit(session,legion,move);
  var db=new Jdbc(legion);db.own(bot,stack);refusal(db,request,"custody lock");
  synchronized(LegionWarehouse.CUSTODY_LOCK) {
   var checkpointDb=new Jdbc(legion);var pendingShared=item(560,true);pendingShared.setItemLocation(3);wh.onLoadHandler(pendingShared);pendingShared.setPersistentState(PersistentState.NEW);
   var checkpoint=InventoryDAO.storeCompanionLegionInventory(checkpointDb.connection(),legion);
   check(checkpoint.equals(List.of(pendingShared)) && pendingShared.getPersistentState()==PersistentState.NEW,"Shared checkpoint stages pending insert without publication");
   check(checkpointDb.writes.size()==1 && checkpointDb.writes.getFirst().contains("9=700") && checkpointDb.writes.getFirst().contains("13=3"),"Shared pending insertion keeps native legion owner and location");
   wh.remove(pendingShared);checkpointDb=new Jdbc(legion);wh.onLoadHandler(pendingShared);pendingShared.setPersistentState(PersistentState.UPDATED);pendingShared.setPersistentState(PersistentState.UPDATE_REQUIRED);
   try{InventoryDAO.storeCompanionLegionInventory(checkpointDb.connection(),legion);throw new AssertionError("missing shared checkpoint");}catch(SQLException e){check(e.getMessage().contains("row missing"),"Dirty existing shared item cannot recreate absent row");}
   checkpointDb.rows.put(560,new long[]{999,164000001,0});
   try{InventoryDAO.storeCompanionLegionInventory(checkpointDb.connection(),legion);throw new AssertionError("foreign shared checkpoint");}catch(SQLException e){check(e.getMessage().contains("custody mismatch"),"Pending native transfers await authoritative shared custody");}
   check(checkpointDb.writes.isEmpty(),"Uncommitted foreign shared custody cannot overwrite its row");wh.remove(pendingShared);
      PlayerBotLegionStorageStore.access(db.connection(),request);check(true,"Both persisted member ranks and legion permissions locked");
   for(int fault=0;fault<4;fault++){var bad=new Jdbc(legion);bad.missingLegion=fault==0;bad.badPermissions=fault==1;bad.badMember=fault==2;bad.badRank=fault==3;accessRefusal(bad,request,fault<2?"permissions":"membership");}
   PlayerBotLegionStorageStore.transfer(db.connection(),request);check(db.writes.size()==1 && db.writes.getFirst().contains("item_owner=?,item_location=3,slot=?"),"Only native custody/location/slot transferred");
   check(db.writes.getFirst().contains("1=700") && db.writes.getFirst().contains("3=501") && db.writes.getFirst().contains("4=2") && db.writes.getFirst().contains("6=17"),"Exact legion, object, character and whole count guarded");
   check(stack.getItemLocation()==0 && bot.cube.getItemByObjId(501)==stack && wh.getItemByObjId(501)==null,"SQL phase does not publish native transfer");
   for(int column=0;column<6;column++){var bad=new Jdbc(legion);bad.own(bot,stack);bad.rows.get(501)[column]++;refusal(bad,request,"custody/count/slot");}
   var missing=new Jdbc(legion);refusal(missing,request,"row missing");
   var extra=new Jdbc(legion);extra.own(bot,stack);extra.shared.add(new long[]{999,164000001,17,0,1});refusal(extra,request,"image mismatch");
   var stored=item(504,true);stored.setEquipmentSlot(0);stored.setItemLocation(3);stored.setPersistentState(PersistentState.UPDATED);wh.onLoadHandler(stored);
   var absent=new Jdbc(legion);absent.own(bot,stack);refusal(absent,request,"rows missing");
   var occupied=new Jdbc(legion);occupied.own(bot,stack);occupied.shared.add(new long[]{504,164000001,17,0,0});refusal(occupied,request,"slot changed");
   for(int column=1;column<5;column++){var badImage=new Jdbc(legion);badImage.own(bot,stack);long[] row={504,164000001,17,0,0};row[column]++;badImage.shared.add(row);refusal(badImage,request,"image mismatch");}
   wh.remove(stored);
   for(int slot:List.of(-1,wh.getLimit())){var invalid=new PlayerBotLegionStorage.Deposit(session,legion,new PlayerBotWarehouse.Move(bot,stack,bot.cube,bot.getStorage(3),(short)slot,17));var bad=new Jdbc(legion);bad.own(bot,stack);refusal(bad,invalid,"exact native");}
   for(int location:List.of(1,2,32,125)){var invalid=new PlayerBotLegionStorage.Deposit(session,legion,new PlayerBotWarehouse.Move(bot,stack,bot.cube,new PlayerStorage(null,StorageType.getStorageTypeById(location)),(short)0,17));var bad=new Jdbc(legion);bad.own(bot,stack);refusal(bad,invalid,"exact native");}
   bot.world=true;var bad=new Jdbc(legion);bad.own(bot,stack);refusal(bad,request,"exact native");bot.world=false;
   bot.owner=999;refusal(bad,request,"exact native");bot.owner=1;
   wh.unsetInUse(2);refusal(bad,request,"exact native");wh.setInUse(2);
   var zero=new Jdbc(legion);zero.own(bot,stack);zero.updates=0;try{PlayerBotLegionStorageStore.transfer(zero.connection(),request);throw new AssertionError("zero update");}catch(SQLException e){check(e.getMessage().contains("source changed"),"Zero affected rows cannot announce success");}
   var committed=new Jdbc(legion);committed.own(bot,stack);
   PlayerBotWarehouseStore.transaction(committed.connection(),()->{committed.events.add("write");PlayerBotLegionStorageStore.transfer(committed.connection(),request);},()->{
    committed.events.add("publish");bot.cube.remove(stack);stack.setEquipmentSlot(move.slot());move.destination().add(stack);PlayerBotWarehouse.verifyProjection(move);
   });
   check(committed.events.equals(List.of("write","commit","publish")),"Shared projection follows acknowledged SQL commit");
   check(wh.getItemByObjId(501)==stack && bot.cube.getItemByObjId(501)==null && stack.getItemCount()==17 && stack.getEnchantLevel()==8 && stack.getPackCount()==2,"Native proxy preserves stack/object/enchant/pack in legion storage");
   PlayerBotWarehouse.verifyProjection(move);check(true,"Exact post-projection custody verified");
   wh.setPersistentState(PersistentState.UPDATE_REQUIRED);bot.cube.setPersistentState(PersistentState.UPDATE_REQUIRED);
   check(bot.getDirtyItemsToUpdate().stream().noneMatch(i->i.getItemLocation()==3),"Native companion checkpoint excludes donated/shared objects");
   for(int failure=0;failure<4;failure++) {
    var fault=new Jdbc(legion);fault.commitFails=failure==1;fault.rollbackFails=failure==2;boolean[] published={false};int kind=failure;
    try{PlayerBotWarehouseStore.transaction(fault.connection(),()->{if(kind==0 || kind==2)throw new SQLException("write fault");},()->{published[0]=true;if(kind==3)throw new IllegalStateException("projection fault");});throw new AssertionError("accepted fault");}
    catch(PlayerBotWarehouseStore.Held e){check(failure!=0,"Unknown commit/rollback/projection outcomes held");}
    catch(SQLException e){check(failure==0,"Known write rollback remains ordinary failure");}
    check(published[0]==(failure==3),"Precommit failure cannot publish");
   }
   var latch=new java.util.concurrent.CountDownLatch(1);var entered=new java.util.concurrent.atomic.AtomicBoolean();
   Thread worker=new Thread(()->{latch.countDown();bot.getStorage(3).remove(stack);entered.set(true);},"offline-legion-lock-check");
   worker.start();check(latch.await(2,java.util.concurrent.TimeUnit.SECONDS),"Native proxy worker started");
   check(!entered.get(),"Native proxy mutation cannot cross in-progress custody monitor");
   // A hold established before releasing the monitor rejects the waiting mutation.
   worker.setUncaughtExceptionHandler((t,e)->{if(!(e instanceof IllegalStateException))throw new AssertionError(e);});
   LegionWarehouse.holdCustody(700);check(LegionWarehouse.custodyHeld(700),"Ambiguous shared state quarantined");
   check(!InventoryDAO.store(List.of(stack),null,null,700),"Quarantined shared state refuses native persistence without SQL");
   com.aionemu.gameserver.services.item.ItemMoveService.moveItem(bot,501,(byte)3,(byte)0,(short)0);
   check(wh.getItemByObjId(501)==stack,"Native human/companion move routing refuses quarantined shared state");
   com.aionemu.gameserver.services.item.ItemSplitService.splitItem(bot,501,0,1,(short)1,(byte)3,(byte)0);
   check(wh.getItemByObjId(501)==stack && stack.getItemCount()==17,"Native split refuses held storage before ID allocation");
   check(!wh.unsetInUse(2) && !wh.setInUse(1),"Custody hold retains exclusive lease");
   try{bot.getStorage(3).remove(stack);throw new AssertionError("held proxy remove");}catch(IllegalStateException e){check(true,"Direct shared proxy mutation refuses hold");}
   try{wh.increaseKinah(10);throw new AssertionError("held siege money");}catch(IllegalStateException e){check(true,"Siege reward cannot alter held inventory");}
  }
  System.out.println("OK: "+checks+" legion deposit rights, exact SQL custody, native proxy and failure-boundary checks; no live World/DB/ID operations.");
 }
}
