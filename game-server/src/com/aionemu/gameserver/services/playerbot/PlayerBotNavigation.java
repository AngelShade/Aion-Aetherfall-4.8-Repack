package com.aionemu.gameserver.services.playerbot;

import java.util.ArrayDeque;
import java.util.Deque;

import com.aionemu.gameserver.geoEngine.math.Vector3f;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.utils.PositionUtil;
import com.aionemu.gameserver.world.geo.GeoService;
import com.aionemu.gameserver.configs.main.PlayerBotConfig;

/** Owner breadcrumbs plus short, collision-tested steps; never teleports to escape combat obstacles. */
final class PlayerBotNavigation {
	record Point(float x, float y, float z) {}
	private final Creature bot;
	private final Deque<Point> trail = new ArrayDeque<>();
	private Point lastOwner;
	private final Deque<Point> route = new ArrayDeque<>();
	private Point routeGoal;
	private volatile PlayerBotRouteSearch routeSearch;
	private Point progressWaypoint;
	private double closestWaypointDistance;
	private volatile long searchProgress;
	private double closestGoalDistance;
	private long lastProgress, nextSearch;
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
	private Creature approachTarget;
	private float approachRange;
	private long approachSelected;
	private long nextMove;
	private long nextHeightSearch;
	private Point publishedGoal,publishedDestination;
	private int publishedWorld,publishedInstance;
	private int worldId, instanceId;
	private volatile String status = "ready";
	private boolean routeFailure;
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
	void hazards(java.util.List<PlayerBotHazards.Area> areas) { hazards = java.util.List.copyOf(areas); }
	boolean planningRoute(){return routeSearch!=null && routeSearch.pending();}
	boolean waitingForTerrainWork(){return TERRAIN.waiting(searchToken) || TERRAIN.waiting(heightSearchToken)
		|| TERRAIN.waiting(localSearchToken) || TERRAIN.waiting(approachSearchToken);}
	boolean routeProgressing(long now){return planningRoute() && (TERRAIN.waiting(searchToken) || now-searchProgress<30000);}
	private void cancelTicket(Object token){SEARCHES.cancel(token);TERRAIN.cancel(token);}
	private PlayerBotNavigationBudget.Priority terrainPriority(double distance) {
		if(bot instanceof Player player && (!player.isWorldBot() || player.getPlayerBotOwnerId()!=0))return PlayerBotNavigationBudget.Priority.CONTROLLED;
		return distance<=30 ? PlayerBotNavigationBudget.Priority.NEAR : PlayerBotNavigationBudget.Priority.WORLD;
	}
	private void planRoute(Point start,Point goal,boolean ahead,long now) {
		cancelTicket(heightSearchToken);
		routeSearch=new PlayerBotRouteSearch(start,goal,bot.getWorldMapInstance().getParent().getWorldSize(),4096,PlayerBotConfig.TERRAIN_SEGMENT_METRES);
		lookahead=ahead;searchProgress=now;
		if(routeSearch.pending())TERRAIN.request(searchToken,terrainPriority(PositionUtil.getDistance(bot,goal.x(),goal.y(),goal.z())));
	}
	boolean progressing(long now){return routeProgressing(now) || !route.isEmpty() && now-lastProgress<4000;}
	private boolean retainingPublishedMove(Point goal,long now) {
		return publishedGoal!=null && publishedDestination!=null && bot.getMoveController().isInMove()
			&& publishedWorld==bot.getWorldId() && publishedInstance==bot.getInstanceId()
			&& PositionUtil.getDistance(publishedGoal.x(),publishedGoal.y(),publishedGoal.z(),goal.x(),goal.y(),goal.z())<.25
			&& PositionUtil.getDistance(bot,publishedDestination.x(),publishedDestination.y(),publishedDestination.z())>.75
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
		if(routeSearch!=null && routeSearch.pending() && routeSearch.targets(goal) && TERRAIN.waiting(searchToken))return true;
		if(localOrigin==null || localGoal==null || !localOrigin.equals(new Point(bot.getX(),bot.getY(),bot.getZ()))
			|| !hazards.equals(localHazards))return false;
		return localCursor<localLimit && TERRAIN.waiting(localSearchToken) || heightCursor<25 && TERRAIN.waiting(heightSearchToken);
	}
	boolean escapeHazards() {
		Point destination = PlayerBotHazards.escape(new Point(bot.getX(), bot.getY(), bot.getZ()), hazards, bot.getHeading());
		return destination != null && move(destination.x(), destination.y(), destination.z());
	}

	void record(Player owner) {
		if (owner.isFlying() || bot.isFlying()) { trail.clear(); lastOwner = null; return; }
		if (worldId != owner.getWorldId() || instanceId != owner.getInstanceId()) {
			trail.clear();
			lastOwner = null;
			worldId = owner.getWorldId();
			instanceId = owner.getInstanceId();
		}
		if (lastOwner == null || PositionUtil.getDistance(owner, lastOwner.x(), lastOwner.y(), lastOwner.z()) > 2) {
			lastOwner = new Point(owner.getX(), owner.getY(), owner.getZ());
			trail.addLast(lastOwner);
			while (trail.size() > 128) trail.removeFirst();
		}
	}

	boolean follow(Player owner, int formationSlot) {
		PlayerBotFormation.following(owner, (Player)bot, formationSlot);
		Point formation = PlayerBotFormation.destination(owner, (Player)bot, formationSlot);
		if (bot.isFlying()) {
			// Airborne follow uses the leader's altitude, never a ground breadcrumb.
			if (!PlayerBotFormation.needsFollow(owner,(Player)bot,formationSlot)) { stop(); return false; }
			boolean moved=move(formation.x(),formation.y(),formation.z());
			if(moved)PlayerBotFollowIntent.bind(owner,(Player)bot,formationSlot,formation,hazards);
			return moved;
		}
		PlayerBotNavigationTrail.consume(trail, new Point(bot.getX(),bot.getY(),bot.getZ()));
		if (!PlayerBotFormation.needsFollow(owner,(Player)bot,formationSlot)) {
			stop();
			return false;
		}
		// Reached breadcrumbs have been consumed across the whole prefix, not only
		// at its head. An old unvisited formation point cannot pin the bot to its
		// current position while an opened doorway occludes newer owner samples.
		Point destination = trail.peekFirst();
		for (Point point : trail)
			if (PositionUtil.getDistance(bot,point.x(),point.y(),point.z())>=3 && GeoService.getInstance().canSee(bot, point.x(), point.y(), point.z(),
				com.aionemu.gameserver.geoEngine.collision.IgnoreProperties.ANY_RACE)) destination = point;
		if (GeoService.getInstance().canSee(bot, formation.x(),formation.y(),formation.z(),com.aionemu.gameserver.geoEngine.collision.IgnoreProperties.ANY_RACE)) destination=formation;
		if (destination == null) destination = new Point(owner.getX(), owner.getY(), owner.getZ());
		boolean moved=move(destination.x(), destination.y(), destination.z());
		if(moved)PlayerBotFollowIntent.bind(owner,(Player)bot,formationSlot,formation,hazards);
		return moved;
	}

	boolean approach(Creature target, float range) {
		if(bot.getMoveController() instanceof com.aionemu.gameserver.controllers.movement.PlayerBotMoveController mover && mover.traversingHeight())return true;
		if(bot.isCasting() || !bot.canPerformMove() || bot.isDead()){cancelTicket(approachSearchToken);return false;}
		if(routeWorld!=bot.getWorldId() || routeInstance!=bot.getInstanceId()){stop();routeWorld=bot.getWorldId();routeInstance=bot.getInstanceId();}
		boolean visible=GeoService.getInstance().canSee(bot,target);
		if (PositionUtil.isInRange(bot,target,range,false) && visible) {
			stop();
			return false;
		}
		var point = PlayerBotCombatPosition.point(bot,target,range);
		if (!visible) {
			if (range<8) return move(target.getX(),target.getY(),target.getZ());
			Point targetPoint=new Point(target.getX(),target.getY(),target.getZ());
			long now=System.currentTimeMillis();
			if(approachTarget!=target || approachRange!=range || approachTargetPoint==null
				|| PositionUtil.getDistance(approachTargetPoint.x(),approachTargetPoint.y(),approachTargetPoint.z(),targetPoint.x(),targetPoint.y(),targetPoint.z())>.5
				|| approachDestination!=null && now-approachSelected>=4000 && !progressing(now) && !bot.getMoveController().isInMove()) {
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
				if (ground!=null && Float.isFinite(ground.z) && Math.hypot(ground.x-x,ground.y-y)<.3
					&& GeoService.getInstance().canSee(target,ground.x,ground.y,ground.z,com.aionemu.gameserver.geoEngine.collision.IgnoreProperties.ANY_RACE)) {approachDestination=new Point(ground.x,ground.y,ground.z);break;}
			 }

			}
			if(approachDestination==null && approachCursor<(bot.isFlying()?5:20)){TERRAIN.request(approachSearchToken,terrainPriority(PositionUtil.getDistance(bot,target)));status="planning firing position";return true;}
			cancelTicket(approachSearchToken);if(approachDestination==null)approachDestination=point;approachSelected=now;
			return move(approachDestination.x(),approachDestination.y(),approachDestination.z(),true);
		}
		cancelTicket(approachSearchToken);approachTarget=null;approachDestination=null;
		return move(point.x(),point.y(),point.z());
	}

	boolean retreat(Creature target) {
		float dx = bot.getX() - target.getX(), dy = bot.getY() - target.getY();
		double distance = Math.max(0.01, Math.hypot(dx, dy));
		return move(bot.getX() + (float) (dx / distance * 5), bot.getY() + (float) (dy / distance * 5), bot.getZ());
	}

	boolean escape(PlayerBotTactics.Hazard hazard) {
		Point point = PlayerBotTactics.escape(bot.getX(), bot.getY(), bot.getZ(), hazard, bot.getHeading());
		return move(point.x(), point.y(), point.z());
	}

	boolean move(float targetX, float targetY, float targetZ) {
		return move(targetX,targetY,targetZ,false);
	}
	private boolean move(float targetX,float targetY,float targetZ,boolean firingIntent) {
		routeFailure=false;
		if(!Float.isFinite(targetX) || !Float.isFinite(targetY) || !Float.isFinite(targetZ)){stop();return false;}
		// Safety/follow/new orders replace a pending firing branch. Its ticket
		// must not own a queue turn that this successor can never consume.
		if(!firingIntent){cancelTicket(approachSearchToken);approachTarget=null;approachDestination=null;}
		if(bot.getMoveController() instanceof com.aionemu.gameserver.controllers.movement.PlayerBotMoveController mover && mover.traversingHeight())return true;
		if (bot.isCasting() || !bot.canPerformMove() || bot.isDead()) { cancelTicket(searchToken);cancelTicket(heightSearchToken);cancelTicket(localSearchToken);cancelTicket(approachSearchToken); return false; }
		long now = System.currentTimeMillis();
		if(routeWorld!=bot.getWorldId() || routeInstance!=bot.getInstanceId()) {
			cancelTicket(searchToken);cancelTicket(heightSearchToken);cancelTicket(localSearchToken);cancelTicket(approachSearchToken);localOrigin=null;approachTarget=null;approachDestination=null;routeSearch=null;route.clear();partialRoute=lookahead=false;routeGoal=null;publishedGoal=null;publishedDestination=null;
		}
		Point goal = new Point(targetX, targetY, targetZ);
		// An early advisory callback owns no new movement turn. Preserve only the
		// same fresh, registered planning intent; an orphan search is not an action.
		if (now < nextMove) return bot.getMoveController().isInMove() || ownsPendingMove(goal);
		nextMove = now + 200;
		if(bot instanceof Player player)PlayerBotFollowIntent.clear(player);
		if (bot.isFlying()) {
			cancelTicket(searchToken);cancelTicket(heightSearchToken);cancelTicket(localSearchToken);
			route.clear();routeSearch=null;partialRoute=lookahead=false;
			boolean moved = PlayerBotFlight.move(bot, targetX, targetY, targetZ, hazards);
			status = moved ? "flying" : "air route blocked";
			return moved;
		}
		double goalDistance = PositionUtil.getDistance(bot, targetX, targetY, targetZ);
		if (goalDistance < 0.15) { stop(); status="at navigation waypoint"; return false; }
		// MovementActions::IsDuplicateMove / IsWaitingForLastMove. The native headless
		// controller keeps validating floor/body/door clearance on every movement step.
		if(publishedDestination!=null && !PlayerBotHazards.safePath(new Point(bot.getX(),bot.getY(),bot.getZ()),publishedDestination,hazards))stopMovement();
		if (goalDistance < closestGoalDistance - 0.5) { closestGoalDistance=goalDistance;lastProgress=now; }
		if (retainingPublishedMove(goal,now) && !lookahead && !partialRoute) {
			cancelTicket(searchToken);cancelTicket(localSearchToken);cancelTicket(heightSearchToken);localOrigin=null;return true;
		}
		// A retained terrain query belongs to its actual destination, including Z.
		// A nearby successor must not keep searching the predecessor's floor.
		if (routeGoal == null || routeWorld != bot.getWorldId() || routeInstance != bot.getInstanceId()
			|| PositionUtil.getDistance(routeGoal.x(), routeGoal.y(), routeGoal.z(), targetX, targetY, targetZ) > .25
			|| routeSearch!=null && !routeSearch.targets(goal)) {
			cancelTicket(searchToken);cancelTicket(heightSearchToken);cancelTicket(localSearchToken);localOrigin=null;
			route.clear();routeSearch=null;partialRoute=lookahead=false;progressWaypoint=null; routeGoal = goal; routeWorld = bot.getWorldId(); routeInstance = bot.getInstanceId();
			closestGoalDistance = goalDistance; lastProgress = now; nextSearch = 0;
		}
		if (goalDistance < closestGoalDistance - 0.5) { closestGoalDistance = goalDistance; lastProgress = now; }
		while (!route.isEmpty() && PositionUtil.getDistance(bot, route.peekFirst().x(), route.peekFirst().y(), route.peekFirst().z()) < 0.6) route.removeFirst();
		if(!route.isEmpty() && !PlayerBotHazards.safePath(new Point(bot.getX(),bot.getY(),bot.getZ()),route.peekFirst(),hazards)) {
			cancelTicket(localSearchToken);localOrigin=null;discardLookahead();route.clear();partialRoute=false;
			status="route interrupted by fresh hazard";stopMovement();return false;
		}
		if(!route.isEmpty()) {
			Point waypoint=route.peekFirst();double distanceToWaypoint=PositionUtil.getDistance(bot,waypoint.x(),waypoint.y(),waypoint.z());
			if(!waypoint.equals(progressWaypoint)){progressWaypoint=waypoint;closestWaypointDistance=distanceToWaypoint;lastProgress=now;}
			if(distanceToWaypoint<closestWaypointDistance-.2){closestWaypointDistance=distanceToWaypoint;lastProgress=now;}
			if(now-lastProgress>4000){route.clear();progressWaypoint=null;partialRoute=false;if(lookahead){cancelTicket(searchToken);routeSearch=null;lookahead=false;}lastProgress=now-1800;}
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
				if(route.isEmpty()){status="planning supported ground route";stopMovement();return true;}
			} else {
				if(routeSearch.route().isEmpty() && route.isEmpty()) {
					status=routeSearch.reason();routeSearch=null;lookahead=partialRoute=false;
					nextSearch=now+3000;routeFailure=true;cancelTicket(searchToken);stopMovement();return false;
				}
				partialRoute=routeSearch.partial();route.addAll(routeSearch.route());status=routeSearch.reason();routeSearch=null;lookahead=false;
				nextSearch=partialRoute?0:now+3000;lastProgress=now;
			}
		} else cancelTicket(searchToken);
		prefetchAhead(goal,now);
		// The native mover still checks floor/body/doors every200ms. A pending
		// future query cannot stop that earned edge or repeatedly re-prove it in
		// the shared planning quota while other actors are waiting for a route.
		if(!route.isEmpty() && route.peekFirst().equals(publishedDestination) && retainingPublishedMove(goal,now)) {
			cancelTicket(localSearchToken);cancelTicket(heightSearchToken);localOrigin=null;
			status="following local route";return true;
		}
		if (!route.isEmpty()) { Point waypoint = route.peekFirst(); targetX = waypoint.x(); targetY = waypoint.y(); targetZ = waypoint.z(); }
		double angle = Math.toDegrees(Math.atan2(targetY - bot.getY(), targetX - bot.getX()));
		float distance = (float) Math.min(Math.max(6,bot.getGameStats().getMovementSpeedFloat()*2), Math.hypot(targetX - bot.getX(), targetY - bot.getY()));
		Point origin=new Point(bot.getX(),bot.getY(),bot.getZ()),segmentGoal=new Point(targetX,targetY,targetZ);
		int[] offsets=route.isEmpty()?new int[]{0,30,-30,60,-60,90,-90}:new int[]{0};
		if(!origin.equals(localOrigin) || !segmentGoal.equals(localGoal) || !hazards.equals(localHazards) || localLimit!=offsets.length) {
			cancelTicket(localSearchToken);localOrigin=origin;localGoal=segmentGoal;localHazards=hazards;localLimit=offsets.length;localCursor=heightCursor=0;localBest=null;localScore=Double.NEGATIVE_INFINITY;
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
		double localGoalDistance=PositionUtil.getDistance(origin.x(),origin.y(),origin.z(),targetX,targetY,targetZ);
		boolean improvingStep=best!=null && PositionUtil.getDistance(best.x(),best.y(),best.z(),targetX,targetY,targetZ)
			<localGoalDistance-.4;
		// Walking retains its strict slope/step rules. A separate supported ballistic arc
		// handles nearby height changes/gaps without making cliffs into walkable edges.
		if(bot instanceof Player player && player.getMoveController() instanceof com.aionemu.gameserver.controllers.movement.PlayerBotMoveController mover
			&& now>=nextHeightSearch && !improvingStep) {
			routeWorld=bot.getWorldId();routeInstance=bot.getInstanceId();
			try(var lease=TERRAIN.begin(heightSearchToken,terrainPriority(goalDistance))) {
			 if(lease==null){status="waiting to plan supported height traversal";return true;}
			 {
				long deadline=lease.deadlineNanos();
				float speed=com.aionemu.gameserver.utils.stats.StatFunctions.adjustStatByMovementModifier(bot,
					com.aionemu.gameserver.model.stats.container.StatEnum.SPEED,bot.getGameStats().getMovementSpeedFloat());
				int[] offsetsHeight={0,30,-30,60,-60};float[] reaches={2,3,4,6,8};
				while(heightCursor<25 && System.nanoTime()<deadline) {
					int offset=offsetsHeight[heightCursor/5];float reach=reaches[heightCursor%5];heightCursor++;
					double radians=Math.toRadians(angle+offset);float span=(float)Math.min(reach,Math.max(.5,Math.hypot(targetX-bot.getX(),targetY-bot.getY())));
					float x=bot.getX()+(float)Math.cos(radians)*span,y=bot.getY()+(float)Math.sin(radians)*span;
					var plan=GeoService.getInstance().findPlayerBotHeightTraversal(bot.getWorldId(),bot.getInstanceId(),bot.getX(),bot.getY(),bot.getZ(),x,y,targetZ,speed,deadline);
					if(plan==null || PositionUtil.getDistance(x,y,plan.endZ(),targetX,targetY,targetZ)>=localGoalDistance-.4)continue;
					boolean safe=true;Point previous=new Point(plan.x(),plan.y(),plan.z());
					for(int sample=1;sample<=32;sample++){var p=plan.at(plan.seconds()*sample/32);Point next=new Point(p.x,p.y,p.z);
						if(!PlayerBotHazards.safePath(previous,next,hazards)){safe=false;break;}previous=next;}
					if(safe && mover.startHeightTraversal(plan)){cancelTicket(localSearchToken);cancelTicket(approachSearchToken);localOrigin=null;nextHeightSearch=now+500;discardLookahead();route.clear();partialRoute=false;publishedGoal=null;status="jumping to verified landing";return true;}
				}
			 }
			}
			if(heightCursor<25){TERRAIN.request(heightSearchToken,terrainPriority(goalDistance));status="planning supported height traversal";return true;}
			nextHeightSearch=now+500;
		}else cancelTicket(heightSearchToken);
		// Queue latency is not a failed movement attempt. Publish a proved useful
		// step first; only an unavailable/non-improving step needs route fallback.
		if(route.isEmpty() && !improvingStep && goalDistance>2 && now-lastProgress>=1800 && now>=nextSearch) {
			cancelTicket(heightSearchToken);
			Point start;
			try(var lease=TERRAIN.begin(localSearchToken,terrainPriority(goalDistance))) {
			 if(lease==null){status="waiting to prove route origin";return true;}
			 Vector3f supportedOrigin=GeoService.getInstance().findGroundMovementCollision(bot.getWorldId(),bot.getInstanceId(),bot.getX(),bot.getY(),bot.getZ(),bot.getX(),bot.getY(),goal.z());
			 start=supportedOrigin==null?origin:new Point(supportedOrigin.x,supportedOrigin.y,supportedOrigin.z);
			}
			planRoute(start,goal,false,now);
			if(routeSearch.pending()){
				searchProgress=now;status="planning supported ground route";stopMovement();return true;
			}
			status=routeSearch.reason();routeSearch=null;nextSearch=now+3000;routeFailure=true;stopMovement();return false;
		}
		if (best == null) { if(TERRAIN.waiting(heightSearchToken))return true;localOrigin=null;discardLookahead();route.clear();partialRoute=false; status = "blocked by geometry"; stopMovement(); return false; }
		status = route.isEmpty() ? "moving" : "following local route";
		byte heading = Math.hypot(best.x() - bot.getX(), best.y() - bot.getY()) < 0.001 ? bot.getHeading()
			: (byte) Math.floorMod((int) Math.round(Math.toDegrees(Math.atan2(best.y() - bot.getY(), best.x() - bot.getX())) / 3), 120);
		synchronized(bot.getMoveController()) {
			if(bot instanceof Player player)PlayerBotFollowIntent.clear(player);
			bot.getMoveController().setNewDirection(best.x(), best.y(), best.z(), heading);
			bot.getMoveController().startMovingToDestination();
		}
		cancelTicket(heightSearchToken);localOrigin=null;publishedGoal=goal;publishedDestination=best;publishedWorld=bot.getWorldId();publishedInstance=bot.getInstanceId();
		return true;
	}

	void stop() { cancelTicket(searchToken);cancelTicket(heightSearchToken);cancelTicket(localSearchToken);cancelTicket(approachSearchToken);localOrigin=null;approachTarget=null;approachDestination=null;route.clear();routeSearch=null;partialRoute=lookahead=routeFailure=false;routeGoal=null;progressWaypoint=null;stopMovement();status="ready"; }
	private void discardLookahead() { if(lookahead){cancelTicket(searchToken);routeSearch=null;lookahead=false;} }
	private void stopMovement() { publishedGoal=null;publishedDestination=null;if (bot.getMoveController().isInMove()) bot.getMoveController().abortMove(); }
	String status() { return TERRAIN.waiting(searchToken) || TERRAIN.waiting(heightSearchToken) || TERRAIN.waiting(localSearchToken) || TERRAIN.waiting(approachSearchToken)?"waiting for terrain work budget":status; }
}
