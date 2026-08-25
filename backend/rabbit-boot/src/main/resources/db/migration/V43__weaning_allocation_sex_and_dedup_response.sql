-- Allocation sex remains nullable: historical rows and count-only requests are not reliable.
ALTER TABLE weaning_record_allocations
    ADD COLUMN male_count INT NULL AFTER alloc_count,
    ADD COLUMN female_count INT NULL AFTER male_count;

-- Deferred separation replays must return the first response, not reconstructed current state.
ALTER TABLE request_dedup
    ADD COLUMN response_payload JSON NULL AFTER error_message;
