package com.aionemu.gameserver.services.playerbot;

import java.util.concurrent.FutureTask;
import com.aionemu.gameserver.controllers.ObserveController;
import com.aionemu.gameserver.controllers.PlayerController;
import com.aionemu.gameserver.controllers.observer.ItemUseObserver;
import com.aionemu.gameserver.controllers.observer.StartMovingListener;
import com.aionemu.gameserver.model.PlayerClass;
import com.aionemu.gameserver.model.TaskId;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.skillengine.condition.PlayerMovedCondition;
import com.aionemu.gameserver.skillengine.model.Skill;
import sun.misc.Unsafe;

/** Native task/observer/condition entrypoints on world-free probes; no casts, DB, world or scheduler. */
public final class PlayerBotItemUseCheck {
 static int checks;
 static void check(boolean value, String label) { checks++; if (!value) throw new AssertionError(label); }
 static final class Actor extends Player {
  PlayerController controller; ObserveController observers;
  Actor() { super(null,null); }
  @Override public PlayerController getController() { return controller; }
  @Override public ObserveController getObserveController() { return observers; }
 }
 static void set(Object object,Class<?> owner,String name,Object value) throws Exception {
  var field=owner.getDeclaredField(name);field.setAccessible(true);field.set(object,value);
 }
 public static void main(String[] args) throws Exception {
  var field=Unsafe.class.getDeclaredField("theUnsafe");field.setAccessible(true);var unsafe=(Unsafe)field.get(null);
  for(var pc:new PlayerClass[]{PlayerClass.BARD,PlayerClass.SORCERER,PlayerClass.CLERIC})
   for(String state:new String[]{"absent","active","complete","cancelled","failed"})
    for(boolean combat:new boolean[]{false,true}) {
     var bot=(Actor)unsafe.allocateInstance(Actor.class);
     bot.controller=new PlayerController();bot.controller.setOwner(bot);bot.observers=new ObserveController();
     var skill=(Skill)unsafe.allocateInstance(Skill.class);var moving=new StartMovingListener();
     set(skill,Skill.class,"moveListener",moving);bot.observers.attach(moving);
     var stationary=new PlayerMovedCondition();set(stationary,PlayerMovedCondition.class,"allow",false);
     var future=new FutureTask<Void>(() -> { if(state.equals("failed"))throw new IllegalStateException("fixture");return null; });
     if(state.equals("complete") || state.equals("failed"))future.run();
     if(state.equals("cancelled"))future.cancel(false);
     if(!state.equals("absent"))bot.controller.addTask(TaskId.ITEM_USE,future);
     int[] cancellations={0};
     bot.observers.addObserver(new ItemUseObserver(bot) {
      @Override protected void onAbort() { cancellations[0]++;bot.controller.cancelTask(TaskId.ITEM_USE); }
     });
     boolean paused=PlayerBotItemUse.pause(bot,combat);
     check(paused==(state.equals("active")&&!combat),pc+" pauses only for an unfinished item task outside combat: "+state);
     check(cancellations[0]==(state.equals("active")&&combat?1:0),pc+" aborts only an active item operation: "+state);
     check(stationary.validate(skill),pc+" stationary spell remains valid after the real item policy: "+state);
     check(!moving.isEffectorMoved(),pc+" policy must not fabricate movement: "+state);
     if(!state.equals("active")&&!state.equals("absent")) {
      check(bot.controller.hasTask(TaskId.ITEM_USE)&&!bot.controller.hasScheduledTask(TaskId.ITEM_USE),"Native completed task remains registered");
      // Reproduce the old production path: a retained finished task plus combat
      // fabricated movement and failed this exact native cast-completion condition.
      if(combat){bot.observers.notifyMoveObservers();check(!stationary.validate(skill),"Old item-task path reproduces stationary cast failure");}
     }
    }
  System.out.println("OK: "+checks+" native item-task and stationary cast-completion checks; no native casts or world tasks");
 }
}
