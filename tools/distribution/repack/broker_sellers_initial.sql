-- Reserved offline broker sellers for the standalone Aetherfall repack.
-- These are system identities only: no login account, personal character or inventory is imported.
-- Native initial locations are from game-server/data/static_data/player_initial_data.xml.
START TRANSACTION;
-- Inherit native string collations, including databases upgraded from older repacks.
CREATE TEMPORARY TABLE aetherfall_expected_market_sellers (PRIMARY KEY (id)) AS
 SELECT id,name,account_id,account_name,race,world_id,x,y,z FROM players WHERE 1=0;
INSERT INTO aetherfall_expected_market_sellers VALUES
 (999901,'AetherfallElya',-900001,'aetherfall_market_elyos','ELYOS',210010000,1212.9423,1044.8516,140.75568),
 (999902,'AetherfallElyb',-900001,'aetherfall_market_elyos','ELYOS',210010000,1212.9423,1044.8516,140.75568),
 (999903,'AetherfallElyc',-900001,'aetherfall_market_elyos','ELYOS',210010000,1212.9423,1044.8516,140.75568),
 (999904,'AetherfallAsma',-900002,'aetherfall_market_asmodians','ASMODIANS',220010000,571.0388,2787.3420,299.8750),
 (999905,'AetherfallAsmb',-900002,'aetherfall_market_asmodians','ASMODIANS',220010000,571.0388,2787.3420,299.8750),
 (999906,'AetherfallAsmc',-900002,'aetherfall_market_asmodians','ASMODIANS',220010000,571.0388,2787.3420,299.8750);

-- A conflicting identity aborts this connection before any permanent row is written.
-- The client runs without --force; disconnect on error rolls back this transaction.
CREATE TEMPORARY TABLE aetherfall_market_identity_conflict (guard_key INT PRIMARY KEY);
INSERT INTO aetherfall_market_identity_conflict VALUES (1);
INSERT INTO aetherfall_market_identity_conflict
 SELECT 1 FROM players p JOIN aetherfall_expected_market_sellers e ON p.id=e.id
 WHERE p.name<>e.name OR p.account_id<>e.account_id OR p.account_name<>e.account_name
    OR p.race<>e.race OR p.deletion_date IS NOT NULL LIMIT 1;
INSERT INTO aetherfall_market_identity_conflict
 SELECT 1 FROM players p JOIN aetherfall_expected_market_sellers e ON p.name=e.name
 WHERE p.id<>e.id LIMIT 1;
INSERT INTO aetherfall_market_identity_conflict
 SELECT 1 FROM players p WHERE p.account_id IN (-900001,-900002)
 AND p.id NOT IN (999901,999902,999903,999904,999905,999906) LIMIT 1;

INSERT INTO players
 (id,name,account_id,account_name,x,y,z,heading,world_id,gender,race,player_class,creation_date,note)
 SELECT e.id,e.name,e.account_id,e.account_name,e.x,e.y,e.z,32,e.world_id,'MALE',e.race,'WARRIOR',
        UTC_TIMESTAMP(),'Aetherfall reserved offline broker seller'
 FROM aetherfall_expected_market_sellers e LEFT JOIN players p ON p.id=e.id WHERE p.id IS NULL;
COMMIT;
DROP TEMPORARY TABLE aetherfall_market_identity_conflict;
DROP TEMPORARY TABLE aetherfall_expected_market_sellers;
