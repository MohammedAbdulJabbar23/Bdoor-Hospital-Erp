# Production Deployment Package — Design Spec

- **Date:** 2026-06-11
- **Status:** Approved (user: "upload to production"; no deploy target exists in the repo)
- **Goal:** make the HMS deployable on a hospital server with one command, with persistent
  data (Postgres + the hardened document storage) surviving restarts.

## Deliverables

1. **`backend/Dockerfile`** — multi-stage: `maven:3.9-eclipse-temurin-21` builds the reactor
   (`mvn -pl app -am package -DskipTests`); runtime `eclipse-temurin:21-jre` running the app
   jar. `HMS_ATTACHMENTS_DIR=/var/hms/attachments` default in the image; datasource via
   `SPRING_DATASOURCE_URL/USERNAME/PASSWORD` env.
2. **`frontend/Dockerfile`** + **`frontend/nginx.conf`** — `node:20` build → `nginx:alpine`
   serving `dist/` with SPA fallback (`try_files … /index.html`) and `/api/` proxied to
   `backend:8080` (preserve `client_max_body_size 30m` for document uploads).
3. **`docker-compose.prod.yml`** — services: `db` (postgres:16-alpine, named volume,
   healthcheck), `backend` (depends_on db healthy, attachments named volume mounted at
   `/var/hms/attachments`, restart unless-stopped), `frontend` (port 80 → nginx). Secrets
   via a committed **`.env.prod.example`** (POSTGRES_PASSWORD, JWT secret if the identity
   module reads one from config — check `application.yml`/identity for the property name and
   wire the env passthrough; never commit real secrets).
4. **Demo-data gating** — check `DevDataSeeder`: demo users/catalogue must NOT seed in
   production. Gate the seeder behind the `dev` Spring profile (compose dev keeps it via
   `SPRING_PROFILES_ACTIVE=dev` documented; existing local runs must keep working — verify
   how the app is started in dev and keep that path seeded). Production bootstraps a single
   ADMIN user only when the users table is empty, username `admin`, password from
   `HMS_ADMIN_INITIAL_PASSWORD` env (required in prod profile — fail fast if missing and no
   users exist). OPERATIONS.md documents changing it immediately.
5. **`docs/OPERATIONS.md`** — new "Deployment" section: prerequisites (Docker + compose),
   first-time setup (`cp .env.prod.example .env && edit`, `docker compose -f
   docker-compose.prod.yml up -d --build`), upgrade procedure (git pull → up -d --build;
   Flyway migrates on boot), backup (pg_dump + the attachments volume together), where the
   one-time attachment consolidation fits.

## Verification (must actually run)

Build both images, bring the prod stack up locally (dev stack stopped), then: frontend
serves 200 on `:80`; login via nginx proxy (`POST /api/auth/login`) succeeds with the
bootstrap admin; a document upload round-trips (proves the attachments volume + multipart
proxy limits); `docker compose restart backend` and the uploaded document still streams
(persistence). Existing dev workflow (compose db + spring-boot:run + vite) still works —
re-run one IT class and one e2e spec after the seeder gating to prove nothing dev-side broke.

## Out of scope
TLS/domain (hospital network specifics unknown — documented as a note), CI/CD, image
registry, MinIO.
