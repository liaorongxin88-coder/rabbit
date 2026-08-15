ALTER TABLE breeding_cycles
  DROP INDEX uk_bc_mother_cycle,
  DROP INDEX idx_bc_batch_mother,
  ADD UNIQUE KEY uk_bc_batch_mother_cycle (
    house_id,
    batch_id,
    mother_rabbit_id,
    cycle_no
  );
