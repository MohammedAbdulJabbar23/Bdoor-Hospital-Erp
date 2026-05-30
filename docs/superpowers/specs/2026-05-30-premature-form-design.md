# Premature Form + Tour Vitals — Design Spec (Sub-project B)

- **Date:** 2026-05-30
- **Status:** Approved
- **BRD:** HMS-BRD-REC-005 §6.5 (Premature Form / Neonatal Admission Data) + P7b (Doctor tours)
- **Builds on:** sub-project A (admission spine). The form/tours attach to a `PrematureAdmission`.

## Goal
Capture the neonatal clinical data the BRD's Premature Form specifies: a per-admission static
form (measurements, clinical-pharmacy notes, culture, Rx, signatures) plus a repeating
Morning/Night **tour vitals** log, surfaced on a dedicated premature **case page**.

## Scope
**In:** static Premature Form (1:1 with admission); repeating tour entries (Morning/Night);
3 drawn/uploaded signatures; pre-fill of static measurements from registration; a case page.
**Out (later sub-projects):** nursing procedures / treatment chart / medical-history sheet /
statistics upload / routine check-up (D); drug & lab/radiology orders + finish-treatment
results-gate (C); Patient Case Form P6 (deferred per BRD §8.1).

## Architecture — new domain in the `premature` module

### `PrematureForm` (aggregate, 1:1 with admission; table `prem_form`)
`id`, `admissionId` (unique FK-by-id), `visitId`, `patientId` (snapshots), audit + `@Version`.
Fields (mandatory/optional per BRD §6.5):
- **Identity/measurements:** `ageText` (M), `birthWeightKg` (M) + `birthWeightDate`,
  `currentWeightKg` (M) + `currentWeightDate`, `gestationalAgeWeeks`/`Days` (M),
  `correctedGaWeeks`/`Days` (M), `lengthCm` (M) + `lengthDate`, `ofcCm` (M) + `ofcDate`.
- **Clinical pharmacy:** `feedingType` (M), `kcalPerOz`, `enteralPerKg`, `kcalPerKg`, `gir`,
  `pharmacyOthers` (multiline) — all optional except feeding type.
- **Culture (optional):** `lastCultureDate`, `sampleType`, `cultureResult`.
- **Rx (optional, multiline):** `prescriptionNotes`.
- **Notes (optional, multiline):** `specialistDoctorNotes`.
- **Signatures (optional):** three slots — `clinicalPharmacy`, `resident`, `seniorResident`
  — each embedding `{ imageKey, signerName, signedBy (user), signedAt }` (see Signatures).

### `PrematureTour` (entity, many per admission; table `prem_tour`)
`id`, `admissionId`, `tourType` (`MORNING`|`NIGHT`), `recordedAt`, `recordedBy`, audit + `@Version`.
Vitals (BRD tour grid; M = mandatory per tour):
`respRate` (M), `spo2` (M, %), `pulseRate` (M), `respSupport` (M, **multi-select** of
`MV`/`CPAP`/`HFNC`/`NC`/`ROOM_AIR` — stored as an `@ElementCollection` set), `bowelMotion`,
`uop` (M), `feeding`, `vomiting`, `jaundice`, `ivAccess`, `ivFluid`, `babyTempC` (M, °C),
`incubatorTempC`, `humidity` (%), `nasalSeptum`, `rbs`, `others` (multiline). It is a
time-series log — many entries over the stay, returned newest-first.

### Migration
`V0xx__premature_form.sql` (next free global version — confirm at plan time): `prem_form`,
`prem_tour`, and `prem_tour_resp_support` (element-collection) tables; UUID PKs, audit +
`version` columns; FK/index on `admission_id`.

## Signatures (drawn / uploaded)
A reusable **`SignaturePad`** (HTML canvas; draw with pointer, or upload an image) exports a
PNG. `POST …/form/signatures/{slot}` stores the PNG via the **platform `FileStorage`** (the same
infra case attachments use) and records `{ imageKey, signerName, signedBy, signedAt }` on the
form's matching slot; `GET …/form/signatures/{slot}` streams the image. Three slots:
`CLINICAL_PHARMACY`, `RESIDENT`, `SENIOR_RESIDENT`. Signatures are optional.

## Backend slices / endpoints (vertical-slice)
- `GET /api/premature/admissions/{id}/case` → `{ admission, form|null, prefill, tours[] }`.
  `prefill` is computed from the infant's registration record (birth weight, GA weeks/days,
  length, OFC) + age derived from `Patient.dateOfBirth` and `admission.admittedAt`; used to
  populate a fresh form. `isAuthenticated()`.
- `PUT /api/premature/admissions/{id}/form` → upsert `PrematureForm`. `PREMATURE_STAFF`/`DOCTOR`/`ADMIN`.
- `POST /api/premature/admissions/{id}/tours` → add a tour entry. `PREMATURE_STAFF`/`DOCTOR`/`NURSE`/`ADMIN`.
- `POST /api/premature/admissions/{id}/form/signatures/{slot}` (PNG) + `GET` (stream).
  `PREMATURE_STAFF`/`DOCTOR`/`ADMIN`.
All return DTOs via the central `GlobalExceptionHandler`; reads `@Transactional(readOnly=true)`.

## Frontend
- **`PrematureCasePage`** at `/premature/admissions/:id` — tabs: **Overview** (admission summary
  + patient → `/patients/:id` history link), **Premature Form** (react-hook-form + zod, grouped
  sections: Measurements / Clinical pharmacy / Culture / Rx / Notes / Signatures; pre-filled,
  Save), **Tours** (newest-first list + "Add tour" with the 17-field grid, Morning/Night toggle).
  Designed to host the future C/D tabs.
- **Entry points:** the bed-detail drawer and bed card get **"Open case →"** → the case page.
- **`SignaturePad`** component (canvas draw + upload fallback).
- Data via react-query (the page loads `/case`; mutations invalidate it). Full **en/ar** i18n,
  RTL-aware. New route in `App.tsx` + nav unaffected (reached via the workspace).

## Testing
- **Backend unit:** `PrematureForm`/`PrematureTour` construction + validation (mandatory fields,
  resp-support multi, tour type), age-derivation/pre-fill logic.
- **Backend integration (Testcontainers, via Failsafe):** upsert form (create + update),
  pre-fill defaults from a registered infant, record + list tours, signature upload + retrieve,
  role-based auth (200/403).
- **E2E (Playwright):** open an under-care bed's case → fill & save the Premature Form (assert
  persisted on reload) → add a Morning tour (assert listed) → draw & save a signature (assert
  rendered). Existing specs unaffected.
- `npx tsc -b` + `npm run build` clean; `mvn -pl premature,app -am verify` green; live screenshot.

## Open items to pin at plan time
- Exact `FileStorage` API (mirror `CaseAttachmentController`/`LocalFileSystemStorage`).
- Next free Flyway version (current max + 1).
- Whether tours support edit/delete (B = add + list; edit/delete deferred unless trivial).
