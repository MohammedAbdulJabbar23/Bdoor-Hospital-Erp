# Bed-Stay Orders + Results-Pending Gate + Discharge Note — Design Spec

- **Document ID:** internal design for HMS-BRD-REC-004 (Emergency) / REC-005 (Premature) — clinical-loop sub-project
- **Date:** 2026-06-02
- **Status:** Approved (design); spec under review
- **Module / process:** Premature + Emergency bed-stay cases — in-stay ordering, finish-treatment results gate, discharge note

## 1. Context
Premature (REC-005) and Emergency (REC-004) admission spines are built and merged: reception →
bed + (service) → two-stage payment → extend → finish-treatment → discharge, with the P12b
re-issue. Two clinical-completeness gaps remain that this sub-project closes for **both**
departments:
1. An admitted patient cannot be sent for Lab/Radiology/ECO work-ups from the bed-stay case.
2. `finish-treatment` has no gate on outstanding results (the premature `FinishTreatmentHandler`
   literally carries the comment *"the block finish if lab/radiology results pending gate is
   sub-project C"*), and there is no discharge note.

The generic forward/return machinery already exists for the doctor-appointment flow:
`ForwardVisitHandler` creates a `FORWARDED` child visit and **pauses the parent** to
`AWAITING_RESULTS`; `FinalizeCaseHandler`/`ReturnVisitHandler` call
`Visit.receiveResultsFromChild(...)`, which **requires** the parent to be `AWAITING_RESULTS` and
resumes it to `IN_PROGRESS`. That model assumes the doctor "pause-and-wait, one order at a time"
pattern.

## 2. Why bed-stay ordering is different (the core design driver)
A bed-stay admission is **continuous**: the patient stays admitted (visit `IN_PROGRESS`) while a
work-up happens, and may need **several concurrent orders** (e.g. Lab *and* X-ray). The existing
pause/resume model would (a) make the admission look "paused" and (b) break on concurrent orders —
the single `AWAITING_RESULTS` flag can't track two open children, and the second child's return
would throw `NOT_AWAITING_RESULTS`.

**Chosen approach (B):** bed-stay orders create a forwarded child visit **without pausing the
parent**, and the return path is made **tolerant** of a non-paused parent. The finish gate checks
child visits directly. This reuses the entire downstream department flow unchanged and is the only
change to shared visit code (backward-compatible with the doctor flow).

Rejected: (A) reuse pause/resume as-is — wrong semantics, breaks on concurrent orders, hard-blocks
finish (conflicts with the warn+override decision); (C) a separate orders aggregate — duplicates
the forward/return/department machinery.

## 3. Goals
| Ref | Requirement |
|---|---|
| O1 | From a premature admission / emergency case, order Lab, Radiology, or ECO (creates a forwarded child visit; parent bed-stay visit stays `IN_PROGRESS`). |
| O2 | The ordered department processes the child via its **existing** opencase → referral payment → findings → finalize flow, unchanged. |
| O3 | When a child is finalized, results return without requiring/altering the bed-stay parent's status (tolerant return); a `VisitReturnedEvent` is still emitted. |
| G1 | `finish-treatment` computes pending orders = child visits not in `{COMPLETED, CANCELLED}`. |
| G2 | Pending + no override → `409 RESULTS_PENDING` listing open orders (dept + status). |
| G3 | Pending + override → record `finishOverrideReason` (audit) and finish. |
| G4 | No pending → finish unchanged. |
| D1 | Free-text `dischargeNote` settable/updatable on the admission/case; surfaced in the bed-detail drawer; persists on the case. |
| UI | Drawer (both depts): Orders section (Send to Lab/Radiology/ECO + live order list w/ status), Discharge note textarea+save, and a Finish "results pending" confirm→override dialog. |
| i18n | en/ar for all new strings. |

## 4. Non-goals
Structured discharge summary fields / printable discharge letter (this pass is free-text only).
Notifications/alerts on results return (the `VisitReturnedEvent` is emitted; wiring a notification
feed is separate). Ordering Drugs/Pharmacy from bed-stay. Premature C/D/E and Emergency EB clinical
forms (medical history, nursing procedures, treatment chart, statistics). A hard results block
(decision: warn + override).

## 5. Architecture & changes

### 5.1 Shared visit code (minimal, backward-compatible)
- **`ForwardVisitHandler`** — add a `pauseParent` parameter (overloaded `handle(parentVisitId, cmd,
  boolean pauseParent)`; existing 2-arg `handle` delegates with `pauseParent = true`). When false,
  the child is created but `parent.markAwaitingResults()` is **not** called and no parent event is
  pulled for a status change.
- **`Visit.receiveResultsFromChild(childId, childType, summary)`** — make tolerant:
  - if `status == AWAITING_RESULTS` → snapshot summary, `transitionTo(IN_PROGRESS)`, emit
    `VisitReturnedEvent` (today's doctor behavior, **unchanged**);
  - else (parent already `IN_PROGRESS`, i.e. bed-stay) → snapshot summary, emit `VisitReturnedEvent`,
    **no transition**.
  Remove the hard `NOT_AWAITING_RESULTS` throw for the bed-stay case while keeping the doctor path
  identical. `FinalizeCaseHandler`/`ReturnVisitHandler` are otherwise unchanged.

### 5.2 Premature module
- `PrematureAdmission`: new fields `dischargeNote` (text, nullable), `finishOverrideReason` (text,
  nullable).
- Slice `orderworkup`: `POST /api/premature/admissions/{id}/orders` body `{ targetType:
  LABORATORY|RADIOLOGY|ECO }`. Handler: load admission (must be `UNDER_CARE`), load visit, call
  `ForwardVisitHandler.handle(visitId, cmd, false)`; return the created child `VisitResponse`.
  Roles: `PREMATURE_STAFF`/`DOCTOR`/`NURSE`/`ADMIN`.
- Slice `setdischargenote`: `POST /api/premature/admissions/{id}/discharge-note` `{ note }`.
- `FinishTreatmentHandler.handle(admissionId, FinishCommand)` where `FinishCommand{ boolean
  override, String overrideReason }`: query `visits.findAllByParentVisitId(visitId)`; pending = any
  child status ∉ `{COMPLETED, CANCELLED}`. If pending && !override → throw
  `DomainException("RESULTS_PENDING", …)` carrying the open-order list. If pending && override →
  require non-blank reason, set `admission.recordFinishOverride(reason)`. Then proceed with the
  existing finish logic.
- `ListAdmissionOrders` (read, required by the drawer): `GET /api/premature/admissions/{id}/orders`
  returns the case's child visits (`visitDisplayId`, `visitType`, `status`) via
  `findAllByParentVisitId`, so the drawer can render the live order list.

### 5.3 Emergency module
Mirror 5.2 against `EmergencyCase` (status `UNDER_TREATMENT`): `dischargeNote`,
`finishOverrideReason`; `POST /api/emergency/cases/{id}/orders`, `…/discharge-note`; gate in the
emergency `FinishTreatmentHandler`. Explicit bean names per the mirror-module convention
(`@Service("emergencyOrderWorkupHandler")` etc.).

### 5.4 RESULTS_PENDING error contract
Introduce a dedicated `ResultsPendingException` in `platform` (extends `ConflictException`) that the
global exception handler maps to **409 Conflict** with body `{ code: "RESULTS_PENDING", message }` —
the same `{code, message}` shape as every other error. The `message` enumerates the open orders, and
the UI shows that message while re-rendering the live order list from `GET …/orders`; there is no
separate `pendingOrders[]` array. Both finish handlers throw it.

## 6. State interactions
- Bed-stay visit stays `IN_PROGRESS` across ordering and result return (never `AWAITING_RESULTS`).
- Child visit: `CREATED → AWAITING_PAYMENT/IN_PROGRESS → … → COMPLETED` via the department flow
  (or `CANCELLED`). Pending = not terminal-complete.
- `finish-treatment` still drives `UNDER_CARE/UNDER_TREATMENT → TREATMENT_FINISHED →
  AWAITING_DISCHARGE_PAYMENT` and the FINAL discharge payment exactly as today; the gate only adds
  a pre-check + optional override-reason capture.
- Doctor-appointment flow (parent pauses to `AWAITING_RESULTS`, single order, resumes on return) is
  unchanged — guaranteed by a regression test.

## 7. Backend slices / endpoints
- `POST /api/premature/admissions/{id}/orders` — create Lab/Radiology/ECO order (non-pausing).
- `POST /api/premature/admissions/{id}/discharge-note` — set/update free-text note.
- `POST /api/premature/admissions/{id}/finish-treatment` — now accepts `{ override, overrideReason }`.
- `POST /api/emergency/cases/{id}/orders`, `…/discharge-note`, `…/finish-treatment` — mirror.
- Order list surfaced via the existing case/admission GET (child visits by parentVisitId).
- Reused unchanged: department opencase/uploadfindings/finalize, cashier, return.

## 8. Migration
`V022__bed_stay_orders_discharge.sql`: `ALTER TABLE prem_admission ADD COLUMN discharge_note text,
ADD COLUMN finish_override_reason text;` and the same for `emerg_case`. Next free global version
after V021.

## 9. Frontend (`features/premature` + `features/emergency` bed-detail drawer)
- **Orders section** (visible when `UNDER_CARE`/`UNDER_TREATMENT`): three buttons **Send to Lab /
  Radiology / ECO** (testids `order-LABORATORY|RADIOLOGY|ECO`) → `POST …/orders`; a live list of
  the case's orders with dept + status (testid `order-list`).
- **Discharge note**: textarea (`discharge-note-input`) + Save (`discharge-note-save`) → `POST
  …/discharge-note`; shows the saved note.
- **Finish**: `detail-finish` calls finish; on `409 RESULTS_PENDING`, open a confirm dialog
  (`results-pending-dialog`) listing the open orders + a reason field (`override-reason`) and a
  **Finish anyway** button (`finish-override`) that re-calls with `override=true`.
- react-query mutations + toasts; full en/ar i18n (`*.orders`, `*.dischargeNote`,
  `*.resultsPending`, `*.finishAnyway`); RTL-aware.

## 10. Roles
Order/discharge-note/finish: `PREMATURE_STAFF`/`EMERGENCY_STAFF` + `DOCTOR`/`NURSE` + `ADMIN`
(mirrors current finish/extend authorization). Department processing & cashier roles unchanged.

## 11. Testing (production-ready)
- **Domain unit:** `Visit.receiveResultsFromChild` tolerant branch (paused vs non-paused parent);
  `PrematureAdmission`/`EmergencyCase` `recordFinishOverride`, discharge-note setters.
- **Integration (Testcontainers / Failsafe):**
  - order from premature/emergency creates a `FORWARDED` child; **parent stays `IN_PROGRESS`**;
  - finish while a child is open → `409 RESULTS_PENDING` with the open-order list;
  - finish with `override=true` + reason → succeeds, reason persisted; blank reason rejected;
  - child finalize returns results (tolerant) without throwing and without changing parent status;
  - finish after all orders `COMPLETED` → succeeds with no override;
  - discharge note persists and round-trips;
  - **regression:** doctor-appointment → lab forward still pauses/resumes (parent
    `AWAITING_RESULTS` → `IN_PROGRESS`) and finalizes correctly.
  - authz: wrong role → 403 on order/discharge-note.
- **E2E (Playwright):** bed-stay (premature + emergency) → order to Lab via drawer → Finish warns
  (results-pending dialog) → Finish anyway with reason → discharge note saved; existing lifecycle
  specs stay green.
- `mvn -pl premature,emergency,app -am verify` green; `tsc -b` + `npm run build` clean; full
  Playwright suite green.

## 12. Open assumptions
- Orders are single-department child visits using the existing department flow; multi-service
  selection happens in the department's own opencase screen (unchanged), not at order time.
- "Pending" is defined purely by child-visit terminal status; a `CANCELLED` child does not block.
- Discharge note is free-text on the case (not a separate signed document); editable while the case
  is open.
- Bed-stay visit deliberately never enters `AWAITING_RESULTS`; the visit-status graph is not
  extended.
