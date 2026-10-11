package com.aionemu.gameserver.controllers.movement;

import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.taskmanager.tasks.PlayerMoveTaskManager;
import com.aionemu.gameserver.utils.PositionUtil;

/** Server-driven movement; keeps the normal player's speed, effects and movement packets. */
public final class PlayerBotMoveController extends PlayerMoveController {
	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PlayerBotMoveController.class);
	private volatile boolean failed;
	private com.aionemu.gameserver.services.playerbot.PlayerBotHeightTraversal.Plan heightPlan;
	private int heightWorld,heightInstance;
	private double heightTime;
	private boolean interruptedHeight;
	public synchronized boolean traversingHeight(){return heightPlan!=null;}
	public synchronized boolean startHeightTraversal(com.aionemu.gameserver.services.playerbot.PlayerBotHeightTraversal.Plan plan) {
		if(heightPlan!=null)return true;
		if(plan==null || !plan.valid() || failed || !owner.isSpawned() || owner.isDead() || owner.isFlying() || owner.isCasting() || !owner.canPerformMove() || owner.isTrading() || owner.isLooting()
			|| PositionUtil.getDistance(owner,plan.x(),plan.y(),plan.z())>.3
			|| com.aionemu.gameserver.utils.stats.StatFunctions.calculateFallDamage(owner,plan.fallDistance())>=owner.getLifeStats().getCurrentHp())return false;
		if(!com.aionemu.gameserver.world.geo.GeoService.getInstance().validatePlayerBotHeightLanding(owner.getWorldId(),owner.getInstanceId(),plan))return false;
		abortMove();
		heightPlan=plan;heightTime=0;interruptedHeight=false;heightWorld=owner.getWorldId();heightInstance=owner.getInstanceId();
		byte jumpHeading=(byte)Math.floorMod((int)Math.round(Math.toDegrees(Math.atan2(plan.endY()-plan.y(),plan.endX()-plan.x()))/3),120);
		setNewDirection(plan.endX(),plan.endY(),plan.endZ(),jumpHeading);
		updateLastMove();started.set(true);setInMove(true);setIsJumping(true);
		owner.getController().onStartMove();
		com.aionemu.gameserver.utils.PacketSendUtility.broadcastToSightedPlayers(owner,
			new com.aionemu.gameserver.network.aion.serverpackets.SM_EMOTION(owner,com.aionemu.gameserver.model.EmotionType.JUMP));
		sendHeightMove();PlayerMoveTaskManager.getInstance().addPlayer(owner);return true;
	}
	public PlayerBotMoveController(Player owner) { super(owner); }
	public boolean hasFailed() { return failed; }

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
			heightPlan=null;setIsJumping(false);
			// A headless player's failure must not abort the shared human movement task.
			failed = true; started.set(false);
			PlayerMoveTaskManager.getInstance().removePlayer(owner);
			log.error("Companion {} movement failed; its session will dismiss and save", owner.getObjectId(), e);
			try { super.abortMove(); } catch (RuntimeException stopError) { e.addSuppressed(stopError); }
		}
	}

	private void moveStep() {
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
		if (owner.isFlying()) super.moveToDestination();
		else {
			// Native player interpolation is a straight XYZ line supplied by a client.
			// Headless bots must follow the floor and recheck dynamic obstacles at every movement tick.
			double distance = Math.hypot(targetDestX-owner.getX(), targetDestY-owner.getY());
			if (distance < 0.01) {
				var ground = com.aionemu.gameserver.world.geo.GeoService.getInstance().findGroundMovementCollision(
					owner.getWorldId(),owner.getInstanceId(),owner.getX(),owner.getY(),owner.getZ(),owner.getX(),owner.getY(),targetDestZ);
				if (ground != null && Float.isFinite(ground.z) && ground.z < owner.getZ() - 1.25f) {
					com.aionemu.gameserver.world.World.getInstance().updatePosition(owner,ground.x,ground.y,ground.z,heading,false);
					owner.getKnownList().update();
					owner.getController().onMove();
				}
				if (!com.aionemu.gameserver.services.playerbot.PlayerBotFollowIntent.active(owner)) abortMove();
				else updateLastMove();
				return;
			}
			float speed = com.aionemu.gameserver.utils.stats.StatFunctions.adjustStatByMovementModifier(owner,
				com.aionemu.gameserver.model.stats.container.StatEnum.SPEED, owner.getGameStats().getMovementSpeedFloat());
			double fraction = Math.min(1, speed*elapsed/1000.0/distance);
			if (fraction <= 0) { updateLastMove(); return; }
			float x = owner.getX()+(float)((targetDestX-owner.getX())*fraction);
			float y = owner.getY()+(float)((targetDestY-owner.getY())*fraction);
			var ground = com.aionemu.gameserver.world.geo.GeoService.getInstance().findGroundMovementCollision(
				owner.getWorldId(),owner.getInstanceId(),owner.getX(),owner.getY(),owner.getZ(),x,y,targetDestZ);
			if (ground == null || !Float.isFinite(ground.x) || !Float.isFinite(ground.y) || !Float.isFinite(ground.z)
				|| Math.hypot(ground.x-owner.getX(),ground.y-owner.getY()) < 0.001) { abortMove(); return; }
			com.aionemu.gameserver.world.World.getInstance().updatePosition(owner,ground.x,ground.y,ground.z,heading,false);
			updateLastMove();
		}
		owner.getKnownList().update();
		owner.getController().onMove();
		if (PositionUtil.getDistance(owner, targetDestX, targetDestY, targetDestZ) < 0.3
			&& !com.aionemu.gameserver.services.playerbot.PlayerBotFollowIntent.active(owner))
			abortMove();
	}

	@Override
	public synchronized void abortMove() {
		if(heightPlan!=null && heightTime>0 && owner.isSpawned() && !owner.isDead() && owner.getWorldId()==heightWorld && owner.getInstanceId()==heightInstance) {
			// A command/cast cannot freeze a headless actor above a floor. Stop horizontal intent and finish gravity.
			interruptedHeight=true;return;
		}
		heightPlan=null;setIsJumping(false);
		if (com.aionemu.gameserver.services.playerbot.PlayerBotRecall.recalling(owner)) failed = false;
		com.aionemu.gameserver.services.playerbot.PlayerBotFollowIntent.clear(owner);
		boolean wasMoving = isInMove();
		if (wasMoving || started.get()) super.abortMove();
		com.aionemu.gameserver.services.playerbot.PlayerBotFollowSpeed.close(owner);
		if (wasMoving) owner.getController().onStopMove();
	}
	private void sendHeightMove() {
		var next=heightPlan.at(heightTime+.2);
		setNewDirection(next.x,next.y,next.z,heading);
		movementMask=(byte)(MovementMask.POSITION|MovementMask.MANUAL|MovementMask.ABSOLUTE
			|(heightPlan.descending(heightTime)?MovementMask.FALL:0));
		com.aionemu.gameserver.utils.PacketSendUtility.broadcastToSightedPlayers(owner,new com.aionemu.gameserver.network.aion.serverpackets.SM_MOVE(owner));
	}
	private void heightStep() {
		if(!owner.isSpawned() || owner.isDead() || owner.isFlying() || owner.getWorldId()!=heightWorld || owner.getInstanceId()!=heightInstance) {
			heightPlan=null;abortMove();return;
		}
		var geo=com.aionemu.gameserver.world.geo.GeoService.getInstance();
		float currentSpeed=com.aionemu.gameserver.utils.stats.StatFunctions.adjustStatByMovementModifier(owner,
			com.aionemu.gameserver.model.stats.container.StatEnum.SPEED,owner.getGameStats().getMovementSpeedFloat());
		if(Math.hypot(heightPlan.endX()-heightPlan.x(),heightPlan.endY()-heightPlan.y())/(heightPlan.seconds()-heightPlan.delay())>currentSpeed+.05)interruptedHeight=true;
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
		if(heightPlan.descending(heightTime))updateFalling(owner.getZ());
		com.aionemu.gameserver.world.World.getInstance().updatePosition(owner,next.x,next.y,next.z,heading,false);
		heightTime=nextTime;updateLastMove();owner.getKnownList().update();owner.getController().onMove();
		if(nextTime>=heightPlan.seconds()) {
			stopFalling(next.z);heightPlan=null;setIsJumping(false);abortMove();
		}else sendHeightMove();
	}
}
