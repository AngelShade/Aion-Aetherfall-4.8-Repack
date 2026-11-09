package com.aionemu.gameserver.services.playerbot;

import java.util.List;
import java.util.Map;
import com.aionemu.gameserver.configs.main.PlayerBotConfig;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.player.Player;

/** PvP-3 Iron Wall Warfront, a hook of its own (its own switch, target line, travel step and report): non-PvP bots play generators and the roaming boss, supplies and capture points, and Pashid when his 30,000 points win it.
 * The table mirrors the map handler (data/handlers/instance/pvp); PlayerBotBattlegroundMapsCheck pins it there. */
final class PlayerBotIronWall {
 static final PlayerBotBattlegroundPlay.Field FIELD=new PlayerBotBattlegroundPlay.Field(301220000,"Iron Wall Warfront",
  Map.ofEntries(Map.entry(233473,100),Map.entry(702042,500),Map.entry(233491,5000),Map.entry(701943,5000),Map.entry(701944,5000),Map.entry(701945,5000),Map.entry(233494,30000)),
  Map.ofEntries(Map.entry(801903,1500),Map.entry(801772,525),Map.entry(801766,255),Map.entry(801767,255),Map.entry(801818,255),Map.entry(801819,255),Map.entry(801820,255),Map.entry(801821,255),Map.entry(730861,200),Map.entry(730878,200),Map.entry(730879,200),Map.entry(730880,200)),
  233544,30000,false);
 static final PlayerBotBattlegroundPlay.Stats STATS=new PlayerBotBattlegroundPlay.Stats();
 static boolean enabled(){return PlayerBotConfig.PVP_ENABLED && PlayerBotConfig.PVP_BATTLEGROUNDS && PlayerBotConfig.PVP_IRON_WALL;}
 static boolean mechanicEnemy(Player bot,Npc npc,List<Player> party){return enabled() && PlayerBotBattlegroundPlay.mechanicEnemy(FIELD,STATS,bot,npc);}
 static boolean travel(PlayerBotSession session,PlayerBotNavigation navigation,long now){return PlayerBotBattlegroundPlay.travel(FIELD,STATS,enabled(),session,navigation,now);}
 static String stats(){return STATS.line(enabled());}
 private PlayerBotIronWall(){}
}
