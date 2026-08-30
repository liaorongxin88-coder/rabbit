-- growth_stage describes the commercial growth pipeline only.
-- Replacement and breeding rabbits use replacement_records or reproduction cycles.
UPDATE rabbits
SET growth_stage = NULL,
    growth_stage_entered_at = NULL,
    update_by = 'v53',
    update_time = NOW()
WHERE type <> '2'
  AND (growth_stage IS NOT NULL OR growth_stage_entered_at IS NOT NULL);

ALTER TABLE rabbits
  ADD CONSTRAINT ck_rabbits_commodity_growth_stage
  CHECK (
    type = '2'
    OR (growth_stage IS NULL AND growth_stage_entered_at IS NULL)
  );
