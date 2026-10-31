ALTER TABLE clinic_settings
  DROP CONSTRAINT IF EXISTS clinic_settings_booking_horizon_days_check;

ALTER TABLE clinic_settings
  ADD CONSTRAINT clinic_settings_booking_horizon_days_check
  CHECK (booking_horizon_days BETWEEN 1 AND 1826);

UPDATE clinic_settings
SET booking_horizon_days = 1826,
    updated_at = now()
WHERE id = 1;
