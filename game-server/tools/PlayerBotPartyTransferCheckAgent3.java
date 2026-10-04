import java.lang.instrument.Instrumentation;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import com.sun.tools.attach.VirtualMachine;
import com.aionemu.gameserver.model.*;
import com.aionemu.gameserver.model.account.*;
import com.aionemu.gameserver.model.gameobjects.player.*;
import com.aionemu.gameserver.model.items.storage.*;
import com.aionemu.gameserver.model.team.group.PlayerGroupService;
import com.aionemu.gameserver.services.player.PlayerService;
import com.aionemu.gameserver.services.playerbot.*;
import com.aionemu.gameserver.instance.handlers.GeneralInstanceHandler;
import com.aionemu.gameserver.world.*;
import com.aionemu.gameserver.utils.idfactory.IDFactory;

/** Five real native headless characters in private empty instances; no database writes. */
public final class PlayerBotPartyTransferCheckAgent3 {
 static Object field(Object o,String name)throws Exception {Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}
 static void set(Object o,String name,Object value)throws Exception {for(Class<?> t=o.getClass();t!=null;t=t.getSuperclass())try{Field f=t.getDeclaredField(name);f.setAccessible(true);f.set(o,value);return;}catch(NoSuchFieldException e){}throw new NoSuchFieldException(name);}
 static Player make(int owner)throws Exception {
  int id=IDFactory.getInstance().nextId();var common=new PlayerCommonData(id);common.setName("TransferFixture"+id);common.setRace(Race.ELYOS);common.setGender(Gender.FEMALE);common.setPlayerClass(PlayerClass.TEMPLAR);common.setDaeva(true);common.setLevel(12);common.setOnline(true);
  var data=new PlayerAccountData(common,new PlayerAppearance());var account=new Account(2000000000);account.setName("TransferFixture");account.addPlayerAccountData(data);account.setAccountWarehouse(new PlayerStorage(null,StorageType.ACCOUNT_WAREHOUSE));
  Player p=PlayerService.newPlayer(data,account);p.setPlayerBotOwner(owner);PlayerBotGenerationOptions.initialize(p);return p;
 }
 static String signature(Player p){return p.getLevel()+"|"+p.getLifeStats().getCurrentHp()+"|"+p.getLifeStats().getCurrentMp()+"|"+p.getSkillList().getAllSkills().stream().map(s->s.getSkillId()+":"+s.getSkillLevel()).sorted().toList()+"|"+p.getAllItems().stream().map(i->i.getObjectId()+":"+i.getEquipmentSlot()).sorted().toList();}
 @SuppressWarnings("unchecked") public static void agentmain(String argument,Instrumentation ignored)throws Exception {
  List<String> lines=new ArrayList<>();List<Player> players=new ArrayList<>();List<WorldMapInstance> instances=new ArrayList<>();List<PlayerBotSession> sessions=new ArrayList<>();Map<Integer,PlayerBotSession> live=(Map<Integer,PlayerBotSession>)field(PlayerBotService.getInstance(),"sessions");
  synchronized(PlayerBotService.getInstance()) {
   try {
    Field leaseField=PlayerBotLease.class.getDeclaredField("leases");leaseField.setAccessible(true);var leases=(Map<Integer,PlayerBotLease>)leaseField.get(null);
    var ids=(java.util.BitSet)field(IDFactory.getInstance(),"idList");var idLock=(java.util.concurrent.locks.ReentrantLock)field(IDFactory.getInstance(),"lock");
    idLock.lock();try {var released=leases.entrySet().stream().filter(e->!ids.get(e.getKey()) && !live.containsKey(e.getKey()) && !World.getInstance().isInWorld(e.getKey())).toList();if(released.size()>1)throw new AssertionError("Unexpected released lease set");for(var e:released){e.getValue().close();lines.add("CLEANUP: released unsaved ID/lease from the failed fixture: "+e.getKey());}}finally{idLock.unlock();}
    World world=World.getInstance();WorldMap map=world.getWorldMap(300040000);
    for(int i=0;i<2;i++)instances.add(WorldMapInstanceFactory.createWorldMapInstance(map,0,GeneralInstanceHandler::new,6));
    Player owner=make(Integer.MAX_VALUE);players.add(owner);
    // Closed, unregistered sink supplies the owner's presence check, without a socket.
    Field unsafeField=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");unsafeField.setAccessible(true);var unsafe=(sun.misc.Unsafe)unsafeField.get(null);
    var sink=(com.aionemu.gameserver.network.aion.AionConnection)unsafe.allocateInstance(com.aionemu.gameserver.network.aion.AionConnection.class);set(sink,"guard",new Object());set(sink,"closed",true);owner.setClientConnection(sink);
    world.setPosition(owner,map.getMapId(),instances.get(0).getInstanceId(),500,500,100,(byte)0);world.storeObject(owner);world.spawn(owner);
    Constructor<PlayerBotSession> ctor=PlayerBotSession.class.getDeclaredConstructor(Player.class,Player.class,PlayerBotLease.class,int.class,boolean.class);ctor.setAccessible(true);
    for(int i=0;i<5;i++) {
     Player bot=make(owner.getObjectId());players.add(bot);world.setPosition(bot,map.getMapId(),instances.get(0).getInstanceId(),500+i,500,100,(byte)0);world.storeObject(bot);world.spawn(bot);
     if(owner.getPlayerGroup()==null)PlayerGroupService.createGroup(owner,bot,com.aionemu.gameserver.model.team.TeamType.GROUP,0);else PlayerGroupService.addPlayer(owner.getPlayerGroup(),bot);
     var held=PlayerBotLease.acquire(bot.getObjectId());PlayerBotSession session;try{session=ctor.newInstance(owner,bot,held,i,false);}catch(Throwable error){held.close();throw error;}sessions.add(session);live.put(bot.getObjectId(),session);
    }
    Map<Integer,String> before=new HashMap<>();for(Player p:players)before.put(p.getObjectId(),signature(p));
    // Reproduce destination combat and one already-despawned member, both previously blocked.
    world.despawn(owner);world.setPosition(owner,map.getMapId(),instances.get(1).getInstanceId(),500,500,100,(byte)0);world.spawn(owner);
    Field attack=owner.getController().getClass().getDeclaredField("lastAttackedMillis");attack.setAccessible(true);attack.setLong(owner.getController(),System.currentTimeMillis());
    world.despawn(players.getLast());
    Method follow=Class.forName("com.aionemu.gameserver.services.playerbot.PlayerBotTransfers").getDeclaredMethod("follow",PlayerBotSession.class);follow.setAccessible(true);follow.invoke(null,sessions.getFirst());
    for(Player p:players.subList(1,players.size())) {
     if(!p.isSpawned() || p.getWorldId()!=owner.getWorldId() || p.getInstanceId()!=owner.getInstanceId() || p.getPlayerGroup()!=owner.getPlayerGroup() || !signature(p).equals(before.get(p.getObjectId())) || !owner.getWorldMapInstance().isRegistered(p.getObjectId()))throw new AssertionError("Partial transfer/state loss: "+p.getName()+" spawned="+p.isSpawned()+" instance="+p.getInstanceId()+" expected="+owner.getInstanceId()+" registered="+owner.getWorldMapInstance().isRegistered(p.getObjectId())+" preserved="+signature(p).equals(before.get(p.getObjectId())));
     lines.add("OK: native party member transferred and registered; HP/MP/level/equipment/skills/group preserved: "+p.getName());
    }
    lines.add("SUCCESS: 5/5 native companion transfer with destination combat and a despawned member; isolated empty instances, no human movement or database writes.");
   }catch(Throwable e){var w=new java.io.StringWriter();e.printStackTrace(new java.io.PrintWriter(w));lines.add("FAIL: "+w);}
   finally {
    for(var session:sessions){live.remove(session.bot().getObjectId());Method close=Class.forName("com.aionemu.gameserver.services.playerbot.PlayerBotQuestSync").getDeclaredMethod("close",PlayerBotSession.class);close.setAccessible(true);close.invoke(null,session);}
    for(Player p:players){if(p.getPlayerGroup()!=null)PlayerGroupService.removePlayer(p);if(p.isSpawned())World.getInstance().despawn(p);p.getLifeStats().cancelAllTasks();p.getEffectController().removeAllEffects(true);World.getInstance().removeObject(p);for(var item:p.getAllItems())IDFactory.getInstance().releaseId(item.getObjectId());IDFactory.getInstance().releaseId(p.getObjectId());}
    for(var session:sessions){Method lease=PlayerBotSession.class.getDeclaredMethod("lease");lease.setAccessible(true);((PlayerBotLease)lease.invoke(session)).close();}
    for(var instance:instances)World.getInstance().getWorldMap(instance.getMapId()).removeWorldMapInstance(instance.getInstanceId());
   }
  }
  Files.write(Path.of(argument),lines,StandardOpenOption.CREATE_NEW);
 }
 public static void main(String[] args)throws Exception {var vm=VirtualMachine.attach(args[0]);try{vm.loadAgent(Path.of(args[1]).toAbsolutePath().toString(),Path.of(args[2]).toAbsolutePath().toString());}finally{vm.detach();}}
}
