package com.aionemu.gameserver.services.playerbot;

import java.lang.reflect.*;
import java.nio.file.*;
import java.nio.channels.*;
import java.nio.*;
import java.sql.*;
import java.util.*;
import sun.misc.Unsafe;
import ch.qos.logback.classic.*;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.aionemu.commons.configs.DatabaseConfig;
import com.aionemu.commons.configuration.ConfigurableProcessor;
import com.aionemu.commons.database.DatabaseFactory;
import com.aionemu.gameserver.configs.main.*;
import com.aionemu.gameserver.controllers.PlayerController;
import com.aionemu.gameserver.controllers.movement.PlayerMoveController;
import com.aionemu.gameserver.dao.InventoryDAO;
import com.aionemu.gameserver.dataholders.*;
import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.model.account.Account;
import com.aionemu.gameserver.model.gameobjects.Item;
import com.aionemu.gameserver.model.gameobjects.Persistable.PersistentState;
import com.aionemu.gameserver.model.gameobjects.player.*;
import com.aionemu.gameserver.model.items.storage.*;
import com.aionemu.gameserver.model.stats.calc.*;
import com.aionemu.gameserver.model.stats.container.*;
import com.aionemu.gameserver.model.templates.item.*;
import com.aionemu.gameserver.model.trade.*;
import com.aionemu.gameserver.network.aion.*;
import com.aionemu.gameserver.network.aion.clientpackets.CM_BUY_ITEM;
import com.aionemu.gameserver.network.aion.serverpackets.SM_MESSAGE;
import com.aionemu.gameserver.services.*;
import com.aionemu.gameserver.services.trade.EconomyPrices;
import com.aionemu.gameserver.world.knownlist.KnownList;

/** Native restock/open/sell/checkpoint/reload on scratch SQL only. No server, socket or gameplay loop. */
public final class PlayerBotRoadMerchantSaleCheck {
 private static Unsafe unsafe;
 private static int checks;private static boolean stacks;
 private static final ListAppender<ILoggingEvent> warnings=new ListAppender<>();
 private static final int BOT=5001,BUYER=5002,GOODS=100000001,MONEY=182400001,SHARDS=169000009;
 private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 private static Field field(Class<?> type,String name)throws Exception {var f=type.getDeclaredField(name);f.setAccessible(true);return f;}
 private static void set(Object target,String name,Object value)throws Exception {field(target.getClass(),name).set(target,value);}
 @SuppressWarnings("unchecked") private static <T>T get(Object target,Class<?> type,String name)throws Exception {return (T)field(type,name).get(target);}
 private static final class Client extends AionConnection {
  Queue<AionServerPacket> packets;
  Client()throws Exception {super(null,null);}
 }
 private static final class Key extends SelectionKey {
  final Selector selector;
  Key()throws Exception {selector=Selector.open();}
  @Override public SelectableChannel channel(){return null;}
  @Override public Selector selector(){return selector;}
  @Override public boolean isValid(){return true;}
  @Override public void cancel(){}
  @Override public int interestOps(){return 0;}
  @Override public SelectionKey interestOps(int ops){return this;}
  @Override public int readyOps(){return 0;}
 }
 private static final class Stats extends PlayerGameStats {
  Stats(){super(null);}
  @Override public Stat2 getAttackSpeed(){return new AdditionStat(StatEnum.ATTACK_SPEED,1500,null);}
  @Override public float getMovementSpeedFloat(){return 6;}
 }
 private static final class Controller extends PlayerController {
  @Override public boolean isInCombat(){return false;}
 }
 private static final class Actor extends Player {
  int id;boolean bot,dead;float x;Race race;Storage cube;Equipment equipment;Account account;Client client;
  KnownList known;PlayerMoveController move;PlayerGameStats stats;PlayerController controller;
  Actor(){super(null,null);}
  @Override public int getObjectId(){return id;}
  @Override public String getName(boolean tag){return bot?"Scratch Peddler":"Scratch Buyer";}
  @Override public boolean isPlayerBot(){return bot;}
  @Override public boolean isWorldBot(){return bot;}
  @Override public AionConnection getClientConnection(){return client;}
  @Override public boolean isSpawned(){return true;}
  @Override public boolean isDead(){return dead;}
  @Override public boolean isCasting(){return false;}
  @Override public boolean isLooting(){return false;}
  @Override public Race getRace(){return race;}
  @Override public byte getLevel(){return 20;}
  @Override public int getWorldId(){return 1;}
  @Override public int getInstanceId(){return 1;}
  @Override public float getX(){return x;}
  @Override public float getY(){return 0;}
  @Override public float getZ(){return 0;}
  @Override public Storage getInventory(){return cube;}
  @Override public Storage getStorage(int location){return location==0?cube:null;}
  @Override public Equipment getEquipment(){return equipment;}
  @Override public Account getAccount(){return account;}
  @Override public KnownList getKnownList(){return known;}
  @Override public PlayerMoveController getMoveController(){return move;}
  @Override public PlayerGameStats getGameStats(){return stats;}
  @Override public PlayerController getController(){return controller;}
 }
 private static Actor actor(int id,boolean bot)throws Exception {
  var a=(Actor)unsafe.allocateInstance(Actor.class);a.id=id;a.bot=bot;a.race=Race.ELYOS;
  a.account=new Account(bot?PlayerBotPopulationPolicy.account(Race.ELYOS,0):1);
  a.cube=new PlayerStorage(a,StorageType.CUBE);a.cube.setLimit(30);a.equipment=new Equipment(a);field(Player.class,"equipment").set(a,a.equipment);
  a.known=new KnownList(a);a.move=new PlayerMoveController(a);a.controller=new Controller();a.stats=(Stats)unsafe.allocateInstance(Stats.class);
  if(!bot){a.client=(Client)unsafe.allocateInstance(Client.class);a.client.packets=new ArrayDeque<>();
   field(AionConnection.class,"sendMsgQueue").set(a.client,a.client.packets);
   field(AionConnection.class,"activePlayer").set(a.client,new java.util.concurrent.atomic.AtomicReference<Player>(a));
   field(AionConnection.class,"state").set(a.client,AionConnection.State.IN_GAME);
   field(com.aionemu.commons.network.AConnection.class,"guard").set(a.client,new Object());
   field(com.aionemu.commons.network.AConnection.class,"key").set(a.client,new Key());}
  return a;
 }
 private static void templates()throws Exception {
  var data=new ItemData();Map<Integer,ItemTemplate> items=get(data,ItemData.class,"items");
  // A stigma exercises actual eligibility without unrelated combat stat or wardrobe data.
  var goods=new ItemTemplate();set(goods,"itemId",GOODS);set(goods,"name","Regional Stigma");set(goods,"itemQuality",ItemQuality.RARE);set(goods,"useLimits",new ItemUseLimits());
  set(goods,"itemGroup",com.aionemu.gameserver.model.templates.item.enums.ItemGroup.STIGMA);set(goods,"stigma",new Stigma());set(goods,"price",1000);
  var money=new ItemTemplate();set(money,"itemId",MONEY);set(money,"name","Kinah");
  // A stackable equipment-group item, as merchants carry power shards.
  var shards=new ItemTemplate();set(shards,"itemId",SHARDS);set(shards,"name","Premium Power Shard");set(shards,"itemQuality",ItemQuality.COMMON);set(shards,"useLimits",new ItemUseLimits());
  set(shards,"itemGroup",com.aionemu.gameserver.model.templates.item.enums.ItemGroup.POWER_SHARDS);set(shards,"maxStackCount",10000);set(shards,"price",220);
  items.put(GOODS,goods);items.put(MONEY,money);items.put(SHARDS,shards);DataManager.ITEM_DATA=data;
  // Empty native economy catalogue: no full static world load is needed for a custody check.
  field(EconomyPrices.class,"npcRates").set(null,Map.of());field(EconomyPrices.class,"purchaseRates").set(null,Map.of());
 }
 private static PlayerBotRoadDuty.State merchant(Actor bot)throws Exception {
  var session=(PlayerBotSession)unsafe.allocateInstance(PlayerBotSession.class);
  field(PlayerBotSession.class,"bot").set(session,bot);field(PlayerBotSession.class,"owner").set(session,bot);
  Map<Integer,PlayerBotSession> sessions=get(PlayerBotService.getInstance(),PlayerBotService.class,"sessions");sessions.put(BOT,session);
  var route=new PlayerBotPatrolRoutes.Route("scratch",1,Race.ELYOS,20,210001,"Scratch road",List.of(new PlayerBotNavigation.Point(0,0,0),new PlayerBotNavigation.Point(10,0,0)),Map.of(),false);
  field(PlayerBotPatrolRoutes.class,"routes").set(null,Map.of(route.id(),route));
  var values=PlayerBotMetadata.load(bot.account.getId(),BOT,"road-duty",null);
  if(values.isEmpty()){values.setProperty("account",Integer.toString(bot.account.getId()));values.setProperty("character",Integer.toString(BOT));values.setProperty("career","MERCHANT");values.setProperty("rank","PEDDLER");values.setProperty("route",route.id());}
  var state=new PlayerBotRoadDuty.State(session,values);
  Map<Integer,PlayerBotRoadDuty.State> states=get(null,PlayerBotRoadDuty.class,"STATES");states.put(BOT,state);
  var pool=Class.forName(PlayerBotRoadMerchants.class.getName()+"$Pool").getDeclaredConstructors()[0];pool.setAccessible(true);
  var supply=Class.forName(PlayerBotRoadMerchants.class.getName()+"$Supply").getDeclaredConstructors()[0];supply.setAccessible(true);
  Map<Object,Object> pools=get(null,PlayerBotRoadMerchants.class,"POOLS");
  int sold=stacks?SHARDS:GOODS;
  pools.put(pool.newInstance(1,Race.ELYOS,20,0),supply.newInstance(List.of(new LootCodexService.RegionalGoods(sold,210001,1,"")),Set.of(sold)));
  return state;
 }
 private static void checkpoint(Actor bot)throws Exception {
  try(var c=DatabaseFactory.getConnection()){c.setAutoCommit(false);var items=InventoryDAO.storeCompanionInventory(c,bot);var metadata=PlayerBotMetadata.pending(bot.account.getId(),BOT);
   PlayerBotMetadata.store(c,metadata);c.commit();InventoryDAO.companionInventoryCommitted(items);PlayerBotMetadata.committed(metadata);}
 }
 private static long scalar(Connection c,String sql)throws Exception {try(var s=c.createStatement();var r=s.executeQuery(sql)){check(r.next(),"SQL result exists");return r.getLong(1);}}
 private static void schema(Connection c,Path root)throws Exception {
  var all=Files.readString(root.resolve("game-server/sql/aion_gs.sql"));
  try(var s=c.createStatement()) {
   for(String name:List.of("inventory","item_stones")){int start=all.indexOf("CREATE TABLE `"+name+"`");s.execute(all.substring(start,all.indexOf(';',all.indexOf("ENGINE=",start))));}
   s.execute("CREATE TABLE players(id INT PRIMARY KEY,account_id INT NOT NULL)");
   s.execute("CREATE TABLE player_registered_items(item_unique_id INT PRIMARY KEY)");s.execute("CREATE TABLE mail(mail_unique_id INT PRIMARY KEY)");s.execute("CREATE TABLE guides(guide_id INT PRIMARY KEY)");
   for(String name:List.of("legions","houses","player_pets"))s.execute("CREATE TABLE "+name+"(id INT PRIMARY KEY)");
   s.execute(Files.readString(root.resolve("game-server/sql/playerbot_metadata.sql")));
   s.execute("INSERT INTO players VALUES("+BOT+","+PlayerBotPopulationPolicy.account(Race.ELYOS,0)+"),("+BUYER+",1)");
  }
 }
 private static void buy(Actor bot,Actor buyer,int index,long count){var request=new TradeList(BOT);request.addItem(index,count);PrivateStoreService.sellStoreItem(bot,buyer,request);}
 private static void packetBuy(Actor buyer,int action)throws Exception {
  var packet=new CM_BUY_ITEM(0,Set.of(AionConnection.State.IN_GAME));packet.setConnection(buyer.client);
  var bytes=ByteBuffer.allocate(action==0?20:8).order(ByteOrder.LITTLE_ENDIAN);bytes.putInt(BOT).put((byte)action).put((byte)0).putShort((short)(action==0?1:0));
  if(action==0)bytes.putInt(0).putLong(1);bytes.flip();packet.setBuffer(bytes);check(packet.read(),"Native CM_BUY_ITEM reads the request");packet.run();
 }
 private static Runnable packet(Actor buyer,int action){return ()->{try{packetBuy(buyer,action);}catch(Exception error){throw new AssertionError(error);}};}
 private static void rejected(Actor bot,Actor buyer,Runnable request,String reason)throws Exception {
  int messages=buyer.client.packets.size(),logs=warnings.list.size();long from=buyer.cube.getKinah(),to=bot.cube.getKinah();
  request.run();
  check(buyer.client.packets.size()==messages+1,"Refusal sends one buyer packet: "+reason);
  var packet=buyer.client.packets.stream().skip(messages).findFirst().orElseThrow();
  check(packet instanceof SM_MESSAGE && ((String)field(SM_MESSAGE.class,"message").get(packet)).contains(reason),"Refusal explains why in game: "+reason);
  check(warnings.list.size()==logs+1 && warnings.list.getLast().getLevel()==Level.WARN,"Refusal logs exactly one WARN: "+reason);
  String log=warnings.list.getLast().getFormattedMessage();
  check(log.contains("merchant=Scratch Peddler (5001)") && log.contains("item=") && log.contains("reason="),"Refusal log identifies merchant, item and reason");
  check(buyer.cube.getKinah()==from && bot.cube.getKinah()==to,"Refusal never moves native Kinah");
 }
 private static void refusals(Connection c,Actor bot,Actor buyer,PlayerBotRoadDuty.State state,TradePSItem entry,boolean reload)throws Exception {
  var store=bot.getStore();
  buyer.known=new KnownList(buyer){@Override public com.aionemu.gameserver.model.gameobjects.VisibleObject getObject(int id){return id==BOT?bot:null;}};
  rejected(bot,buyer,packet(buyer,99),"shop request is invalid");rejected(bot,buyer,packet(buyer,13),"shop request is invalid");
  buyer.race=Race.ASMODIANS;rejected(bot,buyer,()->buy(bot,buyer,0,1),"own faction");buyer.race=Race.ELYOS;
  bot.setStore(null);rejected(bot,buyer,()->buy(bot,buyer,0,1),"shop has closed");bot.setStore(store);
  rejected(bot,buyer,()->PrivateStoreService.sellStoreItem(bot,buyer,new TradeList(BOT)),"Choose an item");
  rejected(bot,buyer,()->buy(bot,buyer,10,1),"listing is no longer available");
  rejected(bot,buyer,()->buy(bot,buyer,0,2),"available quantity");rejected(bot,buyer,()->buy(bot,buyer,0,0),"available quantity");
  buyer.cube.setLimit(0);rejected(bot,buyer,()->buy(bot,buyer,0,1),"Make room");buyer.cube.setLimit(30);
  var money=buyer.cube.getKinahItem();long funds=money.getItemCount();money.setItemCount(entry.getPrice()-1);
  rejected(bot,buyer,()->buy(bot,buyer,0,1),"Not enough Kinah");money.setItemCount(funds);
  buyer.x=100;rejected(bot,buyer,()->buy(bot,buyer,0,1),"Stay beside");buyer.x=0;
  buyer.dead=true;rejected(bot,buyer,()->buy(bot,buyer,0,1),"alive");buyer.dead=false;
  String key="stock."+entry.getItemObjId();Object provenance=state.values.remove(key);
  rejected(bot,buyer,()->buy(bot,buyer,0,1),"item is no longer available");state.values.put(key,provenance);
  store.addItemToSell(entry.getItemObjId(),new TradePSItem(999999,GOODS,1,entry.getPrice()));
  rejected(bot,buyer,()->buy(bot,buyer,0,1),"no longer in stock");store.addItemToSell(entry.getItemObjId(),entry);
  Map<Integer,PlayerBotSession> sessions=get(PlayerBotService.getInstance(),PlayerBotService.class,"sessions");var session=sessions.remove(BOT);
  rejected(bot,buyer,()->buy(bot,buyer,0,1),"merchant is unavailable");sessions.put(BOT,session);
  field(PlayerBotSession.class,"owner").set(session,buyer);rejected(bot,buyer,()->buy(bot,buyer,0,1),"shop has changed or closed");field(PlayerBotSession.class,"owner").set(session,bot);
  rejected(bot,buyer,()->PlayerBotRoadMerchants.sell(bot,buyer,new PrivateStore(bot),List.of(entry),entry.getPrice()),"shop has changed or closed");
  if(reload){try(var s=c.createStatement()){s.executeUpdate("UPDATE inventory SET item_owner=9999 WHERE item_unique_id="+entry.getItemObjId());}
   rejected(bot,buyer,()->buy(bot,buyer,0,1),"could not be saved");
   check(scalar(c,"SELECT item_owner FROM inventory WHERE item_unique_id="+entry.getItemObjId())==9999,"Custody refusal preserves the foreign owner");
   try(var s=c.createStatement()){s.executeUpdate("UPDATE inventory SET item_owner="+BOT+" WHERE item_unique_id="+entry.getItemObjId());}}
  check(store.getSoldItems().size()==1 && bot.cube.getItemByObjId(entry.getItemObjId())!=null,"Refusals retain listing and merchandise");
 }
 private static void sale(Connection c,boolean reload)throws Exception {
  var bot=actor(BOT,true);var buyer=actor(BUYER,false);var state=merchant(bot);
  for(var a:List.of(bot,buyer)){var money=new Item(a.bot?90001:90002,DataManager.ITEM_DATA.getItemTemplate(MONEY),a.bot?100:1000000,false,0);a.cube.onLoadHandler(money);check(InventoryDAO.store(money,a),"Scratch wallet stored");}
  PlayerBotRoadMerchants.maintenance(state);
  check(bot.cube.getItems().size()==1,"Actual maintenance restocks finite merchandise");var item=bot.cube.getItems().iterator().next();int id=item.getObjectId();
  check(item.getPersistentState()==PersistentState.NEW && scalar(c,"SELECT COUNT(*) FROM inventory WHERE item_unique_id="+id)==0,"Fresh ItemFactory merchandise has not been checkpointed");
  if(reload){checkpoint(bot);PlayerBotMetadata.release(bot.account.getId(),BOT);bot=actor(BOT,true);InventoryDAO.loadStorage(BOT,bot.cube);state=merchant(bot);item=bot.cube.getItemByObjId(id);check(item!=null && item.getPersistentState()==PersistentState.UPDATED,"Native DAO reload restores checkpointed merchandise and provenance");}
  check(!bot.isOnline() && bot.getClientConnection()==null && buyer.isOnline(),"Native isOnline distinguishes a headless merchant from its buyer");
  PlayerBotRoadMerchants.open(state);var store=bot.getStore();check(store!=null && store.getSoldItems().size()==1,"Actual open lists restocked merchandise");
  var entry=store.getSoldItems().values().iterator().next();long price=entry.getPrice();check(price>0 && entry.getItemObjId()==id && entry.getCount()==1,"Shop lists the actual object, count and price");
  // Run the happy-path assertion first on the baseline, so it fails at the reported public entry point.
  if(Boolean.getBoolean("merchant.refusals"))refusals(c,bot,buyer,state,entry,reload);
  long beforeBuyer=buyer.cube.getKinah(),beforeSeller=bot.cube.getKinah();buy(bot,buyer,0,1);
  if(buyer.cube.getItemByObjId(id)==null){check(bot.cube.getKinah()==beforeSeller && buyer.cube.getKinah()==beforeBuyer && scalar(c,"SELECT item_count FROM inventory WHERE item_unique_id=90002")==beforeBuyer && scalar(c,"SELECT item_count FROM inventory WHERE item_unique_id=90001")==beforeSeller,"Reproduced refusal leaves both native and SQL wallets unchanged");System.out.println("REPRO: headless merchant silently refused; item/listing retained; Kinah unchanged; reloaded="+reload);}
  check(buyer.cube.getItemByObjId(id)!=null,"Public sellStoreItem must deliver merchandise (reloaded="+reload+")");
  check(bot.cube.getItemByObjId(id)==null && store.getSoldItems().isEmpty(),"Sale removes seller inventory and listing");
  check(buyer.cube.getKinah()==beforeBuyer-price && bot.cube.getKinah()==beforeSeller+price,"Native wallets move exactly the price once");
  check(scalar(c,"SELECT item_owner FROM inventory WHERE item_unique_id="+id)==BUYER && scalar(c,"SELECT item_count FROM inventory WHERE item_unique_id=90002")==beforeBuyer-price && scalar(c,"SELECT item_count FROM inventory WHERE item_unique_id=90001")==beforeSeller+price,"SQL ownership and both wallets commit together");
  buy(bot,buyer,0,1);check(buyer.cube.getKinah()==beforeBuyer-price && bot.cube.getKinah()==beforeSeller+price,"Retry cannot pay twice");
  if(Boolean.getBoolean("merchant.refusals"))rejected(bot,buyer,packet(buyer,0),"shop has closed");
  checkpoint(bot);check(InventoryDAO.store(buyer.getDirtyItemsToUpdate(),BUYER,1,null),"Buyer relog checkpoint succeeds");
  var restoredBuyer=actor(BUYER,false);var restoredBot=actor(BOT,true);InventoryDAO.loadStorage(BUYER,restoredBuyer.cube);InventoryDAO.loadStorage(BOT,restoredBot.cube);
  check(restoredBuyer.cube.getItemByObjId(id)!=null && restoredBot.cube.getItemByObjId(id)==null,"Native reload retains exactly one buyer-owned item");
  check(restoredBuyer.cube.getKinah()==beforeBuyer-price && restoredBot.cube.getKinah()==beforeSeller+price,"Native reload retains both settled wallets");
  PlayerBotMetadata.release(bot.account.getId(),BOT);var restoredState=merchant(restoredBot);PlayerBotRoadMerchants.open(restoredState);
  check(restoredBot.getStore()==null,"Reloaded road provenance cannot relist sold merchandise");
 }
 private static long count(Connection c,int id)throws Exception {return scalar(c,"SELECT item_count FROM inventory WHERE item_unique_id="+id);}
 /** Stackable goods: stocked and listed as one stack, part of it sold as a new item, the rest later, then a reload. */
 private static void stackSale(Connection c)throws Exception {
  var bot=actor(BOT,true);var buyer=actor(BUYER,false);var state=merchant(bot);
  for(var a:List.of(bot,buyer)){var money=new Item(a.bot?90001:90002,DataManager.ITEM_DATA.getItemTemplate(MONEY),a.bot?100:100000000,false,0);a.cube.onLoadHandler(money);check(InventoryDAO.store(money,a),"Scratch wallet stored");}
  PlayerBotRoadMerchants.maintenance(state);
  check(bot.cube.getItems().size()==1,"Maintenance stocks one stack");var stack=bot.cube.getItems().iterator().next();int id=stack.getObjectId();
  check(stack.getItemId()==SHARDS && stack.getItemCount()>=100 && stack.getItemCount()<=1000,"A stackable item is stocked as a stack of 100-1000, not 1: "+stack.getItemCount());
  // An older single-unit stack is topped up at the next restock, saved or not.
  checkpoint(bot);stack.setItemCount(1);check(InventoryDAO.store(stack,bot),"Legacy single unit saved");
  state.values.setProperty("restockAt","1");PlayerBotRoadMerchants.maintenance(state);
  check(bot.cube.getItems().size()==1 && stack.getItemCount()>=100 && stack.getItemCount()<=1000,"Restock tops a single unit back up to a stack: "+stack.getItemCount());
  checkpoint(bot);long full=stack.getItemCount();check(count(c,id)==full,"Topped-up stack is saved");
  PlayerBotRoadMerchants.open(state);var store=bot.getStore();
  var entry=store.getSoldItems().values().iterator().next();long unit=entry.getPrice();
  check(entry.getItemObjId()==id && entry.getCount()==full,"The shop lists the whole stack");
  check(unit>0 && unit<PlayerBotMerchantConfig.PRICE_MINIMUM,"Each unit keeps its own value, not the per-item minimum: "+unit);
  long buyer0=buyer.cube.getKinah(),seller0=bot.cube.getKinah();
  buy(bot,buyer,0,7);
  var bought=buyer.cube.getItemsByItemId(SHARDS);
  check(bought.size()==1 && bought.get(0).getItemCount()==7 && bought.get(0).getObjectId()!=id,"Part of a stack arrives as a new item of that count");
  check(bot.cube.getItemByObjId(id).getItemCount()==full-7 && store.getSoldItems().get(id).getCount()==full-7,"The merchant's stack and listing shrink by the amount bought");
  check(buyer.cube.getKinah()==buyer0-7*unit && bot.cube.getKinah()==seller0+7*unit,"Kinah moves for each unit bought");
  int split=bought.get(0).getObjectId();
  check(scalar(c,"SELECT item_owner FROM inventory WHERE item_unique_id="+split)==BUYER && count(c,split)==7 && count(c,id)==full-7,"SQL holds both parts of the stack");
  buy(bot,buyer,0,full-7);
  check(buyer.cube.getItemByObjId(id)!=null && bot.cube.getItemByObjId(id)==null && bot.getStore()==null,"The rest of the stack sells whole and the shop closes");
  check(buyer.cube.getKinah()==buyer0-full*unit && bot.cube.getKinah()==seller0+full*unit,"The whole stack costs its units' total");
  checkpoint(bot);check(InventoryDAO.store(buyer.getDirtyItemsToUpdate(),BUYER,1,null),"Buyer relog checkpoint succeeds");
  var restored=actor(BUYER,false);InventoryDAO.loadStorage(BUYER,restored.cube);
  check(restored.cube.getItemsByItemId(SHARDS).stream().mapToLong(Item::getItemCount).sum()==full,"Reload keeps exactly the units bought");
 }
 public static void main(String[] args)throws Exception {
  try{run(args);System.exit(0);}catch(Throwable error){error.printStackTrace();System.exit(1);}
 }
 private static void run(String[] args)throws Exception {
  if(args.length!=3 || !args[0].matches("jdbc:mysql://127\\.0\\.0\\.1:33907/peddler_sale_[a-z0-9_]+\\?sslMode=DISABLED"))throw new IllegalArgumentException("Use only a fresh peddler_sale_* scratch schema on isolated port 33907");
  unsafe=(Unsafe)field(Unsafe.class,"theUnsafe").get(null);
  warnings.start();((Logger)org.slf4j.LoggerFactory.getLogger(PlayerBotRoadMerchants.class)).addAppender(warnings);
  ConfigurableProcessor.process(new Properties(),PlayerBotMerchantConfig.class,PlayerBotConfig.class,com.aionemu.gameserver.configs.network.NetworkConfig.class);
  DatabaseConfig.DATABASE_URL=args[0];DatabaseConfig.DATABASE_USER="root";DatabaseConfig.DATABASE_PASSWORD="";DatabaseConfig.DATABASE_CONNECTIONS_MAX=3;DatabaseConfig.DATABASE_TIMEOUT=5000;
  stacks=args[2].equals("stack");
  DatabaseFactory.init();try(var c=DatabaseFactory.getConnection()){schema(c,Path.of(args[1]));templates();if(stacks)stackSale(c);else sale(c,Boolean.parseBoolean(args[2]));}finally{DatabaseFactory.shutdown();}
  System.out.println("OK: "+checks+" native merchant sale checks");
 }
}
