-- Native world-helper quotes. Preview never charges; ACTIVE records a completed one-time payment and fixed term.
CREATE TABLE IF NOT EXISTS playerbot_hire_contracts (
 contract_id VARCHAR(36) NOT NULL PRIMARY KEY, account_id INT NOT NULL, owner_id INT NOT NULL, bot_id INT NOT NULL,
 fee BIGINT NOT NULL, duration_ms BIGINT NOT NULL, quote_until BIGINT NOT NULL, quoted_rank VARCHAR(16) NOT NULL,
 state VARCHAR(12) NOT NULL DEFAULT 'QUOTED', started_at BIGINT NOT NULL DEFAULT 0, ends_at BIGINT NOT NULL DEFAULT 0,
 ended_reason VARCHAR(32) NOT NULL DEFAULT '', created_at BIGINT NOT NULL,
 INDEX bot_terms(bot_id,state), INDEX owner_terms(account_id,owner_id,state)
) ENGINE=InnoDB;
