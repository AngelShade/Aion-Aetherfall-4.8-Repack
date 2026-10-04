package com.aionemu.gameserver.services.playerbot;

import java.lang.reflect.*;
import java.sql.*;
import java.util.*;
import sun.misc.Unsafe;
import com.aionemu.gameserver.dao.InventoryDAO;
import com.aionemu.gameserver.model.gameobjects.*;
import com.aionemu.gameserver.model.gameobjects.Persistable.PersistentState;
import com.aionemu.gameserver.model.gameobjects.player.*;
import com.aionemu.gameserver.model.items.storage.*;
import com.aionemu.gameserver.model.templates.item.ItemTemplate;

/** Exercises the actual transaction with a recording JDBC boundary, no live DB or IDs. */
public final class PlayerBotCustodyCheck {
 static int checks;static Unsafe unsafe;
 static void check(boolean value,String reason){checks++;if(!value)throw new AssertionError(reason);}
 static class Actor extends Player {
  List<Item> collected;PlayerStorage cube;Equipment equipment;boolean bot=true;
  Actor(){super(null,null);}
  @Override public boolean isPlayerBot(){return bot;}
  @Override public List<Item> getDirtyItemsToUpdate(){cube.setPersistentState(PersistentState.UPDATED);return collected;}
  @Override public Storage getStorage(int id){return id==0?cube:null;}
  @Override public Equipment getEquipment(){return equipment;}
 }
 static Actor actor(List<Item> items)throws Exception {
  var a=(Actor)unsafe.allocateInstance(Actor.class);a.bot=true;a.collected=items;a.cube=new PlayerStorage(null,StorageType.CUBE);
  a.equipment=(Equipment)unsafe.allocateInstance(Equipment.class);return a;
 }
 static Item item(int id,int template,int location,PersistentState state){
  var i=new Item(id,new ItemTemplate(){@Override public int getTemplateId(){return template;}},1,false,0);
  i.setItemLocation(location);i.setPersistentState(PersistentState.UPDATED);i.setPersistentState(state);return i;
 }
 static Object fallback(Class<?> type){if(!type.isPrimitive() || type==void.class)return null;if(type==boolean.class)return false;if(type==long.class)return 0L;if(type==float.class)return 0f;if(type==double.class)return 0d;return 0;}
 static <T>T proxy(Class<T> type,InvocationHandler h){return type.cast(Proxy.newProxyInstance(type.getClassLoader(),new Class<?>[]{type},h));}
 static class Jdbc {
  Map<Integer,int[]> rows=new HashMap<>();List<Integer> checked=new ArrayList<>(),written=new ArrayList<>();boolean autoCommit,failBatch;
  Connection connection(){return proxy(Connection.class,(p,m,a)->{
   if(m.getName().equals("getAutoCommit"))return autoCommit;
   if(m.getName().equals("prepareStatement"))return statement((String)a[0]);
   if(Set.of("commit","rollback").contains(m.getName()))throw new AssertionError("Inventory DAO must leave transaction completion to caller");
   return fallback(m.getReturnType());
  });}
  PreparedStatement statement(String sql){var params=new HashMap<Integer,Object>();return proxy(PreparedStatement.class,(p,m,a)->{
   if(m.getName().startsWith("set")){params.put((Integer)a[0],a[1]);return null;}
   if(m.getName().equals("executeQuery")){
    int id=(Integer)params.get(1);checked.add(id);int[] row=rows.get(id);boolean[] seen={false};
    return proxy(ResultSet.class,(r,rm,ra)->{if(rm.getName().equals("next")){boolean next=row!=null && !seen[0];seen[0]=true;return next;}if(rm.getName().equals("getInt"))return row[(Integer)ra[0]-1];return fallback(rm.getReturnType());});
   }
   if(m.getName().equals("addBatch")){int pos=sql.startsWith("DELETE")?1:sql.startsWith("INSERT")?1:28;written.add((Integer)params.get(pos));return null;}
   if(m.getName().equals("executeBatch")){if(failBatch)throw new SQLException("fixture batch failure");return new int[]{1};}
   return fallback(m.getReturnType());
  });}
 }
 static void rejected(Actor a,Jdbc db,String message)throws Exception {
  try{InventoryDAO.storeCompanionInventory(db.connection(),a);throw new AssertionError("Expected rejection: "+message);}catch(SQLException expected){check(expected.getMessage().contains(message),"Correct guard reason");}
  check(db.written.isEmpty(),"Custody rejection precedes all SQL writes");
  check(a.cube.getPersistentState()==PersistentState.UPDATE_REQUIRED,"Failed checkpoint retains storage retry");
 }
 public static void main(String[] args)throws Exception {
  var f=Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);unsafe=(Unsafe)f.get(null);
  for(var state:PersistentState.values()) {
   var entry=item(16204,164000119,0,state);var a=actor(List.of(entry));var db=new Jdbc();db.rows.put(16204,new int[]{9403,182004100,0});
   if(Set.of(PersistentState.NEW,PersistentState.UPDATE_REQUIRED,PersistentState.DELETED).contains(state))rejected(a,db,"custody mismatch");
   else {var pending=InventoryDAO.storeCompanionInventory(db.connection(),a);check(pending.isEmpty(),"Committed/no-action objects excluded");check(db.checked.isEmpty() && db.written.isEmpty(),"Obsolete foreign ID neither queried nor written");}
  }
  for(var state:List.of(PersistentState.NEW,PersistentState.UPDATE_REQUIRED,PersistentState.DELETED)) {
   for(int shared:List.of(2,3,125)){
    var a=actor(List.of(item(27593,164000064,0,state)));var db=new Jdbc();db.rows.put(27593,new int[]{0,164000064,shared});rejected(a,db,"custody mismatch");
    a=actor(List.of(item(27593,164000064,shared,state)));db=new Jdbc();rejected(a,db,"shared inventory");
   }
   var a=actor(List.of(item(106916,164000073,0,state)));var db=new Jdbc();db.rows.put(106916,new int[]{0,162000043,0});rejected(a,db,"custody mismatch");
  }
  var old=item(106916,164000073,0,PersistentState.UPDATED);var current=item(106916,162000043,0,PersistentState.UPDATE_REQUIRED);
  var created=item(9000001,164000119,0,PersistentState.NEW);var deleted=item(9000002,164000064,0,PersistentState.DELETED);
  var a=actor(List.of(old,current,created,deleted));var db=new Jdbc();db.rows.put(106916,new int[]{0,162000043,0});db.rows.put(9000002,new int[]{0,164000064,0});
  var pending=InventoryDAO.storeCompanionInventory(db.connection(),a);
  check(pending.size()==3 && pending.stream().noneMatch(i->i==old),"Recycled ID preserves distinct new object; stale identity excluded");
  check(db.checked.equals(List.of(106916,9000001,9000002)),"Custody checks exactly pending writes");
  check(db.written.equals(List.of(9000002,9000001,106916)),"Correct native delete/insert/update order and IDs");
  check(deleted.getPersistentState()==PersistentState.DELETED && created.getPersistentState()==PersistentState.NEW,"State unchanged until caller commit");
  deleted.setPersistentState(PersistentState.UPDATED);created.setPersistentState(PersistentState.UPDATED);current.setPersistentState(PersistentState.UPDATED);
  db=new Jdbc();db.rows.put(9000002,new int[]{9403,121000328,0});pending=InventoryDAO.storeCompanionInventory(db.connection(),a);
  check(pending.isEmpty() && db.checked.isEmpty() && db.written.isEmpty(),"Later save cannot touch/release reused committed deletion ID");
  a=actor(List.of(item(9000003,164000073,0,PersistentState.DELETED)));db=new Jdbc();db.failBatch=true;
  try{InventoryDAO.storeCompanionInventory(db.connection(),a);throw new AssertionError("SQL failure must escape");}catch(SQLException expected){check(expected.getMessage().contains("transaction failed"),"SQL failure blocks checkpoint");}
  check(a.collected.getFirst().getPersistentState()==PersistentState.DELETED && a.cube.getPersistentState()==PersistentState.UPDATE_REQUIRED,"Rollback retry preserves pending deletion");
  a=actor(List.of());a.bot=false;db=new Jdbc();try{InventoryDAO.storeCompanionInventory(db.connection(),a);throw new AssertionError("Human guard");}catch(SQLException expected){check(expected.getMessage().contains("private"),"Human persistence unchanged");}
  a=actor(List.of());db=new Jdbc();db.autoCommit=true;try{InventoryDAO.storeCompanionInventory(db.connection(),a);throw new AssertionError("Transaction guard");}catch(SQLException expected){check(expected.getMessage().contains("private"),"Private transaction required");}
  System.out.println("OK: "+checks+" production custody checks; no DB, world objects or ID allocation/release.");
 }
}
