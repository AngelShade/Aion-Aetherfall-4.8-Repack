-- Companion-only historical Fame. These rows survive retirement/recycling; native identities are never reused.
CREATE TABLE IF NOT EXISTS playerbot_collection_renown (
 account_id INT NOT NULL PRIMARY KEY,
 combat BIGINT NOT NULL DEFAULT 0,
 life BIGINT NOT NULL DEFAULT 0,
 legend BIGINT NOT NULL DEFAULT 0,
 credited_bots INT NOT NULL DEFAULT 0,
 revision BIGINT NOT NULL DEFAULT 0,
 stipend_day DATE NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS playerbot_collection_renown_credits (
 player_id INT NOT NULL PRIMARY KEY,
 account_id INT NOT NULL,
 rarity_base INT NOT NULL,
 combat BIGINT NOT NULL DEFAULT 0,
 life BIGINT NOT NULL DEFAULT 0,
 legend BIGINT NOT NULL DEFAULT 0,
 KEY account_credits(account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS playerbot_collection_stipends (
 account_id INT NOT NULL,
 claim_day DATE NOT NULL,
 player_id INT NOT NULL,
 kinah BIGINT NOT NULL,
 PRIMARY KEY(account_id,claim_day)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
