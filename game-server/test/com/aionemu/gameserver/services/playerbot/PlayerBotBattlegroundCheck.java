package com.aionemu.gameserver.services.playerbot;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.function.Predicate;
import java.util.concurrent.ConcurrentHashMap;

import com.aionemu.gameserver.configs.main.PlayerBotConfig;
import com.aionemu.gameserver.controllers.attack.AggroInfo;
import com.aionemu.gameserver.controllers.attack.AggroList;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.dataholders.AutoGroupData;
import com.aionemu.gameserver.model.PlayerClass;
import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.model.account.Account;
import com.aionemu.gameserver.model.autogroup.*;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.VisibleObject;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.items.storage.PlayerStorage;
import com.aionemu.gameserver.model.items.storage.StorageType;
import com.aionemu.gameserver.model.team.group.PlayerGroup;
import com.aionemu.gameserver.model.team.group.PlayerGroupMember;
import com.aionemu.gameserver.services.AutoGroupService;
import com.aionemu.gameserver.services.autogroup.AutoGroupUtility;
import com.aionemu.gameserver.services.instance.PvPArenaService;
import com.aionemu.gameserver.world.WorldMapInstance;

import sun.misc.Unsafe;

/** PvP-3/4 generic layer: inside battlegrounds and arenas every bot fights native enemies instance-wide. Wiring only. */
public final class PlayerBotBattlegroundCheck {
 static int checks;
 static void check(boolean value,String why){checks++;if(!value)throw new AssertionError(why);}

 public static void main(String[] args) throws Exception {
  var root=args.length>0?Path.of(args[0]):Path.of(".");
  var src=root.resolve("src/com/aionemu/gameserver/services/playerbot");
  var seek=Files.readString(src.resolve("PlayerBotPvpSeek.java"));
  var pvp=Files.readString(src.resolve("PlayerBotPvp.java"));
  var session=Files.readString(src.resolve("PlayerBotSession.java"));
  var stats=Files.readString(src.resolve("PlayerBotPvpStats.java"));
  var config=Files.readString(root.resolve("config/playerbots/pvp.properties"));
  check(seek.contains("bot.getPosition()!=null && bot.isInInstance() && com.aionemu.gameserver.model.autogroup.AutoGroupType.getAutoGroupByWorld(bot.getLevel(),bot.getWorldId())!=null"),
   "A battleground or arena is recognised from native Find Group instance types, safely for bots without a position");
  check(seek.contains("p!=bot && p.isSpawned() && !p.isDead() && bot.isEnemy(p)"),"Inside, the game's own enemy rule decides (factions, or everyone in a free-for-all arena)");
  check(seek.contains("if(arena){if(PlayerBotPvp.hp(bot)<40){release();return false;}return hunt(session,navigation,bot,now);}")
   && seek.indexOf("if(arena)") < seek.indexOf("if(!pvpLeaning && !visit)"),"Every bot fights in a battleground, whatever its leaning, and party membership does not stop it");
  check(seek.contains("(arena || !guarded(") && seek.contains("!arena && visited.containsKey(p.getObjectId())"),"Guards and the lone arena opponent are never avoided");
  check(pvp.contains("PlayerBotPvpSeek.battleground(bot)?100:PlayerBotConfig.PVP_AGGRESSION)"),"In a battleground the bot always starts the fight");
  check(session.contains("&& (!PlayerBotFindGroup.committed(bot) || bot.isInInstance())"),"Bots that joined through Find Group still fight inside the instance");
  check(stats.contains("in battlegrounds and arenas \"+BATTLEGROUND_SEEKS.get()") && config.contains("gameserver.playerbots.pvp.battlegrounds.enable = "),"Reported and documented");
  nativeCompanionEntry(root);
  mixedDredgionSignals();
  var autoGroup=Files.readString(root.resolve("src/com/aionemu/gameserver/services/AutoGroupService.java"));
  var service=Files.readString(src.resolve("PlayerBotService.java"));
  var hiring=Files.readString(src.resolve("PlayerBotHiring.java"));
  check(autoGroup.contains("new CompanionEntry(player, member, instance, expiresAt)")
   && autoGroup.indexOf("new CompanionEntry(player, member, instance, expiresAt)")<autoGroup.indexOf("PlayerGroupService.removePlayer(player)"),
   "Accepting human captures only enrolled controlled companions before native team removal");
  check(session.contains("enterCompanionWithOwner(owner,bot)") && hiring.contains("!PlayerBotTeams.same(session.owner(),session.bot()) && !queueEntry"),
   "Existing companion worker owns entry, and the paid term survives only the accepted native handoff");
  check(service.contains("loadWorldResidents(false,true)") && service.contains(".filter(id->!queuedOnly || populationManager.queueReserved(id))")
   && service.contains("if(global && !queueReload)target=latestFocus.target(target)"),
   "Queued factory reload has priority and retains safe queue placement through focus-only admission");
  System.out.println("OK: "+checks+" battleground checks; isolated native queue paths, no World/SQL/tasks");
 }

 private static final class Actor extends Player {
  int id, owner, accountId;
  boolean online, spawned, world;
  Group group;
  Scene scene;
  Cube cube;
  Actor(){super(null,null);}
  @Override public int getObjectId(){return id;}
  @Override public int getPlayerBotOwnerId(){return owner;}
  @Override public boolean isPlayerBot(){return owner!=0;}
  @Override public boolean isWorldBot(){return world;}
  @Override public boolean isOnline(){return online;}
  @Override public boolean isSpawned(){return spawned;}
  @Override public boolean isDead(){return false;}
  @Override public boolean isEnemy(Creature target){return target instanceof Npc;}
  @Override public boolean isInGroup(){return group!=null;}
  @Override public PlayerGroup getPlayerGroup(){return group;}
  @Override public boolean isInAlliance(){return false;}
  @Override public WorldMapInstance getWorldMapInstance(){return scene;}
  @Override public Account getAccount(){return new Account(accountId);}
  @Override public PlayerStorage getInventory(){return cube;}
  @Override public Race getRace(){return Race.ELYOS;}
  @Override public PlayerClass getPlayerClass(){return PlayerClass.GLADIATOR;}
  // getName() is final and reads the common data; give the stub a named one.
  @Override public String getName(boolean tag){return "QueueActor"+id;}
 }

 private static final class Group extends PlayerGroup {
  List<Player> players;
  Map<Integer,Integer> brands=Map.of();
  Group(){super(null,null,1);}
  @Override public int getObjectId(){return 8000;}
  @Override public List<Player> getMembers(){return players;}
  @Override public List<Player> filterMembers(Predicate<Player> filter){return players.stream().filter(filter).toList();}
  @Override public int getBrandTargetId(int brand){return brands==null?0:brands.getOrDefault(brand,0);}
  void initialLeader(Player player){setLeader(new PlayerGroupMember(player));}
 }

 private static final class Cube extends PlayerStorage {
  Cube(){super(null,StorageType.CUBE);}
  @Override public long getItemCountByItemId(int itemId){return 0;}
 }

 private static final class Scene extends WorldMapInstance {
  Set<Integer> registered;
  Map<Integer,VisibleObject> objects;
  Scene(){super(null,1,12,null);}
  @Override public int getMapId(){return 300440000;}
  @Override public boolean isRegistered(int id){return registered.contains(id);}
  @Override public VisibleObject getObject(int id){return objects==null?null:objects.get(id);}
  @Override protected void initMapRegions(){}
  @Override public com.aionemu.gameserver.world.MapRegion getRegion(float x,float y,float z){return null;}
  @Override public boolean isPersonal(){return false;}
  @Override public int getOwnerId(){return 0;}
 }

 private static final class Objective extends Npc {
  int id;
  Objective(){super(null,null,null);}
  @Override public int getObjectId(){return id;}
  @Override public boolean isSpawned(){return true;}
  @Override public boolean isDead(){return false;}
 }

 private static void setField(Class<?> type,Object object,String name,Object value)throws Exception {
  var field=type.getDeclaredField(name);field.setAccessible(true);field.set(object,value);
 }

 private static Actor partyMember(Unsafe unsafe,Group group,Scene scene,int id,boolean bot)throws Exception {
  var member=(Actor)unsafe.allocateInstance(Actor.class);
  member.id=id;member.owner=bot?id:0;member.online=!bot;member.spawned=true;member.group=group;member.scene=scene;
  return member;
 }

 @SuppressWarnings("unchecked")
 private static void attack(Actor member,Objective objective)throws Exception {
  setField(VisibleObject.class,member,"target",objective);
  var aggro=objective.getAggroList();
  var entries=AggroList.class.getDeclaredField("aggroList");entries.setAccessible(true);
  var constructor=AggroInfo.class.getDeclaredConstructor(Creature.class);constructor.setAccessible(true);
  var hit=constructor.newInstance(member);
  setField(AggroInfo.class,hit,"damage",1);
  hit.addHate(1);
  ((ConcurrentHashMap<Integer,AggroInfo>)entries.get(aggro)).put(member.id,hit);
 }

 private static void mixedDredgionSignals()throws Exception {
  var unsafeField=Unsafe.class.getDeclaredField("theUnsafe");unsafeField.setAccessible(true);
  var unsafe=(Unsafe)unsafeField.get(null);
  var scene=(Scene)unsafe.allocateInstance(Scene.class);
  var otherScene=(Scene)unsafe.allocateInstance(Scene.class);
  var group=(Group)unsafe.allocateInstance(Group.class);
  var bot=partyMember(unsafe,group,scene,801,true);
  var leader=partyMember(unsafe,group,scene,802,false);
  var ally=partyMember(unsafe,group,scene,803,false);
  var secondAlly=partyMember(unsafe,group,scene,804,false);
  group.initialLeader(bot);
  var a=(Objective)unsafe.allocateInstance(Objective.class);a.id=810;
  var b=(Objective)unsafe.allocateInstance(Objective.class);b.id=811;
  setField(Creature.class,a,"aggroList",new AggroList(a));
  setField(Creature.class,b,"aggroList",new AggroList(b));
  scene.objects=Map.of(a.id,a,b.id,b);
  boolean nativeMarks=PlayerBotConfig.NATIVE_MARKS;
  PlayerBotConfig.NATIVE_MARKS=true;
  try {
   group.players=List.of(bot);
   check(PlayerBotDredgionParty.humanObjective(bot,n->true)==null,"An all-bot Dredgion team supplies no human objective signal");
   group.players=List.of(bot,ally);
   setField(VisibleObject.class,ally,"target",b);
   check(PlayerBotDredgionParty.humanObjective(bot,n->true)==null,"Selecting a Surkana without engaging it does not order an attack");
   attack(ally,b);
   check(PlayerBotDredgionParty.humanObjective(bot,n->true)==b,"A single human supplies a real combat objective even with a bot leader");
   group.players=List.of(bot,leader,ally,secondAlly);
   attack(leader,a);attack(secondAlly,b);
   check(PlayerBotDredgionParty.humanObjective(bot,n->true)==b,"Several humans assist the same objective when the bot holds the leader badge");
   group.changeLeader(new PlayerGroupMember(leader));
   check(PlayerBotDredgionParty.humanObjective(bot,n->true)==a,"An engaged human leader takes priority over other active humans");
   group.brands=Map.of(0,b.id);
   check(PlayerBotDredgionParty.humanObjective(bot,n->true)==b,"Native numbered marks supersede individual target choices");
   group.brands=Map.of(0,b.id,14,b.id);
   check(PlayerBotDredgionParty.humanObjective(bot,n->true)==a,"A native sleep mark is protected from mixed-party focus");
   leader.scene=otherScene;
   check(PlayerBotDredgionParty.humanObjective(bot,n->true)==null,"Human calls from another match do not direct this party");
  } finally {
   PlayerBotConfig.NATIVE_MARKS=nativeMarks;
  }
 }

 private static final class Match extends AutoInstance {
  int entries;
  Group group;
  Match(){super(AutoGroupType.TERATH_DREDGION);}
  @Override public void onPressEnter(Player player){entries++;var bot=(Actor)player;bot.scene=(Scene)getInstance();bot.group=group;}
 }

 @SuppressWarnings("unchecked")
 private static void nativeCompanionEntry(Path root)throws Exception {
  var unsafeField=Unsafe.class.getDeclaredField("theUnsafe");unsafeField.setAccessible(true);
  var unsafe=(Unsafe)unsafeField.get(null);
  DataManager.AUTO_GROUP=jakarta.xml.bind.JAXB.unmarshal(root.resolve("data/static_data/auto_group/auto_group.xml").toFile(),AutoGroupData.class);
  var owner=(Actor)unsafe.allocateInstance(Actor.class);owner.id=7000;owner.accountId=7;owner.online=owner.spawned=true;
  var bot=(Actor)unsafe.allocateInstance(Actor.class);bot.id=7001;bot.owner=owner.id;bot.accountId=-1000000;bot.world=bot.spawned=true;
  var human=(Actor)unsafe.allocateInstance(Actor.class);human.id=7002;human.accountId=8;human.online=human.spawned=true;
  var group=(Group)unsafe.allocateInstance(Group.class);group.players=List.of(owner,bot,human);owner.group=bot.group=human.group=group;
  var registration=new LookingForParty(owner,EntryRequestType.GROUP_ENTRY,3);
  check(registration.getMembers().keySet().equals(Set.of(owner.id,bot.id,human.id)),"Actual group registration includes spawned headless companions and human members");
  owner.online=false;
  check(!AutoGroupUtility.canRegisterQuickEntry(owner,AutoGroupType.TERATH_DREDGION),"Native Quick Entry keeps its solo-only team rule");
  owner.online=true;
  check(AutoGroupUtility.serverOwnedBot(bot) && !AutoGroupUtility.hasCoolDown(bot,300440000),"Dedicated server resident is exempt from native queue entry counts");
  new AutoInstance(null){}.onPressEnter(bot);
  check(PvPArenaService.checkItem(bot,AutoGroupType.ARENA_OF_HARMONY_4),"Server resident can fill an arena without player tickets");
  bot.accountId=7;bot.cube=new Cube();
  check(!AutoGroupUtility.serverOwnedBot(bot) && !PvPArenaService.checkItem(bot,AutoGroupType.ARENA_OF_HARMONY_4),"Positive-account world characters retain player ticket requirements");
  bot.accountId=-1000000;bot.world=false;
  check(!AutoGroupUtility.serverOwnedBot(bot),"Owned companion marker alone cannot exempt a character from player requirements");bot.world=true;
  var target=(Scene)unsafe.allocateInstance(Scene.class);target.registered=new HashSet<>();
  var match=new Match();match.group=group;match.onInstanceCreate(target);match.getRegisteredAGPlayers().putAll(registration.getMembers());
  var constructor=AutoGroupService.class.getDeclaredConstructor();constructor.setAccessible(true);var queues=constructor.newInstance();
  var instancesField=AutoGroupService.class.getDeclaredField("autoInstances");instancesField.setAccessible(true);
  ((Map<WorldMapInstance,AutoInstance>)instancesField.get(queues)).put(target,match);
  var entriesField=AutoGroupService.class.getDeclaredField("companionEntries");entriesField.setAccessible(true);
  var pending=(Map<Integer,Object>)entriesField.get(queues);
  var pendingType=Class.forName(AutoGroupService.class.getName()+"$CompanionEntry");
  var pendingConstructor=pendingType.getDeclaredConstructor(Player.class,Player.class,AutoInstance.class,long.class);pendingConstructor.setAccessible(true);
  pending.put(bot.id,pendingConstructor.newInstance(owner,bot,match,System.currentTimeMillis()+120000));
  bot.group=null;
  check(queues.companionEntryPending(owner,bot),"Accepted matched handoff retains companion after old native team removal");
  check(!queues.enterCompanionWithOwner(human,bot) && match.entries==0,"Another human cannot consume this owner's companion invitation");
  check(queues.enterCompanionWithOwner(owner,bot) && match.entries==0,"Bot waits while human has not reached the matched map");
  owner.scene=target;owner.spawned=false;
  check(queues.enterCompanionWithOwner(owner,bot) && match.entries==0,"Bot waits through human map loading");owner.spawned=true;
  check(queues.enterCompanionWithOwner(owner,bot) && match.entries==0,"Spawn publication alone does not precede native instance callback");
  target.registered.add(owner.id);
  check(queues.enterCompanionWithOwner(owner,bot) && match.entries==0,"Human registration also waits for native auto-team registration");
  target.registered.add(group.getObjectId());
  check(queues.enterCompanionWithOwner(owner,bot) && match.entries==1 && bot.scene==target,"Companion uses actual native pressEnter after human and native faction team are ready");
  queues.pressEnter(bot,3);
  check(match.entries==1 && pending.isEmpty(),"Repeated native Enter neither transfers again nor retains completed handoff");
  check(!queues.enterCompanionWithOwner(owner,human),"Human group members keep their manual Enter confirmation");
 }
 private PlayerBotBattlegroundCheck(){}
}
