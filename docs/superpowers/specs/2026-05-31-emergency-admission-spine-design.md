# Emergency Admission Spine — Design Spec (Sub-project EA)

- **Document ID:** internal design for HMS-BRD-REC-004 (Emergency Visit), sub-project EA
- **Date:** 2026-05-31
- **Status:** Approved
- **Module / process:** Emergency Department — admission spine (Reception → bed + service + period of stay → two-stage payment → discharge)

## 1. Context & decomposition
The Emergency module (REC-004) is the same **bed-stay episode of care** as Premature (REC-005),
plus an emergency **service-type selection** at admission. The BRD calls the Emergency case
"distinct from all other visit types," and the codebase organizes one module per bounded
context, so this is a **new `emergency` module mirroring `premature`** (the proven template).

Already in place to build on: `VisitType.EMERGENCY`, `Role.EMERGENCY_STAFF`, the seeded
`emergency` user (EMERGENCY_STAFF+DOCTOR), the **40 EMERGENCY catalogue items** (seeded in
V004, incl. forward-to pointers for Echo/Lab/Sonar/X-Ray), the `VisitStatus` bed-stay graph
(`AWAITING_PAYMENT→IN_PROGRESS→{AWAITING_RESULTS,TREATMENT_FINISHED}→AWAITING_FINAL_PAYMENT→
COMPLETED/OUTSTANDING_BALANCE`), the cashier engine, and the generic `PaymentVisitBridge`.

Emergency decomposes into **EA (this spec — admission spine)**, **EB (clinical forms:
medical history, nursing procedures, treatment chart, statistics), EC (drug/lab/radiology
orders + Finish-Treatment results gate + notifications)**.

## 2. Goals (EA scope)
| BRD ref | Requirement |
|---|---|
| R1–R4 / 6.1 | `EMERGENCY` selectable in reception; New/Returning; route to Emergency incoming queue + unique visit id (reuse existing infra) |
| E1 / 6.4 | Assign available bed + initial period of stay; bed → `PENDING_PAYMENT` |
| E2 / 6.5 / 6.6 | Select **1 of 40 emergency service types** (configured fee); fee recorded on the case |
| E3–E4 | Route to cashier; initial payment **bills the selected service**; cashier screen shows patient/MRN/visit/service/amount |
| E5a/E5b | Approved → bed `OCCUPIED`, case `UNDER_TREATMENT`, visit `IN_PROGRESS`; rejected → release bed, cancel visit/case |
| E8 | Mark `TREATMENT_FINISHED` (the transition; **pending-results gate is EC**) |
| E9–E11a | Final discharge payment → case `CLOSED`, bed `DISCHARGED`, closure timestamp |
| E11b | **P12b-equivalent:** rejected final payment → case stays open & re-issuable |
| 6.3 | Bed dashboard: tiles show number, status (Available/Pending Payment/Occupied/Discharged), patient; click occupied bed → patient + case |
| §7 | Period of stay extendable (doctor/nurse) |
| §7 | en/ar i18n |

## 3. Non-goals (EB/EC)
Clinical forms (medical history, nursing procedures, treatment chart, statistics upload, doctor
notes) → **EB**. Drug/lab/radiology orders "Forwarded from Emergency", results return +
notifications, and the **Finish-Treatment results gate** → **EC**. Role split / global status
i18n / audit-history → cross-cutting later. The full clinical **case page** lands in EB; EA's
bed-detail drawer carries the spine actions inline.

## 4. Architecture — new `emergency` module
Mirrors `premature`: `EmergencyBed` + `EmergencyCase` aggregates, vertical slices, own Flyway
migration (`V021__emergency_init.sql`), `EmergencyPaymentBridge` in the app composition root.
The generic `Visit` remains the reception entry/queue token; `EmergencyCase` is the source of
truth for the bed-stay case, referencing the visit by id. `Visit` is not extended.

### `EmergencyBed` (aggregate; table `emerg_bed`)
`id`, `code` (unique, e.g. `EMRG-01`), `room`, `status` ∈ {`AVAILABLE`,`PENDING_PAYMENT`,
`OCCUPIED`}, `active`, audit + `@Version`. Methods: `create`, `reserve`, `occupy`, `release`,
`discharge`, `updateDetails`, `deactivate` (identical to premature `Bed`).

### `EmergencyCase` (aggregate; table `emerg_case`)
`id`, `visitId` (unique), `patientId`, `patientMrn`, `patientName`, `visitDisplayId` (snapshots),
`bedId`, `bedCode`, **`serviceItemId`, `serviceCode`, `serviceName`** (selected emergency
service, snapshot), `stayValue`+`stayUnit` ∈ {`HOURS`,`DAYS`}, `admittedAt`, `stayExpiresAt`,
`treatmentFinishedAt`, `closedAt`, `initialPaymentId`, `finalPaymentId`, `status`, audit +
`@Version`. `EmergencyCaseStatus` ∈ `AWAITING_INITIAL_PAYMENT → UNDER_TREATMENT →
TREATMENT_FINISHED → AWAITING_DISCHARGE_PAYMENT → CLOSED` (+ `CANCELLED`). The headline
`UNDER_TREATMENT`/`TREATMENT_FINISHED`/`CLOSED`/`CANCELLED` are the BRD §7 terms; the two
`AWAITING_*` are payment-wait sub-states (the BRD models payment status as a separate field).

## 5. Service-type selection (E2 — the Emergency-specific addition)
- `GET /api/emergency/services` → active `EMERGENCY` catalogue items **with `forward_to` = null**
  (the in-emergency billable charges; the forward-to pointers like Lab/X-Ray are EC orders, not
  the admission service). Reuses catalogue `ServiceItemRepository`.
- Admission command carries `serviceItemId`. The admit handler validates it's an active,
  non-forward EMERGENCY item, snapshots its code/name on the case, and creates the **INITIAL**
  payment for that service item (via `CreatePaymentHandler`, the `OpenCaseHandler` seam).

## 6. State machines
**Bed:** `AVAILABLE →(assign)→ PENDING_PAYMENT →(initial approved)→ OCCUPIED →(discharge)→
AVAILABLE`; initial-rejected `PENDING_PAYMENT → AVAILABLE`. (BRD "Discharged" = the discharge
action freeing the bed; durable record is the case `CLOSED` + `closedAt`.)
**Case:** `AWAITING_INITIAL_PAYMENT →(initial approved)→ UNDER_TREATMENT →(finish)→
TREATMENT_FINISHED →(final payment generated)→ AWAITING_DISCHARGE_PAYMENT →(final approved)→
CLOSED`; initial-rejected → `CANCELLED`; final-rejected → stays `AWAITING_DISCHARGE_PAYMENT`
(re-issuable).
**Visit (reused):** `CREATED → AWAITING_PAYMENT → IN_PROGRESS → TREATMENT_FINISHED →
AWAITING_FINAL_PAYMENT → COMPLETED` (or `CANCELLED`).
**P12b guard:** the generic `PaymentVisitBridge` FINAL-reject → `OUTSTANDING_BALANCE` is guarded
to skip `EMERGENCY` (as it already does for `PREMATURE`); the emergency bridge keeps the visit
at `AWAITING_FINAL_PAYMENT` and the case at `AWAITING_DISCHARGE_PAYMENT` so a new FINAL payment
can be re-issued.

## 7. Backend slices / endpoints (`emergency` module, vertical-slice)
- `GET /api/emergency/services` — billable emergency services for selection.
- `POST /api/emergency/cases` — admit: assign bed + service + period of stay (creates case +
  INITIAL payment). `EMERGENCY_STAFF`/`ADMIN`.
- `POST /api/emergency/cases/{id}/extend-stay` — `EMERGENCY_STAFF`/`NURSE`/`DOCTOR`/`ADMIN`.
- `POST /api/emergency/cases/{id}/finish-treatment` — `EMERGENCY_STAFF`/`DOCTOR`/`ADMIN`.
- `POST /api/emergency/cases/{id}/reissue-discharge-payment` — re-issue final (P12b).
- `GET /api/emergency/cases?status=…` — active cases.
- `GET /api/emergency/beds` — bed dashboard (with occupant + computed expiry). `isAuthenticated`.
- `GET/POST/PUT /api/emergency/beds…` — admin bed CRUD (`ADMIN`/`EMERGENCY_STAFF`).
Reuses existing endpoints for visit creation and payment create/approve/reject.

## 8. Integration / bridges
New `EmergencyPaymentBridge` (app composition root), `@TransactionalEventListener` AFTER_COMMIT
+ `REQUIRES_NEW`, filtered to emergency cases by `initialPaymentId`/`finalPaymentId`:
- INITIAL approved → case `UNDER_TREATMENT`, bed `OCCUPIED` (visit→IN_PROGRESS via generic bridge).
- INITIAL rejected → bed released, case `CANCELLED`, visit cancelled.
- FINAL approved → case `CLOSED`, bed discharged (visit→COMPLETED via generic bridge).
- FINAL rejected → no-op (P12b; re-issuable). Generic bridge guarded to skip EMERGENCY.
Payment creation reuses the `CreatePaymentHandler` seam. ArchUnit gets `layeredWithinEmergency`.

## 9. Frontend (`features/emergency`, mirrors premature)
- **`EmergencyWorkspacePage`** at `/departments/emergency` (replaces ComingSoonPage): incoming
  queue (EMERGENCY visits without a case) + **bed dashboard** + admit dialog with **service-type
  dropdown** (from `/api/emergency/services`), period of stay.
- **Bed-detail drawer** (mirror premature): occupied bed → patient + case details (service, stay,
  status) + actions (extend / finish / re-issue); patient name → `/patients/:id` history.
- **Bed admin** page; **`PatientProfilePage`** start-visit options gain `EMERGENCY`.
- react-query, react-hook-form, full en/ar i18n, RTL-aware.

## 10. Roles
`RECEPTIONIST` start visit; `EMERGENCY_STAFF` admit/extend/finish/reissue + bed CRUD; `NURSE`
extend; `DOCTOR` extend/finish; `CASHIER`/`ADMIN` payments; `ADMIN` bed CRUD. (Distinct
Emergency cashier role deferred — generic `CASHIER` as in premature.)

## 11. Testing (production-ready)
- Backend unit: `EmergencyBed` + `EmergencyCase` state machines, stay-expiry, service-required,
  P12b re-issue.
- Backend integration (Testcontainers via Failsafe): admit (with service) → initial
  approve/reject → extend → finish → final approve/reject(re-issue) → discharge; service billed
  correctly; role auth (200/403).
- ArchUnit: `layeredWithinEmergency` green.
- E2E (Playwright): `brd-rec-004-emergency.spec.ts` (reception→service→pay→Occupied/Under-Treatment
  → finish → discharge happy path + initial-reject + final-reject/re-issue) + `emergency-ui.spec.ts`
  (admit a bed from the dashboard with a service type). Existing specs unaffected.
- `mvn -pl emergency,app -am verify` green; `tsc -b` + `npm run build` clean; full Playwright suite green.

## 12. Migration / versioning
`V021__emergency_init.sql`: `emerg_bed` + `emerg_case` (UUID PKs, audit + version, FK
`emerg_case.bed_id → emerg_bed.id`, unique `visit_id`, status/stay-unit CHECKs, indexes), and a
seeded starter set of beds (`EMRG-01…EMRG-08`). Next free global version after V020.

## 13. Open assumptions
- Service selection at admission is a single service item (BRD E2 "select the service type");
  additional per-procedure charges during the stay are EC/orders, not EA.
- Bed "Discharged" modeled as the discharge action freeing the bed to `AVAILABLE`; the durable
  discharge record is `CLOSED` + `closedAt`.
- Reuse the cashier `CreatePaymentHandler` seam exactly as premature/department-services do.
