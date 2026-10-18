package com.aionemu.gameserver.controllers.movement;

import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.taskmanager.tasks.PlayerMoveTaskManager;
import com.aionemu.gameserver.utils.PositionUtil;

/** Server-driven movement; keeps the normal player's speed, effects and movement packets. */
public final class PlayerBotMoveController extends PlayerMoveController {
	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PlayerBotMoveController.class);
	private volatile boolean failed;
	private final java.util.ArrayDeque<WalkPoint> walkAhead = new java.util.ArrayDeque<>();
	public record WalkPoint(long index,float x,float y,float z) {}
	public record WalkStatus(long generation,long reachedIndex,WalkPoint current,boolean active,boolean blocked) {}
	private java.util.function.BiPredicate<WalkPoint,WalkPoint> walkSafe;
	private WalkPoint walkCurrent;
	private long walkUntil,walkGeneration,walkReached=-1;
	private boolean walkActive,walkBlocked,internalWalkDirection;
	private int walkWorld,walkInstance;
	/** A generation owns one immutable prefix. Refreshes append/trim its bounded
	 * tail without replacing the active edge or acknowledging physical progress. */
	public synchronized boolean publishWalk(long generation,java.util.List<WalkPoint> points,
		java.util.function.BiPredicate<WalkPoint,WalkPoint> safe) {
		if(generation<walkGeneration || points.isEmpty() || failed || !owner.isSpawned() || owner.isDead()
			|| owner.isFlying() || owner.isCasting() || owner.isTrading() || owner.isLooting() || !owner.canPerformMove()
			|| heightPlan!=null || platformPlan!=null)return false;
		for(int i=0;i<points.size();i++) {
			var point=points.get(i);
			if(!Float.isFinite(point.x()) || !Float.isFinite(point.y()) || !Float.isFinite(point.z())
				|| i>0 && point.index()!=points.get(i-1).index()+1)return false;
		}
		boolean fresh=generation>walkGeneration;
		int cursor=0;
		if(!fresh) {
			if(!walkActive || walkBlocked || System.currentTimeMillis()>walkUntil
				|| owner.getWorldId()!=walkWorld || owner.getInstanceId()!=walkInstance)return false;
			while(cursor<points.size() && points.get(cursor).index()<walkCurrent.index())cursor++;
			if(cursor==points.size() || !points.get(cursor).equals(walkCurrent))return false;
		}
		var first=points.get(cursor);
		if(fresh) {
			walkGeneration=generation;walkReached=first.index()-1;walkCurrent=first;walkActive=true;walkBlocked=false;
			com.aionemu.gameserver.services.playerbot.PlayerBotFollowIntent.clear(owner);
		}
		walkAhead.clear();
		double distance=0;var previous=first;
		for(int i=cursor+1;i<points.size() && i<cursor+16;i++) {
			var point=points.get(i);distance+=PositionUtil.getDistance(previous.x(),previous.y(),previous.z(),point.x(),point.y(),point.z());
			if(distance>Math.max(6,owner.getGameStats().getMovementSpeedFloat()*2))break;
			walkAhead.addLast(point);previous=point;
		}
		walkSafe=java.util.Objects.requireNonNull(safe);walkWorld=owner.getWorldId();walkInstance=owner.getInstanceId();walkUntil=System.currentTimeMillis()+3000;
		if(fresh){walkDirection(first);startMovingToDestination();}
		return isInMove();
	}
	public synchronized WalkStatus walkStatus() {
		return new WalkStatus(walkGeneration,walkReached,walkCurrent,walkActive && isInMove() && System.currentTimeMillis()<=walkUntil,walkBlocked);
	}
	public synchronized void cancelWalk(long generation) {
		if(generation==walkGeneration && walkActive)abortMove();
	}
	/** Hazard publication does not extend ownership or refresh an abandoned path. */
	public synchronized void walkSafety(long generation,java.util.function.BiPredicate<WalkPoint,WalkPoint> safe) {
		if(generation==walkGeneration && walkActive)walkSafe=java.util.Objects.requireNonNull(safe);
	}
	private void revokeWalk(boolean blocked) {
		walkAhead.clear();walkCurrent=null;walkSafe=null;walkUntil=0;walkActive=false;walkBlocked=blocked;
	}
	private void walkDirection(WalkPoint point) {
		byte direction=(byte)Math.floorMod((int)Math.round(Math.toDegrees(Math.atan2(point.y()-owner.getY(),point.x()-owner.getX()))/3),120);
		internalWalkDirection=true;
		try{setNewDirection(point.x(),point.y(),point.z(),direction);}finally{internalWalkDirection=false;}
	}
	private boolean nextWalkPoint(long now) {
		if(!walkActive || now>walkUntil || owner.getWorldId()!=walkWorld || owner.getInstanceId()!=walkInstance
			|| owner.isCasting() || owner.isTrading() || owner.isLooting() || !owner.canPerformMove())return false;
		if(walkCurrent!=null)walkReached=walkCurrent.index();
		if(walkAhead.isEmpty()){walkCurrent=null;walkActive=false;return false;}
		var next=walkAhead.peekFirst();
		var from=new WalkPoint(-1,owner.getX(),owner.getY(),owner.getZ());
		if(walkSafe==null || !walkSafe.test(from,next)){walkBlocked=true;return false;}
		// Recheck the whole handoff edge for fresh walls/doors. The ordinary
		// ground loop still checks the actual floor step on every movement tick.
		var ground=com.aionemu.gameserver.world.geo.GeoService.getInstance().findGroundMovementCollision(owner.getWorldId(),owner.getInstanceId(),
			owner.getX(),owner.getY(),owner.getZ(),next.x(),next.y(),next.z());
		if(ground==null || !Float.isFinite(ground.x) || !Float.isFinite(ground.y) || !Float.isFinite(ground.z)
			|| Math.hypot(ground.x-next.x(),ground.y-next.y())>.15 || Math.abs(ground.z-next.z())>.75){walkBlocked=true;return false;}
		walkAhead.removeFirst();
		walkCurrent=next;walkDirection(next);setAndSendStartMove(owner);
		return true;
	}
	private com.aionemu.gameserver.services.playerbot.PlayerBotHeightTraversal.Plan heightPlan;
	private int heightWorld,heightInstance;
	private double heightTime;
	private boolean interruptedHeight;
	private com.aionemu.gameserver.services.playerbot.PlayerBotPlatformRide.Plan platformPlan;
	private com.aionemu.gameserver.services.playerbot.PlayerBotPlatformRide.Plan completedPlatform;
	private long lastPlatformPacket;
	private com.aionemu.gameserver.services.playerbot.PlayerBotPlatformRide.State lastPlatformState;
	public synchronized boolean ridingPlatform(){return platformPlan!=null;}
	public synchronized com.aionemu.gameserver.services.playerbot.PlayerBotPlatformRide.Plan completedPlatform(){var plan=completedPlatform;completedPlatform=null;return plan;}
	public synchronized com.aionemu.gameserver.services.playerbot.PlayerBotPlatformRide.Plan platformPlan(){return platformPlan;}
	public synchronized boolean startPlatform(com.aionemu.gameserver.services.playerbot.PlayerBotPlatformRide.Plan plan){
		if(platformPlan!=null)return true;
		if(heightPlan!=null || failed || owner.isDead() || owner.isCasting() || !owner.canPerformMove() || plan==null || !plan.begin(owner))return false;
		abortMove();platformPlan=plan;completedPlatform=null;lastPlatformState=null;lastPlatformPacket=0;updateLastMove();started.set(true);setInMove(true);
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

	/** Only the final direct follow edge is mutable. A detour's indexed corners
	 * remain immutable, and refreshing a target never extends its AI-owned lease. */
	public synchronized boolean refreshFollowDestination(float x,float y,float z,byte direction) {
		if(!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z)
			|| !isInMove() || failed || owner.isDead() || owner.isCasting() || !owner.canPerformMove()
			|| heightPlan!=null || platformPlan!=null)return false;
		if(walkActive) {
			if(walkCurrent==null || !walkAhead.isEmpty() || System.currentTimeMillis()>walkUntil)return false;
			var next=new WalkPoint(walkCurrent.index(),x,y,z);
			if(walkSafe==null || !walkSafe.test(new WalkPoint(-1,owner.getX(),owner.getY(),owner.getZ()),next))return false;
			walkCurrent=next;walkDirection(next);
		} else setNewDirection(x,y,z,direction);
		return true;
	}

	@Override
	public synchronized void setNewDirection(float x, float y, float z) {
		// External commands revoke the corridor; internal edge advancement keeps
		// its generation and monotonic acknowledgment cursor.
		if(!internalWalkDirection && walkActive)revokeWalk(false);
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
			if(platformPlan!=null)platformPlan.close();platformPlan=null;
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
		if (failed || !started.get() || !isInMove()) return;
		if(owner.isCasting()) {
			if(walkActive){
				revokeWalk(false);com.aionemu.gameserver.services.playerbot.PlayerBotFollowIntent.clear(owner);
				// Retire movement without PlayerController.onStopMove, whose native
				// observer would cancel the cast that just took ownership.
				super.abortMove();
			}
			return;
		}
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
		else {groundStep(now,elapsed);return;}
		owner.getKnownList().update();owner.getController().onMove();
		if(PositionUtil.getDistance(owner,targetDestX,targetDestY,targetDestZ)<.3
			&& !com.aionemu.gameserver.services.playerbot.PlayerBotFollowIntent.active(owner))abortMove();
	}

	/** Spend one time allowance across a bounded corridor, never bank time for a
	 * later burst. Every corner keeps its own speed, floor and fresh safety proof. */
	private void groundStep(long now,long elapsed) {
		double remaining=elapsed/1000.;
		for(int edges=0;edges<8;edges++) {
			if(!started.get() || !isInMove())return;
			if(owner.isCasting() || owner.isTrading() || owner.isLooting() || !owner.canPerformMove()) {abortMove();return;}
			if(walkActive && (now>walkUntil || owner.getWorldId()!=walkWorld || owner.getInstanceId()!=walkInstance)) {abortMove();return;}
			double distance=Math.hypot(targetDestX-owner.getX(),targetDestY-owner.getY());
			if(walkActive && (walkSafe==null || !walkSafe.test(new WalkPoint(-1,owner.getX(),owner.getY(),owner.getZ()),walkCurrent))) {
				walkBlocked=true;abortMove();return;
			}
			if(distance<.01) {
				var ground=com.aionemu.gameserver.world.geo.GeoService.getInstance().findGroundMovementCollision(
					owner.getWorldId(),owner.getInstanceId(),owner.getX(),owner.getY(),owner.getZ(),owner.getX(),owner.getY(),targetDestZ);
				if(ground!=null && Float.isFinite(ground.z) && ground.z<owner.getZ()-1.25f){startSupportedFall();return;}
				if(ground==null || !Float.isFinite(ground.z) || Math.abs(ground.z-targetDestZ)>.75) {walkBlocked=true;abortMove();return;}
				if(com.aionemu.gameserver.services.playerbot.PlayerBotFollowIntent.active(owner)){updateLastMove();return;}
				if(!nextWalkPoint(now)){abortMove();return;}
				continue;
			}
			if(remaining<=0){updateLastMove();return;}
			float speed=requestedMovementSpeed();
			if(!Float.isFinite(speed) || speed<=0){updateLastMove();return;}
			double fraction=Math.min(1,speed*remaining/distance);
			float x=owner.getX()+(float)((targetDestX-owner.getX())*fraction);
			float y=owner.getY()+(float)((targetDestY-owner.getY())*fraction);
			var ground=com.aionemu.gameserver.world.geo.GeoService.getInstance().findGroundMovementCollision(
				owner.getWorldId(),owner.getInstanceId(),owner.getX(),owner.getY(),owner.getZ(),x,y,targetDestZ);
			if(ground==null || !Float.isFinite(ground.x) || !Float.isFinite(ground.y) || !Float.isFinite(ground.z)){walkBlocked=true;abortMove();return;}
			if(ground.z<owner.getZ()-1.25f){startSupportedFall();return;}
			double covered=Math.hypot(ground.x-owner.getX(),ground.y-owner.getY());
			if(covered<.001){walkBlocked=true;abortMove();return;}
			long generation=walkGeneration;boolean owned=walkActive;
			com.aionemu.gameserver.world.World.getInstance().updatePosition(owner,ground.x,ground.y,ground.z,heading,false);
			remaining=Math.max(0,remaining-covered/speed);updateLastMove();
			owner.getKnownList().update();owner.getController().onMove();
			// A native observer may change casting, movement or the command itself.
			if(owner.isCasting() || !isInMove() || !started.get() || generation!=walkGeneration || owned && !walkActive)return;
			if(fraction<1 || Math.hypot(targetDestX-owner.getX(),targetDestY-owner.getY())>=.01)return;
			if(Math.abs(targetDestZ-owner.getZ())>.75){walkBlocked=true;abortMove();return;}
			if(com.aionemu.gameserver.services.playerbot.PlayerBotFollowIntent.active(owner))return;
			if(!nextWalkPoint(now)){abortMove();return;}
		}
		updateLastMove(); // fixed edge cap discards excess time, never a future catch-up burst
	}

	@Override
	public synchronized void abortMove() {
		revokeWalk(walkBlocked);
		if(platformPlan!=null && platformPlan.onboard() && owner.isSpawned() && !owner.isDead()){
			// A command or cast cannot leave the actor suspended off its moving floor.
			platformPlan.interrupt();return;
		}
		if(platformPlan!=null)platformPlan.close();platformPlan=null;
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
		long now=System.currentTimeMillis();var state=platformPlan.state();
		if(state==com.aionemu.gameserver.services.playerbot.PlayerBotPlatformRide.State.RIDING && lastPlatformState==state && now-lastPlatformPacket<1000)return;
		lastPlatformState=state;lastPlatformPacket=now;
		setNewDirection(owner.getX(),owner.getY(),owner.getZ(),heading);
		movementMask=(byte)(MovementMask.POSITION|MovementMask.MANUAL|MovementMask.ABSOLUTE);
		if(platformPlan.onboard()){
			movementMask|=MovementMask.VEHICLE;unk1=platformPlan.entityId();unk2=platformPlan.wirePart();
			if(state==com.aionemu.gameserver.services.playerbot.PlayerBotPlatformRide.State.RIDING)movementMask=MovementMask.VEHICLE;
			double[] local=platformPlan.local();vehicleX=(float)local[0];vehicleY=(float)local[1];vehicleZ=(float)local[2];
		}
		com.aionemu.gameserver.utils.PacketSendUtility.broadcastToSightedPlayers(owner,new com.aionemu.gameserver.network.aion.serverpackets.SM_MOVE(owner));
	}
	private void platformStep(){
		var plan=platformPlan;
		if(!plan.valid(owner)){plan.close();platformPlan=null;completedPlatform=plan;abortMove();return;}
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
