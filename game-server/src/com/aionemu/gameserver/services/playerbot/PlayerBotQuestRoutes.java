/* ChooseTravelTargetAction.cpp at mod-playerbots 037c01418b5d01506917a3db9b44fd56ac5f965c.
 * GPL-2.0-or-later; third-party/playerbots/AUTHORS.md. Group destinations, active
 * objectives and bounded retries are adapted to native Aion objectives and the owner leash. */
package com.aionemu.gameserver.services.playerbot;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.templates.npc.NpcRating;
import com.aionemu.gameserver.questEngine.QuestEngine;
import com.aionemu.gameserver.questEngine.model.QuestStatus;
import com.aionemu.gameserver.utils.PositionUtil;
import static com.aionemu.gameserver.services.playerbot.PlayerBotEngine.*;

/** Local autonomous destinations for approved active quests; native handlers still own every objective. */
final class PlayerBotQuestRoutes {
 enum Kind { REWARD, CONVERSATION, OBJECT, HUNT }
 record Goal(int quest,int npc,Kind kind,PlayerBotNavigation.Point point,boolean grouped,boolean visible,double distance) {}
 private static final class Plan {
  Goal goal;long nextScan,lastProgress;double best=Double.POSITIVE_INFINITY;
  int world,instance;
  final Map<String,Long> excluded=new HashMap<>();
 }
 private static final Map<Integer,Plan> PLANS=new ConcurrentHashMap<>();
 static String key(Goal goal){return goal.quest()+":"+goal.npc()+":"+goal.kind()+":"+goal.point();}
 static Goal select(List<Goal> choices) {
  return choices.stream().filter(g->Double.isFinite(g.distance()) && g.distance()>=0
   && finite(g.point()))
   .min(Comparator.comparing(Goal::grouped).reversed().thenComparing(Goal::kind)
    .thenComparing(Comparator.comparing(Goal::visible).reversed()).thenComparingDouble(Goal::distance)
    .thenComparingInt(Goal::quest).thenComparingInt(Goal::npc)).orElse(null);
 }
 static Set<Integer> ids(Player owner,Player bot,int quest,Kind kind) {
  var state=bot.getQuestStateList().getQuestState(quest);if(state==null)return Set.of();
  boolean ready=PlayerBotQuestMetadata.ready(bot,quest);
  if(kind==Kind.REWARD)return ready ? PlayerBotQuestMetadata.rewardNpcIds(bot,quest) : Set.of();
  if(ready || state.getStatus()!=QuestStatus.START)return Set.of();
  return switch(kind) {
   case CONVERSATION -> PlayerBotQuestConversations.objectiveIds(owner,bot,quest);
   case OBJECT -> PlayerBotQuestObjects.objectiveIds(bot,quest);
   case HUNT -> {
    Set<Integer> targets=new TreeSet<>(QuestEngine.getInstance().getRequiredKillNpcIds(bot,quest));
    targets.addAll(PlayerBotQuestObjects.objectiveIds(bot,quest));yield targets;
   }
   default -> Set.of();
  };
 }
 static float leash(Kind kind){return kind==Kind.HUNT ? 25 : kind==Kind.REWARD ? 34 : 24;}
 static boolean enabled(Kind kind,boolean questCombat){return kind!=Kind.HUNT || questCombat;}
 static boolean stillWanted(PlayerBotSession session,Goal goal) {
  return enabled(goal.kind(),Boolean.TRUE.equals(PlayerBotPartyBehavior.snapshot(session).get("questCombat")))
   && PlayerBotQuestSync.wanted(session.owner(),session.bot(),goal.quest())
   && ids(session.owner(),session.bot(),goal.quest(),goal.kind()).contains(goal.npc());
 }
 static Goal choose(PlayerBotSession session,long now) {return PlayerBotQuestObjectives.choose(session,now);}
 static Trigger trigger(PlayerBotSession session,PlayerBotNavigation navigation) {return PlayerBotQuestObjectives.trigger(session,navigation);}
 static void close(Player bot){PLANS.remove(bot.getObjectId());PlayerBotQuestObjectives.close(bot);}
 static boolean finite(PlayerBotNavigation.Point p){return p!=null && Float.isFinite(p.x()) && Float.isFinite(p.y()) && Float.isFinite(p.z());}
 private PlayerBotQuestRoutes() {}
}
