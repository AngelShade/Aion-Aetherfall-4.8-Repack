package com.aionemu.gameserver.services.playerbot;

import java.util.List;
import java.util.Map;
import com.aionemu.gameserver.configs.main.PlayerBotConfig;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.player.Player;

/** PvP-3 Kamar Battlefield, a hook of its own (its own switch, target line, travel step and report): non-PvP bots play kills and supplies, the capture points (each spawns your flag) and the Kamar commander when its 3,500 points win it.
 * The table mirrors the map handler (data/handlers/instance/pvp); PlayerBotBattlegroundMapsCheck pins it there. */
final class PlayerBotKamar {
 static final PlayerBotBattlegroundPlay.Field FIELD=new PlayerBotBattlegroundPlay.Field(301120000,"Kamar Battlefield",
  Map.ofEntries(Map.entry(232856,1250),Map.entry(232855,1250),Map.entry(232852,1250),Map.entry(701807,225),Map.entry(701808,225),Map.entry(701911,225),Map.entry(701912,225),Map.entry(232847,140),Map.entry(232848,140),Map.entry(232849,140),Map.entry(232850,140),Map.entry(232851,140),Map.entry(233261,140),Map.entry(233260,50),Map.entry(232841,50),Map.entry(232842,50),Map.entry(232843,50),Map.entry(232844,50),Map.entry(232845,50),Map.entry(232846,50),Map.entry(801771,75)),
  Map.ofEntries(Map.entry(801903,1500),Map.entry(801772,525),Map.entry(801766,255),Map.entry(801767,255),Map.entry(801818,255),Map.entry(801819,255),Map.entry(801820,255),Map.entry(801821,255),Map.entry(730861,200),Map.entry(730878,200),Map.entry(730879,200),Map.entry(730880,200)),
  232853,3500,false);
 static final PlayerBotBattlegroundPlay.Stats STATS=new PlayerBotBattlegroundPlay.Stats();
 static boolean enabled(){return PlayerBotConfig.PVP_ENABLED && PlayerBotConfig.PVP_BATTLEGROUNDS && PlayerBotConfig.PVP_KAMAR;}
 static boolean mechanicEnemy(Player bot,Npc npc,List<Player> party){return enabled() && PlayerBotBattlegroundPlay.mechanicEnemy(FIELD,STATS,bot,npc);}
 static boolean travel(PlayerBotSession session,PlayerBotNavigation navigation,long now){return PlayerBotBattlegroundPlay.travel(FIELD,STATS,enabled(),session,navigation,now);}
 static String stats(){return STATS.line(enabled());}
 private PlayerBotKamar(){}
}
