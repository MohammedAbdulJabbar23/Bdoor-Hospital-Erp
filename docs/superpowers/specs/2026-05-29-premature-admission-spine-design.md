# Premature Admission Spine — Design Spec

- **Document ID:** internal design for HMS-BRD-REC-005 (Premature Unit Visit), sub-project A
- **Date:** 2026-05-29
- **Status:** Approved (architecture: Approach 1)
- **Module / process:** Premature Department — admission spine (Reception → bed/period-of-stay → two-stage payment → discharge)

## 1. Context & problem

A 16-agent alignment audit of the codebase against `Reception - Premature - BRD V1.docx`
(HMS-BRD-REC-005) found the Premature workflow is essentially unbuilt: of 58 requirements,
4 Implemented, 22 Partial, 30 Missing, 2 Divergent. Key facts:

- No UI can start a `PREMATURE` visit (the only start-visit widget hardcodes
  `['LABORATORY','RADIOLOGY','ECO']` at `PatientProfilePage.tsx:200`).
- The `/departments/premature` route renders a `ComingSoonPage` stub (`App.tsx:66`,
  `routes.ts:64`).
- No bed / period-of-stay table exists in any migration; no premature case aggregate.
- The cashier engine exists generically but no premature flow drives it.
- `VisitStatus.TREATMENT_FINISHED` and `AWAITING_FINAL_PAYMENT` are defined but never used.
- Divergence (P12b): a rejected final payment becomes terminal `REJECTED` + visit
  `OUTSTANDING_BALANCE`, instead of the BRD's "payment Pending, case stays open".

The full workflow is decomposed into sub-projects A–E (see §11). **This spec covers
sub-project A: the admission spine** — the backbone every later slice hangs off.

## 2. Goals (scope IN)

| BRD ref | Requirement |
|---|---|
| R1 | `PREMATURE` selectable as a visit type in reception |
| R2 | New vs Returning classification persisted as `VisitOrigin` (fix hardcoded value) |
| R3A/R3B | New infant → existing infant GDF; Returning → load record (reuse existing features) |
| R4 | Routing to a real Premature department workspace + incoming queue; unique visit ID |
| P1 / 6.3 | Bed assignment + initial period of stay; bed status set `PENDING_PAYMENT` |
| P2–P4 | Initial admission payment → approved: bed `OCCUPIED`, case `UNDER_CARE`, visit `IN_PROGRESS`; rejected: bed released, admission/visit cancelled |
| P8 / 6.3 | Doctor/Nurse extend period of stay any time; expiry surfaced (computed-on-read) |
| P9 | Mark case `TREATMENT_FINISHED` (the transition itself; pending-results gate deferred to C) |
| P10–P12a | Final discharge payment → approved: case `CLOSED`, bed `DISCHARGED`, closure timestamp |
| P12b | **Divergence fix:** rejected final payment → case stays open, final payment re-issuable |
| 6.3 | Admin-managed bed inventory + bed dashboard with statuses |
| §7 | en/ar i18n for all new screens |

## 3. Non-goals (scope OUT — later sub-projects)

- Premature Form (neonatal admission data) + Morning/Night tour grids → **Sub-project B**
- Pending lab/radiology **results gate** on Finish Treatment; drug/lab/radiology orders
  "Forwarded from Premature"; results-return notifications → **Sub-project C**
- Nursing procedures, treatment chart, routine check-up, statistics upload,
  medical-history sheet → **Sub-project D**
- Prior-state audit-history table; global status-label i18n; **role split** into
  Premature Doctor / Premature Cashier → **Sub-project E**
- Patient Case Form (P6) — deferred per BRD §8.1 (clinical team to define)
- Scheduler/cron for expiry (decision: computed-on-read + existing polling bell)

## 4. Architecture decision

**Approach 1 (approved): a new `premature` bounded-context Maven module.**

The premature workflow is a *bed-stay episode of care*, semantically distinct from the
LAB/RAD/ECO *study-order* case in `department-services`. The new module owns `Bed` and
`PrematureAdmission` aggregates and its own Flyway migrations, follows the established
vertical-slice pattern (reference: `patient-registry`, `department-services`), and
integrates with existing modules across well-defined seams.

The generic `Visit` (in `visit-management`) remains the **reception entry-point and queue
token**; `PrematureAdmission` is the **source of truth for the bed-stay case** and
references the visit by id. The `Visit` is not extended with premature-specific fields.

Rejected alternatives:
- **Extend `Visit`** with bed/stay fields — pollutes a shared cross-department aggregate;
  DDD/ArchUnit smell; hard to grow with forms.
- **Reuse `DepartmentCase`** (department-services) — its NEW→AWAITING_STUDY→FINDINGS_COMPLETE
  →RETURNED lifecycle is wrong for a bed-stay admission.

## 5. Domain model (`premature` module)

### `Bed` (aggregate root)
`id`, `code` (unique, e.g. `PREM-01`), `room` / label, `active` (bool), `status`
∈ {`AVAILABLE`, `PENDING_PAYMENT`, `OCCUPIED`}, audit fields (from platform `BaseEntity`),
`@Version` optimistic lock (consistent with V017). → table `prem_bed`.

### `PrematureAdmission` (aggregate root)
`id`, `visitId` (unique FK-by-id), `patientId`, `bedId`, `status` (see §6),
`stayValue` (int) + `stayUnit` ∈ {`HOURS`, `DAYS`}, `admittedAt`, `stayExpiresAt` (computed
from admittedAt/extensions), `treatmentFinishedAt`, `closedAt`, `initialPaymentId`,
`finalPaymentId`, audit + `@Version`. → table `prem_admission`.

### Persistence
- Migrations: `V019__premature_init.sql` (next free global version; max is currently V018).
- Bed starter set seeded idempotently via the existing `DevDataSeeder` pattern; admin CRUD
  manages the rest.
- `prem_admission.visit_id` unique; both tables carry the standard audit columns.

## 6. State machines

### Bed
```
AVAILABLE --assign--> PENDING_PAYMENT --initial approved--> OCCUPIED --discharge--> AVAILABLE
PENDING_PAYMENT --initial rejected--> AVAILABLE
```
The BRD's "Discharged" bed status is represented by the discharge *action* freeing the bed
back to `AVAILABLE`; the durable discharge record is the admission's `CLOSED` status +
`closedAt` timestamp. The stored bed status enum is therefore the three values in §5.

### PrematureAdmission
```
AWAITING_ADMISSION_PAYMENT --initial approved--> UNDER_CARE
AWAITING_ADMISSION_PAYMENT --initial rejected--> CANCELLED
UNDER_CARE --finish treatment--> TREATMENT_FINISHED
TREATMENT_FINISHED --final payment generated--> AWAITING_DISCHARGE_PAYMENT
AWAITING_DISCHARGE_PAYMENT --final approved--> CLOSED
AWAITING_DISCHARGE_PAYMENT --final rejected--> AWAITING_DISCHARGE_PAYMENT (stays open; retry)
```
BRD headline statuses Under Care / Treatment Finished / Closed are the milestones; the
`AWAITING_*` states model the payment waits.

### Visit (reused, finally exercising dormant states)
```
CREATED -> AWAITING_PAYMENT -> IN_PROGRESS -> TREATMENT_FINISHED
        -> AWAITING_FINAL_PAYMENT -> COMPLETED   (or CANCELLED)
```

### P12b divergence fix
For a **premature FINAL** payment rejection, the admission stays open at
`AWAITING_DISCHARGE_PAYMENT` and a **new** final payment can be re-issued (retry), rather
than the generic terminal `REJECTED` + visit `OUTSTANDING_BALANCE`. This realizes BRD
"case remains open; payment Pending" (lines 78, 257, 484). **Chosen mechanism:** the generic
visit-bridge's FINAL-reject to `OUTSTANDING_BALANCE` is guarded so it does **not** apply to
`PREMATURE` visits; the premature bridge instead keeps the visit at `AWAITING_FINAL_PAYMENT`
and the admission at `AWAITING_DISCHARGE_PAYMENT`, so a new FINAL payment can be re-issued.
Generic (non-premature) cashier behavior is left unchanged.

## 7. End-to-end workflow

1. **Reception (R1–R4).** "Start Premature visit" on a patient profile creates a
   `PREMATURE` Visit with the correct `VisitOrigin` (New → `DIRECT_NEW`, Returning →
   `DIRECT_RETURNING`). New infants first complete the existing infant GDF. Visit lands in
   the Premature incoming queue.
2. **Assign bed (P1).** Premature staff pick an incoming patient + an `AVAILABLE` bed +
   period of stay → creates `PrematureAdmission` (`AWAITING_ADMISSION_PAYMENT`), bed →
   `PENDING_PAYMENT`, generates an INITIAL payment, visit → `AWAITING_PAYMENT`.
3. **Initial payment (P3–P4).** Cashier approves (existing cashier queue) → admission
   `UNDER_CARE`, bed `OCCUPIED`, visit `IN_PROGRESS`. Reject → bed freed, admission
   `CANCELLED`, visit `CANCELLED`.
4. **Extend stay (P8).** Doctor/Nurse extend → new `stayExpiresAt`. Dashboard + existing
   ~20s polling bell surface "expiring soon" (computed on read; no scheduler).
5. **Finish treatment (P9).** Premature staff mark finished → admission `TREATMENT_FINISHED`,
   visit `TREATMENT_FINISHED`, generates a FINAL payment, visit `AWAITING_FINAL_PAYMENT`.
   (No pending-results gate yet — Slice C.)
6. **Discharge (P11–P12).** Cashier approves FINAL → admission `CLOSED` (closure timestamp),
   bed `DISCHARGED` → freed to `AVAILABLE`, visit `COMPLETED`. Reject → retry path (§6 P12b).

## 8. API surface (new, `premature` module)

- `POST /api/premature/admissions` — assign bed + period of stay
- `POST /api/premature/admissions/{id}/extend-stay` — extend (doctor/nurse)
- `POST /api/premature/admissions/{id}/finish-treatment` — mark treatment finished
- `GET  /api/premature/admissions?status=…` — incoming queue + active admissions
- `GET  /api/premature/beds` — bed dashboard (occupant + computed expiry/remaining)
- `GET/POST/PUT /api/premature/beds…` — admin bed inventory CRUD (ADMIN)

Reuses existing endpoints for visit creation and for payment create/approve/reject.
Controllers return `ApiError` via the central `GlobalExceptionHandler`.

## 9. Integration / bridges

- New `PrematurePaymentBridge` in the `app` composition root, listening to
  `PaymentApprovedEvent` / `PaymentRejectedEvent`, filtered to premature admissions,
  driving admission + bed state. Mirrors the existing `PaymentVisitBridge` /
  `PaymentToCaseBridge` pattern.
- Payment creation reuses the same seam `department-services`' `OpenCaseHandler` uses to
  create INITIAL payments (confirm exact port/handler in the plan).
- ArchUnit module-boundary rules extended to permit the new `premature` module.

## 10. Roles & authorization (Slice A)

Keep current roles; role split deferred to Slice E.
- `RECEPTIONIST` — start premature visit.
- `PREMATURE_STAFF` — assign bed, extend stay, finish treatment (the seeded `premature`
  user also has `DOCTOR`).
- `NURSE` — extend stay.
- `CASHIER` / `ADMIN` — approve/reject payments (existing generic cashier).
- `ADMIN` — bed inventory CRUD.

## 11. i18n

All new screens fully keyed in `en.ts` + `ar.ts` (RTL), including bed statuses, admission
statuses, period-of-stay units, and action labels. Global status-label i18n across the rest
of the app stays in Slice E.

## 12. Testing strategy (production-ready)

- **Foundation:** add the **Testcontainers-Postgres** test profile the repo already
  anticipates (`HmsApplicationTests` is `@Disabled("Enable once Testcontainers Postgres
  profile is added")`); enables `@SpringBootTest` integration tests and un-disables the
  context-load test. No Testcontainers/H2 present today.
- **Backend unit (no DB):** `Bed` and `PrematureAdmission` state-machine transitions incl.
  illegal transitions; stay-expiry computation (hours/days, extensions); handlers with
  mocked ports; the P12b retry rule.
- **Backend integration (`@SpringBootTest` + Testcontainers, real Flyway schema):**
  admit → initial approve/reject → extend → finish → final approve/reject(retry) →
  discharge; bed status transitions; role-based authorization (200/403).
- **E2E (Playwright):** `e2e/brd-rec-005-premature.spec.ts` — full reception→discharge
  happy path + initial-reject and final-reject(retry) branches, using existing
  `helpers/{auth,api,seeds}` and the seeded `premature` / `cashier` / `receptionist`
  users; plus a bed-admin spec. (No new frontend unit runner — decision: Playwright E2E +
  backend unit/integration only.)
- **ArchUnit:** module-boundary test green for the new module.

Definition of done for Slice A: all of the above pass; `mvn` build + `npm run build` clean;
the workflow is exercisable end-to-end through the UI.

## 13. Open assumptions

- Bed "Discharged" is modeled as a transient discharge event that frees the bed back to
  `AVAILABLE` for reuse; the dashboard shows live bed status while the admission carries the
  `CLOSED` + closure timestamp. (Flagged for confirmation.)
- Exact payment-creation seam and the precise mechanism for the P12b override are to be
  pinned down during planning against the real `OpenCaseHandler` / `PaymentVisitBridge` code.
- Migration version `V019` assumes no other branch lands a `V019` first; reconcile at merge.
