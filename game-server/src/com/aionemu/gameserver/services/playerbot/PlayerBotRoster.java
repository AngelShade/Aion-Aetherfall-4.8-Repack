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
		return lists(List.of(account)).getOrDefault(account, List.of());
	}
	/** Bounded native XP snapshot only for identities already verified by the joined roster query. */
	static Map<Integer,Integer> populationLevels(Collection<Integer> characters) {
		if(characters.isEmpty())return Map.of();
		try(Connection connection=DatabaseFactory.getConnection()) {
			return populationLevels(connection,characters,exp->com.aionemu.gameserver.dataholders.DataManager.PLAYER_EXPERIENCE_TABLE.getLevelForExp(exp));
		}catch(SQLException e){throw new IllegalStateException("Cannot read saved population levels; no identities rerolled",e);}
	}
	static Map<Integer,Integer> populationLevels(Connection connection,Collection<Integer> characters,java.util.function.LongToIntFunction level)throws SQLException {
		var ids=new ArrayList<>(new TreeSet<>(characters));var result=new HashMap<Integer,Integer>();
		if(ids.stream().anyMatch(id->id<=0) || ids.size()>PlayerBotPopulationPolicy.MAX_RESERVE)throw new IllegalArgumentException("Invalid population identity snapshot");
		for(int start=0;start<ids.size();start+=1024) {
			var chunk=ids.subList(start,Math.min(ids.size(),start+1024));
			try(var statement=connection.prepareStatement("SELECT id,exp FROM players WHERE id IN ("+String.join(",",Collections.nCopies(chunk.size(),"?"))+")")) {
				for(int i=0;i<chunk.size();i++)statement.setInt(i+1,chunk.get(i));
				try(var rows=statement.executeQuery()){while(rows.next()) {
					int id=rows.getInt(1),value=level.applyAsInt(rows.getLong(2));
					if(!chunk.contains(id) || value<1 || value>65 || result.putIfAbsent(id,value)!=null)throw new SQLException("Invalid population XP snapshot");
				}}
			}
		}
		return Map.copyOf(result);
	}

	/** Fresh, account-scoped visibility snapshot. Archive checks share the same query as roster ownership. */
	public static Map<Integer, List<Entry>> lists(Collection<Integer> accounts) {
		if (accounts.isEmpty() || !available()) return Map.of();
		try (Connection connection = DatabaseFactory.getConnection()) {
			return lists(connection, accounts);
		} catch (SQLException e) { throw new IllegalStateException("Cannot read generated companion roster", e); }
	}

	static Map<Integer, List<Entry>> lists(Connection connection, Collection<Integer> accounts) throws SQLException {
		var requested = new TreeSet<>(accounts);
		if (requested.isEmpty()) return Map.of();
		if (requested.size() > 4096 || requested.stream().anyMatch(a -> !PlayerBotMetadata.validAccount(a)))
			throw new IllegalArgumentException("Invalid companion roster accounts");
		var result = new LinkedHashMap<Integer, List<Entry>>();
		requested.forEach(a -> result.put(a, new ArrayList<>()));
		var identities = new HashSet<Integer>();
		String placeholders = String.join(",", Collections.nCopies(requested.size(), "?"));
		try (PreparedStatement statement = connection.prepareStatement(
			"SELECT b.account_id,b.player_id,p.name,b.ready,r.account_id,r.document FROM playerbot_roster b "
			+ "LEFT JOIN players p ON p.id=b.player_id LEFT JOIN playerbot_removed r ON r.player_id=b.player_id "
			+ "WHERE b.account_id IN (" + placeholders + ") ORDER BY b.account_id,b.player_id")) {
			int index = 0;
			for (int account : requested) statement.setInt(++index, account);
			try (ResultSet rows = statement.executeQuery()) {
				while (rows.next()) {
					int account = rows.getInt(1), id = rows.getInt(2);
					if (!requested.contains(account) || id <= 0 || !identities.add(id))
						throw new SQLException("Invalid companion roster identity");
					String marker = rows.getString(6);
					if (rows.getObject(5) != null) {
						if (rows.getInt(5) != account || marker == null) throw new SQLException("Invalid archived companion owner");
						try { PlayerBotRepository.validateMarker(account, id, marker); }
						catch (java.io.IOException e) { throw new SQLException("Invalid archived companion marker", e); }
					} else {
						if (marker != null) throw new SQLException("Archived companion lacks an owner");
						result.get(account).add(new Entry(id, rows.getString(3), rows.getBoolean(4)));
					}
				}
			}
		}
		result.replaceAll((account, entries) -> List.copyOf(entries));
		return Collections.unmodifiableMap(result);
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
