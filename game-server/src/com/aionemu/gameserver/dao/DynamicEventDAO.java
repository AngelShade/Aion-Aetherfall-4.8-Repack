package com.aionemu.gameserver.dao;

import java.sql.Connection;
import java.sql.Date;
import java.sql.SQLException;
import java.time.LocalDate;

/**
 * Daily world-boss chests of the dynamic events (config/dynamic-events/README.md): once per character per chain per day,
 * and a bonus once per account per day, as Guild Wars 2's world bosses pay. A claim is one INSERT IGNORE, so two kills
 * on the same day, or a retried write, can never pay twice.
 */
public final class DynamicEventDAO {

	public static void prepare(Connection c) throws SQLException {
		try (var s = c.createStatement()) {
			s.executeUpdate("CREATE TABLE IF NOT EXISTS dynamic_event_chests (player_id INT NOT NULL,chain_id VARCHAR(40) NOT NULL,claim_day DATE NOT NULL,"
				+ "PRIMARY KEY(player_id,chain_id,claim_day)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
			s.executeUpdate("CREATE TABLE IF NOT EXISTS dynamic_event_bonus (account_id INT NOT NULL,claim_day DATE NOT NULL,PRIMARY KEY(account_id,claim_day)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
		}
	}

	/** True when this character had not opened this chain's chest today; the row is the claim. */
	public static boolean claimChest(Connection c, int player, String chain, LocalDate day) throws SQLException {
		try (var s = c.prepareStatement("INSERT IGNORE INTO dynamic_event_chests (player_id,chain_id,claim_day) VALUES (?,?,?)")) {
			s.setInt(1, player);
			s.setString(2, chain);
			s.setDate(3, Date.valueOf(day));
			return s.executeUpdate() == 1;
		}
	}

	/** True when this account had not opened a bonus chest today. */
	public static boolean claimBonus(Connection c, int account, LocalDate day) throws SQLException {
		try (var s = c.prepareStatement("INSERT IGNORE INTO dynamic_event_bonus (account_id,claim_day) VALUES (?,?)")) {
			s.setInt(1, account);
			s.setDate(2, Date.valueOf(day));
			return s.executeUpdate() == 1;
		}
	}

	private DynamicEventDAO() {
	}
}
