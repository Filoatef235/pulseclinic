-- Demo-only data. This location is enabled only by the `demo` Spring profile.
INSERT INTO users (id, email, password_hash, role, email_verified) VALUES
  ('00000000-0000-0000-0000-000000000001', 'patient@pulseclinic.demo', crypt('DemoPatient!2026', gen_salt('bf', 10)), 'PATIENT', true),
  ('00000000-0000-0000-0000-000000000002', 'maya.hassan@pulseclinic.demo', crypt('DemoDoctor!2026', gen_salt('bf', 10)), 'DOCTOR', true),
  ('00000000-0000-0000-0000-000000000003', 'omar.karim@pulseclinic.demo', crypt('DemoDoctor!2026', gen_salt('bf', 10)), 'DOCTOR', true),
  ('00000000-0000-0000-0000-000000000004', 'staff@pulseclinic.demo', crypt('DemoStaff!2026', gen_salt('bf', 10)), 'STAFF', true),
  ('00000000-0000-0000-0000-000000000005', 'admin@pulseclinic.demo', crypt('DemoAdmin!2026', gen_salt('bf', 10)), 'ADMIN', true),
  ('00000000-0000-0000-0000-000000000006', 'lina.salem@pulseclinic.demo', crypt('DemoDoctor!2026', gen_salt('bf', 10)), 'DOCTOR', true),
  ('00000000-0000-0000-0000-000000000007', 'nora.ali@pulseclinic.demo', crypt('DemoStaff!2026', gen_salt('bf', 10)), 'STAFF', true),
  ('00000000-0000-0000-0000-000000000008', 'samir.haddad@pulseclinic.demo', crypt('DemoDoctor!2026', gen_salt('bf', 10)), 'DOCTOR', true),
  ('00000000-0000-0000-0000-000000000009', 'sofia.nasser@pulseclinic.demo', crypt('DemoDoctor!2026', gen_salt('bf', 10)), 'DOCTOR', true),
  ('00000000-0000-0000-0000-000000000010', 'ryan.moore@pulseclinic.demo', crypt('DemoDoctor!2026', gen_salt('bf', 10)), 'DOCTOR', true),
  ('00000000-0000-0000-0000-000000000011', 'aya.farid@pulseclinic.demo', crypt('DemoStaff!2026', gen_salt('bf', 10)), 'STAFF', true),
  ('00000000-0000-0000-0000-000000000012', 'leo.grant@pulseclinic.demo', crypt('DemoStaff!2026', gen_salt('bf', 10)), 'STAFF', true);

INSERT INTO patients (id, user_id, full_name, phone) VALUES
  ('10000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', 'Alex Morgan', '+1 555 010 0200');
INSERT INTO staff_profiles (id, user_id, full_name) VALUES
  ('20000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000004', 'Jordan Lee'),
  ('20000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000007', 'Nora Ali'),
  ('20000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000011', 'Aya Farid'),
  ('20000000-0000-0000-0000-000000000004', '00000000-0000-0000-0000-000000000012', 'Leo Grant');
INSERT INTO specialties (id, name) VALUES
  ('30000000-0000-0000-0000-000000000001', 'Family Medicine'),
  ('30000000-0000-0000-0000-000000000002', 'Physiotherapy');
INSERT INTO doctors (id, user_id, specialty_id, full_name, bio) VALUES
  ('40000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002', '30000000-0000-0000-0000-000000000001', 'Dr. Maya Hassan', 'Thoughtful, whole-person primary care.'),
  ('40000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000003', '30000000-0000-0000-0000-000000000002', 'Dr. Omar Karim', 'Movement-focused care for a more comfortable life.'),
  ('40000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000006', '30000000-0000-0000-0000-000000000001', 'Dr. Lina Salem', 'Preventive care with a calm, practical approach.'),
  ('40000000-0000-0000-0000-000000000004', '00000000-0000-0000-0000-000000000008', '30000000-0000-0000-0000-000000000002', 'Dr. Samir Haddad', 'Practical physiotherapy focused on steady progress.'),
  ('40000000-0000-0000-0000-000000000005', '00000000-0000-0000-0000-000000000009', '30000000-0000-0000-0000-000000000001', 'Dr. Sofia Nasser', 'Preventive, whole-person family medicine.'),
  ('40000000-0000-0000-0000-000000000006', '00000000-0000-0000-0000-000000000010', '30000000-0000-0000-0000-000000000002', 'Dr. Ryan Moore', 'A considered approach to mobility and recovery.');
INSERT INTO services (id, name, duration_minutes, price_cents) VALUES
  ('50000000-0000-0000-0000-000000000001', 'General consultation', 30, 8500),
  ('50000000-0000-0000-0000-000000000002', 'Wellness review', 45, 12000),
  ('50000000-0000-0000-0000-000000000003', 'Physiotherapy session', 45, 11000);
INSERT INTO doctor_services (doctor_id, service_id) VALUES
  ('40000000-0000-0000-0000-000000000001', '50000000-0000-0000-0000-000000000001'),
  ('40000000-0000-0000-0000-000000000001', '50000000-0000-0000-0000-000000000002'),
  ('40000000-0000-0000-0000-000000000002', '50000000-0000-0000-0000-000000000003'),
  ('40000000-0000-0000-0000-000000000003', '50000000-0000-0000-0000-000000000001'),
  ('40000000-0000-0000-0000-000000000003', '50000000-0000-0000-0000-000000000002'),
  ('40000000-0000-0000-0000-000000000004', '50000000-0000-0000-0000-000000000003'),
  ('40000000-0000-0000-0000-000000000005', '50000000-0000-0000-0000-000000000001'),
  ('40000000-0000-0000-0000-000000000005', '50000000-0000-0000-0000-000000000002'),
  ('40000000-0000-0000-0000-000000000006', '50000000-0000-0000-0000-000000000003');
INSERT INTO working_hours (doctor_id, day_of_week, start_time, end_time)
SELECT doctor_id, day_of_week, '09:00'::time, '17:00'::time
FROM (VALUES ('40000000-0000-0000-0000-000000000001'::uuid), ('40000000-0000-0000-0000-000000000002'::uuid), ('40000000-0000-0000-0000-000000000003'::uuid), ('40000000-0000-0000-0000-000000000004'::uuid), ('40000000-0000-0000-0000-000000000005'::uuid), ('40000000-0000-0000-0000-000000000006'::uuid)) doctors(doctor_id)
CROSS JOIN generate_series(1, 5) day_of_week;
