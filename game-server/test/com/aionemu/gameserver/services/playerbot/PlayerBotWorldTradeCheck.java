package com.aionemu.gameserver.services.playerbot;

import java.sql.SQLException;
import java.util.List;
import com.aionemu.gameserver.model.trade.*;
import com.aionemu.gameserver.model.gameobjects.Persistable.PersistentState;
import sun.misc.Unsafe;

/** Executes the production priced-custody transaction against controlled JDBC, never a database/world/ID allocator. */
public final class PlayerBotWorldTradeCheck {
 static int checks;
 static void check(boolean b,String message){checks++;if(!b)throw new AssertionError(message);}
 static void refuses(PlayerBotTradeCheck.Jdbc db,PlayerBotTradeCheck.Actor seller,PlayerBotTradeCheck.Actor buyer,Exchange goods,long price,String why)throws Exception{
  try{PlayerBotTradeStore.purchaseTransfer(db.connection(),seller,buyer,goods,price);throw new AssertionError("Accepted "+why);}
  catch(SQLException e){check(e.getMessage().contains(why),"Refusal: "+why+" got "+e);}
 }
 public static void main(String[]args)throws Exception{
  var field=Unsafe.class.getDeclaredField("theUnsafe");field.setAccessible(true);PlayerBotTradeCheck.unsafe=(Unsafe)field.get(null);
  for(boolean botSelling:List.of(false,true)){
   var seller=PlayerBotTradeCheck.actor(1,botSelling);var buyer=PlayerBotTradeCheck.actor(2,!botSelling);
   var goodsItem=PlayerBotTradeCheck.item(100,112000001,1,false);goodsItem.setEnchantLevel(9);seller.cube.add_CharacterTransfer(goodsItem);
   var sellerMoney=PlayerBotTradeCheck.item(101,182400001,10,true);seller.cube.onLoadHandler(sellerMoney);
   var buyerMoney=PlayerBotTradeCheck.item(102,182400001,100,true);buyer.cube.onLoadHandler(buyerMoney);
   var offer=new Exchange(seller,buyer);offer.addItem(100,new ExchangeItem(100,1,goodsItem));
   var db=new PlayerBotTradeCheck.Jdbc();db.own(seller,goodsItem);db.own(seller,sellerMoney);db.own(buyer,buyerMoney);
   PlayerBotTradeStore.purchaseTransfer(db.connection(),seller,buyer,offer,25);
   check(db.writes.size()==3,"Goods and both wallets are written in the same caller transaction in either direction");
   check(db.writes.get(0).contains("item_owner=?") && db.writes.get(0).contains("1=2"),"Real item ownership goes only to buyer");
   check(db.writes.get(1).contains("1=75") && db.writes.get(2).contains("1=35"),"Opposite payment debits buyer and credits seller");
   check(goodsItem.getEnchantLevel()==9 && seller.cube.getItemByObjId(100)==goodsItem && buyer.cube.getItemByObjId(100)==null,"No published ownership before commit; enchant preserved");
   check(sellerMoney.getItemCount()==10 && buyerMoney.getItemCount()==100,"No published balances before commit");
   for(int badId:List.of(100,101,102))for(int column:List.of(0,1,2,3,4)){
    db=new PlayerBotTradeCheck.Jdbc();db.own(seller,goodsItem);db.own(seller,sellerMoney);db.own(buyer,buyerMoney);db.rows.get(badId)[column]++;
    refuses(db,seller,buyer,offer,25,"custody/count");check(db.writes.isEmpty(),"Invalid goods/wallet ownership/template/storage/count/equipped is rejected before any write");
   }
   db=new PlayerBotTradeCheck.Jdbc();refuses(db,seller,buyer,offer,101,"funds changed");check(db.writes.isEmpty(),"Insufficient funds cannot move goods");
   for(long price:List.of(0L,-1L)){refuses(db,seller,buyer,offer,price,"Invalid purchase");}
   db.autoCommit=true;refuses(db,seller,buyer,offer,25,"Invalid purchase");db.autoCommit=false;
   sellerMoney.setItemCount(Long.MAX_VALUE);refuses(db,seller,buyer,offer,25,"overflow");sellerMoney.setItemCount(10);
   offer.addKinah(1);refuses(db,seller,buyer,offer,25,"Invalid purchase");
   var stack=PlayerBotTradeCheck.item(103,164000001,10,false);seller.cube.add_CharacterTransfer(stack);
   var split=PlayerBotTradeCheck.item(104,164000001,3,false);split.setPersistentState(PersistentState.NEW);
   offer=new Exchange(seller,buyer);offer.addItem(103,new ExchangeItem(103,3,split));
   db=new PlayerBotTradeCheck.Jdbc();db.own(seller,stack);db.own(seller,sellerMoney);db.own(buyer,buyerMoney);
   PlayerBotTradeStore.purchaseTransfer(db.connection(),seller,buyer,offer,12);
   check(db.writes.size()==4 && db.writes.get(0).contains("1=7") && db.writes.get(1).startsWith("INSERT"),"Paid split uses exact remainder and separate persistent identity");
   check(db.writes.get(2).contains("1=88") && db.writes.get(3).contains("1=22"),"Paid stack and both currency sides share transaction");
   check(stack.getItemCount()==10 && buyer.cube.getItemByObjId(104)==null,"Paid split is not projected before acknowledgement");
  }
  System.out.println("OK: "+checks+" world-bot priced trade SQL/custody checks; no world/DB/IDs");
 }
}
