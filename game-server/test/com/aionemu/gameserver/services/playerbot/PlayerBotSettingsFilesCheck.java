package com.aionemu.gameserver.services.playerbot;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.lang.reflect.*;
import sun.misc.Unsafe;

/** Deterministic Windows-denial policy and actual care-file serialization; isolated files only. */
public final class PlayerBotSettingsFilesCheck {
 static int checks;
 static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}
 static void field(Object o,String name,Object value)throws Exception {var f=o.getClass().getDeclaredField(name);f.setAccessible(true);f.set(o,value);}
 static PlayerBotQuestSync.State state()throws Exception {
  var f=Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);var state=(PlayerBotQuestSync.State)((Unsafe)f.get(null)).allocateInstance(PlayerBotQuestSync.State.class);
  field(state,"account",1999999700);field(state,"character",1999999701);field(state,"skipped",new HashSet<>(Set.of(20)));
  field(state,"approved",new HashSet<>(Set.of(30)));field(state,"managed",new HashSet<>(Set.of(40)));
  field(state,"ownerCompletions",new HashMap<>(Map.of(40,2)));field(state,"spentDay","2026-10-05");
  state.reserve=10000;state.dailyBudget=50000;state.spent=123;state.partySync=true;return state;
 }
 static void verifyCare(Path path)throws Exception {
  var p=new Properties();try(var in=Files.newInputStream(path)){p.load(in);}
  check(p.getProperty("account").equals("1999999700") && p.getProperty("character").equals("1999999701"),"Owner/character retained");
  check(p.getProperty("skipped").equals("20")&&p.getProperty("approved").equals("30")&&p.getProperty("together.40").equals("2"),"Native quest witnesses and consent retained");
  check(p.getProperty("partySync").equals("true")&&p.getProperty("enchant").equals("false")&&p.getProperty("salvage").equals("false"),"Opt-in care policy retained");
  check(p.getProperty("reserve").equals("10000")&&p.getProperty("dailyBudget").equals("50000")&&p.getProperty("spent").equals("123")&&p.getProperty("spentDay").equals("2026-10-05"),"Budget/provenance metadata retained");
 }
 public static void main(String[] args)throws Exception {
  if(args.length==2 && args[0].equals("native-lock")){nativeLock(Path.of(args[1]));return;}
  Path directory=Files.createTempDirectory("playerbot-settings-check-");Path original=directory.resolve("committed.properties"),temporary=directory.resolve("new.tmp");
  try {
   Files.writeString(original,"old=unchanged\n");Files.writeString(temporary,"new=complete\n");List<Long> pauses=new ArrayList<>();int[] attempts={0};
   PlayerBotSettingsFiles.replace(temporary,original,(from,to,atomic)->{
    check(atomic,"Atomic rename retained while retrying");
    if(attempts[0]++<2){check(Files.readString(to).equals("old=unchanged\n"),"Failure cannot truncate existing settings");throw new AccessDeniedException(from.toString(),to.toString(),"Sharing violation");}
    Files.move(from,to,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
   },pauses::add);
   check(attempts[0]==3 && pauses.equals(List.of(25L,50L)),"Transient denial recovered with bounded backoff");
   check(Files.readString(original).equals("new=complete\n")&&!Files.exists(temporary),"Complete file committed once");
   Files.writeString(temporary,"must not overwrite\n");pauses.clear();attempts[0]=0;
   try{PlayerBotSettingsFiles.replace(temporary,original,(a,b,c)->{attempts[0]++;throw new AccessDeniedException(a.toString());},pauses::add);throw new AssertionError("Permanent denial hidden");}
   catch(AccessDeniedException expected){check(attempts[0]==4&&pauses.equals(List.of(25L,50L,100L)),"Permanent denial visible after 175 ms maximum delay");}
   check(Files.readString(original).equals("new=complete\n")&&Files.exists(temporary),"Persistent failure preserves committed file");
   attempts[0]=0;pauses.clear();
   try{PlayerBotSettingsFiles.replace(temporary,original,(a,b,c)->{attempts[0]++;throw new IOException("Disk failure");},pauses::add);throw new AssertionError("Disk failure hidden");}
   catch(IOException expected){check(attempts[0]==1&&pauses.isEmpty(),"Unrelated IO errors are not retried");}
   attempts[0]=0;pauses.clear();
   PlayerBotSettingsFiles.replace(temporary,original,(a,b,atomic)->{
    if(attempts[0]++==0)throw new AtomicMoveNotSupportedException(a.toString(),b.toString(),"fixture");
    check(!atomic,"Unsupported atomic move retains documented fallback");
    if(attempts[0]==2)throw new AccessDeniedException(b.toString());Files.move(a,b,StandardCopyOption.REPLACE_EXISTING);
   },pauses::add);
   check(attempts[0]==3 && pauses.equals(List.of(25L)),"Fallback sharing denial also receives bounded retry");
   Files.writeString(temporary,"interrupted\n");
   try{PlayerBotSettingsFiles.replace(temporary,original,(a,b,c)->{throw new AccessDeniedException(b.toString());},delay->{throw new InterruptedException("fixture");});throw new AssertionError("Interruption hidden");}
   catch(InterruptedIOException expected){check(Thread.currentThread().isInterrupted(),"Interruption flag retained");check(expected.getCause() instanceof InterruptedException && expected.getSuppressed().length==1,"Interrupted error retains denied cause");Thread.interrupted();}
   var care=state();Path carePath=care.path();check(!Files.exists(carePath),"Private fixture settings absent");
   try {care.save();verifyCare(carePath);care.approved.add(31);care.save();var p=new Properties();try(var in=Files.newInputStream(carePath)){p.load(in);}check(p.getProperty("approved").equals("30,31"),"Actual State.save updates existing file");}
   finally {Files.deleteIfExists(carePath);}
  } finally {try(var paths=Files.list(directory)){for(Path p:paths.toList())Files.delete(p);}Files.delete(directory);}
  System.out.println("OK: "+checks+" sharing-violation retries, permanent denial, atomic/fallback, interruption and production care serialization checks; no DB/world/ID writes");
 }
 static void nativeLock(Path signals)throws Exception {
  var care=state();Path path=care.path();check(Files.exists(path),"Windows locked private fixture present");
  Files.writeString(signals.resolve("ready"),"ready");long deadline=System.nanoTime()+10_000_000_000L;
  while(!Files.exists(signals.resolve("go"))){if(System.nanoTime()>deadline)throw new AssertionError("Lock handshake timeout");Thread.sleep(5);}
  String original=Files.readString(path);long start=System.nanoTime();
  try{care.save();throw new AssertionError("Windows file lock was ignored");}
  catch(IllegalStateException expected){check(expected.getCause() instanceof AccessDeniedException,"Native Windows AccessDenied reaches production save");}
  check(Files.readString(path).equals(original),"Locked destination preserved");
  check((System.nanoTime()-start)/1_000_000<2000,"Native lock failure bounded, not multi-second scheduler stall");
  Files.writeString(signals.resolve("denied"),"denied");deadline=System.nanoTime()+10_000_000_000L;
  while(!Files.exists(signals.resolve("released"))){if(System.nanoTime()>deadline)throw new AssertionError("Unlock handshake timeout");Thread.sleep(5);}
  care.save();verifyCare(path);
  System.out.println("OK: "+checks+" real Windows deny-delete lock and recovery checks; isolated fixture file only");
 }
}
