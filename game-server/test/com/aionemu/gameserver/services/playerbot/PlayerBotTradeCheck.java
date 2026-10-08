package com.aionemu.gameserver.services.playerbot;

import java.lang.reflect.*;
import java.sql.*;
import java.util.*;
import sun.misc.Unsafe;
import com.aionemu.gameserver.model.account.Account;
import com.aionemu.gameserver.model.gameobjects.*;
import com.aionemu.gameserver.model.gameobjects.Persistable.PersistentState;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.items.storage.*;
import com.aionemu.gameserver.model.templates.item.ItemTemplate;
import com.aionemu.gameserver.model.trade.*;
import com.aionemu.gameserver.services.ExchangeService;
import com.aionemu.gameserver.controllers.PlayerController;
import com.aionemu.gameserver.model.TaskId;

/** Actual ownership SQL and native lock/cancel paths on world-free actors/JDBC; no live DB or IDs. */
public final class PlayerBotTradeCheck {
 static int checks;static Unsafe unsafe;
 static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
 static class Actor extends Player {
  int id,owner;PlayerStorage cube;boolean companion;Account account;Tasks tasks=new Tasks();
  Actor(){super(null,null);}
  @Override public int getObjectId(){return id;}
  @Override public PlayerStorage getInventory(){return cube;}
  @Override public boolean isPlayerBot(){return companion;}
  @Override public int getPlayerBotOwnerId(){return owner;}
  @Override public boolean isOnline(){return false;}
  @Override public Account getAccount(){return account;}
  @Override public PlayerController getController(){return tasks;}
  @Override public boolean isDead(){return false;}
  @Override public boolean isCasting(){return false;}
  @Override public boolean isLooting(){return false;}
 }
 static class Tasks extends PlayerController {boolean itemUse;@Override public boolean hasScheduledTask(TaskId id){return id==TaskId.ITEM_USE && itemUse;}@Override public boolean isInCombat(){return false;}}
 static Actor actor(int id,boolean bot)throws Exception {Actor a=(Actor)unsafe.allocateInstance(Actor.class);a.id=id;a.companion=bot;a.owner=1;a.cube=new PlayerStorage(null,StorageType.CUBE);a.account=(Account)unsafe.allocateInstance(Account.class);a.tasks=new Tasks();return a;}
 static Item item(int id,int tid,long count,boolean money){Item i=new Item(id,new ItemTemplate(){@Override public int getTemplateId(){return tid;}@Override public boolean isKinah(){return money;}},count,false,0);i.setItemLocation(0);i.setPersistentState(PersistentState.UPDATED);return i;}
 static Object zero(Class<?> t){if(!t.isPrimitive() || t==void.class)return null;if(t==boolean.class)return false;if(t==long.class)return 0L;if(t==double.class)return 0d;if(t==float.class)return 0f;return 0;}
 static <T>T proxy(Class<T> t,InvocationHandler h){return t.cast(Proxy.newProxyInstance(t.getClassLoader(),new Class<?>[]{t},h));}
 static class Jdbc {
  Map<Integer,long[]> rows=new HashMap<>();List<String> writes=new ArrayList<>();boolean autoCommit;int updates=1;
  Connection connection(){return proxy(Connection.class,(p,m,a)->{
   if(m.getName().equals("getAutoCommit"))return autoCommit;
   if(m.getName().equals("prepareStatement"))return statement((String)a[0]);
   if(Set.of("commit","rollback").contains(m.getName()))throw new AssertionError("Transfer must leave commit/rollback to caller");return zero(m.getReturnType());
  });}
  PreparedStatement statement(String sql){var values=new HashMap<Integer,Object>();return proxy(PreparedStatement.class,(p,m,a)->{
   if(m.getName().startsWith("set")){values.put((Integer)a[0],a[1]);return null;}
   if(m.getName().equals("executeQuery")){long[] row=rows.get((Integer)values.get(1));boolean[] used={false};
    return proxy(ResultSet.class,(r,rm,ra)->{if(rm.getName().equals("next")){boolean yes=row!=null && !used[0];used[0]=true;return yes;}
     if(rm.getName().equals("getInt"))return (int)row[(Integer)ra[0]-1];if(rm.getName().equals("getLong"))return row[(Integer)ra[0]-1];
     if(rm.getName().equals("getBoolean"))return row[(Integer)ra[0]-1]!=0;return zero(rm.getReturnType());});}
   if(m.getName().equals("executeUpdate")){writes.add(sql+" "+values);return updates;}
   if(m.getName().equals("addBatch")){writes.add(sql+" "+values);return null;}
   if(m.getName().equals("executeBatch"))return new int[]{1};return zero(m.getReturnType());
  });}
  void own(Actor a,Item i){rows.put(i.getObjectId(),new long[]{a.id,i.getItemId(),0,i.getItemCount(),0});}
 }
 static void fails(Actor owner,Actor bot,Exchange offer,Jdbc db,String reason)throws Exception {
  try{PlayerBotTradeStore.transfer(db.connection(),owner,bot,offer);throw new AssertionError("Expected "+reason);}catch(SQLException e){check(e.getMessage().contains(reason),"Exact transaction refusal: "+reason);}
 }
 @SuppressWarnings("unchecked") public static void main(String[] args)throws Exception {
  var f=Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);unsafe=(Unsafe)f.get(null);
  Actor owner=actor(1,false),bot=actor(2,true),foreign=actor(3,true);foreign.owner=999;
  check(PlayerBotTrade.session(owner,foreign)==null,"Foreign owner rejected without a roster lookup");
  check(!PlayerBotTrade.guard(owner,"item",55,1),"Human trades fall through unchanged");
  check(!PlayerBotTrade.ready(owner,bot),"Disconnected owner cannot trade");
  Item real=item(500,112000001,1,false);real.setEnchantLevel(8);real.setPackCount(2);real.setPersistentState(PersistentState.UPDATED);owner.cube.add_CharacterTransfer(real);
  var offer=new Exchange(owner,bot);offer.addItem(500,new ExchangeItem(500,1,real));var db=new Jdbc();db.own(owner,real);
  PlayerBotTradeStore.transfer(db.connection(),owner,bot,offer);
  check(db.writes.size()==1 && db.writes.getFirst().contains("item_owner=?") && db.writes.getFirst().contains("1=2"),"Full item moves durable ownership to exact companion");
  check(real.getEnchantLevel()==8 && real.getPackCount()==2 && owner.cube.getItemByObjId(500)==real && bot.cube.getItemByObjId(500)==null,"DB phase preserves attributes and publishes no in-memory success");
  for(int column:List.of(0,1,2,3,4)) {
   db=new Jdbc();db.own(owner,real);db.rows.get(500)[column]+=1;fails(owner,bot,offer,db,"custody/count");check(db.writes.isEmpty(),"Foreign/template/storage/count/equipped rejection precedes writes");
  }
  db=new Jdbc();fails(owner,bot,offer,db,"row missing");check(db.writes.isEmpty(),"Missing persisted item refused");
  for(int shared:List.of(2,3,125)){db=new Jdbc();db.own(owner,real);db.rows.get(500)[2]=shared;fails(owner,bot,offer,db,"custody/count");}
  db=new Jdbc();db.own(owner,real);db.autoCommit=true;fails(owner,bot,offer,db,"requires transaction");
  db=new Jdbc();db.own(owner,real);db.updates=0;fails(owner,bot,offer,db,"custody changed");
  real.setEquipped(true);db=new Jdbc();db.own(owner,real);fails(owner,bot,offer,db,"item changed");real.setEquipped(false);
  for(long count:List.of(0L,-1L,2L)){offer=new Exchange(owner,bot);offer.addItem(500,new ExchangeItem(500,count,real));db=new Jdbc();db.own(owner,real);fails(owner,bot,offer,db,"item changed");}
  Item stack=item(501,164000001,10,false),split=item(502,164000001,3,false);split.setPersistentState(PersistentState.NEW);owner.cube.add_CharacterTransfer(stack);
  offer=new Exchange(owner,bot);offer.addItem(501,new ExchangeItem(501,3,split));db=new Jdbc();db.own(owner,stack);PlayerBotTradeStore.transfer(db.connection(),owner,bot,offer);
  check(db.writes.size()==2 && db.writes.get(0).contains("1=7") && db.writes.get(1).startsWith("INSERT"),"Partial stack debits original and inserts fresh recipient ID in caller transaction");
  check(stack.getItemCount()==10 && owner.cube.getItemByObjId(501)==stack && bot.cube.getItemByObjId(502)==null,"Split DB phase does not publish or consume original");
  offer=new Exchange(owner,bot);offer.addItem(501,new ExchangeItem(501,3,stack));db=new Jdbc();db.own(owner,stack);fails(owner,bot,offer,db,"split identity");
  Item humanWallet=item(510,182400001,100,true),botWallet=item(511,182400001,20,true);owner.cube.onLoadHandler(humanWallet);bot.cube.onLoadHandler(botWallet);
  offer=new Exchange(owner,bot);offer.addKinah(30);db=new Jdbc();db.own(owner,humanWallet);db.own(bot,botWallet);PlayerBotTradeStore.transfer(db.connection(),owner,bot,offer);
  check(db.writes.size()==2 && db.writes.get(0).contains("1=70") && db.writes.get(1).contains("1=50"),"Kinah debit/credit use exact separate wallet custody");
  check(humanWallet.getItemCount()==100 && botWallet.getItemCount()==20,"Currency remains unchanged until DB commit");
  offer.addKinah(100);db=new Jdbc();fails(owner,bot,offer,db,"funds changed");
  check(PlayerBotTradeStore.balance(Long.MAX_VALUE-1,1)==Long.MAX_VALUE,"Currency boundary allowed");
  for(long[] bad:List.of(new long[]{Long.MAX_VALUE,1},new long[]{-1,0},new long[]{0,-1})){try{PlayerBotTradeStore.balance(bad[0],bad[1]);throw new AssertionError("overflow");}catch(SQLException e){check(e.getMessage().contains("overflow"),"Signed overflow/negative refused");}}
  // Production cleanup with no allocated IDs. A success cleanup must not release persisted split IDs.
  var service=ExchangeService.getInstance();var field=ExchangeService.class.getDeclaredField("exchanges");field.setAccessible(true);var map=(Map<Integer,Exchange>)field.get(service);
  map.put(1,new Exchange(owner,bot));map.put(2,new Exchange(bot,owner));
  var finishing=PlayerBotTrade.class.getDeclaredField("FINISHING");finishing.setAccessible(true);((ThreadLocal<Player>)finishing.get(null)).set(owner);
  var inside=PlayerBotTrade.class.getDeclaredField("INSIDE");inside.setAccessible(true);((ThreadLocal<Boolean>)inside.get(null)).set(true);
  service.cancelExchange(owner);((ThreadLocal<?>)inside.get(null)).remove();((ThreadLocal<?>)finishing.get(null)).remove();
  check(!service.isPlayerInExchange(owner) && !service.isPlayerInExchange(bot),"Successful native cleanup removes both sides without cancellation or ID release");
  check(!PlayerBotTrade.finishing(owner),"Completion guard cleared");
  var sessionsField=PlayerBotService.class.getDeclaredField("sessions");sessionsField.setAccessible(true);var sessions=(Map<Integer,PlayerBotSession>)sessionsField.get(PlayerBotService.getInstance());
  for(boolean temporary:List.of(false,true)) {
   var s=(PlayerBotSession)unsafe.allocateInstance(PlayerBotSession.class);
   for(var e:Map.of("owner",(Object)owner,"bot",bot,"generated",temporary).entrySet()){var member=PlayerBotSession.class.getDeclaredField(e.getKey());member.setAccessible(true);member.set(s,e.getValue());}
   sessions.put(bot.id,s);map.put(1,new Exchange(owner,bot));map.put(2,new Exchange(bot,owner));
   try {
    check(PlayerBotTrade.trading(owner)==s && PlayerBotTrade.trading(bot)==s,"Native companion pair resolves for alt and Temporary Bot");
    check(PlayerBotTrade.confirm(owner),"Premature confirmation handled by bot adapter");
    check(service.isPlayerInExchange(owner),"Unlocked confirmation cannot transfer or close the offer");
    ((ThreadLocal<Boolean>)inside.get(null)).set(true);service.lockExchange(owner);((ThreadLocal<?>)inside.get(null)).remove();
    check(map.get(1).isLocked() && map.get(2).isLocked(),"Owner lock automatically locks companion's native side");
    ((ThreadLocal<Boolean>)inside.get(null)).set(true);service.cancelExchange(owner);((ThreadLocal<?>)inside.get(null)).remove();
    check(!service.isPlayerInExchange(owner) && !service.isPlayerInExchange(bot),"Native cancellation clears both sides for alt and Temporary Bot");
    var receivedField=PlayerBotTrade.class.getDeclaredField("RECEIVED");receivedField.setAccessible(true);var received=(Map<Integer,Set<Integer>>)receivedField.get(null);
    received.put(bot.id,new HashSet<>(List.of(502)));bot.tasks.itemUse=true;
    check(PlayerBotTrade.tick(s),"Active native received-item binding/identification pauses follow AI");
    check(received.get(bot.id).contains(502),"Received item remains pending during native ITEM_USE");
    received.remove(bot.id);bot.tasks.itemUse=false;
    check(!PlayerBotTrade.tick(s),"No pending item task resumes ordinary AI");
   }finally{sessions.remove(bot.id);map.remove(1);map.remove(2);((ThreadLocal<?>)inside.get(null)).remove();}
  }
  check(!PlayerBotTrade.confirm(owner),"Human confirmation remains native when partner is human/absent");
  check(!PlayerBotTrade.confirm(null) && !PlayerBotTrade.guard(null,"cancel",0,0),"Native null participant/confirmation guards preserved");
  System.out.println("OK: "+checks+" actual trade custody, split, currency, human-fallthrough and native completion checks; no live DB/world/ID operations.");
 }
}
