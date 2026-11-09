package com.aionemu.gameserver.services.playerbot;

import java.util.concurrent.atomic.LongAdder;
import com.aionemu.gameserver.model.gameobjects.player.Player;

/** Successful native world-character completions, independent of planning and population rotation. */
public final class PlayerBotWorldOutcomes {
 private static final LongAdder LEVELS=new LongAdder(),QUESTS=new LongAdder(),CRAFTS=new LongAdder(),GATHERS=new LongAdder(),BACKGROUND_CRAFTS=new LongAdder(),BACKGROUND_GATHERS=new LongAdder();
 public static void levelChanged(Player bot,int oldLevel,int newLevel){if(bot.isWorldBot() && newLevel>oldLevel){LEVELS.add(newLevel-oldLevel);PlayerBotActivityMetrics.outcome(bot,PlayerBotActivityMetrics.Outcome.LEVEL,newLevel-oldLevel);}}
 public static void questReward(Player bot){if(bot.isWorldBot()){QUESTS.increment();PlayerBotActivityMetrics.outcome(bot,PlayerBotActivityMetrics.Outcome.TURN_IN,1);}}
 public static void crafted(Player bot){if(bot.isWorldBot()){CRAFTS.increment();PlayerBotActivityMetrics.outcome(bot,PlayerBotActivityMetrics.Outcome.CRAFT,1);}}
 public static void gathered(Player bot){if(bot.isWorldBot()){GATHERS.increment();PlayerBotActivityMetrics.outcome(bot,PlayerBotActivityMetrics.Outcome.GATHER,1);}}
 static void killed(Player bot){PlayerBotActivityMetrics.outcome(bot,PlayerBotActivityMetrics.Outcome.KILL,1);}
 static void sold(Player bot,long units){PlayerBotActivityMetrics.outcome(bot,PlayerBotActivityMetrics.Outcome.SALE,units);}
 /** Crafts already count through the native finish; this only records how many ran with no player on the map. */
 static void backgroundCrafted(){BACKGROUND_CRAFTS.increment();}
 static void backgroundGathered(Player bot){BACKGROUND_GATHERS.increment();gathered(bot);}
 static String report(){return "world completions since last report: level-ups="+LEVELS.sumThenReset()+", quest turn-ins="+QUESTS.sumThenReset()
  +", crafts="+CRAFTS.sumThenReset()+", gathers="+GATHERS.sumThenReset();}
 /** Running totals of the crafts and gathers above that ran with no player on the map. */
 static String background(){return "on empty maps crafts="+BACKGROUND_CRAFTS.sum()+", gathers="+BACKGROUND_GATHERS.sum();}
 private PlayerBotWorldOutcomes(){}
}
