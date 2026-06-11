# Case-Page Results Detail + Timeline Polish — Design Spec

- **Date:** 2026-06-11
- **Status:** Approved (user: "fix all of them" against the assessment of 2026-06-11)
- **Motivation:** (1) Lab/Rad/ECO results on the bed-stay case page are fragmented: the order
  row shows a one-line summary + raw status code; per-test findings (CaseServiceLine) are not
  surfaced at all; result documents live in a different tab; resultsAt is never rendered.
  (2) The patient timeline double-counts (forwarded child visits and bed-stay anchor visits
  appear as standalone VISIT entries beside their ADMISSION/DOCUMENT entries), inflates the
  visits chip, hides EXAM entries (no pill), spams one FORM entry per treatment-chart day,
  bakes English titles into the backend (Arabic UI shows English), and result-document
  previews 403 for dept staff (legacy route in fileUrl).

## Part A — Expandable results on the case-page order rows

1. **New stay-scoped endpoint** (bed-stay-forms): `GET /api/bed-stays/{dept}/{stayId}/orders/{visitId}/results`
   → `{ services: [{ serviceName, findings }], documents: [StayDocumentDto…] }`.
   Ownership: visitId must be one of the stay's `orders()` (404 otherwise — same walk as
   `requireResultAttachment`); roles = `checkRead`. Implementation: `DepartmentCaseRepository.findByVisitId`
   → its `CaseServiceLine`s (read the entity for exact getters) + that case's attachments as
   result-source `StayDocumentDto`s. Absent dept-case → empty lists (order sent, lab hasn't
   opened it yet).
2. **OrdersTab UI**: each order row becomes expandable (chevron). Expanded: per-service
   findings list, the order's documents with the shared `DocumentPreview` + download, and
   `resultsAt` rendered next to the summary. Collapsed row gains a friendly status pill
   replacing the raw code: `awaitingResults` (no resultsAt) / `resultsReady` (resultsAt
   present) / keep raw code as tooltip. i18n en/ar under `caseView.orderStatus.*`.

## Part B — Timeline correctness & polish

3. **De-duplicate VISIT entries** (`PatientHistoryHandler.buildTimeline`): skip visits with
   `parentVisitId != null` (forwarded children — represented by order/DOCUMENT entries) and
   visits whose `visitType` is PREMATURE or EMERGENCY (anchor visits — represented by
   ADMISSION entries). Legacy `entries`/`totalVisits` untouched.
4. **Visits chip**: count timeline VISIT entries (post-filter), not `totalVisits`.
5. **Exams pill**: add `exams` filter (EXAM type) to `UnifiedTimeline` + locales.
6. **Treatment-chart roll-up**: premature/emergency contributors emit ONE FORM entry per stay
   — "Treatment charts — N day(s)" (`at` = latest chart's createdAt) instead of one per date.
7. **Timeline i18n**: `HistoryEntry` gains `kind` (stable machine code) + `params`
   (`Map<String,String>`). Kinds: `visit`, `examFinalized`, `admissionOpened`,
   `admissionClosed`, `medicalHistorySheet`, `treatmentCharts`, `nursingLog`,
   `documentUploaded`, `resultDocument`. Backend keeps `title`/`detail` as fallback;
   frontend translates known kinds via `patientProfile.timeline.kind.*` (en/ar) with params
   ({{bed}}, {{count}}, {{fileName}}, {{visitType}}…), falling back to `title`.
8. **Role-correct result-document URLs in the timeline**: `StayDirectory` gains
   `Optional<StayRef> stayForOrderVisit(UUID childVisitId)` (`record StayRef(StayDepartment
   department, UUID stayId)`) — impls: child visit → parentVisitId → `findByVisitId` on the
   admission/case repo (both finders exist). `BedStayFormsHistoryContributor` builds result-doc
   fileUrls on the stay-scoped route (`…/documents/results/{id}/file`) when a stay resolves;
   falls back to the legacy dept-cases route otherwise (outpatient lab visits).

## Testing
- IT: order-results endpoint (findings + documents round-trip; foreign-visit 404; empty
  before lab opens); timeline de-dup (child + anchor visits absent; ADMISSION/DOCUMENT
  present); chart roll-up (3 chart days → 1 FORM entry, count=3); kind/params present.
- E2E: extend case-page/profile specs — expand an order row and see findings + document;
  Arabic profile shows translated timeline titles (i18n-rtl pattern); exams pill filters.
- Full suites stay green.
