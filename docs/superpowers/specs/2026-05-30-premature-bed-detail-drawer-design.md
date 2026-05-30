# Premature Bed Detail Drawer → Patient History — Design Spec

- **Date:** 2026-05-30
- **Status:** Approved
- **Scope:** Frontend only (`frontend/src/features/premature/*`, i18n, E2E). No backend changes — the workspace already loads full `Admission` objects (incl. `patientId`).

## Goal
Clicking an occupied bed in the Premature workspace opens a right-side drawer showing the
patient + admission details; clicking the patient navigates to their full history
(`/patients/:id`). Bed cards are decluttered (actions move into the drawer). Polished UX.

## Components
- **`BedDetailPanel.tsx` (new):** right-side slide-in drawer. Props: `bed: Bed`,
  `admission: Admission | null`, `onClose`, `onExtend`, `onFinish`, `onReissue`,
  `pending: boolean`, `t`, `dir: 'ltr'|'rtl'`. Renders:
  - Header: bed code + admission-status badge + close (✕).
  - Patient row (button → `navigate('/patients/'+admission.patientId)`): Baby icon, name, MRN ·
    visit id, chevron; plus an explicit "View full history →" link.
  - Admission details (`<dl>`): status, bed code · room, period of stay (value + unit),
    admitted-at, stay-expires-at (red + "expiring soon" when < 2h), treatment-finished-at (if set).
  - Contextual actions footer by `admission.status`:
    - `UNDER_CARE` → Extend control (number + Days/Hours, default 1 day) + Finish treatment.
    - `AWAITING_DISCHARGE_PAYMENT` → note + Re-issue discharge payment.
    - `AWAITING_ADMISSION_PAYMENT` / `TREATMENT_FINISHED` → informational note.
- **`PrematureWorkspacePage.tsx` (edit):** bed cards become clean & clickable (occupied/pending
  only): code, status badge, patient name + MRN, "expiring soon" dot, chevron. Inline
  Extend/Finish/Re-issue buttons removed (now in the drawer). New state
  `selectedAdmissionId`; derive `selectedAdmission` from the loaded `admissions` and
  `selectedBed` from `beds` (by `occupant.admissionId`). Auto-close when the selected
  admission leaves the active set (e.g. discharged). Pass `dir = i18n.dir()`.

## Data flow
No new fetches. `selectedAdmission = admissions.find(a => a.id === selectedAdmissionId)`;
`selectedBed = beds.find(b => b.occupant?.admissionId === selectedAdmissionId)`. Actions reuse
the existing `extendMut` / `finishMut` / `reissueMut` mutations (which invalidate queries); the
drawer re-derives from refreshed data. Navigation via `useNavigate()`.

## UX / a11y
Slide-in transition + backdrop scrim (click to close), **Esc to close**, `role="dialog"` +
`aria-modal` + label, patient row is a real `<button>` (keyboard-activatable), RTL-aware
(slides from the logical side; chevrons mirror). Status colors consistent with cards. Full
en/ar i18n: new keys `premature.admissionStatus.*` (6 statuses) and `premature.detail.*`.

## Edge cases
- Discharged while open → admission gone from active list → drawer auto-closes.
- Available bed → not clickable.
- `admission` momentarily null during refetch → drawer shows a "no longer occupied" note.

## Testing
- New Playwright `e2e/premature-detail.spec.ts`: seed (create bed, register patient, PREMATURE
  visit, admit, approve INITIAL → UNDER_CARE) → login premature → click the bed → assert drawer
  shows patient name → click patient → assert URL `/patients/<id>` and history page loads.
- `npx tsc -b` + `npm run build` clean; existing specs unaffected; live screenshot check.

## Out of scope
Backend changes; bed-card redesign beyond declutter; any change to the history page itself.
