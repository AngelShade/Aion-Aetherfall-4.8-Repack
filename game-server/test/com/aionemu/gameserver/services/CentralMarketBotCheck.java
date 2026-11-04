package com.aionemu.gameserver.services;

import java.lang.reflect.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import com.aionemu.commons.configs.DatabaseConfig;
import com.aionemu.commons.database.DatabaseFactory;
import com.aionemu.gameserver.dao.PlayerBotRecruitmentDAO;
import com.aionemu.gameserver.dao.InventoryDAO;
import com.aionemu.gameserver.dataholders.*;
import com.aionemu.gameserver.model.gameobjects.Item;
import com.aionemu.gameserver.model.gameobjects.Persistable.PersistentState;
import com.aionemu.gameserver.model.gameobjects.player.*;
import com.aionemu.gameserver.model.items.storage.*;
import com.aionemu.gameserver.model.templates.item.ItemTemplate;
import sun.misc.Unsafe;
import static com.aionemu.gameserver.services.CentralMarketService.*;

/** Bot market custody on a scratch database: args are a JDBC URL and the native schema dump. Never point this at a real save. */
public final class CentralMarketBotCheck {
 private static int checks;
 private static long next=1000;
 private static final int BOT=5001,SIBLING=5002,WORLD_A=5101,WORLD_B=5102,OWNER=10,STRANGER=20;
 private static void expect(boolean ok,String what){checks++;if(!ok)throw new AssertionError(what);}
 private static void set(Object target,String name,Object value)throws Exception {var f=target.getClass().getDeclaredField(name);f.setAccessible(true);f.set(target,value);}
 private static final class Bot extends Player {
  Storage cube,warehouse;Equipment equipment;
  Bot(){super(null,null);}
  @Override public int getObjectId(){return 5201;}
  @Override public boolean isPlayerBot(){return true;}
  @Override public Storage getInventory(){return cube;}
  @Override public Storage getStorage(int location){return location==0?cube:location==1?warehouse:null;}
  @Override public Equipment getEquipment(){return equipment;}
  @Override public List<Item> getDirtyItemsToUpdate(){return List.of();}
 }
 private static ItemTemplate template(int id,boolean stack)throws Exception {
  var t=new ItemTemplate();set(t,"itemId",id);set(t,"name","Scratch custody item");set(t,"price",100);set(t,"mask",14);set(t,"maxStackCount",stack?100:1);return t;
 }
 private static void closure(Connection c,String url)throws Exception {
  var unsafeField=Unsafe.class.getDeclaredField("theUnsafe");unsafeField.setAccessible(true);var unsafe=(Unsafe)unsafeField.get(null);
  var bot=(Bot)unsafe.allocateInstance(Bot.class);bot.equipment=new Equipment(bot);
  bot.cube=new PlayerStorage(bot,StorageType.CUBE);bot.warehouse=new PlayerStorage(bot,StorageType.REGULAR_WAREHOUSE);
  bot.cube.setLimit(0);bot.warehouse.setLimit(0);
  var data=new ItemData();var field=ItemData.class.getDeclaredField("items");field.setAccessible(true);
  @SuppressWarnings("unchecked") var templates=(Map<Integer,ItemTemplate>)field.get(data);
  templates.put(100,template(100,false));templates.put(182400001,template(182400001,true));DataManager.ITEM_DATA=data;
  var coin=new Item(90001,templates.get(182400001),100,false,0);coin.setPersistentState(PersistentState.UPDATED);bot.cube.onLoadHandler(coin);
  update(c,"INSERT INTO inventory(item_unique_id,item_id,item_count,item_owner,item_location) VALUES(90001,182400001,100,5201,0)");
  int subject=-5201;wallet(c,subject,50);update(c,"UPDATE central_market_wallet SET proceeds=1000 WHERE account_id=?",subject);
  long sale=listing(c,subject,"100:0:0",1000);update(c,"UPDATE central_market_orders SET quantity=2 WHERE id=?",sale);
  update(c,"INSERT INTO central_market_settlements(order_id,gross) VALUES(?,1000)",sale);
  long bid=order(c,subject,"100:0:0","B",900,2);update(c,"UPDATE central_market_orders SET state='QUEUED' WHERE id=?",bid);
  long purchased=order(c,subject,"100:0:0","B",1000,1);update(c,"UPDATE central_market_orders SET state='FILLED',remaining=0 WHERE id=?",purchased);
  update(c,"INSERT INTO central_market_settlements(order_id) VALUES(?)",purchased);
  update(c,"INSERT INTO inventory(item_unique_id,item_id,item_count,item_owner,item_location) VALUES(90002,100,1,?,125)",subject);
  update(c,"INSERT INTO central_market_stock(item_unique_id,account_id,variant,order_id) VALUES(90002,?,'100:0:0',?)",subject,purchased);
  DatabaseConfig.DATABASE_URL=url;DatabaseConfig.DATABASE_USER="root";DatabaseConfig.DATABASE_PASSWORD="";DatabaseConfig.DATABASE_CONNECTIONS_MAX=3;DatabaseConfig.DATABASE_TIMEOUT=5000;DatabaseFactory.init();
  try {
   synchronized(bot) {
    try{closeCompanionPosition(bot);expect(false,"Full storage must reject recycling closure");}catch(IllegalArgumentException full){expect(true,"Full private storage rejects closure");}
    expect(ledger(c,subject).kinah()==50 && ledger(c,subject).orders().stream().filter(CompanionOrder::live).count()==2,"Capacity failure rolls cancellations, collection and wallet back together");
    expect(lng(row(c,"SELECT proceeds FROM central_market_wallet WHERE account_id=?",subject),"proceeds")==1000 && bot.cube.getKinah()==100,"Capacity failure preserves proceeds and live wallet");
    bot.warehouse.setLimit(2);closeCompanionPosition(bot);
    requireCompanionClosed(c,5201);
    expect(ledger(c,subject).settled(),"Actual closure API leaves no live order, wallet or available stock");
    expect(lng(row(c,"SELECT COUNT(*) n FROM central_market_stock WHERE account_id=?",subject),"n")==0,"Closure removes all stock rows including purchased reservations");
    expect(bot.warehouse.getItems().size()==2 && bot.cube.getItems().isEmpty(),"Full inventory automatically returns both goods to private warehouse");
    expect(bot.cube.getKinah()==2600 && lng(row(c,"SELECT item_count FROM inventory WHERE item_unique_id=90001"),"item_count")==2600,"Cancelled escrow and taxed proceeds return exactly once to bot wallet");
    expect(lng(row(c,"SELECT COUNT(*) n FROM inventory WHERE item_owner=-5201 AND item_location=125"),"n")==0,"No market inventory is stranded after closure");
    closeCompanionPosition(bot);expect(bot.cube.getKinah()==2600 && bot.warehouse.getItems().size()==2,"Rechecking closure at confirm is idempotent");
    update(c,"INSERT INTO central_market_stock(item_unique_id,account_id,variant,order_id) VALUES(90003,?,'100:0:0',?)",subject,purchased);
    try{requireCompanionClosed(c,5201);expect(false,"Hidden stock must block archive");}catch(SQLException held){expect(true,"Archive refuses even reserved or orphaned stock");}
    update(c,"DELETE FROM central_market_stock WHERE item_unique_id=90003");
    // Lose the acknowledgement after the real commit: the durable rows are safe,
    // but stale native objects must never be saved over them or used for archive.
    wallet(c,subject,100);
    var factory=DatabaseFactory.class.getDeclaredField("dataSource");factory.setAccessible(true);Object pool=factory.get(null);
    var wrapped=Proxy.newProxyInstance(CentralMarketBotCheck.class.getClassLoader(),new Class<?>[]{javax.sql.DataSource.class},(proxy,m,params)->{
     Object result;
     try{result=m.invoke(pool,params);}catch(InvocationTargetException error){throw error.getCause();}
     if(!m.getName().equals("getConnection"))return result;
     Connection connection=(Connection)result;
     return Proxy.newProxyInstance(CentralMarketBotCheck.class.getClassLoader(),new Class<?>[]{Connection.class},(p,call,values)->{
      try{Object value=call.invoke(connection,values);if(call.getName().equals("commit"))throw new SQLException("Injected lost commit acknowledgement");return value;}
      catch(InvocationTargetException error){throw error.getCause();}
     });
    });
    factory.set(null,wrapped);
    try {
     try{closeCompanionPosition(bot);expect(false,"Ambiguous commit must hold custody");}catch(CompanionHeld held){expect(true,"Lost commit acknowledgement quarantines custody");}
    }finally{factory.set(null,pool);}
    expect(bot.cube.getKinah()==2600 && lng(row(c,"SELECT item_count FROM inventory WHERE item_unique_id=90001"),"item_count")==2700,"Crash boundary leaves committed native funds intact without stale projection");
    c.setAutoCommit(false);
    try{InventoryDAO.storeCompanionInventory(c,bot);expect(false,"Quarantined actor must not overwrite committed rows");}catch(SQLException held){expect(true,"Quarantine blocks stale inventory saves until fresh native load");}finally{c.rollback();c.setAutoCommit(true);}
    requireCompanionClosed(c,5201);
   }
  }finally{DatabaseFactory.shutdown();}
 }
 private static Method method(Class<?> owner,String name,Class<?>... args)throws Exception {var m=owner.getDeclaredMethod(name,args);m.setAccessible(true);return m;}
 private static void match(Connection c,String key)throws Exception {method(CentralMarketService.class,"fill",Connection.class,String.class,Map.class,List.class).invoke(null,c,key,row(c,"SELECT item_id,floor_price,ceiling_price FROM central_market_catalog WHERE variant=?",key),new ArrayList<Integer>());}
 private static void cancel(Connection c,int subject,long order)throws Exception {method(CentralMarketService.class,"cancel",Connection.class,int.class,long.class).invoke(null,c,subject,order);}
 private static void wallet(Connection c,int subject,long kinah)throws Exception {update(c,"INSERT INTO central_market_wallet(account_id,kinah) VALUES(?,?) ON DUPLICATE KEY UPDATE kinah=VALUES(kinah),proceeds=0",subject,kinah);}
 private static long order(Connection c,int subject,String key,String side,long price,long qty)throws Exception {
  long id=next++;update(c,"INSERT INTO central_market_orders(id,account_id,variant,side,price,quantity,remaining,state,created_at,available_at) VALUES(?,?,?,?,?,?,?,'OPEN',1,1)",id,subject,key,side,price,qty,qty);return id;
 }
 private static long listing(Connection c,int subject,String key,long price)throws Exception {
  long id=order(c,subject,key,"S",price,1);int item=(int)next++;
  update(c,"INSERT INTO inventory(item_unique_id,item_id,item_count,item_owner,is_equipped,slot,item_location) VALUES(?,100,1,?,0,65535,125)",item,subject);
  update(c,"INSERT INTO central_market_stock(item_unique_id,account_id,variant,order_id) VALUES(?,?,?,?)",item,subject,key,id);return id;
 }
 private static long trades(Connection c)throws Exception {return lng(row(c,"SELECT COUNT(*) n FROM central_market_trades"),"n");}
 private static void starterAndHelpRollback(Connection c)throws Exception {
  c.setAutoCommit(false);
  var grant=PlayerBotRecruitmentDAO.starter(c,90,50);c.commit();
  expect(!grant.replayed() && PlayerBotRecruitmentDAO.wallet(c,90).tickets()==50,"Starter grants fifty once to an account on scratch data");
  grant=PlayerBotRecruitmentDAO.starter(c,90,50);c.commit();
  expect(grant.replayed() && PlayerBotRecruitmentDAO.wallet(c,90).tickets()==50,"Starter relog replay grants nothing again");
  PlayerBotRecruitmentDAO.starter(c,90,500);c.commit();
  expect(PlayerBotRecruitmentDAO.wallet(c,90).tickets()==50,"Raising starter configuration does not repay old accounts");
  PlayerBotRecruitmentDAO.starter(c,91,50);c.rollback();
  expect(PlayerBotRecruitmentDAO.wallet(c,91).tickets()==0,"Failed starter transaction retains neither credit nor its receipt");c.rollback();
  for(String request:List.of("rollback-a","rollback-b","rollback-c"))PlayerBotRecruitmentDAO.helped(c,92,request,3,1);
  c.rollback();
  expect(PlayerBotRecruitmentDAO.wallet(c,92).tickets()==0,"Failed help contract rolls its ticket and request credits back together");c.rollback();
  for(String request:List.of("rollback-a","rollback-b","rollback-c"))PlayerBotRecruitmentDAO.helped(c,92,request,3,1);
  c.commit();
  expect(PlayerBotRecruitmentDAO.wallet(c,92).tickets()==1,"Rolled-back help requests can retry to one legitimate ticket");
  expect(PlayerBotRecruitmentDAO.helped(c,92,"rollback-c",3,1)==0 && PlayerBotRecruitmentDAO.wallet(c,92).tickets()==1,"Replayed help delivery does not pay twice");c.commit();c.setAutoCommit(true);
 }
 private static void priceEvidence(Connection c)throws Exception {
  long now=System.currentTimeMillis();String key="200:0:0";
  for(String variant:List.of(key,"200:1:0"))update(c,"INSERT INTO central_market_catalog(variant,item_id,enchant,tempering,base_price,floor_price,ceiling_price,previous_price) VALUES(?,200,0,0,1000,900,1100,1000)",variant);
  order(c,-BOT,key,"S",900,1);order(c,-SIBLING,key,"S",900,1);order(c,OWNER,key,"B",1100,1);
  order(c,0,key,"S",905,10);order(c,0,key,"B",1000,10);
  for(int party:List.of(30,40,50)) {
   order(c,party,key,"S",910+party,1);order(c,party,key,"B",900+party,1);
   update(c,"INSERT INTO central_market_trades(variant,buyer_account,seller_account,quantity,unit_price,buy_order,sell_order,traded_at) VALUES(?,?,?,1,960,0,0,?)",key,party+100,party,now);
  }
  for(int[] pair:new int[][]{{0,30},{40,0},{OWNER,30},{30,-SIBLING},{40,40}})
   update(c,"INSERT INTO central_market_trades(variant,buyer_account,seller_account,quantity,unit_price,buy_order,sell_order,traded_at) VALUES(?,?,?,1,1,0,0,?)",key,pair[0],pair[1],now);
  update(c,"INSERT INTO central_market_trades(variant,buyer_account,seller_account,quantity,unit_price,buy_order,sell_order,traded_at) VALUES('200:1:0',999,998,1,1,0,0,?)",now);
  var quote=companionQuote(c,-BOT,key,now);
  expect(quote.trades().size()==3 && quote.trades().stream().allMatch(p->!p.simulated() && p.price()==960),"Price evidence excludes simulated, own-owner, self and modified-variant trades");
  expect(quote.asks().stream().anyMatch(CompanionPrice::simulated) && quote.asks().stream().noneMatch(p->p.owner()==OWNER),"Simulated asks remain executable but owner and sibling asks are excluded");
  expect(quote.bids().stream().noneMatch(p->p.owner()==OWNER),"Book evidence excludes human owner's bids");
  for(int n=0;n<60;n++) {
   order(c,1000+n,key,"S",1000+n,1);order(c,2000+n,key,"B",1000-n,1);
   update(c,"INSERT INTO central_market_trades(variant,buyer_account,seller_account,quantity,unit_price,buy_order,sell_order,traded_at) VALUES(?,?,?,1,1000,0,0,?)",key,3000+n,4000+n,now+n+1);
  }
  quote=companionQuote(c,-BOT,key,now+60);
  expect(quote.trades().size()<=32 && quote.asks().size()<=16 && quote.bids().size()<=16,"Dense market evidence stays inside fixed trade and order windows");
 }
 private static String table(String all,String name) {
  int start=all.indexOf("CREATE TABLE `"+name+"`");return all.substring(start,all.indexOf(";",all.indexOf("ENGINE=",start)));
 }
 private static void open(Connection c,Path dump)throws Exception {
  try(var s=c.createStatement()) {
   String all=Files.readString(dump);
   s.execute(table(all,"inventory"));s.execute(table(all,"item_stones"));
   // Only native ID allocation reads these unrelated owners in this fixture.
   s.execute("CREATE TABLE player_registered_items(item_unique_id INT PRIMARY KEY)");
   s.execute("CREATE TABLE mail(mail_unique_id INT PRIMARY KEY)");s.execute("CREATE TABLE guides(guide_id INT PRIMARY KEY)");
   for(String name:List.of("legions","houses","player_pets"))s.execute("CREATE TABLE "+name+"(id INT PRIMARY KEY)");
   s.execute("CREATE TABLE players(id INT PRIMARY KEY,account_id INT NOT NULL)");
   for(String statement:Files.readString(Path.of("config/central-market/schema.sql")).split(";"))if(!statement.isBlank())s.execute(statement);
   s.execute("INSERT INTO players VALUES("+BOT+","+OWNER+"),("+SIBLING+","+OWNER+"),("+WORLD_A+",-1000000),("+WORLD_B+",-1000000)");
   s.execute("INSERT INTO central_market_catalog(variant,item_id,enchant,tempering,base_price,floor_price,ceiling_price,previous_price) VALUES('100:0:0',100,0,0,1000,900,1100,1000)");
  }
 }
 public static void main(String[] args)throws Exception {
  if(!args[0].startsWith("jdbc:mysql://127.0.0.1:33907/"))throw new IllegalArgumentException("Use the isolated scratch server on port 33907 only");
  if(args.length==2 && args[1].equals("help-only")) {
   try(var c=DriverManager.getConnection(args[0],"root","")){helpRequests(c);}
   System.out.println("OK: "+checks+" durable help SQL checks on isolated port 33907");return;
  }
  Class.forName("com.mysql.cj.jdbc.Driver");
  try(var c=DriverManager.getConnection(args[0],"root","")) {
   open(c,Path.of(args[1]));
   String key="100:0:0";int bot=-BOT,sibling=-SIBLING,worldA=-WORLD_A,worldB=-WORLD_B;
   for(int s:List.of(bot,sibling,worldA,worldB,OWNER,STRANGER))wallet(c,s,1000000);

   long sale=listing(c,bot,key,1000);order(c,OWNER,key,"B",1000,1);match(c,key);
   expect(trades(c)==0,"A human cannot buy from their own bot");
   order(c,sibling,key,"B",1000,1);match(c,key);
   expect(trades(c)==0,"A bot cannot buy from a sibling with the same owner");
   long bid=order(c,STRANGER,key,"B",1000,1);match(c,key);
   expect(trades(c)==1,"A stranger buys from the bot");
   var ledger=ledger(c,bot);
   expect(ledger.orders().size()==1 && ledger.orders().getFirst().gross()==1000,"Proceeds wait on the bot's own sale");
   expect(lng(row(c,"SELECT proceeds FROM central_market_wallet WHERE account_id=?",OWNER),"proceeds")==0,"The owner receives no bot proceeds");
   expect(lng(row(c,"SELECT item_owner FROM inventory i JOIN central_market_stock s USING(item_unique_id) WHERE s.order_id=?",bid),"item_owner")==STRANGER,"The sold item moved to the buyer");
   CentralMarketSettlement.collect(c,bot,sale,6500);
   expect(lng(row(c,"SELECT kinah FROM central_market_wallet WHERE account_id=?",bot),"kinah")==1000650,"The bot collects its taxed proceeds");
   expect(ledger(c,bot).orders().isEmpty(),"A collected sale leaves the ledger");
   try{CentralMarketSettlement.collect(c,bot,sale,6500);expect(false,"Second collection");}catch(IllegalArgumentException once){expect(true,"Proceeds collect once");}

   update(c,"DELETE FROM central_market_orders WHERE account_id=? AND side='B' AND state='OPEN'",OWNER);
   update(c,"DELETE FROM central_market_orders WHERE account_id=? AND side='B' AND state='OPEN'",sibling);
   wallet(c,bot,1000);
   long buy=order(c,bot,key,"B",1000,1);update(c,"UPDATE central_market_wallet SET kinah=0 WHERE account_id=?",bot);
   listing(c,STRANGER,key,1000);match(c,key);
   expect(trades(c)==2,"The bot buys from a stranger");
   ledger=ledger(c,bot);
   expect(ledger.orders().getFirst().uncollected()==1,"The purchase waits for collection");
   CentralMarketSettlement.collect(c,bot,buy,6500);
   ledger=ledger(c,bot);
   expect(ledger.orders().isEmpty() && ledger.stock().size()==1 && ledger.stock().getFirst().itemId()==100,"Collected goods wait in the bot's market stock");
   expect(lng(row(c,"SELECT item_owner FROM inventory WHERE item_unique_id=?",ledger.stock().getFirst().object()),"item_owner")==bot,"The bought item is held by the bot's subject, not the human account");

   long open=order(c,bot,key,"B",900,2);update(c,"UPDATE central_market_wallet SET kinah=0 WHERE account_id=?",bot);
   cancel(c,bot,open);
   expect(ledger(c,bot).kinah()==1800,"Cancelling returns the buy escrow");
   long unsold=listing(c,bot,key,1100);cancel(c,bot,unsold);
   expect(ledger(c,bot).stock().size()==2,"Cancelling returns unsold goods to stock");
   expect(!ledger(c,bot).settled(),"A bot with goods or Kinah inside is not settled");

   listing(c,worldA,key,1000);order(c,worldB,key,"B",1000,1);match(c,key);
   expect(trades(c)==3,"Two world bots on one server account are separate parties");

   PlayerBotRecruitmentDAO.prepare(c);c.setAutoCommit(false);
   expect(PlayerBotRecruitmentDAO.helped(c,OWNER,"a",3,1)==1,"First request counts");
   expect(PlayerBotRecruitmentDAO.helped(c,OWNER,"a",3,1)==0,"A repeated request does not count");
   expect(PlayerBotRecruitmentDAO.helped(c,OWNER,"b",3,1)==2 && PlayerBotRecruitmentDAO.wallet(c,OWNER).tickets()==0,"Two requests pay nothing");
   expect(PlayerBotRecruitmentDAO.helped(c,OWNER,"c",3,1)==3 && PlayerBotRecruitmentDAO.wallet(c,OWNER).tickets()==1,"The third request pays one ticket");
   expect(PlayerBotRecruitmentDAO.helped(c,OWNER,"d",3,1)==4 && PlayerBotRecruitmentDAO.wallet(c,OWNER).tickets()==1,"The fourth starts the next contract");
   c.commit();
   c.setAutoCommit(true);starterAndHelpRollback(c);priceEvidence(c);closure(c,args[0]);
  }
  System.out.println("OK: "+checks+" bot market and help contract checks");
 }

 /** Repository acceptance/claim transactions without world actors or the market fixture. */
 private static void helpRequests(Connection c)throws Exception {
  var account=8001;
  com.aionemu.gameserver.dao.PlayerBotHelpDAO.prepare(c);PlayerBotRecruitmentDAO.prepare(c);c.setAutoCommit(false);
  var first=new com.aionemu.gameserver.dao.PlayerBotHelpDAO.Row("help-sql-1",account,9001,9101,0,"ACTIVE","{\"count\":0}");
  var accepted=com.aionemu.gameserver.dao.PlayerBotHelpDAO.accept(c,first);c.commit();
  expect(com.aionemu.gameserver.dao.PlayerBotHelpDAO.current(c,account).key().equals(first.key()),"Acceptance survives a new SQL read");
  expect(com.aionemu.gameserver.dao.PlayerBotHelpDAO.accept(c,first).equals(accepted),"Reopening returns the same accepted row");c.commit();
  try{com.aionemu.gameserver.dao.PlayerBotHelpDAO.accept(c,new com.aionemu.gameserver.dao.PlayerBotHelpDAO.Row("help-sql-other",account,9002,9102,0,"ACTIVE","{}"));throw new AssertionError("Second active account request");}catch(IllegalArgumentException expected){expect(true,"One active request per account");c.rollback();}
  try{com.aionemu.gameserver.dao.PlayerBotHelpDAO.accept(c,new com.aionemu.gameserver.dao.PlayerBotHelpDAO.Row("help-sql-busy",8002,9002,9101,0,"ACTIVE","{}"));throw new AssertionError("Second requester binding");}catch(IllegalArgumentException expected){expect(true,"One accepted human per requesting bot");c.rollback();}
  try{com.aionemu.gameserver.dao.PlayerBotHelpDAO.close(c,account,first.key(),true,3,1);throw new AssertionError("Claim before checkpoint");}catch(IllegalArgumentException expected){expect(true,"Unconfirmed completion cannot be claimed");c.rollback();}
  com.aionemu.gameserver.dao.PlayerBotHelpDAO.store(c,accepted,"ACTIVE","{\"count\":2}");c.rollback();
  expect(com.aionemu.gameserver.dao.PlayerBotHelpDAO.current(c,account).payload().equals(first.payload()),"Native checkpoint rollback rolls back help counters");c.commit();
  var progress=com.aionemu.gameserver.dao.PlayerBotHelpDAO.store(c,accepted,"ACTIVE","{\"count\":2}");c.commit();
  expect(progress.revision()==1 && com.aionemu.gameserver.dao.PlayerBotHelpDAO.current(c,account).payload().contains(":2"),"Committed progress and revision persist");c.commit();
  var recovered=com.aionemu.gameserver.dao.PlayerBotHelpDAO.store(c,accepted,"ACTIVE","{\"count\":3}");c.commit();
  expect(recovered.equals(progress),"An uncertain earlier commit is adopted without overwriting it");
  var completed=com.aionemu.gameserver.dao.PlayerBotHelpDAO.store(c,progress,"COMPLETE","{\"count\":3}");c.commit();
  com.aionemu.gameserver.dao.PlayerBotHelpDAO.close(c,account,first.key(),true,3,1);c.rollback();
  expect(com.aionemu.gameserver.dao.PlayerBotHelpDAO.current(c,account).status().equals("COMPLETE"),"Claim rollback retains the entitlement");
  try(var s=c.prepareStatement("SELECT COUNT(*) FROM playerbot_ticket_help WHERE account_id=?")){s.setInt(1,account);try(var r=s.executeQuery()){r.next();expect(r.getInt(1)==0,"Claim rollback retains the existing help counter");}}c.commit();
  var claimed=com.aionemu.gameserver.dao.PlayerBotHelpDAO.close(c,account,completed.key(),true,3,1);c.commit();
  expect(claimed.status().equals("CLAIMED") && com.aionemu.gameserver.dao.PlayerBotHelpDAO.current(c,account)==null,"Claim releases the active slot");c.commit();
  com.aionemu.gameserver.dao.PlayerBotHelpDAO.close(c,account,first.key(),true,3,1);c.commit();
  for(int i=2;i<=3;i++) {
   var row=new com.aionemu.gameserver.dao.PlayerBotHelpDAO.Row("help-sql-"+i,account,9001,9100+i,0,"ACTIVE","{}");
   row=com.aionemu.gameserver.dao.PlayerBotHelpDAO.accept(c,row);row=com.aionemu.gameserver.dao.PlayerBotHelpDAO.store(c,row,"COMPLETE","{}");
   com.aionemu.gameserver.dao.PlayerBotHelpDAO.close(c,account,row.key(),true,3,1);c.commit();
  }
  expect(PlayerBotRecruitmentDAO.wallet(c,account).tickets()==1,"Three distinct collected requests pay the existing one ticket");
  try(var s=c.prepareStatement("SELECT COUNT(*) FROM playerbot_ticket_help WHERE account_id=?")){s.setInt(1,account);try(var r=s.executeQuery()){r.next();expect(r.getInt(1)==3,"A repeated collect did not count twice");}}c.commit();
  var partial=new com.aionemu.gameserver.dao.PlayerBotHelpDAO.Row("help-sql-partial",account,9001,9200,0,"ACTIVE","{\"count\":2}");
  com.aionemu.gameserver.dao.PlayerBotHelpDAO.accept(c,partial);com.aionemu.gameserver.dao.PlayerBotHelpDAO.close(c,account,partial.key(),false,3,1);c.commit();
  var abandoned=com.aionemu.gameserver.dao.PlayerBotHelpDAO.latest(c,account);
  expect(abandoned.status().equals("ABANDONED") && abandoned.payload().equals(partial.payload()),"Abandonment retains prior committed credit");c.commit();
  try{com.aionemu.gameserver.dao.PlayerBotHelpDAO.accept(c,partial);throw new AssertionError("Recreated abandoned need");}catch(IllegalArgumentException expected){expect(true,"Reopening never creates a second objective for the same need");c.rollback();}
 }
}
