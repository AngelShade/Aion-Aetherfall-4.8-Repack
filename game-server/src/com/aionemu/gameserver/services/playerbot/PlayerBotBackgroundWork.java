package com.aionemu.gameserver.services.playerbot;

import java.util.concurrent.Executor;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/** Bounded native jobs; creation/load/team work stays serial unless explicitly given independent slots. */
final class PlayerBotBackgroundWork {
 private final int capacity;private int running;private volatile boolean closed;
 private final java.util.Set<CompletableFuture<Void>> pending=new java.util.HashSet<>();
 PlayerBotBackgroundWork(){this(1);}
 PlayerBotBackgroundWork(int capacity){if(capacity<1 || capacity>4)throw new IllegalArgumentException("Invalid native worker capacity");this.capacity=capacity;}
 boolean submit(Executor executor,Runnable job) {
  CompletableFuture<Void> done;var finished=new AtomicBoolean();
  synchronized(this) {
   if(closed || running>=capacity)return false;
   running++;done=new CompletableFuture<>();
   pending.add(done);
  }
  try{executor.execute(()->{try{if(!closed)job.run();}finally{finish(done,finished);}});return true;}
  catch(RuntimeException|Error e){finish(done,finished);throw e;}
 }
 private void finish(CompletableFuture<Void> done,AtomicBoolean finished){if(finished.compareAndSet(false,true)){synchronized(this){running--;pending.remove(done);done.complete(null);}}}
 synchronized boolean busy(){return running>=capacity;}
 synchronized int available(){return closed?0:capacity-running;}
 synchronized int running(){return running;}
 synchronized void close(){closed=true;}
 void awaitCompletion() throws InterruptedException {
  CompletableFuture<Void> pending;
  synchronized(this){if(!closed)throw new IllegalStateException("Close admission before draining native work");pending=CompletableFuture.allOf(this.pending.toArray(CompletableFuture[]::new));}
  try{pending.get();}catch(java.util.concurrent.ExecutionException e){throw new IllegalStateException(e);}
 }
}
