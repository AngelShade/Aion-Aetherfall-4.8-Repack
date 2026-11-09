package com.aionemu.gameserver.services.playerbot;

import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.*;
import com.aionemu.gameserver.configs.main.PlayerBotConfig;
import com.aionemu.gameserver.configs.main.PlayerBotMerchantConfig;
import com.aionemu.gameserver.configs.main.PlayerBotEconomyConfig;
import com.aionemu.gameserver.controllers.PlayerController;
import com.aionemu.gameserver.dataholders.*;
import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.model.TaskId;
import com.aionemu.gameserver.model.account.Account;
import com.aionemu.gameserver.model.gameobjects.Persistable.PersistentState;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.services.item.ItemPacketService.ItemUpdateType;
import com.aionemu.gameserver.model.items.storage.*;
import com.aionemu.gameserver.model.skill.*;
import com.aionemu.gameserver.model.templates.spawns.*;
import com.aionemu.gameserver.model.templates.world.WorldMapTemplate;
import com.aionemu.gameserver.world.knownlist.KnownList;
import static com.aionemu.gameserver.services.playerbot.PlayerBotQuestCrafting.*;

/** Actual awake admission/work with recording native custody, no World, SQL or movement. */
public final class PlayerBotProfessionDecisionCheck {
 static int checks;
 static sun.misc.Unsafe unsafe;
 static void check(boolean ok,String reason){checks++;if(!ok)throw new AssertionError(reason);}
 static Field field(Class<?> type,String name)throws Exception{while(type!=null){try{var f=type.getDeclaredField(name);f.setAccessible(true);return f;}catch(NoSuchFieldException missing){type=type.getSuperclass();}}throw new NoSuchFieldException(name);}
 static void set(Object target,String name,Object value)throws Exception{field(target.getClass(),name).set(target,value);}
 @SuppressWarnings("unchecked") static <K,V> Map<K,V> map(Class<?> type,String name)throws Exception{return (Map<K,V>)field(type,name).get(null);}
 static final class Controller extends PlayerController {
  boolean combat;
  @Override public boolean isInCombat(){return combat;}
  @Override public boolean isUnderStance(){return false;}
  @Override public boolean hasScheduledTask(TaskId task){return false;}
 }
 static final class Cube extends PlayerStorage {
  long money=100000;int charges;
  Cube(Player bot){super(bot,StorageType.CUBE);}
  @Override public long getKinah(){return money;}
  @Override public boolean tryDecreaseKinah(long amount,ItemUpdateType type){if(amount<0 || amount>money)return false;money-=amount;charges++;return true;}
 }
 static final class Bot extends Player {
  Cube cube;Controller controller;PlayerSkillList skills;KnownList known;boolean grouped;
  Bot(){super(null,null);}
  @Override public int getObjectId(){return 1999976175;}
  @Override public Account getAccount(){return new Account(1);}
  @Override public Race getRace(){return Race.ELYOS;}
  @Override public byte getLevel(){return 65;}
  @Override public int getWorldId(){return 110010000;}
  @Override public int getInstanceId(){return 1;}
  @Override public boolean isWorldBot(){return true;}
  @Override public boolean isPlayerBot(){return true;}
  @Override public boolean isSpawned(){return true;}
  @Override public boolean isDead(){return false;}
  @Override public boolean isCasting(){return false;}
  @Override public boolean isTrading(){return false;}
  @Override public boolean isLooting(){return false;}
  @Override public boolean isFlying(){return false;}
  @Override public boolean isInAnyHide(){return false;}
  @Override public boolean isInInstance(){return false;}
  @Override public boolean isInGroup(){return grouped;}
  @Override public boolean isInAlliance(){return false;}
  @Override public boolean canPerformMove(){return true;}
  @Override public boolean isUsingFlightTransporterOrWindstream(){return false;}
  @Override public PlayerController getController(){return controller;}
  @Override public PlayerStorage getInventory(){return cube;}
  @Override public PlayerStorage getStorage(int type){return null;}
  @Override public PlayerSkillList getSkillList(){return skills;}
  @Override public KnownList getKnownList(){return known;}
 }
 static void seedMetadata(Bot bot,Properties values)throws Exception {
  Map<PlayerBotMetadata.Key,PlayerBotMetadata.Entry> cache=map(PlayerBotMetadata.class,"CACHE");
  cache.put(new PlayerBotMetadata.Key(bot.getObjectId(),"profession"),new PlayerBotMetadata.Entry(1,1,PlayerBotMetadata.values(values),PlayerBotMetadata.values(values)));
  cache.put(new PlayerBotMetadata.Key(bot.getObjectId(),"care"),new PlayerBotMetadata.Entry(1,0,Map.of(),Map.of()));
 }
 public static void main(String[] args)throws Exception {
  var f=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);unsafe=(sun.misc.Unsafe)f.get(null);
  var bot=(Bot)unsafe.allocateInstance(Bot.class);bot.controller=new Controller();bot.cube=new Cube(bot);bot.known=new KnownList(bot);
  bot.skills=new PlayerSkillList(List.of(new PlayerSkillEntry(30002,99,0,PersistentState.UPDATED)));
  var session=(PlayerBotSession)unsafe.allocateInstance(PlayerBotSession.class);set(session,"owner",bot);set(session,"bot",bot);
  boolean enabled=PlayerBotEconomyConfig.ECONOMY_ENABLED,professions=PlayerBotEconomyConfig.PROFESSIONS_ENABLE,prepared=field(PlayerBotProfessions.class,"prepared").getBoolean(null);
  boolean bots=PlayerBotConfig.ENABLED,worldBots=PlayerBotConfig.WORLD_ENABLED,findGroup=PlayerBotConfig.FIND_GROUP_ENABLED;
  int peddler=PlayerBotMerchantConfig.CHANCE_PEDDLER;
  var oldMaps=DataManager.WORLD_MAPS_DATA;var oldSpawns=DataManager.SPAWNS_DATA;Object occupied=field(PlayerBotWorldActivity.class,"occupied").get(null);
  try {
   PlayerBotEconomyConfig.ECONOMY_ENABLED=true;PlayerBotEconomyConfig.PROFESSIONS_ENABLE=true;field(PlayerBotProfessions.class,"prepared").setBoolean(null,true);
   PlayerBotMerchantConfig.CHANCE_PEDDLER=6000;
   field(PlayerBotWorldActivity.class,"occupied").set(null,Set.of(((long)bot.getWorldId()<<32)|1L));
   var world=new WorldMapTemplate();set(world,"mapId",110010000);
   DataManager.WORLD_MAPS_DATA=new WorldMapsData(){@Override public Iterator<WorldMapTemplate> iterator(){return List.of(world).iterator();}};
   var group=new SpawnGroup(110010000,203780,0,null);group.getSpawnTemplates().add(new SpawnTemplate(group,1,1,1,(byte)0,0,null,0));
   DataManager.SPAWNS_DATA=new SpawnsData(){@Override public List<SpawnGroup> getSpawnsForNpc(int map,int npc){return npc==203780?List.of(group):List.of();}};
   var values=new Properties();values.putAll(Map.of("version","1","account","1","character",Integer.toString(bot.getObjectId()),"primary","30002","style","regular"));
   seedMetadata(bot,values);var state=new PlayerBotProfessions.State(bot,values);state.plan=new Plan(Stage.TRAIN,0,0,30002,0,Map.of(),Map.of(),"native rank");
   PlayerBotProfessionDecisionCheck.<Integer,PlayerBotProfessions.State>map(PlayerBotProfessions.class,"STATES").put(bot.getObjectId(),state);
   long now=System.currentTimeMillis();
   check(PlayerBotProfessions.offer(session,null,now),"actual awake entry admits one due work turn");
   check(state.awakePending && state.trip==null && bot.cube.charges==0,"stationless admission queues fallback without paying or moving");
   check(PlayerBotProfessions.awakeTick(session,null,now),"stationless turn reaches real ledgered work without stalling");
   check(bot.skills.getSkillLevel(30002)==100 && bot.cube.money==83000 && bot.cube.charges==1,"native learnBackground charges its real 17000 fee and advances the native skill once");
   check(state.number("trainingSpent")==17000 && state.number("spent")==17000 && state.number("trainingCount")==1,"same native fee reaches the existing profession ledgers");
   check(PlayerBotQuestSync.state(session).spent==17000,"shared spending ledger receives the same charge");
   check(!PlayerBotProfessions.awakeTick(session,null,now+1) && bot.cube.charges==1 && !state.awakePending,"the completed turn cannot execute or charge twice");
   check(PlayerBotProfessions.offer(session,null,now+1) && !state.awakePending && bot.cube.charges==1
    && !PlayerBotProfessions.offer(session,null,now+PlayerBotEconomyConfig.PROFESSIONS_WORK_SLICE_SECONDS*1000L+1),"an active gather slice cannot readmit paid work, and the existing interval holds after it expires");
   state.activeUntil=0;state.nextAwake=0;set(session,"worldDormant",true);
   check(!PlayerBotProfessions.offer(session,null,now+2000) && !PlayerBotProfessions.awakeTick(session,null,now+2000) && bot.cube.charges==1,"dormant entry never admits awake work or movement");
   state.awakePending=true;state.pendingSince=now-(com.aionemu.gameserver.configs.main.PlayerBotEconomyConfig.PROFESSIONS_TRIP_SECONDS+61)*1000L;check(!PlayerBotProfessions.pending(bot) && !state.awakePending,"a trip held back too long gives up instead of stalling the world planner");
   state.awakePending=true;state.pendingSince=System.currentTimeMillis();check(PlayerBotProfessions.pending(bot),"a recent trip stays pending");state.awakePending=false;
   set(session,"worldDormant",false);state.awakePending=true;bot.grouped=true;
   check(!PlayerBotProfessions.awakeTick(session,null,now+2000) && bot.cube.charges==1,"a real native party blocks the pending optional work");bot.grouped=false;
   bot.controller.combat=true;check(!PlayerBotProfessions.awakeTick(session,null,now+2000) && bot.cube.charges==1,"native combat blocks the pending optional work");bot.controller.combat=false;
   PlayerBotConfig.ENABLED=true;PlayerBotConfig.WORLD_ENABLED=true;PlayerBotConfig.FIND_GROUP_ENABLED=true;
   PlayerBotProfessionDecisionCheck.<Integer,Object>map(PlayerBotFindGroup.class,"MEETINGS").put(bot.getObjectId(),new Object());
   check(!PlayerBotProfessions.awakeTick(session,null,now+2000) && bot.cube.charges==1,"an existing Find Group meeting blocks the pending optional work");
   PlayerBotProfessionDecisionCheck.<Integer,Object>map(PlayerBotFindGroup.class,"MEETINGS").remove(bot.getObjectId());
   // A station step that never wins a turn must not freeze the planner for the whole trip limit, and the work still happens.
   var turns=(java.util.concurrent.atomic.LongAdder)field(PlayerBotProfessions.class,"TURNS").get(null);
   long t=System.currentTimeMillis();state.activeUntil=0;state.awakePending=true;state.pendingSince=t;state.stationTick=0;state.trip=null;
   check(PlayerBotProfessions.holding(session,t+1000) && state.awakePending,"a fresh station visit holds world planning for its first step");
   long before=turns.sum();
   check(!PlayerBotProfessions.holding(session,t+PlayerBotProfessions.FIRST_STATION_TICK_MS) && !state.awakePending && turns.sum()==before+1
    && PlayerBotProfessions.stats().contains("trip stalls released=1"),"a station step that never ran releases planning and does the work where the bot stands");
   check(!PlayerBotProfessions.holding(session,t+PlayerBotProfessions.FIRST_STATION_TICK_MS+1) && turns.sum()==before+1,"a released visit cannot run its work twice");
   state.awakePending=true;state.pendingSince=t-60000;state.stationTick=t-1000;
   check(PlayerBotProfessions.holding(session,t),"a trip whose step is still running keeps holding past the first-step limit");
   state.stationTick=t-PlayerBotEconomyConfig.PROFESSIONS_TRIP_STALL_SECONDS*1000L;
   check(!PlayerBotProfessions.holding(session,t) && !state.awakePending,"a trip whose step stopped running for the stall limit releases planning");
   check(PlayerBotProfessionTravel.CRAFT_RANGE==5,"workbench arrival uses the native CraftService tool range");
   var road=(PlayerBotRoadDuty.State)unsafe.allocateInstance(PlayerBotRoadDuty.State.class);set(road,"career",PlayerBotRoadDuty.Career.MERCHANT);
   PlayerBotProfessionDecisionCheck.<Integer,PlayerBotRoadDuty.State>map(PlayerBotRoadDuty.class,"STATES").put(bot.getObjectId(),road);
   check(!PlayerBotProfessions.awakeTick(session,null,now+2000) && bot.cube.charges==1,"a persistent road career blocks the pending optional work");
   var source=Files.readString(Path.of(args[0],"src/com/aionemu/gameserver/services/playerbot/PlayerBotSession.java"));
   check(source.contains("mission==null && questJob==null && PlayerBotProfessions.pending(bot)"),"Session places local quest and mission actions above station work");
   check(source.contains("PlayerBotFindGroup.travel(this,navigation)") && source.indexOf("PlayerBotFindGroup.travel(this,navigation)")<source.indexOf("PlayerBotProfessions.offer(this,navigation,now)"),"Find Group travel owns its earlier actual Session boundary");
   System.out.println("OK: "+checks+" world profession decision and ledger checks");
  }finally {
   PlayerBotEconomyConfig.ECONOMY_ENABLED=enabled;PlayerBotEconomyConfig.PROFESSIONS_ENABLE=professions;field(PlayerBotProfessions.class,"prepared").setBoolean(null,prepared);
   PlayerBotConfig.ENABLED=bots;PlayerBotConfig.WORLD_ENABLED=worldBots;PlayerBotConfig.FIND_GROUP_ENABLED=findGroup;
   PlayerBotMerchantConfig.CHANCE_PEDDLER=peddler;
   DataManager.WORLD_MAPS_DATA=oldMaps;DataManager.SPAWNS_DATA=oldSpawns;field(PlayerBotWorldActivity.class,"occupied").set(null,occupied);
   PlayerBotProfessionDecisionCheck.<Integer,Object>map(PlayerBotProfessions.class,"STATES").remove(bot.getObjectId());PlayerBotProfessionDecisionCheck.<Integer,Object>map(PlayerBotQuestSync.class,"STATES").remove(bot.getObjectId());PlayerBotProfessionDecisionCheck.<Integer,Object>map(PlayerBotRoadDuty.class,"STATES").remove(bot.getObjectId());
   PlayerBotProfessionDecisionCheck.<Integer,Object>map(PlayerBotFindGroup.class,"MEETINGS").remove(bot.getObjectId());
   Map<PlayerBotMetadata.Key,PlayerBotMetadata.Entry> cache=map(PlayerBotMetadata.class,"CACHE");cache.remove(new PlayerBotMetadata.Key(bot.getObjectId(),"profession"));cache.remove(new PlayerBotMetadata.Key(bot.getObjectId(),"care"));
  }
 }
 private PlayerBotProfessionDecisionCheck(){}
}
