/* Threat ability priorities and loose-target recovery adapt TankWarriorStrategy
 * and TankAssistStrategy at the pinned mod-playerbots revision. Native Aion hate,
 * effects and skill admission remain authoritative. GPL-2.0-or-later. */
package com.aionemu.gameserver.services.playerbot;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import com.aionemu.gameserver.model.gameobjects.*;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.stats.container.StatEnum;
import com.aionemu.gameserver.skillengine.effect.*;
import com.aionemu.gameserver.skillengine.model.SkillTemplate;
import com.aionemu.gameserver.utils.PositionUtil;
import static com.aionemu.gameserver.services.playerbot.PlayerBotRules.*;

public final class PlayerBotTank {
 private static final Map<String,Long> OPENINGS=new ConcurrentHashMap<>();
 static double enmity(SkillTemplate skill,int level) {
  if(skill==null || skill.getEffects()==null)return 0;double score=0;
  for(var effect:skill.getEffects().getEffects()) {
   if(effect instanceof HostileUpEffect)score+=Math.max(0,effect.getValue()+effect.getDelta()*level);
   if(PlayerBotQuestConversations.field(effect,"hopType")!=null)score+=Math.max(0,PlayerBotQuestConversations.number(effect,"hopB")+PlayerBotQuestConversations.number(effect,"hopA")*level);
  }return score;
 }
 static boolean hateBuff(SkillTemplate skill){return skill!=null && skill.getEffects()!=null && skill.getEffects().getEffects().stream().anyMatch(e->e.getChange()!=null && e.getChange().stream().anyMatch(c->c.getStat()==StatEnum.BOOST_HATE && c.getValue()>0));}
 static double priority(double nativeThreat,boolean taunt,boolean victimIsTank,int ownHate,int otherHate) {
  if(nativeThreat<=0)return taunt ? 0 : 0;
  if(taunt)return !victimIsTank ? 70 : ownHate<otherHate*1.25 ? 55 : 0;
  return (!victimIsTank || ownHate<otherHate*1.25 || ownHate==0) ? 30+Math.min(20,Math.log1p(nativeThreat)*2) : Math.min(8,Math.log1p(nativeThreat));
 }
 static double priority(Player bot,PlayerBotSkills.Entry entry,Creature target,List<Player> party) {
  if(!(target instanceof Npc npc))return 0;int other=party.stream().filter(p->p!=bot).mapToInt(p->npc.getAggroList().getHate(p)+(p.getSummon()==null ? 0 : npc.getAggroList().getHate(p.getSummon()))).max().orElse(0);
  return priority(enmity(PlayerBotSkills.actualTemplate(bot,entry),entry.level()),entry.kind()==SkillKind.TAUNT,npc.getTarget()==bot,npc.getAggroList().getHate(bot),other);
 }
 static boolean openingPause(Player bot,Npc enemy,List<Player> party,long now) {
  if(enemy==null || enemy.getLifeStats().getHpPercentage()<=10)return false;
  Player tank=party.stream().filter(p->p!=bot && !p.isDead() && p.canAttack() && PlayerBotService.getInstance().combatRole(p)==Role.TANK && PositionUtil.isInRange(p,enemy,30)).findFirst().orElse(null);
  if(tank==null || enemy.getAggroList().getHate(tank)>0)return false;
  if(OPENINGS.size()>512)OPENINGS.entrySet().removeIf(e->now-e.getValue()>60000);
  String key=bot.getObjectId()+":"+enemy.getObjectId();long start=OPENINGS.computeIfAbsent(key,k->now);
  return now-start<1500;
 }
 static void close(Player bot){OPENINGS.keySet().removeIf(k->k.startsWith(bot.getObjectId()+":"));}
 private PlayerBotTank(){}
}
