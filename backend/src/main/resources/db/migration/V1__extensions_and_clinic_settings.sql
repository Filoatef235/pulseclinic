CREATE EXTENSION IF NOT EXISTS btree_gist;
CREATE EXTENSION IF NOT EXISTS citext;
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TYPE timerange AS RANGE (subtype = time);

CREATE TABLE clinic_settings (
  id smallint PRIMARY KEY DEFAULT 1 CHECK (id = 1),
  clinic_name text NOT NULL,
  timezone text NOT NULL,
  slot_length_minutes int NOT NULL DEFAULT 30 CHECK (slot_length_minutes BETWEEN 5 AND 120),
  cancel_cutoff_hours int NOT NULL DEFAULT 24 CHECK (cancel_cutoff_hours >= 0),
  booking_horizon_days int NOT NULL DEFAULT 60 CHECK (booking_horizon_days BETWEEN 1 AND 365),
  updated_at timestamptz NOT NULL DEFAULT now()
);

INSERT INTO clinic_settings (id, clinic_name, timezone)
VALUES (1, 'PulseClinic', 'UTC');
