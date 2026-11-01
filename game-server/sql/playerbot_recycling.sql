-- Native terminal recycling. Character, item and prior contribution rows remain archived.
CREATE TABLE IF NOT EXISTS playerbot_recycle_quotes (
 quote_key CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
 account_id INT NOT NULL, source_id INT NOT NULL, target_id INT NOT NULL, receiver_id INT NOT NULL,
 source_name VARCHAR(64) NOT NULL, target_name VARCHAR(64) NOT NULL,
 fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 learning_xp INT NOT NULL, kinah BIGINT NOT NULL, item_appraisal BIGINT NOT NULL,
 appraised_items INT NOT NULL, actual_learning BIGINT NOT NULL, recycled_learning BIGINT NOT NULL,
 expires_at BIGINT NOT NULL, KEY account_recycle_quotes(account_id,expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS playerbot_recycles (
 source_id INT NOT NULL PRIMARY KEY, account_id INT NOT NULL, target_id INT NOT NULL, receiver_id INT NOT NULL,
 quote_key CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 learning_xp INT NOT NULL, kinah BIGINT NOT NULL, completed_at BIGINT NOT NULL,
 UNIQUE KEY recycle_quote(quote_key), KEY account_recycles(account_id,completed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
-- Learning from recycling never becomes actual-play learning in a later appraisal.
CREATE TABLE IF NOT EXISTS playerbot_recycle_learning (
 player_id INT NOT NULL PRIMARY KEY, account_id INT NOT NULL, received_xp BIGINT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
