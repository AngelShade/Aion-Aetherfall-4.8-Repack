import java.lang.instrument.Instrumentation;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import com.sun.tools.attach.VirtualMachine;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.*;
import com.aionemu.gameserver.model.account.*;
import com.aionemu.gameserver.model.gameobjects.*;
import com.aionemu.gameserver.model.gameobjects.player.*;
import com.aionemu.gameserver.model.gameobjects.state.CreatureState;
import com.aionemu.gameserver.model.items.storage.*;
import com.aionemu.gameserver.model.team.group.*;
import com.aionemu.gameserver.services.player.PlayerService;
import com.aionemu.gameserver.services.playerbot.*;
import com.aionemu.gameserver.instance.handlers.GeneralInstanceHandler;
import com.aionemu.gameserver.utils.idfactory.IDFactory;
import com.aionemu.gameserver.world.*;

/** Real native item casts and resurrection on isolated unsaved world actors. */
public class PlayerBotSuppliesRecoveryCheckAgent2 {
 static void set(Object o,String name,Object value)throws Exception{for(Class<?> c=o.getClass();c!=null;c=c.getSuperclass())try{var f=c.getDeclaredField(name);f.setAccessible(true);f.set(o,value);return;}catch(NoSuchFieldException e){}throw new NoSuchFieldException(name);}
 static Object field(Class<?> c,String name)throws Exception{var f=c.getDeclaredField(name);f.setAccessible(true);return f.get(null);}
 static Object call(Class<?> c,String name,Class<?>[] types,Object... values)throws Exception{var m=c.getDeclaredMethod(name,types);m.setAccessible(true);try{return m.invoke(null,values);}catch(InvocationTargetException e){if(e.getCause() instanceof Exception cause)throw cause;throw e;}}
 static Player make(PlayerClass pc,int owner)throws Exception {
  int id=IDFactory.getInstance().nextId();var common=new PlayerCommonData(id);common.setName("SuppliesFixture"+id);common.setRace(Race.ELYOS);common.setGender(Gender.FEMALE);common.setPlayerClass(pc);common.setDaeva(true);common.setLevel(39);common.setOnline(true);
  var data=new PlayerAccountData(common,new PlayerAppearance());var a=new Account(2000000000);a.setName("SuppliesFixture");a.addPlayerAccountData(data);a.setAccountWarehouse(new PlayerStorage(null,StorageType.ACCOUNT_WAREHOUSE));var p=PlayerService.newPlayer(data,a);p.setPlayerBotOwner(owner);PlayerBotGenerationOptions.initialize(p);p.setMotions(new com.aionemu.gameserver.model.gameobjects.player.motion.MotionList(p));call(Class.forName("com.aionemu.gameserver.services.playerbot.PlayerBotGenerated"),"equipStarterSet",new Class<?>[]{Player.class},p);return p;
 }
 static void dead(Player bot){bot.getController().cancelCurrentSkill(null);bot.getEffectController().removeAllEffects(true);bot.getLifeStats().setCurrentHp(0);bot.setState(CreatureState.DEAD);}
 @SuppressWarnings("unchecked") public static void agentmain(String argument,Instrumentation ignored)throws Exception {
  var lines=new ArrayList<String>();var players=new ArrayList<Player>();var sessions=new ArrayList<PlayerBotSession>();var leases=new ArrayList<PlayerBotLease>();WorldMapInstance instance=null;
  var service=PlayerBotService.getInstance();var f=PlayerBotService.class.getDeclaredField("sessions");f.setAccessible(true);var live=(Map<Integer,PlayerBotSession>)f.get(service);
  Class<?> supplies=PlayerBotSupplies.class,recovery=PlayerBotRecovery.class;var states=(Map<Integer,Object>)field(PlayerBotTemporary.class,"STATES");var stateCtor=Class.forName(PlayerBotTemporary.class.getName()+"$State").getDeclaredConstructor();stateCtor.setAccessible(true);
  synchronized(service){try {
   var world=World.getInstance();var map=world.getWorldMap(300040000);instance=WorldMapInstanceFactory.createWorldMapInstance(map,0,GeneralInstanceHandler::new,6);
   var owner=make(PlayerClass.CLERIC,Integer.MAX_VALUE);players.add(owner);var u=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");u.setAccessible(true);var unsafe=(sun.misc.Unsafe)u.get(null);var sink=(com.aionemu.gameserver.network.aion.AionConnection)unsafe.allocateInstance(com.aionemu.gameserver.network.aion.AionConnection.class);set(sink,"guard",new Object());set(sink,"closed",true);owner.setClientConnection(sink);
   world.setPosition(owner,map.getMapId(),instance.getInstanceId(),500,500,100,(byte)0);world.storeObject(owner);world.spawn(owner);
   var ctor=PlayerBotSession.class.getDeclaredConstructor(Player.class,Player.class,PlayerBotLease.class,int.class,boolean.class);ctor.setAccessible(true);
   for(var pc:List.of(PlayerClass.CLERIC,PlayerClass.GLADIATOR,PlayerClass.SORCERER,PlayerClass.BARD)) {
    var p=make(pc,owner.getObjectId());players.add(p);world.setPosition(p,map.getMapId(),instance.getInstanceId(),501+sessions.size(),500,100,(byte)0);world.storeObject(p);world.spawn(p);
    if(owner.getPlayerGroup()==null)PlayerGroupService.createGroup(owner,p,com.aionemu.gameserver.model.team.TeamType.GROUP,0);else PlayerGroupService.addPlayer(owner.getPlayerGroup(),p);
    var lease=PlayerBotLease.acquire(p.getObjectId());leases.add(lease);var s=ctor.newInstance(owner,p,lease,sessions.size(),false);sessions.add(s);live.put(p.getObjectId(),s);
    int altCount=p.getAllItems().size();call(supplies,"provision",new Class<?>[]{Player.class},p);if(p.getAllItems().size()!=altCount)throw new AssertionError("Owned alt received generated supplies");
    states.put(p.getObjectId(),stateCtor.newInstance());call(supplies,"provision",new Class<?>[]{Player.class},p);
    var stock=p.getInventory().getItems().stream().filter(i->{try{return (boolean)call(supplies,"safe",new Class<?>[]{com.aionemu.gameserver.skillengine.model.SkillTemplate.class},call(supplies,"skill",new Class<?>[]{com.aionemu.gameserver.model.templates.item.ItemTemplate.class},i.getItemTemplate()));}catch(Exception e){throw new RuntimeException(e);}}).toList();
    var categories=new HashSet<String>();for(var item:stock){categories.add((String)call(supplies,"category",new Class<?>[]{Player.class,com.aionemu.gameserver.model.templates.item.ItemTemplate.class},p,item.getItemTemplate()));if(item.getItemTemplate().getRequiredLevel(pc)>p.getLevel() || item.getItemCount()>20)throw new AssertionError("Illegal or unbounded supply");}
    if(!categories.contains("hp") || !categories.contains("mp") || categories.stream().noneMatch(c->c.startsWith("buff:")))throw new AssertionError("Missing native recovery/build stock: "+pc+" "+categories);
    int count=p.getAllItems().size();call(supplies,"provision",new Class<?>[]{Player.class},p);if(p.getAllItems().size()!=count)throw new AssertionError("Repeated stock grants");
    lines.add("OK: "+pc+" native stock "+stock.stream().map(i->i.getItemId()+":"+i.getItemTemplate().getName()+" x"+i.getItemCount()).toList()+"; alt untouched; bounded/idempotent.");
   }
   var healer=sessions.getFirst().bot();healer.getLifeStats().setCurrentHpPercent(40);healer.getLifeStats().setCurrentMpPercent(100);var candidates=(List<Item>)call(supplies,"candidates",new Class<?>[]{Player.class},healer);
   boolean used=false;for(var item:candidates){if(!((String)call(supplies,"category",new Class<?>[]{Player.class,com.aionemu.gameserver.model.templates.item.ItemTemplate.class},healer,item.getItemTemplate())).equals("hp"))continue;
    int hp=healer.getLifeStats().getCurrentHp();long count=item.getItemCount();if(!(boolean)call(supplies,"use",new Class<?>[]{Player.class,Item.class},healer,item))continue;long deadline=System.currentTimeMillis()+12000;while(System.currentTimeMillis()<deadline && (healer.isCasting() || healer.getLifeStats().getCurrentHp()<=hp))Thread.sleep(100);
    if(healer.getLifeStats().getCurrentHp()<=hp || item.getItemCount()!=count-1 || !healer.hasCooldown(item))throw new AssertionError("Native potion did not heal/consume/cooldown: "+item.getItemId()+" count="+item.getItemCount()+" before="+count);
    if((boolean)call(supplies,"use",new Class<?>[]{Player.class,Item.class},healer,item))throw new AssertionError("Potion cooldown bypassed");used=true;lines.add("OK: actual native HP item "+item.getItemId()+" healed, consumed one item and enforced cooldown.");break;
   }if(!used)throw new AssertionError("No stock HP potion could be natively used");
   owner.setTarget(null);if(PlayerBotRecovery.selected(owner,"all").size()!=4)throw new AssertionError("No-target summon did not choose party");owner.setTarget(sessions.get(1).bot());if(PlayerBotRecovery.selected(owner,"all").size()!=1 || PlayerBotRecovery.selected(owner,"all").getFirst()!=sessions.get(1))throw new AssertionError("Selected summon did not choose exact companion");owner.setTarget(owner);try{PlayerBotRecovery.selected(owner,"all");throw new AssertionError("Foreign target summoned party");}catch(IllegalArgumentException expected){}owner.setTarget(null);
   var chosen=sessions.get(1);dead(chosen.bot());dead(sessions.get(2).bot());owner.setTarget(chosen.bot());PlayerBotTravel.summonAll(PlayerBotRecovery.selected(owner,"all"));if(chosen.bot().isDead() || !sessions.get(2).bot().isDead() || !chosen.bot().isSpawned())throw new AssertionError("Selected resurrection changed other corpse");lines.add("OK: selected native summon resurrected/relocated only selected bot; another corpse stayed dead.");
   for(var s:sessions)dead(s.bot());set(owner.getController(),"lastAttackedMillis",System.currentTimeMillis());try{PlayerBotTravel.summonAll(sessions);throw new AssertionError("Combat summon accepted");}catch(IllegalArgumentException expected){}if(sessions.stream().anyMatch(s->!s.bot().isDead()))throw new AssertionError("Rejected summon partly revived party");set(owner.getController(),"lastAttackedMillis",0L);owner.setTarget(null);
   call(recovery,"tick",new Class<?>[]{PlayerBotSession.class},sessions.getFirst());if(sessions.stream().anyMatch(s->s.bot().isDead() || !s.bot().isSpawned() || s.bot().getInstanceId()!=owner.getInstanceId()))throw new AssertionError("Only-owner-alive recovery failed");lines.add("OK: only-owner-alive recovery revived/regrouped all four; active combat rejected before any resurrection.");
   for(var s:sessions)dead(s.bot());dead(owner);call(recovery,"tick",new Class<?>[]{PlayerBotSession.class},sessions.getFirst());if(sessions.stream().anyMatch(s->!s.bot().isDead()))throw new AssertionError("Bots revived while owner dead");com.aionemu.gameserver.services.player.PlayerReviveService.revive(owner,25,25,false,0);call(recovery,"tick",new Class<?>[]{PlayerBotSession.class},sessions.getFirst());if(sessions.stream().anyMatch(s->s.bot().isDead()))throw new AssertionError("Post-wipe owner revival failed");lines.add("OK: full wipe waits for owner resurrection, then native recovery revives/regroups the party.");
   lines.add("SUCCESS: effective native supply selection/use, alt preservation, item costs/cooldowns, selection routing, manual resurrection and both wipe paths; no DB/human writes.");
  }catch(Throwable e){var trace=new java.io.StringWriter();e.printStackTrace(new java.io.PrintWriter(trace));lines.add("FAIL: "+trace);}
  finally {
   for(var s:sessions){live.remove(s.bot().getObjectId());states.remove(s.bot().getObjectId());call(supplies,"close",new Class<?>[]{Player.class},s.bot());}
   for(var lease:leases)lease.close();
   var itemIds=new HashSet<Integer>();for(var p:players){for(var item:p.getAllItems())itemIds.add(item.getObjectId());p.getController().cancelCurrentSkill(null);p.getLifeStats().cancelAllTasks();p.getEffectController().removeAllEffects(true);if(p.getPlayerGroup()!=null)PlayerGroupService.removePlayer(p);World.getInstance().removeObject(p);IDFactory.getInstance().releaseId(p.getObjectId());}
   for(int id:itemIds)IDFactory.getInstance().releaseId(id);if(!players.isEmpty())call(recovery,"close",new Class<?>[]{Player.class},players.getFirst());if(instance!=null)World.getInstance().getWorldMap(instance.getMapId()).removeWorldMapInstance(instance.getInstanceId());lines.add("CLEANUP: isolated unsaved actors, private items, leases, state and instance removed; no NPC IDs manually released.");
  }}Files.write(Path.of(argument),lines,StandardOpenOption.CREATE_NEW);if(lines.stream().anyMatch(s->s.startsWith("FAIL:")))throw new AssertionError("Native check failed; see receipt");
 }
 public static void main(String[] args)throws Exception{var vm=VirtualMachine.attach(args[0]);try{vm.loadAgent(Path.of(args[1]).toAbsolutePath().toString(),Path.of(args[2]).toAbsolutePath().toString());}finally{vm.detach();}}
}
