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
