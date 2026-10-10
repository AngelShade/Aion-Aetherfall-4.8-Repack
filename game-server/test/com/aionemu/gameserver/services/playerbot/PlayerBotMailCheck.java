package com.aionemu.gameserver.services.playerbot;

import java.sql.*;
import java.util.*;
import com.aionemu.gameserver.configs.main.PlayerBotConfig;
import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.model.gameobjects.*;
import com.aionemu.gameserver.model.gameobjects.Persistable.PersistentState;
import com.aionemu.gameserver.model.gameobjects.player.*;
import com.aionemu.gameserver.model.items.ItemMask;
import com.aionemu.gameserver.model.items.storage.*;
import com.aionemu.gameserver.model.templates.item.*;
import com.aionemu.gameserver.services.mail.MailService;
import sun.misc.Unsafe;

/** Production native mail planning/projection and SQL custody; synthetic actors/JDBC only. */
public final class PlayerBotMailCheck {
 static int checks;static void check(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
 static class Actor extends PlayerBotWarehouseCheck.Actor {
  Mailbox mailbox;PlayerCommonData common;
  @Override public Mailbox getMailbox(){return mailbox;}
  @Override public void setMailbox(Mailbox value){mailbox=value;}
  @Override public PlayerCommonData getCommonData(){return common;}
  @Override public Race getRace(){return Race.ELYOS;}
  @Override public BlockList getBlockList(){return new BlockList(Map.of());}
  @Override public boolean isStaff(){return false;}
 }
 static Actor actor(int id,boolean bot)throws Exception {
  var a=(Actor)PlayerBotTradeCheck.unsafe.allocateInstance(Actor.class);a.id=id;a.owner=1;a.companion=bot;a.account=PlayerBotTradeCheck.actor(1,false).account;
  a.cube=new PlayerStorage(null,StorageType.CUBE);a.warehouse=new PlayerStorage(null,StorageType.REGULAR_WAREHOUSE);a.mailbox=new Mailbox(a);a.common=new PlayerCommonData(id);a.common.setName("Mailbot"+id);
  a.tasks=new PlayerBotTradeCheck.Tasks();var wallet=item(900+id,182400001,1000,true);a.cube.onLoadHandler(wallet);return a;
 }
 static Item item(int id,int tid,long count,boolean trade) {
  Item item=new Item(id,new ItemTemplate(){@Override public int getTemplateId(){return tid;}@Override public boolean isKinah(){return tid==182400001;}@Override public long getPrice(){return 100;}@Override public ItemQuality getItemQuality(){return ItemQuality.COMMON;}},count,false,0){@Override public int getItemMask(){return trade?ItemMask.TRADEABLE:0;}};
  item.setItemLocation(0);item.setEquipmentSlot(3);item.setPersistentState(PersistentState.UPDATED);return item;
 }
 static Letter letter(Actor bot,int id,Item item,long money){var l=new Letter(id,bot.id,item,money,"Title","Body","Sender",new Timestamp(1000),true,LetterType.NORMAL);l.setPersistentState(PersistentState.UPDATED);bot.mailbox.putLetterToMailbox(l);return l;}
 static class Jdbc extends PlayerBotWarehouseCheck.Jdbc {
  Object[] mail;int recipient=1,count;boolean wrongRace,missingRecipient;List<Object[]> loaded=new ArrayList<>();
  @Override PreparedStatement statement(String sql) {
   if(!sql.startsWith("SELECT mail_recipient") && !sql.startsWith("SELECT id,race") && !sql.startsWith("SELECT COUNT") && !sql.startsWith("SELECT mail_unique"))return super.statement(sql);
   return PlayerBotTradeCheck.proxy(PreparedStatement.class,(p,m,args)->{
    if(!m.getName().equals("executeQuery"))return PlayerBotTradeCheck.zero(m.getReturnType());
    List<Object[]> result=sql.startsWith("SELECT mail_recipient")?(mail==null?List.of():Collections.singletonList(mail)):
     sql.startsWith("SELECT id,race")?(missingRecipient?List.of():Collections.singletonList(new Object[]{recipient,wrongRace?"ASMODIANS":"ELYOS"})):
     sql.startsWith("SELECT COUNT")?Collections.singletonList(new Object[]{count}):loaded;
    int[] index={-1};return PlayerBotTradeCheck.proxy(ResultSet.class,(r,rm,ra)->{
     if(rm.getName().equals("next"))return ++index[0]<result.size();
     if(Set.of("getInt","getLong","getBoolean","getString").contains(rm.getName())) {
      Object value=result.get(index[0])[(Integer)ra[0]-1];
      return switch(rm.getName()){case "getInt"->((Number)value).intValue();case "getLong"->((Number)value).longValue();case "getBoolean"->value instanceof Boolean b?b:((Number)value).longValue()!=0;default->value.toString();};
     }return PlayerBotTradeCheck.zero(rm.getReturnType());
    });
   });
  }
  void own(Actor a,Item item){rows.put(item.getObjectId(),new long[]{a.id,item.getItemId(),item.getItemLocation(),item.getItemCount(),item.isEquipped()?1:0,item.getEquipmentSlot()});}
  void mailbox(Letter l){mail=new Object[]{l.getRecipientId(),l.getAttachedItem()==null?0:l.getAttachedItem().getObjectId(),l.getAttachedKinah(),l.isUnread(),l.getLetterType().getId()};}
 }
 static void refuses(Runnable operation,String fragment){try{operation.run();throw new AssertionError("Accepted "+fragment);}catch(IllegalArgumentException e){check(e.getMessage().contains(fragment),"Planning refusal: "+e.getMessage());}}
 static void sqlRefuses(Jdbc db,PlayerBotMail.Request r,String fragment)throws Exception {try{PlayerBotMailStore.transfer(db.connection(),r);throw new AssertionError("Accepted "+fragment);}catch(SQLException e){check(e.getMessage().contains(fragment),"SQL refuses "+fragment+": "+e);}}
 public static void main(String[] args)throws Exception {
  var f=Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);PlayerBotTradeCheck.unsafe=(Unsafe)f.get(null);PlayerBotConfig.ENABLED=true;
  Actor owner=actor(1,false),bot=actor(2,true);Item item=item(500,112000001,17,true);item.setEnchantLevel(9);bot.cube.onLoadHandler(item);
  check(PlayerBotMail.sendable(item),"Whole tradeable cube stack allowed");
  check(!PlayerBotMail.sendable(null) && !PlayerBotMail.sendable(bot.cube.getKinahItem()),"No phantom/currency item request");
  for(int location:List.of(1,2,3,125,127,32)){item.setItemLocation(location);check(!PlayerBotMail.sendable(item),"No shared/stored/mail/equipment source");}item.setItemLocation(0);
  item.setEquipped(true);check(!PlayerBotMail.sendable(item),"Equipped item refused");item.setEquipped(false);
  item.setPackCount(2);check(!PlayerBotMail.sendable(item),"Packed bypass is not a free gift");item.setPackCount(0);
  check(!PlayerBotMail.sendable(item(501,112000002,1,false)),"Soulbound/untradeable item refused");
  check(MailService.companionMailBaseCost(item,0)==44,"Native normal quality/whole-stack/base postage quote");
  check(MailService.companionMailBaseCost(null,1000)==20,"Native 1 percent currency commission");
  refuses(()->MailService.companionMailBaseCost(null,-1),"Invalid");
  refuses(()->PlayerBotMail.plan(bot,owner,"senditem",999,0,777),"tradeable");
  refuses(()->PlayerBotMail.plan(bot,owner,"sendkinah",0,-1,777),"positive");
  PlayerBotConfig.ENABLED=false;refuses(()->PlayerBotMail.plan(bot,owner,"sendkinah",0,1,777),"disabled");PlayerBotConfig.ENABLED=true;
  bot.world=true;refuses(()->PlayerBotMail.plan(bot,owner,"read",123,0,0),"own companion");bot.world=false;
  bot.owner=999;refuses(()->PlayerBotMail.plan(bot,owner,"take",123,0,0),"own companion");bot.owner=1;
  // Outbound SQL commits one exact original object and charges sender's wallet in that transaction.
  var outgoing=new Letter(777,owner.id,item,0,"Item(s) you asked for","Request",bot.getName(),new Timestamp(1000),true,LetterType.NORMAL);
  var send=new PlayerBotMail.Request(bot,owner,"senditem",outgoing,item,(short)0,0,44,1000,false);
  Jdbc db=new Jdbc();db.own(bot,item);db.own(bot,bot.cube.getKinahItem());PlayerBotMailStore.transfer(db.connection(),send);
  check(db.writes.size()==4,"Send wallet, ownership, native mail and recipient counter commit together");
  check(db.writes.get(1).contains("item_location=127") && db.writes.get(1).contains("1=1") && db.writes.get(1).contains("2=500"),"Original whole object assigned to owner mailbox; no split/new item");
  check(item.getItemLocation()==0 && item.getItemCount()==17 && item.getEnchantLevel()==9 && bot.cube.getKinah()==1000,"SQL stage publishes no native mutation or lost metadata");
  for(int column=0;column<6;column++){db=new Jdbc();db.own(bot,item);db.own(bot,bot.cube.getKinahItem());db.rows.get(500)[column]++;sqlRefuses(db,send,"custody/count/slot");check(db.writes.isEmpty(),"Source mismatch before any writes");}
  db=new Jdbc();db.count=100;sqlRefuses(db,send,"recipient full");check(db.writes.isEmpty(),"Actual durable capacity refuses before writes");
  db=new Jdbc();db.wrongRace=true;sqlRefuses(db,send,"recipient changed");db=new Jdbc();db.missingRecipient=true;sqlRefuses(db,send,"recipient changed");
  db=new Jdbc();db.autoCommit=true;sqlRefuses(db,send,"exact private");
  var moneyLetter=new Letter(778,1,null,100,"Money you asked for","Body",bot.getName(),new Timestamp(1000),true,LetterType.NORMAL);
  var sendMoney=new PlayerBotMail.Request(bot,owner,"sendkinah",moneyLetter,null,(short)0,100,111,1000,false);
  db=new Jdbc();db.own(bot,bot.cube.getKinahItem());PlayerBotMailStore.transfer(db.connection(),sendMoney);
  check(db.writes.size()==3 && db.writes.getFirst().contains("1=889") && db.writes.get(1).contains("8=100"),"Requested money/postage is debited, attached only once");
  // Incoming native object and Kinah are handled atomically, with the letter retained.
  bot.cube.remove(item);item.setItemLocation(127);item.setPersistentState(PersistentState.UPDATED);var incoming=letter(bot,888,item,35);
  var take=PlayerBotMail.plan(bot,owner,"take",888,0,0);check(take.item()==item && take.money()==35 && take.slot()==0,"Both attachments use one exact plan");
  db=new Jdbc();db.mailbox(incoming);db.own(bot,item);db.own(bot,bot.cube.getKinahItem());PlayerBotMailStore.transfer(db.connection(),take);
  check(db.writes.size()==3 && db.writes.get(1).contains("attached_item_id=0,attached_kinah_count=0"),"Wallet, letter clear, item transfer share transaction");
  check(incoming.getAttachedItem()==item && incoming.getAttachedKinah()==35 && bot.cube.getItemByObjId(500)==null,"No premature attachment publication");
  for(int column=0;column<5;column++){db=new Jdbc();db.mailbox(incoming);db.mail[column]=column==3?false:column==4?1:((Number)db.mail[column]).longValue()+1;db.own(bot,item);db.own(bot,bot.cube.getKinahItem());sqlRefuses(db,take,"custody/attachment changed");check(db.writes.isEmpty(),"Letter owner/item/money/unread/type stale state cannot duplicate");}
  db=new Jdbc();db.mailbox(incoming);db.occupied=true;db.own(bot,item);db.own(bot,bot.cube.getKinahItem());sqlRefuses(db,take,"destination slot changed");check(db.writes.isEmpty(),"Full/stale destination does not consume mail");
  refuses(()->PlayerBotMail.plan(bot,owner,"delete",888,0,0),"Collect");
  PlayerBotMail.project(take);
  check(bot.cube.getItemByObjId(500)==item && item.getItemCount()==17 && item.getEnchantLevel()==9 && item.getItemLocation()==0,"Native whole-stack receive keeps original ID and attributes");
  check(bot.cube.getKinah()==1035 && incoming.getAttachedItem()==null && incoming.getAttachedKinah()==0 && !incoming.isUnread(),"Both attachments projected once; no letter auto-deletion");
  refuses(()->PlayerBotMail.plan(bot,owner,"take",888,0,0),"no attachments");
  var delete=PlayerBotMail.plan(bot,owner,"delete",888,0,0);db=new Jdbc();db.mailbox(incoming);PlayerBotMailStore.transfer(db.connection(),delete);
  check(db.writes.getFirst().startsWith("DELETE FROM mail") && db.writes.getFirst().contains("attached_item_id=0 AND attached_kinah_count=0"),"Only empty letters may be deleted");
  var text=letter(bot,889,null,0);var read=PlayerBotMail.plan(bot,owner,"read",889,0,0);db=new Jdbc();db.mailbox(text);PlayerBotMailStore.transfer(db.connection(),read);PlayerBotMail.project(read);
  check(!text.isUnread() && text.getPersistentState()==PersistentState.UPDATED,"Read state is durable before publication");
  db=new Jdbc();db.mailbox(text);PlayerBotMailStore.transfer(db.connection(),PlayerBotMail.plan(bot,owner,"read",889,0,0));check(db.writes.isEmpty(),"Repeated read is idempotent with changed-row JDBC drivers");
  var large=letter(bot,890,null,Long.MAX_VALUE);refuses(()->PlayerBotMail.plan(bot,owner,"take",890,0,0),"overflow");
  var invalid=letter(bot,891,null,-1);refuses(()->PlayerBotMail.plan(bot,owner,"take",891,0,0),"Invalid");
  refuses(()->PlayerBotMail.plan(bot,owner,"take",9999,0,0),"current letter");
  // Load verification includes overflow; never accept a swallowed native DAO error as an empty mailbox.
  Actor loaded=actor(4,true);var loadLetter=letter(loaded,999,null,10);db=new Jdbc();db.loaded.add(new Object[]{999,4,0,10,true,0});PlayerBotMailStore.verifyLoaded(db.connection(),loaded,loaded.mailbox);check(true,"Exact complete native letter load accepted");
  db=new Jdbc();try{PlayerBotMailStore.verifyLoaded(db.connection(),loaded,loaded.mailbox);throw new AssertionError("ghost letter");}catch(SQLException e){check(e.getMessage().contains("Unexpected"),"Loaded ghost letter cannot be published");}
  loaded.mailbox=new Mailbox(loaded);db.loaded.add(new Object[]{999,4,0,10,true,0});try{PlayerBotMailStore.verifyLoaded(db.connection(),loaded,loaded.mailbox);throw new AssertionError("swallowed load");}catch(SQLException e){check(e.getMessage().contains("Incomplete"),"Native load failure cannot erase durable mail");}
  for(int n=0;n<101;n++)letter(loaded,2000+n,null,0);check(loaded.mailbox.getLetters().size()==100 && loaded.mailbox.getAllLetters().size()==101,"Native overflow is preserved and verified without exposing another character");
  var session=PlayerBotWarehouseCheck.session(owner,bot);var snapshot=new HashMap<String,Object>();PlayerBotMail.snapshot(session,snapshot);check(((List<?>)snapshot.get("mail")).size()==4 && !(Boolean)snapshot.get("mailAvailable"),"Owned read-only mailbox shown outside postal range without SQL");
  bot.world=true;snapshot.clear();PlayerBotMail.snapshot(session,snapshot);check(((List<?>)snapshot.get("mail")).isEmpty() && !(Boolean)snapshot.get("mailLoaded"),"Invitation cannot reveal world-bot mail");bot.world=false;
  for(int failure=0;failure<5;failure++){Jdbc jdbc=new Jdbc();jdbc.commitFails=failure==2;jdbc.rollbackFails=failure==3;final int step=failure;
   try{PlayerBotMailStore.transaction(jdbc.connection(),()->{jdbc.events.add("write");if(step==0 || step==3)throw new SQLException("write");if(step==1)throw new IllegalArgumentException("runtime");},()->{jdbc.events.add("publish");if(step==4)throw new IllegalStateException("projection");});throw new AssertionError("Accepted failure");}
   catch(PlayerBotMailStore.Held e){check(failure>=2,"Ambiguous commit/rollback or postcommit projection holds custody");}
   catch(SQLException|IllegalArgumentException e){check(failure<2,"Acknowledged precommit rollback is retryable");}
   check(jdbc.events.contains("publish")== (failure==4),"No publication before commit acknowledgement");check(jdbc.events.contains("rollback")== (failure!=4),"Postcommit projection failure is never described as rollback");
  }
  Jdbc ordered=new Jdbc();PlayerBotMailStore.transaction(ordered.connection(),()->ordered.events.add("write"),()->ordered.events.add("publish"));check(ordered.events.equals(List.of("write","commit","publish")),"Native publication follows acknowledged transaction");
  System.out.println("OK: "+checks+" mail planning, native projection, private SQL custody and failure-boundary checks; no World/DB/IDs");
 }
}
