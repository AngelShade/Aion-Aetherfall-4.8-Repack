package com.aionemu.gameserver.services.playerbot;

import java.util.*;
import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.model.DialogAction;
import com.aionemu.gameserver.model.DialogPage;
import com.aionemu.gameserver.model.gameobjects.Npc;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.templates.QuestTemplate;
import com.aionemu.gameserver.model.templates.quest.QuestCategory;
import com.aionemu.gameserver.model.templates.quest.QuestItems;
import com.aionemu.gameserver.questEngine.QuestEngine;
import com.aionemu.gameserver.questEngine.model.QuestStatus;
import com.aionemu.gameserver.services.DialogService;
import com.aionemu.gameserver.services.QuestService;
import com.aionemu.gameserver.utils.PositionUtil;
import com.aionemu.gameserver.world.geo.GeoService;

/** Nearby quest interaction; handlers own prerequisites, work items, progress and rewards. */
final class PlayerBotQuests {
	record Job(Npc npc, int quest, boolean turnIn) {}
	private final Map<Long, Long> retryAfter = new HashMap<>();
	private long nextScan;
	private Job pending;
	private long pendingSince;
	Job choose(Player owner, Player bot) {
		long now = System.currentTimeMillis();
		if (pending != null) {
			var state = bot.getQuestStateList().getQuestState(pending.quest());
			boolean current = pending.turnIn() ? state != null && state.getStatus() == QuestStatus.REWARD
				: QuestService.checkStartConditions(bot, pending.quest(), false);
			if (current && nearby(owner, bot, pending.npc()) && now - pendingSince < 20000) return pending;
			retryAfter.put(key(pending.npc(), pending.quest()), now + 30000); pending = null;
		}
		if (now < nextScan) return null;
		nextScan = now + 2000;
		retryAfter.entrySet().removeIf(entry -> entry.getValue() <= now);
		List<Job> jobs = new ArrayList<>();
		bot.getKnownList().forEachNpc(npc -> {
			if (!nearby(owner, bot, npc)) return;
			var registered = QuestEngine.getInstance().getQuestNpc(npc.getNpcId());
			if (registered == null) return;
			for (var state : bot.getQuestStateList().getUncompletedQuests())
				if (state.getStatus() == QuestStatus.REWARD && registered.getOnTalkEvent().contains(state.getQuestId())
					&& ordinary(DataManager.QUEST_DATA.getQuestById(state.getQuestId())) && !blocked(npc, state.getQuestId()))
					jobs.add(new Job(npc, state.getQuestId(), true));
			for (int id : registered.getOnQuestStart()) {
				var ownersQuest = owner.getQuestStateList().getQuestState(id);
				if (ownersQuest != null && (ownersQuest.getStatus() == QuestStatus.START || ownersQuest.getStatus() == QuestStatus.REWARD)
					&& ordinary(DataManager.QUEST_DATA.getQuestById(id)) && !blocked(npc, id) && QuestService.checkStartConditions(bot, id, false))
					jobs.add(new Job(npc, id, false));
			}
		});
		pending = jobs.stream().sorted(Comparator.comparing(Job::turnIn).reversed()
			.thenComparingDouble(job -> PositionUtil.getDistance(bot, job.npc())).thenComparingInt(Job::quest)).findFirst().orElse(null);
		pendingSince = now;
		return pending;
	}
	boolean interact(Player owner, Player bot, Job job, PlayerBotRules.Role role) {
		return interact(owner, bot, job, role, false);
	}
	boolean interact(Player owner, Player bot, Job job, PlayerBotRules.Role role, boolean mission) {
		Npc npc = job.npc();
		boolean missionReward = mission && job.turnIn() && QuestEngine.getInstance().getPlayerBotRewardNpcIds(bot, job.quest()).contains(npc.getNpcId())
			&& !owner.isDead() && !npc.isDead() && npc.isSpawned() && npc.getMaster() == npc && !bot.isEnemy(npc)
			&& npc.getWorldId() == bot.getWorldId() && npc.getInstanceId() == bot.getInstanceId() && bot.getKnownList().sees(npc)
			&& PositionUtil.isInRange(owner, npc, PlayerBotMission.range()) && DialogService.isInteractionAllowed(bot, npc);
		if ((!missionReward && !nearby(owner, bot, npc)) || !PositionUtil.isInTalkRange(bot, npc) || !GeoService.getInstance().canSee(bot, npc)) return false;
		// A handler can open a custom page instead; do not guess subsequent story-dialog actions.
		try (var dialog = PlayerBotQuestDialog.open(bot.getObjectId(), npc.getObjectId(), job.quest())) {
			if (!job.turnIn()) {
				var owned = owner.getQuestStateList().getQuestState(job.quest());
				if (owned == null || owned.getStatus() != QuestStatus.START && owned.getStatus() != QuestStatus.REWARD) return false;
				if (!QuestService.checkStartConditions(bot, job.quest(), false)) return false;
				npc.getController().onDialogSelect(DialogAction.ASK_QUEST_ACCEPT, 0, bot, job.quest(), 0);
				if (dialog.offered(DialogPage.ASK_QUEST_ACCEPT_WINDOW.id()))
					npc.getController().onDialogSelect(DialogAction.QUEST_ACCEPT, DialogPage.ASK_QUEST_ACCEPT_WINDOW.id(), bot, job.quest(), 0);
				var state = bot.getQuestStateList().getQuestState(job.quest());
				return state != null && state.getStatus() == QuestStatus.START;
			}
			var state = bot.getQuestStateList().getQuestState(job.quest());
			var template = DataManager.QUEST_DATA.getQuestById(job.quest());
			if (state == null || state.getStatus() != QuestStatus.REWARD || !ordinary(template)) return false;
			// Extended/repeat choices have separate client index semantics; require a human until supported.
			if (template.getExtendedRewards() != null) return false;
			npc.getController().onDialogSelect(DialogAction.SELECT_QUEST_REWARD, 0, bot, job.quest(), 0);
			int page = DialogPage.getRewardPageByIndex(state.getRewardGroup()).id();
			if (!dialog.offered(page)) return false; // confirms that this handler authorized this reward NPC
			Integer group = state.getRewardGroup();
			if (group != null && (group < 0 || group >= template.getRewards().size())) return false;
			List<QuestItems> choices = group == null ? List.of() : template.getRewards().get(group).getSelectableRewardItem();
			if (template.isClassRewardOnEveryRepeat() || template.isSingleTimeClassReward() && state.getCompleteCount() == template.getRewardRepeatCount() - 1)
				choices = template.getSelectableRewardByClass(bot.getPlayerClass());
			int choice = rewardChoice(bot, role, choices);
			if (choice < -1 || choice > 14) return false;
			npc.getController().onDialogSelect(choice < 0 ? DialogAction.SELECTED_QUEST_NOREWARD : DialogAction.SELECTED_QUEST_REWARD1 + choice,
				page, bot, job.quest(), 0);
			return state.getStatus() != QuestStatus.REWARD;
		} finally {
			pending = null;
			retryAfter.put(key(npc, job.quest()), System.currentTimeMillis() + 30000);
			DialogService.onCloseDialog(bot, npc);
		}
	}
	static boolean ordinary(QuestTemplate template) { return template != null && template.getCategory() == QuestCategory.QUEST; }
	Npc hunt(Player owner, Player bot) {
		return hunt(owner, bot, 0);
	}
	Npc hunt(Player owner, Player bot, int missionQuest) {
		Set<Integer> required = new HashSet<>();
		for (var state : bot.getQuestStateList().getUncompletedQuests())
			if ((missionQuest == 0 || state.getQuestId() == missionQuest) && state.getStatus() == QuestStatus.START && ordinary(DataManager.QUEST_DATA.getQuestById(state.getQuestId())))
				required.addAll(QuestEngine.getInstance().getRequiredKillNpcIds(bot, state.getQuestId()));
		if (required.isEmpty()) return null;
		List<Npc> candidates = new ArrayList<>();
		bot.getKnownList().forEachNpc(npc -> {
			var rating = npc.getObjectTemplate().getRating();
			if (required.contains(npc.getNpcId()) && (rating == com.aionemu.gameserver.model.templates.npc.NpcRating.JUNK
				|| rating == com.aionemu.gameserver.model.templates.npc.NpcRating.NORMAL)
				&& npc.getLevel() <= bot.getLevel() + 2 && npc.isSpawned() && !npc.isDead() && npc.getMaster() == npc
				&& npc.getAggroList().stream().findAny().isEmpty() && bot.isEnemy(npc)
				&& (missionQuest == 0 ? PositionUtil.isInRange(owner, npc, 12) : PositionUtil.isInRange(bot, npc, 12) && PositionUtil.isInRange(owner, npc, PlayerBotMission.range()))
				&& npc.getWorldId() == bot.getWorldId() && npc.getInstanceId() == bot.getInstanceId() && GeoService.getInstance().canSee(bot, npc))
				candidates.add(npc);
		});
		return candidates.stream().min(Comparator.comparingDouble(npc -> PositionUtil.getDistance(bot, npc))).orElse(null);
	}
	Job missionReward(Player owner, Player bot, int quest) {
		Set<Integer> required = QuestEngine.getInstance().getPlayerBotRewardNpcIds(bot, quest);
		List<Npc> candidates = new ArrayList<>();
		bot.getKnownList().forEachNpc(npc -> {
			if (required.contains(npc.getNpcId()) && !blocked(npc, quest) && !npc.isDead() && npc.isSpawned() && npc.getMaster() == npc && !bot.isEnemy(npc)
				&& npc.getWorldId() == bot.getWorldId() && npc.getInstanceId() == bot.getInstanceId() && PositionUtil.isInRange(owner, npc, PlayerBotMission.range())
				&& DialogService.isInteractionAllowed(bot, npc)) candidates.add(npc);
		});
		return candidates.stream().min(Comparator.comparingDouble(n -> PositionUtil.getDistance(bot, n))).map(n -> new Job(n, quest, true)).orElse(null);
	}
	private static int rewardChoice(Player bot, PlayerBotRules.Role role, List<QuestItems> choices) {
		if (choices.isEmpty()) return -1;
		int selected = -2; double best = -Double.MAX_VALUE;
		for (int index = 0; index < choices.size(); index++) {
			var item = DataManager.ITEM_DATA.getItemTemplate(choices.get(index).getItemId());
			if (item == null || !item.isClassSpecific(bot.getPlayerClass()) || item.getRequiredLevel(bot.getPlayerClass()) < 0) continue;
			double score = item.getItemSlot() == 0 ? 0 : PlayerBotEquipment.score(item, role, bot.getPlayerClass());
			var weapon = bot.getEquipment().getMainHandWeapon();
			if (item.isWeapon() && weapon != null && weapon.getItemTemplate().getItemGroup() == item.getItemGroup()) score += 10000;
			if (score > best) { best = score; selected = index; }
		}
		return selected;
	}
	private boolean blocked(Npc npc, int quest) { return retryAfter.containsKey(key(npc, quest)); }
	private static long key(Npc npc, int quest) { return (long) npc.getObjectId() << 32 | quest & 0xffffffffL; }
	private static boolean nearby(Player owner, Player bot, Npc npc) {
		return !owner.isDead() && !npc.isDead() && npc.isSpawned() && npc.getMaster() == npc && !bot.isEnemy(npc)
			&& npc.getWorldId() == bot.getWorldId() && npc.getInstanceId() == bot.getInstanceId()
			&& PositionUtil.isInRange(owner, npc, 15) && bot.getKnownList().sees(npc) && DialogService.isInteractionAllowed(bot, npc);
	}
}
