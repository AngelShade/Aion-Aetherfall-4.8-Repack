package com.aionemu.gameserver.services.playerbot;

import java.util.List;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.skillengine.properties.FirstTargetAttribute;
import com.aionemu.gameserver.utils.PositionUtil;
import com.aionemu.gameserver.world.geo.GeoService;

/** Spell-specific support eligibility before choosing the most urgent recipient.
 * Preserves PartyMemberToHeal's reachable/visible target purpose using native
 * Aion target, resource and effect conditions. Planning never casts or pays costs.
 */
final class PlayerBotSupport {
 static List<Player> allowed(Player bot, PlayerBotSkills.Entry entry, List<Player> party) {
  var candidates=entry.template().getProperties().getFirstTarget()==FirstTargetAttribute.ME ? List.of(bot) : party;
  return candidates.stream().filter(p -> p.isSpawned() && p.getWorldId()==bot.getWorldId() && p.getInstanceId()==bot.getInstanceId()
   && (p==bot || PositionUtil.isInRange(bot,p,Math.max(40,entry.range()*2)) && GeoService.getInstance().canSee(bot,p))
   && PlayerBotSkills.canPlan(bot,entry,p,false)).toList();
 }
 private PlayerBotSupport(){}
}
