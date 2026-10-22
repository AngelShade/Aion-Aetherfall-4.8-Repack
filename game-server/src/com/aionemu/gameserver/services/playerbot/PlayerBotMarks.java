/* Purpose adapted from mod-playerbots 037c01418b5d01506917a3db9b44fd56ac5f965c:
 * RtiTargetValue.cpp / RtiCcValue.cpp (raid target icons for kill and crowd-control
 * targets), PullStrategy.cpp and the tank "pull" actions (tag from range, return to
 * the pull position, let the group wait for threat). GPL-2.0-or-later; upstream
 * contributors: third-party/playerbots/AUTHORS.md. Aion has no native raid icons:
 * marks are owner commands kept per owner, and native hate decides who is tagged. */
package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import com.aionemu.gameserver.configs.main.PlayerBotConfig;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.skillengine.effect.AbnormalState;
import com.aionemu.gameserver.skillengine.effect.EffectType;
import com.aionemu.gameserver.skillengine.model.SkillTemplate;
import com.aionemu.gameserver.utils.PositionUtil;
import static com.aionemu.gameserver.services.playerbot.PlayerBotRules.*;

/**
 * Kill mark (WoW skull): the party's preferred target among enemies already in the fight.
 * Crowd-control mark (WoW moon): nobody damages it while other enemies remain; a
 * companion with a long native control (sleep, paralysis, fear) keeps it controlled.
 * Pull: the tank tags the selected monster, preferably from range, and brings it back to
 * the position the owner held when the pull was ordered; the rest of the party waits there.
 */
public final class PlayerBotMarks {

 static final class Pull {
  final Npc npc;final int tank;final PlayerBotNavigation.Point spot;final long started;
  volatile long taggedAt;
  Pull(Npc npc,int tank,PlayerBotNavigation.Point spot,long started){this.npc=npc;this.tank=tank;this.spot=spot;this.started=started;}
  /** Native hate on the tank (or its pet) means the monster is tagged. */
  boolean tagged(Player tank,long now) {
   if(taggedAt==0 && (npc.getAggroList().isHating(tank) || tank.getSummon()!=null && npc.getAggroList().isHating(tank.getSummon())))taggedAt=now;
   return taggedAt!=0;
  }
 }
 private static final class Marks { volatile Npc kill, cc; volatile long ccFreeSince; volatile Pull pull; }
 private static final Map<Integer,Marks> MARKS=new ConcurrentHashMap<>();

 private static boolean alive(Npc npc){return npc!=null && npc.isSpawned() && !npc.isDead();}
 static boolean longControl(SkillTemplate skill){return skill!=null && skill.hasAnyEffect(EffectType.SLEEP,EffectType.PARALYZE,EffectType.FEAR);}
 static boolean controlled(Npc npc) {
  return npc.getEffectController().isAbnormalSet(AbnormalState.SLEEP) || npc.getEffectController().isUnderFear()
   || npc.getEffectController().isAbnormalSet(AbnormalState.PARALYZE);
 }

 /** Owner command: kill, cc or clear on the owner's selected monster. Returns the message for the owner. */
 public static String mark(Player owner,List<PlayerBotSession> companions,String kind) {
  if(!PlayerBotConfig.MARKS)throw new IllegalArgumentException("Companion target marks are disabled on this server.");
  if(kind.equals("clear")){MARKS.remove(owner.getObjectId());return "Companion target marks cleared.";}
  if(!(owner.getTarget() instanceof Npc npc) || !alive(npc) || !owner.isEnemy(npc))throw new IllegalArgumentException("Select a hostile monster to mark.");
  if(!PositionUtil.isInRange(owner,npc,45))throw new IllegalArgumentException("The marked monster must be within 45m of you.");
  var marks=MARKS.computeIfAbsent(owner.getObjectId(),id->new Marks());
  switch(kind) {
   case "kill" -> {if(marks.cc==npc)marks.cc=null;marks.kill=npc;return "Kill mark: companions focus "+npc.getName()+" once it is in the fight.";}
   case "cc" -> {
    var controller=companions.stream().filter(s->!s.bot().isDead()).filter(s->PlayerBotSkills.read(s.bot()).stream()
     .anyMatch(e->e.kind()==SkillKind.CONTROL && longControl(e.template()))).map(s->s.bot().getName()).findFirst().orElse(null);
    if(controller==null)throw new IllegalArgumentException("None of your companions has a sleep, paralysis or fear skill to hold a crowd-control mark.");
    if(marks.kill==npc)marks.kill=null;marks.cc=npc;marks.ccFreeSince=0;
    return "Crowd-control mark: companions leave "+npc.getName()+" alone and "+controller+" keeps it controlled.";
   }
   default -> throw new IllegalArgumentException("Use .bot mark kill|cc|clear.");
  }
 }

 /** Current kill mark object ID for this owner's companions, or 0. */
 static int kill(Player owner) {
  var marks=PlayerBotConfig.MARKS ? MARKS.get(owner.getObjectId()) : null;if(marks==null)return 0;
  var npc=marks.kill;if(npc==null)return 0;
  if(!alive(npc)){marks.kill=null;return 0;}
  return npc.getObjectId();
 }
 static boolean ccMarked(Player owner,Npc npc) {
  var marks=PlayerBotConfig.MARKS ? MARKS.get(owner.getObjectId()) : null;if(marks==null || marks.cc!=npc)return false;
  if(!alive(npc)){marks.cc=null;return false;}
  return true;
 }
 /** Leave a crowd-control mark alone while it is not the last enemy and its control has not failed for too long. */
 static boolean leaveAlone(Player owner,Npc npc,int enemies,long now) {
  if(enemies<=1 || !ccMarked(owner,npc))return false;
  var marks=MARKS.get(owner.getObjectId());if(marks==null)return false;
  if(controlled(npc) || npc.getAggroList().stream().findAny().isEmpty()){marks.ccFreeSince=0;return true;}
  if(marks.ccFreeSince==0)marks.ccFreeSince=now;
  return now-marks.ccFreeSince<PlayerBotConfig.MARKS_CC_RELEASE_MS;
 }
 /** A free crowd-control mark that a long control skill should (re)apply now. */
 static boolean keepControlled(Player owner,Npc npc,SkillTemplate skill) {
  return longControl(skill) && ccMarked(owner,npc) && !controlled(npc);
 }
 /** The free crowd-control mark, if any, for control recipient selection. */
 static Npc controlTarget(Player owner) {
  var marks=PlayerBotConfig.MARKS ? MARKS.get(owner.getObjectId()) : null;if(marks==null)return null;
  var npc=marks.cc;if(npc==null)return null;
  if(!alive(npc)){marks.cc=null;return null;}
  return controlled(npc) ? null : npc;
 }

 /** Owner command: the tank companion tags the selected monster and brings it back here. */
 public static String pull(Player owner,List<PlayerBotSession> companions) {
  if(!PlayerBotConfig.TANK_PULL)throw new IllegalArgumentException("Companion pulls are disabled on this server.");
  if(!(owner.getTarget() instanceof Npc npc) || !alive(npc) || !owner.isEnemy(npc))throw new IllegalArgumentException("Select a hostile monster to pull.");
  if(!PositionUtil.isInRange(owner,npc,45))throw new IllegalArgumentException("The monster to pull must be within 45m of you.");
  if(npc.getAggroList().stream().findAny().isPresent())throw new IllegalArgumentException(npc.getName()+" is already fighting; use .bot attack instead.");
  var tank=companions.stream().filter(s->!s.bot().isDead() && s.bot().getWorldId()==owner.getWorldId() && s.bot().getInstanceId()==owner.getInstanceId()
   && PlayerBotService.getInstance().combatRole(s.bot())==Role.TANK).min(Comparator.comparingDouble(s->PositionUtil.getDistance(s.bot(),npc))).orElse(null);
  if(tank==null)throw new IllegalArgumentException("No tank companion here. Set one with .bot role <name> tank.");
  tank.pullSelectedTarget(npc);
  MARKS.computeIfAbsent(owner.getObjectId(),id->new Marks()).pull=new Pull(npc,tank.bot().getObjectId(),
   new PlayerBotNavigation.Point(owner.getX(),owner.getY(),owner.getZ()),System.currentTimeMillis());
  return tank.bot().getName()+" is pulling "+npc.getName()+" back to you; the others wait here.";
 }
 /** The live pull for this owner, ended when it expires or the monster is gone. */
 static Pull pull(Player owner,long now) {
  var marks=MARKS.get(owner.getObjectId());if(marks==null)return null;
  var pull=marks.pull;if(pull==null)return null;
  if(!PlayerBotConfig.TANK_PULL){marks.pull=null;return null;}
  if(!alive(pull.npc) || pull.taggedAt==0 && now-pull.started>PlayerBotConfig.TANK_PULL_REACH_MS || pull.taggedAt!=0 && now-pull.taggedAt>PlayerBotConfig.TANK_PULL_RETURN_MS){marks.pull=null;return null;}
  return pull;
 }
 static void endPull(Player owner,Pull pull){var marks=MARKS.get(owner.getObjectId());if(marks!=null && marks.pull==pull)marks.pull=null;}
 static void close(Player owner){MARKS.remove(owner.getObjectId());}
 private PlayerBotMarks(){}
}
