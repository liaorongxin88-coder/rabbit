-- Keep the original sex counts on new weaning records until the later separation.
-- Existing historical rows and their already-generated rabbits remain unchanged.
ALTER TABLE weaning_records
    ADD COLUMN male_count INT NULL AFTER waiting_count,
    ADD COLUMN female_count INT NULL AFTER male_count;
