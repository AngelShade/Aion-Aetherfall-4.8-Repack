package com.aionemu.gameserver;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggerContextListener;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.aionemu.commons.database.DatabaseFactory;
import com.aionemu.gameserver.configs.main.ShutdownConfig;
import com.aionemu.gameserver.utils.ThreadPoolManager;
import com.zaxxer.hikari.HikariDataSource;

/** Actual shutdown orchestration with inert save services, executors and a connection-free native pool. */
public final class DatabaseShutdownCheck {
 public static final List<String> events = new ArrayList<>();
 private static int checks;
 private static void check(boolean condition, String reason) {
  checks++;
  if (!condition) throw new AssertionError(reason + ": " + events);
 }
 public static void event(String event) { events.add(event); }
 private static void set(Object target, Class<?> type, String name, Object value) throws Exception {
  Field field = type.getDeclaredField(name); field.setAccessible(true); field.set(target, value);
 }
 static final class Pool extends HikariDataSource {
  int closes;
  @Override public void close() { closes++; event("database-close"); super.close(); }
 }
 static final class Worker extends ThreadPoolExecutor {
  final String name;
  boolean terminated, finish = true;
  Worker(String name) { super(0, 1, 0, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>()); this.name = name; }
  @Override public void shutdown() { event(name + "-shutdown"); terminated = finish; }
  @Override public boolean isTerminated() { return terminated; }
  @Override public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
   if (!terminated) Thread.sleep(unit.toMillis(timeout));
   return terminated;
  }
 }
 static final class Scheduled extends ScheduledThreadPoolExecutor {
  boolean terminated, finish = true;
  Scheduled() { super(1); }
  @Override public void shutdown() { event("scheduled-shutdown"); terminated = finish; }
  @Override public boolean isTerminated() { return terminated; }
  @Override public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
   if (!terminated) Thread.sleep(unit.toMillis(timeout));
   return terminated;
  }
 }
 public static void main(String[] args) throws Exception {
  var manager = ThreadPoolManager.getInstance();
  // Retire only the isolated fixture JVM's empty native executors before injecting inert ones.
  for (String name : List.of("scheduledPool", "instantPool", "longRunningPool")) {
   var field = ThreadPoolManager.class.getDeclaredField(name); field.setAccessible(true);
   var executor = (ThreadPoolExecutor) field.get(manager);
   executor.shutdown(); check(executor.awaitTermination(2, TimeUnit.SECONDS), "Empty fixture executor drains");
  }
  var scheduled = new Scheduled(); var instant = new Worker("instant"); var longRunning = new Worker("long");
  set(manager, ThreadPoolManager.class, "scheduledPool", scheduled);
  set(manager, ThreadPoolManager.class, "instantPool", instant);
  set(manager, ThreadPoolManager.class, "longRunningPool", longRunning);
  check(!manager.isTerminated(), "Unshut executors cannot authorize database closure");
  scheduled.terminated = instant.terminated = longRunning.terminated = true;
  check(manager.isTerminated(), "All three terminated executors authorize closure");
  for (String name : List.of("scheduled", "instant", "long")) {
   scheduled.terminated = !name.equals("scheduled"); instant.terminated = !name.equals("instant"); longRunning.terminated = !name.equals("long");
   check(!manager.isTerminated(), name + " executor independently prevents closure");
  }
  DatabaseFactory.shutdown();
  var field = DatabaseFactory.class.getDeclaredField("dataSource"); field.setAccessible(true);
  check(field.get(null) == null, "Absent pool shutdown does not initialize connections");
  var context = (LoggerContext) LoggerFactory.getILoggerFactory();
  var listener = new LoggerContextListener() {
   public boolean isResetResistant() { return true; }
   public void onStart(LoggerContext context) {}
   public void onReset(LoggerContext context) {}
   public void onLevelChange(Logger logger, Level level) {}
   public void onStop(LoggerContext context) { event("logger-stop"); }
  };
  context.addListener(listener);
  ShutdownConfig.DELAY = 0; ShutdownConfig.RESTART_SCHEDULE = null;
  var hook = ShutdownHook.getInstance(); var pool = new Pool(); field.set(null, pool); events.clear();
  hook.run();
  check(events.equals(List.of("bots-save", "network-close", "market-stop", "players-save", "stats", "periodic-save", "time-save", "cron-stop", "scheduled-shutdown", "instant-shutdown", "long-shutdown", "database-close", "logger-stop")), "All save services finish before drain and pool closure precedes logger stop");
  check(pool.closes == 1 && pool.isClosed(), "Native pool is closed exactly once");
  DatabaseFactory.shutdown(); DatabaseFactory.init();
  check(pool.closes == 1 && field.get(null) == pool, "Repeated close and late init cannot reopen the closed pool");
  context.start(); context.addListener(listener); pool = new Pool(); field.set(null, pool);
  var errors = new ListAppender<ILoggingEvent>(); errors.start(); context.getLogger(ShutdownHook.class).addAppender(errors);
  scheduled.terminated = instant.terminated = longRunning.terminated = false; instant.finish = false; events.clear();
  hook.run();
  check(!manager.isTerminated(), "Timed native shutdown cannot claim a completed drain");
  check(pool.closes == 0 && !pool.isClosed(), "Incomplete drain leaves pool available for pending saves");
  check(errors.list.stream().anyMatch(error -> error.getLevel() == Level.ERROR && error.getFormattedMessage().contains("shutdown workers did not finish draining")), "Incomplete drain logs an explicit pool-cleanup failure");
  check(events.contains("periodic-save") && events.getLast().equals("logger-stop") && !events.contains("database-close"), "Failed drain preserves save and log flush ordering");
  instant.terminated = true;
  DatabaseFactory.shutdown(); check(pool.closes == 1 && pool.isClosed(), "Explicit cleanup succeeds after delayed executor completes");
  System.out.println("OK: " + checks + " native database shutdown checks; no World/SQL/native IDs");
 }
}
