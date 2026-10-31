ALTER TABLE users
    ADD COLUMN last_signed_in_at timestamptz;

ALTER TABLE doctors
    ADD COLUMN professional_title text,
    ADD COLUMN photo_storage_key text,
    ADD COLUMN phone text;

ALTER TABLE staff_profiles
    ADD COLUMN professional_title text,
    ADD COLUMN bio text,
    ADD COLUMN phone text,
    ADD COLUMN photo_storage_key text,
    ADD COLUMN active boolean NOT NULL DEFAULT true;

CREATE TABLE team_invitations (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash text NOT NULL UNIQUE,
    expires_at timestamptz NOT NULL,
    used_at timestamptz,
    revoked_at timestamptz,
    created_by uuid REFERENCES users(id),
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_team_invitations_user ON team_invitations (user_id, created_at DESC);

CREATE TABLE waitlist_entries (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id uuid NOT NULL REFERENCES patients(id),
    service_id uuid NOT NULL REFERENCES services(id),
    doctor_id uuid REFERENCES doctors(id),
    requested_date date,
    status text NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'OFFERED', 'FULFILLED', 'CANCELLED', 'EXPIRED')),
    contact_preference text NOT NULL DEFAULT 'EMAIL' CHECK (contact_preference IN ('EMAIL', 'PHONE')),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_waitlist_active_lookup ON waitlist_entries (status, service_id, doctor_id, requested_date, created_at);
CREATE UNIQUE INDEX uq_waitlist_active_request
    ON waitlist_entries (patient_id, service_id, COALESCE(doctor_id, '00000000-0000-0000-0000-000000000000'::uuid), COALESCE(requested_date, DATE '0001-01-01'))
    WHERE status IN ('ACTIVE', 'OFFERED');
