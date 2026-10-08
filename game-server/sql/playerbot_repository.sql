-- PB-SCOPE-012C: native account presets and roster archive markers.
CREATE TABLE IF NOT EXISTS playerbot_saved_parties (
 account_id INT NOT NULL PRIMARY KEY,
 document LONGTEXT NOT NULL,
 updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS playerbot_removed (
 player_id INT NOT NULL PRIMARY KEY,
 account_id INT NOT NULL,
 document TEXT NOT NULL,
 updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 KEY account_removed (account_id),
 CONSTRAINT playerbot_removed_roster_fk FOREIGN KEY (player_id) REFERENCES playerbot_roster(player_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
