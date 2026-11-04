-- Apply offline before enabling earned recruitment. Tickets belong to the account, not a character.
-- recycle_credit is stored in hundredths of a ticket and carries into whole tickets at 100.
CREATE TABLE IF NOT EXISTS playerbot_ticket_wallet (
 account_id INT NOT NULL PRIMARY KEY,
 tickets BIGINT NOT NULL DEFAULT 0,
 recycle_credit INT NOT NULL DEFAULT 0,
 revision BIGINT NOT NULL DEFAULT 0,
 updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
-- One row per balance change. The key makes a repeated completion or recycle return the recorded outcome.
CREATE TABLE IF NOT EXISTS playerbot_ticket_receipts (
 receipt_key VARCHAR(96) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
 account_id INT NOT NULL,
 kind VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 ticket_delta BIGINT NOT NULL,
 credit_delta INT NOT NULL,
 tickets_after BIGINT NOT NULL,
 credit_after INT NOT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 KEY account_ticket_receipts (account_id,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
-- The roll is stored before any reveal. A ROLLED row is resumed, never redrawn. character_id survives bot removal as history.
-- Native delivery outbox: committed completions survive restarts and wallet outages.
CREATE TABLE IF NOT EXISTS playerbot_ticket_entitlements (
 receipt_key VARCHAR(96) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
 account_id INT NOT NULL,
 tickets BIGINT NOT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 KEY pending_account (account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS playerbot_recruitment_operations (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 account_id INT NOT NULL,
 request_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 state VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 advanced_class VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 auto_equip_rarity VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 combat_rank VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 character_id INT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 completed_at TIMESTAMP NULL,
 UNIQUE KEY account_recruitment_request (account_id,request_key),
 UNIQUE KEY recruited_character (character_id),
 KEY account_recruitment_state (account_id,state)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- One row per distinct finished help request; every third pays a ticket by default.
CREATE TABLE IF NOT EXISTS playerbot_ticket_help (
 request_key VARCHAR(96) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
 account_id INT NOT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 KEY account_help (account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
