package com.aionemu.gameserver.services.playerbot;

import java.util.List;
import java.util.Map;
import com.aionemu.gameserver.configs.main.PlayerBotConfig;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.player.Player;

/** PvP-3 Idgel Dome, a hook of its own (its own switch, target line, travel step and report): non-PvP bots play the arena guards and Kunax when his 6,000 points win it.
 * The table mirrors the map handler (data/handlers/instance/pvp); PlayerBotBattlegroundMapsCheck pins it there. */
final class PlayerBotIdgelDome {
 static final PlayerBotBattlegroundPlay.Field FIELD=new PlayerBotBattlegroundPlay.Field(301310000,"Idgel Dome",
  Map.ofEntries(Map.entry(234186,120),Map.entry(234187,120),Map.entry(234189,120),Map.entry(234751,200),Map.entry(234752,200),Map.entry(234753,200)),
  Map.of(),
  287249,6000,false);
 static final PlayerBotBattlegroundPlay.Stats STATS=new PlayerBotBattlegroundPlay.Stats();
 static boolean enabled(){return PlayerBotConfig.PVP_ENABLED && PlayerBotConfig.PVP_BATTLEGROUNDS && PlayerBotConfig.PVP_IDGEL;}
 static boolean mechanicEnemy(Player bot,Npc npc,List<Player> party){return enabled() && PlayerBotBattlegroundPlay.mechanicEnemy(FIELD,STATS,bot,npc);}
 static boolean travel(PlayerBotSession session,PlayerBotNavigation navigation,long now){return PlayerBotBattlegroundPlay.travel(FIELD,STATS,enabled(),session,navigation,now);}
 static String stats(){return STATS.line(enabled());}
 private PlayerBotIdgelDome(){}
}
