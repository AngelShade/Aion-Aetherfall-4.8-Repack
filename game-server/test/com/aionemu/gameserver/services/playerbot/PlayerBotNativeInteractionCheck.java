package com.aionemu.gameserver.services.playerbot;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.skillengine.task.AbstractInteractionTask;

/** Native interaction callbacks on inert actors; no scheduler, World, SQL or IDs. */
public final class PlayerBotNativeInteractionCheck {
 static int checks;
 static void check(boolean ok,String reason){checks++;if(!ok)throw new AssertionError(reason);}
 static void set(Object target,String name,Object value)throws Exception {
  Field field=target.getClass().getDeclaredField(name);field.setAccessible(true);field.set(target,value);
 }
 static class Actor extends Player {
  boolean bot=true,online,spawned=true,dead;int world=1,instance=1;
  Actor(){super(null,null);}
  @Override public int getObjectId(){return 1999976137;}
  @Override public boolean isPlayerBot(){return bot;}
  @Override public boolean isOnline(){return online;}
  @Override public boolean isSpawned(){return spawned;}
  @Override public boolean isDead(){return dead;}
  @Override public int getWorldId(){return world;}
  @Override public int getInstanceId(){return instance;}
 }
 static class Work extends AbstractInteractionTask {
  final PlayerBotService service;int ticks,finished,aborted;
  Work(Actor actor,PlayerBotService service){super(actor,actor);this.service=service;actor.setInteractionTask(this);}
  @Override protected boolean interactionPresent(){return requester.isPlayerBot()?service.allowsHeadlessInteraction(requester):super.interactionPresent();}
  @Override protected boolean onInteraction(){return ++ticks==3;}
  @Override protected void onInteractionFinish(){finished++;}
  @Override protected void onInteractionAbort(){aborted++;}
  @Override protected void onInteractionStart(){}
  void tick(){runInteraction();}
 }
 @SuppressWarnings("unchecked") public static void main(String[] args)throws Exception {
  var field=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");field.setAccessible(true);var u=(sun.misc.Unsafe)field.get(null);
  var service=(PlayerBotService)u.allocateInstance(PlayerBotService.class);
  Map<Integer,PlayerBotSession> registered=new ConcurrentHashMap<>();set(service,"sessions",registered);
  var actor=(Actor)u.allocateInstance(Actor.class);actor.bot=true;actor.spawned=true;actor.world=actor.instance=1;
  var session=(PlayerBotSession)u.allocateInstance(PlayerBotSession.class);set(session,"bot",actor);
  check(!service.allowsHeadlessInteraction(actor),"Unregistered headless actors cannot keep native work running");
  registered.put(actor.getObjectId(),session);
  check(!service.allowsHeadlessInteraction(actor),"Registration without character custody is insufficient");
  try(var lease=PlayerBotLease.acquire(actor.getObjectId())) {
   check(lease!=null,"Isolated lease acquired without native ID allocation");
   check(!actor.isOnline() && service.allowsHeadlessInteraction(actor),"Current living actor with lease is intentionally headless");
   var work=new Work(actor,service);work.tick();work.tick();
   check(work.ticks==2 && work.finished==0 && actor.getInteractionTask()==work,"Native progress callbacks continue across ticks without a client");
   work.tick();check(work.ticks==3 && work.finished==1 && work.aborted==0 && actor.getInteractionTask()==null,"Native completion callback owns finalization");
   for(int invalid=0;invalid<6;invalid++) {
    actor.spawned=true;actor.dead=false;actor.world=actor.instance=1;set(session,"closing",false);set(service,"shuttingDown",false);registered.put(actor.getObjectId(),session);
    work=new Work(actor,service);
    switch(invalid){case 0->actor.dead=true;case 1->actor.spawned=false;case 2->set(session,"closing",true);case 3->set(service,"shuttingDown",true);case 4->registered.clear();default->set(session,"bot",u.allocateInstance(Actor.class));}
    check(!service.allowsHeadlessInteraction(actor),"Death/despawn/dismissal/shutdown/unregistration/identity replacement deny presence: "+invalid);
    work.tick();check(work.ticks==0 && work.aborted==1 && work.finished==1 && actor.getInteractionTask()==null,"Invalid actor aborts before native progression: "+invalid);
    set(session,"bot",actor);
   }
   actor.spawned=true;actor.dead=false;set(service,"shuttingDown",false);registered.put(actor.getObjectId(),session);
   for(boolean map:new boolean[]{true,false}) {
    actor.world=actor.instance=1;work=new Work(actor,service);if(map)actor.world=2;else actor.instance=2;
    work.tick();check(work.ticks==0 && work.aborted==1,"Native work cannot survive a map/instance handoff");
   }
   actor.world=actor.instance=1;work=new Work(actor,service);actor.setInteractionTask(null);work.tick();
   check(work.ticks==0 && work.aborted==1,"Superseded native task cannot progress");
   actor.bot=false;actor.online=true;work=new Work(actor,service);work.tick();
   check(work.ticks==1 && work.finished==0,"Connected human retains native work behavior");
   actor.online=false;work.tick();check(work.ticks==1 && work.finished==1 && work.aborted==0,"Disconnected human retains original stop behavior");
  }
  actor.bot=true;check(!service.allowsHeadlessInteraction(actor),"Released native custody cannot be substituted by stale registration");
  System.out.println("OK: "+checks+" native timed interaction progression, presence, custody, shutdown and transfer checks; no scheduler/World/SQL/IDs");
 }
}
