package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.team.*;
import com.aionemu.gameserver.model.team.group.PlayerGroupService;
import com.aionemu.gameserver.model.team.alliance.PlayerAllianceService;

/** Native party/alliance identity, never inventory ownership or an invented parallel team. */
final class PlayerBotTeams {
 static boolean same(Player a,Player b){return a!=null && b!=null && a.getCurrentTeam()!=null && a.getCurrentTeam()==b.getCurrentTeam();}
 /** One read of the team: a party that disbands in between is simply empty, not an error. */
 static List<Player> members(Player p){var team=p.getCurrentTeam();return team==null?List.of():List.copyOf(team.getMembers());}
 static boolean allowed(TeamType type){return type==TeamType.GROUP || type==TeamType.ALLIANCE;}
 static boolean allowed(TemporaryPlayerTeam<?> team){
  if(team instanceof com.aionemu.gameserver.model.team.group.PlayerGroup group)return allowed(group.getTeamType());
  if(team instanceof com.aionemu.gameserver.model.team.alliance.PlayerAlliance alliance)return allowed(alliance.getTeamType());
  return false;
 }
 static void remove(Player p){if(p.isInGroup())PlayerGroupService.removePlayer(p);if(p.isInAlliance())PlayerAllianceService.removePlayer(p);}
 static void restore(Player owner,Player bot) {
  if(same(owner,bot))return;
  remove(bot);
  if(owner.isInAlliance())PlayerAllianceService.addPlayer(owner.getPlayerAlliance(),bot);
  else if(owner.isInGroup())PlayerGroupService.addPlayer(owner.getPlayerGroup(),bot);
  else PlayerGroupService.createGroup(owner,bot,TeamType.GROUP,0);
 }
 private PlayerBotTeams(){}
}
