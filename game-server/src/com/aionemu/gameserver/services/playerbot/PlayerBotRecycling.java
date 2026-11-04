package com.aionemu.gameserver.services.playerbot;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.util.*;

import com.aionemu.commons.database.DatabaseFactory;
import com.aionemu.commons.utils.Json;
import com.aionemu.gameserver.GameServer;
import com.aionemu.gameserver.configs.main.PlayerBotConfig;
import com.aionemu.gameserver.configs.main.PlayerBotEconomyConfig;
import com.aionemu.gameserver.dao.*;
import com.aionemu.gameserver.dao.PlayerBotRecyclingDAO.Quote;
import com.aionemu.gameserver.dao.PlayerBotRecyclingDAO.Receipt;
import com.aionemu.gameserver.model.gameobjects.Item;
import com.aionemu.gameserver.model.gameobjects.Persistable.PersistentState;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.items.storage.StorageType;
import com.aionemu.gameserver.model.templates.item.enums.ItemGroup;
import com.aionemu.gameserver.services.CentralMarketRules;
import com.aionemu.gameserver.services.item.ItemPacketService;
import com.aionemu.gameserver.services.trade.PricesService;
import org.slf4j.LoggerFactory;

/** One terminal archive and all three quoted rewards in one native transaction. No item/wallet withdrawal. */
public final class PlayerBotRecycling {
 private static volatile boolean ready;
 private record Pair(PlayerBotSession from,PlayerBotSession to,PlayerBotSession first,PlayerBotSession second) {}
 private record Terms(String fingerprint,int learning,long kinah,long appraisal,int items,long actual,long recycled,Properties nextGrowth) {}
 @FunctionalInterface private interface Work<T>{T run(Pair pair)throws SQLException,IOException;}
 /** A quote is set only when the rewards dropped and the player has to review them again. */
 public record Outcome(String notice,Map<String,Object> quote) {}

 /** The tombstone remains enforced even after new recycling is disabled. */
 public static synchronized void initialize() {
  if(ready)return;
  try(var c=DatabaseFactory.getConnection()){PlayerBotRecyclingDAO.prepare(c);ready=true;}
  catch(SQLException e){throw new IllegalStateException("Cannot initialize permanent companion recycling custody",e);}
 }
 public static boolean enabled(){return ready && PlayerBotEconomyConfig.RECYCLING_ENABLED && PlayerBotConfig.TICKETS_ENABLED;}
 /** Dedicated identity admission also checks terminal history when a caller has stale account data. */
 static void requireRecruitable(int account,int character) {
  try(var c=DatabaseFactory.getConnection()) {
   if(PlayerBotRecyclingDAO.receipt(c,character)!=null || PlayerBotRepositoryDAO.removed(c,account,character)!=null)
    throw new IllegalArgumentException("This companion was archived or permanently recycled and cannot be recruited.");
   if(!PlayerBotConfig.GENERATED_ENABLED) {
    var growth=PlayerBotMetadataDAO.load(c,account,character,"growth");
    boolean ticket=growth!=null && PlayerBotGrowth.decode(growth.values()).origin()==PlayerBotGrowth.Origin.GACHA;
    PlayerBotCreationPolicy.requireRecruitment(true,ticket);
   }
  }catch(SQLException error){throw new IllegalStateException("Cannot verify companion archive custody; recruitment refused.",error);}
 }
 private static void available(){if(!enabled())throw new IllegalArgumentException("Companion recycling is unavailable.");}
 private static <T> T actors(Player owner,String source,String target,Work<T> work)throws SQLException,IOException {
  var service=PlayerBotService.getInstance();var from=service.find(owner,source);var to=service.find(owner,target);
  if(from==to || from.bot().getObjectId()==to.bot().getObjectId())throw new IllegalArgumentException("Choose a different ticket recruit to receive Learning XP.");
  var first=from.bot().getObjectId()<to.bot().getObjectId()?from:to;var second=first==from?to:from;
  if(!service.claimTransfer(first))throw new IllegalArgumentException("A companion is busy; wait and retry.");
  try {
   if(!service.claimTransfer(second))throw new IllegalArgumentException("A companion is busy; wait and retry.");
   try{synchronized(first){synchronized(second){synchronized(first.bot()){synchronized(second.bot()){
    var pair=new Pair(from,to,first,second);safe(owner,pair);return work.run(pair);
   }}}}}finally{service.completeTransfer(second);}
  }finally{service.completeTransfer(first);}
 }
 private static void safe(Player owner,Pair pair) {
  if(owner==null || owner.isPlayerBot() || !owner.isOnline() || !owner.isSpawned() || GameServer.isShuttingDownSoon()
   || !PlayerBotWarehouse.idle(owner))throw new IllegalArgumentException("Recycle while you and both recruits are alive, on the ground and outside combat, trade or item use.");
  for(var s:List.of(pair.first(),pair.second())) {
   if(!PlayerBotItemTransfer.collectionCompanion(owner,s) || !s.bot().isSpawned() || !s.bot().isPlaying() || !PlayerBotWarehouse.idle(s.bot()))
    throw new IllegalArgumentException("Choose two active, idle ticket recruits from your own collection.");
   var interaction=s.bot().getInteractionTask();
   if(interaction!=null && interaction.isInProgress())throw new IllegalArgumentException("Finish both companions' current interactions before recycling.");
  }
 }
 private static Object client(Player owner) {
  var guard=owner==null?null:owner.getClientConnection();
  if(guard==null || owner.isPlayerBot())throw new IllegalArgumentException("Log in before recycling companions.");return guard;
 }
 /** Preview is durable and recipient-bound. It grants nothing and expires if items, growth or tuning changes. */
 public static Map<String,Object> quote(Player owner,String source,String target) {
  available();Object guard=client(owner);
  synchronized(guard) {
   if(owner.getClientConnection()!=guard)throw new IllegalArgumentException("Reconnect before recycling.");
   try{return actors(owner,source,target,pair->{PlayerBotMarket.requireSettled(pair.from());return view(fresh(owner,pair));});}
   catch(SQLException|IOException error){throw new IllegalStateException("Cannot save a recycling quote; no companion was consumed.",error);}
  }
 }
 private static Quote fresh(Player owner,Pair pair)throws SQLException,IOException {
  int account=owner.getAccount().getId();
  try(var c=DatabaseFactory.getConnection()) {
   c.setAutoCommit(false);
   try {
    for(var s:List.of(pair.first(),pair.second()))PlayerBotRecyclingDAO.actor(c,account,s.bot().getObjectId());
    var terms=terms(c,pair);long expiry=Math.addExact(System.currentTimeMillis(),Math.multiplyExact(PlayerBotEconomyConfig.RECYCLE_QUOTE_SECONDS,1000L));
    var q=new Quote(UUID.randomUUID().toString(),account,pair.from().bot().getObjectId(),pair.to().bot().getObjectId(),owner.getObjectId(),
     pair.from().bot().getName(),pair.to().bot().getName(),terms.fingerprint(),terms.learning(),terms.kinah(),terms.appraisal(),terms.items(),terms.actual(),terms.recycled(),expiry);
    PlayerBotRecyclingDAO.quote(c,q);c.commit();return q;
   }catch(SQLException|IOException|RuntimeException error){try{c.rollback();}catch(SQLException rollback){error.addSuppressed(rollback);}throw error;}
  }
 }
 private static Map<String,Object> view(Quote q) {
  var out=new LinkedHashMap<String,Object>();out.put("key",q.key());out.put("source",q.sourceName());out.put("sourceId",q.source());out.put("target",q.targetName());out.put("targetId",q.target());
  out.put("ticketCredit",0.33);out.put("learningXp",q.learning());out.put("kinah",q.kinah());out.put("itemAppraisal",q.appraisal());out.put("appraisedItems",q.items());
  out.put("actualPlayLearning",q.actualLearning());out.put("recycledLearning",q.recycledLearning());out.put("expiresAt",q.expires());
  out.put("warning","Permanent recycling. Market orders close automatically and their goods and Kinah return to this recruit before appraisal. Its items, equipment and stored Kinah remain archived and cannot be withdrawn. Rewards go to your account, the selected recruit, and your character respectively.");return out;
 }
 public static Outcome execute(Player owner,String source,String target,String key) {
  available();Object guard=client(owner);
  if(key==null || !key.matches("[a-f0-9-]{36}"))throw new IllegalArgumentException("Preview recycling before confirming it.");
  synchronized(guard) {
   if(owner.getClientConnection()!=guard)throw new IllegalArgumentException("Reconnect before recycling.");
   try {
    Quote q;Receipt previous;
    try(var c=DatabaseFactory.getConnection()){q=PlayerBotRecyclingDAO.quote(c,key,false);bound(owner,source,target,q);previous=PlayerBotRecyclingDAO.receipt(c,q.source());}
    if(previous!=null)return new Outcome(replay(q,previous),null);
    return actors(owner,source,target,pair->{
     if(pair.from().bot().getObjectId()!=q.source() || pair.to().bot().getObjectId()!=q.target())throw new IllegalArgumentException("Companion identity changed; preview again.");
     PlayerBotMarket.requireSettled(pair.from());
     boolean changed=q.expires()<System.currentTimeMillis();
     try(var c=DatabaseFactory.getConnection()) {
      var current=terms(c,pair);
      changed|=!current.fingerprint().equals(q.fingerprint()) || current.learning()!=q.learning() || current.kinah()!=q.kinah();
     }
     // Active recruits loot and spend all the time. The player approved these rewards as a minimum.
     var latest=changed?fresh(owner,pair):q;
     if(latest.learning()<q.learning() || latest.kinah()<q.kinah())
      return new Outcome("The rewards dropped while "+q.sourceName()+" was active. Review the new amounts and confirm again.",view(latest));
     // Preserve all final native progress and historical collection contribution, using
     // the existing home/checkpoint owner before any irreversible archive or reward.
     try{PlayerBotService.getInstance().checkpointBeforeArchive(pair.from());}
     catch(SQLException|RuntimeException error){hold(owner,pair,error);throw new IllegalStateException("Final companion progress needs verification; recycling did not proceed.",error);}
     if(owner.getInventory().getKinahItem()==null)owner.getInventory().increaseKinah(0);
     if(!InventoryDAO.store(owner))throw new SQLException("Recycling recipient wallet checkpoint failed");
     complete(owner,pair,latest);
     // The tombstone already prevents every admission path. Normal dismissal keeps
     // rows, IDs and archived equipment; its existing save retry retains custody.
     try{PlayerBotService.getInstance().dismiss(owner,pair.from().bot().getName());}
     catch(RuntimeException error){pair.from().markClosing();LoggerFactory.getLogger(PlayerBotRecycling.class).error("Recycled companion awaits normal dismissal retry",error);}
     return new Outcome(result(latest),null);
    });
   }catch(SQLException|IOException error){throw new IllegalStateException("Recycling could not be saved; no completed reward is retried without its native receipt.",error);}
  }
 }
 private static void bound(Player owner,String source,String target,Quote q) {
  if(q==null || q.account()!=owner.getAccount().getId() || q.receiver()!=owner.getObjectId()
   || !q.sourceName().equalsIgnoreCase(source) || !q.targetName().equalsIgnoreCase(target))throw new IllegalArgumentException("This recycling quote belongs to a different character or companion selection. Preview again.");
 }
 private static String replay(Quote q,Receipt receipt) {
  if(!q.key().equals(receipt.key()) || q.account()!=receipt.account() || q.target()!=receipt.target() || q.receiver()!=receipt.receiver()
   || q.learning()!=receipt.learning() || q.kinah()!=receipt.kinah())throw new IllegalArgumentException("This recruit has already been recycled under a different confirmed quote.");
  return result(q)+" This confirmation was already completed; no second reward was granted.";
 }
 private static String result(Quote q){return "Recycled "+q.sourceName()+": +0.33 Recruitment Ticket credit, +"+q.learning()+" Learning XP to "+q.targetName()+", and +"+q.kinah()+" Kinah to your character. Archived items cannot be withdrawn.";}
 private static void complete(Player owner,Pair pair,Quote expected)throws SQLException,IOException {
  var c=DatabaseFactory.getConnection();var inventory=new ArrayList<Item>();var metadata=new ArrayList<PlayerBotMetadata.Pending>();
  PlayerBotMetadata.Pending[] growth={null};Properties[] projected={null};long[] balance={0};
  try {
   PlayerBotWarehouseStore.transaction(c,()->{
    safe(owner,pair);
    var q=PlayerBotRecyclingDAO.quote(c,expected.key(),true);bound(owner,expected.sourceName(),expected.targetName(),q);
    if(q.expires()<System.currentTimeMillis())throw new IllegalArgumentException("This recycling quote expired. Preview it again.");
    var previous=PlayerBotRecyclingDAO.receipt(c,q.source());if(previous!=null)throw new IllegalArgumentException("This recruit was already recycled; refresh the window.");
    for(var s:List.of(pair.first(),pair.second()))PlayerBotRecyclingDAO.actor(c,q.account(),s.bot().getObjectId());
    com.aionemu.gameserver.services.CentralMarketService.requireCompanionClosed(c,q.source());
    Terms terms;
    try{terms=terms(c,pair);}catch(IOException error){throw new SQLException("Cannot verify quoted companion growth",error);}
    if(!terms.fingerprint().equals(q.fingerprint()) || terms.learning()!=q.learning() || terms.kinah()!=q.kinah())throw new IllegalArgumentException("Items, companion progress or recycling settings changed. Preview again before confirming.");
    for(var s:List.of(pair.first(),pair.second())) {
     inventory.addAll(InventoryDAO.storeCompanionInventory(c,s.bot()));
     var rows=PlayerBotMetadata.pending(q.account(),s.bot().getObjectId());PlayerBotMetadata.store(c,rows);metadata.addAll(rows);
    }
    verifyArchivedInventory(c,pair.from().bot());
    var current=PlayerBotMetadataDAO.load(c,q.account(),q.target(),"growth");
    Map<String,String> cached;
    try{cached=PlayerBotMetadata.cached(q.account(),q.target(),"growth");}
    catch(IOException error){throw new SQLException("Cannot verify target growth custody",error);}
    if(current==null || cached==null || !current.values().equals(cached))throw new SQLException("Target growth checkpoint changed");
    var values=PlayerBotMetadata.values(terms.nextGrowth());PlayerBotGrowth.validateUpdate(current.values(),values);
    growth[0]=new PlayerBotMetadata.Pending(q.account(),q.target(),"growth",current.revision(),values);projected[0]=terms.nextGrowth();
    PlayerBotMetadata.store(c,List.of(growth[0]));
    PlayerBotRecruitmentDAO.recycle(c,q.account(),"recycle:"+q.source());
    Item coin=owner.getInventory().getKinahItem();long before=owner.getInventory().getKinah();
    balance[0]=CentralMarketRules.add(before,q.kinah());
    PlayerBotTradeStore.currency(c,owner,coin,before,balance[0]);
    String marker=Json.write(Map.of("version",1,"account",q.account(),"id",q.source(),"name",q.sourceName(),"removedAt",System.currentTimeMillis()));
    PlayerBotRecyclingDAO.complete(c,q,marker);
   },()->{
    InventoryDAO.companionInventoryCommitted(inventory);PlayerBotMetadata.committed(metadata);
    try{PlayerBotMetadata.save(expected.account(),expected.target(),"growth",projected[0]);}
    catch(IOException error){throw new IllegalStateException("Committed recycling growth could not be projected",error);}
    PlayerBotMetadata.committed(List.of(growth[0]));
    Item coin=owner.getInventory().getKinahItem();coin.setItemCount(balance[0]);coin.setPersistentState(PersistentState.UPDATED);
   });
  }catch(PlayerBotWarehouseStore.Held error){hold(owner,pair,error);throw new IllegalStateException("Recycling persistence needs verification; both companions and your wallet are held. Reconnect after the server log is reviewed.",error);}
  catch(SQLException|RuntimeException error){InventoryDAO.markCompanionInventoryDirty(pair.first().bot());InventoryDAO.markCompanionInventoryDirty(pair.second().bot());throw error;}
  finally{try{c.close();}catch(SQLException cleanup){LoggerFactory.getLogger(PlayerBotRecycling.class).warn("Recycling connection cleanup failed after outcome was determined",cleanup);}}
  try{ItemPacketService.sendItemUpdatePacket(owner,owner.getInventory().getStorageType(),owner.getInventory().getKinahItem(),ItemPacketService.ItemUpdateType.INC_KINAH_COLLECT);}
  catch(RuntimeException error){LoggerFactory.getLogger(PlayerBotRecycling.class).warn("Committed recycling wallet notification failed",error);}
 }
 private static void hold(Player owner,Pair pair,Exception error) {
  for(var s:List.of(pair.first(),pair.second())){InventoryDAO.quarantineMarketInventory(s.bot().getObjectId());s.markClosing();}
  InventoryDAO.quarantineMarketInventory(owner.getObjectId());
  LoggerFactory.getLogger(PlayerBotRecycling.class).error("Recycling custody held for native characters {}, {}, {}; no identities released",pair.from().bot().getObjectId(),pair.to().bot().getObjectId(),owner.getObjectId(),error);
  if(owner.getClientConnection()!=null)owner.getClientConnection().close();
 }
 private static long remaining(PlayerBotGrowth.Profile p) {
  long total=0;int[] costs={1000,3000,6000,10000};for(int n=p.initialCombatRank().ordinal();n<costs.length;n++)total+=costs[n];
  return Math.max(0,total-p.earnedLearningXp());
 }
 private static String tuning() {
  if(PlayerBotEconomyConfig.RECYCLE_LEARNING_BASE==null || PlayerBotEconomyConfig.RECYCLE_KINAH_BASE==null || PlayerBotEconomyConfig.RECYCLE_LEARNING_BASE.length!=5 || PlayerBotEconomyConfig.RECYCLE_KINAH_BASE.length!=5
   || Arrays.stream(PlayerBotEconomyConfig.RECYCLE_LEARNING_BASE).anyMatch(n->n<=0) || Arrays.stream(PlayerBotEconomyConfig.RECYCLE_KINAH_BASE).anyMatch(n->n<=0)
   || PlayerBotEconomyConfig.RECYCLE_LEARNING_PERCENT<0 || PlayerBotEconomyConfig.RECYCLE_LEARNING_PERCENT>100 || PlayerBotEconomyConfig.RECYCLE_LEARNING_CAP<=0
   || PlayerBotEconomyConfig.RECYCLE_ITEM_LEARNING_DIVISOR<=0 || PlayerBotEconomyConfig.RECYCLE_ITEM_LEARNING_CAP<0 || PlayerBotEconomyConfig.RECYCLE_KINAH_CAP<=0
   || PlayerBotEconomyConfig.RECYCLE_KINAH_CAP>CentralMarketRules.MAX_KINAH || PlayerBotEconomyConfig.RECYCLE_ACTUAL_LEARNING_CAP<0
   || PlayerBotEconomyConfig.RECYCLE_KINAH_PER_LEARNING<0 || PlayerBotEconomyConfig.RECYCLE_QUOTE_SECONDS<10 || PlayerBotEconomyConfig.RECYCLE_QUOTE_SECONDS>3600)
   throw new IllegalArgumentException("Recycling configuration contains an invalid reward or quote limit.");
  return Arrays.toString(PlayerBotEconomyConfig.RECYCLE_LEARNING_BASE)+Arrays.toString(PlayerBotEconomyConfig.RECYCLE_KINAH_BASE)+":"+PlayerBotEconomyConfig.RECYCLE_LEARNING_PERCENT+":"+PlayerBotEconomyConfig.RECYCLE_LEARNING_CAP
   +":"+PlayerBotEconomyConfig.RECYCLE_ITEM_LEARNING_DIVISOR+":"+PlayerBotEconomyConfig.RECYCLE_ITEM_LEARNING_CAP+":"+PlayerBotEconomyConfig.RECYCLE_KINAH_CAP+":"+PlayerBotEconomyConfig.RECYCLE_ACTUAL_LEARNING_CAP
   +":"+PlayerBotEconomyConfig.RECYCLE_KINAH_PER_LEARNING+":"+PlayerBotEconomyConfig.RECYCLE_QUOTE_SECONDS+":"+PricesService.getVendorSellModifier();
 }
 private static Terms terms(Connection c,Pair pair)throws SQLException,IOException {
  var from=pair.from().bot();var to=pair.to().bot();int account=from.getAccount().getId();
  var source=PlayerBotGrowth.profile(from);var target=PlayerBotGrowth.profile(to);
  if(source.origin()!=PlayerBotGrowth.Origin.GACHA || target.origin()!=PlayerBotGrowth.Origin.GACHA)throw new IllegalArgumentException("Only ticket recruits can recycle or receive training.");
  long capacity=remaining(target);if(capacity<=0 || target.combatRank()==PlayerBotGrowth.CombatRank.EXCELLENT)throw new IllegalArgumentException("Choose a recruit that can still gain Learning XP. Excellent recruits cannot receive recycling training.");
  long received=PlayerBotRecyclingDAO.received(c,account,from.getObjectId()),targetReceived=PlayerBotRecyclingDAO.received(c,account,to.getObjectId());
  if(received>source.earnedLearningXp() || targetReceived>target.earnedLearningXp())throw new SQLException("Recycling training exceeds the native growth record");
  long actual=source.earnedLearningXp()-received;var generated=PlayerBotGearPolicy.state(pair.from()).generated;
  var items=privateItems(from);
  var fingerprint=new StringBuilder(tuning()).append('|').append(source).append('|').append(target).append('|').append(from.getLevel()).append('|').append(from.getPlayerClass())
   .append('|').append(received).append('|').append(targetReceived).append('|').append(new TreeSet<>(generated));
  long appraisal=0;int counted=0;
  for(var item:items.values()) {
   if(item.getItemTemplate().isKinah())continue; // Stored funds never become a human withdrawal through recycling.
   boolean eligible=!generated.contains(item.getObjectId()) && item.getItemCount()>0 && item.getPersistentState()!=PersistentState.DELETED
    && item.isSellable() && item.getItemTemplate().getItemGroup()!=ItemGroup.QUEST && item.getExpireTime()==0 && item.getItemTemplate().getExpireTime()==0
    && !PlayerBotCare.worldQuestItem(from,item.getItemId());
   fingerprint.append('|').append(item).append(':').append(eligible).append(':').append(item.getItemTemplate().getPrice());
   if(!eligible)continue;
   long unit=PricesService.getSellReward(item.getItemTemplate().getPrice(),PricesService.getVendorSellModifier());
   if(unit<=0)continue;
   long value=saturatingMultiply(unit,item.getItemCount(),PlayerBotEconomyConfig.RECYCLE_KINAH_CAP);
   appraisal=saturatingAdd(appraisal,value,PlayerBotEconomyConfig.RECYCLE_KINAH_CAP);counted++;
  }
  int rarity=source.autoEquipRarity().ordinal();
  long learning=PlayerBotEconomyConfig.RECYCLE_LEARNING_BASE[rarity];
  learning=saturatingAdd(learning,saturatingMultiply(actual,PlayerBotEconomyConfig.RECYCLE_LEARNING_PERCENT,Long.MAX_VALUE)/100,PlayerBotEconomyConfig.RECYCLE_LEARNING_CAP);
  learning=saturatingAdd(learning,Math.min(appraisal,PlayerBotEconomyConfig.RECYCLE_ITEM_LEARNING_CAP)/PlayerBotEconomyConfig.RECYCLE_ITEM_LEARNING_DIVISOR,PlayerBotEconomyConfig.RECYCLE_LEARNING_CAP);
  learning=Math.min(capacity,learning);
  long kinah=saturatingAdd(PlayerBotEconomyConfig.RECYCLE_KINAH_BASE[rarity],saturatingMultiply(Math.min(actual,PlayerBotEconomyConfig.RECYCLE_ACTUAL_LEARNING_CAP),PlayerBotEconomyConfig.RECYCLE_KINAH_PER_LEARNING,PlayerBotEconomyConfig.RECYCLE_KINAH_CAP),PlayerBotEconomyConfig.RECYCLE_KINAH_CAP);
  kinah=saturatingAdd(kinah,appraisal,PlayerBotEconomyConfig.RECYCLE_KINAH_CAP);
  if(learning<=0 || kinah<=0)throw new IllegalArgumentException("This recycling quote cannot deliver all three rewards.");
  var values=PlayerBotMetadata.cached(account,to.getObjectId(),"growth");if(values==null || values.isEmpty())throw new SQLException("Target growth is not loaded");
  var next=PlayerBotMetadata.properties(values);long xp=Math.addExact(target.earnedLearningXp(),learning);
  next.setProperty("earnedLearningXp",Long.toString(xp));next.setProperty("combatRank",PlayerBotGrowth.rank(target.initialCombatRank(),xp).name());
  fingerprint.append('|').append(learning).append('|').append(kinah).append('|').append(appraisal).append('|').append(counted);
  return new Terms(hash(fingerprint.toString()),Math.toIntExact(learning),kinah,appraisal,counted,actual,received,next);
 }
 private static Map<Integer,Item> privateItems(Player from) {
  var items=new TreeMap<Integer,Item>();
  for(var type:StorageType.values())if(type==StorageType.CUBE || type==StorageType.REGULAR_WAREHOUSE || type.getId()>=32 && type.getId()<=43 || type.getId()>=60 && type.getId()<=79) {
   var storage=from.getStorage(type.getId());if(storage!=null)for(var item:storage.getItemsWithKinah())add(items,item);
  }
  for(var item:from.getEquipment().getEquippedItems())add(items,item);
  return items;
 }
 /** The complete private snapshot, not only dirty rows, must still have native source custody. */
 private static void verifyArchivedInventory(Connection c,Player source)throws SQLException {
  var items=new TreeMap<>(privateItems(source));
  try(var s=c.prepareStatement("SELECT item_unique_id,item_id,item_count,item_location,is_equipped,is_soul_bound,slot,pack_count FROM inventory WHERE item_owner=? AND (item_location IN (0,1) OR item_location BETWEEN 32 AND 43 OR item_location BETWEEN 60 AND 79) FOR UPDATE")) {
   s.setInt(1,source.getObjectId());try(var r=s.executeQuery()){while(r.next()) {
    Item item=items.remove(r.getInt(1));
    if(item==null || item.getItemId()!=r.getInt(2) || item.getItemCount()!=r.getLong(3) || item.getItemLocation()!=r.getInt(4)
     || item.isEquipped()!=r.getBoolean(5) || item.isSoulBound()!=r.getBoolean(6) || item.getEquipmentSlot()!=r.getLong(7) || item.getPackCount()!=r.getInt(8))
     throw new SQLException("Quoted recycling inventory custody changed");
   }}
  }
  if(!items.isEmpty())throw new SQLException("Quoted recycling inventory is missing native owned rows");
 }
 private static void add(Map<Integer,Item> items,Item item) {
  if(item==null || item.getObjectId()<=0)throw new IllegalArgumentException("Companion item identity is invalid.");
  Item old=items.putIfAbsent(item.getObjectId(),item);if(old!=null && old!=item)throw new IllegalArgumentException("Companion inventory contains conflicting native item identities.");
 }
 static long saturatingMultiply(long a,long b,long cap){if(a<0 || b<0 || cap<0)throw new IllegalArgumentException("Negative recycling value");return a==0 || b==0?0:a>cap/b?cap:Math.min(cap,a*b);}
 static long saturatingAdd(long a,long b,long cap){if(a<0 || b<0 || cap<0)throw new IllegalArgumentException("Negative recycling value");return a>=cap || b>=cap-a?cap:a+b;}
 private static String hash(String text) {
  try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}
  catch(java.security.NoSuchAlgorithmException error){throw new IllegalStateException("SHA-256 is unavailable",error);}
 }
 private PlayerBotRecycling() {}
}
