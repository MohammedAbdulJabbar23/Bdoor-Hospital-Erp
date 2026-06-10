# Bed-Stay Clinical Forms + Patient Case Form — Design Spec (Sub-project D core + P6)

- **Date:** 2026-06-10
- **Status:** Approved
- **BRD:** HMS-BRD-REC-005 §6.6.1–6.6.3 (shared clinical forms), P6 (Patient Case Form),
  P7d/P7e/P7f; HMS-BRD-REC-004 §6.7 (shared-form counterpart on the Emergency side).
- **Source of truth:** the Premature BRD's §6.6 field lists **plus the paper forms embedded
  in the BRD docx** (extracted to `_brd_embedded_extracted/`): `MedicalHistorySheet.pdf`,
  `NursingProcdure.pdf`, `TreatmentChart.pdf`, `Pateint Case.pdf`. Note: Emergency BRD
  §6.7.1–6.7.3, which Premature §6.6 cites for "full field specification", **was never
  written** — §6.7 contains only an intro sentence. The field lists below are therefore
  authoritative.

## Goal
Align the premature workflow with the hospital's actual paper chart set: digitise the three
clinical forms the BRD declares **shared between Premature and Emergency** (Medical History
& Physical Examination Sheet, Nursing Procedures log, Treatment Chart) and the
premature-only **Patient Case Form** (P6 — its structure, previously deferred per §8.1, is
now known from the embedded scan).

## Scope
**In:** new shared `bed-stay-forms` backend module (3 aggregates, keyed by department +
stay); `StayDirectory` port implemented by `premature` and `emergency`; `PatientCaseForm`
aggregate in `backend/premature`; three new shared tabs (History / Nursing / Treatment) on
`BedStayCasePage` for both departments + a premature-only Case File tab; drawn signatures
for form-level signs; en/ar i18n; ITs + Playwright coverage.
**Out (still deferred):** routine check-up (P7a), statistics upload (P7c), drug orders
P7g/PHARMACY order target, results-return notifications, sub-project E items (audit-history
table, role split).

## Decisions (with rationale)
1. **Shared module, wired to both departments.** BRD §6.6: form structures are "shared
   between the Premature and Emergency modules", definitions "identical". One implementation
   prevents drift; Emergency gets the forms now rather than re-building in sub-project EB.
2. **P6 prefilled, not duplicated.** Registry fields (name, mother's name, age, sex,
   occupation, address) render read-only from the patient record; only case-specific fields
   are stored (ward no., next-of-kin contact, treating specialist, initial/final diagnosis).
3. **Signatures: drawn for forms, auto for rows.** Medical History's three signature lines
   and the Treatment Chart doctor sign reuse the existing `SignaturePad` + platform
   `FileStorage` pattern. Nursing-procedure rows are auto-attributed (nurse name + user id +
   timestamp) instead of drawn per-row signs.
4. **Physical examination is free text.** §6.6.1 mentions "Physical Examination grid rows"
   but neither the paper form nor any BRD defines a grid; we store one multiline field
   rather than invent structure.
5. **Forms are read-only once the stay is Closed.** Writes are rejected after case closure;
   reads remain available.

## Architecture — new `backend/bed-stay-forms` module

All aggregates carry `department` (`PREMATURE` | `EMERGENCY`) + `stayId` (the
`PrematureAdmission` / emergency case id), audit columns + `@Version`, following existing
module conventions (vertical slices, ArchUnit rules, auto-config).

### `StayDirectory` (port)
Interface in `bed-stay-forms`; Spring beans implementing it live in `premature` and
`emergency` (same pattern as `PrematurePaymentBridge`). Answers, for a `(department,
stayId)` pair:
- `exists` / `isOpen` (open = not Closed; gates writes),
- prefill: patient id, patient name, MRN/Pt. code, age (derived), admission date (DOA).

### `MedicalHistorySheet` (1:1 per stay; table `stay_medical_history`)
Stored fields: `weightKg`, `heightCm`, `doctorName`, `chiefComplaint`, `presentIllnessHx`,
`psHx`, `pmHx`, `familyHx`, `allergicHx`, `socialSmoker`, `socialAlcohol`, `socialSleep`,
`drugHx`, `physicalExamination` (multiline). All optional (paper allows partial completion).
Signature slots (optional, drawn): `SPECIALIST`, `PERMANENT`, `RESIDENT` — each
`{ imageKey, signerName, signedBy, signedAt }`. Auto fields (Pt. Code, Pt. Name, Age, DOA)
come from `StayDirectory` prefill in the response; never stored.

### `NursingProcedureEntry` (many per stay; table `stay_nursing_procedure`)
`procedureName` (required), `performedAt` (date+time, required), `note` (optional);
`nurseName` + `recordedBy` (user id) auto-filled from the authenticated user, `recordedAt`
timestamp. Rows are append-only (no edit/delete in this slice — matches a paper log and the
BRD's auditability rule); listed newest-first.

### `TreatmentChart` (one per stay per date; table `stay_treatment_chart` + child rows)
`chartDate` (required, unique per stay+department), doctor signature slot (optional, drawn),
rows (`stay_treatment_chart_row`, ordered): `position` (auto 1..n, no hard cap — the paper's
11-row limit is a page artifact, "additional pages supported"), `medicineName` (required),
`dose`, `frequency`, `timing` — exactly 6 optional short-text slots matching the paper's
AM/AM/PM/PM/PM/AM columns (free text, since the paper is hand-annotated with times).
Chart upsert replaces the row set atomically (form-style editing, like `upsertform`).

### Migration
One Flyway migration (next free global version — confirm at plan time):
`stay_medical_history`, `stay_nursing_procedure`, `stay_treatment_chart`,
`stay_treatment_chart_row`; UUID PKs, audit + `version`, index on `(department, stay_id)`,
unique constraints: medical-history per stay, chart per stay+date.

## Endpoints (under `/api/bed-stays/{department}/{stayId}/…`)
| Endpoint | Method | Roles (write) |
|---|---|---|
| `/medical-history` | GET / PUT | GET: dept staff, DOCTOR, NURSE, ADMIN. PUT: dept staff, DOCTOR, ADMIN |
| `/medical-history/signatures/{slot}` | GET / POST | as above (PUT roles for POST) |
| `/nursing-procedures` | GET / POST | GET: dept staff, DOCTOR, NURSE, ADMIN. POST: dept staff, NURSE, ADMIN |
| `/treatment-charts` (GET lists all; PUT at `/{date}`) | GET / PUT | GET: dept staff, DOCTOR, NURSE, ADMIN. PUT: dept staff, DOCTOR, ADMIN |
| `/treatment-charts/{date}/signature` | GET / POST | as chart PUT |

"Dept staff" = `PREMATURE_STAFF` for premature stays, `EMERGENCY_STAFF` for emergency
stays — enforced per-request against the path's `{department}` (a `@PreAuthorize` can't see
which department; the handler/controller checks the path-role match and returns 403 on
mismatch). Unknown stay → 404; closed stay on write → 409/422 per existing convention.

## `PatientCaseForm` (P6) — in `backend/premature`
1:1 with admission (table `prem_patient_case`): `wardNumber`, `nextOfKinAddress`,
`nextOfKinPhone`, `treatingSpecialist`, `initialDiagnosis`, `finalDiagnosis` (all optional).
Slices `upsertcaseform` / folded into `getcase` response alongside prefill (registry fields
read-only: name, mother's name, age, sex, occupation, governorate/district/sub-district/
quarter/alley/house-no from the patient record — whatever subset the registry holds; absent
registry fields render as em-dash, not editable inputs). Roles: PUT `PREMATURE_STAFF`,
`DOCTOR`, `ADMIN`.
**Flagged for client:** one header label on the scan is illegible (pairs with رقم الردهة);
left out of the model. If the client identifies it, it's an additive column.

## Frontend
- `BedStayCasePage` gains three tabs for **both** departments: `history`, `nursing`,
  `treatment` (after Clinical, before Billing). Premature cases additionally get `caseFile`
  (P6). Tab content components live in `frontend/src/features/beds/case/forms/`.
- Forms follow `PrematureCasePage` patterns: plain controlled inputs (no form library,
  matching existing style), save via PUT/POST, `SignaturePad` reuse for drawn signatures,
  newest-first lists for nursing rows, date picker + row editor for treatment charts.
- Read-only rendering when the case is Closed (server enforces; UI disables).
- en/ar i18n keys under `caseView.forms.*`; Arabic labels mirror the paper forms
  (e.g. التشخيص الأولي / التشخيص النهائي for P6 diagnoses).

## Testing
- **Backend ITs (Failsafe):** per-form happy path (write→read round-trip incl. prefill),
  closed-case write rejection, department/role mismatch → 403, unknown stay → 404,
  nursing rows append-only + attribution, chart upsert replaces rows, signature
  upload/stream round-trip. ArchUnit suite for the new module. Emergency-side IT proving
  the same endpoints work on an emergency case via its `StayDirectory` impl.
- **Playwright:** premature case — fill each form, save, reload, assert persisted (incl.
  one drawn signature); emergency case smoke — open History tab, save a field, reload.
- Full backend `mvn verify` + existing Playwright suites stay green.
