CREATE TABLE appointments (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  patient_id uuid NOT NULL REFERENCES patients(id),
  doctor_id uuid NOT NULL REFERENCES doctors(id),
  service_id uuid NOT NULL REFERENCES services(id),
  starts_at timestamptz NOT NULL,
  ends_at timestamptz NOT NULL,
  status text NOT NULL DEFAULT 'PENDING'
    CHECK (status IN ('PENDING','CONFIRMED','CANCELLED','COMPLETED','NO_SHOW')),
  notes text,
  created_by uuid REFERENCES users(id),
  version bigint NOT NULL DEFAULT 0,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CHECK (ends_at > starts_at),
  CONSTRAINT no_doctor_overlap EXCLUDE USING gist (
    doctor_id WITH =,
    tstzrange(starts_at, ends_at) WITH &&
  ) WHERE (status IN ('PENDING','CONFIRMED')),
  CONSTRAINT no_patient_overlap EXCLUDE USING gist (
    patient_id WITH =,
    tstzrange(starts_at, ends_at) WITH &&
  ) WHERE (status IN ('PENDING','CONFIRMED'))
);

CREATE INDEX idx_appt_doctor_start ON appointments (doctor_id, starts_at);
CREATE INDEX idx_appt_patient_start ON appointments (patient_id, starts_at DESC);
CREATE INDEX idx_appt_status_start ON appointments (status, starts_at);

CREATE TABLE appointment_status_history (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  appointment_id uuid NOT NULL REFERENCES appointments(id),
  from_status text,
  to_status text NOT NULL,
  changed_by uuid REFERENCES users(id),
  changed_at timestamptz NOT NULL DEFAULT now(),
  reason text
);
