# PulseClinic

Portfolio clinic appointment and operations platform. This project is a demonstration application and must not be used to store real patient or medical data.

## Stack

Spring Boot 3 (Java 17) · PostgreSQL 16 + Flyway · React + TypeScript + Tailwind · Docker · GitHub Actions

## Local setup

1. Copy `.env.example` to `.env` (do not commit real secrets).
2. Start Postgres: `docker compose up -d postgres`
3. Backend: `cd backend` then `mvn spring-boot:run`
4. Frontend: `cd frontend` then `npm install` and `npm run dev`
5. Open http://localhost:5173 — it should report API and database up.

Full stack in Docker: `docker compose up --build` then http://localhost:3000 (API http://localhost:8081/api/v1/health).

Local Docker starts with fictional demo clinic data for development. Demo credentials are intentionally not published in this repository.

## Tests

- Backend (Testcontainers PostgreSQL): `cd backend` then `mvn -B verify`
- Frontend: `cd frontend` then `npm test` and `npm run build`

## Env vars

`DATABASE_URL` (JDBC), `DATABASE_USERNAME`, `DATABASE_PASSWORD`, `SERVER_PORT`, `PULSECLINIC_CORS_ORIGINS`

## Deploy (HTTPS)

Connect this repo to Render or Railway. Run the API as a Docker web service (`backend/Dockerfile`), Postgres as the managed database, and the frontend as a static site or `frontend/Dockerfile`. Set the JDBC URL and a strong DB password. Paid/keep-alive hosting is required to avoid cold starts.

## Privacy and security

- Do not commit `.env` files, API keys, app passwords, or real patient data.
- Use only fictional data and `@pulseclinic.demo` identities for local demonstrations.
- Copy `.env.example` to `.env` and replace every development secret before deployment.
