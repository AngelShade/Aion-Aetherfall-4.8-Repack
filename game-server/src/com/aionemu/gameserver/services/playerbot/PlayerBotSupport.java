package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import com.aionemu.gameserver.model.gameobjects.Creature;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.skillengine.properties.*;
import com.aionemu.gameserver.utils.PositionUtil;
import com.aionemu.gameserver.world.geo.GeoService;

/** Spell-specific support eligibility before choosing the most urgent recipient.
 * Preserves PartyMemberToHeal's reachable/visible target purpose using native
 * Aion target, resource and effect conditions. Planning never casts or pays costs.
 */
final class PlayerBotSupport {
 /** Nearby public spell assistance is an Aion extension; it never extends attack or loot rights. */
 static List<Player> members(Player bot) { return members(bot,PlayerBotTeams.members(bot)); }
 static List<Player> members(Player bot,List<Player> party) {
  var result=new LinkedHashSet<Player>();result.add(bot);
  party.stream().filter(p->p==bot || PlayerBotTeams.same(bot,p)).filter(p->eligible(bot,p)).forEach(result::add);
  var nearby=new ArrayList<Player>();var known=bot.getKnownList();
  if(known!=null)known.forEachPlayer(p->{if(!result.contains(p) && eligible(bot,p) && PositionUtil.isInRange(bot,p,40))nearby.add(p);});
  nearby.sort(Comparator.<Player>comparingDouble(PlayerBotHealing::health).thenComparingDouble(p->PositionUtil.getDistance(bot,p)).thenComparingInt(Player::getObjectId));
  nearby.stream().limit(16).forEach(result::add);
  return List.copyOf(result);
 }
 static boolean eligible(Player bot,Player target) {
  return target!=null && target.isSpawned() && target.isPlaying() && target.getWorldId()==bot.getWorldId() && target.getInstanceId()==bot.getInstanceId()
   && !bot.isEnemy(target) && !target.isEnemy(bot) && TargetRelationProperty.isBuffAllowed(bot,target)
   && (target==bot || PlayerBotTeams.same(bot,target) || bot.getRace()==target.getRace());
 }
 /** Used again by native skill admission/effect application, independently of a planning snapshot. */
 static boolean effectAllowed(Player bot,Creature target) {
  if(target==bot || target.getMaster()==bot)return true;
  if(!(target.getMaster() instanceof Player player) || !eligible(bot,player) || !target.isSpawned()
   || target.getWorldId()!=bot.getWorldId() || target.getInstanceId()!=bot.getInstanceId())return false;
  var known=bot.getKnownList();
  return known!=null && known.sees(target) && (PlayerBotTeams.same(bot,player) || PositionUtil.isInRange(bot,target,40));
 }
 /** Match native FirstTargetProperty without its target fallback or packet side effects. */
 static boolean legalAnchor(Player bot,PlayerBotSkills.Entry entry,Creature target) {
  var p=entry.template().getProperties();var first=p.getFirstTarget();
  if(first==FirstTargetAttribute.ME || first==FirstTargetAttribute.PASSIVE)return target==bot;
  if(first==FirstTargetAttribute.MYPET)return target==bot.getSummon();
  if(first!=FirstTargetAttribute.TARGET && first!=FirstTargetAttribute.TARGETORME)return false;
  if(bot.isEnemy(target) || !TargetRelationProperty.isBuffAllowed(bot,target))return false;
  if(p.getTargetType()==TargetRangeAttribute.PARTY || p.getTargetType()==TargetRangeAttribute.PARTY_WITHPET) {
   var team=p.getTargetType()==TargetRangeAttribute.PARTY ? bot.getCurrentGroup() : bot.getCurrentTeam();
   if(target!=bot && (team==null || !team.getMembers().contains(target)))return false;
  }
  if(p.getTargetRelation()==TargetRelationAttribute.MYPARTY)
   return target==bot ? first==FirstTargetAttribute.TARGETORME : target instanceof Player player && PlayerBotTeams.same(bot,player);
  return p.getTargetRelation()!=TargetRelationAttribute.ENEMY
   && !(target==bot && first==FirstTargetAttribute.TARGET && p.getTargetType()==TargetRangeAttribute.PARTY);
 }
 static List<Player> allowed(Player bot, PlayerBotSkills.Entry entry, List<Player> party) {
  var candidates=entry.template().getProperties().getFirstTarget()==FirstTargetAttribute.ME ? List.of(bot) : party;
  return candidates.stream().filter(p -> p.isSpawned() && p.getWorldId()==bot.getWorldId() && p.getInstanceId()==bot.getInstanceId()
   && (p==bot || PositionUtil.isInRange(bot,p,Math.max(40,entry.range()*2)) && GeoService.getInstance().canSee(bot,p))
   && legalAnchor(bot,entry,p) && PlayerBotSkills.canPlan(bot,entry,p,false)).toList();
 }
 private PlayerBotSupport(){}
}
