package com.aionemu.gameserver.spawnengine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.aionemu.gameserver.configs.main.GeoDataConfig;
import com.aionemu.gameserver.controllers.movement.NpcGroundNavigation;
import com.aionemu.gameserver.geoEngine.math.Vector3f;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.templates.walker.RouteStep;
import com.aionemu.gameserver.model.templates.walker.WalkerTemplate;
import com.aionemu.gameserver.model.templates.zone.Point2D;
import com.aionemu.gameserver.world.geo.GeoService;

/**
 * Stores for the spawn needed information, used for forming walker groups and spawning NPCs
 * 
 * @author vlog, Rolandas
 */
public class ClusteredNpc extends WalkerGroupShift {

	private static final Logger log=LoggerFactory.getLogger(ClusteredNpc.class);
	private Npc npc;
	private int instance;
	private WalkerTemplate walkTemplate;
	private float x;
	private float y;
	private float authoredX,authoredY;
	private RouteStep authoredStep;

	public ClusteredNpc(Npc npc, int instance, WalkerTemplate walkTemplate) {
		super(0, 0);
		this.npc = npc;
		this.instance = instance;
		this.walkTemplate = walkTemplate;
		this.x = npc.getSpawn().getX();
		this.y = npc.getSpawn().getY();
	}

	public Npc getNpc() {
		return npc;
	}

	public int getInstance() {
		return instance;
	}

	public void spawn(float z) {
		SpawnEngine.bringIntoWorld(npc, npc.getSpawn().getWorldId(), instance, x, y, z, npc.getSpawn().getHeading());
	}

	protected float resolveSpawnHeight(float z) {
		// Admission occurs before World registration. Prove the formation offset
		// from its authored route layer, rather than settling a live actor through a floor.
		if(npc.isSpawned() || NpcGroundNavigation.isAerialPatrol(npc)
			|| GeoDataConfig.MODE!=GeoDataConfig.Mode.ON || !GeoDataConfig.GEO_NPC_MOVE)return z;
		float anchorZ=z;
		Vector3f supported=supportedSpawn(anchorZ,x,y);
		if(supported!=null)return supported.z;
		// The route and spawn may disagree at identical XY. A declared route
		// layer is an admission anchor only after its own native support/body proof.
		if(supportedSpawn(anchorZ,authoredX,authoredY)==null && authoredStep!=null
			&& authoredStep.getX()==authoredX && authoredStep.getY()==authoredY) {
			Vector3f anchor=supportedSpawn(authoredStep.getZ(),authoredX,authoredY);
			if(anchor!=null) {
				anchorZ=anchor.z;
				supported=supportedSpawn(anchorZ,x,y);
				if(supported!=null)return supported.z;
			}
		}
		// Compact only an actual formation shift. Never invent a floor or
		// collapse the formation onto its center after a failed edge proof.
		if(getSagittalShift()!=0 || getCoronalShift()!=0) {
			float dx=x-authoredX,dy=y-authoredY;
			for(float scale:new float[]{.75f,.5f,.25f}) {
				float candidateX=authoredX+dx*scale,candidateY=authoredY+dy*scale;
				supported=supportedSpawn(anchorZ,candidateX,candidateY);
				if(supported!=null) {
					x=candidateX;y=candidateY;
					set(new WalkerGroupShift(getSagittalShift()*scale,getCoronalShift()*scale));
					return supported.z;
				}
			}
		}
		log.warn("NPC {} walker {} has no supported formation spawn from {}/{}/{} to {}/{} in world {}; retaining authored height",
			npc.getNpcId(),npc.getSpawn().getWalkerId(),authoredX,authoredY,z,x,y,npc.getSpawn().getWorldId());
		return z;
	}

	private Vector3f supportedSpawn(float z,float targetX,float targetY) {
		return GeoService.getInstance().findNpcPatrolSpawn(npc.getSpawn().getWorldId(),instance,
			authoredX,authoredY,z,targetX,targetY,npc.getObjectTemplate().getHeight());
	}

	protected void bringIntoWorld(float z) {
		SpawnEngine.bringIntoWorld(npc, npc.getSpawn().getWorldId(), instance, x, y, z, npc.getSpawn().getHeading());
	}

	public void despawn() {
		npc.getMoveController().abortMove();
		npc.getController().deleteIfAliveOrCancelRespawn();
	}

	public void setNpc(Npc npc, RouteStep step) {
		this.npc = npc;
		this.x = step.getX();
		this.y = step.getY();
	}

	public boolean hasSamePosition(ClusteredNpc other) {
		if (this == other)
			return true;
		if (other == null)
			return false;
		return this.x == other.x && this.y == other.y;
	}

	public int getPositionHash() {
		final int prime = 31;
		int result = 1;
		result = prime * result + Float.floatToIntBits(x);
		result = prime * result + Float.floatToIntBits(y);
		return result;
	}

	/**
	 * @return the x
	 */
	public float getX() {
		return x;
	}

	public float getXDelta() {
		return walkTemplate.getRouteStep(0).getX() - x;
	}

	/**
	 * @param x
	 *          the x to set
	 */
	public void setX(float x) {
		this.x = x;
	}

	/**
	 * @return the y
	 */
	public float getY() {
		return y;
	}

	public float getYDelta() {
		return walkTemplate.getRouteStep(0).getY() - y;
	}

	/**
	 * @param y
	 *          the y to set
	 */
	public void setY(float y) {
		this.y = y;
	}

	/**
	 * @return the walkTemplate
	 */
	public WalkerTemplate getWalkTemplate() {
		return walkTemplate;
	}

	public Integer getWalkerIndex() {
		return npc.getSpawn().getWalkerIndex();
	}

}
