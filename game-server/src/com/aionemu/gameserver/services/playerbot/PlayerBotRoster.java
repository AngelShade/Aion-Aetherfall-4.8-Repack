package com.aionemu.gameserver.services.playerbot;

import java.sql.*;
import java.util.*;

import com.aionemu.commons.database.DatabaseFactory;

/** Dedicated generated characters share character persistence but never normal character slots. */
public final class PlayerBotRoster {
	public record Entry(int id, String name, boolean ready) {}
	private static volatile Boolean schemaAvailable;
	private PlayerBotRoster() {}

	/** Optional schema: only a confirmed missing table permits the ordinary-character fallback. */
	public static boolean available() {
		Boolean known = schemaAvailable;
		if (known != null) return known;
		synchronized (PlayerBotRoster.class) {
			if (schemaAvailable != null) return schemaAvailable;
			try (Connection connection = DatabaseFactory.getConnection();
				PreparedStatement statement = connection.prepareStatement("SELECT player_id FROM playerbot_roster LIMIT 0");
				ResultSet ignored = statement.executeQuery()) {
				schemaAvailable = true;
			} catch (SQLException e) {
				if (!"42S02".equals(e.getSQLState()) && e.getErrorCode() != 1146)
					throw new IllegalStateException("Cannot verify the companion roster; refusing to expose generated characters", e);
				schemaAvailable = false;
			}
			return schemaAvailable;
		}
	}

	public static String ordinaryCharactersClause() {
		return available() ? " AND NOT EXISTS (SELECT 1 FROM playerbot_roster b WHERE b.player_id=players.id)" : "";
	}

	public static List<Entry> list(int account) {
		if (!available()) return List.of();
		List<Entry> result = new ArrayList<>();
		try (Connection connection = DatabaseFactory.getConnection(); PreparedStatement statement = connection.prepareStatement(
			"SELECT b.player_id,p.name,b.ready FROM playerbot_roster b LEFT JOIN players p ON p.id=b.player_id WHERE b.account_id=? ORDER BY b.player_id")) {
			statement.setInt(1, account);
			try (ResultSet rows = statement.executeQuery()) {
				while (rows.next()) result.add(new Entry(rows.getInt(1), rows.getString(2), rows.getBoolean(3)));
			}
			return List.copyOf(result);
		} catch (SQLException e) { throw new IllegalStateException("Cannot read generated companion roster", e); }
	}

	static boolean contains(int character) {
		if (!available()) return false;
		try (Connection connection = DatabaseFactory.getConnection(); PreparedStatement statement = connection.prepareStatement(
			"SELECT player_id FROM playerbot_roster WHERE player_id=?")) {
			statement.setInt(1, character);
			try (ResultSet row = statement.executeQuery()) { return row.next(); }
		} catch (SQLException e) { throw new IllegalStateException("Cannot verify generated companion identity", e); }
	}

	/** Reserve visibility before creating the player row; failures stay quarantined as pending. */
	static void reserve(int account, int character) throws SQLException {
		if (!available()) throw new SQLException("Apply game-server/sql/playerbots.sql and restart before creating dedicated companions");
		try (Connection connection = DatabaseFactory.getConnection(); PreparedStatement statement = connection.prepareStatement(
			"INSERT INTO playerbot_roster (player_id,account_id,ready) VALUES (?,?,FALSE)")) {
			statement.setInt(1, character); statement.setInt(2, account);
			if (statement.executeUpdate() != 1) throw new SQLException("Cannot reserve generated companion");
		}
	}

	static void ready(int account, int character) throws SQLException {
		try (Connection connection = DatabaseFactory.getConnection(); PreparedStatement statement = connection.prepareStatement(
			"UPDATE playerbot_roster SET ready=TRUE WHERE player_id=? AND account_id=? AND ready=FALSE")) {
			statement.setInt(1, character); statement.setInt(2, account);
			if (statement.executeUpdate() != 1) throw new SQLException("Cannot finalize generated companion");
		}
	}
}
