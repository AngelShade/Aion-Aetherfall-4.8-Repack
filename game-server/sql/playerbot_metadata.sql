-- PB-SCOPE-012A. Apply offline before installing the companion metadata DAO.
-- Legacy care/gear files are imported by the guarded migration tool, never removed.
CREATE TABLE IF NOT EXISTS playerbot_metadata (
 player_id INT NOT NULL,
 account_id INT NOT NULL,
 section VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 revision BIGINT NOT NULL,
 settings LONGTEXT NOT NULL,
 updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 PRIMARY KEY (player_id,section),
 KEY account_bot_metadata (account_id,player_id),
 CONSTRAINT playerbot_metadata_player_fk FOREIGN KEY (player_id) REFERENCES players(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
