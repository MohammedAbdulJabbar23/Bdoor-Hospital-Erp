# Operations

## Document storage

All clinical attachments (stay documents, lab/radiology result files, signature images) are
stored as files on disk under a single canonical directory; the database only holds metadata
plus an opaque storage key of the form `yyyy-MM-dd/uuid.ext`. The directory is configured with
the `HMS_ATTACHMENTS_DIR` environment variable (Spring property `hms.attachments.dir`,
default `data/attachments`). In production this MUST be an absolute path — e.g.
`HMS_ATTACHMENTS_DIR=/var/lib/hms/attachments` — so the location does not depend on the
process working directory.

If the backend logs a WARN at startup saying the attachments dir is a RELATIVE path, files are
being written relative to wherever the JVM was launched from. Different launch directories then
silently produce different attachment roots (this is how `data/attachments`,
`backend/data/attachments` and `backend/app/data/attachments` came to coexist during
development). Fix the env var rather than ignoring the warning.

Backups must capture the attachments directory and the Postgres database together, from the
same point in time. A database dump without the files leaves every document row pointing at
nothing; files without the database are unlabeled blobs.

To merge legacy attachment roots into the canonical directory, stop the backend, then run:

    ops/consolidate-attachments.sh /var/lib/hms/attachments [legacy-dir ...]

Storage keys are date-prefixed UUIDs, so a plain move preserves every DB reference and
collisions are impossible; files already present at the destination are skipped and reported.

After any consolidation, restore, or disk incident — and periodically as routine hygiene —
call `POST /api/admin/storage/verify` (ADMIN role). It walks every DB-referenced blob across
all modules and returns: `checked` (total references), `missing` (DB rows whose file is gone),
`corrupt` (files whose SHA-256 no longer matches the recorded hash, where one was recorded),
`unreadable` (references whose blob could not be opened or hashed, e.g. a null storage key or
an I/O error mid-read), and `orphanedFiles` (files on disk that no DB row references). A healthy
system returns empty `missing`, `corrupt`, and `unreadable` lists; `orphanedFiles`, however, can
legitimately contain superseded signature images — re-signing overwrites the reference without
deleting the old blob — so treat orphans as a cleanup candidate list, not as corruption.

## Deployment

The repository ships a self-contained production stack: `docker-compose.prod.yml` at the repo
root builds and runs three services — `db` (postgres:16-alpine, named volume `hms-prod-db`),
`backend` (multi-stage build from `backend/Dockerfile`, prod Spring profile, attachments on
named volume `hms-prod-attachments` mounted at `/var/hms/attachments`) and `frontend`
(nginx serving the built SPA on port 80, proxying `/api/` to the backend). The volume names
are deliberately distinct from the dev compose's `hms-db-data`, so dev and prod stacks never
share data even on the same machine.

### Prerequisites

- Docker Engine + the compose plugin (`docker compose version` works).
- Port 80 free on the host.
- Outbound network access during the first build (Maven Central, npm, Docker Hub).

### First-time setup

    cp .env.prod.example .env.prod
    # edit .env.prod:
    #   POSTGRES_PASSWORD          — strong DB password
    #   HMS_JWT_SECRET             — openssl rand -base64 48
    #   HMS_ADMIN_INITIAL_PASSWORD — initial password for the bootstrap "admin" user
    docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build

On first boot Flyway creates the schema and `ProdAdminBootstrap` (prod profile only) creates a
single active ADMIN user, username `admin`, with `HMS_ADMIN_INITIAL_PASSWORD`. The demo seeder
(`DevDataSeeder`) is disabled under the prod profile — no demo users or catalogue data exist.

**Log in and change the admin password immediately.** The bootstrap password sits in plain
text in `.env.prod`; once any user exists the bootstrap never runs again, so after changing
the password you can (and should) blank `HMS_ADMIN_INITIAL_PASSWORD` in `.env.prod`. Never
commit `.env.prod` — it is gitignored; only `.env.prod.example` (placeholders) is tracked.

If the users table is empty and `HMS_ADMIN_INITIAL_PASSWORD` is unset, the backend refuses to
start (fail fast) — a production system with zero users and no way to log in is a
misconfiguration.

### Upgrades

    git pull
    docker compose -f docker-compose.prod.yml --env-file .env.prod up -d --build

Flyway applies any new migrations on boot; data in the named volumes is untouched. Roll out
during a quiet window — the backend restarts and in-flight sessions get logged out.

### Backups

Back up the database and the attachments volume **together, from the same point in time**
(see "Document storage" above for why one without the other is useless):

    docker exec hms-prod-db pg_dump -U hms hms | gzip > hms-db-$(date +%F).sql.gz
    docker run --rm -v hms-prod-attachments:/data -v "$PWD":/backup alpine \
        tar czf /backup/hms-attachments-$(date +%F).tar.gz -C /data .

(The compose file pins the volume names explicitly, so `hms-prod-db` and
`hms-prod-attachments` are the exact names — no project prefix.) Restore order: load the SQL
dump into a fresh `db` volume, untar the attachments into the attachments volume, start the
backend, then run `POST /api/admin/storage/verify` to confirm zero missing/corrupt blobs.

### Migrating attachments from an existing (non-Docker) install

If you are moving onto this stack from a host-run backend, consolidate any legacy attachment
roots first (`ops/consolidate-attachments.sh`, see "Document storage"), then copy the canonical
directory into the volume once:

    docker run --rm -v hms-prod-attachments:/data -v /var/lib/hms/attachments:/src:ro alpine \
        sh -c 'cp -a /src/. /data/'

This is a one-time step; afterwards the volume is the single source of truth.

### TLS

The stack serves plain HTTP on port 80. Terminate TLS at the hospital's existing reverse
proxy / load balancer in front of the `frontend` container, or extend `frontend/nginx.conf`
with a certificate — hospital network specifics are out of scope here.
