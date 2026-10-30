CREATE TABLE IF NOT EXISTS wardrobe_accounts (
 account_id INT NOT NULL PRIMARY KEY
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS wardrobe_skins (
 account_id INT NOT NULL, item_id INT NOT NULL, unlocked_at BIGINT NOT NULL,
 source_character INT NOT NULL, PRIMARY KEY (account_id,item_id)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS wardrobe_outfits (
 account_id INT NOT NULL, name VARCHAR(32) NOT NULL, skins_json TEXT NOT NULL,
 updated_at BIGINT NOT NULL, PRIMARY KEY (account_id,name)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS wardrobe_requests (
 request_id VARCHAR(64) NOT NULL PRIMARY KEY, account_id INT NOT NULL,
 result VARCHAR(512) NOT NULL, created_at BIGINT NOT NULL,
 INDEX receipt_expiry (created_at)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS wardrobe_dyes (
 account_id INT NOT NULL, color INT NOT NULL, dye_item INT NOT NULL,
 unlocked_at BIGINT NOT NULL, source_character INT NOT NULL, PRIMARY KEY (account_id,color)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS wardrobe_palettes (
 account_id INT NOT NULL PRIMARY KEY, unlocked_at BIGINT NOT NULL, source_character INT NOT NULL
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS wardrobe_outfit_dyes (
 account_id INT NOT NULL, name VARCHAR(32) NOT NULL, dyes_json TEXT NOT NULL,
 PRIMARY KEY (account_id,name)
) ENGINE=InnoDB;
