# AGENTS.md — PulseClinic

Instructions for Cursor (the coding agent). Read fully before acting. When this file conflicts with a chat message from the owner, the owner wins; ask if unclear.

---

## 0. Agent status and hard rules

- **Spend tokens on work, not chat.** Replies stay short: result, what to approve next, blockers. No recaps of this file, no architecture essays, no dumping SQL/API lists, no restating the stack or build order. Do not narrate plans the owner already wrote here. Inspect, code, test, run commands; explain only when asked or when a decision is required.
- **Planning mode is the default.** Do NOT create files, folders, git repos, or scaffolding until the owner says "start building".
- After "start building", complete **one build-order step at a time** (section 15), then stop and report for approval (2–5 sentences).
- Never commit secrets. Never disable tests to pass a build. Never claim something works without running it.
- Ask before: adding a dependency, changing the schema outside a planned migration, touching auth/security config, or deviating from the locked stack. One question at a time.
- Never invent endpoints, tables, or business rules. Inspect first; if ambiguous, choose the safest interpretation and document the assumption in one line.
- Owner's machine: Windows, Java 17, Maven 3.9, Node 22, Git, Cursor. No Gradle. Use Windows-friendly commands (`mvnw.cmd`, PowerShell). Do not rely on `/bin/sh`.

## 1. Mission

PulseClinic is a **production-oriented clinic appointment and management platform**: a live, public, HTTPS SaaS that doubles as a strong CV project. It must demonstrate professional engineering: correctness, security, data integrity, tests, CI/CD, observability, documentation. Not a tutorial, not simplistic CRUD.

**Roles:** `PATIENT`, `DOCTOR`, `STAFF`, `ADMIN`.
**Core flow:** pick service → pick doctor → pick slot → confirm → email.
**Non-negotiable invariant:** two appointments for the same doctor can never overlap in time. Enforced **in PostgreSQL** (exclusion constraint), not only in application code.

**Definition of done for v1:** a stranger opens the live URL, books in ~2 minutes, sees it under My Appointments, can cancel it; staff see it on the day calendar and update its status; a conflicting booking is rejected and tests prove it.

## 2. Locked stack

| Layer | Choice |
|---|---|
| API | Spring Boot 3, Java 17, Maven, REST/JSON, `/api/v1/...` |
| DB | PostgreSQL (system of record), Flyway migrations |
| Frontend | React + TypeScript (strict) + Tailwind |
| Auth | Spring Security, httpOnly cookie (session or JWT in httpOnly cookie), CSRF handled, RBAC |
| Tests | JUnit 5, Spring Boot Test, **Testcontainers PostgreSQL** (H2 is NOT used), React Testing Library + Vitest, Playwright for 1-2 e2e flows |
| Email | Resend or SES behind an `EmailService` adapter |
| CI | GitHub Actions on every push/PR |
| Deploy | Docker; Render or Railway (paid tier or keep-alive to avoid cold starts) |
| Repo | Single repo: `backend/`, `frontend/`, `docs/`, `ml/` (later) |
| API docs | OpenAPI/Swagger (springdoc) |

Avoid unless a demonstrated need exists: microservices, Kubernetes, Kafka, Redis, GraphQL, CQRS, event sourcing, multiple databases. Prefer a **well-designed modular monolith**.

## 3. Engineering principles

Priority order: **Correctness → Security → Data integrity → Maintainability → Testability → Performance → Simplicity.**

Prefer simple over clever, explicit over magic, composition over inheritance, strong typing over `any`, domain naming over generic naming, measured optimization over premature optimization. Never add a pattern just to show it.

Before modifying code: inspect structure and conventions, find reusable code, identify domain/security/DB/API implications, implement the smallest clean solution, run relevant tests/builds, then review.

## 4. Architecture

Backend flow: `Controller → Request DTO + validation → Application Service → Domain logic → Repository / Integration port → PostgreSQL / external service`.
Frontend flow: `Page → Feature components → Hooks/state → API service → REST API`.

Never: business logic in controllers or React; workflows in repositories; JPA entities as API contracts; frontend validation replacing backend validation; vendor SDK calls scattered through business logic.

Backend packages (feature-oriented, `com.pulseclinic`):

```
common/ (exception, security, validation, audit, config)
user/ auth/ patient/ doctor/ specialty/ clinicservice/
scheduling/ (working hours, time off, slot generation)
appointment/ (controller, dto, entity, repository, service, domain)
waitlist/ notification/ (service, integration) billing/ review/ reporting/ admin/
```

Frontend (`src/`): `app/ components/ layouts/ features/{auth,booking,appointments,doctors,patients,staff,admin,dashboard}/ hooks/ api/ types/ utils/ styles/`.

SOLID with Spring **constructor injection** (no field injection). Use Strategy for notification channels/pricing/scheduling policies, Adapter for email/SMS/payment/storage, Specification for dynamic search, a small State model for appointment lifecycle, application events for decoupled side effects (email, audit). Only where they solve a real problem.

## 5. Domain rules

**Appointment statuses:** `PENDING`, `CONFIRMED`, `CANCELLED`, `COMPLETED`, `NO_SHOW`.

Allowed transitions (backend rejects all others with 409/422):
- `PENDING → CONFIRMED | CANCELLED`
- `CONFIRMED → COMPLETED | NO_SHOW | CANCELLED`
- `CANCELLED`, `COMPLETED`, `NO_SHOW` are terminal.

Who may transition: patient may cancel own appointment before the cutoff (default **24h**, configurable in `clinic_settings`); staff/admin may apply any valid transition; doctor may mark own appointments `COMPLETED`/`NO_SHOW`.

**Slots:** generated from doctor weekly working hours minus time off minus existing active appointments, in fixed slot length (default 30 min); a service occupies `ceil(duration / slot_length)` slots. Booking computes `ends_at = starts_at + service.duration`.

**Time:** store all timestamps as UTC (`timestamptz`); clinic time zone lives in `clinic_settings`; convert only at the edge (API returns ISO-8601 UTC, UI formats in clinic zone).

**Reschedule** = atomic cancel + rebook in one transaction with the conflict check.

**Privacy:** demo/seed data is fake. Do not store real medical details in v1. Never claim HIPAA/GDPR compliance.

## 6. Database plan (PostgreSQL + Flyway)

Rules: normalized model; FKs, unique and check constraints; indexes from access patterns; all changes via versioned Flyway migrations (`V1__...sql`); never edit applied migrations; `timestamptz` everywhere timestamps are instants; UUID primary keys (`gen_random_uuid()`); `created_at/updated_at` on mutable tables; `version` on appointments for optimistic locking. Roles stored as check-constrained text (not PG enums) so Flyway stays simple.

**Step 2 — v1 schema lock (awaiting owner approval; no migrations written until approved):**

- **One role per user** on `users.role`. No `user_roles` table in v1.
- **Slots are computed, not stored.** No `slots` table. Generate from weekly `working_hours` (clinic-local `time`) minus `time_off` minus active appointments (`PENDING`/`CONFIRMED`). Slot length and cancel cutoff live in `clinic_settings`. Service occupies `ceil(duration_minutes / slot_length_minutes)` consecutive slots. `appointments.ends_at = starts_at + service.duration`.
- **`tstzrange(starts_at, ends_at)` is `[)`** (end exclusive) so back-to-back bookings are allowed.
- **Doctor overlap** exclusion on active rows is mandatory. **Patient overlap** exclusion on active rows is also mandatory (no double-booking a patient).
- **ISO day_of_week:** 1 = Monday … 7 = Sunday. Multiple `working_hours` rows per doctor per day allowed (split shifts); overlapping ranges for the same doctor+day are rejected.
- **Walk-in patients:** `patients.user_id` nullable. Doctors and staff must have a user (`doctors.user_id` and `staff_profiles.user_id` NOT NULL UNIQUE).
- **v1 tables only** through V6 + demo seed. `waitlist_entries`, `reviews`, `doctor_notes`, payments, ML — later migrations, not v1.

Migration order (planned, not yet created):

| Migration | Contents |
|---|---|
| V1 | extensions (`btree_gist`, `citext`, `pgcrypto`), `clinic_settings` |
| V2 | `users`, `patients`, `staff_profiles`, `email_verification_tokens`, `password_reset_tokens`, `refresh_tokens` |
| V3 | `specialties`, `doctors`, `services`, `doctor_services` |
| V4 | `working_hours`, `time_off` |
| V5 | `appointments` + both exclusion constraints + indexes, `appointment_status_history` |
| V6 | `audit_log`, `email_outbox`, `idempotency_keys` |
| V7 | seed data (demo accounts, doctors, services, hours) — dev/demo profile only |
| later | waitlist, reviews, doctor_notes, payments, invoices, prescriptions, ml_predictions, documents |

Approved v1 DDL (create only after owner says approved + start building):

```sql
CREATE EXTENSION IF NOT EXISTS btree_gist;
CREATE EXTENSION IF NOT EXISTS citext;
CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE TYPE timerange AS RANGE (subtype = time);

CREATE TABLE clinic_settings (
  id smallint PRIMARY KEY DEFAULT 1 CHECK (id = 1),
  clinic_name text NOT NULL,
  timezone text NOT NULL,                 -- IANA, e.g. Africa/Cairo
  slot_length_minutes int NOT NULL DEFAULT 30 CHECK (slot_length_minutes BETWEEN 5 AND 120),
  cancel_cutoff_hours int NOT NULL DEFAULT 24 CHECK (cancel_cutoff_hours >= 0),
  booking_horizon_days int NOT NULL DEFAULT 60 CHECK (booking_horizon_days BETWEEN 1 AND 365),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE users (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  email citext NOT NULL UNIQUE,
  password_hash text NOT NULL,
  role text NOT NULL CHECK (role IN ('PATIENT','DOCTOR','STAFF','ADMIN')),
  email_verified boolean NOT NULL DEFAULT false,
  enabled boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE patients (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid UNIQUE REFERENCES users(id),   -- null = walk-in / phone booking
  full_name text NOT NULL,
  phone text,
  date_of_birth date,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE staff_profiles (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL UNIQUE REFERENCES users(id),
  full_name text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE email_verification_tokens (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token_hash text NOT NULL UNIQUE,
  expires_at timestamptz NOT NULL,
  used_at timestamptz
);

CREATE TABLE password_reset_tokens (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token_hash text NOT NULL UNIQUE,
  expires_at timestamptz NOT NULL,
  used_at timestamptz
);

CREATE TABLE refresh_tokens (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token_hash text NOT NULL UNIQUE,
  expires_at timestamptz NOT NULL,
  revoked_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now()
);

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

CREATE TABLE working_hours (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  doctor_id uuid NOT NULL REFERENCES doctors(id),
  day_of_week smallint NOT NULL CHECK (day_of_week BETWEEN 1 AND 7), -- 1=Mon
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
  doctor_id uuid REFERENCES doctors(id),      -- null = clinic-wide closure
  starts_at timestamptz NOT NULL,
  ends_at timestamptz NOT NULL,
  reason text,
  CHECK (ends_at > starts_at)
);

CREATE TABLE appointments (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  patient_id uuid NOT NULL REFERENCES patients(id),
  doctor_id uuid NOT NULL REFERENCES doctors(id),
  service_id uuid NOT NULL REFERENCES services(id),
  starts_at timestamptz NOT NULL,
  ends_at timestamptz NOT NULL,
  status text NOT NULL DEFAULT 'PENDING'
    CHECK (status IN ('PENDING','CONFIRMED','CANCELLED','COMPLETED','NO_SHOW')),
  notes text,                                 -- non-medical in v1
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

CREATE INDEX idx_appt_doctor_start  ON appointments (doctor_id, starts_at);
CREATE INDEX idx_appt_patient_start ON appointments (patient_id, starts_at DESC);
CREATE INDEX idx_appt_status_start  ON appointments (status, starts_at);
CREATE INDEX idx_working_hours_doctor ON working_hours (doctor_id, day_of_week);
CREATE INDEX idx_time_off_doctor_start ON time_off (doctor_id, starts_at);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);

CREATE TABLE appointment_status_history (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  appointment_id uuid NOT NULL REFERENCES appointments(id),
  from_status text, to_status text NOT NULL,
  changed_by uuid REFERENCES users(id),
  changed_at timestamptz NOT NULL DEFAULT now(),
  reason text
);

CREATE TABLE audit_log (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  actor_user_id uuid, action text NOT NULL,
  entity_type text NOT NULL, entity_id uuid,
  metadata jsonb, ip inet, created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE email_outbox (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  to_email citext NOT NULL, template text NOT NULL, payload jsonb NOT NULL,
  status text NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','SENT','FAILED')),
  attempts int NOT NULL DEFAULT 0, next_attempt_at timestamptz,
  last_error text, sent_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE idempotency_keys (
  key text PRIMARY KEY, user_id uuid NOT NULL REFERENCES users(id),
  request_hash text NOT NULL,
  response_status int, response_body jsonb,
  created_at timestamptz NOT NULL DEFAULT now()
);
```

**Concurrency:** the exclusion constraint is the authority. The service may pre-check availability for friendly UX, but must catch the constraint violation and map it to `409 APPOINTMENT_SLOT_UNAVAILABLE`. Use optimistic locking (`version`) for status changes. Emails go through `email_outbox`, written in the same transaction as the booking; a worker sends after commit; email failure never fails the booking.

Avoid N+1: use projections/fetch joins/entity graphs; no blanket `EAGER`; no blanket `CascadeType.ALL`. Verify important query plans with `EXPLAIN`.

## 7. API conventions

- REST, `/api/v1`, DTOs only (`CreateAppointmentRequest`, `AppointmentResponse`), explicit mapping, never expose entities.
- One coherent business operation = one request (no per-field calls).
- Pagination everywhere: `?page=0&size=20`, enforced max size (e.g. 100).
- Status codes: 200, 201, 204, 400, 401, 403, 404, **409** (slot conflict, invalid transition), 422, 429, 500.
- Central `@RestControllerAdvice`; consistent error body:

```json
{ "code": "APPOINTMENT_SLOT_UNAVAILABLE", "message": "The selected appointment slot is no longer available.",
  "timestamp": "2026-10-05T17:30:00Z", "path": "/api/v1/appointments" }
```

Never leak stack traces, SQL, class names, tokens, or patient data.

- Domain exceptions: `AppointmentSlotUnavailableException`, `ResourceNotFoundException`, `UnauthorizedResourceAccessException`, `InvalidAppointmentStateException`. No exceptions for normal control flow.
- Idempotency: `POST /appointments` accepts an `Idempotency-Key` header; replays return the original result.
- Key endpoints (sketch): `GET /doctors`, `GET /doctors/{id}/slots?serviceId&date`, `POST /appointments`, `GET /appointments` (scoped by role), `POST /appointments/{id}/cancel`, `POST /appointments/{id}/reschedule`, `PATCH /appointments/{id}/status`, `/auth/*`, `/admin/*`.

## 8. Security

- Server-side authN/authZ always; RBAC plus **resource-level checks** (a patient can only read their own appointments; no IDOR).
- BCrypt/Argon2 password hashing, httpOnly + Secure + SameSite cookies, CSRF, strict CORS, secure headers, rate limiting on login/register/booking/password reset, input validation at every layer (React → Bean Validation → business → DB constraints).
- Secrets only via environment variables; ship `.env.example`; never commit real values.
- Do not log passwords, tokens, secrets, or sensitive personal data. Audit sensitive actions (role changes, status changes, record access).

## 9. Frontend rules

- TypeScript strict; avoid `any`.
- Separate UI state, server state (TanStack Query), form state (React Hook Form + Zod), auth state. No giant global store.
- All HTTP through `api/` services + hooks; central base URL, auth and error handling.
- Every data view handles: loading, success, empty, error, unauthorized, forbidden, not found, retry.
- Forms: disable submit while pending, show validation and server errors (e.g. "That slot was just taken — pick another"), accessible labels and errors.
- Accessibility (semantic HTML, keyboard, focus, contrast) and mobile-first responsive design. Calm, trustworthy medical look; minimal decoration.

## 10. Testing

Tests are part of the feature. Meaningful coverage over test count.

Must-have scenarios: successful booking; slot already taken; **two concurrent booking attempts (only one succeeds)**; patient accessing another patient's data (403/404); invalid status transition; invalid input; missing doctor/patient; constraint violation mapping; auth failure and expired auth; cancellation cutoff; time zone/DST edge cases; slot generation around time off.

Levels: unit (domain), controller/API (status, validation, authz), integration (Spring + Postgres Testcontainers + security), repository (constraints and key queries). Frontend: user behavior (login, booking, validation, API errors, protected routes). 1-2 Playwright e2e flows (booking happy path, staff calendar).

## 11. Observability, config, Docker, CI/CD

- Structured logs with `requestId`/correlation ID; health check (`/actuator/health`); metrics; error monitoring (Sentry or similar).
- Config via env vars (`DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, `JWT_SECRET`, `EMAIL_API_KEY`, ...); profiles `dev`, `test`, `prod`.
- Docker: `docker-compose` for local Postgres + backend + frontend; small images, non-root user.
- CI: install → lint → compile → unit tests → integration tests → dependency/security scan → build. A PR is not healthy just because it compiles.
- Git: focused commits; no mixing feature + refactor + formatting; no secrets, build artifacts, or debug code.

## 12. Maximum feature set

**v1 — must ship**
- Patient: register/login/logout, email verification, password reset, browse services and doctors, book (service → doctor → slot → confirm), My Appointments (upcoming/past), cancel before cutoff, reschedule, confirmation/cancellation emails, clear conflict errors.
- Staff: day calendar (filter by doctor), update statuses, book on behalf of walk-in/phone patients.
- Admin: manage services, doctors, weekly hours, time off, staff accounts.
- Platform: RBAC, slot generation, UTC + clinic time zone, seeded demo data with one demo account per role, rate limiting, validation, Swagger docs, health check, CI, HTTPS deploy.

**v1.1 — right after launch**
- 24h reminder emails; waitlist for full days; patient and doctor profile pages (photo, specialty, bio); search/filter and history; audit log viewer; admin dashboard (bookings/day, no-show rate, utilization); .ics calendar download; multi-language (EN/AR) and RTL support; dark mode; PWA install.

**v2 — expanded product**
- Online payments/deposits (Stripe/Paymob via `PaymentGateway` adapter, idempotent); invoices and PDF receipts; SMS/WhatsApp reminders; Google Calendar sync; recurring appointments; patient medical profile (allergies, history) with strict access control and audit; prescriptions; document upload (lab results) with validated, access-controlled storage; doctor notes; ratings and reviews; family/dependent accounts; telehealth link (external provider, not built in-house); insurance fields; multi-branch/multi-clinic; in-app notifications; two-factor authentication; GDPR-style data export/delete; reporting exports (CSV/PDF); queue/check-in at the clinic (QR check-in).

**Out of scope until a real requirement exists:** microservices, Kubernetes, Kafka, native mobile apps, custom video infrastructure, EHR/insurance integrations.

## 13. ML / Deep Learning roadmap

**Rules:** PostgreSQL stays the system of record. ML runs as a **separate Python service** (`ml/`, FastAPI) that reads from Postgres (read replica or export) and exposes predictions over HTTP. Never train inside Spring Boot. Do not start ML until real booking/cancel/no-show data exists (seed data is not enough for honest metrics). Every model needs an evaluation report, versioning, monitoring, and a human-readable explanation of limitations. Predictions are advisory and never block a booking automatically without an owner decision.

Ordered by value for effort:

1. **No-show prediction (tabular, first ML task).** Gradient boosting (XGBoost/LightGBM) on lead time, day/hour, patient history, service, doctor, reminder status. Use: highlight high-risk slots, targeted reminders, smart overbooking suggestions.
2. **Appointment duration prediction.** Regression per service/doctor/patient type to improve slot sizing and reduce wait times.
3. **Demand forecasting.** Time-series (Prophet/SARIMA, later gradient boosting) for bookings per day/specialty to guide staffing and schedules.
4. **Smart slot recommendation / ranking.** Rank slots by patient preference history, doctor load balance, and cancellation risk.
5. **Waitlist fill optimization.** When a cancellation occurs, rank waitlisted patients by likelihood to accept and fit.
6. **Patient segmentation and churn risk.** Clustering and classification for retention reminders.
7. **Symptom-to-specialty triage assistant (NLP).** Start with a transformer text classifier or an LLM-based router that suggests the specialty from a free-text complaint. Must show a clear "not medical advice" notice, never diagnose, and escalate red-flag phrases to "seek urgent care".
8. **Review sentiment analysis.** Classify review text and surface themes to admins.
9. **Chatbot booking assistant.** LLM with tool calls into the real booking API (never direct DB access), with confirmation before any write.
10. **Deep learning only with the right data:** medical-image or document tasks (e.g. OCR/text extraction from uploaded lab reports, image classification) require real labeled data, ethics review, and regulatory consideration. Do not attempt deep learning on calendar/tabular data; it will not beat gradient boosting there.

Supporting infrastructure when ML starts: `ml_predictions` table (appointment_id, model_name, model_version, score, created_at), feature pipeline with documented leakage checks, MLflow (or simple versioned artifacts) for experiments, drift/performance monitoring, and an ADR for each model.

## 14. Documentation and ADRs

Maintain `README.md` (purpose, architecture, stack, local setup, env vars, DB setup, tests, run, API docs, deployment, 60-second demo script) and:

```
docs/ architecture/  api/  database/  deployment/  decisions/
```

Write an ADR (Context, Decision, Alternatives, Reasoning, Consequences) for: authentication approach, database strategy, appointment concurrency strategy, notification architecture, file storage, caching, ML service boundary. No ADRs for trivial choices. Keep OpenAPI docs synchronized with code.

## 15. Build order (one step at a time, stop after each for approval)

1. Product locked (done).
2. Data model finalized — **awaiting owner approval** of section 6 (then mark done).
3. **Deploy an empty skeleton** (Spring Boot + React + Postgres) with CI and HTTPS; ship every later step to the live URL.
4. Slots and booking with DB-enforced conflict rejection + conflict and concurrency tests.
5. Auth and roles.
6. React happy path against the real API.
7. Cancel, reschedule, statuses, emails (outbox).
8. Staff calendar and admin management.
9. Hardening: validation, rate limiting, seed data, secrets, Swagger, observability.
10. Polish: mobile, empty/error states, accessibility pass, README with architecture diagram, demo script.
11. v1.1 features.
12. v2 features as chosen by the owner.
13. ML phase, only with real data.

## 16. Definition of done (per feature)

- [ ] Requirement understood; existing architecture and reusable code inspected
- [ ] API contract defined; DTOs used; status codes correct
- [ ] DB impact considered; Flyway migration added if needed
- [ ] Validation and authorization (including resource ownership) implemented server-side
- [ ] Transactions and concurrency considered
- [ ] Error handling implemented and consistent
- [ ] Frontend states handled (loading/empty/error/forbidden); accessible and responsive
- [ ] Tests added for happy path, failures, authorization, invariants, concurrency where relevant
- [ ] Build and tests pass; CI green
- [ ] No secrets introduced; no sensitive data logged
- [ ] OpenAPI and README/docs updated if needed
- [ ] No unnecessary abstraction; no unrelated files changed

## 17. Production readiness checklist

Security, data integrity, validation, authorization, transactions, concurrency, error handling, tests, logging, monitoring, migrations, configuration, deployment, performance, accessibility, documentation. "Works on my machine" is not production ready.

## 18. Final agent self-review (before finishing any substantial task)

- **Architecture:** right layer? unnecessary coupling? respected boundaries?
- **Database:** constraints correct? N+1? indexes? transactions? can concurrent requests break an invariant?
- **Security:** authenticated? authorized? can another user reach this resource? any secret or sensitive data exposed?
- **API:** one coherent operation? DTOs? consistent errors?
- **Frontend:** all states handled? accessible? server state not duplicated?
- **Testing:** happy path, failures, authorization, invariants, concurrency?
- **Performance:** unbounded queries? extra requests? optimization without evidence?
- **Maintainability:** would another developer understand it? simpler alternative?

## 19. Golden rule

Build the simplest architecture that correctly models the domain, protects the data, enforces business invariants, and can evolve without unnecessary complexity. Design around the underlying business operation, not the current screen. Optimize for: **Correctness → Security → Data Integrity → Maintainability → Testability → Performance → Simplicity.**
