package com.aionemu.gameserver.services.playerbot;

import java.util.concurrent.atomic.LongAdder;
import com.aionemu.gameserver.model.gameobjects.player.Player;

/** Successful native world-character completions, independent of planning and population rotation. */
public final class PlayerBotWorldOutcomes {
 private static final LongAdder LEVELS=new LongAdder(),QUESTS=new LongAdder(),CRAFTS=new LongAdder(),GATHERS=new LongAdder();
 public static void levelChanged(Player bot,int oldLevel,int newLevel){if(bot.isWorldBot() && newLevel>oldLevel)LEVELS.add(newLevel-oldLevel);}
 public static void questReward(Player bot){if(bot.isWorldBot())QUESTS.increment();}
 public static void crafted(Player bot){if(bot.isWorldBot())CRAFTS.increment();}
 public static void gathered(Player bot){if(bot.isWorldBot())GATHERS.increment();}
 static String report(){return "world completions since last report: level-ups="+LEVELS.sumThenReset()+", quest turn-ins="+QUESTS.sumThenReset()
  +", crafts="+CRAFTS.sumThenReset()+", gathers="+GATHERS.sumThenReset();}
 private PlayerBotWorldOutcomes(){}
}
