package com.aionemu.gameserver.services.playerbot;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.aionemu.gameserver.controllers.movement.PlayerBotMoveController;
import com.aionemu.gameserver.geoEngine.collision.IgnoreProperties;
import com.aionemu.gameserver.geoEngine.collision.CollisionIntention;
import com.aionemu.gameserver.geoEngine.math.Vector3f;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.services.playerbot.PlayerBotNavigation.Point;
import com.aionemu.gameserver.skillengine.effect.*;
import com.aionemu.gameserver.skillengine.model.SkillTemplate;
import com.aionemu.gameserver.skillengine.properties.FirstTargetAttribute;
import com.aionemu.gameserver.utils.PositionUtil;
import com.aionemu.gameserver.world.geo.GeoService;

/** Native skill positions, admitted through the bot's ground and hazard rules. Never moves an actor. */
final class PlayerBotSkillPosition {
	private static final Map<SkillTemplate,List<EffectTemplate>> MOVEMENT = new ConcurrentHashMap<>();
	private enum Placement { POINT, TARGET, FORWARD, SELF }
	private static final Map<SkillTemplate,Placement> PLACEMENT = new ConcurrentHashMap<>();

	private static List<EffectTemplate> effects(SkillTemplate template) {
		return MOVEMENT.computeIfAbsent(template,t->t.getEffects()==null?List.of():t.getEffects().getEffects().stream()
			.filter(e->e instanceof DashEffect || e instanceof BackDashEffect || e instanceof MoveBehindEffect || e instanceof RandomMoveLocEffect).toList());
	}
	static boolean movement(SkillTemplate template) { return !effects(template).isEmpty(); }
	static boolean retreat(SkillTemplate template) {
		return effects(template).stream().anyMatch(e->e instanceof BackDashEffect || e instanceof RandomMoveLocEffect r && r.getDirection()==1);
	}
	/** The selected creature's present supported position is the aim, never an invented velocity or world origin. */
	static Point point(Player bot,SkillTemplate template,Creature target) {
		if(target==null || !target.isSpawned() || target.getWorldId()!=bot.getWorldId() || target.getInstanceId()!=bot.getInstanceId()
			|| target.isFlying())return null;
		var geo=GeoService.getInstance();
		float floor=geo.playerBotStaticFloor(bot.getWorldId(),bot.getInstanceId(),target.getX(),target.getY(),target.getZ());
		if(!Float.isFinite(floor) || !PlayerBotWater.safe(bot,floor))return null;
		Point point=new Point(target.getX(),target.getY(),floor);
		if(!PositionUtil.isInRange(bot,point.x(),point.y(),point.z(),template.getProperties().getFirstTargetRange())
			|| !geo.canSee(bot,point.x(),point.y(),point.z(),IgnoreProperties.of(bot.getRace())))return null;
		return point;
	}
	static Point placement(Player bot,SkillTemplate template,Creature target) {
		Placement mode=PLACEMENT.computeIfAbsent(template,t->{
			if(t.getProperties().getFirstTarget()==FirstTargetAttribute.POINT)return Placement.POINT;
			if(t.hasAnyEffect(EffectType.SUMMONSKILLAREA))return t.getProperties().getFirstTarget()==FirstTargetAttribute.ME?Placement.SELF:Placement.TARGET;
			if(t.hasAnyEffect(EffectType.SUMMONHOMING))return Placement.SELF;
			if(t.hasAnyEffect(EffectType.SUMMONSERVANT) || t.getProperties().getFirstTarget()==FirstTargetAttribute.ME
				&& t.hasAnyEffect(EffectType.SUMMONTRAP,EffectType.SUMMONTOTEM))return Placement.FORWARD;
			return Placement.SELF;
		});
		if(mode==Placement.POINT)return point(bot,template,target);
		Creature center=mode==Placement.TARGET?target:bot;
		if(center==null || !center.isSpawned() || center.isFlying() || center.getWorldId()!=bot.getWorldId() || center.getInstanceId()!=bot.getInstanceId())return null;
		var geo=GeoService.getInstance();
		Vector3f position=new Vector3f(center.getX(),center.getY(),center.getZ());
		if(mode==Placement.FORWARD) {
			double angle=Math.toRadians(PositionUtil.convertHeadingToAngle(bot.getHeading()));
			position=geo.getClosestCollision(bot,bot.getX()+(float)Math.cos(angle)*2,bot.getY()+(float)Math.sin(angle)*2,bot.getZ(),
				true,CollisionIntention.DEFAULT_COLLISIONS.getId(),IgnoreProperties.of(bot.getRace()));
		}
		float floor=geo.playerBotStaticFloor(bot.getWorldId(),bot.getInstanceId(),position.x,position.y,position.z);
		return Float.isFinite(floor) && PlayerBotWater.safe(bot,floor)?new Point(position.x,position.y,position.z):null;
	}
	static boolean deploymentAllowed(Player bot,SkillTemplate template,Creature target,PlayerBotNavigation navigation) {
		Point point=placement(bot,template,target);
		return point!=null && navigation!=null && navigation.safeSkillPath(new Point(bot.getX(),bot.getY(),bot.getZ()),point);
	}
	static boolean movementAllowed(Player bot,SkillTemplate template,Creature target,PlayerBotNavigation navigation) {
		var effects=effects(template);
		if(effects.isEmpty())return true;
		// All learned player movement skills are instant. A later timed variant needs end-cast admission.
		if(template.getDuration()>0 || bot.isFlying() || bot.isInGlidingState() || !bot.canPerformMove()
			|| bot.getMoveController() instanceof PlayerBotMoveController mover && (mover.traversingHeight() || mover.ridingPlatform())
			|| navigation==null || navigation.waitingForTransport())return false;
		Point from=new Point(bot.getX(),bot.getY(),bot.getZ());
		for(var effect:effects) {
			Vector3f destination=destination(bot,target,effect);
			if(destination==null || !Float.isFinite(destination.x) || !Float.isFinite(destination.y) || !PlayerBotWater.safe(bot,destination.z))return false;
			Point to=new Point(destination.x,destination.y,destination.z);
			if(!navigation.safeSkillPath(from,to))return false;
			var reached=PlayerBotWater.walk(bot,bot.getWorldId(),bot.getInstanceId(),from.x(),from.y(),from.z(),to.x(),to.y(),to.z());
			if(!reached(reached,to))return false;
		}
		return true;
	}
	static boolean reached(Vector3f reached,Point target) {
		return reached!=null && Math.hypot(reached.x-target.x(),reached.y-target.y())<.3 && Math.abs(reached.z-target.z())<.3;
	}
	/** Mirrors the native effect's endpoint calculation; the effect remains the only movement owner. */
	private static Vector3f destination(Player bot,Creature target,EffectTemplate effect) {
		var geo=GeoService.getInstance();
		if(effect instanceof RandomMoveLocEffect random) {
			float angle=PositionUtil.convertHeadingToAngle(bot.getHeading());
			return geo.findMovementCollision(bot,random.getDirection()==1?angle+180:angle,random.getDistance());
		}
		if(target==null || target==bot || !target.isSpawned() || target.getWorldId()!=bot.getWorldId() || target.getInstanceId()!=bot.getInstanceId())return null;
		byte heading=PositionUtil.getHeadingTowards(bot,target);
		if(effect instanceof BackDashEffect back)
			return geo.findMovementCollision(bot,PositionUtil.convertHeadingToAngle(heading)+180,back.getDistance());
		double angle=Math.toRadians(PositionUtil.convertHeadingToAngle(effect instanceof MoveBehindEffect?target.getHeading():heading));
		float distance=bot.getObjectTemplate().getBoundRadius().getMaxOfFrontAndSide()+target.getObjectTemplate().getBoundRadius().getMaxOfFrontAndSide()+1;
		float x=target.getX()+(float)Math.cos(Math.PI+angle)*distance,y=target.getY()+(float)Math.sin(Math.PI+angle)*distance;
		return geo.getClosestCollision(effect instanceof DashEffect?target:bot,x,y,target.getZ());
	}
	private PlayerBotSkillPosition() {}
}
