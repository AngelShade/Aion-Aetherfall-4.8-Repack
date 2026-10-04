package com.aionemu.gameserver.services.playerbot;

import java.util.List;
import java.util.Comparator;
import com.aionemu.gameserver.model.PlayerClass;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.utils.PositionUtil;
import static com.aionemu.gameserver.services.playerbot.PlayerBotRules.*;

/** One range policy for combat movement and hostile spell prerequisites. */
public final class PlayerBotCombatPosition {
 static List<Integer> formationOrder(Player owner) {
  return PlayerBotService.getInstance().companions(owner).stream()
   .sorted(Comparator.comparingInt((PlayerBotSession s) -> s.combatRole()==Role.TANK ? 0 : s.combatRole()==Role.MELEE ? 1 : 2)
    .thenComparingInt(s -> s.bot().getObjectId())).map(s -> s.bot().getObjectId()).toList();
 }
 static boolean ranged(PlayerClass pc, Role role) {
  return role == Role.RANGED || role == Role.HEALER || role == Role.SUPPORT && pc != PlayerClass.CHANTER;
 }
 static float desired(PlayerClass pc, Role role, float weapon, List<PlayerBotSkills.Entry> skills) {
  float nativeDistance=PlayerBotSpacing.nativeDistance(pc,role,weapon,skills);
  return ranged(pc,role)?Math.min(10,nativeDistance):nativeDistance;
 }
 static float desired(Player bot, Role role, List<PlayerBotSkills.Entry> skills) {
  float nativeDistance=PlayerBotSpacing.nativeDistance(bot.getPlayerClass(),role,bot.getGameStats().getAttackRange().getCurrent()/1000f,skills);
  return ranged(bot.getPlayerClass(),role)?PlayerBotSpacing.attack(bot,nativeDistance):nativeDistance;
 }
 static boolean allowSpellApproach(Player bot, Role role, PlayerBotSkills.Entry entry, Creature target, List<PlayerBotSkills.Entry> skills) {
  if (!ranged(bot.getPlayerClass(),role) || !(target instanceof Npc) || !bot.isEnemy(target)) return true;
  // Short hostile utility remains usable when an enemy comes close. It must not
  // drag a ranged build forward; friendly recovery/resurrection keeps its range.
  return entry.range()>=Math.min(8,desired(bot,role,skills))
   || PositionUtil.isInRange(bot,target,entry.range(),false);
 }
 static boolean tooClose(Player bot,Creature target,float desired) {
  return PlayerBotSpacing.canRetreat(bot,target,desired);
 }
 static PlayerBotNavigation.Point point(float x,float y,float z,float tx,float ty,float tz,float radius,byte heading,boolean flying) {
  double dx=x-tx,dy=y-ty,dz=flying ? z-tz : 0,length=Math.sqrt(dx*dx+dy*dy+dz*dz);
  if(length<.01){dx=-Math.cos(Math.toRadians(heading*3));dy=-Math.sin(Math.toRadians(heading*3));dz=0;length=1;}
  float r=Math.max(.5f,radius);
  return new PlayerBotNavigation.Point(tx+(float)(dx/length*r),ty+(float)(dy/length*r),flying ? tz+(float)(dz/length*r) : z);
 }
 static PlayerBotNavigation.Point point(Creature bot,Creature target,float range) {
  float radius=Math.max(.5f,range-.35f)+bot.getObjectTemplate().getBoundRadius().getMaxOfFrontAndSide()
   +target.getObjectTemplate().getBoundRadius().getMaxOfFrontAndSide();
  // On the ground, retain reachable ground movement; never path to a mob's model
  // center/height. In flight the range shell includes the vertical separation.
  return point(bot.getX(),bot.getY(),bot.getZ(),target.getX(),target.getY(),target.getZ(),radius,bot.getHeading(),bot.isFlying());
 }
 private PlayerBotCombatPosition() {}
}

