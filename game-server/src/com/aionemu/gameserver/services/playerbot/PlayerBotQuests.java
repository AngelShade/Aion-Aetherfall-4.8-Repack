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
			boolean current = pending.turnIn() ? PlayerBotQuestMetadata.ready(bot, pending.quest())
				: QuestService.checkStartConditions(bot, pending.quest(), false);
			if (current && PlayerBotQuestObjectives.job(bot,pending) && PlayerBotQuestSync.wanted(owner, bot, pending.quest()) && nearby(owner, bot, pending.npc()) && now - pendingSince < 60000) return pending;
			if(!current || PlayerBotQuestObjectives.job(bot,pending))retryAfter.put(key(pending.npc(), pending.quest()), now + 30000); pending = null;nextScan=0;
		}
		if (now < nextScan) return null;
		nextScan = now + 2000;
		retryAfter.entrySet().removeIf(entry -> entry.getValue() <= now);
		List<Job> jobs = new ArrayList<>();
		owner.getKnownList().forEachNpc(npc -> {
			if (!nearby(owner, bot, npc)) return;
			var registered = QuestEngine.getInstance().getQuestNpc(npc.getNpcId());
			if (registered == null) return;
			for (var state : bot.getQuestStateList().getUncompletedQuests())
				if (PlayerBotQuestMetadata.ready(bot, state.getQuestId()) && PlayerBotQuestSync.wanted(owner, bot, state.getQuestId()) && registered.getOnTalkEvent().contains(state.getQuestId())
					&& PlayerBotQuestMetadata.rewardNpcIds(bot, state.getQuestId()).contains(npc.getNpcId()) && !blocked(npc, state.getQuestId()))
					if(PlayerBotQuestObjectives.job(bot,new Job(npc,state.getQuestId(),true)))jobs.add(new Job(npc, state.getQuestId(), true));
			for (int id : registered.getOnQuestStart()) {
				PlayerBotQuestSync.nearby(owner, bot, id);
				if (PlayerBotQuestSync.wanted(owner, bot, id) && ordinary(DataManager.QUEST_DATA.getQuestById(id))
					&& !blocked(npc, id) && QuestService.checkStartConditions(bot, id, false))
					if(PlayerBotQuestObjectives.job(bot,new Job(npc,id,false)))jobs.add(new Job(npc, id, false));
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
		if ((!mission && !PlayerBotQuestObjectives.job(bot,job)) || (!missionReward && !nearby(owner, bot, npc)) || !PositionUtil.isInTalkRange(bot, npc) || !GeoService.getInstance().canSee(bot, npc)) return false;
		// A handler can open a custom page instead; do not guess subsequent story-dialog actions.
		try (var dialog = PlayerBotQuestDialog.open(bot.getObjectId(), npc.getObjectId(), job.quest())) {
			if (!job.turnIn()) {
				if (!PlayerBotQuestSync.wanted(owner, bot, job.quest())) return false;
				if (!QuestService.checkStartConditions(bot, job.quest(), false)) return false;
				PlayerBotQuestMetadata.accept(bot, npc, job.quest(), dialog);
				var state = bot.getQuestStateList().getQuestState(job.quest());
				boolean accepted = state != null && state.getStatus() == QuestStatus.START;
				if (accepted) PlayerBotQuestSync.accepted(owner, bot, job.quest(), false);
				return accepted;
			}
			var state = bot.getQuestStateList().getQuestState(job.quest());
			var template = DataManager.QUEST_DATA.getQuestById(job.quest());
			if (state == null || !PlayerBotQuestMetadata.ready(bot, job.quest()) || template == null) return false;
			npc.getController().onDialogSelect(PlayerBotQuestMetadata.rewardAction(bot, job.quest()), 0, bot, job.quest(), 0);
			if (state.getStatus() != QuestStatus.REWARD) return false;
			int page = DialogPage.getRewardPageByIndex(state.getRewardGroup()).id();
			if (!dialog.offered(page)) return false; // confirms that this handler authorized this reward NPC
			Integer group = state.getRewardGroup();
			if (group != null && (group < 0 || group >= template.getRewards().size())) return false;
			List<QuestItems> choices = group == null ? List.of() : template.getRewards().get(group).getSelectableRewardItem();
			if (template.isClassRewardOnEveryRepeat() || template.isSingleTimeClassReward() && state.getCompleteCount() == template.getRewardRepeatCount() - 1)
				choices = template.getSelectableRewardByClass(bot.getPlayerClass());
			int choice = rewardChoice(bot, role, choices);
			if (choice < -1 || choice > 14) return false;
			boolean extended=template.getExtendedRewards()!=null && state.getCompleteCount()==template.getRewardRepeatCount()-1;
			int extra=extended ? rewardChoice(bot,role,template.getExtendedRewards().getSelectableRewardItem()) : -1;
			// Native combined reward dialog indexes use 8+choice for extended rewards.
			int action=extended && extra>=0 ? DialogAction.SELECTED_QUEST_NOREWARD : choice<0 ? DialogAction.SELECTED_QUEST_NOREWARD : DialogAction.SELECTED_QUEST_REWARD1+choice;
			int extraIndex=extra>=0 ? 8+extra : 0;
			if(action==DialogAction.SELECTED_QUEST_NOREWARD && choice>=0 && extra>=0 && choice!=extra)return false; // native shared index cannot express two different choices
			npc.getController().onDialogSelect(action, page, bot, job.quest(), extraIndex);
			boolean completed = state.getStatus() != QuestStatus.REWARD;
			if (completed) PlayerBotQuestSync.accepted(owner, bot, job.quest(), true);
			return completed;
		} finally {
			if(!mission && job.turnIn())PlayerBotQuestObjectives.rewardAttempted(bot,job.quest());
			pending = null;
			retryAfter.put(key(npc, job.quest()), System.currentTimeMillis() + 30000);
			DialogService.onCloseDialog(bot, npc);
		}
	}
	static boolean ordinary(QuestTemplate template) { return template != null && (template.getCategory() == QuestCategory.QUEST || template.getCategory() == QuestCategory.IMPORTANT); }
	Npc hunt(Player owner, Player bot) {
		return hunt(owner, bot, 0);
	}
	Npc hunt(Player owner, Player bot, int missionQuest) {
		return PlayerBotPartyBehavior.hunt(owner,bot,missionQuest);
	}
	Job missionReward(Player owner, Player bot, int quest) {
		Set<Integer> required = PlayerBotQuestMetadata.rewardNpcIds(bot, quest);
		List<Npc> candidates = new ArrayList<>();
		bot.getKnownList().forEachNpc(npc -> {
			if (required.contains(npc.getNpcId()) && !blocked(npc, quest) && !npc.isDead() && npc.isSpawned() && npc.getMaster() == npc && !bot.isEnemy(npc)
				&& npc.getWorldId() == bot.getWorldId() && npc.getInstanceId() == bot.getInstanceId() && PositionUtil.isInRange(owner, npc, PlayerBotMission.range())
				&& DialogService.isInteractionAllowed(bot, npc)) candidates.add(npc);
		});
		return candidates.stream().min(Comparator.comparingDouble(n -> PositionUtil.getDistance(bot, n))).map(n -> new Job(n, quest, true)).orElse(null);
	}
	private static int rewardChoice(Player bot, PlayerBotRules.Role role, List<QuestItems> choices) {
		return PlayerBotGearPolicy.rewardChoice(bot, role, choices);
	}
	private boolean blocked(Npc npc, int quest) { return retryAfter.containsKey(key(npc, quest)); }
	private static long key(Npc npc, int quest) { return (long) npc.getObjectId() << 32 | quest & 0xffffffffL; }
	private static boolean nearby(Player owner, Player bot, Npc npc) {
		return !owner.isDead() && !npc.isDead() && npc.isSpawned() && npc.getMaster() == npc && !bot.isEnemy(npc)
			&& npc.getWorldId() == bot.getWorldId() && npc.getInstanceId() == bot.getInstanceId()
			&& PositionUtil.isInRange(owner, npc, 40) && owner.getKnownList().sees(npc) && DialogService.isInteractionAllowed(bot, npc);
	}
}
