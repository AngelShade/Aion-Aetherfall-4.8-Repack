package com.aionemu.gameserver.services.playerbot;

import java.io.IOException;
import java.sql.*;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import com.aionemu.commons.database.DatabaseFactory;
import com.aionemu.gameserver.configs.main.PlayerBotConfig;
import com.aionemu.gameserver.model.gameobjects.Persistable.PersistentState;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.services.AetherfallBoonService;

/** Historical companion-only Fame. Identity maxima and aggregate deltas commit with native bot progress. */
public final class PlayerBotCollectionRenown {
 public record Totals(long combat,long life,long legend,int creditedBots,long revision,LocalDate stipendDay) {
  public long total(){return Math.addExact(Math.addExact(combat,life),legend);}
 }
 private static final Totals ZERO=new Totals(0,0,0,0,0,null);
 private static final Map<Integer,Totals> ACCOUNTS=new ConcurrentHashMap<>();
 private static final Map<Integer,Tracked> ACTIVE=new ConcurrentHashMap<>();
 private static volatile boolean ready;
 private static volatile int[] rarityWeights;
 private static final class Tracked {
  final Player bot;final int rarityBase;long revision=1;boolean dirty=true;
  Tracked(Player bot,int rarityBase){this.bot=bot;this.rarityBase=rarityBase;}
 }
 public static final class Checkpoint {
  private final Tracked tracked;private final long revision;private final AetherfallBoonService.BotFame fame;
  private Totals result;
  private Checkpoint(Tracked tracked){this.tracked=tracked;revision=tracked.revision;fame=AetherfallBoonService.botPersonalFame(tracked.bot);}
 }
 public static void initialize()throws SQLException {
  if(!PlayerBotConfig.COLLECTION_RENOWN_ENABLED)return;
  String[] values=PlayerBotConfig.COLLECTION_RENOWN_RARITY_WEIGHTS.split(",",-1);int[] weights=new int[5];
  if(values.length!=weights.length)throw new IllegalArgumentException("Collection Renown needs five rarity weights");
  for(int i=0;i<weights.length;i++){weights[i]=Integer.parseInt(values[i].trim());if(weights[i]<0 || weights[i]>1_000_000)throw new IllegalArgumentException("Invalid Collection Renown rarity weight");}
  try(var c=DatabaseFactory.getConnection();var s=c.createStatement()){
   s.execute("CREATE TABLE IF NOT EXISTS playerbot_collection_renown (account_id INT NOT NULL PRIMARY KEY, combat BIGINT NOT NULL DEFAULT 0, life BIGINT NOT NULL DEFAULT 0, legend BIGINT NOT NULL DEFAULT 0, credited_bots INT NOT NULL DEFAULT 0, revision BIGINT NOT NULL DEFAULT 0, stipend_day DATE NULL) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
   s.execute("CREATE TABLE IF NOT EXISTS playerbot_collection_renown_credits (player_id INT NOT NULL PRIMARY KEY, account_id INT NOT NULL, rarity_base INT NOT NULL, combat BIGINT NOT NULL DEFAULT 0, life BIGINT NOT NULL DEFAULT 0, legend BIGINT NOT NULL DEFAULT 0, KEY account_credits(account_id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
   s.execute("CREATE TABLE IF NOT EXISTS playerbot_collection_stipends (account_id INT NOT NULL, claim_day DATE NOT NULL, player_id INT NOT NULL, kinah BIGINT NOT NULL, PRIMARY KEY(account_id,claim_day)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
  }
  rarityWeights=weights;ready=true;
 }
 /** One own identity and one aggregate row, never a roster scan. Existing collections catch up as bots are recruited. */
 public static boolean loaded(Player bot) {
  if(!PlayerBotConfig.COLLECTION_RENOWN_ENABLED || !ready || !bot.isPlayerBot())return false;
  try {
   var profile=PlayerBotGrowth.profile(bot);if(profile.origin()!=PlayerBotGrowth.Origin.GACHA)return false;
   int account=bot.getAccount().getId();if(account<=0 || bot.isWorldBot())throw new IllegalArgumentException("Collection Fame needs an owned ticket recruit");
   var known=ACTIVE.get(bot.getObjectId());if(known!=null && known.bot==bot)return true;
   load(account);ACTIVE.put(bot.getObjectId(),new Tracked(bot,rarityWeights[profile.autoEquipRarity().ordinal()]));return true;
  }catch(IOException e){throw new IllegalStateException("Cannot verify Collection Renown identity",e);}
 }
 public static void close(Player bot){var tracked=ACTIVE.get(bot.getObjectId());if(tracked!=null && tracked.bot==bot)ACTIVE.remove(bot.getObjectId(),tracked);}
 public static void markChanged(Player bot){var tracked=ACTIVE.get(bot.getObjectId());if(tracked!=null && tracked.bot==bot)synchronized(tracked){tracked.revision++;tracked.dirty=true;}}
 public static Checkpoint prepare(Player bot){var tracked=ACTIVE.get(bot.getObjectId());if(tracked==null || tracked.bot!=bot)return null;synchronized(tracked){return tracked.dirty?new Checkpoint(tracked):null;}}
 public static Totals cached(int account){return ACCOUNTS.getOrDefault(account,ZERO);}
 private static Totals read(ResultSet r)throws SQLException {var day=r.getDate("stipend_day");return new Totals(r.getLong("combat"),r.getLong("life"),r.getLong("legend"),r.getInt("credited_bots"),r.getLong("revision"),day==null?null:day.toLocalDate());}
 private static Totals load(int account){try(var c=DatabaseFactory.getConnection();var q=c.prepareStatement("SELECT * FROM playerbot_collection_renown WHERE account_id=?")){q.setInt(1,account);try(var r=q.executeQuery()){Totals value=r.next()?read(r):ZERO;publish(account,value);return cached(account);}}catch(SQLException e){throw new IllegalStateException("Cannot load companion Collection Renown",e);}}
 private static boolean publish(int account,Totals value){var changed=new boolean[1];ACCOUNTS.compute(account,(key,old)->{if(old==null || value.revision()>old.revision()){changed[0]=!value.equals(old);return value;}return old;});return changed[0];}
 /** Must run in the SAME native character checkpoint transaction, after the character progress rows. */
 public static void store(Connection c,Checkpoint token)throws SQLException {
  if(token==null)return;
  if(c.getAutoCommit())throw new SQLException("Collection Fame requires a native checkpoint transaction");
  var bot=token.tracked.bot;int account=bot.getAccount().getId(),id=bot.getObjectId();
  // The native roster and immutable persisted growth row are authoritative, including on a retry.
  try(var q=c.prepareStatement("SELECT p.id FROM players p JOIN playerbot_roster b ON b.player_id=p.id AND b.account_id=p.account_id JOIN playerbot_metadata m ON m.player_id=p.id AND m.account_id=p.account_id AND m.section='growth' WHERE p.id=? AND p.account_id=? AND JSON_UNQUOTE(JSON_EXTRACT(m.settings,'$.origin'))='GACHA'")){
   q.setInt(1,id);q.setInt(2,account);try(var r=q.executeQuery()){if(!r.next())throw new SQLException("Collection Fame identity changed");}
  }
  try(var q=c.prepareStatement("INSERT INTO playerbot_collection_renown(account_id) VALUES(?) ON DUPLICATE KEY UPDATE account_id=VALUES(account_id)")){q.setInt(1,account);q.executeUpdate();}
  Totals previous;
  try(var q=c.prepareStatement("SELECT * FROM playerbot_collection_renown WHERE account_id=? FOR UPDATE")){q.setInt(1,account);try(var r=q.executeQuery()){if(!r.next())throw new SQLException("Collection Fame aggregate missing");previous=read(r);}}
  long oldCombat=0,oldLife=0,oldLegend=0;int base=token.tracked.rarityBase;boolean fresh;
  try(var q=c.prepareStatement("SELECT account_id,rarity_base,combat,life,legend FROM playerbot_collection_renown_credits WHERE player_id=? FOR UPDATE")){
   q.setInt(1,id);try(var r=q.executeQuery()){fresh=!r.next();if(!fresh){if(r.getInt(1)!=account)throw new SQLException("Collection Fame ownership changed");base=r.getInt(2);oldCombat=r.getLong(3);oldLife=r.getLong(4);oldLegend=r.getLong(5);}}
  }
  long combat=Math.max(oldCombat,Math.addExact((long)token.fame.combat(),base)),life=Math.max(oldLife,token.fame.life()),legend=Math.max(oldLegend,token.fame.legend());
  try(var q=c.prepareStatement("INSERT INTO playerbot_collection_renown_credits(player_id,account_id,rarity_base,combat,life,legend) VALUES(?,?,?,?,?,?) ON DUPLICATE KEY UPDATE combat=VALUES(combat),life=VALUES(life),legend=VALUES(legend)")){
   q.setInt(1,id);q.setInt(2,account);q.setInt(3,base);q.setLong(4,combat);q.setLong(5,life);q.setLong(6,legend);q.executeUpdate();
  }
  token.result=new Totals(Math.addExact(previous.combat(),combat-oldCombat),Math.addExact(previous.life(),life-oldLife),Math.addExact(previous.legend(),legend-oldLegend),Math.addExact(previous.creditedBots(),fresh?1:0),Math.addExact(previous.revision(),1),previous.stipendDay());
  try(var q=c.prepareStatement("UPDATE playerbot_collection_renown SET combat=?,life=?,legend=?,credited_bots=?,revision=? WHERE account_id=?")){
   q.setLong(1,token.result.combat());q.setLong(2,token.result.life());q.setLong(3,token.result.legend());q.setInt(4,token.result.creditedBots());q.setLong(5,token.result.revision());q.setInt(6,account);if(q.executeUpdate()!=1)throw new SQLException("Collection Fame aggregate changed");
  }
 }
 public static void committed(Checkpoint token){if(token==null || token.result==null)return;var tracked=token.tracked;synchronized(tracked){if(token.revision==tracked.revision)tracked.dirty=false;}if(publish(tracked.bot.getAccount().getId(),token.result))AetherfallBoonService.collectionRenownChanged(tracked.bot.getAccount().getId());}
 public static Map<String,Object> view(Player owner){
  if(!PlayerBotConfig.COLLECTION_RENOWN_ENABLED || !ready)return Map.of("enabled",false);
  int account=owner.getAccount().getId();Totals totals=ACCOUNTS.containsKey(account)?cached(account):load(account);
  return Map.of("enabled",true,"combat",totals.combat(),"life",totals.life(),"legend",totals.legend(),"renown",totals.total(),"creditedBots",totals.creditedBots(),"stipend",AetherfallBoonService.collectionStipend(totals.combat(),totals.life(),totals.legend()),"stipendClaimed",LocalDate.now().equals(totals.stipendDay()));
 }
 /** Explicitly claimed once per collection/day, paid only into the selected ticket recruit's own native wallet. */
 public static String claimStipend(Player owner,String name){
  if(!PlayerBotConfig.COLLECTION_RENOWN_ENABLED || !ready)throw new IllegalArgumentException("Companion Collection Renown is unavailable.");
  var service=PlayerBotService.getInstance();var session=service.find(owner,name);
  if(!service.claimTransfer(session))throw new IllegalArgumentException("This companion is busy; retry when its current action finishes.");
  try{synchronized(session){
   if(!service.current(session) || !PlayerBotWarehouse.privateOwner(session) || !PlayerBotGrowth.giveOnly(session.bot()))throw new IllegalArgumentException("Choose one of your active ticket recruits for the collection stipend.");
   String blocker=PlayerBotItemTransfer.blocker(session);if(!blocker.isEmpty())throw new IllegalArgumentException(blocker);
   Player bot=session.bot();long amount,total;Totals next;
   if(bot.getInventory().getKinahItem()==null)bot.getInventory().increaseKinah(0);
   try{
    PlayerBotPersistence.save(bot,service.checkpoint(session));
    try(var c=DatabaseFactory.getConnection()){
     c.setAutoCommit(false);
     try{
      Totals current;try(var q=c.prepareStatement("SELECT * FROM playerbot_collection_renown WHERE account_id=? FOR UPDATE")){q.setInt(1,owner.getAccount().getId());try(var r=q.executeQuery()){if(!r.next())throw new IllegalArgumentException("Recruit a ticket companion and save its earned collection progress first.");current=read(r);}}
      LocalDate today=LocalDate.now();if(today.equals(current.stipendDay()))throw new IllegalArgumentException("This collection's stipend has already been claimed today.");
      amount=AetherfallBoonService.collectionStipend(current.combat(),current.life(),current.legend());if(amount<=0)throw new IllegalArgumentException("This collection has no earned stipend yet.");
      total=PlayerBotTradeStore.balance(bot.getInventory().getKinah(),amount);
      try(var q=c.prepareStatement("INSERT INTO playerbot_collection_stipends(account_id,claim_day,player_id,kinah) VALUES(?,?,?,?)")){q.setInt(1,owner.getAccount().getId());q.setDate(2,java.sql.Date.valueOf(today));q.setInt(3,bot.getObjectId());q.setLong(4,amount);q.executeUpdate();}
      PlayerBotTradeStore.currency(c,bot,bot.getInventory().getKinahItem(),bot.getInventory().getKinah(),total);
      try(var q=c.prepareStatement("UPDATE playerbot_collection_renown SET stipend_day=?,revision=revision+1 WHERE account_id=?")){q.setDate(1,java.sql.Date.valueOf(today));q.setInt(2,owner.getAccount().getId());if(q.executeUpdate()!=1)throw new SQLException("Collection stipend owner changed");}
      next=new Totals(current.combat(),current.life(),current.legend(),current.creditedBots(),Math.addExact(current.revision(),1),today);
      try{c.commit();}catch(SQLException error){throw new PlayerBotTradeStore.Indeterminate(error);}
     }catch(SQLException|RuntimeException error){if(!(error instanceof PlayerBotTradeStore.Indeterminate))try{c.rollback();}catch(SQLException cleanup){error.addSuppressed(cleanup);throw new PlayerBotTradeStore.Indeterminate(cleanup);}throw error;}
    }
   }catch(PlayerBotTradeStore.Indeterminate error){PlayerBotTrade.hold(session,error);throw new IllegalStateException("Collection stipend custody needs verification; see the server log.",error);}
   catch(SQLException error){throw new IllegalStateException("Collection stipend rolled back; nothing was paid.",error);}
   try{var kinah=bot.getInventory().getKinahItem();kinah.setItemCount(total);kinah.setPersistentState(PersistentState.UPDATED);publish(owner.getAccount().getId(),next);}
   catch(RuntimeException error){PlayerBotTrade.hold(session,error);throw new IllegalStateException("Committed collection stipend needs custody verification.",error);}
   return "Paid "+amount+" Kinah to "+bot.getName()+" from today's collection stipend.";
  }}finally{service.completeTransfer(session);}
 }
 private PlayerBotCollectionRenown(){}
}
