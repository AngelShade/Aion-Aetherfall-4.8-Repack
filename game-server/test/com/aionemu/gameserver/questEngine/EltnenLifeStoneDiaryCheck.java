package com.aionemu.gameserver.questEngine;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import com.aionemu.gameserver.dataholders.DataManager;
import com.aionemu.gameserver.dataholders.NpcData;
import com.aionemu.gameserver.dataholders.QuestsData;
import com.aionemu.gameserver.model.DialogAction;
import com.aionemu.gameserver.model.Race;
import com.aionemu.gameserver.model.gameobjects.Item;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.gameobjects.player.QuestStateList;
import com.aionemu.gameserver.model.items.storage.PlayerStorage;
import com.aionemu.gameserver.model.items.storage.StorageType;
import com.aionemu.gameserver.model.templates.QuestTemplate;
import com.aionemu.gameserver.model.templates.npc.NpcTemplate;
import com.aionemu.gameserver.questEngine.handlers.HandlerResult;
import com.aionemu.gameserver.questEngine.model.*;
import quest.eltnen._1035RefreshingtheSprings;
import quest.eltnen._1038TheShadowsCommand;

/** Real handlers/engine dispatch, isolated inventory recording; no World, DB, IDs or networking. */
public class EltnenLifeStoneDiaryCheck {
	private static int checks;
	private static Object unsafe;
	private static <T> T allocate(Class<T> type) throws Exception {
		return type.cast(unsafe.getClass().getMethod("allocateInstance", Class.class).invoke(unsafe, type));
	}
	private static void check(boolean ok, String why) {
		checks++;
		if (!ok) throw new AssertionError(why);
	}
	private static void set(Object object, Class<?> owner, String name, Object value) throws Exception {
		Field field = owner.getDeclaredField(name);
		field.setAccessible(true);
		field.set(object, value);
	}
	private static class Inventory extends PlayerStorage {
		final Map<Integer, Long> counts = new HashMap<>();
		Item owned;
		boolean reject;
		int removals;
		Inventory(Player player) { super(player, StorageType.CUBE); }
		@Override public long getItemCountByItemId(int id) { return counts.getOrDefault(id, 0L); }
		@Override public Item getItemByObjId(int id) { return owned != null && owned.getObjectId() == id ? owned : null; }
		@Override public boolean decreaseByItemId(int id, long count, QuestStatus status) {
			if (reject || getItemCountByItemId(id) < count) return false;
			counts.put(id, getItemCountByItemId(id) - count);
			removals++;
			return true;
		}
	}
	private static class SpringHandler extends _1035RefreshingtheSprings {
		int updates, movies, timers;
		@Override public void updateQuestStatus(QuestEnv env) { updates++; }
		@Override public boolean playQuestMovie(QuestEnv env, int movie) { movies++; return false; }
		// Also compiles against the previous handler, allowing a failing-before regression run.
		protected void finishSpringTimer(QuestEnv env) { timers++; }
	}
	private static class DiaryHandler extends _1038TheShadowsCommand {
		int updates;
		@Override public void updateQuestStatus(QuestEnv env) { updates++; }
		@Override public void onLevelChangedEvent(Player player) { /* native prerequisite checks covered separately */ }
	}
	private static Player player(int quest, QuestStatus status, int step, int item, long count) throws Exception {
		Player player = allocate(Player.class);
		player.setQuestStateList(new QuestStateList());
		if (status != null) {
			QuestState qs = new QuestState(quest, status);
			qs.setQuestVarById(0, step);
			qs.setQuestVarById(1, 5);
			player.getQuestStateList().addQuest(quest, qs);
		}
		Inventory inventory = new Inventory(player);
		inventory.counts.put(item, count);
		set(player, Player.class, "inventory", inventory);
		return player;
	}
	private static QuestEnv env(Player player, int quest, int target, int action) {
		return new QuestEnv(null, player, quest, action) {
			@Override public int getTargetId() { return target; }
		};
	}
	private static void stones() throws Exception {
		SpringHandler registration = new SpringHandler();
		QuestEngine.getInstance().addQuestHandler(registration);
		int[][] objects = {{700158, 2, 3, 182201014}, {700160, 7, 8, 182201024}, {700159, 10, 11, 182201025}};
		// Reproduce multiplied quest drops first: old exactly-one check rejects this genuine item.
		for (int[] object : objects) {
			Player player = player(1035, QuestStatus.START, object[1], object[3], 2);
			SpringHandler handler = new SpringHandler();
			check(handler.onDialogEvent(env(player, 1035, object[0], DialogAction.USE_OBJECT)), "Multiple owned beads must insert at " + object[0]);
			check(player.getInventory().getItemCountByItemId(object[3]) == 1, "Consume exactly one bead");
		}
		for (int[] object : objects) {
			check(QuestEngine.getInstance().getQuestNpc(object[0]).getOnTalkEvent().contains(1035), "Stone native talk registration");
			for (QuestStatus status : new QuestStatus[] {null, QuestStatus.START, QuestStatus.REWARD, QuestStatus.COMPLETE}) {
				for (int step : new int[] {0, object[1] - 1, object[1], object[1] + 1}) {
					for (long count : new long[] {0, 1, 2, 10}) {
						Player player = player(1035, status, step, object[3], count);
						Inventory inv = (Inventory) player.getInventory();
						SpringHandler handler = new SpringHandler();
						QuestEnv env = env(player, 1035, object[0], DialogAction.USE_OBJECT);
						boolean expected = status == QuestStatus.START && step == object[1] && count > 0;
						check(QuestEngine.getInstance().onCanAct(env, object[0], QuestActionType.ACTION_ITEM_USE) == expected, "Native permission admission");
						check(handler.onDialogEvent(env) == expected, "Completed native object admission");
						check(inv.getItemCountByItemId(object[3]) == count - (expected ? 1 : 0), "Item custody count");
						check(handler.updates == (expected ? 1 : 0) && inv.removals == (expected ? 1 : 0), "One transaction/update");
						check(handler.movies == (expected && object[0] == 700160 ? 1 : 0)
							&& handler.timers == handler.movies, "Desert completes timer before optional movie");
						QuestState qs = player.getQuestStateList().getQuestState(1035);
						if (qs != null) check(qs.getQuestVarById(0) == (expected ? object[2] : step)
							&& qs.getQuestVarById(1) == 5 && qs.getStatus() == status, "Only relevant saved step changes");
						if (expected) check(!handler.onDialogEvent(env) && inv.removals == 1, "No duplicate insertion");
						int before = qs == null ? -1 : qs.getQuestVarById(0);
						handler.onMovieEndEvent(env, 31);
						check((qs == null || qs.getQuestVarById(0) == before) && inv.removals == (expected ? 1 : 0), "Movie acknowledgement owns no credit/items");
					}
				}
			}
			Player failed = player(1035, QuestStatus.START, object[1], object[3], 1);
			((Inventory) failed.getInventory()).reject = true;
			SpringHandler handler = new SpringHandler();
			check(!handler.onDialogEvent(env(failed, 1035, object[0], DialogAction.USE_OBJECT)), "Rejected native removal cannot progress");
			check(failed.getQuestStateList().getQuestState(1035).getQuestVarById(0) == object[1]
				&& handler.updates == 0 && handler.movies == 0 && handler.timers == 0, "Rejected consumption keeps progress/timer");
		}
		Player timed = player(1035, QuestStatus.START, 7, 182201024, 1);
		SpringHandler handler = new SpringHandler();
		check(handler.onQuestTimerEndEvent(env(timed, 1035, 0, 0))
			&& timed.getQuestStateList().getQuestState(1035).getQuestVarById(0) == 6, "Expired timer retains retry step");
		check(!handler.onDialogEvent(env(timed, 1035, 700160, DialogAction.USE_OBJECT)), "Expired timer cannot insert");
		handler.onMovieEndEvent(env(timed, 1035, 0, 0), 31);
		check(timed.getQuestStateList().getQuestState(1035).getQuestVarById(0) == 6, "Late movie cannot bypass expired timer");
	}
	private static void diary() throws Exception {
		DiaryHandler registration = new DiaryHandler();
		QuestEngine.getInstance().addQuestHandler(registration);
		Player acquired = player(1038, QuestStatus.START, 2, 182201007, 1);
		QuestEngine.getInstance().onItemGet(acquired, 182201007);
		check(acquired.getQuestStateList().getQuestState(1038).getQuestVarById(0) == 3, "Actual diary acquisition must dispatch to step 3");
		check(registration.updates == 1, "Acquisition emits one quest update");
		QuestEngine.getInstance().onItemGet(acquired, 182201007);
		check(registration.updates == 1, "Repeated item notification grants no duplicate progress");
		for (QuestStatus status : new QuestStatus[] {null, QuestStatus.START, QuestStatus.REWARD, QuestStatus.COMPLETE}) {
			for (int step = 0; step <= 7; step++) {
				for (long count : new long[] {0, 1, 2}) {
					Player player = player(1038, status, step, 182201007, count);
					DiaryHandler handler = new DiaryHandler();
					boolean expected = status == QuestStatus.START && step == 2 && count > 0;
					QuestEnv env = env(player, 1038, 700172, DialogAction.USE_OBJECT);
					check(QuestEngine.getInstance().onCanAct(env, 700172, QuestActionType.ACTION_ITEM_USE)
						== (status == QuestStatus.START && step == 2 && count == 0), "Corpse permission owns required step/custody");
					check(handler.onGetItemEvent(env) == expected, "Diary acquisition needs real owned item");
					check(handler.updates == (expected ? 1 : 0), "Acquisition update count");
					check(player.getInventory().getItemCountByItemId(182201007) == count, "Diary retained for native reading/turn-in");
					QuestState qs = player.getQuestStateList().getQuestState(1038);
					if (qs != null) check(qs.getQuestVarById(0) == (expected ? 3 : step)
						&& qs.getQuestVarById(1) == 5 && qs.getStatus() == status, "Existing state preserved");
					Player saved = player(1038, status, step, 182201007, count);
					new DiaryHandler().onEnterWorldEvent(env(saved, 1038, 0, 0));
					QuestState restored = saved.getQuestStateList().getQuestState(1038);
					check(restored == null || restored.getQuestVarById(0) == (expected ? 3 : step), "Saved diary repair only affects held step 2");
				}
			}
		}
		Item item = allocate(Item.class);
		var template = new com.aionemu.gameserver.model.templates.item.ItemTemplate() {
			@Override public int getTemplateId() { return 182201007; }
		};
		set(item, Item.class, "itemTemplate", template);
		Player holder = player(1038, QuestStatus.START, 2, 182201007, 1);
		((Inventory) holder.getInventory()).owned = item;
		DiaryHandler handler = new DiaryHandler();
		check(handler.onItemUseEvent(env(holder, 1038, 0, 0), item) == HandlerResult.UNKNOWN, "Native ReadAction remains available");
		check(holder.getQuestStateList().getQuestState(1038).getQuestVarById(0) == 3 && handler.updates == 1, "Reading heals already-held diary state");
		Player foreign = player(1038, QuestStatus.START, 2, 182201007, 1);
		check(handler.onItemUseEvent(env(foreign, 1038, 0, 0), item) == HandlerResult.UNKNOWN
			&& foreign.getQuestStateList().getQuestState(1038).getQuestVarById(0) == 2, "Foreign item must not advance journal");
	}
	public static void main(String[] args) throws Exception {
		Field field = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
		field.setAccessible(true);
		unsafe = field.get(null);
		DataManager.QUEST_DATA = new QuestsData() {
			@Override public QuestTemplate getQuestById(int id) {
				return new QuestTemplate() {
					@Override public Race getRacePermitted() { return Race.ELYOS; }
				};
			}
		};
		DataManager.NPC_DATA = new NpcData() {
			@Override public NpcTemplate getNpcTemplate(int id) {
				return new NpcTemplate() {
					@Override public String getAiName() { return "quest_use_item"; }
				};
			}
		};
		if (args.length == 0 || args[0].equals("stones")) stones();
		if (args.length == 0 || args[0].equals("diary")) diary();
		System.out.println("ELTNEN LIFE STONE/DIARY CHECK PASSED: " + checks + " checks");
		System.out.println("OK: " + checks + " Eltnen life stone and diary checks");
	}
}
