package com.aionemu.gameserver.controllers.movement;

import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.taskmanager.tasks.PlayerMoveTaskManager;
import com.aionemu.gameserver.utils.PositionUtil;

/** Server-driven movement; keeps the normal player's speed, effects and movement packets. */
public final class PlayerBotMoveController extends PlayerMoveController {
	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PlayerBotMoveController.class);
	private volatile boolean failed;
	private final java.util.ArrayDeque<WalkPoint> walkAhead = new java.util.ArrayDeque<>();
	private final java.util.ArrayDeque<WalkPoint> walked = new java.util.ArrayDeque<>();
	public record WalkPoint(float x,float y,float z) {}
	private java.util.function.BiPredicate<WalkPoint,WalkPoint> walkSafe;
	private WalkPoint walkCurrent;
	private long walkUntil;
	private int walkWorld,walkInstance;
	/** Only the navigation owner publishes already proved corridor edges. No path search runs on the mover. */
	public synchronized void continueWalk(java.util.List<WalkPoint> points,
		java.util.function.BiPredicate<WalkPoint,WalkPoint> safe) {
		walkAhead.clear();walkCurrent=null;
		if(points.isEmpty() || !isInMove() || owner.isFlying() || heightPlan!=null || platformPlan!=null)return;
		var first=points.getFirst();
		if(PositionUtil.getDistance(targetDestX,targetDestY,targetDestZ,first.x(),first.y(),first.z())>.15)return;
		walkCurrent=first;
		double distance=0;var previous=first;
		for(int i=1;i<points.size() && i<16;i++) {
			var point=points.get(i);distance+=PositionUtil.getDistance(previous.x(),previous.y(),previous.z(),point.x(),point.y(),point.z());
			if(distance>Math.max(6,owner.getGameStats().getMovementSpeedFloat()*2))break;
			walkAhead.addLast(point);previous=point;
		}
		walkSafe=java.util.Objects.requireNonNull(safe);walkWorld=owner.getWorldId();walkInstance=owner.getInstanceId();walkUntil=System.currentTimeMillis()+3000;
	}
	public synchronized java.util.List<WalkPoint> completedWalk() {
		var result=java.util.List.copyOf(walked);walked.clear();return result;
	}
	private boolean nextWalkPoint(long now) {
		if(walkAhead.isEmpty() || now>walkUntil || owner.getWorldId()!=walkWorld || owner.getInstanceId()!=walkInstance
			|| owner.isCasting() || owner.isTrading() || owner.isLooting() || !owner.canPerformMove())return false;
		var next=walkAhead.peekFirst();
		var from=new WalkPoint(owner.getX(),owner.getY(),owner.getZ());
		if(walkSafe==null || !walkSafe.test(from,next))return false;
		// Recheck the whole handoff edge for fresh walls/doors. The ordinary
		// ground loop still checks the actual floor step on every movement tick.
		var ground=com.aionemu.gameserver.world.geo.GeoService.getInstance().findGroundMovementCollision(owner.getWorldId(),owner.getInstanceId(),
			owner.getX(),owner.getY(),owner.getZ(),next.x(),next.y(),next.z());
		if(ground==null || !Float.isFinite(ground.x) || !Float.isFinite(ground.y) || !Float.isFinite(ground.z)
			|| Math.hypot(ground.x-next.x(),ground.y-next.y())>.15 || Math.abs(ground.z-next.z())>.75)return false;
		walked.addLast(walkCurrent!=null?walkCurrent:new WalkPoint(targetDestX,targetDestY,targetDestZ));
		while(walked.size()>16)walked.removeFirst();
		walkAhead.removeFirst();
		byte direction=(byte)Math.floorMod((int)Math.round(Math.toDegrees(Math.atan2(next.y()-owner.getY(),next.x()-owner.getX()))/3),120);
		var remaining=java.util.List.copyOf(walkAhead);
		setNewDirection(next.x(),next.y(),next.z(),direction);walkCurrent=next;walkAhead.addAll(remaining);setAndSendStartMove(owner);
		return true;
	}
	private com.aionemu.gameserver.services.playerbot.PlayerBotHeightTraversal.Plan heightPlan;
	private int heightWorld,heightInstance;
	private double heightTime;
	private boolean interruptedHeight;
	private com.aionemu.gameserver.services.playerbot.PlayerBotPlatformRide.Plan platformPlan;
	private com.aionemu.gameserver.services.playerbot.PlayerBotPlatformRide.Plan completedPlatform;
	public synchronized boolean ridingPlatform(){return platformPlan!=null;}
	public synchronized com.aionemu.gameserver.services.playerbot.PlayerBotPlatformRide.Plan completedPlatform(){var plan=completedPlatform;completedPlatform=null;return plan;}
	public synchronized com.aionemu.gameserver.services.playerbot.PlayerBotPlatformRide.Plan platformPlan(){return platformPlan;}
	public synchronized boolean startPlatform(com.aionemu.gameserver.services.playerbot.PlayerBotPlatformRide.Plan plan){
		if(platformPlan!=null)return true;
		if(heightPlan!=null || failed || owner.isDead() || owner.isCasting() || !owner.canPerformMove() || plan==null || !plan.begin(owner))return false;
		abortMove();platformPlan=plan;completedPlatform=null;updateLastMove();started.set(true);setInMove(true);
		owner.getController().onStartMove();PlayerMoveTaskManager.getInstance().addPlayer(owner);sendPlatformMove();return true;
	}
	public synchronized boolean traversingHeight(){return heightPlan!=null;}
	/** Native glide takes over from a jump/drop, revoking the old gravity plan. */
	public synchronized void onGlideStarted(){
		if(!owner.isInGlidingState() || platformPlan!=null)return;
		heightPlan=null;interruptedHeight=false;setIsJumping(false);abortMove();
	}
	public synchronized boolean startHeightTraversal(com.aionemu.gameserver.services.playerbot.PlayerBotHeightTraversal.Plan plan) {
		if(heightPlan!=null)return true;
		if(plan==null || !plan.valid() || failed || !owner.isSpawned() || owner.isDead() || owner.isFlying())return false;
		if(!plan.falling() && (owner.isCasting() || !owner.canPerformMove() || owner.isTrading() || owner.isLooting()))return false;
		if(PositionUtil.getDistance(owner,plan.x(),plan.y(),plan.z())>.3)return false;
		if(!com.aionemu.gameserver.world.geo.GeoService.getInstance().validatePlayerBotHeightLanding(owner.getWorldId(),owner.getInstanceId(),plan))return false;
		abortMove();
		heightPlan=plan;heightTime=0;interruptedHeight=false;heightWorld=owner.getWorldId();heightInstance=owner.getInstanceId();
		byte jumpHeading=(byte)Math.floorMod((int)Math.round(Math.toDegrees(Math.atan2(plan.endY()-plan.y(),plan.endX()-plan.x()))/3),120);
		setNewDirection(plan.endX(),plan.endY(),plan.endZ(),jumpHeading);
		updateLastMove();started.set(true);setInMove(true);setIsJumping(plan.launch()>0);
		owner.getController().onStartMove();
		if(plan.launch()>0)com.aionemu.gameserver.utils.PacketSendUtility.broadcastToSightedPlayers(owner,
			new com.aionemu.gameserver.network.aion.serverpackets.SM_EMOTION(owner,com.aionemu.gameserver.model.EmotionType.JUMP));
		sendHeightMove();PlayerMoveTaskManager.getInstance().addPlayer(owner);return true;
	}
	public PlayerBotMoveController(Player owner) { super(owner); }
	public boolean hasFailed() { return failed; }

	@Override
	public synchronized void setNewDirection(float x, float y, float z) {
		// Any different command revokes the previous corridor continuation.
		// nextWalkPoint replaces this queue explicitly after changing direction.
		if(x!=targetDestX || y!=targetDestY || z!=targetDestZ){walkAhead.clear();walkCurrent=null;}
		super.setNewDirection(x, y, z);
		// Headless intent replaces the client's move request, not its position sample.
		// Forced fear/confusion travel must not start voluntary combat modifiers.
		var effects = owner.getEffectController();
		if (effects != null && (effects.isUnderFear() || effects.isConfused())) onMovementStopped();
		else updateMovementModifierDirection();
	}

	private float requestedMovementSpeed() {
		MovementModifierDirection direction = PositionUtil.getDistance(owner.getX(), owner.getY(), targetDestX, targetDestY) < MOVE_CHECK_OFFSET
			? MovementModifierDirection.NONE : calculateDirection(PositionUtil.getHeadingTowards(owner.getX(), owner.getY(), targetDestX, targetDestY), heading);
		return com.aionemu.gameserver.utils.stats.StatFunctions.adjustSpeedByMovementModifier(direction, owner.getGameStats().getMovementSpeedFloat());
	}

	@Override
	public synchronized void startMovingToDestination() {
		if (failed || !owner.canPerformMove() || owner.isCasting()) return;
		if (started.compareAndSet(false, true)) {
			updateLastMove();
			owner.getController().onStartMove();
			setInMove(true);
			com.aionemu.gameserver.services.playerbot.PlayerBotFollowSpeed.update(owner,targetDestX,targetDestY,targetDestZ);
			setAndSendStartMove(owner);
			PlayerMoveTaskManager.getInstance().addPlayer(owner);
		} else if (com.aionemu.gameserver.services.playerbot.PlayerBotFormation.sendUpdate(owner)) {
			com.aionemu.gameserver.services.playerbot.PlayerBotFollowSpeed.update(owner,targetDestX,targetDestY,targetDestZ);
			setAndSendStartMove(owner);
		}
	}

	@Override
	public synchronized void moveToDestination() {
		try { moveStep(); }
		catch (RuntimeException e) {
			platformPlan=null;
			heightPlan=null;setIsJumping(false);
			// A headless player's failure must not abort the shared human movement task.
			failed = true; started.set(false);
			PlayerMoveTaskManager.getInstance().removePlayer(owner);
			log.error("Companion {} movement failed; its session will dismiss and save", owner.getObjectId(), e);
			try { super.abortMove(); } catch (RuntimeException stopError) { e.addSuppressed(stopError); }
		}
	}

	private void moveStep() {
		if(platformPlan!=null){platformStep();return;}
		if(heightPlan!=null){heightStep();return;}
		// Removal from the shared scheduler does not revoke its current snapshot.
		// A queued step after stop/cast must not emit native movement callbacks:
		// PlayerController.onStopMove and skill movement observers cancel casts.
		if (failed || !started.get() || !isInMove() || owner.isCasting()) return;
		if (!owner.isSpawned() || owner.isDead() || !owner.canPerformMove()) {
			abortMove();
			return;
		}
		long now=System.currentTimeMillis(),elapsed=Math.max(0,Math.min(1000,now-lastMoveUpdate));
		com.aionemu.gameserver.services.playerbot.PlayerBotFollowIntent.refresh(owner);
		com.aionemu.gameserver.services.playerbot.PlayerBotFollowSpeed.update(owner,targetDestX,targetDestY,targetDestZ);
		lastMoveUpdate=now-elapsed;
		if (owner.isFlying()) {
			// A moving leader may refresh the destination after the AI's original
			// proof. Validate this actual airborne body step against fresh geometry.
			double distance=PositionUtil.getDistance(owner,targetDestX,targetDestY,targetDestZ);
			double fraction=distance<.001?0:Math.min(1,requestedMovementSpeed()*elapsed/1000.0/distance);
			var from=new com.aionemu.gameserver.geoEngine.math.Vector3f(owner.getX(),owner.getY(),owner.getZ());
			var to=new com.aionemu.gameserver.geoEngine.math.Vector3f(owner.getX()+(float)((targetDestX-owner.getX())*fraction),
				owner.getY()+(float)((targetDestY-owner.getY())*fraction),owner.getZ()+(float)((targetDestZ-owner.getZ())*fraction));
			if(!com.aionemu.gameserver.world.geo.GeoService.getInstance().validatePlayerBotHeightSegment(owner.getWorldId(),owner.getInstanceId(),from,to)){abortMove();return;}
			super.moveToDestination();
		}
		else {
			// Native player interpolation is a straight XYZ line supplied by a client.
			// Headless bots must follow the floor and recheck dynamic obstacles at every movement tick.
			double distance = Math.hypot(targetDestX-owner.getX(), targetDestY-owner.getY());
			if (distance < 0.01) {
				var ground = com.aionemu.gameserver.world.geo.GeoService.getInstance().findGroundMovementCollision(
					owner.getWorldId(),owner.getInstanceId(),owner.getX(),owner.getY(),owner.getZ(),owner.getX(),owner.getY(),targetDestZ);
				if (ground != null && Float.isFinite(ground.z) && ground.z < owner.getZ() - 1.25f) {
					startSupportedFall();
					return;
				}
				if (!com.aionemu.gameserver.services.playerbot.PlayerBotFollowIntent.active(owner)) abortMove();
				else updateLastMove();
				return;
			}
			float speed = requestedMovementSpeed();
			double fraction = Math.min(1, speed*elapsed/1000.0/distance);
			if (fraction <= 0) { updateLastMove(); return; }
			float x = owner.getX()+(float)((targetDestX-owner.getX())*fraction);
			float y = owner.getY()+(float)((targetDestY-owner.getY())*fraction);
			var ground = com.aionemu.gameserver.world.geo.GeoService.getInstance().findGroundMovementCollision(
				owner.getWorldId(),owner.getInstanceId(),owner.getX(),owner.getY(),owner.getZ(),x,y,targetDestZ);
			if (ground == null || !Float.isFinite(ground.x) || !Float.isFinite(ground.y) || !Float.isFinite(ground.z)) { abortMove(); return; }
			if (ground.z < owner.getZ() - 1.25f) { startSupportedFall(); return; }
			if (Math.hypot(ground.x-owner.getX(),ground.y-owner.getY()) < 0.001) { abortMove(); return; }
			com.aionemu.gameserver.world.World.getInstance().updatePosition(owner,ground.x,ground.y,ground.z,heading,false);
			updateLastMove();
		}
		owner.getKnownList().update();
		owner.getController().onMove();
		if (PositionUtil.getDistance(owner, targetDestX, targetDestY, targetDestZ) < 0.3
			&& !com.aionemu.gameserver.services.playerbot.PlayerBotFollowIntent.active(owner)
			&& !nextWalkPoint(now))abortMove();
	}

	@Override
	public synchronized void abortMove() {
		walkAhead.clear();walkCurrent=null;walkSafe=null;walkUntil=0;
		if(platformPlan!=null && platformPlan.onboard() && owner.isSpawned() && !owner.isDead()){
			// A command or cast cannot leave the actor suspended off its moving floor.
			platformPlan.interrupt();return;
		}
		platformPlan=null;
		if(heightPlan!=null && (heightTime>0 || heightPlan.falling()) && owner.isSpawned() && !owner.isDead() && owner.getWorldId()==heightWorld && owner.getInstanceId()==heightInstance) {
			// A command/cast cannot freeze a headless actor above a floor. Stop horizontal intent and finish gravity.
			// Once horizontal intent has been shed, repeated stop requests must not
			// restart a vertical fall from rest on every decision tick.
			if(!heightPlan.falling())interruptedHeight=true;
			return;
		}
		heightPlan=null;setIsJumping(false);
		if (com.aionemu.gameserver.services.playerbot.PlayerBotRecall.recalling(owner)) failed = false;
		com.aionemu.gameserver.services.playerbot.PlayerBotFollowIntent.clear(owner);
		boolean wasMoving = isInMove();
		if (wasMoving || started.get()) super.abortMove();
		com.aionemu.gameserver.services.playerbot.PlayerBotFollowSpeed.close(owner);
		if (wasMoving) owner.getController().onStopMove();
	}
	private void startSupportedFall() {
		// Ground probes may recover an inherited airborne origin, but that lower
		// floor is not an instantaneous walking position. Keep physical custody
		// in the same gravity mover used after an interrupted jump/platform ride.
		var fall=com.aionemu.gameserver.world.geo.GeoService.getInstance().findPlayerBotFall(
			owner.getWorldId(),owner.getInstanceId(),owner.getX(),owner.getY(),owner.getZ());
		if(fall==null || !startHeightTraversal(fall))abortMove();
	}
	private void sendPlatformMove(){
		if(platformPlan==null)return;
		setNewDirection(owner.getX(),owner.getY(),owner.getZ(),heading);
		movementMask=(byte)(MovementMask.POSITION|MovementMask.MANUAL|MovementMask.ABSOLUTE);
		if(platformPlan.onboard()){
			movementMask|=MovementMask.VEHICLE;unk1=platformPlan.entityId();unk2=platformPlan.wirePart();
			double[] local=platformPlan.local();vehicleX=(float)local[0];vehicleY=(float)local[1];vehicleZ=(float)local[2];
		}
		com.aionemu.gameserver.utils.PacketSendUtility.broadcastToSightedPlayers(owner,new com.aionemu.gameserver.network.aion.serverpackets.SM_MOVE(owner));
	}
	private void platformStep(){
		var plan=platformPlan;
		if(!plan.valid(owner)){platformPlan=null;completedPlatform=plan;abortMove();return;}
		var step=plan.advance(owner);
		if(step.state()==com.aionemu.gameserver.services.playerbot.PlayerBotPlatformRide.State.FAILED){
			platformPlan=null;completedPlatform=plan;abortMove();
			// Failed boarding stays on its supported origin; failed carry must run
			// the existing supported gravity path rather than inventing a landing.
			var fall=com.aionemu.gameserver.world.geo.GeoService.getInstance().findPlayerBotFall(owner.getWorldId(),owner.getInstanceId(),owner.getX(),owner.getY(),owner.getZ());
			if(fall!=null){if(!startHeightTraversal(fall))failed=true;}
			else if(!Float.isFinite(com.aionemu.gameserver.world.geo.GeoService.getInstance().playerBotStaticFloor(owner.getWorldId(),owner.getInstanceId(),owner.getX(),owner.getY(),owner.getZ())))failed=true;
			return;
		}
		boolean changed=PositionUtil.getDistance(owner,step.x(),step.y(),step.z())>.001;
		if(changed){com.aionemu.gameserver.world.World.getInstance().updatePosition(owner,step.x(),step.y(),step.z(),heading,false);owner.getKnownList().update();owner.getController().onMove();}
		updateLastMove();sendPlatformMove();
		if(step.state()==com.aionemu.gameserver.services.playerbot.PlayerBotPlatformRide.State.COMPLETE){platformPlan=null;completedPlatform=plan;unk1=unk2=0;vehicleX=vehicleY=vehicleZ=0;abortMove();}
	}
	private void sendHeightMove() {
		// S_MOVE_NEW has no duration/ballistic payload: ABSOLUTE installs a
		// client destination, not a timed sample of this server-owned arc.
		// Native IMMEDIATE revokes that old destination and consumes current XYZ.
		// Stream the actual collision-checked pose; do not let client walking
		// prediction pull a descending actor back toward a ledge waypoint.
		movementMask=MovementMask.IMMEDIATE;
		com.aionemu.gameserver.utils.PacketSendUtility.broadcastToSightedPlayers(owner,new com.aionemu.gameserver.network.aion.serverpackets.SM_MOVE(owner));
	}
	private boolean retainHeightStep(com.aionemu.gameserver.services.playerbot.PlayerBotHeightTraversal.Plan plan) {
		if(heightPlan!=plan)return false;
		if(owner.isSpawned() && !owner.isDead() && !owner.isFlying() && owner.getWorldId()==heightWorld && owner.getInstanceId()==heightInstance)return true;
		heightPlan=null;abortMove();return false;
	}
	private void heightStep() {
		if(!owner.isSpawned() || owner.isDead() || owner.isFlying() || owner.getWorldId()!=heightWorld || owner.getInstanceId()!=heightInstance) {
			heightPlan=null;abortMove();return;
		}
		var geo=com.aionemu.gameserver.world.geo.GeoService.getInstance();
		float currentSpeed=requestedMovementSpeed();
		if(heightPlan.horizontalSpeed()>currentSpeed+.05)interruptedHeight=true;
		if(interruptedHeight || (!owner.canPerformMove() || owner.isCasting())
			&& (heightPlan.launch()>0 || heightPlan.endX()!=heightPlan.x() || heightPlan.endY()!=heightPlan.y())) {
			var fall=geo.findPlayerBotFall(heightWorld,heightInstance,owner.getX(),owner.getY(),owner.getZ());
			if(fall!=null){heightPlan=fall;heightTime=0;}
			else if(heightTime==0){heightPlan=null;abortMove();return;}
			interruptedHeight=false;
		}
		double elapsed=Math.max(0,Math.min(200,System.currentTimeMillis()-lastMoveUpdate))/1000.0;
		double nextTime=Math.min(heightPlan.seconds(),heightTime+elapsed);
		var previous=new com.aionemu.gameserver.geoEngine.math.Vector3f(owner.getX(),owner.getY(),owner.getZ());
		var next=heightPlan.at(nextTime);
		boolean clear=true;
		int samples=Math.max(1,(int)Math.ceil(previous.distance(next)/.2));
		for(int i=1;i<=samples;i++) {
			var sample=heightPlan.at(heightTime+(nextTime-heightTime)*i/samples);
			if(!geo.validatePlayerBotHeightSegment(heightWorld,heightInstance,previous,sample)){clear=false;break;}
			previous=sample;
		}
		if(!clear || nextTime>=heightPlan.seconds() && !geo.validatePlayerBotHeightLanding(heightWorld,heightInstance,heightPlan)) {
			var fall=geo.findPlayerBotFall(heightWorld,heightInstance,owner.getX(),owner.getY(),owner.getZ());
			if(fall!=null){heightPlan=fall;heightTime=0;updateLastMove();return;}
			// Dynamic geometry blocks all descent. Leave the physical actor in place and report a real failed mover.
			heightPlan=null;failed=true;abortMove();return;
		}
		var activePlan=heightPlan;
		if(activePlan.descending(heightTime))updateFalling(owner.getZ());
		// Native fall damage/observers can synchronously kill, transfer or stop
		// the actor. Such a callback owns the outcome, not this queued sample.
		if(!retainHeightStep(activePlan))return;
		com.aionemu.gameserver.world.World.getInstance().updatePosition(owner,next.x,next.y,next.z,heading,false);
		heightTime=nextTime;updateLastMove();owner.getKnownList().update();owner.getController().onMove();
		if(!retainHeightStep(activePlan))return;
		if(nextTime>=heightPlan.seconds()) {
			stopFalling(next.z);heightPlan=null;setIsJumping(false);abortMove();
		}else sendHeightMove();
	}
}
