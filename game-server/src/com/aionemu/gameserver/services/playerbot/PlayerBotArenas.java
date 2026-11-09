package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import com.aionemu.gameserver.configs.main.PlayerBotConfig;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.player.Player;

/**
 * PvP-4 arenas, a hook of its own (pvp.battlegrounds.arenas): in the Arenas of Chaos, Discipline, Harmony and Glory and
 * their training grounds, non-PvP bots go for the arena's point objects and monsters when no opponent is near: relics,
 * flame throwers and floor shifters are used through their native cast bar (some also cast their bonus skill on the
 * user), monsters and the plaza wall are fought. Opponents (800 points a kill) stay with PlayerBotPvpSeek; rounds and
 * zone changes stay native. The table mirrors PvPArenaInstance.getPoints, which every arena uses; PlayerBotArenasCheck
 * pins it there.
 */
final class PlayerBotArenas {
 /** Native point objects: used, not fought (RelicsAI, PlazaFlameThrowerAI, HarmonyShifterAI). */
 static final Map<Integer,Integer> USES=Map.ofEntries(
  Map.entry(701187,1750),Map.entry(701188,1750),Map.entry(701181,750),Map.entry(701195,750),Map.entry(701209,750),
  Map.entry(701172,500),Map.entry(701171,500),Map.entry(701170,500),Map.entry(701169,500),Map.entry(701317,500),Map.entry(701318,500),Map.entry(701319,500),
  Map.entry(207102,400),Map.entry(207116,400),Map.entry(207099,200),
  Map.entry(701215,100),Map.entry(701220,100),Map.entry(701225,100),Map.entry(701216,100),Map.entry(701221,100),Map.entry(701226,100));
 /** Arena monsters and walls: fought, points shared by damage natively. */
 static final Map<Integer,Integer> KILLS=Map.ofEntries(
  Map.entry(218690,1500),Map.entry(218703,1500),Map.entry(218716,1500),Map.entry(218691,1500),Map.entry(218704,1500),Map.entry(218717,1500),
  Map.entry(218682,1250),Map.entry(218695,1250),Map.entry(218708,1250),
  Map.entry(218689,750),Map.entry(218702,750),Map.entry(218715,750),Map.entry(218701,650),Map.entry(218688,650),Map.entry(218714,650),
  Map.entry(218806,500),Map.entry(218807,500),Map.entry(218808,500),
  Map.entry(219277,400),Map.entry(219278,400),Map.entry(219279,400),Map.entry(219481,400),Map.entry(219485,400),Map.entry(219486,400),Map.entry(219648,400),Map.entry(219652,400),
  Map.entry(218685,250),Map.entry(218698,250),Map.entry(218711,250),Map.entry(218687,250),Map.entry(218700,250),Map.entry(218713,250),Map.entry(218686,250),Map.entry(218699,250),Map.entry(218712,250),
  Map.entry(218683,100),Map.entry(218696,100),Map.entry(218709,100),Map.entry(218684,100),Map.entry(218697,100),Map.entry(218710,100),Map.entry(218693,100),Map.entry(218706,100),Map.entry(218719,100),
  Map.entry(219280,100),Map.entry(219281,100),Map.entry(219282,100),Map.entry(219649,100),Map.entry(233058,100),
  Map.entry(219283,50),Map.entry(219284,50),Map.entry(219285,50),Map.entry(219650,50),Map.entry(219328,50));
 /** Arena of Chaos, Discipline, Glory, Harmony; Chaos, Discipline, Harmony training grounds. */
 static final int[] MAPS={300350000,300360000,300550000,300450000,300420000,300430000,300570000};
 private static final Map<Integer,PlayerBotBattlegroundPlay.Field> FIELDS=new HashMap<>();
 static{for(int map:MAPS)FIELDS.put(map,new PlayerBotBattlegroundPlay.Field(map,"Arena",KILLS,USES,0,0,false));}
 static final PlayerBotBattlegroundPlay.Stats STATS=new PlayerBotBattlegroundPlay.Stats();
 static boolean enabled(){return PlayerBotConfig.PVP_ENABLED && PlayerBotConfig.PVP_BATTLEGROUNDS && PlayerBotConfig.PVP_ARENAS;}
 static PlayerBotBattlegroundPlay.Field field(int map){return FIELDS.get(map);}
 static boolean mechanicEnemy(Player bot,Npc npc,List<Player> party) {
  if(!enabled() || bot.getPosition()==null)return false;
  var f=field(bot.getWorldId());return f!=null && PlayerBotBattlegroundPlay.mechanicEnemy(f,STATS,bot,npc);
 }
 static boolean travel(PlayerBotSession session,PlayerBotNavigation navigation,long now) {
  var bot=session.bot();var f=bot.getPosition()==null?null:field(bot.getWorldId());
  return f!=null && PlayerBotBattlegroundPlay.travel(f,STATS,enabled(),session,navigation,now);
 }
 static String stats(){return STATS.line(enabled());}
 private PlayerBotArenas(){}
}
