/* CasterFindTargetSmartStrategy / ComboFindTargetSmartStrategy from pinned
 * mod-playerbots DpsTargetValue.cpp, 037c01418b5d01506917a3db9b44fd56ac5f965c.
 * GPL-2.0-or-later; third-party/playerbots/AUTHORS.md. Native Aion rune effects replace combo targets. */
package com.aionemu.gameserver.services.playerbot;
import java.util.*;
import com.aionemu.gameserver.model.PlayerClass;
import com.aionemu.gameserver.model.gameobjects.*;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.skillengine.effect.EffectType;
import com.aionemu.gameserver.utils.PositionUtil;
import static com.aionemu.gameserver.services.playerbot.PlayerBotRules.*;

final class PlayerBotTargetStrategies {
 record Candidate(int id,boolean commanded,boolean assisted,boolean inRange,double lifetime,double distance,boolean current,boolean runes) {}
 static int interval(boolean inRange,double lifetime) {return (inRange ? 10 : 0)+(lifetime>=5 && lifetime<=30 ? 2 : lifetime>30 ? 0 : 1);}
 static int select(List<Candidate> candidates,boolean combo) {
  Comparator<Candidate> compare=Comparator.comparing(Candidate::commanded).reversed()
   .thenComparing(Comparator.comparing(Candidate::assisted).reversed());
  if(combo)compare=compare.thenComparing(Comparator.comparing(Candidate::inRange).reversed())
   .thenComparing(c->c.inRange() && c.runes() ? 0 : 1)
   .thenComparingDouble(c->c.inRange() ? c.lifetime() : c.distance());
  else compare=compare.thenComparing(Comparator.<Candidate>comparingInt(c->interval(c.inRange(),c.lifetime())).reversed())
   .thenComparing(c->interval(c.inRange(),c.lifetime())%10==1 && c.current() ? 0 : 1)
   .thenComparingDouble(c->interval(c.inRange(),c.lifetime())%10==1 ? -c.lifetime() : c.lifetime());
  return candidates.stream().filter(c->Double.isFinite(c.lifetime()) && c.lifetime()>=0 && Double.isFinite(c.distance()))
   .min(compare.thenComparing(Comparator.comparing(Candidate::current).reversed()).thenComparingInt(Candidate::id))
   .map(Candidate::id).orElse(0);
 }
 static boolean caster(PlayerClass pc,Role role) {
  return role==Role.RANGED && switch(pc){case MAGE,SORCERER,SPIRIT_MASTER,BARD,ARTIST,GUNNER,ENGINEER -> true;default -> false;};
 }
 static double estimatedDps(Player player) {
  var weapon=player.getEquipment().getMainHandWeapon();
  double base=weapon==null || weapon.getItemTemplate().getWeaponStats()==null ? player.getGameStats().getStatsTemplate().getAttack()
   : weapon.getItemTemplate().getWeaponStats().getMeanDamage();
  double best=Math.max(1,base)*1000/Math.max(300,player.getGameStats().getAttackSpeed().getCurrent());
  for(var entry:PlayerBotSkills.read(player)) {
   var skill=PlayerBotSkills.actualTemplate(player,entry);if(skill==null || !PlayerBotOffense.instant(skill) || skill.hasAnyEffect(EffectType.SIGNETBURST))continue;
   best=Math.max(best,PlayerBotRotation.estimatedDamage(skill,entry.level(),base,0)/Math.max(1,1+skill.getDuration()/1000.0));
  }
  return Double.isFinite(best) ? best : 1;
 }
 static Npc choose(Player bot,Player owner,Role role,int commanded,List<Npc> enemies,List<Player> party,List<PlayerBotSkills.Entry> skills) {
  boolean combo=bot.getPlayerClass()==PlayerClass.ASSASSIN && role==Role.MELEE;
  if(!combo && !caster(bot.getPlayerClass(),role))return null;
  var nearby=party.stream().filter(p->!p.isDead() && p.isSpawned() && p.getWorldId()==bot.getWorldId()
   && p.getInstanceId()==bot.getInstanceId() && PositionUtil.isInRange(bot,p,45)).toList();
  // Original source activates these strategies only with more than three nearby group members.
  if(nearby.size()<=3)return null;
  double dps=nearby.stream().mapToDouble(PlayerBotTargetStrategies::estimatedDps).sum();
  float range=Math.max(1.5f,bot.getGameStats().getAttackRange().getCurrent()/1000f);
  if(!combo)range=Math.max(range,skills.stream().filter(e->e.kind()==SkillKind.DAMAGE).map(PlayerBotSkills.Entry::range).max(Float::compare).orElse(range));
  List<Candidate> values=new ArrayList<>();Map<Integer,Npc> targets=new HashMap<>();
  for(var npc:enemies) {
   boolean runes=npc.getEffectController().getAbnormalEffects().stream()
    .anyMatch(e->e.getEffector()==bot && e.getSkillTemplate().hasAnyEffect(EffectType.SIGNET) && e.getSkillLevel()>0);
   values.add(new Candidate(npc.getObjectId(),npc.getObjectId()==commanded,owner.getTarget()==npc,
    PositionUtil.isInAttackRange(bot,npc,range+5),npc.getLifeStats().getCurrentHp()/Math.max(1,dps),
    PositionUtil.getDistance(bot,npc),bot.getTarget()==npc,runes));targets.put(npc.getObjectId(),npc);
  }
  return targets.get(select(values,combo));
 }
 private PlayerBotTargetStrategies() {}
}
