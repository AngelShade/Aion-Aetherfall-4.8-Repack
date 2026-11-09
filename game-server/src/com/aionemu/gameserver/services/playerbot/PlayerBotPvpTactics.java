package com.aionemu.gameserver.services.playerbot;

import static com.aionemu.gameserver.skillengine.effect.EffectType.*;

import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.skillengine.effect.AbnormalState;
import com.aionemu.gameserver.skillengine.model.SkillTemplate;
import com.aionemu.gameserver.skillengine.model.SkillType;
import com.aionemu.gameserver.skillengine.properties.TargetRelationAttribute;
import com.aionemu.gameserver.utils.PositionUtil;

/**
 * PvP fight craft (owner 8 Nov: "pvp is about movement and skill usage ... if they are smart they should act like
 * that, if they suppose to be stupid they should be"). Wits come from the bot's combat rank, the same rank that sets
 * its mistake chance: POOR 0, ORDINARY 1, GOOD 2, GREAT 3, EXCELLENT 4.
 *
 * <ul>
 * <li>Movement: instant skills no longer plant the feet; ranged bots kite melee, skilled melee circle to the flank.</li>
 * <li>Defense: skilled bots hold defensive skills for real pressure and incoming burst; poor ones panic early.</li>
 * <li>Control: no stun on a stunned target; interrupts come after a reaction time and only if the cast is noticed;
 * skilled bots keep an interrupt for a cast, a kill or a rescue.</li>
 * <li>Damage: burst when it matters (low target, held target, enemy heal); big cooldowns wait for that window;
 * attacks into an active dodge/parry/block or resist effect are avoided.</li>
 * </ul>
 * Pure policy below; {@link #read} takes the visible facts once per decision.
 */
final class PlayerBotPvpTactics {
 /** Combat rank (by its mistake chance) to wits 0..4. A bot without a rank has no mistake chance and plays at 4. */
 static int wits(int mistakePercent) {
  return mistakePercent>=30?0:mistakePercent>=20?1:mistakePercent>=10?2:mistakePercent>=4?3:4;
 }
 /** Milliseconds before a bot reacts to an enemy cast, and the share of casts it notices at all. */
 static final long[] REACTION={1400,900,550,320,180};
 static final int[] NOTICE={35,60,80,95,100};
 /** Casts shorter than this cannot be read and answered. */
 static final int READABLE_CAST_MS=500;

 /** Whether this bot answers this cast now: after its reaction time, before the cast lands, if it noticed it. */
 static boolean interruptNow(int wits,long castStart,int castMs,long now,int bot,int caster) {
  if(castStart<=0 || castMs<READABLE_CAST_MS || now>=castStart+castMs)return false;
  if(now<castStart+REACTION[wits])return false;
  return Math.floorMod(mix(bot,caster,castStart),100)<NOTICE[wits];
 }
 private static long mix(int a,int b,long c) {
  long h=a*0x9E3779B97F4A7C15L^b*0xC2B2AE3D27D4EB4FL^c;h^=h>>>31;h*=0xBF58476D1CE4E5B9L;h^=h>>>29;return h;
 }

 /** A defensive skill on itself: skilled bots wait for pressure and burst, poor ones panic early (pressure or not). */
 static double defense(int wits,double health,boolean pressured,boolean burst) {
  if(!Double.isFinite(health) || health<=0)return 0;
  if(wits<=1)return health<(wits==0?60:50)?PlayerBotEngine.ENCOUNTER:0;
  if(!pressured)return 0;
  if(burst && health<70)return PlayerBotEngine.ENCOUNTER+5;
  return health<(wits>=4?30:wits==3?35:40)?PlayerBotEngine.ENCOUNTER:0;
 }

 /** A control skill (stun, sleep, root, silence...). */
 static double control(int wits,double base,boolean interrupts,boolean casting,boolean answer,boolean held,boolean rootOnly,boolean rooted,
  boolean window,boolean rescue,boolean fleeing) {
  if(base<=0)return base;
  // Overwriting a running stun/sleep wastes it (and its follow-up); a second root on a rooted target too.
  if(wits>=1 && (held && !rootOnly || rootOnly && rooted))return 0;
  if(interrupts && answer)return Math.max(base,PlayerBotEngine.INTERRUPT+8);
  // Reading the cast takes the bot's reaction time: the skilled wait for it instead of firing blind, the rest have
  // not seen it and carry on with their rotation (the native planner rates every visible cast at once).
  if(interrupts && casting && !answer)return wits>=2?0:Math.min(base,PlayerBotEngine.NORMAL);
  if(rootOnly && fleeing && wits>=2)return Math.max(base,PlayerBotEngine.INTERRUPT-4);
  // An interrupt is the skilled player's trump: kept for a cast, a kill or a rescue.
  if(interrupts && wits>=3 && !window && !rescue)return Math.min(base,PlayerBotEngine.NORMAL);
  return base;
 }

 /** A damage skill. */
 static double damage(int wits,double base,boolean interrupts,boolean casting,boolean answer,boolean window,boolean bigCooldown,
  boolean desperate,boolean warded,boolean castTime,boolean kited) {
  if(base<=0)return base;
  double value=base;
  // An unread cast earns no interrupt bonus at any rank; a read one is answered.
  if(interrupts && casting)value=answer?Math.max(value,PlayerBotEngine.INTERRUPT+6):Math.min(value,PlayerBotEngine.HIGH+5);
  // Hitting into Evasion/Parry/Block or a resist charge is wasted; the skilled read the buff.
  if(warded)value*=wits>=3?.25:wits==2?.6:1;
  if(window)value+=3*wits;
  else if(bigCooldown && wits>=3 && !desperate)value=Math.min(value,PlayerBotEngine.DEFAULT);
  // While a melee enemy is on top of a ranged bot, instants keep it moving; a cast plants its feet.
  if(castTime && kited && wits>=2)value*=.5;
  return value;
 }
 /** Burst when it matters: a low or held target, or one healing itself. */
 static boolean window(double targetHealth,boolean held,boolean healing) {
  return targetHealth>0 && targetHealth<=35 || held || healing;
 }
 /** 30 s or longer (template cooldown is in tenths of a second). */
 static boolean bigCooldown(int cooldownTenths){return cooldownTenths>=300;}

 /** Ranged kiting: step out when a melee enemy is closer than this (0: stands its ground). */
 static float kiteDistance(int wits){return switch(wits){case 0->0;case 1->3;case 2->5;case 3->7;default->8;};}
 static double kitePriority(int wits){return wits>=3?PlayerBotEngine.INTERRUPT+3:PlayerBotEngine.MOVE+2;}
 /** Skilled melee circle to an enemy's flank this often while it faces them (0: never). */
 static long strafeEvery(int wits){return wits>=4?3000:wits==3?4500:0;}
 static final double STRAFE_PRIORITY=PlayerBotEngine.MOVE+6;
 /** A step out of reach: from 3 to 7 m, enough to clear the given gap. */
 static float kiteStep(double distance,float wanted){return (float)Math.max(3,Math.min(7,wanted-distance+3));}
 /** Instant skills keep the stride; anything with a cast time or a charge plants the feet natively. */
 static boolean keepsMoving(int castMs,boolean charge){return castMs<=0 && !charge;}

 /** What the bot can see of this fight, once per decision. */
 record Read(int wits,double health,double targetHealth,boolean pressured,boolean burst,boolean casting,boolean answer,boolean healing,
  boolean held,boolean rooted,boolean fleeing,boolean physicalWard,boolean magicalWard,boolean meleeClose,boolean facing) {
  boolean window(){return PlayerBotPvpTactics.window(targetHealth,held,healing);}
  boolean rescue(){return pressured && health<40;}
  boolean warded(SkillTemplate skill){return skill.getType()==SkillType.PHYSICAL?physicalWard:skill.getType()==SkillType.MAGICAL && magicalWard;}
 }
 static Read read(Player bot,Creature target,int wits,float meleeRange,boolean rangedRole,long now) {
  var effects=target.getEffectController();
  boolean held=effects.isInAnyAbnormalState(AbnormalState.ANY_STUN) || effects.isAbnormalSet(AbnormalState.SLEEP)
   || effects.isAbnormalSet(AbnormalState.PARALYZE) || effects.isAbnormalSet(AbnormalState.OPENAERIAL) || effects.isUnderFear();
  var cast=target.getCastingSkill();
  // Instants also set the casting skill for their animation; only a cast with a readable cast time counts.
  boolean casting=cast!=null && cast.getCastDuration()>=READABLE_CAST_MS;
  boolean answer=casting && interruptNow(wits,cast.getCastStartTime(),cast.getCastDuration(),now,bot.getObjectId(),target.getObjectId());
  boolean healing=casting && cast.getSkillTemplate().hasAnyEffect(HEAL,HEALINSTANT,CASEHEAL);
  boolean[] pressure={false,false};
  bot.getKnownList().forEachObject(o->{
   if(!(o instanceof Creature c) || !PlayerBotPvp.playerOwned(c) || c.isDead() || !bot.isEnemy(c) || !PositionUtil.isInRange(bot,c,30))return;
   if(c.getTarget()==bot)pressure[0]=true;
   var incoming=c.getCastingSkill();
   if(incoming!=null && incoming.getFirstTarget()==bot && incoming.getSkillTemplate().getProperties()!=null
    && incoming.getSkillTemplate().getProperties().getTargetRelation()==TargetRelationAttribute.ENEMY){pressure[0]=true;pressure[1]=true;}
  });
  double distance=PositionUtil.getDistance(bot,target);
  boolean melee=target instanceof Player p && !PlayerBotCombatPosition.ranged(p.getPlayerClass(),PlayerBotRules.roleFor(p.getPlayerClass()));
  boolean meleeClose=rangedRole && (melee || target.getTarget()==bot) && distance<kiteDistance(wits);
  boolean fleeing=!rangedRole && target.getMoveController().isInMove() && target.getTarget()!=bot && distance>meleeRange+1.5;
  boolean physical=effects.hasAbnormalEffect(e->e.getSkillTemplate()!=null && e.getSkillTemplate().hasAnyEffect(ALWAYSDODGE,ALWAYSPARRY,ALWAYSBLOCK));
  boolean magical=effects.hasAbnormalEffect(e->e.getSkillTemplate()!=null && e.getSkillTemplate().hasAnyEffect(ALWAYSRESIST));
  return new Read(wits,PlayerBotDefense.health(bot),PlayerBotDefense.health(target),pressure[0],pressure[1],casting,answer,healing,
   held,effects.isAbnormalSet(AbnormalState.ROOT),fleeing,physical,magical,meleeClose,target.getTarget()==bot && !PositionUtil.isBehind(bot,target));
 }
 /** Whether a control skill only roots or snares (it cannot interrupt and does not stop the enemy acting). */
 static boolean rootOnly(SkillTemplate skill){return skill.hasAnyEffect(ROOT,SNARE) && !PlayerBotSkills.canInterrupt(skill);}

 /** A point on the far side of the bot from the enemy. */
 static float[] away(float x,float y,float ex,float ey,byte heading,float step) {
  double dx=x-ex,dy=y-ey,length=Math.hypot(dx,dy);
  if(length<.01){dx=Math.cos(heading*Math.PI/60);dy=Math.sin(heading*Math.PI/60);length=1;}
  return new float[]{x+(float)(dx/length*step),y+(float)(dy/length*step)};
 }
 /** The bot's position turned round the enemy by about 70 degrees, left or right, at the same reach. */
 static float[] flank(float x,float y,float ex,float ey,float radius,boolean left) {
  double angle=Math.atan2(y-ey,x-ex)+(left?1.22:-1.22);
  return new float[]{ex+(float)(Math.cos(angle)*radius),ey+(float)(Math.sin(angle)*radius)};
 }

 private PlayerBotPvpTactics(){}
}
