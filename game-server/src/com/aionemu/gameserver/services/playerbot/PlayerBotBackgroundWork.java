package com.aionemu.gameserver.services.playerbot;

import java.util.concurrent.Executor;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/** One native creation/load job at a time; no scheduler backlog or service monitor around database initialization. */
final class PlayerBotBackgroundWork {
 private final AtomicBoolean busy=new AtomicBoolean();private volatile boolean closed;
 private CompletableFuture<Void> completion=CompletableFuture.completedFuture(null);
 boolean submit(Executor executor,Runnable job) {
  CompletableFuture<Void> done;
  synchronized(this) {
   if(closed || !busy.compareAndSet(false,true))return false;
   completion=done=new CompletableFuture<>();
  }
  try{executor.execute(()->{try{if(!closed)job.run();}finally{busy.set(false);done.complete(null);}});return true;}
  catch(RuntimeException e){busy.set(false);done.complete(null);throw e;}
 }
 boolean busy(){return busy.get();}
 synchronized void close(){closed=true;}
 void awaitCompletion() throws InterruptedException {
  CompletableFuture<Void> pending;
  synchronized(this){if(!closed)throw new IllegalStateException("Close admission before draining native work");pending=completion;}
  try{pending.get();}catch(java.util.concurrent.ExecutionException e){throw new IllegalStateException(e);}
 }
}
