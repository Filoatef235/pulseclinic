-- The demo clinic is in Los Angeles. The booking UI therefore shows its
-- 09:00-17:00 weekday working hours as local clinic times.
UPDATE clinic_settings
SET timezone = 'America/Los_Angeles', updated_at = now()
WHERE id = 1;
