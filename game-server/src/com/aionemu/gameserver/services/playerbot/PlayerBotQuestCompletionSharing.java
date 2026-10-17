/* Owner-requested Aion completion sharing, informed by pinned mod-playerbots
 * QuestAction::CompleteQuest; GPL-2.0-or-later, third-party/playerbots/AUTHORS.md.
 * Historical catch-up and waived objectives are deliberate Aion policies. */
package com.aionemu.gameserver.services.playerbot;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.templates.QuestTemplate;
import com.aionemu.gameserver.model.templates.quest.QuestCategory;
import com.aionemu.gameserver.model.templates.quest.QuestItems;
import com.aionemu.gameserver.questEngine.model.*;
import com.aionemu.gameserver.services.QuestService;

/** Runs under the existing companion session lock and native progress checkpoint. */
final class PlayerBotQuestCompletionSharing {
 record Completed(int quest, int count, Integer reward, int flags, long time) {}
 record Snapshot(long expires, List<Completed> quests) {}
 record Progress(int ownerCount, int target) {}
 private static final Map<Integer, Snapshot> OWNERS = new ConcurrentHashMap<>();
 private static final Map<Integer, State> STATES = new ConcurrentHashMap<>();
 private static long key(int owner, int quest) { return (long)owner << 32 | quest & 0xffffffffL; }
 private static final class State {
  final Set<Integer> blocked = new HashSet<>();
  final Map<Long, Progress> progress = new HashMap<>();
  final Map<Integer, Integer> urgent = new LinkedHashMap<>();
  Snapshot observed;
  int cursor;
  long next;
  State(Player bot) {
   try {
    var p = PlayerBotMetadata.load(bot.getAccount().getId(), bot.getObjectId(), "quest-completion",
     Path.of("config/playerbots/quest-completion-character-" + bot.getObjectId() + ".properties"));
    for (String id : p.getProperty("blocked", "").split(",")) if (!id.isBlank()) blocked.add(Integer.parseInt(id));
    for (String name : p.stringPropertyNames()) if (name.startsWith("quest.")) {
     var ids = name.split("\\."); var counts = p.getProperty(name).split(":");
     progress.put(key(Integer.parseInt(ids[1]), Integer.parseInt(ids[2])), new Progress(Integer.parseInt(counts[0]), Integer.parseInt(counts[1])));
    }
   } catch (java.io.IOException error) { throw new IllegalStateException("Cannot load shared quest reward custody", error); }
  }
  void save(Player bot) {
   var p = new Properties();
   p.setProperty("account", Integer.toString(bot.getAccount().getId()));
   p.setProperty("character", Integer.toString(bot.getObjectId()));
   p.setProperty("blocked", blocked.stream().sorted().map(String::valueOf).collect(java.util.stream.Collectors.joining(",")));
   progress.forEach((id, value) -> p.setProperty("quest." + (int)(id >> 32) + "." + (int)(long)id, value.ownerCount() + ":" + value.target()));
   try { PlayerBotMetadata.save(bot.getAccount().getId(), bot.getObjectId(), "quest-completion", p); }
   catch (java.io.IOException error) { throw new IllegalStateException("Cannot preserve shared quest reward custody", error); }
  }
 }
 static boolean applies(PlayerBotSession session) {
  var owner = session.owner(); var bot = session.bot();
  return !session.world() && owner != bot && !owner.isPlayerBot() && bot.isPlayerBot()
   && bot.getPlayerBotOwnerId() == owner.getObjectId() && owner.getAccount() != null && bot.getAccount() != null
   && owner.getAccount().getId() == bot.getAccount().getId();
 }
 static void ownerCompleted(Player owner, int quest) {
  OWNERS.remove(owner.getObjectId());
 }
 static void close(Player bot) {
  STATES.remove(bot.getObjectId()); OWNERS.remove(bot.getPlayerBotOwnerId());
 }
 static Snapshot snapshot(Player owner, long now) {
  var existing = OWNERS.get(owner.getObjectId());
  if (existing != null && existing.expires() > now) return existing;
  var completed = new ArrayList<Completed>();
  // Native add/delete also lock the list; never iterate its mutable map directly.
  synchronized (owner.getQuestStateList()) {
   for (var q : owner.getQuestStateList().getAllQuestState()) if (q.getCompleteCount() > 0)
    completed.add(new Completed(q.getQuestId(), q.getCompleteCount(), q.getRewardGroup(), q.getFlags(),
     q.getLastCompleteTime() == null ? 0 : q.getLastCompleteTime().getTime()));
  }
  completed.sort(Comparator.comparingLong(Completed::time).thenComparingInt(Completed::quest));
  var result = new Snapshot(now + 3000, List.copyOf(completed));
  OWNERS.put(owner.getObjectId(), result);
  return result;
 }
 static boolean needsCompletion(QuestState own, int count) { return count > 0 && (own == null || own.getCompleteCount() < count); }
 static int limit(QuestTemplate template) {
  int count = template.getCategory() == QuestCategory.MISSION ? 1 : template.getMaxRepeatCount();
  return count == 255 ? Integer.MAX_VALUE : Math.max(1, count);
 }
 static Progress observe(Progress previous, int ownerCount, int ownCount, int limit) {
  long target = previous == null ? Math.max(ownerCount, ownCount)
   : Math.max(previous.target(), ownCount) + (long)Math.max(0, ownerCount - previous.ownerCount());
  return new Progress(previous == null ? ownerCount : Math.max(previous.ownerCount(), ownerCount), (int)Math.min(target, limit));
 }
 static void observe(PlayerBotSession session, State state, Snapshot snapshot) {
  if (state.observed == snapshot) return;
  boolean changed = false;
  for (var completed : snapshot.quests()) {
   var template = DataManager.QUEST_DATA.getQuestById(completed.quest()); if (template == null) continue;
   var own = session.bot().getQuestStateList().getQuestState(completed.quest());
   long key = key(session.owner().getObjectId(), completed.quest());
   var previous = state.progress.get(key);
   var next = observe(previous, completed.count(), own == null ? 0 : own.getCompleteCount(), limit(template));
   if (!next.equals(previous)) { state.progress.put(key, next); changed = true; }
   int added = previous == null ? state.observed == null ? 0 : completed.count() : Math.max(0, completed.count() - previous.ownerCount());
   if (added > 0 && needsCompletion(own, next.target()))
    state.urgent.merge(completed.quest(), added, (a,b) -> (int)Math.min(Integer.MAX_VALUE, (long)a+b));
  }
  if (changed) state.save(session.bot());
  state.observed = snapshot;
 }
 static Completed next(PlayerBotSession session, State state, Snapshot snapshot) {
  while (!state.urgent.isEmpty()) {
   int id = state.urgent.keySet().iterator().next();
   int count = state.urgent.remove(id);
   var q = snapshot.quests().stream().filter(c -> c.quest() == id).findFirst().orElse(null);
   if (q == null || state.blocked.contains(id) || !needsCompletion(session.bot().getQuestStateList().getQuestState(id),
    state.progress.get(key(session.owner().getObjectId(), id)).target())) continue;
   if (count > 1) state.urgent.put(id, count - 1);
   return q;
  }
  var quests = snapshot.quests();
  for (int i = 0; i < quests.size(); i++) {
   int index = (state.cursor + i) % quests.size(); var q = quests.get(index);
   var progress = state.progress.get(key(session.owner().getObjectId(), q.quest()));
   if (progress != null && !state.blocked.contains(q.quest())
    && needsCompletion(session.bot().getQuestStateList().getQuestState(q.quest()), progress.target())) {
    state.cursor = (index + 1) % quests.size(); return q;
   }
  }
  return null;
 }
 static List<QuestItems> choices(Player bot, QuestTemplate template, int count, Integer group) {
  if (template.isClassRewardOnEveryRepeat() || template.isSingleTimeClassReward() && count == template.getRewardRepeatCount() - 1)
   return template.getSelectableRewardByClass(bot.getPlayerClass());
  return group == null ? List.of() : template.getRewards().get(group).getSelectableRewardItem();
 }
 static int choice(PlayerBotSession session, List<QuestItems> items) {
  int selected = PlayerBotGearPolicy.rewardChoice(session.bot(), session.combatRole(), items);
  // An incompatible class-specific quest still shares its rewards; do not equip them.
  return selected == -2 ? 0 : selected;
 }
 static void tick(PlayerBotSession session) {
  var owner = session.owner(); var bot = session.bot();
  if (session.closing() || !PlayerBotTeams.same(owner, bot)) return;
  var settings = PlayerBotQuestSync.state(session);
  if (!settings.partySync) return;
  var state = STATES.computeIfAbsent(bot.getObjectId(), id -> new State(bot));
  long now = System.currentTimeMillis();
  if (now < state.next) return;
  state.next = now + 500;
  var snapshot = snapshot(owner, now);
  observe(session, state, snapshot);
  // Live completions precede fair historical catch-up; neither a long history nor
  // one heavily repeated quest may monopolize this companion's decision stream.
  var completed = next(session, state, snapshot);
  if (completed != null) {
   var template = DataManager.QUEST_DATA.getQuestById(completed.quest());
   var own = bot.getQuestStateList().getQuestState(completed.quest());
   if (!PlayerBotTeams.same(owner, bot) || session.closing()) return;
   if (own == null) {
    own = new QuestState(completed.quest(), QuestStatus.REWARD);
    if (!bot.getQuestStateList().addQuest(completed.quest(), own)) return;
   }
   // The per-owner award target is saved atomically with native quest counts.
   own.setStatus(QuestStatus.REWARD);
   own.setRewardGroup(completed.reward());
   own.setFlags(completed.flags());
   QuestService.validateAndFixRewardGroup(own, completed.quest());
   int reward = choice(session, choices(bot, template, own.getCompleteCount(), own.getRewardGroup()));
   boolean extended = template.getExtendedRewards() != null && own.getCompleteCount() == template.getRewardRepeatCount() - 1;
   int extra = extended ? choice(session, template.getExtendedRewards().getSelectableRewardItem()) : -1;
   // If a native callback throws after a partial reward, checkpoint the blocker together
   // with that inventory/progress. Never automatically grant a second partial reward.
   state.blocked.add(completed.quest()); state.save(bot);
   try {
    if (!QuestService.finishPlayerBotQuest(new QuestEnv(null, bot, completed.quest()), reward, extra))
     throw new IllegalStateException("Native quest completion was refused");
    state.blocked.remove(completed.quest()); state.save(bot);
    settings.prompts.remove(completed.quest());
    settings.managed.add(completed.quest());
    settings.ownerCompletions.put(completed.quest(), completed.count());
    settings.save();
    PlayerBotQuestSync.notice(bot, "Completed " + com.aionemu.gameserver.utils.ChatUtil.quest(completed.quest())
     + " with you and received its rewards.", "shared-completion", 3000);
   } catch (RuntimeException error) {
    org.slf4j.LoggerFactory.getLogger(PlayerBotQuestCompletionSharing.class).error(
     "Shared quest {} reward stopped for companion {}; partial rewards will not be retried", completed.quest(), bot.getObjectId(), error);
    PlayerBotQuestSync.notice(bot, "Quest " + completed.quest() + " could not finish rewarding me. Check the server log before retrying its rewards.",
     "shared-completion-failed:" + completed.quest(), 60000);
   }
   return;
  }
 }
 private PlayerBotQuestCompletionSharing() {}
}
