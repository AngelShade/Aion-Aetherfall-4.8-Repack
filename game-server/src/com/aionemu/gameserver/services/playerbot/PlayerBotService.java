package com.aionemu.gameserver.services.playerbot;

import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.Future;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aionemu.gameserver.configs.main.*;
import com.aionemu.gameserver.controllers.FlyController;
import com.aionemu.gameserver.controllers.effect.PlayerEffectController;
import com.aionemu.gameserver.dao.PlayerDAO;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.PlayerClass;
import com.aionemu.gameserver.model.account.Account;
import com.aionemu.gameserver.model.account.PlayerAccountData;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.gameobjects.player.PlayerCommonData;
import com.aionemu.gameserver.model.items.storage.PlayerStorage;
import com.aionemu.gameserver.model.items.storage.StorageType;
import com.aionemu.gameserver.model.stats.calc.functions.PlayerStatFunctions;
import com.aionemu.gameserver.model.team.TeamType;
import com.aionemu.gameserver.model.team.group.PlayerGroupService;
import com.aionemu.gameserver.services.AccountService;
import com.aionemu.gameserver.services.NameRestrictionService;
import com.aionemu.gameserver.services.instance.InstanceService;
import com.aionemu.gameserver.services.player.PlayerLeaveWorldService;
import com.aionemu.gameserver.services.player.PlayerService;
import com.aionemu.gameserver.services.playerbot.PlayerBotRules.SkillKind;
import com.aionemu.gameserver.skillengine.SkillEngine;
import com.aionemu.gameserver.utils.PacketSendUtility;
import com.aionemu.gameserver.utils.ThreadPoolManager;
import com.aionemu.gameserver.utils.idfactory.IDFactory;
import com.aionemu.gameserver.world.World;
import com.aionemu.gameserver.world.WorldMapInstance;

/** Owner-controlled, real Player companions. No fake client connection and no account privileges. */
public final class PlayerBotService {
	private static final Logger log = LoggerFactory.getLogger(PlayerBotService.class);
	private static final PlayerBotService INSTANCE = new PlayerBotService();
	private final Map<Integer, PlayerBotSession> sessions = new java.util.concurrent.ConcurrentHashMap<>();
	private final Map<Integer, PlayerBotPersistence.Home> homes = new java.util.concurrent.ConcurrentHashMap<>();
	private record ReservationKey(int ownerId, int targetId, SkillKind kind) {}
	private record Reservation(int caster, long until) {}
	private final Map<ReservationKey, Reservation> reservations = new java.util.concurrent.ConcurrentHashMap<>();
	private Future<?> task, saveTask;
	private volatile boolean shuttingDown;
 private final PlayerBotBackgroundWork backgroundWork=new PlayerBotBackgroundWork();
 private final PlayerBotBackgroundWork controlledWork=new PlayerBotBackgroundWork(),autonomousWork=new PlayerBotBackgroundWork(),maintenanceWork=new PlayerBotBackgroundWork(),checkpointWork=new PlayerBotBackgroundWork();
 private final PlayerBotBackgroundWork activeWorldWork=new PlayerBotBackgroundWork(4);
 private long nextDecisionLagReport;
 private final PlayerBotExecution execution=new PlayerBotExecution();
 private final PlayerBotBackgroundWork teamWork=new PlayerBotBackgroundWork();
 private final Object schedulerLock=new Object();
 private final Object populationLock=new Object();
 private final Map<Integer,Long> retireQueue=new java.util.concurrent.ConcurrentHashMap<>();
 private final PlayerBotDecisionSchedule decisions=new PlayerBotDecisionSchedule();
 private final PlayerBotCheckpointSchedule checkpoints=new PlayerBotCheckpointSchedule();
 private long nextAiMaintenance;
 private long nextWorldReport;
 private List<String> lastWorldReport=List.of();
 private final Map<Integer,Long> nextWorldAdmission=new java.util.concurrent.ConcurrentHashMap<>();
 private volatile int requestedWorldTarget,effectiveWorldTarget;
 private volatile int createdWorldCharacters,loadedWorldCharacters;
 private volatile boolean automaticPopulationReady,automaticAdmissionsInitialized;
 private final long populationStartedAt=System.currentTimeMillis();
 private final java.util.Set<Integer> expiryRetirements=java.util.concurrent.ConcurrentHashMap.newKeySet();
 private final PlayerBotPopulationRepository populationRepository=new PlayerBotPopulationRepository();
	private final PlayerBotWorldRoster worldRoster = new PlayerBotWorldRoster(populationRepository);
	private volatile Map<Integer,PlayerBotWorldRoster.Resident> residents;
	private final java.util.Set<Integer> retiringWorldBots = java.util.concurrent.ConcurrentHashMap.newKeySet();
	private volatile long nextWorldLoad, nextPopulation, nextServerPopulation;
 private final Map<Integer,Long> nextPlacementAttempt = new java.util.concurrent.ConcurrentHashMap<>();
 private volatile PlayerBotPopulationFocus.Snapshot playerFocus=new PlayerBotPopulationFocus.Snapshot(List.of(),900);
 private long nextFocusSnapshot,nextFocusWork;
 private volatile int focusJobId;
 private volatile Thread focusJobThread;
 private volatile List<Player> focusHumans=List.of();
 private final PlayerBotWorldPopulation serverPopulation = new PlayerBotWorldPopulation(populationRepository,true);
 private final PlayerBotPopulationSchedule populationSchedule = new PlayerBotPopulationSchedule(populationRepository);
 private final java.util.random.RandomGenerator populationRandom = new Random();
	private final PlayerBotWorldPopulation population = new PlayerBotWorldPopulation(populationRepository,false);
	public void worldPopulation(Player owner,int count) {synchronized(populationLock) {
		if(!PlayerBotConfig.ENABLED || !PlayerBotConfig.WORLD_ENABLED || shuttingDown || !owner.isOnline())throw new IllegalArgumentException("World population is unavailable.");
		if(!PlayerBotRoster.available())throw new IllegalArgumentException("The native playerbot roster schema is required.");
		try {
			loadWorldRoster();population.configure(owner,count,Math.min(PlayerBotConfig.WORLD_MAX_ACTIVE,PlayerBotConfig.GENERATED_MAX_PER_ACCOUNT));startTasks();nextPopulation=0;
			PacketSendUtility.sendMessage(owner,"World population target: "+count+". New characters start at level 1 and earn their own progress; creation continues while you are offline. Use .bot worldlist.");
		}catch(java.io.IOException e){throw new IllegalStateException("Cannot save world population",e);}}
	}
	private void maintainPopulation() {
		if(shuttingDown || backgroundWork.busy() || !PlayerBotConfig.ENABLED || !PlayerBotConfig.WORLD_ENABLED || System.currentTimeMillis()<nextPopulation)return;
		nextPopulation=System.currentTimeMillis()+5000;
		try {
			loadWorldRoster();
			for(var pool:population.load().values()) {
				for(int index=0;index<pool.characters().size();index++) {
					int id=pool.characters().get(index);
					if(index>=pool.desired()) {
						if(residents.containsKey(id)){retiringWorldBots.add(id);var active=sessions.get(id);if(active!=null)queueRetirement(id);else removeResidence(id);}
						continue;
					}
					if(residents.containsKey(id) || residents.size()>=PlayerBotConfig.WORLD_MAX_ACTIVE)continue;
					if(PlayerBotRoster.list(pool.account()).stream().noneMatch(e->e.id()==id && e.ready()))continue; // A partial creation stays quarantined, never overwritten.
					var common=AccountService.loadPlayerAccountData(id).getPlayerCommonData();
					var r=new PlayerBotWorldRoster.Resident(pool.account(),id,common.getMapId(),common.getX(),common.getY(),common.getZ(),common.getHeading());
					validateResidence(r);var updated=new java.util.TreeMap<>(residents);updated.put(id,r);worldRoster.save(updated);residents=updated;nextWorldLoad=0;
				}
				if(pool.characters().size()<pool.desired() && residents.size()<PlayerBotConfig.WORLD_MAX_ACTIVE
					&& PlayerBotRoster.list(pool.account()).size()<PlayerBotConfig.GENERATED_MAX_PER_ACCOUNT) {queueWorldCreation(pool,population,1);return;}
			}
		}catch(Exception e){log.error("World population maintenance failed; native identities and pending creations retained",e);}
	}
	private void createWorldCharacter(PlayerBotWorldPopulation.Pool pool)throws Exception {
  createWorldCharacter(pool,population,1);
 }
 private boolean queueWorldCreation(PlayerBotWorldPopulation.Pool pool,PlayerBotWorldPopulation store,int level) {
  return backgroundWork.submit(ThreadPoolManager.getInstance()::executeLongRunning,()->{
   try {
    if(shuttingDown || !PlayerBotConfig.ENABLED || !PlayerBotConfig.WORLD_ENABLED)return;
    var latest=store.load().get(pool.account());if(latest==null || latest.characters().size()>=latest.desired())return;
    int id=createWorldCharacter(latest,store,level);
    synchronized(populationLock) {
     if(store==serverPopulation){var settings=PlayerBotPopulationPolicy.settings();populationSchedule.event(id,new PlayerBotPopulationSchedule.Events(0,0,PlayerBotPopulationPolicy.deadline(System.currentTimeMillis(),populationRandom,settings.teleportMin(),settings.teleportMax()),level));}
     worldWorkCompleted();
    }
   }catch(Exception e){log.error("Background world creation failed; native identity and pending intent retained",e);}
  });
 }
 private int createWorldCharacter(PlayerBotWorldPopulation.Pool pool,PlayerBotWorldPopulation store,int level)throws Exception {
  boolean global=PlayerBotPopulationPolicy.serverAccount(pool.account());
  var destination=global?PlayerBotWorldPlacement.destination(pool.race(),level,PlayerBotPopulationPolicy.settings().cityChance(),populationRandom):null;
  if(global && destination==null)throw new IllegalStateException("No validated native population destination for "+pool.race()+" level "+level+"; no identity allocated");
		int id=IDFactory.getInstance().nextId();Player generated=null;
		// Persist intent before native rows. A crash cannot cause repeated invisible allocations.
		synchronized(store){var latest=store.load().get(pool.account());if(latest==null)throw new IllegalStateException("Population pool removed");store.put(latest.append(id));pool=store.load().get(pool.account());}
		try(var lease=PlayerBotLease.acquire(id)) {
			if(lease==null)throw new IllegalStateException("World character ID already reserved");
			var gender=global?new Random((long)id*0x9E3779B97F4A7C15L).nextBoolean()?com.aionemu.gameserver.model.Gender.MALE:com.aionemu.gameserver.model.Gender.FEMALE:pool.gender();
			String name=PlayerBotNames.available(id,gender);
			PlayerBotRoster.reserve(pool.account(),id);
			var common=new PlayerCommonData(id);common.setName(name);common.setRace(pool.race());common.setGender(pool.gender());
			var chosen=PlayerBotWorldPopulation.planned(pool,id);
   common.setGender(gender);
   common.setPlayerClass(level>=10?chosen:chosen.getStartingClass());common.setDaeva(level>=10);common.setLevel(level);
			var data=new PlayerAccountData(common,PlayerBotWorldLooks.create(pool.appearance(),pool.race(),common.getGender(),id));var account=new Account(pool.account());
			account.setName(pool.name());account.setMembership(pool.membership());account.setCreationDate(pool.created());account.addPlayerAccountData(data);
			account.setAccountWarehouse(new PlayerStorage(null,StorageType.ACCOUNT_WAREHOUSE));
			generated=PlayerService.newPlayer(data,account);generated.setPlayerBotOwner(id);generated.setWorldBotController(0);
			PlayerBotGenerationOptions.initialize(generated);PlayerBotTemporary.initialize(generated,PlayerBotRules.roleFor(chosen));
   // Headless native character creation starts at its validated camp, before it is ever visible.
   if(destination!=null && !World.getInstance().setPosition(generated,destination.map(),1,destination.x(),destination.y(),destination.z(),(byte)0))throw new IllegalStateException("Initial population position refused");
			if(!PlayerService.storeNewPlayer(generated,account.getName(),account.getId()))throw new java.sql.SQLException("World character creation failed");
			PlayerBotPersistence.save(generated,PlayerBotPersistence.Home.of(generated));PlayerBotRoster.ready(account.getId(),id);
			synchronized(populationLock){if(global)createdWorldCharacters++;}
   if(global) {
    var settings=PlayerBotPopulationPolicy.settings();int capacity=Math.min(100,Math.max(1,PlayerBotConfig.GENERATED_MAX_PER_ACCOUNT));
    int limit=PlayerBotPopulationRotation.poolCapacity(settings.maximum(),PlayerBotConfig.WORLD_MAX_ACTIVE,settings.periodic(),PlayerBotConfig.POPULATION_ROTATION_RATIO,capacity);
    int saved=store.load().values().stream().mapToInt(p->p.characters().size()).sum();
    log.info("Saved world reserve [{}/{}]: created {} ({}) at level {}; active target {} is separate; existing characters are reused",saved,limit,name,id,level,effectiveWorldTarget);
   }else log.info("World population created and saved {} ({}) at level {}; registered for future reuse",name,id,level);
   return id;
		}finally {
			if(generated!=null){PlayerBotTemporary.release(generated);if(generated.getEffectController()!=null)generated.getEffectController().removeAllEffects(true);generated.getLifeStats().cancelAllTasks();}
		}
	}


 // Global target/admission/events translated from pinned RandomPlayerbotMgr; no human request is needed.
 private void maintainServerPopulation() {
  long now=System.currentTimeMillis();if(shuttingDown || !PlayerBotConfig.ENABLED || !PlayerBotConfig.WORLD_ENABLED || !serverPopulationDue(now))return;
  nextServerPopulation=now+populationManagerDelay(now);
  try {
   loadWorldRoster();var settings=PlayerBotPopulationPolicy.settings();var state=reconcilePopulationTarget(now,settings);
   int target=effectiveWorldTarget;
   var current=new ArrayList<>(residents.values().stream().filter(r->PlayerBotPopulationPolicy.serverAccount(r.account())).toList());
   Collections.shuffle(current,new Random(populationRandom.nextLong()));
   for(var r:current) {
    var event=state.events().get(r.character());var session=sessions.get(r.character());
    boolean expired=event!=null && (PlayerBotPopulationLifecycle.expiredPresence(settings.periodic(),event.presentUntil(),now) || event.pendingOffline(now));
    if((expired || expiryRetirements.contains(r.character())) && (session==null || populationRetirementAllowed(session))) {
     // Expiry is independent of target reductions, visibility and idle planning.
     // Recheck membership under the actor monitor immediately before native departure.
     retiringWorldBots.add(r.character());expiryRetirements.add(r.character());
     if(session==null){commitPopulationDeparture(r.character(),now);removeResidence(r.character());}else queueRetirement(r.character());
    }
   }
   if(!PlayerBotConfig.POPULATION_ENABLED || backgroundWork.busy() || !PlayerBotRoster.available())return;
   var pools=serverPopulation.load();var candidates=new ArrayList<PlayerBotPopulationPolicy.Candidate>();var owners=new HashMap<Integer,Integer>();
   int active=(int)residents.values().stream().filter(r->PlayerBotPopulationPolicy.serverAccount(r.account())).count();
   int capacity=Math.min(100,Math.max(1,PlayerBotConfig.GENERATED_MAX_PER_ACCOUNT));
   int candidateTotal=pools.values().stream().mapToInt(p->p.characters().size()).sum();
   var rotation=PlayerBotPopulationRotation.plan(settings.maximum(),PlayerBotConfig.WORLD_MAX_ACTIVE,settings.periodic(),PlayerBotConfig.POPULATION_ROTATION_RATIO,
    capacity,target,candidateTotal,active,residents.size(),settings.budget());
   automaticPopulationReady=PlayerBotPopulationLifecycle.factoryReady(candidateTotal,rotation.poolCapacity());
   if(!automaticPopulationReady){provisionServerReserve(settings,pools,capacity,now);return;}
   initializeAutomaticAdmissions();
   releaseSurplusPendingAdmissions(target);
   active=(int)residents.values().stream().filter(r->PlayerBotPopulationPolicy.serverAccount(r.account())).count();
   rotation=PlayerBotPopulationRotation.plan(settings.maximum(),PlayerBotConfig.WORLD_MAX_ACTIVE,settings.periodic(),PlayerBotConfig.POPULATION_ROTATION_RATIO,
    capacity,target,candidateTotal,active,residents.size(),settings.budget());
   // Like botLoading, a reserved native-login batch drains before another is admitted.
   if(automaticPendingAdmissions()>0)return;
   var accounts=PlayerBotPopulationRotation.accounts(pools.keySet(),settings.periodic(),PlayerBotConfig.POPULATION_ROTATION_RATIO,populationRandom);
   // Geographic demand selects destinations; it cannot create slots while off-map actors still occupy them.
   int slots=rotation.admissionSlots();
   var admissionFocus=playerFocus.available(focusCounts(),target);
   var rosterSnapshot=slots>0?PlayerBotRoster.lists(accounts):Map.<Integer,List<PlayerBotRoster.Entry>>of();
   var savedLevels=(playerFocus.active() || settings.randomLevels()) && slots>0?PlayerBotRoster.populationLevels(rosterSnapshot.values().stream().flatMap(List::stream)
    .filter(PlayerBotRoster.Entry::ready).map(PlayerBotRoster.Entry::id).toList()):Map.<Integer,Integer>of();
   // Pinned AddRandomBots only queries account candidates when the active set is short.
   // Full occupancy must not block factory provisioning or repeatedly scan native rosters.
   if(slots>0)for(int account:accounts) {
    var pool=pools.get(account);
    for(var row:rosterSnapshot.getOrDefault(pool.account(),List.of())) {
     var event=state.events().get(row.id());
     long unavailableUntil=Math.max(event==null?0:event.offlineUntil(),nextWorldAdmission.getOrDefault(row.id(),0L));
     if(PlayerBotPopulationRotation.eligible(row.ready(),pool.characters().contains(row.id()),residents.containsKey(row.id()),sessions.containsKey(row.id()),unavailableUntil,now)
      && (!playerFocus.active() || admissionFocus.active() && savedLevels.containsKey(row.id())
       && PlayerBotWorldPlacement.focusCompatible(pool.race(),savedLevels.get(row.id()),admissionFocus))) {
      candidates.add(new PlayerBotPopulationPolicy.Candidate(row.id(),pool.race()));owners.put(row.id(),pool.account());
     }
    }
   }
   var admissionPools=pools;
   int elyos=(int)residents.values().stream().filter(r->PlayerBotPopulationPolicy.serverAccount(r.account()) && admissionPools.get(r.account()).race()==com.aionemu.gameserver.model.Race.ELYOS).count();
   int desiredElyos=(int)Math.round(target*settings.elyosPercent()/100.0);
   int percent=slots==0?settings.elyosPercent():Math.min(100,Math.max(0,desiredElyos-elyos)*100/slots);
   var onlineLevels=new EnumMap<com.aionemu.gameserver.model.Race,Map<Integer,Integer>>(com.aionemu.gameserver.model.Race.class);
   if(slots>0 && settings.randomLevels())for(var session:sessions.values())if(session.world() && PlayerBotPopulationPolicy.serverAccount(session.bot().getAccount().getId()))
    onlineLevels.computeIfAbsent(session.bot().getRace(),k->new HashMap<>()).merge((int)session.bot().getLevel(),1,Integer::sum);
   var admitted=settings.randomLevels()?PlayerBotPopulationPolicy.admit(candidates,slots,percent,populationRandom,savedLevels,onlineLevels)
    :PlayerBotPopulationPolicy.admit(candidates,slots,percent,populationRandom);
   for(int id:admitted) {
    var data=AccountService.loadPlayerAccountData(id);if(data==null || data.getDeletionDate()!=null || data.getPlayerCommonData().isOnline())continue;
    var common=data.getPlayerCommonData();var r=new PlayerBotWorldRoster.Resident(owners.get(id),id,common.getMapId(),common.getX(),common.getY(),common.getZ(),common.getHeading());
    if(playerFocus.active()) {
     var counts=focusCounts();var demand=playerFocus.available(counts,target);
     if(!demand.active())continue;
     var choices=demand.prefer(PlayerBotWorldPlacement.focusChoices(common.getRace(),common.getLevel(),demand,populationRandom),counts);
     if(choices.isEmpty())continue;var p=choices.getFirst();
     r=new PlayerBotWorldRoster.Resident(owners.get(id),id,p.map(),p.x(),p.y(),p.z(),common.getHeading());
    }
    validateResidence(r);
    var previous=populationSchedule.load().events().get(id);
    populationSchedule.event(id,new PlayerBotPopulationSchedule.Events(settings.periodic()?PlayerBotPopulationPolicy.deadline(now,populationRandom,settings.presenceMin(),settings.presenceMax()):0,0,previous==null?0:previous.teleportAt(),previous==null?0:previous.placedLevel()));
    var updated=new TreeMap<>(residents);updated.put(id,r);worldRoster.save(updated);residents=updated;nextWorldLoad=0;
   }
  }catch(Exception e){log.error("Server population maintenance failed; native identities/progress and saved configuration retained",e);}
 }
 private void provisionServerReserve(PlayerBotPopulationPolicy.Settings settings,Map<Integer,PlayerBotWorldPopulation.Pool> pools,int capacity,long now)throws Exception {
   // Factory-before-login, with one native job outside the manager monitor.
   // Pending identities count toward the finite bound but cannot be admitted.
    pools=serverPopulation.load();int e=0,a=0;for(var pool:pools.values())if(pool.race()==com.aionemu.gameserver.model.Race.ELYOS)e+=pool.characters().size();else a+=pool.characters().size();
    var race=e<(e+a+1)*settings.elyosPercent()/100.0?com.aionemu.gameserver.model.Race.ELYOS:com.aionemu.gameserver.model.Race.ASMODIANS;
    PlayerBotWorldPopulation.Pool selected=null;
    for(int shard=0;shard<PlayerBotPopulationPolicy.ACCOUNT_SHARDS;shard++) {
     int account=PlayerBotPopulationPolicy.account(race,shard);var pool=pools.get(account);
     if(pool==null) {
      pool=new PlayerBotWorldPopulation.Pool(account,"world-population-"+(-account),(byte)0,now,race,shard%2==0?com.aionemu.gameserver.model.Gender.MALE:com.aionemu.gameserver.model.Gender.FEMALE,PlayerBotWorldLooks.neutral(),capacity,List.of());serverPopulation.put(pool);
     }
     if(pool.characters().size()<Math.min(capacity,pool.desired())){selected=pool;break;}
    }
    if(selected==null)throw new IllegalStateException("Server bot account namespace exhausted; no identities reused");
    int maxPlayer=World.getInstance().getAllPlayers().stream().filter(p->!p.isPlayerBot()).mapToInt(Player::getLevel).max().orElse(settings.minLevel());
    int level=PlayerBotPopulationPolicy.level(settings,maxPlayer,populationRandom);
    // Only new identities may adapt their initial level to occupied native camp bands.
    // Existing levels/XP/builds are never rewritten to follow a player.
    if(playerFocus.active() && !PlayerBotWorldPlacement.focusCompatible(race,level,playerFocus)) {
     var levels=new ArrayList<Integer>();for(int candidate=settings.minLevel();candidate<=settings.maxLevel();candidate++)
      if(PlayerBotWorldPlacement.focusCompatible(race,candidate,playerFocus))levels.add(candidate);
     if(!levels.isEmpty())level=levels.get(populationRandom.nextInt(levels.size()));
    }
    queueWorldCreation(selected,serverPopulation,level);
 }
 void initializeAutomaticAdmissions()throws java.io.IOException {
  if(automaticAdmissionsInitialized || !automaticPopulationReady)return;
  var retained=new TreeMap<>(residents);
  retained.entrySet().removeIf(e->PlayerBotPopulationLifecycle.resetAdmission(PlayerBotPopulationPolicy.serverAccount(e.getValue().account()),
   sessions.containsKey(e.getKey()),retiringWorldBots.contains(e.getKey()),retireQueue.containsKey(e.getKey())));
  if(!retained.equals(residents)){worldRoster.save(retained);residents=retained;}
  automaticAdmissionsInitialized=true;
  log.info("World-bot factory ready; saved characters retained; automatic admission intents rebuilt for target {}",effectiveWorldTarget);
 }
 int automaticPendingAdmissions(){return residents==null?0:(int)residents.values().stream()
  .filter(r->PlayerBotPopulationPolicy.serverAccount(r.account()) && !sessions.containsKey(r.character()) && !retiringWorldBots.contains(r.character())).count();}
 void releaseSurplusPendingAdmissions(int target)throws java.io.IOException {
  int active=(int)sessions.values().stream().filter(s->s.world() && PlayerBotPopulationPolicy.serverAccount(s.bot().getAccount().getId())).count();
  int remaining=Math.max(0,target-active);var retained=new TreeMap<>(residents);
  for(var r:new TreeMap<>(residents).values())if(PlayerBotPopulationPolicy.serverAccount(r.account()) && !sessions.containsKey(r.character()) && !retiringWorldBots.contains(r.character()) && !retireQueue.containsKey(r.character())
   && r.character()!=focusJobId && !execution.busy(r.character()) && !PlayerBotLease.isReserved(r.character())) {
   // A player map change makes this offline login intent undrainable: the
   // loader excludes it, so retaining it would block the entire next reserve
   // batch. Release only geographic intent, in the same durable roster save;
   // native identities, character progress and saved events remain untouched.
   if(playerFocus.active() && !playerFocus.contains(r.map()))retained.remove(r.character());
   else if(remaining>0)remaining--;else retained.remove(r.character());
  }
  if(!retained.equals(residents)){worldRoster.save(retained);residents=retained;}
 }
 long populationManagerDelay(long now) {
  int seconds=PlayerBotConfig.POPULATION_UPDATE_INTERVAL_SECONDS;
  if(seconds<1 || seconds>3600)throw new IllegalArgumentException("Population update interval must be 1..3600 seconds");
  int online=(int)sessions.values().stream().filter(s->s.world() && PlayerBotPopulationPolicy.serverAccount(s.bot().getAccount().getId())).count();
  boolean initializing=Math.max(0,now-populationStartedAt)<510L*PlayerBotConfig.POPULATION_MAX;
  return (initializing?1L:seconds)*(online<PlayerBotConfig.POPULATION_MIN*0.9?500L:1000L);
 }
 private boolean populationRetirementAllowed(PlayerBotSession s) {
  var b=s.bot();return PlayerBotPopulationLifecycle.retirementAllowed(s.autonomous(),s.closing(),b.getPlayerGroup()!=null,b.isInAlliance(),s.closing());
 }
 private void commitPopulationDeparture(int id,long now)throws java.io.IOException {
  var event=populationSchedule.load().events().get(id);if(event==null)return;
  var settings=PlayerBotPopulationPolicy.settings();
  // A zero presence deadline records an already-committed departure. Recovery
  // must finish removing residence without sampling another offline break.
  long sampled=PlayerBotConfig.POPULATION_OFFLINE_COOLDOWN && event.presentUntil()>0?PlayerBotPopulationPolicy.deadline(now,populationRandom,settings.offlineMin(),settings.offlineMax()):0;
  long offline=PlayerBotPopulationLifecycle.postExpiryOfflineUntil(PlayerBotConfig.POPULATION_OFFLINE_COOLDOWN,event.offlineUntil(),sampled);
  populationSchedule.event(id,new PlayerBotPopulationSchedule.Events(0,offline,event.teleportAt(),event.placedLevel()));
 }
 // A pending native load must not consume a manager interval. The pinned manager
 // drains botLoading during its faster initialization cadence. Aion keeps its
 // single native worker and resumes population work at the next bounded poll.
 boolean serverPopulationDue(long now) {return !backgroundWork.busy() && now>=nextServerPopulation;}
 private PlayerBotPopulationSchedule.State reconcilePopulationTarget(long now,PlayerBotPopulationPolicy.Settings settings)throws java.io.IOException {
  var before=populationSchedule.load();var state=populationSchedule.reconcile(now,settings,PlayerBotConfig.POPULATION_ENABLED,populationRandom);
  requestedWorldTarget=PlayerBotConfig.POPULATION_ENABLED?state.target():0;
  effectiveWorldTarget=PlayerBotPopulationPolicy.target(requestedWorldTarget,PlayerBotConfig.WORLD_MAX_ACTIVE,
   (int)residents.values().stream().filter(r->!PlayerBotPopulationPolicy.serverAccount(r.account())).count());
  effectiveWorldTarget=playerFocus.target(effectiveWorldTarget);
  if(before.target()!=state.target() || before.targetUntil()!=state.targetUntil())log.info("Server world-bot population target {} ({}..{})",requestedWorldTarget,settings.minimum(),settings.maximum());
  return state;
 }
 void worldWorkCompleted() {nextPopulation=0;nextServerPopulation=0;nextWorldLoad=0;}
 void nativeLoginCompleted(){nextPopulation=0;nextWorldLoad=0;}
 PlayerClass plannedPopulationClass(Player bot) {
  if(!PlayerBotPopulationPolicy.serverAccount(bot.getAccount().getId()))return PlayerBotWorldPopulation.playerClass(bot.getObjectId());
  try {return PlayerBotWorldPopulation.planned(serverPopulation.load().get(bot.getAccount().getId()),bot.getObjectId());}
  catch(java.io.IOException e){throw new IllegalStateException("Cannot read population specialization; identity retained",e);}
 }
 boolean placePopulation(PlayerBotSession session) {
  return placePopulation(session,false);
 }
 private boolean placePopulation(PlayerBotSession session,boolean focusMove) {
  if(!PlayerBotConfig.POPULATION_ENABLED || !PlayerBotPopulationPolicy.serverAccount(session.bot().getAccount().getId()) || !PlayerBotWorldPlacement.idle(session))return false;
  var bot=session.bot();long now=System.currentTimeMillis();
  try {
   if(now<nextPlacementAttempt.getOrDefault(bot.getObjectId(),0L))return false;
   var committed=populationSchedule.peek();if(committed==null)return false;
   var event=committed.events().get(bot.getObjectId());if(event==null)return false;
   boolean stranded=PlayerBotWorldActivities.stranded(bot,now);
   if(!focusMove && !stranded && event.placedLevel()==0 && now<event.teleportAt())return false;
   boolean initial=event.placedLevel()==0;
   if(!initial && (PlayerBotWorldAscension.pending(bot) || !focusMove && (!PlayerBotWorldActivities.idle(bot) || !stranded && now<event.teleportAt() && event.placedLevel()==bot.getLevel())))return false;
   var settings=PlayerBotPopulationPolicy.settings();
   Set<Integer> focusMaps=Set.of();
   if(focusMove) {
    var counts=focusCounts();counts.computeIfPresent(bot.getWorldId(),(map,count)->Math.max(0,count-1));
    int admitted=(int)residents.values().stream().filter(r->PlayerBotPopulationPolicy.serverAccount(r.account())).count();
    var demand=playerFocus.available(counts,PlayerBotPopulationLifecycle.redistributionCeiling(effectiveWorldTarget,admitted));if(!demand.active())return false;focusMaps=demand.maps();
   }
   // Write the retry time first. Native teleport errors cannot produce a rapid relocation loop.
   nextPlacementAttempt.put(bot.getObjectId(),now+30000);
   populationSchedule.event(bot.getObjectId(),new PlayerBotPopulationSchedule.Events(event.presentUntil(),event.offlineUntil(),now+30000,event.placedLevel()));
   if(!PlayerBotWorldPlacement.place(session,stranded?PlayerBotWorldPlacement.Purpose.FIELD_RESCUE:PlayerBotWorldPlacement.Purpose.POPULATION,settings.cityChance(),populationRandom,focusMaps))return false;
   if(stranded)log.info("World bot {} has no reachable eligible activity; moved to a validated field camp with the same saved character/progress",bot.getName());
   populationSchedule.event(bot.getObjectId(),new PlayerBotPopulationSchedule.Events(event.presentUntil(),event.offlineUntil(),PlayerBotPopulationPolicy.deadline(now,populationRandom,settings.teleportMin(),settings.teleportMax()),bot.getLevel()));
   return true;
  }catch(Exception e){log.error("Population placement failed for {}; native character retained",bot.getName(),e);return false;}
 }

	/** Population residency survives controller logout; adapted from pinned RandomPlayerbotMgr. */
	public void startWorldBots() {synchronized(populationLock) {
		try {
			loadWorldRoster();
			if(PlayerBotConfig.ENABLED && PlayerBotConfig.WORLD_ENABLED)reconcilePopulationTarget(System.currentTimeMillis(),PlayerBotPopulationPolicy.settings());
			if(!residents.isEmpty() || !population.load().isEmpty() || PlayerBotConfig.POPULATION_ENABLED)startTasks();
		}
		catch(Exception e){log.error("World bot roster could not be loaded; no residents were started",e);}}
	}
	private void loadWorldRoster() throws java.io.IOException {synchronized(populationLock){if(residents==null)residents=worldRoster.load();}}
	public synchronized void publishWorldBot(Player owner,String name) {
		if(!PlayerBotConfig.ENABLED || !PlayerBotConfig.WORLD_ENABLED || shuttingDown)throw new IllegalArgumentException("World bots are disabled.");
		var session=find(owner,name);synchronized(session){if(!current(session))throw new IllegalArgumentException("This companion has left; refresh before retrying.");var bot=session.bot();
		if(!session.generated() || session.world() || bot.getAccount().getId()!=owner.getAccount().getId())throw new IllegalArgumentException("Only your own Temporary Bots can become world residents.");
		if(owner.isInInstance() || bot.isInInstance() || owner.isFlying() || bot.isFlying() || owner.isDead() || bot.isDead()
			|| owner.getController().isInCombat() || bot.getController().isInCombat() || bot.isTrading() || bot.isCasting() || session.closing())
			throw new IllegalArgumentException("Register a living, idle Temporary Bot on the ground in an ordinary world map.");
		try {
			loadWorldRoster();if(residents.size()>=PlayerBotConfig.WORLD_MAX_ACTIVE)throw new IllegalArgumentException("World population limit reached.");
			var r=new PlayerBotWorldRoster.Resident(bot.getAccount().getId(),bot.getObjectId(),bot.getWorldId(),bot.getX(),bot.getY(),bot.getZ(),bot.getHeading());
			validateResidence(r);
			PlayerBotPersistence.save(bot,r.home());
			synchronized(populationLock){if(residents.size()>=PlayerBotConfig.WORLD_MAX_ACTIVE && !residents.containsKey(bot.getObjectId()))throw new IllegalArgumentException("World population limit reached; companion remains owned.");var updated=new java.util.TreeMap<>(residents);updated.put(bot.getObjectId(),r);worldRoster.save(updated);residents=updated;}
			homes.put(bot.getObjectId(),r.home());
			bot.setWorldBotController(0);releaseWorldControl(session);
			PacketSendUtility.sendMessage(owner,bot.getName()+" is now an independent world bot. Invite it through the normal party menu.");
		}catch(java.io.IOException | java.sql.SQLException e){throw new IllegalStateException("World residence could not be saved",e);}}
	}
	public synchronized void withdrawWorldBot(Player owner,String name) {
		try {
			loadWorldRoster();
			var entry=PlayerBotRoster.list(owner.getAccount().getId()).stream().filter(r->name.equalsIgnoreCase(r.name())).findFirst().orElseThrow(()->new IllegalArgumentException("Choose your own registered world bot."));
			var r=residents.get(entry.id());if(r==null || r.account()!=owner.getAccount().getId())throw new IllegalArgumentException("That character is not your world resident.");
			if(population.load().values().stream().anyMatch(p->p.characters().contains(entry.id())))throw new IllegalArgumentException("This bot belongs to the automatic population. Use .bot worldpool <count> to change its population target.");
			retiringWorldBots.add(entry.id());var session=sessions.get(entry.id());
			if(session!=null)dismiss(session);else removeResidence(entry.id());
			if(residents.containsKey(entry.id()))throw new IllegalStateException("World bot remains reserved pending a successful save; withdrawal will retry.");
			PacketSendUtility.sendMessage(owner,name+" withdrawn from the world. Its saved native character and progress remain in your roster.");
		}catch(java.io.IOException e){throw new IllegalStateException("World residence could not be updated",e);}
	}
	private void removeResidence(int id)throws java.io.IOException {synchronized(populationLock) {
		var updated=new java.util.TreeMap<>(residents);updated.remove(id);worldRoster.save(updated);residents=updated;retiringWorldBots.remove(id);expiryRetirements.remove(id);retireQueue.remove(id);}
	}
	public List<String> worldBots() {
		return sessions.values().stream().filter(PlayerBotSession::world).map(s->s.bot().getName()+" Lv"+s.bot().getLevel()+" map "+s.bot().getWorldId()+" "+(s.closing()?"saving":s.autonomous()?"independent": "party: "+s.owner().getName())).sorted().toList();
	}
 public int companionCount(){return (int)sessions.values().stream().filter(s->!s.world()).count();}
 public List<String> worldBots(Player viewer,int page,boolean here) {
  if(page<1)throw new IllegalArgumentException("Use .bot worldlist [page starting at 1] [here].");
  var bots=sessions.values().stream().filter(s->s.world() && s.bot().isSpawned() && !s.closing())
   .filter(s->!here || s.bot().getWorldId()==viewer.getWorldId() && s.bot().getInstanceId()==viewer.getInstanceId())
   .sorted(here?Comparator.comparingDouble((PlayerBotSession s)->com.aionemu.gameserver.utils.PositionUtil.getDistance(viewer,s.bot()))
    .thenComparing(s->s.bot().getName()):Comparator.comparing(s->s.bot().getName())).toList();
  int pages=Math.max(1,(bots.size()+19)/20);var lines=new ArrayList<String>();
  lines.add("Spawned world bots"+(here?" on your map/instance":"")+": "+bots.size()+". Page "+page+"/"+pages+". Use .bot worldstats for totals.");
  if(page>pages)return lines;
  for(var s:bots.subList((page-1)*20,Math.min(bots.size(),page*20))) {
   var b=s.bot();lines.add(b.getName()+" #"+b.getObjectId()+" "+b.getRace()+" Lv"+b.getLevel()+" map "+b.getWorldId()+" instance "+b.getInstanceId()
    +String.format(Locale.ROOT," (%.0f, %.0f, %.0f) ",b.getX(),b.getY(),b.getZ())
    +(here?String.format(Locale.ROOT,"%.0fm away, %s; ",com.aionemu.gameserver.utils.PositionUtil.getDistance(viewer,b),viewer.getKnownList().sees(b)?"visible to you":"outside your current visibility"):"")+s.activitySummary());
  }
  return lines;
 }
 public List<String> worldStats(Player viewer) {
  var active=sessions.values().stream().filter(s->s.world() && s.bot().isSpawned() && !s.closing()).toList();
  int global=(int)active.stream().filter(s->PlayerBotPopulationPolicy.serverAccount(s.bot().getAccount().getId())).count();
  int admitted=residents==null?0:(int)residents.values().stream().filter(r->PlayerBotPopulationPolicy.serverAccount(r.account())).count();
  var lines=new ArrayList<String>();
  try {var pools=serverPopulation.load();var settings=PlayerBotPopulationPolicy.settings();
   int poolSize=pools.values().stream().mapToInt(p->p.characters().size()).sum();
   int poolCapacity=PlayerBotPopulationRotation.poolCapacity(settings.maximum(),PlayerBotConfig.WORLD_MAX_ACTIVE,settings.periodic(),PlayerBotConfig.POPULATION_ROTATION_RATIO,
    Math.min(100,Math.max(1,PlayerBotConfig.GENERATED_MAX_PER_ACCOUNT)));
   var ids=new HashSet<Integer>();pools.values().forEach(p->ids.addAll(p.characters()));long now=System.currentTimeMillis();
   long cooldown=populationSchedule.load().events().entrySet().stream().filter(e->ids.contains(e.getKey()) && e.getValue().offlineUntil()>now).count();
   lines.add("Persistent world pool identities="+poolSize+"/"+poolCapacity+" (includes pending reservations); registered inactive="+Math.max(0,poolSize-global)+", offline cooldown="+cooldown
   +"; newly created this run="+createdWorldCharacters+", saved-character loads this run="+loadedWorldCharacters
   +". Offline characters are reused; creation only fills a pool shortfall.");
   lines.add("Automatic population phase: "+(!automaticPopulationReady?"provisioning finite saved pool before login":!automaticAdmissionsInitialized?"rebuilding startup admission intents":automaticPendingAdmissions()>0?"draining saved-login batch":"managing online population")
    +"; lower targets stop new admissions, existing residents leave through timed rotation.");}
  catch(java.io.IOException e){throw new IllegalStateException("Cannot read persistent world population; saved file retained",e);}
  lines.add("World population: requested="+requestedWorldTarget+", target="+effectiveWorldTarget+", spawned="+global+", remaining="+Math.max(0,effectiveWorldTarget-global)
   +", admitted waiting="+Math.max(0,admitted-global)+", native job="+(backgroundWork.busy()?"in progress":"idle")+", world limit="+PlayerBotConfig.WORLD_MAX_ACTIVE+"; companions="+companionCount()+"/"+PlayerBotConfig.MAX_ACTIVE);
  if(playerFocus.active())lines.add("Player-area population: maps="+new TreeSet<>(playerFocus.maps())+", redistributing existing population="+PlayerBotPopulationLifecycle.redistributionCeiling(effectiveWorldTarget,admitted)
   +", preferred camp radius="+playerFocus.radius()+"m; safe redistribution is paced; unseen excess is saved offline, existing characters reused.");
  long elyos=active.stream().filter(s->s.bot().getRace()==com.aionemu.gameserver.model.Race.ELYOS).count();
  lines.add("All spawned world bots="+active.size()+", Elyos="+elyos+", Asmodians="+(active.size()-elyos)
   +", combat="+active.stream().filter(s->s.bot().getController().isInCombat()).count()+", dead="+active.stream().filter(s->s.bot().isDead()).count()
   +", looting="+active.stream().filter(s->s.bot().isLooting()).count()+", traveling to quest="+active.stream().filter(s->PlayerBotWorldActivities.questTravel(s.bot())).count()
   +", traveling to grind="+active.stream().filter(s->PlayerBotWorldActivities.grindTravel(s.bot())).count());
  lines.add("World activity: dormant="+active.stream().filter(PlayerBotSession::dormant).count()
   +"; unoccupied-map dormancy="+PlayerBotConfig.WORLD_ONLY_OCCUPIED_MAPS+"; companions remain active with their owners.");
  var maps=new TreeMap<String,Integer>();for(var s:active){var b=s.bot();maps.merge("map "+b.getWorldId()+" instance "+b.getInstanceId(),1,Integer::sum);}
  maps.forEach((map,count)->lines.add(map+": "+count+" spawned"));
  if(viewer!=null)lines.add("On your map/instance: "+active.stream().filter(s->s.bot().getWorldId()==viewer.getWorldId() && s.bot().getInstanceId()==viewer.getInstanceId()).count()+". Use .bot worldlist 1 here for coordinates.");
  return lines;
 }
 private void reportWorldPopulation(){
  long now=System.currentTimeMillis();if(now<nextWorldReport)return;nextWorldReport=now+30000;
  var current=worldStats(null);
  for(var line:current)if(!lastWorldReport.contains(line))log.info("Playerbots: {}",line);
  // A map disappearing from the current snapshot is also a population change.
  for(var previous:lastWorldReport)if(previous.startsWith("map ")) {
   var map=previous.substring(0,previous.indexOf(':')+1);
   if(current.stream().noneMatch(line->line.startsWith(map)))log.info("Playerbots: {} 0 spawned",map);
  }
  lastWorldReport=List.copyOf(current);
 }
 private Map<Integer,Integer> focusCounts() {
  var counts=new HashMap<Integer,Integer>();if(residents!=null)for(var r:residents.values())if(PlayerBotPopulationPolicy.serverAccount(r.account())) {
   var session=sessions.get(r.character());int map=session==null?r.map():session.bot().getWorldId();
   if(session==null || !session.closing())counts.merge(map,1,Integer::sum);
  }
  return counts;
 }
 private boolean focusProtected(PlayerBotSession session) {
  var bot=session.bot();return PlayerBotPopulationFocus.protectedActor(session.autonomous(),session.closing(),PlayerBotWorldPlacement.idle(session),
   focusHumans.stream().anyMatch(p->p.isSpawned() && p.getInstanceId()==1 && p.getWorldId()==bot.getWorldId()
    && com.aionemu.gameserver.utils.PositionUtil.getDistance(p,bot)<150),
   bot.getKnownList().streamPlayers().anyMatch(p->!p.isPlayerBot() && p.getKnownList().sees(bot)));
 }
 private boolean focusSuitable(PlayerBotSession session) {
  var bot=session.bot();return playerFocus.contains(bot.getWorldId())
   && PlayerBotWorldPlacement.focusCompatibleOnMap(bot.getRace(),bot.getLevel(),bot.getWorldId());
 }
 /** One native move/save at a time, separate from AI ticks and complete-character SQL. */
 private void maintainPlayerFocus(long now)throws java.io.IOException {
  if(now>=nextFocusSnapshot) {
   nextFocusSnapshot=now+2000;var players=World.getInstance().getAllPlayers();
   focusHumans=players.stream().filter(p->!p.isPlayerBot() && p.isSpawned()).toList();
   var next=PlayerBotPopulationFocus.capture(focusHumans);
   if(!next.maps().equals(playerFocus.maps()))nextServerPopulation=0;
   playerFocus=next;
  }
  if(!playerFocus.active() || residents==null || !automaticPopulationReady || !automaticAdmissionsInitialized || backgroundWork.busy() || now<nextFocusWork || shuttingDown
   || !PlayerBotConfig.ENABLED || !PlayerBotConfig.WORLD_ENABLED || !PlayerBotConfig.POPULATION_ENABLED)return;
  nextFocusWork=now+2000;var counts=focusCounts();
  var eligible=sessions.values().stream().filter(s->s.world() && PlayerBotPopulationPolicy.serverAccount(s.bot().getAccount().getId()))
   .filter(s->!focusProtected(s)).toList();
  int admitted=(int)residents.values().stream().filter(r->PlayerBotPopulationPolicy.serverAccount(r.account())).count();
  // A lower random target stops admissions; it does not turn map distribution into forced logout.
  int distribution=PlayerBotPopulationLifecycle.redistributionCeiling(effectiveWorldTarget,admitted);
  for(var s:eligible) {
   var b=s.bot();boolean offMap=!focusSuitable(s);
   boolean distant=!playerFocus.nearby(b.getWorldId(),b.getX(),b.getY(),b.getZ());
   boolean shortage=playerFocus.maps().stream().anyMatch(map->counts.getOrDefault(map,0)<playerFocus.mapTarget(map,distribution));
   var demand=offMap?playerFocus.available(counts,distribution):playerFocus;
   if((offMap && shortage || !offMap && distant && playerFocus.mapTarget(b.getWorldId(),distribution)>0
    && counts.getOrDefault(b.getWorldId(),0)<=playerFocus.mapTarget(b.getWorldId(),distribution)) && now>=nextPlacementAttempt.getOrDefault(b.getObjectId(),0L)
    && demand.active() && !PlayerBotWorldAscension.pending(b) && PlayerBotWorldPlacement.focusCompatible(b.getRace(),b.getLevel(),demand)) {
    focusJobId=b.getObjectId();
    backgroundWork.submit(ThreadPoolManager.getInstance()::executeLongRunning,()->{
     focusJobThread=Thread.currentThread();
     try{synchronized(s){if(!shuttingDown && sessions.get(b.getObjectId())==s && !focusProtected(s))placePopulation(s,true);}}
     catch(Exception e){log.error("Player-area relocation failed for {}; native character retained",b.getName(),e);}
     finally{focusJobThread=null;focusJobId=0;worldWorkCompleted();}
    });return;
   }
  }
  // Save unobserved excess/off-map actors offline. Never retire player-controlled/manual actors.
  var retire=eligible.stream().filter(s->!focusSuitable(s)
   || counts.getOrDefault(s.bot().getWorldId(),0)>playerFocus.mapTarget(s.bot().getWorldId(),distribution))
   .sorted(Comparator.comparingInt(s->focusSuitable(s)?1:0)).findFirst().orElse(null);
  PlayerBotWorldRoster.Resident pending=retire==null?residents.values().stream()
   .filter(r->PlayerBotPopulationPolicy.serverAccount(r.account()) && !sessions.containsKey(r.character())
    && !playerFocus.contains(r.map()))
   .findFirst().orElse(null):residents.get(retire.bot().getObjectId());
  if(pending==null)return;int id=pending.character();focusJobId=id;
  backgroundWork.submit(ThreadPoolManager.getInstance()::executeLongRunning,()->{
   focusJobThread=Thread.currentThread();
   try {
    synchronized(retire==null?populationLock:retire) {
     if(shuttingDown || !playerFocus.active())return;
     if(retire!=null) {
      var bot=retire.bot();
      if(sessions.get(id)!=retire || focusProtected(retire)
       || PlayerBotWorldPlacement.nearHuman(bot.getWorldId(),bot.getX(),bot.getY(),bot.getZ())
       || focusSuitable(retire) && focusCounts().getOrDefault(bot.getWorldId(),0)<=playerFocus.mapTarget(bot.getWorldId(),
        PlayerBotPopulationLifecycle.redistributionCeiling(effectiveWorldTarget,(int)residents.values().stream().filter(r->PlayerBotPopulationPolicy.serverAccount(r.account())).count())))return;
      retiringWorldBots.add(id);retire.markClosing();
     }else if(!residents.containsKey(id) || sessions.containsKey(id)
      || playerFocus.contains(residents.get(id).map()))return;
    }
    var event=populationSchedule.load().events().get(id);
    // Geographic retirement never creates a timer or erases an existing saved cooldown.
    populationSchedule.event(id,new PlayerBotPopulationSchedule.Events(0,event==null?0:event.offlineUntil(),
     event==null?0:event.teleportAt(),event==null?0:event.placedLevel()));
    if(retire==null)removeResidence(id);else dismiss(retire);
   }catch(Exception e){log.error("Player-area retirement {} held pending a successful native save",id,e);}
   finally{focusJobThread=null;focusJobId=0;worldWorkCompleted();}
  });
 }
	private void validateResidence(PlayerBotWorldRoster.Resident r) {
		var map=World.getInstance().getWorldMap(r.map());
		if(map==null || map.isInstanceType() || map.getMainWorldMapInstance().getRegion(r.x(),r.y(),r.z())==null)
			throw new IllegalArgumentException("World residence must be a valid open-world position.");
	}
 private void loadWorldResidents(){loadWorldResidents(false);}
 private void loadWorldResidents(boolean manualOnly) {
  if(residents==null || backgroundWork.busy() || !PlayerBotConfig.WORLD_ENABLED || !PlayerBotConfig.ENABLED || shuttingDown || System.currentTimeMillis()<nextWorldLoad)return;
  // An empty manual-priority probe must not postpone the automatic loader that
  // runs later in this same maintenance poll.
  if(!manualOnly)nextWorldLoad=System.currentTimeMillis()+500;
  int world=(int)sessions.values().stream().filter(PlayerBotSession::world).count();
  int global=(int)sessions.values().stream().filter(s->s.world() && PlayerBotPopulationPolicy.serverAccount(s.bot().getAccount().getId())).count();
  int target=PlayerBotPopulationPolicy.target(PlayerBotConfig.POPULATION_ENABLED?requestedWorldTarget:0,PlayerBotConfig.WORLD_MAX_ACTIVE,
   (int)residents.values().stream().filter(r->!PlayerBotPopulationPolicy.serverAccount(r.account())).count());
  if(world>=PlayerBotConfig.WORLD_MAX_ACTIVE)return;
  var pending=populationLoadCandidates(manualOnly,target,global,world);
  Integer id=PlayerBotPopulationPolicy.nextLoad(pending,sessions.keySet(),retiringWorldBots,nextWorldAdmission,System.currentTimeMillis());
  if(id==null)return;var r=residents.get(id);
  // A lease/admission failure must not monopolize the only native background job.
  nextWorldAdmission.put(id,System.currentTimeMillis()+30000);
  backgroundWork.submit(ThreadPoolManager.getInstance()::executeLongRunning,()->{
   try{loadWorldResident(r);}
   catch(Exception e){log.error("World bot {} could not enter; saved character retained; admission will retry in 30 seconds",r.character(),e);}
   finally {
    try{failedWorldAdmission(r,System.currentTimeMillis());}
    catch(java.io.IOException e){log.error("Failed world admission {} remains registered pending a durable retry-state save",r.character(),e);}
   }
  });
 }
 List<Integer> populationLoadCandidates(boolean manualOnly,int target,int global,int world){
  return residents.values().stream().filter(r->!manualOnly || !PlayerBotPopulationPolicy.serverAccount(r.account()))
   .filter(r->!PlayerBotPopulationPolicy.serverAccount(r.account()) || automaticPopulationReady && automaticAdmissionsInitialized && (!playerFocus.active() || playerFocus.contains(r.map())))
   .filter(r->PlayerBotPopulationPolicy.mayEnter(PlayerBotPopulationPolicy.serverAccount(r.account()),PlayerBotConfig.POPULATION_ENABLED,target,global,world,PlayerBotConfig.WORLD_MAX_ACTIVE))
   .map(PlayerBotWorldRoster.Resident::character).toList();
 }

 // RandomPlayerbotMgr::OnPlayerLoginError clears its add/currentBots state.
 // Keep native identity and the completion-based Aion retry deadline, but release
 // the failed global admission slot so another eligible reserve can take it.
 void failedWorldAdmission(PlayerBotWorldRoster.Resident r,long now)throws java.io.IOException {synchronized(populationLock) {
  if(shuttingDown || !PlayerBotPopulationPolicy.serverAccount(r.account()) || sessions.containsKey(r.character()) || residents==null || !residents.containsKey(r.character()))return;
  nextWorldAdmission.put(r.character(),Math.addExact(now,30000));
  removeResidence(r.character());nextServerPopulation=0;}
 }

 private PlayerBotPersistence.Home checkpoint(PlayerBotSession session) {
  Player bot=session.bot();
  if(session.world() && bot.isSpawned() && !bot.isInInstance())return new PlayerBotPersistence.Home(bot.getWorldId(),bot.getX(),bot.getY(),bot.getZ(),bot.getHeading(),0);
  return homes.get(bot.getObjectId());
 }
 PlayerBotSession worldSession(Player bot){var s=sessions.get(bot.getObjectId());return s!=null && s.world() && !s.closing() ? s : null;}

	private void loadWorldResident(PlayerBotWorldRoster.Resident r)throws Exception {
		if(PlayerBotPopulationPolicy.serverAccount(r.account()) && (!automaticPopulationReady || !automaticAdmissionsInitialized))return;
		validateResidence(r);
  // A committed offline event survives queued-retirement interruption/restart.
  // The background completion releases this failed admission, retaining the ID
  // and its SQL cooldown for the normal eligible-reserve selection.
  if(PlayerBotPopulationPolicy.serverAccount(r.account())) {
   var event=populationSchedule.load().events().get(r.character());if(event!=null && !PlayerBotPopulationLifecycle.admissionAllowed(event.offlineUntil(),System.currentTimeMillis()))return;
  }
		if(PlayerDAO.getAccountId(r.character())!=r.account() || PlayerBotRoster.list(r.account()).stream().noneMatch(e->e.id()==r.character() && e.ready()))
			throw new IllegalArgumentException("World roster identity is not a ready dedicated bot");
		var lease=PlayerBotLease.acquire(r.character());if(lease==null)return;
		Player bot=null;PlayerBotSession session=null;boolean registered=false;
		try {
			if(World.getInstance().isInWorld(r.character()) || PlayerLeaveWorldService.isLeavingWorld(r.character()))return;
			var data=AccountService.loadPlayerAccountData(r.character());
			if(data==null || data.getDeletionDate()!=null || data.getPlayerCommonData().isOnline()
				|| data.getCharBanInfo()!=null && data.getCharBanInfo().getEnd()>=System.currentTimeMillis()/1000)throw new IllegalArgumentException("World bot is banned, deleting or online");
   boolean focused=PlayerBotPopulationPolicy.serverAccount(r.account()) && PlayerBotPopulationFocus.capture(World.getInstance().getAllPlayers()).active();
   PlayerBotWorldPlacement.Location entryCamp=null;
   if(focused){
    var common=data.getPlayerCommonData();
    var selection=PlayerBotWorldPlacement.chooseDestination(common.getRace(),common.getLevel(),PlayerBotPopulationPolicy.settings().cityChance(),populationRandom);
    entryCamp=selection.location();
    if(entryCamp==null){
     log.info("World bot {} admission deferred: {}; checked {} native camps/probes; saved character remains offline",r.character(),selection.reason(),selection.checked());
     return; // Normal completion-based retry custody; avoid loading all skills/effects for a rejected camp.
    }
   }
			Account account=new Account(r.account());var pool=PlayerBotPopulationPolicy.serverAccount(r.account())?serverPopulation.load().get(r.account()):population.load().get(r.account());
			account.setName(pool==null?"world-bot-"+r.account():pool.name());if(pool!=null){account.setMembership(pool.membership());account.setCreationDate(pool.created());}account.addPlayerAccountData(data);
			account.setAccountWarehouse(new PlayerStorage(null,StorageType.ACCOUNT_WAREHOUSE));
			long began=System.currentTimeMillis();bot=PlayerService.getPlayer(r.character(),account);PlayerBotPersistence.validateLoaded(bot,began);
			if(bot.isInPrison())throw new IllegalArgumentException("World bot is in prison");
			bot.setPlayerBotOwner(bot.getObjectId());bot.setWorldBotController(0);bot.setCubeLimit();bot.setWarehouseLimit();
   // This factory state is not a completed quest. Restore it only for dedicated global advanced characters.
   if(PlayerBotPopulationPolicy.serverAccount(r.account()) && !bot.getPlayerClass().isStartingClass())bot.getCommonData().setDaeva(true);
			com.aionemu.gameserver.services.StigmaService.onPlayerLogin(bot);
			for(var learned:bot.getSkillList().getAllSkills()) {
				var template=DataManager.SKILL_DATA.getSkillTemplate(learned.getSkillId());
				if(template!=null && template.isPassive())SkillEngine.getInstance().applyEffectDirectly(template,learned.getSkillLevel(),bot,bot);
			}
			session=new PlayerBotSession(bot,bot,lease,0,false);
   var saved=PlayerBotPersistence.Home.of(bot);
   var map=DataManager.WORLD_MAPS_DATA.getTemplate(saved.map());
   if(map==null || map.isInstance())saved=r.home();
   boolean repairedLanding=false;
   boolean safeLanding=com.aionemu.gameserver.world.geo.GeoService.getInstance().isPlayerBotLanding(saved.map(),1,saved.x(),saved.y(),saved.z());
   // Reused global identities use the same zone preference as new characters,
   // before visibility. Manual residents and owned companions retain their homes.
   boolean concentrate=PlayerBotPopulationPolicy.serverAccount(r.account()) && (PlayerBotConfig.POPULATION_CONCENTRATE || focused);
   if(!safeLanding || concentrate) {
    var camp=entryCamp!=null?entryCamp:PlayerBotWorldPlacement.destination(bot.getRace(),bot.getLevel(),PlayerBotPopulationPolicy.settings().cityChance(),populationRandom);
    if(camp==null && focused){log.info("World bot {} admission deferred after focus changed; saved character remains offline",r.character());return;}
    if(camp==null && !safeLanding)throw new IllegalStateException("Saved world position has no safe walking exit and no validated replacement camp exists; character retained");
    if(camp!=null) {
     saved=new PlayerBotPersistence.Home(camp.map(),camp.x(),camp.y(),camp.z(),saved.heading(),0);repairedLanding=true;
     if(!safeLanding)log.info("World bot {} saved position needs repair; entering validated level {} camp on map {}",bot.getName(),bot.getLevel(),camp.map());
    }
   }
   if(entryCamp!=null && !PlayerBotWorldPlacement.current(entryCamp))return;
   synchronized(this) {synchronized(session) {
    var latestFocus=PlayerBotPopulationFocus.capture(World.getInstance().getAllPlayers());
    if(PlayerBotPopulationPolicy.serverAccount(r.account()) && latestFocus.active() && !latestFocus.contains(saved.map()))return;
    if(shuttingDown || !PlayerBotConfig.ENABLED || !PlayerBotConfig.WORLD_ENABLED || !residents.containsKey(r.character()) || retiringWorldBots.contains(r.character())
     || sessions.values().stream().filter(PlayerBotSession::world).count()>=PlayerBotConfig.WORLD_MAX_ACTIVE || sessions.containsKey(r.character()))return;
    boolean global=PlayerBotPopulationPolicy.serverAccount(r.account());
    int target=global?PlayerBotPopulationPolicy.target(PlayerBotConfig.POPULATION_ENABLED?populationSchedule.load().target():0,
     PlayerBotConfig.WORLD_MAX_ACTIVE,(int)residents.values().stream().filter(resident->!PlayerBotPopulationPolicy.serverAccount(resident.account())).count())
     :pool!=null?pool.desired():(int)residents.values().stream().filter(resident->resident.account()==r.account()).count();
    if(global)target=latestFocus.target(target);
    int activeWorld=(int)sessions.values().stream().filter(PlayerBotSession::world).count();
    int activeGlobal=(int)sessions.values().stream().filter(s->s.world() && PlayerBotPopulationPolicy.serverAccount(s.bot().getAccount().getId())).count();
    // Recheck after slow native loading, under the same lock as publication/manual admission.
    // Target/config/manual capacity can have changed while this character was being loaded.
    if(global && (!automaticPopulationReady || !automaticAdmissionsInitialized) || !PlayerBotPopulationPolicy.mayEnter(global,PlayerBotConfig.POPULATION_ENABLED,target,activeGlobal,activeWorld,PlayerBotConfig.WORLD_MAX_ACTIVE))return;
    if(!World.getInstance().setPosition(bot,saved.map(),1,saved.x(),saved.y(),saved.z(),saved.heading()))throw new IllegalStateException("Invalid residence position");
    World.getInstance().storeObject(bot);World.getInstance().spawn(bot);bot.getController().updateZone();bot.getLifeStats().updateCurrentStats();
    sessions.put(bot.getObjectId(),session);homes.put(bot.getObjectId(),repairedLanding?saved:r.home());registered=true;
    if(PlayerBotPopulationPolicy.serverAccount(r.account()))loadedWorldCharacters++;
    nextWorldAdmission.remove(bot.getObjectId());
    nativeLoginCompleted();
    com.aionemu.gameserver.questEngine.QuestEngine.getInstance().onLevelChanged(bot);
    com.aionemu.gameserver.questEngine.QuestEngine.getInstance().onEnterWorld(bot);
    if(bot.isSpawned()) {
     long spawned=sessions.values().stream().filter(s->s.world() && s.bot().isSpawned() && !s.closing())
      .filter(s->global?PlayerBotPopulationPolicy.serverAccount(s.bot().getAccount().getId()):s.bot().getAccount().getId()==r.account()).count();
     log.info("[{}/{}] World bot spawned: {} ({}) {} level {} map {} instance {} at {}, {}, {}",spawned,target,bot.getName(),bot.getObjectId(),bot.getRace(),bot.getLevel(),bot.getWorldId(),bot.getInstanceId(),bot.getX(),bot.getY(),bot.getZ());
    }
   }}
			// Native roster/cache notifications may load legion data; keep them outside the shared monitor.
			synchronized(session) {
				if(sessions.get(bot.getObjectId())==session && !session.closing() && bot.isSpawned())PlayerBotLegionPresence.entered(bot);
			}
		}finally {
			if(!registered) {
				if(session!=null)session.markClosing();
				if(bot!=null){if(bot.isSpawned())World.getInstance().despawn(bot);World.getInstance().removeObject(bot);bot.getEffectController().removeAllEffects(true);bot.getLifeStats().cancelAllTasks();}
				lease.close();
			}
		}
	}
	private void changeWorldController(PlayerBotSession previous,Player controller) {
		var bot=previous.bot();
		synchronized(previous) {
			previous.markClosing();bot.setTarget(null);
			bot.setWorldBotController(controller==bot?0:controller.getObjectId());
			var next=new PlayerBotSession(controller,bot,previous.lease(),controller==bot?0:companions(controller).size(),false);
			sessions.put(bot.getObjectId(),next);
		}
	}
	private void releaseWorldControl(PlayerBotSession previous) {
		var bot=previous.bot();
		synchronized(previous) {
			PlayerBotTeams.remove(bot);previous.markClosing();bot.setTarget(null);bot.setWorldBotController(0);
			var next=new PlayerBotSession(bot,bot,previous.lease(),0,false);
			synchronized(next) {
				sessions.put(bot.getObjectId(),next);
				// A new decision cannot run between control publication and native arrival.
				// Never leave a controller-less resident stranded in a private instance.
				if(bot.isInInstance() || !bot.isSpawned()) {
					var r=residents.get(bot.getObjectId());validateResidence(r);
					com.aionemu.gameserver.services.teleport.TeleportService.teleportTo(bot,r.map(),1,r.x(),r.y(),r.z(),r.heading());
				}
			}
		}
	}
	/** GuildAcceptAction: answer the native legion request without changing party control. */
 public boolean respondLegionInvitation(Player inviter,Player bot,com.aionemu.gameserver.model.team.legion.Legion legion) {
  if(!bot.isPlayerBot())return false;
  PlayerBotSession session;boolean enabled,worldEnabled,available;
  synchronized(this) {
   session=sessions.get(bot.getObjectId());enabled=PlayerBotConfig.ENABLED;worldEnabled=PlayerBotConfig.WORLD_ENABLED;
   available=!shuttingDown && !retiringWorldBots.contains(bot.getObjectId());
  }
  // Native membership persistence must not hold the population manager's monitor.
  return PlayerBotLegionInvitation.respond(session,inviter,bot,legion,enabled,worldEnabled,available);
 }
	/** Complete the actual native invitation, then attach control only after membership is confirmed. */
 public synchronized boolean inviteWorldBot(Player inviter,Player bot,com.aionemu.gameserver.model.team.group.events.PlayerGroupInvite invitation) {
  return inviteWorldBot(inviter,bot,invitation,false);
 }
 public synchronized boolean inviteWorldBot(Player inviter,Player bot,com.aionemu.gameserver.model.team.alliance.events.PlayerAllianceInvite invitation) {
  return inviteWorldBot(inviter,bot,invitation,true);
 }
 private boolean inviteWorldBot(Player inviter,Player bot,com.aionemu.gameserver.model.gameobjects.player.RequestResponseHandler<Player> invitation,boolean alliance) {
  if(!bot.isWorldBot())return false;
  var current=sessions.get(bot.getObjectId());var team=inviter.getCurrentTeam();
  synchronized(current==null?bot:current) {
  boolean ownTeam=current!=null && current.owner()==inviter && PlayerBotTeams.same(inviter,bot);
  if(current==null || sessions.get(bot.getObjectId())!=current || current.closing() || !current.autonomous() && !ownTeam || retiringWorldBots.contains(bot.getObjectId()) || shuttingDown
   || !PlayerBotConfig.ENABLED || !PlayerBotConfig.WORLD_ENABLED || inviter.isPlayerBot() || !inviter.isOnline() || !inviter.isSpawned()
   || bot.isDead() || !bot.isSpawned() || bot.isTrading() || bot.getFlightPath()!=null || bot.isInTeam() && !ownTeam
   || inviter.getController().isInCombat() || bot.getController().isInCombat() || inviter.getRace()!=bot.getRace()
   || !ownTeam && companions(inviter).size()>=(alliance?PlayerBotRaidPolicy.limit():Math.min(5,PlayerBotRaidPolicy.limit()))
   || team!=null && (!PlayerBotTeams.allowed(team) || !team.isLeader(inviter) && !(alliance && inviter.isInAlliance() && inviter.getPlayerAlliance().isSomeCaptain(inviter)))
   || !alliance && inviter.isInAlliance()) {
   invitation.denyRequest(inviter,bot);
   PacketSendUtility.sendMessage(inviter,"Invite an available living bot of your faction while both are outside combat; lead the party/alliance and keep a free companion slot.");return true;
  }
  invitation.acceptRequest(inviter,bot);
  if(PlayerBotTeams.same(inviter,bot)) {
   changeWorldController(current,inviter);
   PacketSendUtility.sendMessage(inviter,bot.getName()+" joined your "+(alliance?"alliance":"party")+". Normal .bot orders work; its character remains an independent world bot.");
  }
  return true;}
 }
	private Player worldController(PlayerBotSession s,Player excluded) {
		var group=s.bot().getCurrentTeam();if(group==null || !PlayerBotTeams.allowed(group))return null;
		var leader=group.getLeader().getObject();
		if(leader!=excluded && !leader.isPlayerBot() && leader.isOnline())return leader;
		return group.getMembers().stream().filter(p->p!=excluded && !p.isPlayerBot() && p.isOnline()).findFirst().orElse(null);
	}
	public void controllerLogout(Player owner) {
		for(var s:companions(owner)) {
			if(!s.world()){dismiss(s);continue;}
			synchronized(this){if(sessions.get(s.bot().getObjectId())==s){var replacement=worldController(s,owner);if(replacement==null)releaseWorldControl(s);else changeWorldController(s,replacement);}}
		}
	}


	public static PlayerBotService getInstance() { return INSTANCE; }
	private PlayerBotService() {}

	public List<PlayerBotSession> companions(Player owner) {
		return sessions.values().stream().filter(s -> s.owner() == owner && !s.autonomous()).toList();
	}

	public PlayerBotSession find(Player owner, String name) {
		return companions(owner).stream().filter(s -> s.bot().getName().equalsIgnoreCase(name)).findFirst()
			.orElseThrow(() -> new IllegalArgumentException("That character is not your active companion."));
	}

	/** Called by the native quest-share packet. Only the bot's recruiting owner can make it accept. */
	public boolean acceptSharedQuest(Player owner, Player bot, int questId) {
		PlayerBotSession session = sessions.get(bot.getObjectId());
		var template = DataManager.QUEST_DATA.getQuestById(questId);
		var ownerState = owner.getQuestStateList().getQuestState(questId);
		if (session == null || session.closing() || session.owner() != owner || bot.isDead()
			|| template == null || template.isCannotShare() || ownerState == null
			|| ownerState.getStatus() == com.aionemu.gameserver.questEngine.model.QuestStatus.COMPLETE
			|| !PlayerBotTeams.same(owner,bot) || bot.getWorldId() != owner.getWorldId() || bot.getInstanceId() != owner.getInstanceId()
			|| !com.aionemu.gameserver.utils.PositionUtil.isInRange(owner, bot, GroupConfig.GROUP_MAX_DISTANCE)) return false;
		synchronized (session) {
			if(sessions.get(bot.getObjectId())!=session || session.closing())return false;
			PlayerBotQuestSync.state(session);
			boolean accepted = com.aionemu.gameserver.services.QuestService.startQuest(new com.aionemu.gameserver.questEngine.model.QuestEnv(null, bot, questId));
			if (accepted) PlayerBotQuestSync.accepted(owner, bot, questId, false);
			return accepted;
		}
	}

	public synchronized void recruit(Player owner, String name) {
		ensureOwner(owner);
		PlayerAccountData ownData = owner.getAccount().getPlayerAccDataList().stream()
			.filter(p -> p.getPlayerCommonData().getName().equalsIgnoreCase(name)).findFirst()
			.orElse(null);
		if (ownData == null) {
			var dedicated = PlayerBotRoster.list(owner.getAccount().getId()).stream()
				.filter(e -> e.ready() && name.equalsIgnoreCase(e.name())).findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Recruit an offline character or generated companion belonging to your own account."));
			ownData = AccountService.loadPlayerAccountData(dedicated.id());
		}
		int id = ownData.getPlayerCommonData().getPlayerObjId();
		boolean generated = PlayerBotRoster.contains(id);
		try { loadWorldRoster(); if(residents.containsKey(id))throw new IllegalArgumentException("This is a world bot. Invite it normally, or withdraw it with .bot worldoff <name> first."); }
		catch(java.io.IOException e){throw new IllegalStateException("Cannot verify world roster",e);}
		if (id == owner.getObjectId()) throw new IllegalArgumentException("You cannot recruit your current character.");
		PlayerBotLease lease = PlayerBotLease.acquire(id);
		if (lease == null) throw new IllegalArgumentException("Character is logging in, saving, or already recruited.");
		Player bot = null;
		PlayerBotSession session = null;
		boolean registered = false;
		try {
			if (World.getInstance().isInWorld(id) || PlayerLeaveWorldService.isLeavingWorld(id))
				throw new IllegalArgumentException("Wait for that character to finish logging out.");
			PlayerAccountData data = AccountService.loadPlayerAccountData(id);
			PlayerCommonData common = data.getPlayerCommonData();
			boolean banned = data.getCharBanInfo() != null && data.getCharBanInfo().getEnd() >= System.currentTimeMillis() / 1000;
			if (!PlayerBotRules.canRecruit(PlayerDAO.getAccountId(id) == owner.getAccount().getId(), common.getRace() == owner.getRace(),
				common.isOnline(), banned, data.getDeletionDate() != null, false, generated ? 0 : common.getLevel() - owner.getLevel(), PlayerBotConfig.LEVEL_DIFFERENCE))
				throw new IllegalArgumentException("Companion must be offline, same faction, not banned/deleting, and within " + PlayerBotConfig.LEVEL_DIFFERENCE + " levels.");
			// Isolated account view avoids changing the human's live warehouse owner, visible equipment or passports.
			Account account = new Account(owner.getAccount().getId());
			account.setName(owner.getAccount().getName());
			account.setMembership(owner.getAccount().getMembership());
			account.setCreationDate(owner.getAccount().getCreationDate());
			account.addPlayerAccountData(data);
			account.setAccountWarehouse(new PlayerStorage(null, StorageType.ACCOUNT_WAREHOUSE));
			long loadStarted = System.currentTimeMillis();
			bot = PlayerService.getPlayer(id, account);
			try { PlayerBotPersistence.validateLoaded(bot, loadStarted); }
			catch (java.sql.SQLException e) { throw new IllegalArgumentException("Companion data could not be loaded completely. Check server logs before recruiting it.", e); }
			if (generated && !bot.getPlayerClass().isStartingClass()) bot.getCommonData().setDaeva(true);
			if (bot.isInPrison()) throw new IllegalArgumentException("Leave prison before recruiting this character.");
			PlayerBotPersistence.Home home = PlayerBotPersistence.Home.of(bot);
			bot.setPlayerBotOwner(owner.getObjectId());
			bot.setCubeLimit(); bot.setWarehouseLimit();
			session = new PlayerBotSession(owner, bot, lease, companions(owner).size(), generated);
			// Register Temporary Bot management before native Stigma reconstruction.
			// Owned alts return immediately from this path and retain normal login rules.
			PlayerBotTemporary.tick(session,Boolean.TRUE.equals(session.snapshot().get("gear")));
			// Stigma skills are temporary and are reconstructed by normal login, not player_skills.
			com.aionemu.gameserver.services.StigmaService.onPlayerLogin(bot);
			for (var skillEntry : bot.getSkillList().getAllSkills()) {
				var template = DataManager.SKILL_DATA.getSkillTemplate(skillEntry.getSkillId());
				if (template != null && template.isPassive())
					SkillEngine.getInstance().applyEffectDirectly(template, skillEntry.getSkillLevel(), bot, bot);
			}
			World.getInstance().setPosition(bot, owner.getWorldId(), owner.getInstanceId(), owner.getX(), owner.getY(), owner.getZ(), owner.getHeading());
			World.getInstance().storeObject(bot);
			World.getInstance().spawn(bot);
			bot.getController().updateZone();
			bot.getLifeStats().updateCurrentStats();
			PlayerBotRaidPolicy.join(owner, bot);
			if (owner.isInInstance()) InstanceService.onEnterInstance(bot);
			sessions.put(id, session); homes.put(id, home); registered = true;
			PlayerBotLegionPresence.entered(bot);
			startTasks();
			PacketSendUtility.sendMessage(owner, "Companion recruited: " + bot.getName()
				+ (bot.isDead() ? ". This character needs resurrection before it can act." : ". Use .bot help for orders."));
		} finally {
			if (!registered) {
				if(session!=null)PlayerBotQuestSync.close(session);
				if (bot != null) {
					PlayerBotTeams.remove(bot);
					World.getInstance().removeObject(bot);
					bot.getEffectController().removeAllEffects(true);
					bot.getLifeStats().cancelAllTasks();
				}
				lease.close();
			}
		}
	}

	private void ensureOwner(Player owner) {
		if (!PlayerBotConfig.ENABLED || shuttingDown) throw new IllegalArgumentException("Player companions are disabled.");
		if (!owner.isOnline() || !owner.isSpawned()) throw new IllegalArgumentException("Wait until your character is in the world.");
		if (owner.getController().isInCombat()) throw new IllegalArgumentException("You cannot recruit companions while in combat. Wait until combat ends.");
		PlayerBotRaidPolicy.room(owner, companions(owner).size(), companionCount());
	}

	/** Creating a saved roster entry does not require a free active party slot. */
	private void ensureCreation(Player owner) {
		if (!PlayerBotConfig.ENABLED || shuttingDown) throw new IllegalArgumentException("Player companions are disabled.");
		if (!owner.isOnline() || !owner.isSpawned()) throw new IllegalArgumentException("Wait until your character is in the world.");
		if (owner.getController().isInCombat()) throw new IllegalArgumentException("You cannot create companions while in combat. Wait until combat ends.");
	}

	/** Generated recruits are normal, persistent starting characters, not temporary NPCs or privileged max-level characters. */
	public synchronized void create(Player owner, String name, PlayerClass playerClass) {
		ensureOwner(owner);
		if (!PlayerBotConfig.GENERATED_ENABLED) throw new IllegalArgumentException("Generated companions are disabled.");
		if (!playerClass.isStartingClass()) throw new IllegalArgumentException("Choose a starting class: warrior, scout, mage, priest, engineer, artist.");
		if (!NameRestrictionService.isValidName(name) || NameRestrictionService.isForbidden(name) || PlayerService.isNameUsedOrReserved(null, name))
			throw new IllegalArgumentException("Choose an available valid character name.");
		int maxCharacters = owner.getAccount().getMembership() >= MembershipConfig.CHARACTER_ADDITIONAL_ENABLE
			? MembershipConfig.CHARACTER_ADDITIONAL_COUNT : GSConfig.CHARACTER_LIMIT_COUNT;
		if (PlayerDAO.getCharacterCountOnAccount(owner.getAccount().getId()) >= maxCharacters)
			throw new IllegalArgumentException("Your account has no free character slot.");
		int id = IDFactory.getInstance().nextId();
		PlayerCommonData common = new PlayerCommonData(id);
		common.setName(name); common.setRace(owner.getRace()); common.setGender(owner.getGender());
		common.setPlayerClass(playerClass); common.setLevel(1);
		// The native client already knows this appearance's race/gender combination.
		var data = new PlayerAccountData(common, owner.getPlayerAppearance().copy());
		Player generated = null;
		try (PlayerBotLease lease = PlayerBotLease.acquire(id)) {
			if (lease == null) throw new IllegalStateException("New character ID is already reserved");
			Account isolated = new Account(owner.getAccount().getId());
			isolated.setName(owner.getAccount().getName()); isolated.setMembership(owner.getAccount().getMembership());
			isolated.setCreationDate(owner.getAccount().getCreationDate()); isolated.addPlayerAccountData(data);
			isolated.setAccountWarehouse(new PlayerStorage(null, StorageType.ACCOUNT_WAREHOUSE));
			generated = PlayerService.newPlayer(data, isolated);
			generated.setPlayerBotOwner(owner.getObjectId());
			PlayerBotGenerationOptions.initialize(generated);
			if (!PlayerService.storeNewPlayer(generated, isolated.getName(), isolated.getId()))
				throw new java.sql.SQLException("Generated character save failed");
			PlayerBotPersistence.save(generated, PlayerBotPersistence.Home.of(generated));
			data.setCreationDate(new Timestamp(System.currentTimeMillis()));
			PlayerService.storeCreationTime(id, data.getCreationDate());
			data.setVisibleItems(generated.getEquipment().getEquippedForAppearance());
		} catch (Exception e) {
			log.error("Normal companion character creation failed for {} (id {})", name, id, e);
			throw new IllegalArgumentException("Character creation failed; check server logs for id " + id + " before retrying.", e);
		} finally {
			if (generated != null) { if (generated.getEffectController() != null) generated.getEffectController().removeAllEffects(true); generated.getLifeStats().cancelAllTasks(); }
		}
		owner.getAccount().addPlayerAccountData(data);
		PacketSendUtility.sendMessage(owner, "Created " + name + " as a level-1 " + playerClass + " in your character roster. It uses a character slot and normal progression.");
		if (owner.getLevel() - 1 <= PlayerBotConfig.LEVEL_DIFFERENCE) recruit(owner, name);
		else PacketSendUtility.sendMessage(owner, "Recruit it with a character within " + PlayerBotConfig.LEVEL_DIFFERENCE + " levels, or play it to level normally.");
	}

	/** Separate bot-only roster: level/class at recruitment, ordinary stats/skills, no human character slot. */
	public synchronized void generate(Player owner, String name, PlayerClass playerClass) {
		ensureCreation(owner);
		int startingLevel = PlayerBotGenerationOptions.level(owner.getLevel());
		if (!PlayerBotConfig.GENERATED_ENABLED) throw new IllegalArgumentException("Generated companions are disabled.");
		if (!PlayerBotRoster.available()) throw new IllegalArgumentException("The separate companion roster needs game-server/sql/playerbots.sql applied and a server restart.");
		if (!PlayerBotCreationPolicy.room(PlayerBotRoster.list(owner.getAccount().getId()).size()))
			throw new IllegalArgumentException("Your Temporary Bot roster has reached its configured limit of " + PlayerBotCreationPolicy.limit() + ".");
		if (startingLevel < 10 && !playerClass.isStartingClass() || startingLevel >= 10 && playerClass.isStartingClass())
			throw new IllegalArgumentException("Choose a starting class below level 10, or an advanced class from level 10 onward.");
		if (!NameRestrictionService.isValidName(name) || NameRestrictionService.isForbidden(name) || PlayerService.isNameUsedOrReserved(null, name))
			throw new IllegalArgumentException("Choose an available valid companion name.");
		int id = IDFactory.getInstance().nextId();
		Player generated = null;
		try (PlayerBotLease lease = PlayerBotLease.acquire(id)) {
			if (lease == null) throw new IllegalStateException("New generated companion ID is already reserved");
			// Pending metadata is inserted first so a partial create can never appear at character selection.
			PlayerBotRoster.reserve(owner.getAccount().getId(), id);
			PlayerCommonData common = new PlayerCommonData(id);
			common.setName(name); common.setRace(owner.getRace()); common.setGender(owner.getGender());
			common.setPlayerClass(playerClass); common.setDaeva(!playerClass.isStartingClass()); common.setLevel(startingLevel);
			var data = new PlayerAccountData(common, owner.getPlayerAppearance().copy());
			Account isolated = new Account(owner.getAccount().getId());
			isolated.setName(owner.getAccount().getName()); isolated.setMembership(owner.getAccount().getMembership());
			isolated.setCreationDate(owner.getAccount().getCreationDate()); isolated.addPlayerAccountData(data);
			isolated.setAccountWarehouse(new PlayerStorage(null, StorageType.ACCOUNT_WAREHOUSE));
			generated = PlayerService.newPlayer(data, isolated);
			generated.setPlayerBotOwner(owner.getObjectId());
			PlayerBotGenerationOptions.initialize(generated);
			PlayerBotTemporary.initialize(generated,PlayerBotRules.roleFor(playerClass));
			generated.getLifeStats().synchronizeWithMaxStats();
			if (!PlayerService.storeNewPlayer(generated, isolated.getName(), isolated.getId()))
				throw new java.sql.SQLException("Generated character or inventory creation failed");
			PlayerBotPersistence.save(generated, PlayerBotPersistence.Home.of(generated));
			PlayerBotRoster.ready(isolated.getId(), id);
		} catch (Exception e) {
			log.error("Generated companion {} (id {}) remains pending after creation failure", name, id, e);
			throw new IllegalArgumentException("Generated companion creation failed. Pending entries stay hidden from character selection; check server logs for id " + id + ".", e);
		} finally {
			if (generated != null) {
				PlayerBotTemporary.release(generated);
				if (generated.getEffectController() != null) generated.getEffectController().removeAllEffects(true);
				generated.getLifeStats().cancelAllTasks();
			}
		}
		PacketSendUtility.sendMessage(owner, "Temporary Bot " + name + " is ready as a level-" + startingLevel + " " + playerClass + ". Automatic builds and equipment apply only to Temporary Bots.");
		try { ensureOwner(owner); }
		catch (IllegalArgumentException e) {
			PacketSendUtility.sendMessage(owner, "Saved in your companion roster. Recruit " + name + " later: " + e.getMessage());
			return;
		}
		recruit(owner, name);
	}

 private void startTasks() {
  synchronized(schedulerLock) {
   if(shuttingDown)return;
   if(task==null || task.isCancelled())task=ThreadPoolManager.getInstance().schedule(this::runTick,400);
   if(saveTask==null || saveTask.isCancelled())saveTask=ThreadPoolManager.getInstance().schedule(this::runSave,1000);
  }
 }
 private PlayerBotBackgroundWork decisionWork(PlayerBotSession session){
  return !session.autonomous()?controlledWork:session.activeDecision()?activeWorldWork:autonomousWork;
 }
 private void runTick() {
  PlayerBotDecisionSchedule.Pass<PlayerBotSession,PlayerBotBackgroundWork> pass=null;
  try{pass=tickPass(System.currentTimeMillis());}finally{synchronized(schedulerLock){if(!shuttingDown) {
   // Reuse immutable deadlines; only three lane states need a fresh check.
   long earliest=pass==null?Long.MAX_VALUE:pass.earliest(lane->!lane.busy());
   boolean running=pass!=null && pass.saturated() || controlledWork.busy() || activeWorldWork.busy() || autonomousWork.busy();
   task=ThreadPoolManager.getInstance().schedule(this::runTick,PlayerBotDecisionSchedule.delay(System.currentTimeMillis(),earliest,PlayerBotDecisionSchedule.maximum(PlayerBotConfig.TICK_MS,running)));
  }}}
 }
 private void runSave() {
  try{checkpointWork.submit(ThreadPoolManager.getInstance()::executeLongRunning,this::saveAll);}
  finally{synchronized(schedulerLock){if(!shuttingDown)saveTask=ThreadPoolManager.getInstance().schedule(this::runSave,100);}}
 }
 private void tick() {tickPass(System.currentTimeMillis());}
 private PlayerBotDecisionSchedule.Pass<PlayerBotSession,PlayerBotBackgroundWork> tickPass(long now) {
  PlayerBotWorldActivity.refresh(now);
  if(now>=nextAiMaintenance) {
   nextAiMaintenance=now+500;
   maintenanceWork.submit(ThreadPoolManager.getInstance()::executeLongRunning,()->{synchronized(populationLock) {
    if(shuttingDown)return;
    // Manual actor work, like player-driven logins upstream, must not wait for
    // thousands of automatic factory jobs. The native worker remains serial.
    loadWorldResidents(true);maintainPopulation();
    maintainServerPopulation();
    try{maintainPlayerFocus(System.currentTimeMillis());}catch(Exception e){log.error("Player-area population update failed; native identities and progress retained",e);}
    loadWorldResidents();reportWorldPopulation();
   }});
  }
  reservations.values().removeIf(r->r.until()<now);
  decisions.retain(sessions.keySet());
  int controlledSlots=controlledWork.available(),activeSlots=activeWorldWork.available(),idleSlots=autonomousWork.available();
  var pass=new PlayerBotDecisionSchedule.Pass<PlayerBotSession,PlayerBotBackgroundWork>(controlledSlots==0 || activeSlots==0 || idleSlots==0);
  boolean reportLag=now>=nextDecisionLagReport;
  for(var session:sessions.values()) {
   int id=session.bot().getObjectId();
   if(session.closing() || id==focusJobId || execution.busy(id) || sessions.get(id)!=session)continue;
   // Sleeping actors own no decision lane or terrain ticket. Observe only map
   // presence until a human arrives or a player takes control of the actor.
   if(!session.needsDecision()){decisions.suspend(id);continue;}
   boolean controlled=!session.autonomous();long reaction=session.reactionDelay();
   boolean active=reaction<=500 || session.bot().isLooting();
   var lane=controlled?controlledWork:active?activeWorldWork:autonomousWork;
   int slots=controlled?controlledSlots:active?activeSlots:idleSlots;
   pass.observe(session,id,decisions.due(id,reaction,now),controlled,active,lane,slots,now,reportLag);
  }
  pass.finish();
  // An idle route/SQL operation must not own the lane of every fighting bot.
  // Party decisions remain serial; actor exclusion protects state transitions.
  for(var lane:pass.ready().entrySet())for(var batch:PlayerBotDecisionSchedule.batches(lane.getValue(),lane.getKey().available()))
   lane.getKey().submit(ThreadPoolManager.getInstance()::executeLongRunning,()->decide(batch));
  if(reportLag){
   if(pass.lateCount()>0){
    var oldest=pass.oldest();
    log.warn("Playerbot decision backlog: {} active actors overdue >=5s; oldest {} ({}) overdue {}ms; controlled/active-world/idle lanes at capacity {}/{}/{}; active-world workers {}/4; activity: {}",
     pass.lateCount(),oldest.value().bot().getName(),oldest.id(),now-oldest.due(),controlledWork.busy(),activeWorldWork.busy(),autonomousWork.busy(),activeWorldWork.running(),oldest.value().activitySummary());
    nextDecisionLagReport=now+30000;
   }
  }
  return pass;
 }
 private void decide(List<PlayerBotDecisionSchedule.Ready<PlayerBotSession>> ready) {
  long began=System.nanoTime();
  for(var candidate:ready) {
   var session=candidate.value();int id=session.bot().getObjectId();
   if(!execution.claim(id,"decision",System.currentTimeMillis()))continue;
   long actorBegan=System.nanoTime();boolean dismiss=false;
   boolean decided=false;
   try {
    if(shuttingDown || sessions.get(id)!=session || session.closing() || id==focusJobId)continue;
    // Other batches may have completed this actor since their immutable snapshot.
    // A stale snapshot must not shorten native reaction/cast/cooldown intervals.
    long admission=System.currentTimeMillis();
    if(decisions.due(id,session.reactionDelay(),admission)>admission)continue;
    decided=true;
    // Membership transitions acquire service -> actor; decisions never do the reverse.
    if(session.world()) {
     var controller=worldController(session,null);
     if(controller==null && !session.autonomous() || controller!=null && session.owner()!=controller) {
      synchronized(this){if(!shuttingDown && sessions.get(id)==session){if(controller==null)releaseWorldControl(session);else changeWorldController(session,controller);}}
      continue;
     }
    }
    synchronized(session) {
     if(shuttingDown || sessions.get(id)!=session || session.closing() || id==focusJobId)continue;
     try {
      if(!PlayerBotConfig.ENABLED || session.world() && !PlayerBotConfig.WORLD_ENABLED) {
       if(session.world())session.markClosing();dismiss=true;
      }else if(!session.tick())dismiss=true;
      session.resetFailures();
     }catch(Exception e) {
      log.error("Playerbot tick failed for {}",session.bot().getName(),e);
      if(session.failed()>=3) {
       if(session.world())session.markClosing();dismiss=true;
       PacketSendUtility.sendMessage(session.owner(),"Companion "+session.bot().getName()+" stopped after repeated AI errors. Check server logs.");
      }
     }
    }
    if(dismiss)dismiss(session);
   }finally {
    if(decided)decisions.complete(id,System.currentTimeMillis());execution.complete(id);
    long elapsed=(System.nanoTime()-actorBegan)/1_000_000L;
    if(elapsed>1000)log.warn("Playerbot {} ({}) decision took {}ms; activity: {}",session.bot().getName(),id,elapsed,session.activitySummary());
   }
   if(System.nanoTime()-began>=50_000_000L)break;
  }
 }
 private void saveAll() {
  if(shuttingDown)return;
  long interval=PlayerBotCheckpointSchedule.interval(PlayerBotConfig.SAVE_SECONDS);
  Integer id=checkpoints.next(sessions.keySet(),System.currentTimeMillis(),interval,key->{var s=sessions.get(key);return s!=null && !s.autonomous() && !execution.busy(key) && key!=focusJobId;});
  if(id==null)for(var entry:retireQueue.entrySet())if(entry.getValue()<=System.currentTimeMillis()) {
   int retiring=entry.getKey();var s=sessions.get(retiring);
   if(s==null){retireQueue.remove(retiring);continue;}
   if(retiring==focusJobId || !execution.claim(retiring,"retirement",System.currentTimeMillis()))continue;
   try{synchronized(s){if(s.closing() || (expiryRetirements.contains(retiring)?populationRetirementAllowed(s):PlayerBotWorldPlacement.idle(s)))dismiss(s);}}finally{execution.complete(retiring);if(sessions.containsKey(retiring))retireQueue.put(retiring,System.currentTimeMillis()+5000);else retireQueue.remove(retiring);}
   return;
  }
  // A busy actor cannot monopolize the checkpoint schedule either.
  if(id==null)id=checkpoints.next(sessions.keySet(),System.currentTimeMillis(),interval,key->!execution.busy(key) && key!=focusJobId);
  if(id==null)return;
  var session=sessions.get(id);if(session==null || !execution.claim(id,"checkpoint",System.currentTimeMillis()))return;
  try {
   if(session.closing()) {if(sessions.get(id)==session)dismiss(session);}
   else synchronized(session){if(!shuttingDown && sessions.get(id)==session && !session.closing())PlayerBotPersistence.save(session.bot(),checkpoint(session));}
  }catch(Exception e){log.error("Companion periodic save failed for {}",session.bot().getName(),e);}
  finally{checkpoints.complete(id,System.currentTimeMillis(),interval);execution.complete(id);}
 }

	public void dismissAll(Player owner) { companions(owner).forEach(this::dismiss); }
	public void dismiss(Player owner, String name) { dismiss(find(owner, name)); }
 private void queueRetirement(int id){retireQueue.putIfAbsent(id,0L);}

	/** Resolve current state at effect application too, so an area attack cannot hit a newly arrived player. */
	public static boolean allowsTarget(Player bot, com.aionemu.gameserver.model.gameobjects.Creature target) {
		if (!bot.isPlayerBot()) return true;
		if (target instanceof com.aionemu.gameserver.model.gameobjects.Npc npc
			&& (npc.isFlag() || !PlayerBotTargetEligibility.visible(bot,npc))) return false;
		PlayerBotSession active=getInstance().session(bot.getObjectId());
		if(bot.isWorldBot() && active!=null && active.autonomous()) {
			if(!bot.isSpawned() || active.closing())return false;
			if(bot.isEnemy(target))return target instanceof com.aionemu.gameserver.model.gameobjects.Npc npc && target.getMaster()==target && active.allowsEnemy(npc);
			return PlayerBotSupport.effectAllowed(bot,target);
		}
		Player owner = World.getInstance().getPlayer(bot.getPlayerBotOwnerId());
		if (owner == null || !owner.isOnline() || !owner.isSpawned()
			|| owner.getWorldId() != bot.getWorldId() || owner.getInstanceId() != bot.getInstanceId()
			|| !PlayerBotTeams.same(owner,bot)) return false;
		if (bot.isEnemy(target)) {
			PlayerBotSession session = getInstance().session(bot.getObjectId());
			return target instanceof com.aionemu.gameserver.model.gameobjects.Npc npc && target.getMaster() == target
				&& session != null && session.allowsEnemy(npc);
		}
		return target.getMaster() instanceof Player ? PlayerBotSupport.effectAllowed(bot,target) : true;
	}

	private PlayerBotSession session(int id) { return sessions.get(id); }
 /** Native timed work needs a current actor, not a client socket. This read does
  * not acquire service/session locks or treat slow-work diagnostics as logout. */
 public boolean allowsHeadlessInteraction(Player player) {
  if(player==null || !player.isPlayerBot() || !player.isPlaying() || !player.isSpawned() || player.isDead())return false;
  var active=sessions.get(player.getObjectId());
  return active!=null && active.bot()==player && current(active) && PlayerBotLease.isReserved(player.getObjectId());
 }
 /** Social presence follows native actor custody, including living and dead bots;
  * it never fabricates a client login or changes human privacy status. */
 public boolean isSociallyPresent(Player player) {
  if(player==null || !player.isPlayerBot() || !player.isSpawned())return false;
  var active=sessions.get(player.getObjectId());
  return active!=null && active.bot()==player && current(active) && PlayerBotLease.isReserved(player.getObjectId());
 }
 PlayerBotSession currentActor(Player player) {
  var active=sessions.get(player.getObjectId());
  return active!=null && active.bot()==player && current(active) ? active : null;
 }
 public boolean current(PlayerBotSession session){return !shuttingDown && sessions.get(session.bot().getObjectId())==session && !session.closing();}
 boolean claimTransfer(PlayerBotSession session){return current(session) && session.bot().getObjectId()!=focusJobId && execution.claim(session.bot().getObjectId(),"party transfer",System.currentTimeMillis());}
 void completeTransfer(PlayerBotSession session){execution.complete(session.bot().getObjectId());}
 /** A native conversion temporarily removes group identity. Reserve registered actors
  * before that handoff so decisions/checkpoints cannot mistake it for dismissal.
  * No session/service monitor is acquired while the native team lock is held. */
 public void convertTeam(List<Player> members,Runnable conversion) {
  boolean[] finished={false};
  boolean admitted=teamWork.submit(Runnable::run,()->{
   var claimed=new ArrayList<Integer>();
   try {
    if(shuttingDown)throw new IllegalArgumentException("Playerbots are shutting down; conversion is unavailable.");
    for(var member:members.stream().distinct().toList()) {
     var session=sessions.get(member.getObjectId());if(session==null)continue;
     int id=member.getObjectId();
     if(session.bot()!=member || !current(session) || id==focusJobId || !execution.claim(id,"native alliance conversion",System.currentTimeMillis()))
      throw new IllegalArgumentException("A companion is busy with native work. Retry conversion after it finishes.");
     claimed.add(id);
    }
    conversion.run();finished[0]=true;
   }finally{claimed.forEach(execution::complete);}
  });
  if(!admitted || !finished[0])throw new IllegalArgumentException("Native team conversion is already running or closing. Retry after it finishes.");
 }
 /** Null means an intentionally headless, registered healthy actor, not a disconnected client. */
 public String headlessHealth(Player player) {
  var s=sessions.get(player.getObjectId());
  if(s==null || s.bot()!=player)return "unregistered headless character";
  if(!PlayerBotLease.isReserved(player.getObjectId()))return "registered actor has no native character lease";
  if(s.closing())return "leaving or held pending custody/save verification";
  // Player.isOnline() means a real client connection; it is intentionally false
  // for every native headless bot. Registration, lease and spawn are its lifecycle.
  if(!player.isSpawned())return "registered actor is outside the world";
  String work=execution.health(player.getObjectId(),System.currentTimeMillis());
  return work!=null && execution.slow(player.getObjectId(),System.currentTimeMillis(),30000)?work:null;
 }
	public PlayerBotRules.Role combatRole(Player player) {
		var session = sessions.get(player.getObjectId());
		return session == null ? PlayerBotRules.roleFor(player.getPlayerClass()) : session.combatRole();
	}

	private void dismiss(PlayerBotSession session) {
  if(session.bot().getObjectId()==focusJobId && Thread.currentThread()!=focusJobThread)return;
		if(session.world() && !session.closing() && !shuttingDown && PlayerBotConfig.ENABLED && PlayerBotConfig.WORLD_ENABLED && !retiringWorldBots.contains(session.bot().getObjectId())) {
			synchronized(this){if(sessions.get(session.bot().getObjectId())==session)releaseWorldControl(session);}return;
		}
		Player bot = session.bot();
		synchronized (session) {
			if(sessions.get(bot.getObjectId())!=session)return;
			try {
				if(expiryRetirements.contains(bot.getObjectId()) && !session.closing()) {
					if(!populationRetirementAllowed(session))return;
					commitPopulationDeparture(bot.getObjectId(),System.currentTimeMillis());
				}
				if(session.world() && bot.isSpawned() && !bot.isInInstance())homes.put(bot.getObjectId(),checkpoint(session));
				session.markClosing();
				PlayerBotTeams.remove(bot);
				if (bot.isSpawned()) {
					InstanceService.onLeaveInstance(bot);
					World.getInstance().despawn(bot);
					PlayerBotLegionPresence.left(bot);
				}
				bot.getLifeStats().cancelAllTasks();
				bot.getEffectController().removeNonStorableEffectsForLogout();
				PlayerBotPersistence.save(bot, checkpoint(session));
				PlayerBotMetadata.release(bot.getAccount().getId(),bot.getObjectId());
				if(retiringWorldBots.contains(bot.getObjectId()))removeResidence(bot.getObjectId());
				bot.getEffectController().removeAllEffects(true);
				World.getInstance().removeObject(bot);
				bot.getCommonData().setOnline(false);
				sessions.remove(bot.getObjectId()); homes.remove(bot.getObjectId()); nextPlacementAttempt.remove(bot.getObjectId());
				session.lease().close();
				if (session.owner().isOnline()) {
					if (!session.generated() && !session.world()) {
						try { session.owner().getAccount().addPlayerAccountData(AccountService.loadPlayerAccountData(bot.getObjectId())); }
						catch (Exception e) { log.warn("Saved companion roster refresh failed for {}", bot.getName(), e); }
					}
					PacketSendUtility.sendMessage(session.owner(), "Companion dismissed and saved: " + bot.getName() + ".");
				}
			} catch (Exception e) {
				// Hold the reservation until a retry succeeds. Never allow a second copy to overwrite unsaved progress.
				log.error("Companion {} is held pending a successful save", bot.getName(), e);
				PacketSendUtility.sendMessage(session.owner(), "Companion " + bot.getName() + " is waiting for a save retry. Its character remains reserved.");
			}
		}
	}

	public boolean relocate(PlayerBotSession session) {
		synchronized(session){return !shuttingDown && sessions.get(session.bot().getObjectId())==session && PlayerBotTransfers.relocate(session);}
	}

	private boolean canEnter(Player owner, WorldMapInstance destination) {
		// The owner has already entered through the native admission path. Bot
		// recruitment/following is not restricted by PvP flags or map allowlists.
		return destination != null;
	}

	public boolean isReserved(Player caster, com.aionemu.gameserver.model.gameobjects.Creature target, SkillKind kind) {
		Reservation r = reservations.get(new ReservationKey(caster.getPlayerBotOwnerId()==0?caster.getObjectId():caster.getPlayerBotOwnerId(), target.getObjectId(), kind));
		return r != null && r.caster() != caster.getObjectId() && r.until() > System.currentTimeMillis();
	}

	public void reserve(Player caster, com.aionemu.gameserver.model.gameobjects.Creature target, SkillKind kind, long until) {
		if (kind == SkillKind.HEAL || kind == SkillKind.RESURRECT || kind == SkillKind.CLEANSE || kind == SkillKind.CONTROL || kind == SkillKind.TAUNT)
			reservations.put(new ReservationKey(caster.getPlayerBotOwnerId()==0?caster.getObjectId():caster.getPlayerBotOwnerId(), target.getObjectId(), kind), new Reservation(caster.getObjectId(), until));
	}

	public void shutdown() {
		synchronized(this) {
			shuttingDown = true; backgroundWork.close();controlledWork.close();activeWorldWork.close();autonomousWork.close();maintenanceWork.close();checkpointWork.close();teamWork.close();
			if (task != null) task.cancel(false);
			if (saveTask != null) saveTask.cancel(false);
		}
		// Pinned Playerbots OnShutdown clears pending logins outside their callbacks.
		// Native Aion creation/load must finish its own persistence/lease cleanup first.
		boolean interrupted = false;
		if (backgroundWork.busy()) log.info("Waiting for pending playerbot creation/load cleanup before shutdown");
		for (;;) {
			try { controlledWork.awaitCompletion();activeWorldWork.awaitCompletion();autonomousWork.awaitCompletion();maintenanceWork.awaitCompletion();checkpointWork.awaitCompletion();backgroundWork.awaitCompletion();teamWork.awaitCompletion(); break; }
			catch (InterruptedException e) { interrupted = true; }
		}
		int count = sessions.size();
  focusJobId=0;focusJobThread=null; // A queued focus callback may have been cancelled by close(); draining is complete.
		log.info("Logging out and saving {} playerbots before GameServer closes", count);
		for (;;) {
			for (var session : List.copyOf(sessions.values())) {
				synchronized(this) {
					if (sessions.get(session.bot().getObjectId()) == session) dismiss(session);
				}
			}
			if (sessions.isEmpty()) break;
			// Dismissal retains failed saves and their native leases. Keep the DB and
			// executors alive until the retry commits rather than silently losing progress.
			log.error("Shutdown waiting for {} playerbot saves/cleanup; retrying in 5 seconds", sessions.size());
			try { Thread.sleep(5000); } catch (InterruptedException e) { interrupted = true; }
		}
		log.info("Playerbot shutdown complete: {} saved and logged out", count);
		PlayerBotMeshRoute.shutdown();
		if (interrupted) Thread.currentThread().interrupt();
	}
}
