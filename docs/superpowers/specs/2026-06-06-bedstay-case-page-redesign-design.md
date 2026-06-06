# Bed-Stay Case Page Redesign — Design Spec

- **Document ID:** internal design — HMS-BRD-REC-004 (Emergency) / REC-005 (Premature), clinical UX
- **Date:** 2026-06-06
- **Status:** Approved (design)
- **Modules:** `premature`, `emergency`, `visit-management`, `department-services` (backend); `features/{premature,emergency,beds,departments}` (frontend)

## 1. Context & problem
Today, clicking an occupied bed opens a cramped right-side **drawer** (`BedDetailPanel`, duplicated ~95%
between premature and emergency). Premature also has a separate, hard-to-find case page
(`/premature/admissions/:id`, Form/Tours/Overview); emergency has **no case page at all**. The client
finds this unclear and wants a proper, tabbed **case page** modeled on their reference app's
`EncounterPage` (`client/src/features/clinical/EncounterPage.tsx`): a header + patient banner + quick-action
bar + **tabs** (`Vitals/Notes/Diagnoses/Prescriptions/Lab Orders/Imaging Orders/Billing/Discharge`), where
each Lab/Imaging order is a **card with a status tag + details** the doctor can open and read.

Three concrete requirements:
1. **Forward note** — when a bed-stay patient is sent to another department (Lab/Radiology/ECO), capture a
   free-text note that travels with the order and is visible on both sides.
2. **Redesign the case page into tabs**, reached by clicking a bed, showing the full patient case +
   a **timeline** + doctor-viewable **Lab/Radiology/ECO** tabs.
3. **Emergency must not have "extend stay"** (kept for premature only).

## 2. Decisions (agreed)
- Clicking an occupied bed **navigates to the full case page** (the drawer is removed for both depts).
- Case page tabs (mirrors the client, adapted to bed-stay): **Overview · Laboratory · Radiology · ECO ·
  Clinical · Billing · Timeline**. Active tab is synced to the URL (`?tab=laboratory`).
- The case page is **one shared component** parameterized per department (no re-duplication of the new UI);
  premature supplies extra Clinical content (Form + Tours), emergency supplies service details.
- Investigations are **separate Lab / Radiology / ECO tabs** (mirrors the client's separate Lab/Imaging tabs).
- Forward note is **optional** free text (helps the receiving dept understand *why*); never blocks ordering.

## 3. Non-goals
- Structured lab tests/result rows (our results are a free-text `resultsSummary` from the department flow —
  shown as-is). No diagnoses/prescriptions tabs (not part of bed-stay).
- A fully-audited event log with exact payment-approval timestamps. The timeline is **derived** from the
  case aggregate + child visits (see §5.5); precise payment-approval times are out of scope.
- Refactoring the bed **dashboard/workspace** beyond changing the bed-click to navigate (and deleting the
  drawer). Bed admin pages unchanged.
- Touching `features/clinical/*` (doctor OPD WIP) — out of scope.

## 4. Backend changes

### 4.1 Forward/referral note (visit-management)
- `ForwardVisitCommand` (visit-management): add nullable `String note`.
- `Visit`: add nullable field `referralNote` (the note captured when this visit was created as a forward).
  Set in `Visit.createForwarded(...)`. New column via migration **V025** (`ALTER TABLE visit ADD COLUMN
  referral_note text`).
- `ForwardVisitHandler.handle(parentVisitId, cmd, pauseParent)`: pass `cmd.note()` into `createForwarded`.
  Existing 2-arg overload unchanged.
- `VisitResponse`: expose `referralNote` (so any visit view shows it).

### 4.2 Order-workup note (premature + emergency)
- `OrderWorkupCommand` (both modules): add nullable `String note` alongside `targetType`.
- `OrderWorkupHandler` (both): pass note into `new ForwardVisitCommand(targetType, note)`.
- `OrderResponse` (both modules): add `note` (the referral note) and `resultsAt`
  (`resultsLastUpdatedAt` of the child visit, for the timeline). Mirror in both `OrderResponse` copies.

### 4.3 Receiving department sees the note (department-services)
- When the receiving department opens the forwarded child case, the originating note must be visible.
  The child `Visit` already carries `referralNote`; surface it on the department case view: add
  `referralNote` to `DepartmentCaseResponse` (read from the linked visit at open/load time). The
  department workspace renders it as a "Referred with note:" line on the case.

### 4.4 Remove "extend stay" from Emergency only
- Delete `emergency/extendstay/` (Command, Handler, Controller) and the emergency extend-stay endpoint.
- Keep `EmergencyCase.extendStay(...)` domain method **only if** referenced elsewhere; otherwise remove it
  and its `EmergencyCaseTest` cases. **Keep `StayUnit`** (admit still uses it).
- Remove/adjust any IT that exercises emergency extend-stay.
- Premature extend-stay is **unchanged**.

## 5. Frontend changes

### 5.1 Shared case page (`features/beds/`)
New reusable pieces (Tailwind + existing `shared/ui` components — NOT antd; we replicate the *structure*):
- `BedStayCasePage.tsx` — the shell: **header** (back to beds + patient name/MRN/visit + **status badge**),
  **patient/stay banner** (key-value: bed, service [emergency], stay, admitted, expires, treatment-finished),
  a **quick-action bar** (Order work-up · Discharge note · Finish treatment; premature also **Extend stay**),
  and a **Tabs** strip with count badges and `?tab=` URL sync.
- `CaseTabs.tsx` — tab bar (`Overview · Laboratory · Radiology · ECO · Clinical · Billing · Timeline`).
- `OrdersTab.tsx` — given a `targetType`, lists that department's order cards: visit display id, **status
  badge**, sent-at, the **note**, and the **results summary** when returned; plus a "Send to <dept>" button
  opening `OrderDialog`. Empty state: a calm "no orders yet".
- `OrderDialog.tsx` — pick is fixed by tab; a **note textarea** + Send → `orderWorkup(id, targetType, note)`.
- `BillingTab.tsx` — initial & discharge payment ids + state, reissue action when applicable.
- `CaseTimeline.tsx` — see §5.5.
- A small per-department **adapter** object supplies: data hooks (`useCase`, `useOrders`), the i18n
  namespace, the route base, whether `extendStay` is shown, and the **Clinical tab content**.

### 5.2 Premature wiring
- Redesign `/premature/admissions/:id` to render `BedStayCasePage` with the premature adapter; the existing
  **Form + Tours** become the **Clinical** tab content (reuse the current PrematureCasePage form/tours code).
- Premature adapter: `extendStay: true`.

### 5.3 Emergency wiring
- New route `/emergency/cases/:id` rendering `BedStayCasePage` with the emergency adapter.
- Emergency Clinical tab: service name/code + discharge-context notes (no Form/Tours — none exist).
- Emergency adapter: `extendStay: false`; remove the `extendStay` api fn + i18n.

### 5.4 Bed click → navigate; remove drawers
- `PrematureWorkspacePage` / `EmergencyWorkspacePage`: clicking an occupied bed `navigate(...)` to the case
  page instead of opening the drawer. Delete `BedDetailPanel.tsx` in both features; move their actions
  (orders, discharge note, finish, reissue, premature-extend) into the case page's quick-action bar / tabs.

### 5.5 Timeline (client-derived, honest)
Assembled in `CaseTimeline` from the case + orders responses, newest-or-oldest-first, each row = icon +
label + timestamp + detail:
- **Admitted** — `admittedAt`.
- **Order sent → <DEPT>** — `order.startedAt`, shows the **note**; **Results returned** — `order.resultsAt`
  with the results summary (one or two rows per order).
- **Treatment finished** — `treatmentFinishedAt` (+ `finishOverrideReason` if present).
- **Discharged / closed** — `closedAt`.
- Current `status` is the "now" marker. Payment phases are shown as steps tied to status (no exact approval
  time — explicitly out of scope, §3).

### 5.6 i18n
All new strings en + ar (RTL-safe): tab labels, quick actions, order note, timeline labels, empty states,
"referred with note". Reuse existing `premature.*` / `emergency.*` keys where present.

## 6. Migration
`V025__visit_referral_note.sql`: `ALTER TABLE visit ADD COLUMN referral_note text;` (next global version
after V024). No other schema change (extend-stay removal needs no DDL; the emergency column stays unused).

## 7. Testing (production-grade)
- **Backend unit:** `ForwardVisitHandler` stores the note on the child; `Visit.createForwarded` carries it;
  emergency extend-stay endpoint/handler gone (compile + context loads).
- **Backend IT (Failsafe):**
  - order from premature/emergency with a note → child visit has `referralNote`; `GET .../orders` returns it.
  - receiving department case (`/dept-cases/by-visit/{childId}`) exposes the note.
  - emergency `POST /api/emergency/cases/{id}/extend-stay` → **404/405** (endpoint removed); premature
    extend-stay still works.
  - existing premature/emergency lifecycle ITs stay green.
- **Frontend:** `tsc -b` + `vite build` clean.
- **E2E (Playwright):** click a premature bed → case page opens; Laboratory tab → Send to Lab with a note →
  order card shows status + note; Timeline shows "Admitted" + "Order sent → LABORATORY". Same skeleton for
  emergency, asserting **no Extend-stay control**. Existing bed-stay specs updated for the navigate-to-page
  behavior (drawer removed).
- Gate: `mvn -pl premature,emergency,department-services,visit-management,app -am verify` green; full
  Playwright suite green.

## 8. Open assumptions
- Results are the existing free-text `resultsSummary`; no structured test rows.
- The note is captured at order time and is editable only by re-ordering (no separate edit endpoint).
- The timeline is derived from current aggregate + child-visit timestamps; a fully-audited event feed is a
  later, separate effort.
- Shared case-page component lives in `features/beds/`; premature/emergency provide thin adapters.
