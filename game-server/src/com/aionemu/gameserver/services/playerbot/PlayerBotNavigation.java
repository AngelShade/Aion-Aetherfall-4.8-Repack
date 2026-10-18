package com.aionemu.gameserver.services.playerbot;

import java.util.ArrayDeque;
import java.util.Deque;

import com.aionemu.gameserver.geoEngine.math.Vector3f;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.utils.PositionUtil;
import com.aionemu.gameserver.world.geo.GeoService;
import com.aionemu.gameserver.configs.main.PlayerBotConfig;

/** Prepared ground corridors and owner breadcrumbs; native geometry proves every movement edge. */
final class PlayerBotNavigation {
	record Point(float x, float y, float z) {}
	private enum Intent { TRAVEL, TRACK, FOLLOW, FOLLOW_TRAIL, EVADE }
	private final Creature bot;
	private final Deque<Point> trail = new ArrayDeque<>();
	private Point lastOwner;
	private final Deque<Point> route = new ArrayDeque<>();
	private Point routeGoal;
	private volatile PlayerBotRouteSearch routeSearch;
	private final PlayerBotMeshJourney meshJourney=new PlayerBotMeshJourney();
	private Point progressWaypoint;
	private double closestWaypointDistance;
	private volatile long searchProgress;
	private double closestGoalDistance;
	private long lastProgress, nextSearch;
	private long pausedAt;
	private Point observedPosition;
	private int observedWorld,observedInstance;
	private long physicalProgressAt;
	private volatile int routeWorld, routeInstance;
	private static final PlayerBotNavigationSearch SEARCHES = new PlayerBotNavigationSearch();
	private static final PlayerBotNavigationBudget TERRAIN = new PlayerBotNavigationBudget(
		PlayerBotConfig.TERRAIN_BUDGET_MS,PlayerBotConfig.TERRAIN_SLICE_MS,PlayerBotConfig.TERRAIN_WORKERS);
	private boolean partialRoute,lookahead;
	private final Object searchToken = new Object();
	private final Object heightSearchToken = new Object();
	private final Object localSearchToken = new Object();
	private final Object approachSearchToken = new Object();
	private Point localOrigin,localGoal,localBest,approachTargetPoint,approachDestination;
	private java.util.List<PlayerBotHazards.Area> localHazards;
	private double localScore;
	private int localCursor,localLimit,approachCursor,heightCursor;
	private PlayerBotHeightTraversal.Search heightSearch;
	private PlayerBotHeightTraversal.Search preparedJumpSearch;
	private PlayerBotRoadNetwork.Jump preparedJump;
	private Point preparedJumpOrigin;
	private float preparedJumpSpeed;
	private static final int[] HEIGHT_DIRECTIONS={0,30,-30,60,-60,90,-90,135,-135,180};
	private static final float[] HEIGHT_REACHES={2,3,4,6,8};
	private Creature approachTarget;
	private float approachRange;
	private long approachSelected;
	private long nextMove;
	private long nextHeightSearch;
	private Point publishedGoal,publishedDestination;
	private long walkGeneration=1,routeHeadIndex;
	private boolean walkRouteOwned;
	private Point followTrailTail;
	private Intent routeIntent;
	private long intentUntil;
	private int publishedWorld,publishedInstance;
	private int worldId, instanceId;
	private volatile String status = "ready";
	private PlayerBotAnimatedLinks.Ride queuedPlatform;
	private Point platformQueuePoint;
	private int platformQueueRank=-2;
	private long nextPlatformQueueProbe,nextPlatformBoardProbe,platformQueueProgressAt;
	private double platformQueueDistance=Double.POSITIVE_INFINITY;
	private int platformQueueFailures;
	private PlayerBotAnimatedLinks.Ride blockedPlatform;
	private int blockedPlatformWorld,blockedPlatformInstance;
	private long blockedPlatformUntil;
	private boolean transportCongested;
	private boolean routeFailure;
	private boolean groundRouteUnavailable;
	private Point blockedFollowOrigin,blockedFollowGoal;
	private long nextDirectFollowProbe;
	/** Result of the immediately preceding move call, not a cached geometry verdict. */
	boolean routeFailed(){return routeFailure;}
	void clearRouteFailure(){routeFailure=false;}
	private java.util.List<PlayerBotHazards.Area> hazards = java.util.List.of();

	PlayerBotNavigation(Creature bot) { this.bot = bot; }
	boolean waitingForSearchTurn() {
		if (!bot.isSpawned() || bot.isDead() || bot.isCasting() || !bot.canPerformMove() || bot.isFlying()) {
			cancelTicket(searchToken);cancelTicket(heightSearchToken);cancelTicket(localSearchToken);cancelTicket(approachSearchToken);return false;
		}
		boolean turn=TERRAIN.nominated(searchToken) || TERRAIN.nominated(heightSearchToken) || TERRAIN.nominated(localSearchToken) || TERRAIN.nominated(approachSearchToken);
		if (turn && (routeWorld!=bot.getWorldId() || routeInstance!=bot.getInstanceId())) { cancelTicket(searchToken);cancelTicket(heightSearchToken);cancelTicket(localSearchToken);cancelTicket(approachSearchToken);return false; }
		return turn;
	}
	void hazards(java.util.List<PlayerBotHazards.Area> areas) {
		hazards=java.util.List.copyOf(areas);
		if(bot.getMoveController() instanceof com.aionemu.gameserver.controllers.movement.PlayerBotMoveController mover){
			var plan=mover.platformPlan();if(plan!=null)plan.hazards(hazards);
			var safety=hazards;
			mover.walkSafety(walkGeneration,(from,to)->PlayerBotHazards.safePath(new Point(from.x(),from.y(),from.z()),new Point(to.x(),to.y(),to.z()),safety));
		}
	}
	private void clearRoute() {
		if(bot.getMoveController() instanceof com.aionemu.gameserver.controllers.movement.PlayerBotMoveController mover)mover.cancelWalk(walkGeneration);
		walkGeneration++;routeHeadIndex=0;walkRouteOwned=false;followTrailTail=null;route.clear();publishedDestination=null;
	}
	/** Only mover acknowledgments consume a published corridor. Repeated spatial
	 * coordinates cannot acknowledge another generation or a later visit. */
	private void syncWalk() {
		if(!(bot.getMoveController() instanceof com.aionemu.gameserver.controllers.movement.PlayerBotMoveController mover))return;
		var state=mover.walkStatus();if(state.generation()!=walkGeneration)return;
		if(walkRouteOwned)while(!route.isEmpty() && routeHeadIndex<=state.reachedIndex()){route.removeFirst();routeHeadIndex++;}
		publishedDestination=state.active() && state.current()!=null?new Point(state.current().x(),state.current().y(),state.current().z()):null;
		if(state.blocked()) {
			cancelTicket(searchToken);clearSearch();lookahead=partialRoute=false;localOrigin=null;clearRoute();nextSearch=0;
		}
	}
	private java.util.List<com.aionemu.gameserver.controllers.movement.PlayerBotMoveController.WalkPoint> walkPoints() {
		var points=new java.util.ArrayList<com.aionemu.gameserver.controllers.movement.PlayerBotMoveController.WalkPoint>();
		long index=routeHeadIndex;
		for(Point point:route){points.add(new com.aionemu.gameserver.controllers.movement.PlayerBotMoveController.WalkPoint(index++,point.x(),point.y(),point.z()));if(points.size()==16)break;}
		return points;
	}
	private boolean refreshWalk() {
		if(!(bot.getMoveController() instanceof com.aionemu.gameserver.controllers.movement.PlayerBotMoveController mover))return false;
		synchronized(mover) {
			var state=mover.walkStatus();if(state.generation()!=walkGeneration || !state.active() || state.current()==null)return false;
			var points=walkRouteOwned?walkPoints():java.util.List.of(state.current());
			var safety=hazards;
			return mover.publishWalk(walkGeneration,points,(from,to)->PlayerBotHazards.safePath(new Point(from.x(),from.y(),from.z()),new Point(to.x(),to.y(),to.z()),safety));
		}
	}
	/** An idle arbitration may retain already admitted motion, but cannot renew
	 * its three-second lease, revive a failed corridor or keep a search nominated. */
	boolean retainCurrent(){
		if(bot.isCasting() || bot instanceof Player player && (player.isTrading() || player.isLooting()) || bot.isDead() || !bot.canPerformMove())return false;
		if(!(bot.getMoveController() instanceof com.aionemu.gameserver.controllers.movement.PlayerBotMoveController mover))return false;
		var state=mover.walkStatus();return state.generation()==walkGeneration && state.active() && !state.blocked();
	}
	boolean planningRoute(){return routeSearch!=null && routeSearch.pending();}
	boolean travelling(){
		long now=System.currentTimeMillis();
		return routeGoal!=null && bot.isSpawned() && !bot.isDead() && now<=intentUntil
			&& PositionUtil.getDistance(bot,routeGoal.x(),routeGoal.y(),routeGoal.z())>1.6
			&& (retainCurrent() || routeProgressing(now) || waitingForTransport() || localOrigin!=null || pausedAt!=0 && now-pausedAt<3000);
	}
	boolean waitingForTerrainWork(){return TERRAIN.waiting(searchToken) || TERRAIN.waiting(heightSearchToken)
		|| TERRAIN.waiting(localSearchToken) || TERRAIN.waiting(approachSearchToken);}
	boolean waitingForTransport(){return queuedPlatform!=null || bot.getMoveController() instanceof com.aionemu.gameserver.controllers.movement.PlayerBotMoveController mover && mover.ridingPlatform();}
	boolean transportCongested(){return transportCongested;}
	boolean routeProgressing(long now){return planningRoute() && (TERRAIN.waiting(searchToken) || now-searchProgress<30000);}
	private void cancelTicket(Object token){
		SEARCHES.cancel(token);TERRAIN.cancel(token);
		if(token==heightSearchToken){heightSearch=null;preparedJumpSearch=null;preparedJump=null;preparedJumpOrigin=null;}
	}
	private PlayerBotNavigationBudget.Priority terrainPriority(double distance) {
		if(bot instanceof Player player && (!player.isWorldBot() || player.getPlayerBotOwnerId()!=0))return PlayerBotNavigationBudget.Priority.CONTROLLED;
		return distance<=30 ? PlayerBotNavigationBudget.Priority.NEAR : PlayerBotNavigationBudget.Priority.WORLD;
	}
	private void planRoute(Point start,Point goal,boolean ahead,long now) {
		cancelTicket(heightSearchToken);
		clearSearch();
		routeSearch=new PlayerBotRouteSearch(bot.getWorldId(),start,goal,bot.getWorldMapInstance().getParent().getWorldSize(),4096,PlayerBotConfig.TERRAIN_SEGMENT_METRES,meshJourney);
		lookahead=ahead;searchProgress=now;
		if(routeSearch.pending())TERRAIN.request(searchToken,terrainPriority(PositionUtil.getDistance(bot,goal.x(),goal.y(),goal.z())));
	}
	private boolean beginGroundRoute(Point origin,Point goal,double distance,long now) {
		if(now<nextSearch){routeFailure=true;status="ground route unavailable; awaiting retry";stopMovement();return false;}
		cancelTicket(heightSearchToken);
		Point start;
		try(var lease=TERRAIN.begin(localSearchToken,terrainPriority(distance))) {
			if(lease==null){localOrigin=origin;localGoal=goal;localHazards=hazards;localCursor=0;localLimit=1;status="waiting to prove route origin";return true;}
			Vector3f supported=GeoService.getInstance().findGroundMovementCollision(bot.getWorldId(),bot.getInstanceId(),origin.x(),origin.y(),origin.z(),origin.x(),origin.y(),goal.z());
			start=supported==null?origin:new Point(supported.x,supported.y,supported.z);
		}
		cancelTicket(localSearchToken);localOrigin=null;
		planRoute(start,goal,false,now);
		if(routeSearch.pending()){status="planning supported ground route";stopMovement();return true;}
		status=routeSearch.reason();clearSearch();groundRouteUnavailable=true;nextSearch=now+3000;routeFailure=true;stopMovement();return false;
	}
	private Point trackingGoal(Point requested,Intent intent,long now) {
		if((intent!=Intent.TRACK && intent!=Intent.FOLLOW) || routeIntent!=intent || routeGoal==null || routeWorld!=bot.getWorldId() || routeInstance!=bot.getInstanceId()
			|| Math.abs(requested.z()-routeGoal.z())>(intent==Intent.FOLLOW?2:.75)
			|| Math.hypot(requested.x()-routeGoal.x(),requested.y()-routeGoal.y())>PlayerBotConfig.TERRAIN_SEGMENT_METRES)return requested;
		// Finish one funded calculation/current proved edge while a moving target
		// shifts within the walking horizon. Rebind at the next edge, never discard
		// the search every quarter metre or claim arrival at the target's new pose.
		if(walkRouteOwned && routeHeadIndex>0)return requested;
		// A moving leader must not cancel the queued short ground proof every
		// 100 ms. Retain that request's age/turn, then track the fresh slot after
		// the native mover starts. Combat TRACK requests retain their old rules.
		if(planningRoute() || TERRAIN.waiting(heightSearchToken) || intent==Intent.FOLLOW && TERRAIN.waiting(localSearchToken) || retainCurrent()
			|| !walkRouteOwned && !route.isEmpty() && publishedDestination==null)return routeGoal;
		return requested;
	}
	boolean progressing(long now){return physicalProgressAt!=0 && now-physicalProgressAt<4000;}
	private void clearSearch(){var previous=routeSearch;routeSearch=null;if(previous!=null)previous.close();}
	private boolean retainingPublishedMove(Point goal,long now) {
		return publishedGoal!=null && publishedDestination!=null && bot.getMoveController().isInMove()
			&& publishedWorld==bot.getWorldId() && publishedInstance==bot.getInstanceId()
			&& PositionUtil.getDistance(publishedGoal.x(),publishedGoal.y(),publishedGoal.z(),goal.x(),goal.y(),goal.z())<.25
			&& (retainCurrent() || PositionUtil.getDistance(bot,publishedDestination.x(),publishedDestination.y(),publishedDestination.z())>.75)
			&& now-lastProgress<4000
			&& PlayerBotHazards.safePath(new Point(bot.getX(),bot.getY(),bot.getZ()),publishedDestination,hazards);
	}
	private void prefetchAhead(Point goal,long now) {
		if(!partialRoute || route.isEmpty() || routeSearch!=null)return;
		// Begin future work while a complete walking horizon remains, not only
		// at the last two points. Keep at most the configured horizon plus the
		// mover's normal two-second lookahead; do not resolve an entire world trip.
		double distance=0;Point previous=new Point(bot.getX(),bot.getY(),bot.getZ());
		for(Point point:route){distance+=PositionUtil.getDistance(previous.x(),previous.y(),previous.z(),point.x(),point.y(),point.z());previous=point;}
		if(distance<=PlayerBotConfig.TERRAIN_SEGMENT_METRES+Math.max(6,bot.getGameStats().getMovementSpeedFloat()*2))
			planRoute(route.peekLast(),goal,true,now);
	}
	private boolean ownsPendingMove(Point goal) {
		if(!bot.isSpawned() || bot.isFlying() || routeGoal==null
			|| PositionUtil.getDistance(routeGoal.x(),routeGoal.y(),routeGoal.z(),goal.x(),goal.y(),goal.z())>.25)return false;
		if(meshJourney.boarding(new Point(bot.getX(),bot.getY(),bot.getZ()))!=null)return true;
		if(routeSearch!=null && routeSearch.pending() && routeSearch.targets(goal) && TERRAIN.waiting(searchToken))return true;
		if(localOrigin==null || localGoal==null || !localOrigin.equals(new Point(bot.getX(),bot.getY(),bot.getZ()))
			|| !hazards.equals(localHazards))return false;
		return localCursor<localLimit && TERRAIN.waiting(localSearchToken) || heightCursor<HEIGHT_DIRECTIONS.length*HEIGHT_REACHES.length && TERRAIN.waiting(heightSearchToken);
	}
	boolean escapeHazards() {
		Point destination = PlayerBotHazards.escape(new Point(bot.getX(), bot.getY(), bot.getZ()), hazards, bot.getHeading());
		return destination != null && move(destination.x(), destination.y(), destination.z(),false,Intent.EVADE);
	}

	void record(Player owner) {
		if (owner.isFlying() || bot.isFlying()) { trail.clear(); lastOwner = null; return; }
		if (worldId != owner.getWorldId() || instanceId != owner.getInstanceId()) {
			trail.clear();
			lastOwner = null;
			worldId = owner.getWorldId();
			instanceId = owner.getInstanceId();
		}
		if (lastOwner == null || PositionUtil.getDistance(owner, lastOwner.x(), lastOwner.y(), lastOwner.z()) > .75) {
			lastOwner = new Point(owner.getX(), owner.getY(), owner.getZ());
			trail.addLast(lastOwner);
			while (trail.size() > 512) trail.removeFirst();
		}
	}

	boolean follow(Player owner, int formationSlot) {
		syncWalk();
		PlayerBotFormation.following(owner, (Player)bot, formationSlot);
		Point formation = PlayerBotFormation.destination(owner, (Player)bot, formationSlot);
		// Companion pursuit is not a world journey. A complete native connection
		// to the owner/slot supersedes old trail, road and boarding proposals.
		Boolean pursuit=directCompanionFollow(owner,formationSlot,formation);
		if(pursuit!=null)return pursuit;
		// Equivalent to keeping WoW's existing MoveFollow generator: renew the
		// admitted direct edge, not a new journey for every moving-leader sample.
		synchronized(bot.getMoveController()) {
			if(PlayerBotFollowIntent.active((Player)bot) && PlayerBotFollowIntent.refresh((Player)bot)
				&& (bot.isFlying() || refreshWalk())) {
				intentUntil=System.currentTimeMillis()+3000;
				PlayerBotFollowIntent.bind(owner,(Player)bot,formationSlot,formation,hazards,!walkRouteOwned);
				return true;
			}
		}
		if (bot.isFlying()) {
			// Airborne follow uses the leader's altitude, never a ground breadcrumb.
			if (!PlayerBotFormation.needsFollow(owner,(Player)bot,formation)) { stop(); return false; }
			boolean moved=move(formation.x(),formation.y(),formation.z(),false,Intent.FOLLOW);
			if(moved)PlayerBotFollowIntent.bind(owner,(Player)bot,formationSlot,formation,hazards,!walkRouteOwned);
			return moved;
		}
		PlayerBotNavigationTrail.consume(trail, new Point(bot.getX(),bot.getY(),bot.getZ()));
		if (!PlayerBotFormation.needsFollow(owner,(Player)bot,formation)) {
			stop();
			return false;
		}
		Point destination=PlayerBotNavigationTrail.target(trail,new Point(bot.getX(),bot.getY(),bot.getZ()),formation,
			GeoService.getInstance().canSee(bot,formation.x(),formation.y(),formation.z(),com.aionemu.gameserver.geoEngine.collision.IgnoreProperties.ANY_RACE));
		// A ground breadcrumb above a drop is no longer the leader's destination.
		// Sight to the lower floor can be occluded by the very lip we must leave;
		// navigation, rather than LOS to an old upper breadcrumb, earns that route.
		if(owner.getZ()<bot.getZ()-1.75f && PositionUtil.getDistance(bot,owner)>20 && Math.hypot(owner.getX()-bot.getX(),owner.getY()-bot.getY())<=40)
			destination=new Point(owner.getX(),owner.getY(),owner.getZ());
		boolean trailTarget=destination!=formation;
		if (trailTarget && followTrailTail != null && routeIntent == Intent.FOLLOW_TRAIL && walkRouteOwned) {
			followTrailTail = PlayerBotNavigationTrail.append(route, trail, followTrailTail,
				Math.max(6,bot.getGameStats().getMovementSpeedFloat()*2));
			if (refreshWalk()) {
				intentUntil=System.currentTimeMillis()+3000;
				PlayerBotFollowIntent.bind(owner,(Player)bot,formationSlot,destination,hazards,false);
				status="following continuous owner footsteps";
				return true;
			}
		}
		boolean moved=move(destination.x(), destination.y(), destination.z(),false,trailTarget?Intent.FOLLOW_TRAIL:Intent.FOLLOW);
		// A funded turn may have proved the earlier leader sample. Classify the
		// final edge against that actual published goal, then let the persistent
		// follow controller refresh it toward the current formation. Detour corners
		// still fail the final-edge check and remain immutable.
		if(moved)PlayerBotFollowIntent.bind(owner,(Player)bot,formationSlot,publishedGoal==null?formation:publishedGoal,hazards,!trailTarget && !walkRouteOwned);
		return moved;
	}

	private boolean controlledFollow() {
		return bot instanceof Player player && player.isPlayerBot() && player.getPlayerBotOwnerId()!=0;
	}
	/** Session may hand an obsolete trip back to Follow only from real static
	 * support and a proved walking connection. No moving-floor implementation changes. */
	boolean resumeCompanionFollow(Player owner,int slot) {
		synchronized(bot.getMoveController()) {
			Point formation=PlayerBotFormation.destination(owner,(Player)bot,slot);
			Boolean result=directCompanionFollow(owner,slot,formation);
			return result!=null;
		}
	}
	private Boolean directCompanionFollow(Player owner,int slot,Point formation) {
		if(!(bot instanceof Player player) || player==owner || !controlledFollow()
			|| player.getPlayerBotOwnerId()!=owner.getObjectId() || !PlayerBotTeams.same(owner,player)
			|| !owner.isOnline() || !owner.isSpawned() || owner.isDead() || !player.isSpawned() || player.isDead()
			|| owner.getWorldId()!=player.getWorldId() || owner.getInstanceId()!=player.getInstanceId()
			|| owner.isFlying() || player.isFlying() || player.isCasting() || !player.canPerformMove()
			|| player.isTrading() || player.isLooting()
			|| !(player.getMoveController() instanceof com.aionemu.gameserver.controllers.movement.PlayerBotMoveController mover)
			|| mover.traversingHeight())return null;
		var geo=GeoService.getInstance();
		var ride=mover.platformPlan();
		if(ride!=null && !Float.isFinite(geo.playerBotStaticFloor(player.getWorldId(),player.getInstanceId(),player.getX(),player.getY(),player.getZ())))return null;
		if(ride==null && PlayerBotFormation.needsFollow(owner,player,formation)
			&& PlayerBotFollowIntent.active(player) && PlayerBotFollowIntent.refresh(player) && refreshWalk()) {
			intentUntil=System.currentTimeMillis()+3000;
			PlayerBotFollowIntent.bind(owner,player,slot,formation,hazards,true);
			status="pursuing owner directly";return true;
		}
		long now=System.currentTimeMillis();Point origin=new Point(player.getX(),player.getY(),player.getZ());
		if(now<nextDirectFollowProbe && origin.equals(blockedFollowOrigin) && formation.equals(blockedFollowGoal))return null;
		Point goal;
		{
			// Ordinary companion steering must not queue behind world path searches.
			// These two bounded native walking probes are not graph/path expansion;
			// the physical mover still proves every actual step. Obstructed fallback
			// retains the existing measured planning budget and its queued turn.
			// A role slot behind furniture is a preference. Clip it from the
			// leader's actual floor rather than climbing toward a guessed slot Z.
			Vector3f slotGround=geo.findGroundMovementCollision(player.getWorldId(),player.getInstanceId(),owner.getX(),owner.getY(),owner.getZ(),formation.x(),formation.y(),formation.z());
			goal=slotGround!=null && Float.isFinite(slotGround.x) && Float.isFinite(slotGround.y) && Float.isFinite(slotGround.z)
				?new Point(slotGround.x,slotGround.y,slotGround.z):new Point(owner.getX(),owner.getY(),owner.getZ());
			Vector3f reached=geo.findGroundMovementCollision(player.getWorldId(),player.getInstanceId(),origin.x(),origin.y(),origin.z(),goal.x(),goal.y(),goal.z());
			if(reached==null || !Float.isFinite(reached.z) || Math.hypot(reached.x-goal.x(),reached.y-goal.y())>.15
				|| Math.abs(reached.z-goal.z())>.75 || !PlayerBotHazards.safePath(origin,goal,hazards)) {
				blockedFollowOrigin=origin;blockedFollowGoal=formation;nextDirectFollowProbe=now+1000;return null;
			}
		}
		cancelTicket(localSearchToken);
		localOrigin=null;
		// Re-proving a clipped slot must not stop/start the mover every AI turn.
		// Refresh only a single direct edge, never bend an existing detour corner.
		if(ride==null && queuedPlatform==null && routeIntent==Intent.FOLLOW && !walkRouteOwned
			&& PlayerBotFormation.needsFollow(owner,player,goal)
			&& mover.refreshFollowDestination(goal.x(),goal.y(),goal.z(),player.getHeading()) && refreshWalk()) {
			publishedGoal=publishedDestination=routeGoal=goal;lastProgress=now;intentUntil=now+3000;
			PlayerBotFormation.following(owner,player,slot);PlayerBotFollowIntent.bind(owner,player,slot,goal,hazards,true);
			status="pursuing owner directly";return true;
		}
		// A native ground connection, not visual proximity, admits this handoff.
		// An airborne passenger retains physical carry until a ground exit exists.
		if(ride!=null){ride.close();mover.abortMove();mover.completedPlatform();}
		if(queuedPlatform!=null)leavePlatformQueue(player);
		cancelTicket(searchToken);cancelTicket(heightSearchToken);cancelTicket(approachSearchToken);
		clearSearch();meshJourney.close();partialRoute=lookahead=groundRouteUnavailable=routeFailure=false;
		clearRoute();routeIntent=Intent.FOLLOW;routeGoal=goal;routeWorld=player.getWorldId();routeInstance=player.getInstanceId();
		publishedGoal=goal;lastProgress=now;intentUntil=now+3000;nextSearch=0;nextMove=0;
		if(!PlayerBotFormation.needsFollow(owner,player,goal)){stop();status="at companion position";return false;}
		var safety=hazards;
		if(!mover.publishWalk(walkGeneration,java.util.List.of(new com.aionemu.gameserver.controllers.movement.PlayerBotMoveController.WalkPoint(0,goal.x(),goal.y(),goal.z())),
			(from,to)->PlayerBotHazards.safePath(new Point(from.x(),from.y(),from.z()),new Point(to.x(),to.y(),to.z()),safety)))return null;
		publishedDestination=goal;publishedWorld=player.getWorldId();publishedInstance=player.getInstanceId();
		PlayerBotFormation.following(owner,player,slot);PlayerBotFollowIntent.bind(owner,player,slot,goal,hazards,true);
		status="pursuing owner directly";return true;
	}

	boolean approach(Creature target, float range) {
		if(bot.getMoveController() instanceof com.aionemu.gameserver.controllers.movement.PlayerBotMoveController mover && (mover.traversingHeight() || mover.ridingPlatform()))return true;
		if(bot.isCasting() || !bot.canPerformMove() || bot.isDead()){cancelTicket(approachSearchToken);return false;}
		if(routeWorld!=bot.getWorldId() || routeInstance!=bot.getInstanceId()){stop();routeWorld=bot.getWorldId();routeInstance=bot.getInstanceId();}
		boolean visible=GeoService.getInstance().canSee(bot,target);
		if (PositionUtil.isInRange(bot,target,range,false) && visible) {
			stop();
			return false;
		}
		var point = PlayerBotCombatPosition.point(bot,target,range);
		if (!visible || !bot.isFlying()) {
			if (range<8) return move(target.getX(),target.getY(),target.getZ(),false,Intent.TRACK);
			Point targetPoint=new Point(target.getX(),target.getY(),target.getZ());
			long now=System.currentTimeMillis();
			if(approachTarget!=target || approachRange!=range || approachTargetPoint==null
				|| PositionUtil.getDistance(approachTargetPoint.x(),approachTargetPoint.y(),approachTargetPoint.z(),targetPoint.x(),targetPoint.y(),targetPoint.z())>.5
				|| approachDestination!=null && now-approachSelected>=4000 && !progressing(now) && !bot.getMoveController().isInMove()
					&& !planningRoute() && !waitingForTerrainWork()) {
				cancelTicket(approachSearchToken);approachTarget=target;approachTargetPoint=targetPoint;approachRange=range;approachCursor=0;approachDestination=null;
			}
			// MovementActions checks an existing move before building a new path.
			// The normal mover still checks floor/body/doors, hazards and progress;
			// casting continues to require fresh native target sight.
			if(approachDestination!=null)return move(approachDestination.x(),approachDestination.y(),approachDestination.z(),true);
			// Find a walkable firing position, preferring the normal range shell. A stale
			// opened-door sight mesh must not veto walking to the other side of the door.
			// Native sight from the target at the destination remains mandatory; casting is unchanged.
			cancelTicket(localSearchToken);cancelTicket(heightSearchToken);cancelTicket(searchToken);localOrigin=null;
			try(var lease=TERRAIN.begin(approachSearchToken,terrainPriority(PositionUtil.getDistance(bot,target)))) {
			 if(lease==null){status="waiting to plan firing position";return true;}
			 double[] factors=bot.isFlying()?new double[]{1}:new double[]{1,.85,.7,.55};
			 double[] angles={0,Math.PI/4,-Math.PI/4,Math.PI/2,-Math.PI/2};
			 while(approachCursor<factors.length*angles.length && System.nanoTime()<lease.deadlineNanos()) {
				double factor=factors[approachCursor/angles.length],angle=angles[approachCursor%angles.length];approachCursor++;
				double dx=(point.x()-target.getX())*factor,dy=(point.y()-target.getY())*factor;
				float x=target.getX()+(float)(Math.cos(angle)*dx-Math.sin(angle)*dy);
				float y=target.getY()+(float)(Math.sin(angle)*dx+Math.cos(angle)*dy);
				Vector3f ground=bot.isFlying()
					? (GeoService.getInstance().canSee(bot,x,y,point.z(),com.aionemu.gameserver.geoEngine.collision.IgnoreProperties.ANY_RACE) ? new Vector3f(x,y,point.z()) : null)
					: GeoService.getInstance().findGroundMovementCollision(bot.getWorldId(),bot.getInstanceId(),bot.getX(),bot.getY(),bot.getZ(),x,y,point.z());
				// A range shell is an XY preference, not a floor at the caster's Z.
				// If a direct walk cannot reach it, prove its support from the target's
				// own native floor; the retained path then finds the detour from us.
				if(!bot.isFlying() && (ground==null || !Float.isFinite(ground.z) || Math.hypot(ground.x-x,ground.y-y)>=.3))
					ground=GeoService.getInstance().findGroundMovementCollision(bot.getWorldId(),bot.getInstanceId(),target.getX(),target.getY(),target.getZ(),x,y,target.getZ());
				if (ground!=null && Float.isFinite(ground.z) && Math.hypot(ground.x-x,ground.y-y)<.3
					&& GeoService.getInstance().canSee(target,ground.x,ground.y,ground.z,com.aionemu.gameserver.geoEngine.collision.IgnoreProperties.ANY_RACE)) {approachDestination=new Point(ground.x,ground.y,ground.z);break;}
			 }

			}
			if(approachDestination==null && approachCursor<(bot.isFlying()?5:20)){TERRAIN.request(approachSearchToken,terrainPriority(PositionUtil.getDistance(bot,target)));status="planning firing position";return true;}
			cancelTicket(approachSearchToken);if(approachDestination==null)approachDestination=bot.isFlying()?point:new Point(target.getX(),target.getY(),target.getZ());approachSelected=now;
			return move(approachDestination.x(),approachDestination.y(),approachDestination.z(),true);
		}
		cancelTicket(approachSearchToken);approachTarget=null;approachDestination=null;
		return move(point.x(),point.y(),point.z(),false,Intent.TRACK);
	}

	boolean retreat(Creature target) {
		float dx = bot.getX() - target.getX(), dy = bot.getY() - target.getY();
		double distance = Math.max(0.01, Math.hypot(dx, dy));
		return move(bot.getX() + (float) (dx / distance * 5), bot.getY() + (float) (dy / distance * 5), bot.getZ(),false,Intent.EVADE);
	}

	boolean escape(PlayerBotTactics.Hazard hazard) {
		Point point = PlayerBotTactics.escape(bot.getX(), bot.getY(), bot.getZ(), hazard, bot.getHeading());
		return move(point.x(), point.y(), point.z(),false,Intent.EVADE);
	}

	boolean move(float targetX, float targetY, float targetZ) {
		return move(targetX,targetY,targetZ,false);
	}
	private boolean move(float targetX,float targetY,float targetZ,boolean firingIntent) {
		return move(targetX,targetY,targetZ,firingIntent,firingIntent?Intent.TRACK:Intent.TRAVEL);
	}
	private boolean move(float targetX,float targetY,float targetZ,boolean firingIntent,Intent intent) {
		routeFailure=false;
		syncWalk();
		if(!Float.isFinite(targetX) || !Float.isFinite(targetY) || !Float.isFinite(targetZ)){stop();return false;}
		// Safety/follow/new orders replace a pending firing branch. Its ticket
		// must not own a queue turn that this successor can never consume.
		if(!firingIntent){cancelTicket(approachSearchToken);approachTarget=null;approachDestination=null;}
		if(bot.getMoveController() instanceof com.aionemu.gameserver.controllers.movement.PlayerBotMoveController mover && (mover.traversingHeight() || mover.ridingPlatform()))return true;
		if (bot.isCasting() || !bot.canPerformMove() || bot.isDead()) { cancelTicket(searchToken);cancelTicket(heightSearchToken);cancelTicket(localSearchToken);cancelTicket(approachSearchToken); return false; }
		long now = System.currentTimeMillis();
		intentUntil=now+3000;
		if(pausedAt!=0){lastProgress+=Math.max(0,now-pausedAt);pausedAt=0;}
		Point actualPosition=new Point(bot.getX(),bot.getY(),bot.getZ());
		if(observedPosition==null || observedWorld!=bot.getWorldId() || observedInstance!=bot.getInstanceId()) {
			observedPosition=actualPosition;observedWorld=bot.getWorldId();observedInstance=bot.getInstanceId();physicalProgressAt=0;
		} else if(PositionUtil.getDistance(observedPosition.x(),observedPosition.y(),observedPosition.z(),actualPosition.x(),actualPosition.y(),actualPosition.z())>.25) {
			physicalProgressAt=now;observedPosition=actualPosition;
		}
		if(routeWorld!=bot.getWorldId() || routeInstance!=bot.getInstanceId()) {
			meshJourney.close();
			cancelTicket(searchToken);cancelTicket(heightSearchToken);cancelTicket(localSearchToken);cancelTicket(approachSearchToken);localOrigin=null;approachTarget=null;approachDestination=null;clearSearch();clearRoute();partialRoute=lookahead=false;routeGoal=null;publishedGoal=null;publishedDestination=null;
		}
		Point goal = trackingGoal(new Point(targetX,targetY,targetZ),intent,now);
		targetX=goal.x();targetY=goal.y();targetZ=goal.z();
		// An early advisory callback owns no new movement turn. Preserve only the
		// same fresh, registered planning intent; an orphan search is not an action.
		if (now < nextMove && routeIntent==intent && routeGoal!=null
			&& PositionUtil.getDistance(routeGoal.x(),routeGoal.y(),routeGoal.z(),goal.x(),goal.y(),goal.z())<=.25)
			return bot.getMoveController().isInMove() || ownsPendingMove(goal);
		nextMove = now + 200;
		if(bot instanceof Player player)PlayerBotFollowIntent.clear(player);
		if (bot.isFlying()) {
			cancelTicket(searchToken);cancelTicket(heightSearchToken);cancelTicket(localSearchToken);
			clearRoute();clearSearch();partialRoute=lookahead=false;
			boolean moved = PlayerBotFlight.move(bot, targetX, targetY, targetZ, hazards);
			status = moved ? "flying" : "air route blocked";
			return moved;
		}
		double goalDistance = PositionUtil.getDistance(bot, targetX, targetY, targetZ);
		if (goalDistance < 0.15) { stop(); status="at navigation waypoint"; return false; }
		meshJourney.bind(bot.getWorldId(),goal);
		meshJourney.observe(new Point(bot.getX(),bot.getY(),bot.getZ()));
		if(bot instanceof Player player && player.getMoveController() instanceof com.aionemu.gameserver.controllers.movement.PlayerBotMoveController mover){
			var completed=mover.completedPlatform();
			if(completed!=null){
				if(completed.state()==PlayerBotPlatformRide.State.COMPLETE)meshJourney.completeRide(completed.route(),actualPosition);
				else {meshJourney.close();meshJourney.bind(bot.getWorldId(),goal);nextSearch=now+3000;}
				cancelTicket(searchToken);clearSearch();clearRoute();lookahead=partialRoute=false;publishedGoal=publishedDestination=null;lastProgress=now;
			}
			var jump=meshJourney.pendingJump();
			if(jump!=null && meshJourney.jumpLaunched()) {
				// The mover/Session height guard returns until gravity has actually
				// settled. A launched proposal never awards arrival at takeoff time.
				if(!meshJourney.completeJump(actualPosition))meshJourney.rejectJump(actualPosition);
				cancelTicket(searchToken);cancelTicket(heightSearchToken);cancelTicket(localSearchToken);clearSearch();clearRoute();
				lookahead=partialRoute=false;localOrigin=null;publishedGoal=null;lastProgress=now;nextSearch=0;
				jump=meshJourney.pendingJump();
			}
			if(preparedJump!=null && preparedJump!=jump)cancelTicket(heightSearchToken);
			if(jump!=null && PositionUtil.getDistance(player,jump.start().x(),jump.start().y(),jump.start().z())<=.75)
				return prepareJump(player,mover,jump,actualPosition,goalDistance,now);
			var boarding=meshJourney.pendingRide();
			if(queuedPlatform!=null && queuedPlatform!=boarding)leavePlatformQueue(player);
			if(boarding!=null && (queuedPlatform==boarding || PositionUtil.getDistance(player,boarding.from().ground().x(),boarding.from().ground().y(),boarding.from().ground().z())<8)){
				if(blockedPlatform!=null && now<blockedPlatformUntil && blockedPlatformWorld==player.getWorldId() && blockedPlatformInstance==player.getInstanceId()
					&& blockedPlatform.entity().id==boarding.entity().id && blockedPlatform.part()==boarding.part() && blockedPlatform.from().component()==boarding.from().component()){
					transportCongested=true;routeFailure=true;stopMovement();status="lift dock temporarily unavailable";return false;
				}
				if(queuedPlatform==null){
					cancelTicket(searchToken);cancelTicket(heightSearchToken);cancelTicket(localSearchToken);cancelTicket(approachSearchToken);clearSearch();clearRoute();localOrigin=null;lookahead=partialRoute=false;stopMovement();
					queuedPlatform=boarding;platformQueueFailures=0;nextPlatformBoardProbe=0;
				}
				return platformQueue(player,mover,boarding,now,goalDistance);
			}
		}
		// MovementActions::IsDuplicateMove / IsWaitingForLastMove. The native headless
		// controller keeps validating floor/body/door clearance on every movement step.
		if(publishedDestination!=null && !PlayerBotHazards.safePath(new Point(bot.getX(),bot.getY(),bot.getZ()),publishedDestination,hazards))stopMovement();
		if (goalDistance < closestGoalDistance - 0.5) { closestGoalDistance=goalDistance;lastProgress=now; }
		if (routeIntent==intent && refreshWalk() && retainingPublishedMove(goal,now) && !lookahead && !partialRoute) {
			cancelTicket(searchToken);cancelTicket(localSearchToken);cancelTicket(heightSearchToken);localOrigin=null;return true;
		}
		// A retained terrain query belongs to its actual destination, including Z.
		// A nearby successor must not keep searching the predecessor's floor.
		if (routeGoal == null || routeIntent!=intent || routeWorld != bot.getWorldId() || routeInstance != bot.getInstanceId()
			|| PositionUtil.getDistance(routeGoal.x(), routeGoal.y(), routeGoal.z(), targetX, targetY, targetZ) > .25
			|| routeSearch!=null && !routeSearch.targets(goal)) {
			cancelTicket(searchToken);cancelTicket(heightSearchToken);cancelTicket(localSearchToken);localOrigin=null;
			clearRoute();clearSearch();partialRoute=lookahead=groundRouteUnavailable=false;progressWaypoint=null; routeIntent=intent; routeGoal = goal; routeWorld = bot.getWorldId(); routeInstance = bot.getInstanceId();
			closestGoalDistance = goalDistance; lastProgress = now; nextSearch = 0;
		}
		if (goalDistance < closestGoalDistance - 0.5) { closestGoalDistance = goalDistance; lastProgress = now; }
		while (!walkRouteOwned && !route.isEmpty() && PositionUtil.getDistance(bot,route.peekFirst().x(),route.peekFirst().y(),route.peekFirst().z())<.01){route.removeFirst();routeHeadIndex++;}
		if(!route.isEmpty() && !PlayerBotHazards.safePath(new Point(bot.getX(),bot.getY(),bot.getZ()),route.peekFirst(),hazards)) {
			cancelTicket(localSearchToken);localOrigin=null;discardLookahead();clearRoute();partialRoute=false;
			status="route interrupted by fresh hazard";stopMovement();return false;
		}
		if(!route.isEmpty()) {
			Point waypoint=route.peekFirst();double distanceToWaypoint=PositionUtil.getDistance(bot,waypoint.x(),waypoint.y(),waypoint.z());
			if(!waypoint.equals(progressWaypoint)){progressWaypoint=waypoint;closestWaypointDistance=distanceToWaypoint;lastProgress=now;}
			if(distanceToWaypoint<closestWaypointDistance-.2){closestWaypointDistance=distanceToWaypoint;lastProgress=now;}
			if(now-lastProgress>4000){clearRoute();progressWaypoint=null;partialRoute=false;if(lookahead){cancelTicket(searchToken);clearSearch();lookahead=false;}lastProgress=now-1800;}
		}
		// Plan from a proved endpoint while the mover earns the remaining edges.
		// Retain the final goal; this does not report NPC arrival or quest progress.
		prefetchAhead(goal,now);
		if (routeSearch!=null && routeSearch.pending()) {
			// Ground planning supersedes the speculative height search. Its old
			// ticket must not sit ahead of a branch that can no longer consume it.
			cancelTicket(heightSearchToken);if(route.isEmpty()){cancelTicket(localSearchToken);localOrigin=null;}
			// Expiry yields the current edge cursor, rather than losing the search.
			// Charge measured work to the shared budget, preserving fair background access.
			try(var lease=TERRAIN.begin(searchToken,terrainPriority(goalDistance))) {
			 if(lease!=null) {
				long deadline = lease.deadlineNanos();
				int previousProbes=routeSearch.probed();
				routeSearch.advance((from, x, y) -> {
					Vector3f result = GeoService.getInstance().findGroundMovementCollision(bot.getWorldId(), bot.getInstanceId(), from.x(), from.y(), from.z(), x, y, goal.z());
					Point point = result == null ? null : new Point(result.getX(), result.getY(), result.getZ());
					return point != null && PlayerBotHazards.safePath(from, point, hazards) ? point : null;
				},128, () -> System.nanoTime() >= deadline);
				if(routeSearch.probed()>previousProbes)searchProgress=now;
			 }
			}
			if(routeSearch.pending()) {
				TERRAIN.request(searchToken,terrainPriority(goalDistance));
				if(route.isEmpty()){status="planning supported ground route";stopMovement();return true;}
			} else {
				if(routeSearch.route().isEmpty() && route.isEmpty()) {
					status=routeSearch.reason();clearSearch();lookahead=partialRoute=false;
					// Exhausted walking must yield to the existing bounded native jump
					// adapter instead of returning before that branch on every retry.
					groundRouteUnavailable=true;nextSearch=now+3000;cancelTicket(searchToken);stopMovement();
					cancelTicket(localSearchToken);localOrigin=null;nextHeightSearch=0;
				} else {
					groundRouteUnavailable=false;
					partialRoute=routeSearch.partial();route.addAll(routeSearch.route());status=routeSearch.reason();clearSearch();lookahead=false;
					nextSearch=partialRoute?0:now+3000;lastProgress=now;
				}
			}
		} else cancelTicket(searchToken);
		prefetchAhead(goal,now);
		// The native mover still checks floor/body/doors every200ms. A pending
		// future query cannot stop that earned edge or repeatedly re-prove it in
		// the shared planning quota while other actors are waiting for a route.
		if(refreshWalk() && retainingPublishedMove(goal,now)) {
			cancelTicket(localSearchToken);cancelTicket(heightSearchToken);localOrigin=null;
			status="following local route";return true;
		}
		double walkingHorizon=Math.max(6,bot.getGameStats().getMovementSpeedFloat()*2);
		boolean nearbyDescent=targetZ<bot.getZ()-1.25f && Math.hypot(targetX-bot.getX(),targetY-bot.getY())<=8;
		// MovementActions::SearchForBestPath: normal travel obtains a corridor
		// before walking. Only an entire short direct connection may avoid it;
		// clipped goalward steps are not a substitute for going around obstacles.
		if(route.isEmpty() && intent!=Intent.EVADE && goalDistance>walkingHorizon && !nearbyDescent && !groundRouteUnavailable)
			return beginGroundRoute(actualPosition,goal,goalDistance,now);
		if (!route.isEmpty()) { Point waypoint = route.peekFirst(); targetX = waypoint.x(); targetY = waypoint.y(); targetZ = waypoint.z(); }
		double angle = Math.toDegrees(Math.atan2(targetY - bot.getY(), targetX - bot.getX()));
		float distance = (float) Math.min(Math.max(6,bot.getGameStats().getMovementSpeedFloat()*2), Math.hypot(targetX - bot.getX(), targetY - bot.getY()));
		Point origin=new Point(bot.getX(),bot.getY(),bot.getZ()),segmentGoal=new Point(targetX,targetY,targetZ);
		int[] offsets=route.isEmpty() && intent==Intent.EVADE?new int[]{0,30,-30,60,-60,90,-90}:new int[]{0};
		if(!origin.equals(localOrigin) || !segmentGoal.equals(localGoal) || !hazards.equals(localHazards) || localLimit!=offsets.length) {
			cancelTicket(localSearchToken);localOrigin=origin;localGoal=segmentGoal;localHazards=hazards;localLimit=offsets.length;localCursor=heightCursor=0;heightSearch=null;localBest=null;localScore=Double.NEGATIVE_INFINITY;
		}
		if(localCursor<offsets.length) {
			// Local steering is terrain planning too. Retain the candidate cursor
			// across measured slices instead of bypassing the route work quota.
			cancelTicket(heightSearchToken);
			try(var lease=TERRAIN.begin(localSearchToken,terrainPriority(goalDistance))) {
			 if(lease==null){status="waiting to plan supported step";return true;}
			 while(localCursor<offsets.length && System.nanoTime()<lease.deadlineNanos()) {
				int offset=offsets[localCursor++];
				double radians = Math.toRadians(angle + offset);
				float x = bot.getX() + (float) Math.cos(radians) * distance;
				float y = bot.getY() + (float) Math.sin(radians) * distance;
				Vector3f collision = GeoService.getInstance().findGroundMovementCollision(bot.getWorldId(), bot.getInstanceId(), bot.getX(), bot.getY(), bot.getZ(), x, y, targetZ);
				if(collision==null)continue;
				float wantedX=x,wantedY=y;x=collision.x;y=collision.y;float z=collision.z;
				if(!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)
					|| Math.hypot(x-bot.getX(),y-bot.getY())<.15 && z>=bot.getZ()-1.25f)continue;
				if(intent!=Intent.EVADE && (Math.hypot(x-wantedX,y-wantedY)>.15
					|| intent!=Intent.FOLLOW && Math.hypot(wantedX-targetX,wantedY-targetY)<.15 && Math.abs(z-targetZ)>.75))continue;
				Point candidate=new Point(x,y,z);
				if(!PlayerBotHazards.safePath(origin,candidate,hazards))continue;
				double score=-PositionUtil.getDistance(x,y,z,targetX,targetY,targetZ)-Math.abs(offset)*.015;
				if(score>localScore){localScore=score;localBest=candidate;}
				if(offset==0 && Math.hypot(x-wantedX,y-wantedY)<.1){localCursor=offsets.length;break;}
			 }
			}
			if(localCursor<offsets.length){TERRAIN.request(localSearchToken,terrainPriority(goalDistance));status="planning supported step";return true;}
			cancelTicket(localSearchToken);
		}
		Point best=localBest;
		// A blocked approach on the same/higher floor needs a route around the
		// structure first. Exhaustive jump candidates cannot climb a whole storey
		// and previously delayed the only useful walking search for minutes.
		if(best==null && (intent==Intent.FOLLOW_TRAIL || intent==Intent.FOLLOW && controlledFollow()) && !groundRouteUnavailable && targetZ>=bot.getZ()-.75f)
			return beginGroundRoute(origin,goal,goalDistance,now);
		if(best==null && !route.isEmpty()) {
			// Replan a freshly obstructed corridor instead of pressing against its
			// corner until the generic four-second movement timeout expires.
			discardLookahead();clearRoute();partialRoute=false;progressWaypoint=null;nextSearch=0;
		}
		double localGoalDistance=PositionUtil.getDistance(origin.x(),origin.y(),origin.z(),targetX,targetY,targetZ);
		boolean improvingStep=best!=null && PositionUtil.getDistance(best.x(),best.y(),best.z(),targetX,targetY,targetZ)
			<localGoalDistance-.4;
		// Walking retains its strict slope/step rules. A separate supported ballistic arc
		// handles nearby height changes/gaps without making cliffs into walkable edges.
		if(bot instanceof Player player && player.getMoveController() instanceof com.aionemu.gameserver.controllers.movement.PlayerBotMoveController mover
			&& route.isEmpty() && now>=nextHeightSearch && !improvingStep) {
			routeWorld=bot.getWorldId();routeInstance=bot.getInstanceId();
			try(var lease=TERRAIN.begin(heightSearchToken,terrainPriority(goalDistance))) {
			 if(lease==null){status="waiting to plan supported height traversal";return true;}
			 {
				long deadline=lease.deadlineNanos();
				float speed=com.aionemu.gameserver.utils.stats.StatFunctions.adjustStatByMovementModifier(bot,
					com.aionemu.gameserver.model.stats.container.StatEnum.SPEED,bot.getGameStats().getMovementSpeedFloat());
				boolean descending=targetZ<bot.getZ()-.3f;
				int candidates=(descending?HEIGHT_DIRECTIONS.length:5)*HEIGHT_REACHES.length;
				while(heightCursor<candidates && System.nanoTime()<deadline) {
					int offset=HEIGHT_DIRECTIONS[heightCursor/HEIGHT_REACHES.length];float reach=HEIGHT_REACHES[heightCursor%HEIGHT_REACHES.length];
					// A destination almost directly below an edge still needs enough
					// horizontal clearance for the body to leave its supporting ledge.
					// The old half-metre clamp repeated the same obstructed endpoint
					// for every reach, and its forward fan could never go around a lip.
					double radians=Math.toRadians(angle+offset);float span=descending?reach:(float)Math.min(reach,Math.max(.5,Math.hypot(targetX-bot.getX(),targetY-bot.getY())));
					float x=bot.getX()+(float)Math.cos(radians)*span,y=bot.getY()+(float)Math.sin(radians)*span;
					if(heightSearch==null)heightSearch=GeoService.getInstance().searchPlayerBotHeightTraversal(bot.getWorldId(),bot.getInstanceId(),bot.getX(),bot.getY(),bot.getZ(),x,y,targetZ,speed);
					var plan=heightSearch.advance(deadline);
					if(!heightSearch.done())break;
					heightSearch=null;heightCursor++;
					if(plan==null || PositionUtil.getDistance(x,y,plan.endZ(),targetX,targetY,targetZ)>=localGoalDistance-.4
						&& !(descending && plan.endZ()<bot.getZ()-1.25f && Math.abs(plan.endZ()-targetZ)<Math.abs(bot.getZ()-targetZ)-1.25f))continue;
					boolean safe=true;Point previous=new Point(plan.x(),plan.y(),plan.z());
					for(int sample=1;sample<=32;sample++){var p=plan.at(plan.seconds()*sample/32);Point next=new Point(p.x,p.y,p.z);
						if(!PlayerBotHazards.safePath(previous,next,hazards)){safe=false;break;}previous=next;}
					if(safe && mover.startHeightTraversal(plan)){cancelTicket(heightSearchToken);cancelTicket(localSearchToken);cancelTicket(approachSearchToken);localOrigin=null;nextHeightSearch=now+500;groundRouteUnavailable=false;routeFailure=false;discardLookahead();clearRoute();partialRoute=false;publishedGoal=null;status="jumping to verified landing";return true;}
				}
			 }
			}
			if(heightCursor<(targetZ<bot.getZ()-.3f?HEIGHT_DIRECTIONS.length:5)*HEIGHT_REACHES.length){TERRAIN.request(heightSearchToken,terrainPriority(goalDistance));status="planning supported height traversal";return true;}
			nextHeightSearch=now+500;
		}else cancelTicket(heightSearchToken);
		// Short direct movement/descent failed: retain a real route search instead
		// of repeatedly steering toward the same blocked world destination.
		if(route.isEmpty() && !improvingStep && goalDistance>.6 && now>=nextSearch) {
			return beginGroundRoute(origin,goal,goalDistance,now);
		}
		if (best == null) { if(TERRAIN.waiting(heightSearchToken))return true;routeFailure=groundRouteUnavailable;localOrigin=null;discardLookahead();clearRoute();partialRoute=false; status = "blocked by geometry"; stopMovement(); return false; }
		status = route.isEmpty() ? "moving" : "following local route";
		byte heading = Math.hypot(best.x() - bot.getX(), best.y() - bot.getY()) < 0.001 ? bot.getHeading()
			: (byte) Math.floorMod((int) Math.round(Math.toDegrees(Math.atan2(best.y() - bot.getY(), best.x() - bot.getX())) / 3), 120);
		synchronized(bot.getMoveController()) {
			if(bot instanceof Player player)PlayerBotFollowIntent.clear(player);
			if(bot.getMoveController() instanceof com.aionemu.gameserver.controllers.movement.PlayerBotMoveController mover) {
				// New publication follows current native proof. Same-generation tail
				// refresh above never resets this target or spends another local probe.
				walkGeneration++;routeHeadIndex=0;
				followTrailTail=null;
				if (intent==Intent.FOLLOW_TRAIL && route.isEmpty() && !trail.isEmpty() && trail.peekFirst().equals(goal)
					&& Math.hypot(best.x()-goal.x(),best.y()-goal.y())<.15 && Math.abs(best.z()-goal.z())<=.75) {
					route.addLast(best);
					followTrailTail=PlayerBotNavigationTrail.append(route,trail,goal,Math.max(6,bot.getGameStats().getMovementSpeedFloat()*2));
				}
				walkRouteOwned=!route.isEmpty() && Math.hypot(best.x()-route.peekFirst().x(),best.y()-route.peekFirst().y())<.15
					&& Math.abs(best.z()-route.peekFirst().z())<=.75;
				if(walkRouteOwned){route.removeFirst();route.addFirst(best);}
				var points=walkRouteOwned?walkPoints():java.util.List.of(new com.aionemu.gameserver.controllers.movement.PlayerBotMoveController.WalkPoint(0,best.x(),best.y(),best.z()));
				var safety=hazards;
				if(!mover.publishWalk(walkGeneration,points,(from,to)->PlayerBotHazards.safePath(new Point(from.x(),from.y(),from.z()),new Point(to.x(),to.y(),to.z()),safety)))return false;
			}else {
				bot.getMoveController().setNewDirection(best.x(),best.y(),best.z(),heading);
				bot.getMoveController().startMovingToDestination();
			}
		}
		cancelTicket(heightSearchToken);localOrigin=null;publishedGoal=goal;publishedDestination=best;publishedWorld=bot.getWorldId();publishedInstance=bot.getInstanceId();
		return true;
	}

	void stop() { if(bot instanceof Player player)leavePlatformQueue(player);transportCongested=false;cancelTicket(searchToken);cancelTicket(heightSearchToken);cancelTicket(localSearchToken);cancelTicket(approachSearchToken);localOrigin=null;approachTarget=null;approachDestination=null;clearRoute();clearSearch();meshJourney.close();partialRoute=lookahead=routeFailure=groundRouteUnavailable=false;routeGoal=null;progressWaypoint=null;pausedAt=0;stopMovement();status="ready"; }
	/** A short rest/equipment/self-cast pause retains the same journey, not a new destination. */
	void pause() {
		if(pausedAt==0)pausedAt=System.currentTimeMillis();
		cancelTicket(searchToken);cancelTicket(heightSearchToken);cancelTicket(localSearchToken);cancelTicket(approachSearchToken);
		localOrigin=null;approachTarget=null;approachDestination=null;
		// Do not reserve shared query capacity during an arbitrarily long recovery.
		if(routeSearch!=null && routeSearch.prepared()){clearSearch();lookahead=false;}
		meshJourney.pause();
		stopMovement();status="movement paused for native action";
	}
	private void discardLookahead() { if(lookahead){cancelTicket(searchToken);clearSearch();lookahead=false;} }
	private boolean prepareJump(Player player,com.aionemu.gameserver.controllers.movement.PlayerBotMoveController mover,
		PlayerBotRoadNetwork.Jump jump,Point origin,double goalDistance,long now) {
		// A funded ballistic search owns a stable takeoff; do not leave a previous
		// corridor walking while its body/landing proof is sliced across callbacks.
		stopMovement();cancelTicket(searchToken);cancelTicket(localSearchToken);cancelTicket(approachSearchToken);
		clearSearch();clearRoute();lookahead=partialRoute=false;localOrigin=null;
		if(!player.canPerformMove() || player.isCasting() || player.isTrading() || player.isLooting()) {
			cancelTicket(heightSearchToken);status="waiting for native jump admission";return true;
		}
		float speed=com.aionemu.gameserver.utils.stats.StatFunctions.adjustStatByMovementModifier(player,
			com.aionemu.gameserver.model.stats.container.StatEnum.SPEED,player.getGameStats().getMovementSpeedFloat());
		if(preparedJump!=jump || preparedJumpOrigin==null
			|| PositionUtil.getDistance(origin.x(),origin.y(),origin.z(),preparedJumpOrigin.x(),preparedJumpOrigin.y(),preparedJumpOrigin.z())>.05
			|| Math.abs(speed-preparedJumpSpeed)>.01) {
			cancelTicket(heightSearchToken);preparedJump=jump;preparedJumpOrigin=origin;preparedJumpSpeed=speed;
			preparedJumpSearch=GeoService.getInstance().searchPlayerBotHeightTraversal(player.getWorldId(),player.getInstanceId(),
				origin.x(),origin.y(),origin.z(),jump.end().x(),jump.end().y(),jump.end().z(),speed);
		}
		boolean pending=false;
		try(var lease=TERRAIN.begin(heightSearchToken,terrainPriority(goalDistance))) {
			if(lease==null){status="waiting to prove prepared jump or drop";return true;}
			var plan=preparedJumpSearch.advance(lease.deadlineNanos());
			if(!preparedJumpSearch.done()) {
				pending=true;status="proving prepared jump or drop";return true;
			}
			boolean safe=plan!=null && plan.valid()
				&& PositionUtil.getDistance(plan.endX(),plan.endY(),plan.endZ(),jump.end().x(),jump.end().y(),jump.end().z())<=.4;
			if(safe) {
				Point previous=new Point(plan.x(),plan.y(),plan.z());
				for(int sample=1;sample<=32;sample++) {
					var at=plan.at(plan.seconds()*sample/32);Point next=new Point(at.x,at.y,at.z);
					if(!PlayerBotHazards.safePath(previous,next,hazards)){safe=false;break;}previous=next;
				}
			}
			if(safe) {
				// Transient admission loss is not evidence that this geometric arc
				// is obstructed. The native mover repeats landing and speed checks.
				if(!player.canPerformMove() || player.isCasting() || player.isTrading() || player.isLooting())return true;
				if(mover.startHeightTraversal(plan)) {
					meshJourney.markJumpLaunched();cancelTicket(heightSearchToken);nextHeightSearch=now+500;
					status="traversing native-proved prepared jump or drop";return true;
				}
			}
			meshJourney.rejectJump(origin);cancelTicket(heightSearchToken);routeFailure=false;groundRouteUnavailable=false;nextSearch=0;
			status="prepared jump obstructed; replanning directed route";return true;
		}finally{if(pending)TERRAIN.request(heightSearchToken,terrainPriority(goalDistance));}
	}
	private void leavePlatformQueue(Player player){
		PlayerBotPlatformQueue.leave(player);queuedPlatform=null;platformQueuePoint=null;platformQueueRank=-2;
		platformQueueDistance=Double.POSITIVE_INFINITY;platformQueueProgressAt=0;platformQueueFailures=0;cancelTicket(localSearchToken);
	}
	private boolean blockedPlatformQueue(Player player,PlayerBotAnimatedLinks.Ride ride,long now){
		blockedPlatform=ride;blockedPlatformWorld=player.getWorldId();blockedPlatformInstance=player.getInstanceId();blockedPlatformUntil=now+45000;
		leavePlatformQueue(player);transportCongested=true;routeFailure=true;stopMovement();status="lift approach obstructed; released queue position";return false;
	}
	private boolean platformQueue(Player player,com.aionemu.gameserver.controllers.movement.PlayerBotMoveController mover,PlayerBotAnimatedLinks.Ride ride,long now,double goalDistance){
		int rank=PlayerBotPlatformQueue.enter(player,ride,now);transportCongested=rank<0;
		if(rank<0)return blockedPlatformQueue(player,ride,now);
		boolean embark=rank==0 && PlayerBotPlatformRide.docking(ride) && PlayerBotPlatformRide.available(player,ride);
		// The front passenger waits at the static shore, even while the cabin is
		// away. A short docking window must not reverse that approach every cycle.
		int place=rank;var shore=ride.from().ground();
		if(place!=platformQueueRank){PlayerBotPlatformQueue.releaseSlot(player,ride);platformQueueRank=place;platformQueuePoint=null;nextPlatformQueueProbe=0;platformQueueProgressAt=0;platformQueueDistance=Double.POSITIVE_INFINITY;}
		// Waiting for a docking phase must not repeatedly discard an unfunded
		// shore proof. Its queued turn belongs to this unchanged lift approach.
		// A boarding-only request, however, is no longer useful once it departs.
		if(platformQueuePoint!=null && !embark)cancelTicket(localSearchToken);
		if(platformQueuePoint==null){
			if(now<nextPlatformQueueProbe)return true;
			var lease=TERRAIN.begin(localSearchToken,terrainPriority(goalDistance));
			if(lease==null)return true;
			try(lease){
				nextPlatformQueueProbe=now+1000;
				boolean occupied=false;
				var frame=ride.entity().at(ride.from().time(),0);double[] deck=frame.point(ride.part(),ride.local());
				double dx=shore.x()-deck[0],dy=shore.y()-deck[1],length=Math.hypot(dx,dy);
				if(length<.01){dx=1;dy=0;length=1;}dx/=length;dy/=length;
				for(int attempt=0;attempt<(rank==0?1:6);attempt++){
					double back=rank==0?0:1.2+(rank/3)*1.2+attempt*.35,side=rank==0?0:(rank%3-1)*1.2;
					float x=(float)(shore.x()+dx*back-dy*side),y=(float)(shore.y()+dy*back+dx*side);
					var point=GeoService.getInstance().findGroundMovementCollision(player.getWorldId(),player.getInstanceId(),player.getX(),player.getY(),player.getZ(),x,y,shore.z());
					if(point!=null && Math.hypot(point.x-x,point.y-y)<.3 && Math.abs(point.z-shore.z())<.5
						&& PlayerBotHazards.safePath(new Point(player.getX(),player.getY(),player.getZ()),new Point(point.x,point.y,point.z),hazards)){
						var candidate=new Point(point.x,point.y,point.z);
						if(PlayerBotPlatformQueue.reserve(player,ride,candidate)){platformQueuePoint=candidate;break;}
						occupied=true;
					}
				}
				// Only a funded geometry failure counts; waiting for compute or an
				// authored lift cycle cannot evict a healthy passenger from the queue.
				if(platformQueuePoint==null && !occupied && ++platformQueueFailures>=3)return blockedPlatformQueue(player,ride,now);
			}finally{cancelTicket(localSearchToken);}
		}
		if(platformQueuePoint==null){stopMovement();status="waiting for safe lift queue space";return true;}
		double distance=PositionUtil.getDistance(player,platformQueuePoint.x(),platformQueuePoint.y(),platformQueuePoint.z());
		if(distance>.25){
			if(distance<platformQueueDistance-.2 || platformQueueProgressAt==0){platformQueueDistance=distance;platformQueueProgressAt=now;}
			else if(now-platformQueueProgressAt>10000){
				PlayerBotPlatformQueue.releaseSlot(player,ride);platformQueuePoint=null;platformQueueProgressAt=0;stopMovement();
				if(++platformQueueFailures>=3)return blockedPlatformQueue(player,ride,now);
				status="rechecking obstructed lift approach";return true;
			}
			if(!mover.isInMove() || PositionUtil.getDistance(mover.getTargetX2(),mover.getTargetY2(),mover.getTargetZ2(),platformQueuePoint.x(),platformQueuePoint.y(),platformQueuePoint.z())>.1){
				mover.setNewDirection(platformQueuePoint.x(),platformQueuePoint.y(),platformQueuePoint.z(),player.getHeading());mover.startMovingToDestination();
			}
			status="walking to lift queue position";return true;
		}
		stopMovement();status="waiting for moving platform in spaced queue";
		platformQueueProgressAt=0;
		if(embark && now>=nextPlatformBoardProbe){
			var lease=TERRAIN.begin(localSearchToken,terrainPriority(goalDistance));
			if(lease==null)return true;
			try(lease){
				nextPlatformBoardProbe=now+1000;
				if(mover.startPlatform(PlayerBotPlatformRide.plan(player,ride,hazards))){leavePlatformQueue(player);status="riding authored moving platform";}
				else if(++platformQueueFailures>=3)return blockedPlatformQueue(player,ride,now);
			}finally{cancelTicket(localSearchToken);}
		}
		return true;
	}
	private void stopMovement() { publishedGoal=null;publishedDestination=null;if (bot.getMoveController().isInMove()) bot.getMoveController().abortMove(); }
	String status() { return TERRAIN.waiting(searchToken) || TERRAIN.waiting(heightSearchToken) || TERRAIN.waiting(localSearchToken) || TERRAIN.waiting(approachSearchToken)?"waiting for terrain work budget":status; }
}
