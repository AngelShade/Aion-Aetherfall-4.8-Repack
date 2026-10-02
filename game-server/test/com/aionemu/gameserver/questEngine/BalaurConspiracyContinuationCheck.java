package com.aionemu.gameserver.questEngine;

import com.aionemu.gameserver.model.DialogAction;
import com.aionemu.gameserver.model.gameobjects.VisibleObject;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.gameobjects.player.QuestStateList;
import com.aionemu.gameserver.questEngine.model.*;
import com.aionemu.gameserver.world.WorldPosition;
import quest.eltnen._1043BalaurConspiracy;

/** Regression for the missing movie-to-dialog-to-return path. No database or server needed. */
public class BalaurConspiracyContinuationCheck {
	private static int checks;
	private static void check(boolean condition, String message) {
		checks++;
		if (!condition) throw new AssertionError(message);
	}
	private static Player player(int world, QuestStatus status, int step) throws Exception {
		var field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
		field.setAccessible(true);
		Player player = (Player) ((sun.misc.Unsafe) field.get(null)).allocateInstance(Player.class);
		player.setQuestStateList(new QuestStateList());
		var position = VisibleObject.class.getDeclaredField("position");
		position.setAccessible(true);
		position.set(player, new WorldPosition(world));
		if (status != null) {
			QuestState quest = new QuestState(1043, status);
			quest.setQuestVarById(0, step);
			quest.setQuestVarById(1, 4);
			player.getQuestStateList().addQuest(1043, quest);
		}
		return player;
	}
	private static QuestEnv dialog(Player player, int action) {
		return new QuestEnv(null, player, 1043, action) {
			@Override public int getTargetId() { return 204044; }
		};
	}
	private static class Handler extends _1043BalaurConspiracy {
		int shownPage;
		int openedContinuations;
		int returns;
		@Override protected void showDefenseContinuation(Player player) {
			openedContinuations++;
			onDialogEvent(dialog(player, DialogAction.USE_OBJECT));
		}
		@Override public boolean sendQuestDialog(QuestEnv env, int page) { shownPage = page; return true; }
		@Override public void updateQuestStatus(QuestEnv env) { /* suppress networking in the fixture */ }
		@Override public boolean closeDialogWindow(QuestEnv env) { return true; }
		@Override protected void returnToEltnen(Player player) { returns++; }
	}
	public static void main(String[] args) throws Exception {
		com.aionemu.gameserver.dataholders.DataManager.QUEST_DATA = new com.aionemu.gameserver.dataholders.QuestsData();
		Player completedDefense = player(310040000, QuestStatus.START, 4);
		Handler handler = new Handler();
		handler.onMovieEndEvent(new QuestEnv(null, completedDefense, 1043), 157);
		check(handler.openedContinuations == 1 && handler.shownPage == 2034, "Ending movie must open Kimeia's return dialog");
		check(completedDefense.getQuestStateList().getQuestState(1043).getStatus() == QuestStatus.START, "Movie must not grant rewards");
		check(handler.onDialogEvent(dialog(completedDefense, DialogAction.USE_OBJECT)), "Kimeia must be clickable without a quest marker");
		check(handler.onDialogEvent(dialog(completedDefense, DialogAction.SETPRO4)), "Return action must be accepted");
		QuestState state = completedDefense.getQuestStateList().getQuestState(1043);
		check(state.getStatus() == QuestStatus.REWARD && state.getQuestVarById(0) == 4 && state.getQuestVarById(1) == 4,
			"Return must preserve defense progress and leave final reward pending");
		check(handler.returns == 1, "Return must leave the instance");
		check(!handler.onDialogEvent(dialog(completedDefense, DialogAction.SETPRO4)) && handler.returns == 1,
			"Repeated return must not repeat progression");
		for (int world : new int[] { 210020000, 310040000 }) {
			for (QuestStatus status : new QuestStatus[] { null, QuestStatus.START, QuestStatus.REWARD, QuestStatus.COMPLETE }) {
				for (int step : new int[] { 2, 3, 4 }) {
					for (int movie : new int[] { 35, 157 }) {
						if (world == 310040000 && status == QuestStatus.START && step == 4 && movie == 157) continue;
						Handler ignored = new Handler();
						Player fixture = player(world, status, step);
						ignored.onMovieEndEvent(new QuestEnv(null, fixture, 1043), movie);
						check(ignored.openedContinuations == 0 && ignored.returns == 0, "Unexpected movie continuation");
					}
				}
			}
		}
		Handler early = new Handler();
		check(!early.onDialogEvent(dialog(player(310040000, QuestStatus.START, 3), DialogAction.SETPRO4)) && early.returns == 0,
			"Active defense must not be skipped");
		System.out.println("BALAUR CONTINUATION CHECK PASSED: " + checks + " checks");
	}
}
