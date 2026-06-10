# Reliable Documents + Case-Page Documents Tab + Patient Full History — Design Spec

- **Date:** 2026-06-10
- **Status:** Approved
- **Motivation:** (1) Document storage resolves `hms.attachments.dir` relative to the launch
  CWD — the repo already shows three scattered roots (`data/attachments/`, `backend/data/`,
  `backend/app/data/`); no integrity guarantees. (2) Lab/Radiology/ECO result documents
  (`CaseAttachment`) never appear on the bed-stay case page (text summary only), and the
  endpoints that serve them exclude `PREMATURE_STAFF`/`EMERGENCY_STAFF`/`NURSE` while
  being readable cross-department by results staff (known §13 privacy gap). (3) The patient
  page (`/patients/:id`) shows only visits + doctor exams — no admissions, no clinical
  forms, no documents.
- **Delivers in passing:** BRD REC-005 P7c (statistics/scanned-form upload) via direct stay
  uploads; closes the §13 gap for the stay-scoped result-document path.

## Scope
**In:** storage hardening (platform); `StayDocument` upload/list/stream/archive in
`bed-stay-forms`; merged Documents tab on the case page (both departments); patient-profile
full-history overhaul (`HistoryContributor` port + unified timeline UI); en/ar i18n; ITs +
Playwright.
**Out:** MinIO/S3 implementation (interface stays pluggable); DICOM viewing; editing the
doctor visit page's Lab/Radiology tabs; retention/backup tooling beyond the integrity
check + consolidation script.

## Part 1a — Storage hardening (`platform`)

1. `FileStorage` gains `StoredBlob saveVerified(InputStream in, String suggestedName)`
   returning `record StoredBlob(String storageKey, String sha256, long sizeBytes)` —
   SHA-256 computed while streaming to disk (DigestInputStream), size counted, no second
   pass. Existing `save(...)` remains (signature call sites untouched).
2. Missing blob on `open(...)` → new typed `StorageMissingException` mapped to **404,
   code `DOCUMENT_MISSING`** in `GlobalExceptionHandler` (today: raw IOException → 500).
3. Startup: log the resolved absolute root; **WARN** when the value equals the relative
   default (`data/attachments`) — the cause of the scattered roots. Dev runs set
   `hms.attachments.dir` explicitly (run config + compose env) so all launches share one
   root. `docs/OPERATIONS.md` gets a "document storage" section (canonical dir, backup
   expectation, consolidation procedure).
4. **Integrity check:** `POST /api/admin/storage/verify` (ADMIN) walks every recorded
   document (stay documents, case attachments, form signatures), re-hashes the blob where
   a sha256 is recorded, and returns `{checked, missing[], corrupt[], orphanedFiles[]}`
   (orphaned = files under the root with no DB reference). Synchronous, paged walk; fine
   for Phase-1 volumes.
5. `ops/consolidate-attachments.sh`: merges legacy roots (`data/attachments`,
   `backend/data/attachments`, `backend/app/data/attachments`) into the canonical root
   (keys are `yyyy-MM-dd/uuid.ext`, so a move preserves all DB references; collisions
   impossible by construction).

## Part 1b — Stay documents (`bed-stay-forms`)

### `StayDocument` (aggregate; table `stay_document`, migration V029 — confirm next-free)
`id`, `department`, `stayId`, `patientId` (snapshot from `StayInfo`, enables patient-level
aggregation in Part 2), `fileName` (≤300), `contentType` (≤150), `sizeBytes`, `sha256`
(64), `storageKey` (≤500), `label` (optional ≤200), `uploadedBy` (user id),
`archived` (bool, default false; BRD: records are archived, never deleted), audit +
`@Version`. Index `(department, stay_id, created_at DESC)`; index `(patient_id)`.

### `StayDirectory.orders()` (port extension)
`List<StayOrderRef> orders(UUID stayId)` where
`record StayOrderRef(UUID visitId, String targetType, Instant orderedAt)`. Implemented by
premature + emergency from their existing order links. Used to resolve result attachments:
forwarded visit → `DepartmentCase` (by `visitId`) → `CaseAttachment` rows. `bed-stay-forms`
gains a Maven dependency on `department-services` (acyclic; the ArchUnit rule only forbids
premature/emergency dependencies).

### Endpoints (`/api/bed-stays/{department}/{stayId}/documents`)
| Endpoint | Method | Roles |
|---|---|---|
| `` (merged list) | GET | dept staff, DOCTOR, NURSE, ADMIN |
| `` (upload, multipart `file` + optional `label`) | POST | dept staff, DOCTOR, NURSE, ADMIN |
| `/{documentId}/file` (uploaded doc stream) | GET | as list |
| `/{documentId}/archive` | POST | dept staff, ADMIN |
| `/results/{attachmentId}/file` (result-attachment stream, stay-scoped) | GET | as list |

- Upload policy: max **20 MB**; content-type whitelist `image/*` + `application/pdf`
  (server-side check; 422 `DOCUMENT_TYPE_NOT_ALLOWED` / `DOCUMENT_TOO_LARGE`). Upload and
  archive blocked on closed/cancelled stays (`requireOpen` → `STAY_CLOSED` 422); reads stay
  open. Department scoping via the existing `BedStayAccess`.
- GET list returns one merged, newest-first array of
  `{id, source: UPLOAD|LABORATORY|RADIOLOGY|ECO, fileName, contentType, sizeBytes, label,
  uploadedBy/At, archived, fileUrl}` — uploads from `stay_document` (archived included,
  flagged; UI de-emphasizes), results from `CaseAttachment` via `orders()`. Result entries
  are read-only (no archive — they belong to the producing department).
- `/results/{attachmentId}/file` authorizes via `BedStayAccess.checkRead` **and** verifies
  the attachment's `DepartmentCase.visitId` is one of this stay's `orders()` — premature
  staff can stream a lab report for *their* stay but no other (fixes §13 for this path;
  the legacy `/api/dept-cases/...` endpoints are unchanged).

## Part 1c — Documents tab (case page)

New `documents` tab (both departments) between Treatment and Billing
(`caseView.tabs.documents`):
- Source-tagged rows: badge (Lab result / Radiology / ECO / Uploaded), file-type icon
  (image/PDF), fileName + optional label, uploader + date, size.
- **Preview dialog**: `<img>` for `image/*`, `<iframe>`/`<object>` for `application/pdf`
  (blob URL fetched with auth via the api client, revoked on close); Download button.
  The viewer is one shared component (`shared/ui/DocumentPreview.tsx`) reused by Part 2.
- Upload control (file input + optional label) hidden when the case is read-only
  (CLOSED/CANCELLED); archive action with confirm for uploaded docs only; archived rows
  collapsed under a "show archived" toggle.
- Tab badge shows the live document count. en/ar keys under `caseView.documents.*`.

## Part 2 — Patient profile full history

### `HistoryContributor` port (`clinical-case`)
```java
public interface HistoryContributor {
    List<HistoryEntry> contribute(UUID patientId);
}
record HistoryEntry(Instant at, HistoryEntryType type, String department,
                    String title, String detail, HistoryRefs refs) {}
enum HistoryEntryType { VISIT, ADMISSION, EXAM, FORM, DOCUMENT, ORDER }
record HistoryRefs(UUID visitId, UUID stayId, UUID documentId, String fileUrl) {}
```
Implementations (same pattern as `StayDirectory`): **premature** (one ADMISSION entry per
admission: opened, plus closed when discharged — no per-extension noise), **emergency**
(same), **bed-stay-forms** (one FORM entry per filed form: the medical history sheet, each
treatment-chart date, and a single nursing-log entry per stay carrying the row count;
DOCUMENT entries for uploads via the `patientId` column and for result attachments via the
stays' orders), and clinical-case itself contributes visits + finalized exams. The existing
`GET /api/patients/{id}/history` response keeps its current fields and gains
`timeline: HistoryEntry[]` (merged, newest-first). Roles: unchanged from the current
endpoint (doctors/clinical staff/admin — verify current `@PreAuthorize` and keep).

### Profile page redesign (`/patients/:id`)
- **Identity header card**: avatar circle, full name, MRN, age (derived), gender, VIP
  badge, contact (mobile / guardian for infants), archived state.
- **Summary chips**: total visits, admissions, documents, last-seen date.
- **Unified timeline**: filter pills (All / Visits / Admissions / Forms / Documents),
  chronological cards with department color coding (premature = brand, emergency = amber,
  outpatient = ink), expandable detail, DOCUMENT entries open the shared `DocumentPreview`
  dialog, ADMISSION/VISIT entries link to `/premature/admissions/:id`,
  `/emergency/cases/:id`, or the visit/exam view. Empty states per filter.
- Existing exam/visit content on the page is preserved (folded into the new layout, not
  deleted). Full en/ar + RTL; ink/brand design language; `data-testid`s:
  `profile-header`, `chip-*`, `timeline-filter-*`, `timeline-entry-*`.

## Decisions
1. **Aggregate at read time** (no copying of result files into `stay_document`) — history
   included automatically, no blob duplication.
2. **Archive, never delete** (BRD §8.2); result attachments not archivable from the case
   page (owned by the producing department).
3. **Port pattern for cross-module reach** (`orders()` on StayDirectory;
   `HistoryContributor` in clinical-case) — keeps dependency arrows pointing inward;
   ArchUnit extended: clinical-case must not depend on premature/emergency/bed-stay-forms.
4. **sha256 verified opportunistically** (recorded at save; integrity endpoint re-hashes
   on demand) — downloads are not hash-checked inline to keep streaming cheap.

## Testing
- **ITs:** stay-document upload→list→stream→archive round-trip (sha256 + size recorded,
  archived flag honored); oversize → 422, bad type → 422; closed-stay upload → 422;
  cross-department list/stream → 403; result attachment of stay A streamed via stay B →
  404; lab-staff uploads findings → document appears in the stay's merged list with
  source LABORATORY; integrity endpoint reports a deliberately deleted blob as missing;
  full-history endpoint returns VISIT+ADMISSION+FORM+DOCUMENT entries for a patient who
  has all of them; ArchUnit boundary rule for clinical-case.
- **Playwright:** case-page Documents journey (upload with label → appears tagged
  Uploaded → preview dialog opens (image) → archive → hidden behind toggle; lab result
  doc visible with LABORATORY badge after dept-case upload via API); patient profile
  (header card facts, filter pills narrow the timeline, document preview opens, admission
  entry navigates to the case page). Existing suites stay green.
