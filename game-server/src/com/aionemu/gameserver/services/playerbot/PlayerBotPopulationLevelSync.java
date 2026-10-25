/* RandomPlayerbotMgr::CheckPlayers, mod-playerbots
 * 037c01418b5d01506917a3db9b44fd56ac5f965c. GPL-2.0-or-later; AUTHORS.md. */
package com.aionemu.gameserver.services.playerbot;

import java.util.Collection;
import com.aionemu.gameserver.model.gameobjects.player.Player;

/** Derived per-run high-water mark, not persistent character progress. */
final class PlayerBotPopulationLevelSync {
 record Observation(int level,boolean bot,boolean gameMaster) {}
 private int limit;private long nextCheck;
 static int observe(int current,Collection<Observation> players) {
  for(var player:players)if(!player.bot() && !player.gameMaster() && player.level()>current)current=player.level()+3;
  return current;
 }
 synchronized int limit(long now,int starting,Collection<Player> players) {
  if(limit==0)limit=starting;
  if(now>=nextCheck){nextCheck=now+60000;limit=observe(limit,players.stream().filter(Player::isOnline)
   .map(p->new Observation(p.getLevel(),p.isPlayerBot(),p.isStaff())).toList());}
  return limit;
 }
}
