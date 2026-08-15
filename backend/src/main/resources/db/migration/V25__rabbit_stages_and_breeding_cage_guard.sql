-- Stage fields are nullable for backward-compatible clients. New clients submit
-- validated values through the rabbit create/update API.
SELECT COUNT(*) INTO @cnt
FROM information_schema.columns
WHERE table_schema = DATABASE() AND table_name = 'rabbits' AND column_name = 'growth_stage';
SET @sql = IF(@cnt = 0,
    'ALTER TABLE rabbits ADD COLUMN growth_stage VARCHAR(20) NULL AFTER weight',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SELECT COUNT(*) INTO @cnt
FROM information_schema.columns
WHERE table_schema = DATABASE() AND table_name = 'rabbits' AND column_name = 'reproductive_stage';
SET @sql = IF(@cnt = 0,
    'ALTER TABLE rabbits ADD COLUMN reproductive_stage VARCHAR(20) NULL AFTER growth_stage',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- This generated key is null for commodity or departed rabbits, so MySQL permits
-- their normal many-per-cage layout while enforcing one active seed/replacement
-- rabbit per cage as a final guard against all writer paths.
SELECT COUNT(*) INTO @cnt
FROM information_schema.columns
WHERE table_schema = DATABASE() AND table_name = 'rabbits' AND column_name = 'active_breeding_cage_id';
SET @sql = IF(@cnt = 0,
    'ALTER TABLE rabbits ADD COLUMN active_breeding_cage_id BIGINT GENERATED ALWAYS AS (CASE WHEN is_active = TRUE AND type IN (''0'', ''1'') THEN cage_id ELSE NULL END) STORED AFTER is_active',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SELECT COUNT(*) INTO @cnt
FROM information_schema.statistics
WHERE table_schema = DATABASE() AND table_name = 'rabbits'
  AND index_name = 'idx_rabbits_house_cage_active_type_id';
SET @sql = IF(@cnt = 0,
    'ALTER TABLE rabbits ADD KEY idx_rabbits_house_cage_active_type_id (house_id, cage_id, is_active, type, id)',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- If historical data violates this invariant, this statement fails rather than
-- silently choosing a rabbit. Reconcile those cages, then rerun Flyway.
SELECT COUNT(*) INTO @cnt
FROM information_schema.statistics
WHERE table_schema = DATABASE() AND table_name = 'rabbits'
  AND index_name = 'uk_rabbits_house_active_breeding_cage';
SET @sql = IF(@cnt = 0,
    'ALTER TABLE rabbits ADD UNIQUE KEY uk_rabbits_house_active_breeding_cage (house_id, active_breeding_cage_id)',
    'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
