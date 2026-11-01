package com.aionemu.gameserver.services.playerbot;

import java.sql.Connection;
import java.sql.SQLException;

import com.aionemu.commons.database.DatabaseFactory;
import com.aionemu.gameserver.dao.PlayerBotRecruitmentDAO;
import com.aionemu.gameserver.dao.PlayerBotRecruitmentDAO.Operation;
import com.aionemu.gameserver.dao.PlayerBotRecruitmentDAO.Receipt;
import com.aionemu.gameserver.dao.PlayerBotRecruitmentDAO.Wallet;

/** Native account ticket transactions and durable completion delivery. */
public final class PlayerBotTickets {
 private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(PlayerBotTickets.class);
 private static final java.util.Map<String,PlayerBotRecruitmentDAO.Entitlement> awaiting=new java.util.concurrent.ConcurrentHashMap<>();
 private static boolean prepared;
 private static java.util.concurrent.ScheduledFuture<?> delivery;
 private static synchronized void prepare() {
  if(prepared)return;
  try(var c=DatabaseFactory.getConnection()){PlayerBotRecruitmentDAO.prepareDelivery(c);prepared=true;}
  catch(SQLException e){throw new IllegalStateException("Cannot prepare ticket delivery",e);}
 }
 public static synchronized void start() {
  if(delivery!=null)return;
  delivery=com.aionemu.gameserver.utils.ThreadPoolManager.getInstance().scheduleAtFixedRate(PlayerBotTickets::drain,1000,10000);
 }
 public static void initialize() {
  PlayerBotRecycling.initialize();
  if(com.aionemu.gameserver.configs.main.PlayerBotConfig.TICKETS_ENABLED){start();return;}
  // Turning off new earning/spending does not erase already recorded debt.
  try(var c=DatabaseFactory.getConnection();var s=c.prepareStatement("SELECT receipt_key FROM playerbot_ticket_entitlements LIMIT 1");var r=s.executeQuery()){if(r.next())start();}
  catch(SQLException e){if(!"42S02".equals(e.getSQLState()) && e.getErrorCode()!=1146)log.error("Cannot check pending ticket delivery",e);}
 }
 /** Record entitlement before asynchronous wallet delivery. A database outage remains retryable, never abandoned after three tries. */
 public static void award(int account,String key,long tickets) {
  var award=new PlayerBotRecruitmentDAO.Entitlement(account,key,tickets);awaiting.putIfAbsent(key,award);start();
  try{persist(award);awaiting.remove(key,award);}catch(RuntimeException e){log.error("Recruitment Ticket completion {} awaits database recovery",key,e);}
 }
 private static void persist(PlayerBotRecruitmentDAO.Entitlement award) {prepare();transact(award.account(),c->{PlayerBotRecruitmentDAO.enqueue(c,award.account(),award.key(),award.tickets());return null;});}
 private static void drain() {
  try {
   prepare();int budget=100;
   for(var award:awaiting.values()){if(budget--<=0)break;persist(award);awaiting.remove(award.key(),award);}
   java.util.List<PlayerBotRecruitmentDAO.Entitlement> pending;
   try(var c=DatabaseFactory.getConnection()){pending=PlayerBotRecruitmentDAO.pending(c);}
   for(var award:pending)transact(award.account(),c->{PlayerBotRecruitmentDAO.deliver(c,award);return null;});
  }catch(SQLException|RuntimeException e){log.warn("Recruitment Ticket outbox retained for retry: {}",e.toString());}
 }
 private interface Work<T> { T run(Connection c)throws SQLException; }
 private static <T> T transact(int account,Work<T> work) {
  if(account<=0 || PlayerBotPopulationPolicy.serverAccount(account))throw new IllegalArgumentException("Recruitment Tickets belong to player accounts");
  try(var c=DatabaseFactory.getConnection()) {
   c.setAutoCommit(false);
   try {
    T result=work.run(c);c.commit();return result;
   } catch(SQLException | RuntimeException e) {
    c.rollback();throw e;
   }
  } catch(SQLException e) {
   throw new IllegalStateException("Recruitment Ticket storage failed",e);
  }
 }
 public static Wallet wallet(int account) { return transact(account,c->PlayerBotRecruitmentDAO.wallet(c,account)); }
 public static Operation unfinished(int account) { return transact(account,c->PlayerBotRecruitmentDAO.open(c,account)); }
 public static Operation latest(int account) { return transact(account,c->{var op=PlayerBotRecruitmentDAO.latest(c,account);if(op!=null)PlayerBotRecruitmentDAO.ready(c,op);return op;}); }
 public static Operation reserve(Operation op) { return transact(op.account(),c->PlayerBotRecruitmentDAO.reserve(c,op.account(),op.id(),()->com.aionemu.gameserver.utils.idfactory.IDFactory.getInstance().nextId())); }
 public static Receipt grant(int account,String key,String kind,long tickets) { return transact(account,c->PlayerBotRecruitmentDAO.grant(c,account,key,kind,tickets)); }
 public static Receipt recycle(int account,String key) { return transact(account,c->PlayerBotRecruitmentDAO.recycle(c,account,key)); }
 public static Operation begin(int account,String requestKey) { return transact(account,c->PlayerBotRecruitmentDAO.begin(c,account,requestKey,PlayerBotRecruitmentPolicy::roll)); }

 private PlayerBotTickets() {}
}
