/* GeneralFindTargetSmartStrategy and FindTankTargetSmartStrategy adapted from
 * mod-playerbots DpsTargetValue.cpp/TankTargetValue.cpp, revision
 * 037c01418b5d01506917a3db9b44fd56ac5f965c. GPL-2.0-or-later.
 * Aion native ranges, actual hate and party/pet ownership replace WoW APIs.
 */
package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import com.aionemu.gameserver.model.gameobjects.*;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.utils.PositionUtil;
import static com.aionemu.gameserver.services.playerbot.PlayerBotRules.*;

public final class PlayerBotTargetValues {
 record Candidate(int id,boolean commanded,boolean assisted,boolean inRange,
                  int tankInterval,int hate,int health,double distance,double rescueHealth,boolean current) {}

 // Called only with the session's already admitted enemies. This never expands
 // the pull set or bypasses crowd control, instance, owner distance or quest rules.
 static Npc choose(Player bot,Player owner,Role role,int commanded,List<Npc> enemies,List<Player> party,List<PlayerBotSkills.Entry> skills) {
  if(role!=Role.TANK) {
   var specialized=PlayerBotTargetStrategies.choose(bot,owner,role,commanded,enemies,party,skills);
   if(specialized!=null)return specialized;
  }
  double range=Math.max(1.5,bot.getGameStats().getAttackRange().getCurrent()/1000.0);
  if(role!=Role.TANK && role!=Role.MELEE)
   for(var skill:skills)if(skill.kind()==SkillKind.DAMAGE)range=Math.max(range,skill.range());
  List<Candidate> candidates=new ArrayList<>();Map<Integer,Npc> byId=new HashMap<>();
  for(var npc:enemies) {
   boolean inRange=PositionUtil.isInAttackRange(bot,npc,(float)range);
   var victim=npc.getTarget() instanceof Creature c ? c : null;
   var master=victim!=null && victim.getMaster() instanceof Player p ? p : null;
   boolean otherTank=master!=null && master!=bot && party.contains(master) && PlayerBotService.getInstance().combatRole(master)==Role.TANK;
   double rescue=master!=null && master!=bot && party.contains(master) && !otherTank
    ? 100.0*master.getLifeStats().getCurrentHp()/Math.max(1,master.getLifeStats().getMaxHp()) : Double.POSITIVE_INFINITY;
   int interval=otherTank ? -1 : victim!=bot ? 2 : PositionUtil.isInAttackRange(bot,npc,Math.max(1.5f,bot.getGameStats().getAttackRange().getCurrent()/1000f)) ? 1 : 0;
   candidates.add(new Candidate(npc.getObjectId(),npc.getObjectId()==commanded,owner.getTarget()==npc,inRange,interval,
    npc.getAggroList().getHate(bot),npc.getLifeStats().getCurrentHp(),PositionUtil.getDistance(bot,npc),rescue,bot.getTarget()==npc));byId.put(npc.getObjectId(),npc);
  }
  int id=select(candidates,role==Role.TANK);return byId.get(id);
 }

 static int select(List<Candidate> candidates,boolean tank) {
  Comparator<Candidate> compare;
  if(tank)compare=Comparator.comparingDouble(Candidate::rescueHealth)
   .thenComparing(Comparator.comparingInt(Candidate::tankInterval).reversed())
   .thenComparing(Comparator.comparing(Candidate::commanded).reversed())
   .thenComparing(Comparator.comparing(Candidate::assisted).reversed())
   .thenComparingDouble(c->c.tankInterval()==2 ? c.distance() : c.hate())
   .thenComparing(Comparator.comparing(Candidate::current).reversed());
  else compare=Comparator.comparing(Candidate::commanded).reversed()
   .thenComparing(Comparator.comparing(Candidate::assisted).reversed())
   .thenComparing(Comparator.comparing(Candidate::inRange).reversed())
   .thenComparingDouble(c->c.inRange() ? c.health() : c.distance())
   .thenComparing(Comparator.comparing(Candidate::current).reversed());
  return candidates.stream().min(compare.thenComparingInt(Candidate::id)).map(Candidate::id).orElse(0);
 }
 private PlayerBotTargetValues(){}
}
