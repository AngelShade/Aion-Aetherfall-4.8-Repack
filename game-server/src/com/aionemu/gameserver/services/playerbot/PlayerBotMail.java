/* MailAction::Execute/processors and SendMailAction::Execute, mod-playerbots
 * 037c01418b5d01506917a3db9b44fd56ac5f965c. GPL-2.0-or-later;
 * third-party/playerbots/AUTHORS.md. Native Aion private mail adapter. */
package com.aionemu.gameserver.services.playerbot;

import java.sql.*;
import java.util.*;
import com.aionemu.gameserver.GameServer;
import com.aionemu.gameserver.configs.main.PlayerBotConfig;
import com.aionemu.gameserver.dao.*;
import com.aionemu.gameserver.model.gameobjects.*;
import com.aionemu.gameserver.model.gameobjects.Persistable.PersistentState;
import com.aionemu.gameserver.model.gameobjects.player.*;
import com.aionemu.gameserver.services.AdminService;
import com.aionemu.gameserver.services.DialogService;
import com.aionemu.gameserver.services.mail.MailService;
import com.aionemu.gameserver.services.item.ItemPacketService;
import com.aionemu.gameserver.utils.PositionUtil;
import com.aionemu.gameserver.utils.idfactory.IDFactory;
import com.aionemu.gameserver.world.geo.GeoService;
import org.slf4j.LoggerFactory;

/** Exact owner requests, one letter/whole stack per transaction. No public gifts or unpriced world-bot sales. */
public final class PlayerBotMail {
 record Request(Player bot,Player owner,String action,Letter letter,Item item,short slot,long money,long cost,long wallet,boolean expired) {}
 static boolean postal(Player owner,Player bot,Npc npc) {
  // Native PostboxAI handles dialog start directly, rather than a template OPEN_POSTBOX action.
  return "postbox".equals(npc.getObjectTemplate().getAiName()) && npc.isSpawned() && !npc.isDead()
   && npc.getWorldId()==bot.getWorldId() && npc.getInstanceId()==bot.getInstanceId()
   && npc.getObjectTemplate().canInteract() && !owner.isEnemy(npc) && !bot.isEnemy(npc)
   && owner.canSee(npc) && bot.canSee(npc) && PositionUtil.isInTalkRange(owner,npc) && PositionUtil.isInTalkRange(bot,npc)
   && DialogService.isInteractionAllowed(owner,npc) && DialogService.isInteractionAllowed(bot,npc)
   && GeoService.getInstance().canSee(owner,npc) && GeoService.getInstance().canSee(bot,npc);
 }
 static String blocker(PlayerBotSession session) {
  if(!PlayerBotWarehouse.privateOwner(session))return "Mail belongs to your own companion; invited world bots retain their private mailbox and items.";
  if(session.closing())return "The companion is waiting for dismissal or custody verification.";
  Player owner=session.owner(),bot=session.bot();
  if(!PlayerBotConfig.ENABLED || GameServer.isShuttingDownSoon())return "Companion mail is unavailable while bots are disabled or during shutdown.";
  if(!owner.isOnline() || !owner.isSpawned() || !bot.isSpawned() || !bot.isPlaying())return "Recruit this companion before using its mail.";
  if(!PlayerBotWarehouse.idle(owner) || !PlayerBotWarehouse.idle(bot))return "Use mail while both characters are alive, idle, on the ground and outside combat, casting, trade, looting and item use.";
  if(owner.getRace()!=bot.getRace() || owner.getWorldId()!=bot.getWorldId() || owner.getInstanceId()!=bot.getInstanceId() || !PlayerBotTeams.same(owner,bot))return "Your companion must be in your current party or alliance on the same map.";
  boolean[] found={false};bot.getKnownList().forEachNpc(n->{if(!found[0] && postal(owner,bot,n))found[0]=true;});
  return found[0]?"":"Bring both characters within talking distance of the same accessible regular mailbox. Private express couriers cannot serve both characters.";
 }
 static boolean sendable(Item item) {
  return item!=null && item.getObjectId()>0 && item.getItemLocation()==0 && item.getItemCount()>0 && !item.isEquipped()
   && !item.getItemTemplate().isKinah() && item.isTradeable() && item.getPackCount()==0;
 }
 static void ensureLoaded(Player bot)throws SQLException {
  synchronized(bot) {
  if(bot.getMailbox()!=null)return;
  Mailbox mailbox=MailDAO.loadPlayerMailbox(bot);
  try(var c=com.aionemu.commons.database.DatabaseFactory.getConnection()){PlayerBotMailStore.verifyLoaded(c,bot,mailbox);}
  bot.setMailbox(mailbox);bot.getCommonData().setMailboxLetters(mailbox.size());
  }
 }
 /** Native mail delivery shares the lazy-load lock, so concurrent arrivals cannot disappear from the published mailbox. */
 public static void delivered(Player bot,Letter letter) {synchronized(bot){if(bot.getMailbox()!=null){bot.getMailbox().putLetterToMailbox(letter);bot.getCommonData().setMailboxLetters(bot.getMailbox().size());}}}
 static Request plan(Player bot,Player owner,String action,int object,long money,int letterId) {
  if(!bot.isPlayerBot() || bot.isWorldBot() || bot.getPlayerBotOwnerId()!=owner.getObjectId() || bot.getAccount().getId()!=owner.getAccount().getId())
   throw new IllegalArgumentException("Mail requires your own companion.");
  if(bot.getMailbox()==null)throw new IllegalArgumentException("Refresh this companion's mailbox first.");
  long wallet=bot.getInventory().getKinah();
  if(action.equals("senditem") || action.equals("sendkinah")) {
   if(!PlayerBotConfig.ENABLED)throw new IllegalArgumentException("Companions are disabled in gameserver.playerbots.enable.");
   if(owner.getMailbox()==null || !owner.getMailbox().haveFreeSlots())throw new IllegalArgumentException("Your native mailbox is full or unavailable.");
   if(owner.getBlockList().contains(bot.getObjectId()))throw new IllegalArgumentException("Remove this companion from your ignore list before requesting mail.");
   Item item=action.equals("senditem")?bot.getInventory().getItemByObjId(object):null;
   if(action.equals("senditem") && !sendable(item))throw new IllegalArgumentException("Choose a tradeable, unpacked, unequipped whole stack from this companion's cube. Courier-pass and packed-item mail remain separate.");
   if(action.equals("sendkinah") && money<=0 || action.equals("senditem") && money!=0)throw new IllegalArgumentException("Choose either one item stack or a positive Kinah amount.");
   if(item!=null && !AdminService.getInstance().canOperate(bot,null,item,"mail"))throw new IllegalArgumentException("Native staff mail rules refuse this item.");
   long cost=MailService.companionMailCost(bot,item,money);if(cost>wallet)throw new IllegalArgumentException("This companion lacks Kinah for the attachment and native postage.");
   Letter letter=new Letter(letterId,owner.getObjectId(),item,money,item==null?"Money you asked for":"Item(s) you asked for","Requested by "+owner.getName()+" from "+bot.getName()+".",bot.getName(),new Timestamp(System.currentTimeMillis()),true,LetterType.NORMAL);
   return new Request(bot,owner,action,letter,item,(short)0,money,cost,wallet,false);
  }
  Letter letter=bot.getMailbox().getLetterFromMailbox(object);
  if(letter==null || letter.getRecipientId()!=bot.getObjectId())throw new IllegalArgumentException("Choose a current letter from this companion's mailbox.");
  Item item=letter.getAttachedItem();long attached=letter.getAttachedKinah();
  if(action.equals("delete") && (item!=null || attached!=0))throw new IllegalArgumentException("Collect this letter's attachments before deleting it.");
  if(!Set.of("read","take","delete").contains(action))throw new IllegalArgumentException("Unknown mail operation.");
  boolean expired=item!=null && item.getExpireTime()!=0 && item.getExpireTime()<=System.currentTimeMillis()/1000;
  short slot=0;
  if(action.equals("take")) {
   if(item==null && attached==0)throw new IllegalArgumentException("This letter has no attachments to collect.");
   if(attached<0)throw new IllegalArgumentException("Invalid native mail currency.");
   try{Math.addExact(wallet,attached);}catch(ArithmeticException e){throw new IllegalArgumentException("This attachment would overflow the companion's Kinah.",e);}
   if(item!=null && (item.getItemLocation()!=127 || item.isEquipped() || item.getItemCount()<=0 || item.getItemTemplate().isKinah()))throw new IllegalArgumentException("Mail item custody is invalid; no attachment was collected.");
   if(item!=null && !expired)slot=PlayerBotWarehouse.freeSlot(bot.getInventory(),item);
  }
  return new Request(bot,owner,action,letter,item,slot,attached,0,wallet,expired);
 }
 static void project(Request request) {
  var bot=request.bot();var letter=request.letter();var item=request.item();String action=request.action();
  if(action.startsWith("send")) {
   if(item!=null) {
    if(bot.getInventory().remove(item)!=item)throw new IllegalStateException("Committed mail source disappeared");
    item.setItemLocation(127);item.setEquipmentSlot(0);item.setPersistentState(PersistentState.UPDATED);
   }
   bot.getInventory().decreaseKinah(request.cost());
   if(bot.getInventory().getKinah()!=request.wallet()-request.cost())throw new IllegalStateException("Committed postage projection failed");
   bot.getInventory().getKinahItem().setPersistentState(PersistentState.UPDATED);
   letter.setPersistentState(PersistentState.UPDATED);MailService.publishCompanionMail(request.owner(),letter);
   if(request.owner().getMailbox().getLetterFromMailbox(letter.getObjectId())!=letter && !request.owner().getMailbox().getAllLetters().contains(letter))throw new IllegalStateException("Committed mail publication failed");
  }else if(action.equals("take")) {
   if(item!=null) {
    if(request.expired()) {item.setPersistentState(PersistentState.UPDATED);IDFactory.getInstance().releaseId(item.getObjectId());}
    else {
     item.setEquipmentSlot(request.slot());
     if(bot.getInventory().add(item,ItemPacketService.ItemAddType.MAIL)!=item || item.getItemLocation()!=0 || item.getItemCount()<=0)
      throw new IllegalStateException("Committed mail attachment projection failed");
     if(item.getExpireTime()!=0)com.aionemu.gameserver.taskmanager.tasks.ExpireTimerTask.getInstance().registerExpirable(item,bot);
    }
    letter.setAttachedItem(null);
   }
   if(request.money()>0) {
    bot.getInventory().increaseKinah(request.money());
    if(bot.getInventory().getKinah()!=Math.addExact(request.wallet(),request.money()))throw new IllegalStateException("Committed mail Kinah projection failed");
    bot.getInventory().getKinahItem().setPersistentState(PersistentState.UPDATED);letter.removeAttachedKinah();
   }
   letter.setReadLetter();letter.setPersistentState(PersistentState.UPDATED);
  }else if(action.equals("read")){letter.setReadLetter();letter.setPersistentState(PersistentState.UPDATED);}
  else {bot.getMailbox().removeLetter(letter.getObjectId());IDFactory.getInstance().releaseId(letter.getObjectId());}
 }
 public static String request(Player owner,String name,String action,int object,long money) {
  var service=PlayerBotService.getInstance();{var session=service.find(owner,name);synchronized(session){
   if(!service.current(session))throw new IllegalArgumentException("This companion has left; refresh before retrying.");
   String reason=blocker(session);if(!reason.isEmpty())throw new IllegalArgumentException(reason);
   try {ensureLoaded(session.bot());}catch(SQLException e){throw new IllegalStateException("Mailbox load could not be verified; mail and items remain unchanged.",e);}
   if(action.equals("list"))return String.join("\n",listLoaded(session.bot(),object==0?1:object));
   // A zero wallet is a normal native inventory item; never invent funds or a split attachment.
   if((action.startsWith("send") || action.equals("take")) && session.bot().getInventory().getKinahItem()==null)session.bot().getInventory().increaseKinah(0);
   Request planned=plan(session.bot(),owner,action,object,money,0);int reserved=0;
   if(action.startsWith("send")) {
    reserved=IDFactory.getInstance().nextId();var l=planned.letter();
    planned=new Request(planned.bot(),owner,action,new Letter(reserved,l.getRecipientId(),l.getAttachedItem(),l.getAttachedKinah(),l.getTitle(),l.getMessage(),l.getSenderName(),l.getTimeStamp(),true,l.getLetterType()),planned.item(),planned.slot(),planned.money(),planned.cost(),planned.wallet(),false);
   }
   try {session.bot().getMoveController().abortMove();PlayerBotMailStore.commit(planned);}
   catch(PlayerBotMailStore.Held e){InventoryDAO.quarantineMarketInventory(session.bot().getObjectId());session.markClosing();
    LoggerFactory.getLogger(PlayerBotMail.class).error("Mail custody held for bot {} letter {}; all identities retained",session.bot().getObjectId(),planned.letter().getObjectId(),e);
    throw new IllegalStateException("Mail persistence needs custody verification; this companion is held. See the server log.",e);
   }catch(SQLException|RuntimeException e){if(reserved>0)IDFactory.getInstance().releaseId(reserved);throw new IllegalStateException("Mail request was rolled back before publication; existing letters and attachments remain.",e);}
   return session.bot().getName()+": "+(action.equals("read")?planned.letter().getTitle()+" — "+planned.letter().getMessage():action.startsWith("send")?"Mailed your requested "+(planned.item()==null?money+" Kinah":"whole item stack")+"; postage "+(planned.cost()-planned.money())+" Kinah.":action.equals("take")?"Collected available attachments"+(planned.expired()?"; expired item was removed under native expiry rules.":"; letter retained for review."):"Deleted the empty letter.");
  }}
 }
 static List<String> listLoaded(Player bot,int page) {
  var letters=letters(bot);int pages=Math.max(1,(letters.size()+19)/20);if(page<1 || page>pages)throw new IllegalArgumentException("Mail page must be 1.."+pages+".");
  var lines=new ArrayList<String>();lines.add(bot.getName()+" mailbox: "+bot.getMailbox().size()+" letters; page "+page+"/"+pages);
  letters.stream().skip((page-1L)*20).limit(20).forEach(l->lines.add("mail="+l.getObjectId()+" "+l.getTitle()+" from "+l.getSenderName()+"; "+l.getAttachedKinah()+" Kinah"+(l.getAttachedItem()==null?"":"; obj="+l.getAttachedItem().getObjectId()+" x"+l.getAttachedItem().getItemCount())+(l.isUnread()?"; unread":"")));return List.copyOf(lines);
 }
 private static List<Letter> letters(Player bot){return bot.getMailbox()==null?List.of():bot.getMailbox().getLetters().stream().sorted(Comparator.comparing(Letter::getTimeStamp).reversed().thenComparingInt(Letter::getObjectId)).toList();}
 static void snapshot(PlayerBotSession session,Map<String,Object> state) {
  boolean own=PlayerBotWarehouse.privateOwner(session);Player bot=session.bot();String reason=blocker(session);
  state.put("mailAvailable",reason.isEmpty());state.put("mailReason",reason);state.put("mailLoaded",own && bot.getMailbox()!=null);
  state.put("mailSendEnabled",PlayerBotConfig.ENABLED);state.put("mailCount",own && bot.getMailbox()!=null?bot.getMailbox().size():0);
  state.put("mail",!own?List.of():letters(bot).stream().map(l->{Map<String,Object> m=new LinkedHashMap<>();m.put("id",l.getObjectId());m.put("title",l.getTitle());m.put("sender",l.getSenderName());m.put("message",l.getMessage());m.put("unread",l.isUnread());m.put("kinah",l.getAttachedKinah());m.put("item",l.getAttachedItem()==null?"":l.getAttachedItem().getItemTemplate().getName()+" x"+l.getAttachedItem().getItemCount());m.put("hasAttachments",l.getAttachedItem()!=null || l.getAttachedKinah()!=0);return m;}).toList());
 }
 private PlayerBotMail(){}
}
