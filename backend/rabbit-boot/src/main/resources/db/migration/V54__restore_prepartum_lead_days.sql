-- prepartum_days was temporarily interpreted as the duration from palpation to
-- prepartum. Restore its original business meaning: days before expected birth.
-- Convert existing values so the calculated reminder date does not move:
-- mating + palpation + old_duration = mating + 30 - new_lead.
UPDATE global_setting
SET prepartum_days = LEAST(
  29,
  GREATEST(
    1,
    30
      - CASE WHEN palpation_days > 0 THEN palpation_days ELSE 12 END
      - prepartum_days
  )
);
