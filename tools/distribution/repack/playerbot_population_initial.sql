-- Initial native state for a new repack with no legacy population to import.
-- Digest matches PlayerBotPopulationMigration for its four absent legacy files.
-- Never overwrite an imported state, partial pool, roster or native bot identity.
INSERT INTO playerbot_population_state
 (id,version,target,target_until,revision,import_digest)
SELECT 1,1,0,0,0,'ce010d7da5cf425bb206e2912423e8d0d688911b422e138a07b408d5925c8bc1'
WHERE NOT EXISTS (SELECT 1 FROM playerbot_population_state)
 AND NOT EXISTS (SELECT 1 FROM playerbot_population_accounts)
 AND NOT EXISTS (SELECT 1 FROM playerbot_population_members)
 AND NOT EXISTS (SELECT 1 FROM playerbot_world_residents)
 AND NOT EXISTS (SELECT 1 FROM playerbot_population_events)
 AND NOT EXISTS (SELECT 1 FROM playerbot_roster)
 AND NOT EXISTS (SELECT 1 FROM players WHERE account_id BETWEEN -1023999 AND -1000000);
