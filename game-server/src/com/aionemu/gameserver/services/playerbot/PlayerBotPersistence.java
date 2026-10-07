package com.aionemu.gameserver.services.playerbot;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;

import com.aionemu.commons.database.DatabaseFactory;
import com.aionemu.gameserver.dao.*;
import com.aionemu.gameserver.model.gameobjects.player.Player;
import com.aionemu.gameserver.model.gameobjects.Persistable.PersistentState;

/** Private character progress only. Shared account/legion storage and passports are never saved by bots. */
final class PlayerBotPersistence {
	record Home(int map, float x, float y, float z, byte heading, int worldOwner) {
		static Home of(Player player) {
			var p = player.getCommonData();
			return new Home(p.getMapId(), p.getX(), p.getY(), p.getZ(), p.getHeading(), p.getWorldOwnerId());
		}
	}

	static void save(Player bot, Home home) throws SQLException {
		if (home == null) throw new SQLException("Missing companion home checkpoint");
		// Several legacy DAOs catch their SQL errors and return success. For companions, every
		// progress-table write must throw on failure so dismissal cannot release an unsaved character.
		try (Connection connection = DatabaseFactory.getConnection()) {
			connection.setAutoCommit(false);
			try {
				var inventory = InventoryDAO.storeCompanionInventory(connection, bot);
				saveProgress(connection, bot, home);
				var metadata = PlayerBotMetadata.pending(bot.getAccount().getId(),bot.getObjectId());
				PlayerBotMetadata.store(connection,metadata);
				connection.commit();
				PlayerBotMetadata.committed(metadata);
				InventoryDAO.companionInventoryCommitted(inventory);
			} catch (SQLException | RuntimeException e) {
				try { connection.rollback(); } catch (SQLException rollback) { e.addSuppressed(rollback); }
				InventoryDAO.markCompanionInventoryDirty(bot);
				throw e;
			}
		}
	}

	/** Legacy load DAOs sometimes log and return empty data. Refuse destructive saves of such a partial character. */
	static void validateLoaded(Player bot, long loadStarted) throws SQLException {
		if (bot.getAbyssRank() == null || bot.getNpcFactions() == null || bot.getSkillList() == null || bot.getQuestStateList() == null)
			throw new SQLException("Companion character progress was not completely loaded");
		try (Connection connection = DatabaseFactory.getConnection()) {
			verifyIds(connection, bot, "SELECT skill_id FROM player_skills WHERE player_id=?",
				bot.getSkillList().getAllSkills().stream().map(e -> e.getSkillId()).collect(java.util.stream.Collectors.toSet()));
			verifyIds(connection, bot, "SELECT quest_id FROM player_quests WHERE player_id=?",
				bot.getQuestStateList().getAllQuestState().stream().map(e -> e.getQuestId()).collect(java.util.stream.Collectors.toSet()));
			verifyIds(connection, bot, "SELECT item_unique_id FROM inventory WHERE item_owner=? AND (item_location IN (0,1) OR item_location BETWEEN 32 AND 43 OR item_location BETWEEN 60 AND 79)",
				bot.getAllItems().stream().map(e -> e.getObjectId()).collect(java.util.stream.Collectors.toSet()));
			var cooldowns = bot.getSkillCoolDowns();
			verifyIds(connection, bot, "SELECT cooldown_id FROM player_cooldowns WHERE player_id=? AND reuse_delay>" + System.currentTimeMillis(),
				cooldowns == null ? java.util.Set.of() : java.util.Set.copyOf(cooldowns.keySet()));
			verifyIds(connection, bot, "SELECT delay_id FROM item_cooldowns WHERE player_id=? AND reuse_time>" + System.currentTimeMillis(),
				java.util.Set.copyOf(bot.getItemCoolDowns().keySet()));
			// Legacy life loading catches errors and can otherwise turn a saved corpse into a healthy bot.
			try (PreparedStatement statement = connection.prepareStatement(PlayerLifeStatsDAO.SELECT_QUERY)) {
				statement.setInt(1, bot.getObjectId());
				try (var rows = statement.executeQuery()) {
					if (!rows.next()) throw new SQLException("Companion life stats were not loaded completely");
					bot.getLifeStats().setCurrentHp(rows.getInt("hp"));
					bot.getLifeStats().setCurrentMp(rows.getInt("mp"));
					bot.getLifeStats().setCurrentFp(rows.getInt("fp"));
				}
			}
			verifyEffects(connection, bot, loadStarted);
		}
	}

	private static void verifyEffects(Connection connection, Player bot, long loadStarted) throws SQLException {
		var loaded = bot.getEffectController().getAbnormalEffects().stream().map(e -> e.getSkillId()).collect(java.util.stream.Collectors.toSet());
		try (PreparedStatement statement = connection.prepareStatement(PlayerEffectsDAO.SELECT_QUERY)) {
			statement.setInt(1, bot.getObjectId());
			try (var rows = statement.executeQuery()) {
				while (rows.next()) {
					// A short effect may legitimately finish while the full character is being loaded.
					if (rows.getInt("remaining_time") <= Math.max(0, System.currentTimeMillis() - loadStarted)) continue;
					int id = rows.getInt("skill_id");
					var template = com.aionemu.gameserver.dataholders.DataManager.SKILL_DATA.getSkillTemplate(id);
					if (template == null) throw new SQLException("Companion has an unknown saved effect: " + id);
					String type = rows.getString("force_type");
					var force = type == null ? null : com.aionemu.gameserver.skillengine.model.Effect.ForceType.getInstance(type);
					if (com.aionemu.gameserver.services.event.EventService.getInstance().isInactiveEventForceType(force)) continue;
					if (com.aionemu.gameserver.configs.main.CustomConfig.ABYSSXFORM_LOGOUT && template.isDeityAvatar()
						&& rows.getLong("end_time") <= System.currentTimeMillis()) continue;
					if (!loaded.contains(id)) throw new SQLException("Companion has an incompletely loaded saved effect: " + id);
				}
			}
		}
	}

	private static void verifyIds(Connection connection, Player bot, String query, java.util.Set<Integer> loaded) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(query)) {
			statement.setInt(1, bot.getObjectId());
			try (var rows = statement.executeQuery()) {
				while (rows.next()) if (!loaded.contains(rows.getInt(1))) throw new SQLException("Companion has incomplete saved skills, quests or inventory");
			}
		}
	}

	private static void saveProgress(Connection connection, Player bot, Home home) throws SQLException {
		// Leave the companion at its pre-recruitment home on the next human login.
		// Never persist an unregistered private instance or a temporary companion position.
		if(PlayerBotRoster.contains(bot.getObjectId()))try(PreparedStatement statement=connection.prepareStatement("UPDATE players SET player_class=? WHERE id=?")){statement.setString(1,bot.getPlayerClass().name());statement.setInt(2,bot.getObjectId());statement.executeUpdate();}
		try (PreparedStatement statement = connection.prepareStatement(
			"UPDATE players SET exp=?,recoverexp=?,dp=?,soul_sickness=?,reposte_energy=?,x=?,y=?,z=?,heading=?,world_id=?,world_owner=? WHERE id=?")) {
			var data = bot.getCommonData();
			statement.setLong(1, data.getExp()); statement.setLong(2, data.getExpRecoverable());
			statement.setInt(3, data.getDp()); statement.setInt(4, data.getDeathCount());
			statement.setLong(5, data.getCurrentReposeEnergy());
			statement.setFloat(6, home.x()); statement.setFloat(7, home.y()); statement.setFloat(8, home.z());
			statement.setByte(9, home.heading()); statement.setInt(10, home.map()); statement.setInt(11, home.worldOwner());
			statement.setInt(12, bot.getObjectId());
			if (statement.executeUpdate() == 0) {
				// Drivers configured to count changed rows return zero for an unchanged checkpoint.
				try (PreparedStatement exists = connection.prepareStatement("SELECT id FROM players WHERE id=?")) {
					exists.setInt(1, bot.getObjectId());
					try (var row = exists.executeQuery()) {
						if (!row.next()) throw new SQLException("Companion character disappeared during save");
					}
				}
			}
		}
		try (PreparedStatement statement = connection.prepareStatement(
			"INSERT INTO player_life_stats (player_id,hp,mp,fp) VALUES (?,?,?,?) ON DUPLICATE KEY UPDATE hp=?,mp=?,fp=?")) {
			statement.setInt(1, bot.getObjectId());
			for (int offset : new int[] { 2, 5 }) {
				statement.setInt(offset, bot.getLifeStats().getCurrentHp());
				statement.setInt(offset + 1, bot.getLifeStats().getCurrentMp());
				statement.setInt(offset + 2, bot.getLifeStats().getCurrentFp());
			}
			statement.executeUpdate();
		}
		deleteForPlayer(connection, "DELETE FROM player_skills WHERE player_id=?", bot);
		try (PreparedStatement statement = connection.prepareStatement(PlayerSkillListDAO.INSERT_QUERY)) {
			for (var entry : List.copyOf(bot.getSkillList().getAllSkills())) {
				if (entry.getPersistentState() == PersistentState.DELETED || entry.getPersistentState() == PersistentState.NOACTION) continue;
				statement.setInt(1, bot.getObjectId()); statement.setInt(2, entry.getSkillId()); statement.setInt(3, entry.getSkillLevel());
				statement.addBatch();
			}
			statement.executeBatch();
		}
		try (PreparedStatement statement = connection.prepareStatement(PlayerQuestListDAO.DELETE_QUERY)) {
			for (int id : List.copyOf(bot.getQuestStateList().getDeletedQuestIds())) {
				statement.setInt(1, bot.getObjectId()); statement.setInt(2, id); statement.addBatch();
			}
			for (var state : List.copyOf(bot.getQuestStateList().getAllQuestState())) {
				if (state.getPersistentState() != PersistentState.DELETED) continue;
				statement.setInt(1, bot.getObjectId()); statement.setInt(2, state.getQuestId()); statement.addBatch();
			}
			statement.executeBatch();
		}
		try (PreparedStatement statement = connection.prepareStatement(PlayerQuestListDAO.INSERT_QUERY
			+ " ON DUPLICATE KEY UPDATE status=?,quest_vars=?,flags=?,complete_count=?,next_repeat_time=?,reward=?,complete_time=?")) {
			for (var state : List.copyOf(bot.getQuestStateList().getAllQuestState())) {
				if (state.getPersistentState() == PersistentState.DELETED) continue;
				statement.setInt(1, bot.getObjectId()); statement.setInt(2, state.getQuestId());
				for (int offset : new int[] { 3, 10 }) {
					statement.setString(offset, state.getStatus().name()); statement.setInt(offset + 1, state.getQuestVars().getQuestVars());
					statement.setInt(offset + 2, state.getFlags()); statement.setInt(offset + 3, state.getCompleteCount());
					statement.setObject(offset + 4, state.getNextRepeatTime(), Types.TIMESTAMP);
					statement.setObject(offset + 5, state.getRewardGroup(), Types.SMALLINT);
					statement.setObject(offset + 6, state.getLastCompleteTime(), Types.TIMESTAMP);
				}
				statement.addBatch();
			}
			statement.executeBatch();
		}
		deleteForPlayer(connection, PlayerCooldownsDAO.DELETE_QUERY, bot);
		try (PreparedStatement statement = connection.prepareStatement(PlayerCooldownsDAO.INSERT_QUERY)) {
			var cooldowns = bot.getSkillCoolDowns();
			for (var entry : new java.util.HashMap<>(cooldowns == null ? java.util.Map.<Integer, Long>of() : cooldowns).entrySet()) {
				if (entry.getValue() == null || entry.getValue() <= System.currentTimeMillis()) continue;
				statement.setInt(1, bot.getObjectId()); statement.setInt(2, entry.getKey()); statement.setLong(3, entry.getValue()); statement.addBatch();
			}
			statement.executeBatch();
		}
		deleteForPlayer(connection, ItemCooldownsDAO.DELETE_QUERY, bot);
		try (PreparedStatement statement = connection.prepareStatement(ItemCooldownsDAO.INSERT_QUERY)) {
			for (var entry : new java.util.HashMap<>(bot.getItemCoolDowns()).entrySet()) {
				var cooldown = entry.getValue();
				if (cooldown == null || cooldown.getReuseTime() <= System.currentTimeMillis()) continue;
				statement.setInt(1, bot.getObjectId()); statement.setInt(2, entry.getKey()); statement.setInt(3, cooldown.getUseDelay());
				statement.setLong(4, cooldown.getReuseTime()); statement.addBatch();
			}
			statement.executeBatch();
		}
		deleteForPlayer(connection, PlayerEffectsDAO.DELETE_QUERY, bot);
		try (PreparedStatement statement = connection.prepareStatement(PlayerEffectsDAO.INSERT_QUERY)) {
			for (var effect : List.copyOf(bot.getEffectController().getAbnormalEffects())) {
				if (!effect.canSaveOnLogout() || effect.getRemainingTimeMillis() <= 0) continue;
				int criticals = 0;
				for (var template : effect.getEffectTemplates())
					if (effect.isMagicalCritical(template.getPosition())) criticals |= 1 << (template.getPosition() - 1);
				statement.setInt(1, bot.getObjectId()); statement.setInt(2, effect.getSkillId()); statement.setInt(3, effect.getSkillLevel());
				statement.setInt(4, (int) Math.min(Integer.MAX_VALUE, effect.getRemainingTimeMillis())); statement.setLong(5, effect.getEndTime());
				statement.setString(6, effect.getForceType() == null ? null : effect.getForceType().getName());
				statement.setInt(7, criticals); statement.addBatch();
			}
			statement.executeBatch();
		}
		saveFactions(connection, bot);
		saveRank(connection, bot);
	}

	private static void saveFactions(Connection connection, Player bot) throws SQLException {
		if (bot.getNpcFactions() == null) return; // new generated character, before its first full load
		try (PreparedStatement statement = connection.prepareStatement(PlayerNpcFactionsDAO.INSERT_QUERY
			+ " ON DUPLICATE KEY UPDATE active=?,time=?,state=?,quest_id=?")) {
			for (var faction : List.copyOf(bot.getNpcFactions().getNpcFactions())) {
				statement.setInt(1, bot.getObjectId()); statement.setInt(2, faction.getId());
				for (int offset : new int[] { 3, 7 }) {
					statement.setBoolean(offset, faction.isActive()); statement.setInt(offset + 1, faction.getTime());
					statement.setString(offset + 2, faction.getState().name()); statement.setInt(offset + 3, faction.getQuestId());
				}
				statement.addBatch();
			}
			statement.executeBatch();
		}
	}
	private static void saveRank(Connection connection, Player bot) throws SQLException {
		var rank = bot.getAbyssRank();
		if (rank == null) return; // generated character has no rank until its first full load
		if (rank.getPersistentState() != PersistentState.NEW && rank.getPersistentState() != PersistentState.UPDATE_REQUIRED) return;
		try (PreparedStatement statement = connection.prepareStatement("INSERT INTO abyss_rank (player_id,daily_ap,weekly_ap,ap,`rank`,daily_kill,weekly_kill,all_kill,max_rank,last_kill,last_ap,last_update,daily_gp,weekly_gp,gp,last_gp)"
			+ " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE daily_ap=?,weekly_ap=?,ap=?,`rank`=?,daily_kill=?,weekly_kill=?,all_kill=?,max_rank=?,last_kill=?,last_ap=?,last_update=?,daily_gp=?,weekly_gp=?,gp=?,last_gp=?")) {
			statement.setInt(1, bot.getObjectId());
			for (int offset : new int[] { 2, 17 }) {
				statement.setInt(offset, rank.getDailyAP()); statement.setInt(offset + 1, rank.getWeeklyAP()); statement.setInt(offset + 2, rank.getAp());
				statement.setInt(offset + 3, rank.getRank().getId()); statement.setInt(offset + 4, rank.getDailyKill());
				statement.setInt(offset + 5, rank.getWeeklyKill()); statement.setInt(offset + 6, rank.getAllKill());
				statement.setInt(offset + 7, rank.getMaxRank()); statement.setInt(offset + 8, rank.getLastKill());
				statement.setInt(offset + 9, rank.getLastAP()); statement.setLong(offset + 10, rank.getLastUpdate());
				statement.setInt(offset + 11, rank.getDailyGP()); statement.setInt(offset + 12, rank.getWeeklyGP());
				statement.setInt(offset + 13, rank.getCurrentGP()); statement.setInt(offset + 14, rank.getLastGP());
			}
			statement.executeUpdate();
		}
	}

	private static void deleteForPlayer(Connection connection, String sql, Player bot) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setInt(1, bot.getObjectId()); statement.executeUpdate();
		}
	}
}
