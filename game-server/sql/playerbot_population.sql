-- PB-SCOPE-012D. Apply/import offline with tools/migrate-playerbot-population.py.
-- Native players/roster remain authoritative. Members may be pending creation intents.
CREATE TABLE IF NOT EXISTS playerbot_population_state (
 id TINYINT NOT NULL PRIMARY KEY,
 version INT NOT NULL,
 target INT NOT NULL,
 target_until BIGINT NOT NULL,
 revision BIGINT NOT NULL,
 import_digest CHAR(64) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS playerbot_population_accounts (
 account_id INT NOT NULL PRIMARY KEY,
 scope VARCHAR(8) NOT NULL,
 name VARCHAR(255) NOT NULL,
 membership TINYINT NOT NULL,
 created BIGINT NOT NULL,
 race VARCHAR(16) NOT NULL,
 gender VARCHAR(16) NOT NULL,
 appearance TEXT NOT NULL,
 desired INT NOT NULL,
 KEY population_scope (scope)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS playerbot_population_members (
 player_id INT NOT NULL PRIMARY KEY,
 account_id INT NOT NULL,
 ordinal INT NOT NULL,
 UNIQUE KEY population_order (account_id,ordinal),
 CONSTRAINT population_member_account FOREIGN KEY (account_id) REFERENCES playerbot_population_accounts(account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS playerbot_world_residents (
 player_id INT NOT NULL PRIMARY KEY,
 account_id INT NOT NULL,
 map_id INT NOT NULL,
 x FLOAT NOT NULL,
 y FLOAT NOT NULL,
 z FLOAT NOT NULL,
 heading TINYINT NOT NULL,
 CONSTRAINT population_resident_roster FOREIGN KEY (player_id) REFERENCES playerbot_roster(player_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS playerbot_population_events (
 player_id INT NOT NULL PRIMARY KEY,
 present_until BIGINT NOT NULL,
 offline_until BIGINT NOT NULL,
 teleport_at BIGINT NOT NULL,
 placed_level INT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
