ALTER TABLE waitlist_entries
    ADD COLUMN offered_starts_at timestamptz,
    ADD COLUMN offered_at timestamptz,
    ADD COLUMN offered_doctor_id uuid REFERENCES doctors(id);
