CREATE TABLE specialties (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  name text NOT NULL UNIQUE
);

CREATE TABLE doctors (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL UNIQUE REFERENCES users(id),
  specialty_id uuid NOT NULL REFERENCES specialties(id),
  full_name text NOT NULL,
  bio text,
  active boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE services (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  name text NOT NULL UNIQUE,
  duration_minutes int NOT NULL CHECK (duration_minutes BETWEEN 5 AND 480),
  price_cents int NOT NULL CHECK (price_cents >= 0),
  active boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE doctor_services (
  doctor_id uuid NOT NULL REFERENCES doctors(id),
  service_id uuid NOT NULL REFERENCES services(id),
  PRIMARY KEY (doctor_id, service_id)
);
