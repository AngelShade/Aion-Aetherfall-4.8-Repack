package com.aionemu.gameserver.services.playerbot;

import java.sql.*;
import java.util.*;
import com.aionemu.gameserver.model.gameobjects.Item;
import com.aionemu.gameserver.model.gameobjects.Persistable.PersistentState;
import com.aionemu.gameserver.model.items.ItemMask;
import com.aionemu.gameserver.model.items.storage.*;
import com.aionemu.gameserver.model.templates.item.ItemTemplate;
import sun.misc.Unsafe;

/** Production custody SQL/commit boundaries and native whole-stack projection on world-free actors/JDBC. */
public final class PlayerBotWarehouseCheck {
 static int checks;
 static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 static class Actor extends PlayerBotTradeCheck.Actor {
  PlayerStorage warehouse;boolean world;
  Actor(){super();}
  @Override public Storage getStorage(int location){return location==0?cube:location==1?warehouse:null;}
  @Override public boolean isWorldBot(){return world;}
  @Override public PlayerStorage getWarehouse(){return warehouse;}
  @Override public int getNpcExpands(){return 0;}
  @Override public int getQuestExpands(){return 0;}
  @Override public int getItemExpands(){return 0;}
  @Override public int getWhNpcExpands(){return 0;}
  @Override public int getWhBonusExpands(){return 0;}
 }
 static Actor actor(int id)throws Exception {
  var a=(Actor)PlayerBotTradeCheck.unsafe.allocateInstance(Actor.class);a.id=id;a.companion=true;a.owner=1;
  a.cube=new PlayerStorage(null,StorageType.CUBE);a.warehouse=new PlayerStorage(null,StorageType.REGULAR_WAREHOUSE);
  a.account=PlayerBotTradeCheck.actor(1,false).account;a.tasks=new PlayerBotTradeCheck.Tasks();return a;
 }
 static Item item(int object,int template,long count,boolean bankable){
  return item(object,template,count,bankable,-1);
 }
 static Item item(int object,int template,long count,boolean bankable,int extra){
  var item=new Item(object,new ItemTemplate(){@Override public int getTemplateId(){return template;}@Override public boolean isKinah(){return template==182400001;}@Override public int getExtraInventoryId(){return extra;}},count,false,0){@Override public int getItemMask(){return bankable?ItemMask.STORABLE_IN_WH:0;}};
  item.setEquipmentSlot(3);item.setItemLocation(0);item.setPersistentState(PersistentState.UPDATED);return item;
 }
 static class Jdbc extends PlayerBotTradeCheck.Jdbc {
  boolean occupied,commitFails,rollbackFails;Item destinationItem;List<String> events=new ArrayList<>();
  @Override Connection connection(){return PlayerBotTradeCheck.proxy(Connection.class,(p,m,args)->{
   return switch(m.getName()) {
    case "getAutoCommit" -> autoCommit;
    case "setAutoCommit" -> {autoCommit=(Boolean)args[0];yield null;}
    case "prepareStatement" -> statement((String)args[0]);
    case "commit" -> {events.add("commit");if(commitFails)throw new SQLException("synthetic commit");yield null;}
    case "rollback" -> {events.add("rollback");if(rollbackFails)throw new SQLException("synthetic rollback");yield null;}
    default -> PlayerBotTradeCheck.zero(m.getReturnType());
   };
  });}
  @Override PreparedStatement statement(String sql) {
   if(!sql.startsWith("SELECT item_unique_id"))return super.statement(sql);
   return PlayerBotTradeCheck.proxy(PreparedStatement.class,(p,m,args)->{
    if(m.getName().equals("executeQuery")){boolean[] used={false};return PlayerBotTradeCheck.proxy(ResultSet.class,(r,rm,ra)->{
     if(rm.getName().equals("next")){boolean found=(occupied || destinationItem!=null) && !used[0];used[0]=true;return found;}
     if(rm.getName().equals("getInt") && destinationItem!=null)return (Integer)ra[0]==1?destinationItem.getObjectId():destinationItem.getItemId();
     return PlayerBotTradeCheck.zero(rm.getReturnType());});}
    return PlayerBotTradeCheck.zero(m.getReturnType());
   });
  }
  void own(Actor bot,Item item){rows.put(item.getObjectId(),new long[]{bot.id,item.getItemId(),item.getItemLocation(),item.getItemCount(),item.isEquipped()?1:0,item.getEquipmentSlot()});}
 }
 static void refuses(Runnable task,String fragment){try{task.run();throw new AssertionError("Accepted "+fragment);}catch(IllegalArgumentException error){check(error.getMessage().contains(fragment),"Exact native planning refusal: "+error);}}
 static void sqlRefuses(Jdbc db,PlayerBotWarehouse.Move move,String fragment)throws Exception {
  try{PlayerBotWarehouseStore.transfer(db.connection(),move);throw new AssertionError("Accepted "+fragment);}
  catch(SQLException error){check(error.getMessage().contains(fragment),"Exact SQL refusal: "+error);}
  check(db.writes.isEmpty(),"Refused custody has no writes");
 }
 static PlayerBotSession session(PlayerBotTradeCheck.Actor owner,Actor bot)throws Exception {
  var s=(PlayerBotSession)PlayerBotTradeCheck.unsafe.allocateInstance(PlayerBotSession.class);
  for(var entry:Map.of("owner",owner,"bot",bot).entrySet()){var f=PlayerBotSession.class.getDeclaredField(entry.getKey());f.setAccessible(true);f.set(s,entry.getValue());}return s;
 }
 public static void main(String[] args)throws Exception {
  var unsafe=Unsafe.class.getDeclaredField("theUnsafe");unsafe.setAccessible(true);PlayerBotTradeCheck.unsafe=(Unsafe)unsafe.get(null);
  var bot=actor(2);var item=item(501,112000001,17,true);item.setEnchantLevel(9);item.setPackCount(4);item.setPersistentState(PersistentState.UPDATED);bot.cube.onLoadHandler(item);
  check(PlayerBotWarehouse.bankable(item),"Native private-bank mask allows an ordinary whole stack");
  check(!PlayerBotWarehouse.bankable(item(502,112000002,1,false)),"Native no-warehouse mask remains rejected");
  check(!PlayerBotWarehouse.bankable(item(503,182400001,10,true)),"Kinah is never an item deposit");
  check(!PlayerBotWarehouse.bankable(null),"No phantom item");
  item.setEquipped(true);check(!PlayerBotWarehouse.bankable(item),"Equipped item cannot be banked");refuses(()->PlayerBotWarehouse.plan(bot,true,501),"unequipped");item.setEquipped(false);
  refuses(()->PlayerBotWarehouse.plan(bot,true,999),"unequipped");
  var restricted=item(504,112000003,1,false);bot.cube.onLoadHandler(restricted);refuses(()->PlayerBotWarehouse.plan(bot,true,504),"Native item restrictions");
  check(PlayerBotWarehouse.freeSlot(bot.warehouse)==0,"Empty warehouse starts at native slot zero");
  var occupied=item(505,112000004,1,true);occupied.setItemLocation(1);occupied.setEquipmentSlot(0);bot.warehouse.onLoadHandler(occupied);
  check(PlayerBotWarehouse.freeSlot(bot.warehouse)==1,"Existing slot cannot be overwritten");
  for(int slot=1;slot<bot.warehouse.getLimit();slot++){var entry=item(600+slot,112000005,1,true);entry.setEquipmentSlot(slot);entry.setItemLocation(1);bot.warehouse.onLoadHandler(entry);}
  refuses(()->PlayerBotWarehouse.plan(bot,true,501),"full");bot.warehouse=new PlayerStorage(null,StorageType.REGULAR_WAREHOUSE);
  for(boolean deposit:List.of(true,false)) {
   var move=PlayerBotWarehouse.plan(bot,deposit,501);var db=new Jdbc();db.own(bot,item);
   PlayerBotWarehouseStore.transfer(db.connection(),move);
   check(db.writes.size()==1 && db.writes.getFirst().startsWith("UPDATE inventory SET item_location=?,slot=?"),"Only slot/location written in either direction");
   check(db.writes.getFirst().contains("3=501") && db.writes.getFirst().contains("4=2") && db.writes.getFirst().contains("6=17"),"Exact object, character custody and whole stack guarded");
   check(item.getItemLocation()==(deposit?0:1) && item.getItemCount()==17 && item.getEnchantLevel()==9 && item.getPackCount()==4,"SQL phase publishes no native mutation or metadata replacement");
   for(int column=0;column<6;column++){db=new Jdbc();db.own(bot,item);db.rows.get(501)[column]++;sqlRefuses(db,move,"custody/count/slot");}
   db=new Jdbc();sqlRefuses(db,move,"row missing");
   db=new Jdbc();db.own(bot,item);db.occupied=true;sqlRefuses(db,move,"destination slot");
   db=new Jdbc();db.own(bot,item);db.autoCommit=true;sqlRefuses(db,move,"exact private");
   db=new Jdbc();db.own(bot,item);db.updates=0;try{PlayerBotWarehouseStore.transfer(db.connection(),move);throw new AssertionError("zero row update");}catch(SQLException error){check(error.getMessage().contains("source changed"),"Concurrent source change refuses success");}
   item.setPersistentState(PersistentState.NEW);db=new Jdbc();PlayerBotWarehouseStore.source(db.connection(),move,true);check(db.writes.isEmpty(),"Fresh item is permitted only for transactional native checkpoint");
   item.setPersistentState(PersistentState.UPDATED);db=new Jdbc();db.own(bot,item);db.rows.get(501)[3]++;
   try{PlayerBotWarehouseStore.source(db.connection(),move,true);throw new AssertionError("clean row mismatch");}catch(SQLException expected){check(true,"Clean checkpoint cannot overwrite stale count");}
   item.setPersistentState(PersistentState.UPDATE_REQUIRED);PlayerBotWarehouseStore.source(db.connection(),move,true);check(db.writes.isEmpty(),"Pending native count is accepted without publication before checkpoint");
   db.rows.get(501)[4]=1;PlayerBotWarehouseStore.source(db.connection(),move,true);
   check(db.writes.isEmpty(),"An item just unequipped natively may checkpoint its old equipped flag before the exact transfer guard");
   item.setPersistentState(PersistentState.UPDATED);
   db=new Jdbc();db.own(bot,item);var transactionDb=db;
   PlayerBotWarehouseStore.transaction(db.connection(),()->{transactionDb.events.add("write");PlayerBotWarehouseStore.transfer(transactionDb.connection(),move);},()->{transactionDb.events.add("publish");PlayerBotWarehouse.project(move);});
   check(db.events.equals(List.of("write","commit","publish")),"Native publication follows acknowledged custody commit");
   check(move.destination().getItemByObjId(501)==item && move.source().getItemByObjId(501)==null,"Actual native ItemMoveService projects same identity into correct storage");
   check(item.getItemCount()==17 && item.getEnchantLevel()==9 && item.getPackCount()==4,"Native whole-stack roundtrip keeps count/enchant/pack metadata");
   PlayerBotWarehouse.verifyProjection(move);check(true,"Actual projected slot/location verified");
  }
  check(bot.cube.getItemByObjId(501)==item && bot.warehouse.getItemByObjId(501)==null,"Deposit and withdrawal return original object; no split/merge/new ID");
  var storageBot=actor(22);
  for(int slot=0;slot<storageBot.cube.getLimit();slot++){var special=item(2000+slot,182200001,1,true,1);special.setEquipmentSlot(slot);storageBot.cube.onLoadHandler(special);}
  check(PlayerBotWarehouse.freeSlot(storageBot.cube)==0,"Special cube occupied slots do not consume ordinary cube slots");
  var normal=item(2100,112000001,1,true);normal.setItemLocation(1);normal.setEquipmentSlot(1);storageBot.warehouse.onLoadHandler(normal);
  var normalMove=PlayerBotWarehouse.plan(storageBot,false,2100);var separate=new Jdbc();separate.own(storageBot,normal);separate.destinationItem=storageBot.cube.getItemByObjId(2000);
  PlayerBotWarehouseStore.transfer(separate.connection(),normalMove);check(separate.writes.size()==1,"SQL allows native independent special/ordinary slot families");
  PlayerBotWarehouse.project(normalMove);check(storageBot.cube.getItemByObjId(2000)!=null && storageBot.cube.getItemByObjId(2100)==normal,"Native ordinary withdrawal preserves special item at the same numeric slot");
  var extra=item(2200,182200001,1,true,1);extra.setItemLocation(1);storageBot.warehouse.onLoadHandler(extra);
  var extraMove=PlayerBotWarehouse.plan(storageBot,false,2200);
  check(extraMove.slot()==27 && PlayerBotWarehouse.slotLimit(storageBot.cube,extra)==102,"Special withdrawal uses the full native special-cube capacity");
  separate=new Jdbc();separate.own(storageBot,extra);PlayerBotWarehouseStore.transfer(separate.connection(),extraMove);PlayerBotWarehouse.project(extraMove);
  check(storageBot.cube.getItemByObjId(2200)==extra && extra.getEquipmentSlot()==27,"Native special-cube withdrawal retains exact object and slot");
  for(int slot=28;slot<102;slot++){var special=item(2300+slot,182200001,1,true,1);special.setEquipmentSlot(slot);storageBot.cube.onLoadHandler(special);}
  var overflow=item(2500,182200001,1,true,1);overflow.setItemLocation(1);storageBot.warehouse.onLoadHandler(overflow);
  refuses(()->PlayerBotWarehouse.plan(storageBot,false,2500),"special cube is full");
  for(int location:List.of(2,3,125,32,60)) {
   var shared=new PlayerStorage(null,StorageType.getStorageTypeById(location));var invalid=new PlayerBotWarehouse.Move(bot,item,bot.cube,shared,(short)0,17);
   var db=new Jdbc();db.own(bot,item);sqlRefuses(db,invalid,"exact private");
  }
  for(int slot:List.of(-1,bot.warehouse.getLimit())){var db=new Jdbc();db.own(bot,item);sqlRefuses(db,new PlayerBotWarehouse.Move(bot,item,bot.cube,bot.warehouse,(short)slot,17),"exact private");}
  bot.world=true;var db=new Jdbc();db.own(bot,item);sqlRefuses(db,PlayerBotWarehouse.plan(bot,true,501),"exact private");bot.world=false;
  bot.companion=false;db=new Jdbc();db.own(bot,item);sqlRefuses(db,PlayerBotWarehouse.plan(bot,true,501),"exact private");bot.companion=true;
  var owner=PlayerBotTradeCheck.actor(1,false);var s=session(owner,bot);check(PlayerBotWarehouse.privateOwner(s),"Own alt/Temporary Bot custody accepted");
  bot.owner=999;check(!PlayerBotWarehouse.privateOwner(s),"Foreign controller rejected");bot.owner=1;
  bot.world=true;check(!PlayerBotWarehouse.privateOwner(s) && PlayerBotWarehouse.blocker(s).contains("world bots"),"Invitation does not grant private warehouse custody");
  var snapshot=new HashMap<String,Object>();PlayerBotWarehouse.snapshot(s,snapshot);check(((List<?>)snapshot.get("warehouse")).isEmpty() && !(Boolean)snapshot.get("warehouseAvailable"),"World bot private inventory is not leaked to invited master");bot.world=false;
  snapshot.clear();bot.warehouse.onLoadHandler(item(1001,112000001,1,true));PlayerBotWarehouse.snapshot(s,snapshot);
  check(((List<?>)snapshot.get("warehouse")).size()==1 && !(Boolean)snapshot.get("warehouseAvailable"),"Read-only owned roster view works outside banker range; action remains blocked");
  check(PlayerBotWarehouse.blocker(s).contains("Recruit"),"Disconnected master cannot request a write");
  for(int failure=0;failure<5;failure++) {
   var jdbc=new Jdbc();jdbc.commitFails=failure==2;jdbc.rollbackFails=failure==3;final int step=failure;
   try {PlayerBotWarehouseStore.transaction(jdbc.connection(),()->{jdbc.events.add("write");if(step==0 || step==3)throw new SQLException("synthetic write");if(step==1)throw new IllegalArgumentException("synthetic runtime");},()->{jdbc.events.add("publish");if(step==4)throw new IllegalStateException("synthetic projection");});throw new AssertionError("Failure accepted");}
   catch(PlayerBotWarehouseStore.Held held){check(failure>=2,"Only uncertain commit/rollback/projection is held");}
   catch(SQLException|IllegalArgumentException error){check(failure<2,"Known pre-commit failure remains a rollback");}
   check(jdbc.events.contains("publish")== (failure==4),"No publication for write/commit/rollback failure");
   check(jdbc.events.contains("rollback")== (failure!=4),"Acknowledged commit is never rolled back after projection failure");
  }
  System.out.println("OK: "+checks+" warehouse native projection, private custody, capacity, metadata and transaction-boundary checks; no World/DB/IDs");
 }
}
