-- Apply before enabling the separate generated companion roster, then restart the game server.
-- Normal offline-character companions do not require this table.
CREATE TABLE IF NOT EXISTS playerbot_roster (
  player_id INT NOT NULL,
  account_id INT NOT NULL,
  ready BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (player_id),
  KEY account_companions (account_id, ready)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Pending rows deliberately hide partially created characters from character selection.
-- Do not remove roster rows without resolving their corresponding players/inventory rows.
