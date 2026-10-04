import java.lang.instrument.Instrumentation;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import com.sun.tools.attach.VirtualMachine;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.*;
import com.aionemu.gameserver.model.account.*;
import com.aionemu.gameserver.model.gameobjects.player.*;
import com.aionemu.gameserver.model.gameobjects.state.CreatureState;
import com.aionemu.gameserver.model.items.storage.*;
import com.aionemu.gameserver.services.player.PlayerService;
import com.aionemu.gameserver.services.playerbot.*;
import com.aionemu.gameserver.instance.handlers.GeneralInstanceHandler;
import com.aionemu.gameserver.world.*;
import com.aionemu.gameserver.utils.idfactory.IDFactory;
import com.aionemu.gameserver.skillengine.SkillEngine;
public class PlayerBotRevivalCheckAgent {
 public static void agentmain(String report,Instrumentation ignored)throws Exception {
  var lines=new ArrayList<String>();Player p=null;WorldMapInstance instance=null;
  try {
   int id=IDFactory.getInstance().nextId();var c=new PlayerCommonData(id);c.setName("RevivalFixture"+id);c.setRace(Race.ELYOS);c.setGender(Gender.FEMALE);c.setPlayerClass(PlayerClass.CLERIC);c.setDaeva(true);c.setLevel(20);
   var d=new PlayerAccountData(c,new PlayerAppearance());var a=new Account(2000000000);a.setName("RevivalFixture");a.addPlayerAccountData(d);a.setAccountWarehouse(new PlayerStorage(null,StorageType.ACCOUNT_WAREHOUSE));
   p=PlayerService.newPlayer(d,a);p.setPlayerBotOwner(Integer.MAX_VALUE);PlayerBotGenerationOptions.initialize(p);p.setMotions(new com.aionemu.gameserver.model.gameobjects.player.motion.MotionList(p));
   var world=World.getInstance();var map=world.getWorldMap(300040000);instance=WorldMapInstanceFactory.createWorldMapInstance(map,0,GeneralInstanceHandler::new,6);world.setPosition(p,map.getMapId(),instance.getInstanceId(),500,500,100,(byte)0);world.storeObject(p);world.spawn(p);
   var type=Class.forName("com.aionemu.gameserver.services.playerbot.PlayerBotRevival");var ready=type.getDeclaredMethod("ready",Player.class);ready.setAccessible(true);var close=type.getDeclaredMethod("close",Player.class);close.setAccessible(true);
   var states=type.getDeclaredField("STATES");states.setAccessible(true);var values=(Map<?,?>)states.get(null);
   p.getLifeStats().setCurrentHp(0);
   if((boolean)ready.invoke(null,p)||!p.isDead())throw new AssertionError("Death animation was skipped");
   var state=values.get(id);var death=state.getClass().getDeclaredField("death");death.setAccessible(true);death.setLong(state,System.currentTimeMillis()-2600);
   if((boolean)ready.invoke(null,p)||!p.isDead())throw new AssertionError("Dead actor revived without native authorization");
   lines.add("OK: actual native death waits for its animation and does not invent resurrection rights.");
   p.setPlayerResActivate(true);p.setResurrectionSkill(8296);
   if((boolean)ready.invoke(null,p)||p.isDead()||p.getResStatus()||p.isInState(CreatureState.DEAD))throw new AssertionError("Native authorized revive failed");
   int hp=p.getLifeStats().getCurrentHp(),mp=p.getLifeStats().getCurrentMp();
   if((boolean)ready.invoke(null,p))throw new AssertionError("Companion actions resumed during resurrection animation");
   var recovery=state.getClass().getDeclaredField("recovery");recovery.setAccessible(true);recovery.setLong(state,System.currentTimeMillis()-2100);
   if(!(boolean)ready.invoke(null,p)||p.getLifeStats().getCurrentHp()!=hp||p.getLifeStats().getCurrentMp()!=mp||values.containsKey(id))throw new AssertionError("Recovery altered native resources or leaked state");
   lines.add("OK: native skill resurrection consumes authorization, clears corpse state and blocks actions until recovery completes; refresh retains HP/MP.");
   p.getSkillList().addSkill(p,4195,1);p.getLifeStats().setCurrentHpPercent(50);p.getLifeStats().setCurrentMpPercent(100);int beforeHp=p.getLifeStats().getCurrentHp(),beforeMp=p.getLifeStats().getCurrentMp();
   var skill=SkillEngine.getInstance().getSkillFor(p,DataManager.SKILL_DATA.getSkillTemplate(4195),p);
   if(skill==null||!skill.useSkill())throw new AssertionError("Production Healing Wind did not cast after revival");
   long end=System.currentTimeMillis()+8000;while(System.currentTimeMillis()<end&&(p.isCasting()||p.getLifeStats().getCurrentHp()==beforeHp))Thread.sleep(100);
   if(p.getLifeStats().getCurrentHp()<=beforeHp||p.getLifeStats().getCurrentMp()>=beforeMp)throw new AssertionError("Native revived cast did not heal/pay MP");
   lines.add("OK: revived native companion casts production Healing Wind, heals and pays MP.");
   p.setState(CreatureState.DEAD);p.setState(CreatureState.FLOATING_CORPSE);hp=p.getLifeStats().getCurrentHp();PlayerBotRevival.refresh(p);
   if(p.isInState(CreatureState.DEAD)||p.isInState(CreatureState.FLOATING_CORPSE)||p.getLifeStats().getCurrentHp()!=hp)throw new AssertionError("Living corpse-state recovery failed");
   close.invoke(null,p);lines.add("OK: living stale corpse flags repaired without granting life; state cleanup passes. No database or human-character writes.");
  }catch(Throwable e){var s=new java.io.StringWriter();e.printStackTrace(new java.io.PrintWriter(s));lines.add("FAIL: "+s);}
  finally {if(p!=null){p.getController().cancelCurrentSkill(null);p.getLifeStats().cancelAllTasks();p.getEffectController().removeAllEffects(true);World.getInstance().removeObject(p);for(var item:p.getAllItems())IDFactory.getInstance().releaseId(item.getObjectId());IDFactory.getInstance().releaseId(p.getObjectId());}if(instance!=null)World.getInstance().getWorldMap(instance.getMapId()).removeWorldMapInstance(instance.getInstanceId());}
  Files.write(Path.of(report),lines,StandardOpenOption.CREATE_NEW);
  if(lines.stream().anyMatch(s->s.startsWith("FAIL:")))throw new AssertionError("Native revival fixture failed; diagnostic saved");
 }
 public static void main(String[] a)throws Exception{var vm=VirtualMachine.attach(a[0]);try{vm.loadAgent(Path.of(a[1]).toAbsolutePath().toString(),Path.of(a[2]).toAbsolutePath().toString());}finally{vm.detach();}}
}
