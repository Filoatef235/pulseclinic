CREATE TABLE working_hours (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  doctor_id uuid NOT NULL REFERENCES doctors(id),
  day_of_week smallint NOT NULL CHECK (day_of_week BETWEEN 1 AND 7),
  start_time time NOT NULL,
  end_time time NOT NULL,
  CHECK (end_time > start_time),
  CONSTRAINT no_working_hours_overlap EXCLUDE USING gist (
    doctor_id WITH =,
    day_of_week WITH =,
    timerange(start_time, end_time) WITH &&
  )
);

CREATE TABLE time_off (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  doctor_id uuid REFERENCES doctors(id),
  starts_at timestamptz NOT NULL,
  ends_at timestamptz NOT NULL,
  reason text,
  CHECK (ends_at > starts_at)
);

CREATE INDEX idx_working_hours_doctor ON working_hours (doctor_id, day_of_week);
CREATE INDEX idx_time_off_doctor_start ON time_off (doctor_id, starts_at);
