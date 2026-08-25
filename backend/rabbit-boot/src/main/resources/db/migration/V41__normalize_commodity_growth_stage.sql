-- JUVENILE is the legacy name for the commodity-rabbit adaptation stage.
-- Normalize active and departed rows without changing audit metadata or other fields.
UPDATE rabbits
SET growth_stage = 'ADAPTATION'
WHERE growth_stage = 'JUVENILE';
