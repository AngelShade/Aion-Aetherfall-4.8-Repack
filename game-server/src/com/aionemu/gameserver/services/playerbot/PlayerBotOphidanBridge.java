package com.aionemu.gameserver.services.playerbot;

import java.util.List;
import java.util.Map;
import com.aionemu.gameserver.configs.main.PlayerBotConfig;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.player.Player;

/** PvP-3 Engulfed Ophidan Bridge, a hook of its own (its own switch, target line, travel step and report): non-PvP bots play Balaur kills and the power generators: capturing an enemy or Balaur generator is worth 5,000.
 * The table mirrors the map handler (data/handlers/instance/pvp); PlayerBotBattlegroundMapsCheck pins it there. */
final class PlayerBotOphidanBridge {
 static final PlayerBotBattlegroundPlay.Field FIELD=new PlayerBotBattlegroundPlay.Field(301210000,"Engulfed Ophidan Bridge",
  Map.ofEntries(Map.entry(233473,100),Map.entry(233856,100),Map.entry(233484,200),Map.entry(233485,200),Map.entry(233486,200),Map.entry(233481,200),Map.entry(233482,200),Map.entry(233476,200),Map.entry(233475,200),Map.entry(233479,200),Map.entry(233478,200),Map.entry(233477,300),Map.entry(233483,300),Map.entry(233487,300),Map.entry(233480,300),Map.entry(702042,500),Map.entry(233492,2000),Map.entry(233493,2000),Map.entry(701945,5000),Map.entry(233494,30000)),
  Map.ofEntries(Map.entry(701943,5000),Map.entry(701944,5000),Map.entry(701945,5000)),
  0,0,true);
 static final PlayerBotBattlegroundPlay.Stats STATS=new PlayerBotBattlegroundPlay.Stats();
 static boolean enabled(){return PlayerBotConfig.PVP_ENABLED && PlayerBotConfig.PVP_BATTLEGROUNDS && PlayerBotConfig.PVP_OPHIDAN;}
 static boolean mechanicEnemy(Player bot,Npc npc,List<Player> party){return enabled() && PlayerBotBattlegroundPlay.mechanicEnemy(FIELD,STATS,bot,npc);}
 static boolean travel(PlayerBotSession session,PlayerBotNavigation navigation,long now){return PlayerBotBattlegroundPlay.travel(FIELD,STATS,enabled(),session,navigation,now);}
 static String stats(){return STATS.line(enabled());}
 private PlayerBotOphidanBridge(){}
}
