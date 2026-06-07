# Product Gaps & QA Backlog — Albudoor HMS

> Living document. Each `/loop` QA pass appends findings: things that are missing, untested,
> fragile, or inconsistent. Severity: **H** (high — correctness/security/data-loss or BRD-blocking),
> **M** (medium — important but workaround exists), **L** (low — polish). Status: ☐ open / ☑ done.
>
> Last pass: **2026-06-07** (iteration 8). Stack at the time: backend reactor verify green (116 app
> ITs), Playwright **75/75 re-run green** (stability), all localhost endpoints 200.
>
> **Iteration 8 results (clean — no new gaps):**
> - Full Playwright suite re-run: **75/75** (no drift after hours of uptime + accumulated test data).
> - Input validation solid: duplicate national-id → **409 `DUPLICATE_PATIENT`**, negative/zero
>   `stayValue` → **400 `VALIDATION_FAILED`**, blank `fullName` → 400. No raw 500s on bad input.
>
> **Iteration 7 results:**
> - VIP auto-bypass **solid**: a VIP patient's consult → visit IN_PROGRESS with no cashier; payment
>   APPROVED / `VIP_BYPASS` / `vipBypass=true` (and excluded from cash reconciliation).
> - **Found a real gap → new §11:** booking does NOT validate the doctor's availability. With a
>   day-off set (slot grid correctly shows 0 available), a direct `BOOKED` booking at a former slot
>   on that day **succeeded (201)**. The handler only guards past-slot + double-book, so the slot
>   grid is bypassable (day-off confirmed; likely also off-hours / non-working weekdays).
>
> **Iteration 6 results (clean — no new gaps):** state/booking guards all solid —
> - Appointment booking: past slot → **422 `SCHEDULED_IN_PAST`**, double-book same slot →
>   **409 `SLOT_TAKEN`**, unknown `type` enum → 400 (correctly rejected).
> - Dept-case: re-open after payment → **422 `CASE_LOCKED`**, finalize before findings →
>   **422 `CASE_NOT_COMPLETE`** (stays AWAITING_STUDY). No 5xx, no double-effects.
>
> **Iteration 5 results:**
> - Idempotency **solid**: double-approving the same payment → 200 then **422 `PAYMENT_NOT_PENDING`**
>   (no double-charge, no 5xx).
> - Cross-role authz **solid**: lab→approve-payment 403; receptionist→`PUT /exams` 403.
> - **Found a real workflow gap → new §10:** rejecting a *consult* (doctor-appointment) INITIAL
>   payment leaves the visit **stuck in AWAITING_PAYMENT** with a REJECTED payment and no PENDING one
>   to act on. `PaymentVisitBridge.onRejected` only handles FINAL-stage rejects; INITIAL is a no-op
>   left to "the calling module", and doctor-appointment has no such bridge (bed-stay does).
>
> **Iteration 4 results (clean — no new gaps):**
> - **§6 medication pipeline verified end-to-end.** Finalizing a doctor exam with a prescription
>   auto-creates a pharmacy dispense via `ExamFinalizedToDispenseBridge` (AFTER_COMMIT) →
>   `RX-2026-000168 PENDING`, line "Paracetamol 500mg", linked to the exam/visit and visible at
>   `GET /dispenses/by-patient/{id}` (the doctor Medications tab's source). Pipeline is complete.
>
> **Iteration 3 results:**
> - Added `e2e/bedstay-timeline.spec.ts` (admit → order Lab w/ note → drive lab to COMPLETED →
>   assert Lab tab shows note + result, Timeline shows Admitted + Order sent). **Green.**
> - **Found a real bug → new §9:** the case-page Timeline never shows "Results returned". A completed
>   child order carries `resultsSummary` ("LAB-CBC 18.2 … [HIGH]") and `status=COMPLETED`, but
>   `resultsAt` is **null**, and the timeline gates that milestone on `resultsAt`. The result IS shown
>   on the Lab tab; only the timeline entry is missing.
>
> **Iteration 2 results (all clean — no new gaps):**
> - i18n locale files are in **perfect parity** — en 878 leaf keys, ar 878, zero missing either side.
>   So §1 is NOT missing translations; it's components with hardcoded strings bypassing `t()`.
> - Write-flow probe: walk-in appointment create → 201, cancel → 200 (no error).
> - Error-contract sweep (9 malformed/missing-param requests) → **all 400, zero 5xx** — the §8
>   contract concern is verified clean across appointments/dept-cases/payments/visits/exams/patients/slots.

## What is verified working (baseline)
- Backend: full reactor verify green — premature/emergency lifecycle + orders, dept-case, cashier,
  pharmacy stock, identity (user create/edit/reset), error-contract, authz ITs.
- Frontend: tsc + vite build clean; Playwright 74/74 (auth, dashboard, reception, cashier, departments,
  bed-stay case page, doctor exam flow, role-guards, i18n-rtl smoke).
- Endpoints: no 5xx on the smoke set (patients/visits/payments/dashboard/doctors/dispenses/dept-cases/
  users/premature/emergency).

---

## 1. Internationalization (H/M)
- **H — Clinical exam (doctor) page is not internationalized.** `ClinicalExamPage.tsx` has ~46
  hardcoded English strings and only 3 `t()` calls. The new tabs (Consultation/Vitals/Lab/Radiology/
  Medications/History), quick actions, field labels, and the patient-record sub-tabs are English-only;
  they won't switch to Arabic/RTL. *Fix:* extract to `clinical.*` i18n keys (en + ar).
- **M — Verify no missing i18n keys elsewhere.** Audit `t('…')` calls against `en.ts`/`ar.ts` for
  keys that resolve to the raw key (renders the dotted path). Add a test or script that diffs the two
  locale files for parity.

## 2. Security / production hardening (H)
- **H — No HTTPS/TLS in production.** nginx serves `:80` only; JWT + passwords travel in clear. *Fix:*
  Cloudflare in front (free TLS + edge cache near Iraq) or Certbot.
- **H — Production runs the default Spring profile.** Seeders run (admin/admin etc.), default JWT
  secret active, dev users present. *Fix:* activate a prod profile, set `HMS_JWT_SECRET`, rotate the
  admin password, disable dev seeding.
- **M — No automated DB backups.** Only manual pre-deploy `pg_dump`. *Fix:* nightly `pg_dump` cron +
  off-box copy + a documented restore drill.
- **M — No audit trail for sensitive admin actions** (user role/status/password changes, exam
  reopen, finish-treatment overrides). `finishOverrideReason` is captured but there is no queryable
  audit log. *Fix:* an append-only audit table / events feed.
- **L — No password policy / rotation / MFA; no self-service password reset** (admin-reset only).

## 3. Notifications / real-time (M)
- **M — Results-return notifications are not wired.** `VisitReturnedEvent` is emitted when a forwarded
  child (lab/radiology/ECO) returns results, but no feed consumes it — the ordering doctor / bed-stay
  ward is not alerted; they must poll the case page. *Fix:* a notification feed + the Topbar bell
  consuming the event.

## 4. Test-coverage gaps (M)
- ☑ **Bed-stay Timeline + Lab-detail e2e added** (`bedstay-timeline.spec.ts`, iteration 3): asserts
  the Lab tab shows the order note + result summary and the Timeline shows Admitted + Order sent.
  Still **open:** a doctor-side spec (doctor opens a patient with a completed lab → Lab/Radiology
  tabs show value/unit/range/flag), and a Timeline "Results returned" assertion once §9 is fixed.
- **M — Receiving-department referral note only covered backend-side.** The note shows in the dept
  workspace UI but no e2e asserts it. Add a Playwright check.
- **L — `GET /api/emergency/cases/{id}` (new) has no dedicated IT** (covered indirectly).
- **L — No load/concurrency test** for the optimistic-lock paths (concurrent payment approve, bed
  double-admit) beyond unit-level reasoning.

## 5. Missing BRD features (H/M — per project memory)
- **H — Emergency clinical forms (sub-project EB):** medical history, nursing procedures, treatment
  chart, statistics upload — not built. The new Emergency case page's "Clinical" tab is a placeholder.
- **M — Premature sub-projects C/D/E** remain (per `premature_workflow_status`).
- **M — Statistics/export upload** promised by several BRDs (Lab/Radiology/Premature) is not present.

## 6. Clinical / medications completeness (M)
- ☑ **Medication pipeline verified (iteration 4).** Finalizing an exam with a prescription
  auto-creates a dispense (`ExamFinalizedToDispenseBridge`), linked to the exam/visit, surfaced at
  `GET /dispenses/by-patient/{id}` and the doctor Medications tab. The downstream charge → ready →
  given lifecycle is covered by `PharmacyDispenseSummaryIT` / `PharmacyStock*IT`. (Open follow-up:
  no e2e asserts the dispense appears on the doctor Medications tab — see §4.)
- **L — Bed-stay/Doctor timeline lacks exact payment-approval timestamps** (derived client-side from
  aggregate + child visits; payment events would need a cashier feed). Documented as out of scope.

## 7. Data / reporting (M/L)
- **M — Limited reporting.** Dashboard KPIs are real but there are no financial/occupancy/department
  throughput reports or date-range exports, which a hospital will need operationally.
- **L — Cashier close-of-day** exists; verify reconciliation handles partial/rejected/refunded edges.

## 8. UX / edge cases (L)
- **L — Appointments page lands on "doctor isn't working this day"** when today isn't a working day
  (prior finding); consider auto-jumping to the doctor's next working day.
- ☑ **`MissingServletRequestParameter` now 400** (fixed). Iteration-2 sweep of 9 missing-param /
  malformed requests (appointments, dept-cases, payments, visits, exams, patients, slots) returned
  **all 400, zero 5xx** — error contract verified clean.

## 9. Bed-stay case-page Timeline — "Results returned" never shows (M) ☐
- **M — Confirmed bug (iteration 3).** On the premature/emergency case page, the **Timeline** tab
  shows *Admitted* and *Order sent → DEPT* but never *Results returned*, even after the ordered
  lab/radiology case is COMPLETED. Verified via API: a completed child order returns
  `{status: COMPLETED, resultsSummary: "LAB-CBC 18.2 … [HIGH]", resultsAt: null}`. The timeline
  (`BedStayCasePage.tsx` → `TimelineTab`) gates the milestone on `o.resultsAt`, which is null because
  the child visit's `resultsLastUpdatedAt` is a parent-side field that is never set on the child.
  - **Impact:** low data-loss risk — the result IS visible on the Lab/Radiology tab; only the
    timeline narrative is incomplete.
  - **Fix (small):** (a) backend — add the child's `endedAt` to `OrderResponse` (both modules), and
    (b) frontend — gate the "Results returned" entry on `o.resultsSummary` (the real signal) using
    `o.resultsAt ?? o.endedAt ?? o.startedAt` for the timestamp. Then re-enable the assertion in
    `bedstay-timeline.spec.ts`.

## 10. Rejected consult payment leaves the visit stuck (M) ☐
- **M — Confirmed (iteration 5).** When the cashier **rejects** a doctor-appointment (consult)
  **INITIAL** payment: the payment → `REJECTED` but the visit stays `AWAITING_PAYMENT`, and **no new
  PENDING payment is created** — so the cashier has nothing to approve and the doctor can't see the
  patient. The visit is a dead-end (the rejected payment can't be re-approved → `PAYMENT_NOT_PENDING`).
  - **Root cause:** `app/PaymentVisitBridge.onRejected` returns early for any non-FINAL stage; INITIAL
    rejection is intentionally a no-op "for the calling module to decide". Bed-stay modules
    (premature/emergency) own bridges that cancel the visit + release the bed on initial reject
    (BRD P4b). **Doctor-appointment has no equivalent**, so its consult visits get stuck.
  - **Inconsistency:** bed-stay initial-reject = CANCELLED; consult initial-reject = stuck
    AWAITING_PAYMENT.
  - **Fix (decide intent):** add a doctor-appointment payment bridge that, on INITIAL reject, either
    (a) cancels the visit, or (b) re-issues a fresh PENDING payment for retry. Confirm against the
    Doctor-Appointment BRD which is intended. Add an IT either way.
  - **Verified working (no gap):** bed-stay FINAL reject → re-issuable (P12b, covered by e2e); the
    idempotency + authz guards above.

## 11. Appointment booking ignores doctor availability (day-off / hours) (M) ☐
- **M — Confirmed (iteration 7).** `POST /api/appointments` (type `BOOKED`) validates only that the
  slot is **not in the past** (`SCHEDULED_IN_PAST`) and **not already taken** (`SLOT_TAKEN`). It does
  **not** check the doctor's weekly hours or days-off. Reproduced: added a day-off (the slot grid then
  shows 0 available), but a direct booking at a former slot on that day returned **201**.
  - **Impact:** appointments can be created when the doctor is off / outside working hours via the API
    (the UI hides the slots, but the guard is bypassable; any client error or stale grid books anyway).
  - **Fix:** in `BookAppointmentHandler`, validate `scheduledFor` against the doctor's computed
    availability (working `WeeklyHour` for that weekday, not a `DayOff`, aligned to a slot boundary) —
    reuse `SlotComputationService`. Reject with a clear code (e.g. `SLOT_UNAVAILABLE`). Add an IT.
  - **Verified working (no gap):** VIP auto-bypass; past-slot + double-book guards.

---

### Next passes (todo for the loop)
- Add the e2e specs called out in §4 and run them.
- Run a full `mvn verify` across ALL modules (not just touched ones) to catch cross-module regressions.
- Audit i18n key parity (en vs ar) and the clinical page hardcoded strings.
- Probe write/POST workflows (create appointment, payment approve/reject, dispense) for 5xx/edge errors.
