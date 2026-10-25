-- Additive Aetherfall website migration. The initializer selects the configured native databases.
-- Existing accounts, characters, balances and game tables are never replaced.
USE aetherfall_ls;
CREATE TABLE IF NOT EXISTS website_account_profiles (
  account_id INT NOT NULL PRIMARY KEY,
  email VARCHAR(100) NOT NULL DEFAULT '',
  donate_coin BIGINT UNSIGNED NOT NULL DEFAULT 0,
  login_flag_web TINYINT NOT NULL DEFAULT 0,
  CONSTRAINT website_profile_account FOREIGN KEY (account_id)
    REFERENCES account_data(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

USE aetherfall_gs;
CREATE TABLE IF NOT EXISTS website_character_activity (
  player_id INT NOT NULL PRIMARY KEY,
  last_login DATETIME NULL,
  last_logout DATETIME NULL,
  session_started DATETIME NULL,
  total_seconds BIGINT UNSIGNED NOT NULL DEFAULT 0,
  CONSTRAINT website_activity_player FOREIGN KEY (player_id)
    REFERENCES players(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Capture real transitions only. Existing historical activity is not invented.
DELIMITER //
CREATE TRIGGER IF NOT EXISTS website_player_activity_update AFTER UPDATE ON players
FOR EACH ROW
BEGIN
  IF OLD.online <> NEW.online THEN
    IF NEW.online = 1 THEN
      INSERT INTO website_character_activity(player_id,last_login,session_started)
      VALUES(NEW.id,UTC_TIMESTAMP(),UTC_TIMESTAMP())
      ON DUPLICATE KEY UPDATE last_login=UTC_TIMESTAMP(),session_started=UTC_TIMESTAMP();
    ELSE
      INSERT INTO website_character_activity(player_id,last_logout)
      VALUES(NEW.id,UTC_TIMESTAMP())
      ON DUPLICATE KEY UPDATE
        total_seconds=total_seconds+IF(session_started IS NULL,0,GREATEST(0,TIMESTAMPDIFF(SECOND,session_started,UTC_TIMESTAMP()))),
        last_logout=UTC_TIMESTAMP(),session_started=NULL;
    END IF;
  END IF;
END//
CREATE TRIGGER IF NOT EXISTS website_player_activity_insert AFTER INSERT ON players
FOR EACH ROW
BEGIN
  IF NEW.online = 1 THEN
    INSERT INTO website_character_activity(player_id,last_login,session_started)
    VALUES(NEW.id,UTC_TIMESTAMP(),UTC_TIMESTAMP());
  END IF;
END//
DELIMITER ;
