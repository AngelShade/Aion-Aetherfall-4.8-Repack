package com.aionemu.gameserver.services.playerbot;

import java.io.StringReader;
import java.util.Map;
import javax.xml.bind.JAXBContext;
import com.aionemu.gameserver.dataholders.ItemData;
import com.aionemu.gameserver.model.gameobjects.Item;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.templates.item.ItemTemplate;
import com.aionemu.gameserver.model.templates.item.actions.QuestStartAction;

/** Native item metadata and asynchronous offer isolation; no use tasks, World or IDs. */
public final class PlayerBotQuestItemOfferCheck {
 private static int checks;
 private static void check(boolean ok,String reason){checks++;if(!ok)throw new AssertionError(reason);}
 private static final class Bot extends Player {
  int id;Bot(){super(null,null);}
  @Override public int getObjectId(){return id;}
  @Override public int getWorldId(){return 210010000;}
  @Override public int getInstanceId(){return 1;}
 }
 private static final class OwnedItem extends Item {
  ItemTemplate template;OwnedItem(){super(1,null);}
  @Override public int getObjectId(){return 1970043;}
  @Override public ItemTemplate getItemTemplate(){return template;}
 }
 @SuppressWarnings("unchecked") public static void main(String[] args)throws Exception {
  var field=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");field.setAccessible(true);var unsafe=(sun.misc.Unsafe)field.get(null);
  var data=(ItemData)JAXBContext.newInstance(ItemData.class).createUnmarshaller().unmarshal(new StringReader(
   "<item_templates><item_template id='182200001' name='Native quest token' level='1' mask='0' item_group='QUEST' quality='COMMON' price='0' desc='1' casting_delay='2000'><actions><queststart questid='900143'/></actions></item_template></item_templates>"));
  var item=(OwnedItem)unsafe.allocateInstance(OwnedItem.class);item.template=data.getItemTemplate(182200001);
  var action=(QuestStartAction)item.template.getActions().getItemActions().getFirst();
  check(action.getQuestId()==900143,"Quest ID comes from native JAXB item declaration");
  var one=(Bot)unsafe.allocateInstance(Bot.class);one.id=1970001;
  var two=(Bot)unsafe.allocateInstance(Bot.class);two.id=1970002;
  long now=System.currentTimeMillis();
  var first=new PlayerBotQuestItems.Request(one,new PlayerBotQuestItems.Candidate(item,action),now);
  var second=new PlayerBotQuestItems.Request(two,new PlayerBotQuestItems.Candidate(item,action),now);
  check(first.deadline>=now+30000,"Offer remains bounded beyond native item completion");
  check(first.matches(one.id,0,900143,now),"Exact actor and item-owned offer match");
  check(!first.matches(two.id,0,900143,now),"Another actor cannot authorize acceptance");
  check(!first.matches(one.id,1,900143,now),"NPC dialog cannot authorize an item-owned offer");
  check(!first.matches(one.id,0,900144,now),"Another quest cannot authorize acceptance");
  check(!first.matches(one.id,0,900143,first.deadline),"Expired offers are rejected at the deadline");
  var offersField=PlayerBotQuestItems.class.getDeclaredField("OFFERS");offersField.setAccessible(true);
  var offers=(Map<Integer,PlayerBotQuestItems.Request>)offersField.get(null);
  try {
   offers.put(one.id,first);offers.put(two.id,second);
   Thread completion=new Thread(()->PlayerBotQuestDialog.record(one.id,0,4,900143));completion.start();completion.join();
   check(Integer.valueOf(4).equals(first.page),"Native completion on another thread captures its real page");
   check(second.page==null,"Asynchronous completion cannot leak to another bot");
   PlayerBotQuestDialog.record(one.id,1,1011,900143);check(first.page==4,"Unrelated NPC offer cannot overwrite native item offer");
   PlayerBotQuestItems.close(one);PlayerBotQuestDialog.record(one.id,0,5,900143);
   check(first.page==4 && !offers.containsKey(one.id),"Cancellation/despawn drops authorization before late completion");
   try(var dialog=PlayerBotQuestDialog.open(two.id,203001,900143)) {
    PlayerBotQuestDialog.record(two.id,203001,1352,900143);check(dialog.offered(1352),"Synchronous quest dialog is still captured");
    check(second.page==null,"Synchronous NPC dialog cannot become an item acceptance");
    dialog.clearPage();check(!dialog.offered(1352),"Previous page cannot authorize a continuation");
    PlayerBotQuestDialog.record(two.id,203001,1353,900143);check(dialog.offered(1353),"Fresh native continuation page is captured");
   }
  } finally {PlayerBotQuestItems.close(one);PlayerBotQuestItems.close(two);}
  System.out.println("OK: "+checks+" native item declaration, async offer isolation, cancellation and page freshness checks; no World/DB/IDs/item tasks");
 }
}
