/* ArmsWarriorStrategy.cpp, FuryWarriorStrategy.cpp and WarriorTriggers.h at
 * mod-playerbots 037c01418b5d01506917a3db9b44fd56ac5f965c: available reactive
 * Overpower/Victory Rush precede ordinary fillers. GPL-2.0-or-later;
 * third-party/playerbots/AUTHORS.md. Native Aion counter events replace WoW procs. */
package com.aionemu.gameserver.services.playerbot;

import com.aionemu.gameserver.model.PlayerClass;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.skillengine.model.SkillType;
import com.aionemu.gameserver.skillengine.properties.*;
import static com.aionemu.gameserver.services.playerbot.PlayerBotRules.*;

/** Use a native reactive opportunity without inventing, extending or consuming its event. */
final class PlayerBotGladiator {
 static String strategy(PlayerClass pc) { return pc==PlayerClass.GLADIATOR ? "gladiator counters" : PlayerBotTemplar.strategy(pc); }
 /** NaN preserves every unrelated class/skill on its installed offensive path. */
 static double counter(Player bot,PlayerBotSkills.Entry entry,double fitness) {
  var skill=entry.template();var p=skill.getProperties();var event=skill.getCounterSkill();
  if(bot.getPlayerClass()!=PlayerClass.GLADIATOR || entry.kind()!=SkillKind.DAMAGE || event==null
   || skill.isCharge() || skill.isPassive() || skill.getType()!=SkillType.PHYSICAL || !PlayerBotOffense.instant(skill)
   || p==null || p.getTargetRelation()!=TargetRelationAttribute.ENEMY || p.getTargetType()!=TargetRangeAttribute.ONLYONE)return Double.NaN;
  // The exact installed canPlan/Skill gate is five seconds after the native event.
  // Final CastAction rechecks this after priorities/prerequisites are resolved.
  if(bot.getLastCounterSkill(event)+5000<System.currentTimeMillis())return 0;
  // Aion's existing ordinary chain band can reach 30. Keep the reactive window
  // just above it, while existing interrupts, threat and recovery remain higher.
  return PlayerBotEngine.MOVE+1+Math.clamp(Double.isFinite(fitness)?fitness:0,0,7)/100;
 }
 private PlayerBotGladiator() {}
}
