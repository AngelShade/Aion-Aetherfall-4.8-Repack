package com.aionemu.gameserver.services.item;

import java.lang.reflect.*;
import java.nio.*;
import java.nio.channels.*;
import java.sql.*;
import java.util.*;
import javax.sql.DataSource;
import sun.misc.Unsafe;
import com.aionemu.commons.database.DatabaseFactory;
import com.aionemu.commons.network.AConnection;
import com.aionemu.gameserver.configs.main.CustomConfig;
import com.aionemu.gameserver.configs.network.NetworkConfig;
import com.aionemu.gameserver.dao.InventoryDAO;
import com.aionemu.gameserver.dataholders.*;
import com.aionemu.gameserver.model.gameobjects.*;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.items.ItemMask;
import com.aionemu.gameserver.model.items.storage.*;
import com.aionemu.gameserver.model.templates.item.ItemTemplate;
import com.aionemu.gameserver.network.aion.*;
import com.aionemu.gameserver.network.aion.clientpackets.*;
import com.aionemu.gameserver.network.aion.serverpackets.*;
import com.aionemu.gameserver.services.WarehouseService;

/** Actual native CM read/run paths, storage custody, save bindings and corrective packets. No live SQL/world. */
public final class WarehouseDepositCheck {
 static Unsafe unsafe; static int checks, next=10000;
 static final Map<Integer,long[]> rows=new HashMap<>();
 static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 static Field field(Class<?> c,String n)throws Exception{var f=c.getDeclaredField(n);f.setAccessible(true);return f;}
 static class Actor extends Player {
  PlayerStorage cube,warehouse,account; PlayerCommon common; AionConnection client;
  Actor(){super(null,null);}
  @Override public Storage getInventory(){return cube;}
  @Override public Storage getWarehouse(){return warehouse;}
  @Override public Storage getStorage(int id){return id==0?cube:id==1?warehouse:id==2?account:null;}
  @Override public int getWarehouseExpansions(){return common.expands;}
  @Override public int getWhNpcExpands(){return common.expands;}
  @Override public int getWhBonusExpands(){return 0;}
  @Override public int getNpcExpands(){return 0;}
  @Override public int getQuestExpands(){return 0;}
  @Override public int getItemExpands(){return 0;}
  @Override public boolean isOnline(){return true;}
  @Override public boolean isTrading(){return false;}
  @Override public AionConnection getClientConnection(){return client;}
  @Override public com.aionemu.gameserver.model.team.legion.Legion getLegion(){return null;}
 }
 static class PlayerCommon {int expands;}
 static class Bank extends PlayerStorage {
  boolean reject,explode,afterAdd;
  Bank(StorageType t){super(null,t);}
  @Override public Item add(Item item){if(explode)throw new IllegalStateException("synthetic destination failure");if(reject)return null;var added=super.add(item);if(afterAdd)throw new IllegalStateException("synthetic failure after publication");return added;}
 }
 static class Key extends SelectionKey {
  Selector selector; Key(Selector s){selector=s;}
  public SelectableChannel channel(){return null;} public Selector selector(){return selector;}
  public boolean isValid(){return true;} public void cancel(){} public int interestOps(){return 0;}
  public SelectionKey interestOps(int ops){return this;} public int readyOps(){return 0;}
 }
 static Actor actor(int expansions,Selector selector)throws Exception {
  var a=(Actor)unsafe.allocateInstance(Actor.class);a.common=new PlayerCommon();a.common.expands=expansions;
  a.cube=new PlayerStorage(a,StorageType.CUBE);a.cube.setLimit(288);
  a.warehouse=new Bank(StorageType.REGULAR_WAREHOUSE);a.setWarehouseLimit();
  a.account=new Bank(StorageType.ACCOUNT_WAREHOUSE);a.account.setLimit(CustomConfig.EXPANDED_ACCOUNT_WAREHOUSE_SLOTS);a.warehouse.setOwner(a);a.account.setOwner(a);
  a.client=(AionConnection)unsafe.allocateInstance(AionConnection.class);
  field(AionConnection.class,"sendMsgQueue").set(a.client,new ArrayDeque<AionServerPacket>());
  field(AionConnection.class,"activePlayer").set(a.client,new java.util.concurrent.atomic.AtomicReference<Player>(a));
  field(AionConnection.class,"state").set(a.client,AionConnection.State.IN_GAME);
  field(AConnection.class,"guard").set(a.client,new Object());field(AConnection.class,"key").set(a.client,new Key(selector));return a;
 }
 @SuppressWarnings("unchecked") static Deque<AionServerPacket> packets(Actor a)throws Exception{return (Deque<AionServerPacket>)field(AionConnection.class,"sendMsgQueue").get(a.client);}
 static Item item(int template,long count,int location,long slot){var i=new Item(next++,DataManager.ITEM_DATA.getItemTemplate(template),count,false,slot);i.setItemLocation(location);return i;}
 static void load(Storage s,Item i){s.onLoadHandler(i);}
 static void move(Actor a,Item i,int dest,short slot)throws Exception {
  var p=new CM_MOVE_ITEM(0,Set.of(AionConnection.State.IN_GAME));p.setConnection(a.client);
  p.setBuffer(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putInt(i.getObjectId()).put((byte)i.getItemLocation()).put((byte)dest).putShort(slot).flip());
  check(p.read(),"Real CM_MOVE_ITEM bytes read");p.run();
 }
 static void split(Actor a,Item i,int dest,long amount,short slot)throws Exception {
  var p=new CM_SPLIT_ITEM(0,Set.of(AionConnection.State.IN_GAME));p.setConnection(a.client);
  p.setBuffer(ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN).putInt(i.getObjectId()).putLong(amount).put((byte)i.getItemLocation()).putInt(0).put((byte)dest).putShort(slot).flip());
  check(p.read(),"Real CM_SPLIT_ITEM bytes read");p.run();
 }
 static byte[] body(AionServerPacket p)throws Exception {
  var buf=ByteBuffer.allocate(8192).order(ByteOrder.LITTLE_ENDIAN);
  var base=com.aionemu.commons.network.packet.BaseServerPacket.class;
  var set=base.getDeclaredMethod("setBuf",ByteBuffer.class);set.setAccessible(true);set.invoke(p,buf);
  var write=p.getClass().getDeclaredMethod("writeImpl",AionConnection.class);write.setAccessible(true);write.invoke(p,(Object)null);
  return Arrays.copyOf(buf.array(),buf.position());
 }
 static void retained(Actor a,Item item,long count,long slot)throws Exception {
  check(a.cube.getItemByObjId(item.getObjectId())==item && item.getItemCount()==count && item.getItemLocation()==0 && item.getEquipmentSlot()==slot,"Failed deposit retains original source identity/count/location/slot");
  check(a.warehouse.getItemByObjId(item.getObjectId())==null && a.account.getItemByObjId(item.getObjectId())==null,"Refused original is absent from both destinations");
  if(item.getItemId()==164000001)check(a.warehouse.getItemCountByItemId(item.getItemId())+a.account.getItemCountByItemId(item.getItemId())==0,"Refused split leaves no destination quantity");
  var ps=packets(a);check(ps.stream().anyMatch(p->p instanceof SM_SYSTEM_MESSAGE),"Full warehouse message queued");
  int deletion=-1,addition=-1,n=0;
  for(var p:ps){if(p instanceof SM_DELETE_ITEM)deletion=n;if(p instanceof SM_INVENTORY_ADD_ITEM && field(SM_INVENTORY_ADD_ITEM.class,"addType").get(p)==ItemPacketService.ItemAddType.PARTIAL_WITH_SLOT)addition=n;n++;}
  check(deletion>=0 && addition>deletion,"Corrective delete precedes source re-add");
  check(InventoryDAO.store(item,1,2,null),"Native DAO saves retained source");
  check(Arrays.equals(rows.get(item.getObjectId()),new long[]{count,0,slot,1}),"Saved row still belongs to source with original count/slot");
 }
 static Object zero(Class<?> c){if(!c.isPrimitive())return null;if(c==boolean.class)return false;if(c==long.class)return 0L;if(c==int.class)return 0;return null;}
 @SuppressWarnings("unchecked") static <T>T proxy(Class<T> c,InvocationHandler h){return (T)Proxy.newProxyInstance(c.getClassLoader(),new Class[]{c},h);}
 static void jdbc()throws Exception {
  // Only in-memory JDBC: verify real InventoryDAO bind positions and commit, never connect to any database.
  var ds=proxy(DataSource.class,(p,m,a)->m.getName().equals("getConnection")?proxy(Connection.class,(c,cm,ca)->{
   if(cm.getName().equals("getAutoCommit"))return true;
   if(!cm.getName().equals("prepareStatement"))return zero(cm.getReturnType());
   String sql=(String)ca[0];Map<Integer,Object> values=new HashMap<>();List<Map<Integer,Object>> batches=new ArrayList<>();
   return proxy(PreparedStatement.class,(s,sm,sa)->{
    if(sm.getName().startsWith("set") && sa!=null && sa.length>=2){values.put((Integer)sa[0],sa[1]);return null;}
    if(sm.getName().equals("executeQuery"))return proxy(ResultSet.class,(r,rm,ra)->zero(rm.getReturnType()));
    if(sm.getName().equals("addBatch")){batches.add(new HashMap<>(values));return null;}
    if(sm.getName().equals("executeBatch")){for(var v:batches){if(sql.startsWith("UPDATE inventory"))rows.put(((Number)v.get(28)).intValue(),new long[]{((Number)v.get(1)).longValue(),((Number)v.get(11)).longValue(),((Number)v.get(10)).longValue(),((Number)v.get(7)).longValue()});else if(sql.startsWith("INSERT"))rows.put(((Number)v.get(1)).intValue(),new long[]{((Number)v.get(3)).longValue(),((Number)v.get(13)).longValue(),((Number)v.get(12)).longValue(),((Number)v.get(9)).longValue()});}int[] result=new int[batches.size()];Arrays.fill(result,1);return result;}
    return zero(sm.getReturnType());
   });
  }):zero(m.getReturnType()));field(DatabaseFactory.class,"dataSource").set(null,ds);
 }
 static void templates()throws Exception {
  var data=new ItemData();
  @SuppressWarnings("unchecked") var map=(Map<Integer,ItemTemplate>)field(ItemData.class,"items").get(data);
  for(int id:new int[]{100000001,164000001}){var t=new ItemTemplate();field(ItemTemplate.class,"itemId").setInt(t,id);field(ItemTemplate.class,"maxStackCount").setInt(t,id==164000001?1000:1);field(ItemTemplate.class,"mask").setInt(t,ItemMask.STORABLE_IN_WH|ItemMask.STORABLE_IN_AWH);map.put(id,t);}DataManager.ITEM_DATA=data;
 }
 static void scratchSql(Selector selector,String url,boolean before)throws Exception {
  if(!url.matches("jdbc:mysql://127\\.0\\.0\\.1:33907/warehouse_deposit_[a-zA-Z0-9_]+\\?useSSL=false"))throw new IllegalArgumentException("Scratch 33907 warehouse schema only");
  com.aionemu.gameserver.utils.idfactory.IDFactory.getInstance(); // initialize on the in-memory JDBC fixture
  var source=proxy(DataSource.class,(p,m,a)->m.getName().equals("getConnection")?DriverManager.getConnection(url,"root",""):zero(m.getReturnType()));
  field(DatabaseFactory.class,"dataSource").set(null,source);
  for(int expansions:new int[]{0,6,11})for(int dest:new int[]{1,2}) {
   CustomConfig.EXPANDED_WAREHOUSES=true;var a=actor(expansions,selector);var bank=(Bank)a.getStorage(dest);
   for(int n=0;n<72;n++)load(bank,item(100000001,1,dest,n));
   var whole=item(100000001,1,0,4);load(a.cube,whole);check(InventoryDAO.store(whole,1,2,null),"SQL source insert");move(a,whole,dest,(short)(dest==1?359:539));
   check(InventoryDAO.store(whole,1,2,null),"SQL high-slot update");
   var loaded=new PlayerStorage(null,bank.getStorageType());InventoryDAO.loadStorage(dest==1?1:2,loaded);
   var reloaded=loaded.getItemByObjId(whole.getObjectId());check(reloaded!=null && reloaded.getEquipmentSlot()==(dest==1?359:539) && reloaded.getItemCount()==1,"Real MariaDB high-slot survives native load");
   var stack=item(164000001,23,0,5);load(a.cube,stack);check(InventoryDAO.store(stack,1,2,null),"SQL stack insert");split(a,stack,dest,7,(short)73);
   var created=bank.getItems().stream().filter(i->i.getItemId()==164000001).findFirst().orElseThrow();
   check(InventoryDAO.store(List.of(stack,created),1,2,null),"SQL split source and target saved together");
   loaded=new PlayerStorage(null,bank.getStorageType());InventoryDAO.loadStorage(dest==1?1:2,loaded);
   reloaded=loaded.getItemByObjId(created.getObjectId());check(reloaded!=null && reloaded.getItemCount()==7 && stack.getItemCount()==16,"Real MariaDB split quantity survives load");
   check(reloaded.getEquipmentSlot()==(before?65535:73),"SQL split slot is native sentinel before, explicit slot after");
   bank.reject=true;var refused=item(164000001,23,0,6);load(a.cube,refused);check(InventoryDAO.store(refused,1,2,null),"SQL refused source inserted before request");split(a,refused,dest,7,(short)74);
   check(InventoryDAO.store(refused,1,2,null),"SQL refused source saved after request");
   var cube=new PlayerStorage(null,StorageType.CUBE);InventoryDAO.loadStorage(1,cube);
   check(cube.getItemByObjId(refused.getObjectId()).getItemCount()==(before?16:23),"Real MariaDB confirms old loss / repaired source preservation");
   System.out.println("SQL "+(before?"BEFORE":"AFTER")+": credits="+expansions+" storage="+dest+" high-slot persisted, split slot="+reloaded.getEquipmentSlot()+", rejected source count="+cube.getItemByObjId(refused.getObjectId()).getItemCount());
  }
 }
 static void reproduceBefore(Selector selector)throws Exception {
  for(int dest:new int[]{1,2}) {
   CustomConfig.EXPANDED_WAREHOUSES=true;var a=actor(6,selector);var bank=(Bank)a.getStorage(dest);
   for(int n=0;n<72;n++)load(bank,item(100000001,1,dest,n));
   var whole=item(100000001,1,0,4);load(a.cube,whole);move(a,whole,dest,(short)72);
   check(bank.getItemByObjId(whole.getObjectId())==whole && whole.getItemLocation()==dest,"Before fix: high-slot drag is retained in server memory");
   check(InventoryDAO.store(whole,1,2,null) && rows.get(whole.getObjectId())[2]==72,"Before fix: high-slot item is retained by native save bindings");
   var stack=item(164000001,23,0,5);load(a.cube,stack);split(a,stack,dest,7,(short)73);
   var created=bank.getItems().stream().filter(i->i.getItemId()==164000001).findFirst().orElseThrow();
   check(stack.getItemCount()==16 && created.getItemCount()==7,"Before fix: ordinary split preserves server quantity");
   check(InventoryDAO.store(created,1,2,null) && rows.get(created.getObjectId())[0]==7,"Before fix: hidden split stack can still be saved");
   System.out.println("BEFORE: storage="+dest+" whole slot="+whole.getEquipmentSlot()+" split slot="+created.getEquipmentSlot()+" memory and DAO row retained");
   bank.reject=true;var refused=item(164000001,23,0,6);load(a.cube,refused);split(a,refused,dest,7,(short)74);
   check(refused.getItemCount()==16,"Before fix: failed destination add permanently consumes split quantity");
   System.out.println("BEFORE: rejected split 23 -> "+refused.getItemCount()+", no destination stack (7 lost)");
  }
 }
 public static void main(String[] args)throws Exception {
  var packetErrors=new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();packetErrors.start();
  ((ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(AionClientPacket.class)).addAppender(packetErrors);
  unsafe=(Unsafe)field(Unsafe.class,"theUnsafe").get(null);jdbc();templates();
  NetworkConfig.MAX_PENDING_SERVER_PACKETS=10000;NetworkConfig.MAX_SEND_STALL_SECONDS=60;
  NetworkConfig.PACKET_PROCESSOR_MIN_THREADS=1;NetworkConfig.PACKET_PROCESSOR_MAX_THREADS=1;
  NetworkConfig.PACKET_PROCESSOR_THREAD_SPAWN_THRESHOLD=1;NetworkConfig.PACKET_PROCESSOR_THREAD_KILL_THRESHOLD=1;NetworkConfig.PACKET_PROCESSOR_MAX_PENDING_PACKETS_PER_CONNECTION=10000;
  try(Selector selector=Selector.open()) {
   if(args.length>0 && args[0].startsWith("--scratch-"))scratchSql(selector,args[1],args[0].equals("--scratch-before"));
   else if(args.length>0)reproduceBefore(selector);else {
   for(boolean oldSetting:new boolean[]{true,false})for(int expansions:new int[]{0,6,11}) {
    CustomConfig.EXPANDED_WAREHOUSES=oldSetting;var a=actor(expansions,selector);
    check(com.aionemu.gameserver.services.AccountService.loadAccountWarehouse(new com.aionemu.gameserver.model.account.Account(2)).getLimit()==540,"Account loading ignores preserved legacy flag");
    check(a.warehouse.getLimit()==360 && a.account.getLimit()==540,"Capacity independent of earned expansions and preserved legacy flag");
    byte[] bytes=body(SM_CUBE_UPDATE.cubeSize(StorageType.REGULAR_WAREHOUSE,a));check((bytes[6]&255)==42 && bytes[7]==0 && bytes[8]==0,"SM_CUBE_UPDATE wire expansions 2a 00 00");
    WarehouseService.sendWarehouseInfo(a,true);var info=packets(a).stream().filter(p->p instanceof SM_WAREHOUSE_INFO).findFirst().orElseThrow();check((body(info)[2]&255)==42,"SM_WAREHOUSE_INFO wire capacity 2a");packets(a).clear();
    for(int dest:new int[]{1,2})for(boolean stack:new boolean[]{false,true})for(short requested:new short[]{-1,72,(short)(dest==1?359:539)}){
     a=actor(expansions,selector);var bank=a.getStorage(dest);for(int n=0;n<72;n++)load(bank,item(100000001,1,dest,n));
     var i=item(stack?164000001:100000001,stack?23:1,0,4);load(a.cube,i);move(a,i,dest,requested);
     check(a.cube.getItemByObjId(i.getObjectId())==null && bank.getItemByObjId(i.getObjectId())==i,"Native deposit passes the 72 wall");
     check(i.getEquipmentSlot()==(requested==-1?72:requested),"Destination receives deterministic legal slot");
     check(InventoryDAO.store(i,1,2,null),"Native DAO stores deposited item");check(rows.get(i.getObjectId())[1]==dest && rows.get(i.getObjectId())[2]==i.getEquipmentSlot(),"Saved warehouse location and high slot are retained");
    }
   }
   for(int dest:new int[]{1,2})for(int mode=0;mode<6;mode++)for(boolean stack:new boolean[]{false,true}) {
    var a=actor(6,selector);var bank=(Bank)a.getStorage(dest);
    if(mode==0)for(int n=0;n<bank.getLimit();n++)load(bank,item(100000001,1,dest,n));
    if(mode==1)bank.reject=true;if(mode==2)bank.explode=true;if(mode==5)bank.afterAdd=true;
    var i=item(stack?164000001:100000001,stack?23:1,0,4);load(a.cube,i);
    if(mode<3 || mode==5){if(stack)split(a,i,dest,7,(short)72);else move(a,i,dest,(short)-1);}
    else move(a,i,dest,(short)(mode==3?bank.getLimit():-5));
    retained(a,i,stack?23:1,4);
   }
   for(int dest:new int[]{1,2})for(boolean explode:new boolean[]{false,true}) {
    var a=actor(6,selector);var bank=(Bank)a.getStorage(dest);bank.reject=!explode;bank.explode=explode;
    var source=item(100000001,1,0,4);var replace=item(100000001,1,dest,72);load(a.cube,source);load(bank,replace);
    var packet=new CM_REPLACE_ITEM(0,Set.of(AionConnection.State.IN_GAME));packet.setConnection(a.client);
    packet.setBuffer(ByteBuffer.allocate(10).order(ByteOrder.LITTLE_ENDIAN).put((byte)0).putInt(source.getObjectId()).put((byte)dest).putInt(replace.getObjectId()).flip());
    check(packet.read(),"Actual CM_REPLACE_ITEM read");packet.run();retained(a,source,1,4);
    check(bank.getItemByObjId(replace.getObjectId())==replace && replace.getItemLocation()==dest && replace.getEquipmentSlot()==72,"Refused swap restores destination identity/slot too");
   }
   for(int expansions:new int[]{0,6,11})for(int dest:new int[]{1,2}){
    var a=actor(expansions,selector);var i=item(164000001,23,0,4);load(a.cube,i);split(a,i,dest,7,(short)72);
    check(i.getItemCount()==16 && a.getStorage(dest).getItems().getFirst().getItemCount()==7,"Successful partial drag preserves total quantity");
    check(a.getStorage(dest).getItems().getFirst().getEquipmentSlot()==72,"Partial drag honors target slot");
    var whole=item(164000001,19,0,5);load(a.cube,whole);split(a,whole,dest,19,(short)73);
    check(a.cube.getItemByObjId(whole.getObjectId())==null && a.getStorage(dest).getItemByObjId(whole.getObjectId())==whole,"Whole stack split packet routes to lossless move");
   }
   }
  } finally {
   var processor=(com.aionemu.commons.network.PacketProcessor<AionConnection>)field(AionConnection.class,"packetProcessor").get(null);
   var threads=(List<Thread>)field(com.aionemu.commons.network.PacketProcessor.class,"threads").get(processor);
   for(var thread:threads)thread.interrupt();
   var wakeClient=(AionConnection)unsafe.allocateInstance(AionConnection.class);
   field(AConnection.class,"pendingPackets").set(wakeClient,new ArrayDeque<>());
   var wake=new com.aionemu.commons.network.packet.BaseClientPacket<AionConnection>(0){protected void readImpl(){}protected void runImpl(){}public void run(){}};
   wake.setConnection(wakeClient);processor.executePacket(wake);
   for(var thread:threads)thread.join(1000);
  }
  check(packetErrors.list.isEmpty(),"No native packet handler exception may be swallowed");
  System.out.println("OK: "+checks+" warehouse capacity, native packet, save and failed-deposit checks");
 }
}
