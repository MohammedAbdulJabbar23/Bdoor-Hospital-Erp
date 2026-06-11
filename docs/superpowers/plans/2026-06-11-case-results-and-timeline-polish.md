# Case-Page Results Detail + Timeline Polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Expandable lab/rad/ECO results (findings + documents) on the bed-stay order rows, and a de-duplicated, localized, fully-filterable patient timeline — per spec `docs/superpowers/specs/2026-06-11-case-results-and-timeline-polish-design.md`.

**Architecture:** One new stay-scoped results endpoint in `bed-stay-forms` (reusing the orders() ownership walk); timeline fixes in `clinical-case` handler + the three contributors; `HistoryEntry` gains `kind`+`params` for frontend i18n; `StayDirectory` gains `stayForOrderVisit` for role-correct document URLs. Frontend: expandable OrdersTab rows, kind-translated timeline, exams pill, corrected visits chip.

**Tech stack & build env:** as the previous two plans (no ./mvnw; `JAVA_HOME=/usr/lib/jvm/java-25-openjdk` inline; Failsafe ITs via `mvn -pl app -am verify -Dit.test=… -Dsurefire.failIfNoSpecifiedTests=false`; `npx tsc -b`; e2e stack: compose db + spring-boot:run + vite).

This plan is CONTRACT-style: every task names its exact files, behavior contract, reference implementations to copy patterns from, tests, and commit message. Implementers read the named references before coding.

---

### Task 1: Stay-scoped order-results endpoint + IT (TDD)

**Files:** Create `backend/bed-stay-forms/src/main/java/com/albudoor/hms/bedstayforms/documents/{OrderResultsController,OrderResultsHandler}.java`, `api/OrderResultsResponse.java`; Test: extend `backend/app/src/test/java/com/albudoor/hms/app/bedstayforms/StayDocumentsIT.java`.

**Contract:** `GET /api/bed-stays/{department}/{stayId}/orders/{visitId}/results` → `OrderResultsResponse(List<ServiceFinding> services, List<StayDocumentDto> documents)` with `record ServiceFinding(String serviceName, String findings)`. Auth: `@PreAuthorize("isAuthenticated()")` + `BedStayAccess.checkRead`. Ownership: `visitId` must be in `stays.directory(dept).orders(stayId)` else `NotFoundException` (mirror `requireResultAttachment` in `StayDocumentsHandler`). Data: `DepartmentCaseRepository.findByVisitId(visitId)` → its service lines (READ `backend/department-services/.../domain/{DepartmentCase,CaseServiceLine}.java` for the collection/getter names — serviceName + findings) + `CaseAttachmentRepository.findAllByCaseIdOrderByUploadedAtAsc` mapped via `StayDocumentDto.fromResultAttachment` (fileUrl on the existing stay-scoped `…/documents/results/{id}/file` route). No dept-case yet → both lists empty, 200.

**IT (write FIRST, watch 404):** in StayDocumentsIT add: (a) full round-trip — reuse `orderLabAndUploadResult`, also PUT findings text via the lab flow if the existing helper doesn't already (READ how `UploadFindingsController` is driven in `backend/app/src/test/.../deptcase/CaseAttachmentFinalizedIT.java` or `PrematureOrdersIT`), then GET the new endpoint as `premature` → services contains the findings text, documents contains result.png; (b) foreign visit (an order belonging to another stay) → 404; (c) order placed but no dept-case opened → 200 with empty lists.

**Verify:** `-Dit.test=StayDocumentsIT` all green (8 tests). **Commit:** `feat(bed-stay-forms): stay-scoped order results (findings + documents) + IT`

---

### Task 2: Timeline correctness — de-dup, roll-up, kind/params, stay-scoped URLs + IT

**Files:** Modify `backend/clinical-case/.../history/HistoryEntry.java` (+`String kind`, `Map<String,String> params` — update ALL constructions), `.../patienthistory/PatientHistoryHandler.java`, `backend/bed-stay-forms/.../directory/StayDirectory.java` (+`record StayRef(StayDepartment department, UUID stayId)` in directory pkg; `Optional<StayRef> stayForOrderVisit(UUID childVisitId)`), both directory impls (child visit → `getParentVisitId()` via VisitRepository → `findByVisitId` on admission/case repo), `BedStayFormsHistoryContributor.java`, `PrematureHistoryContributor.java`, `EmergencyHistoryContributor.java`; Test: extend `backend/app/src/test/.../clinicalcase/FullHistoryIT.java`.

**Contract:**
- Handler skips VISIT entries where `parentVisitId != null` OR visitType is PREMATURE/EMERGENCY. Legacy `entries`/`totalVisits` byte-identical.
- Kinds + params (exact strings): `visit` {visitType, displayId}, `examFinalized` {}, `admissionOpened` {bed}, `admissionClosed` {bed}, `medicalHistorySheet` {}, `treatmentCharts` {count}, `nursingLog` {count}, `documentUploaded` {fileName}, `resultDocument` {fileName, visitType}. Keep existing `title`/`detail` text unchanged as fallback.
- Premature/emergency contributors: replace per-date chart entries with ONE `treatmentCharts` entry per stay (title `"Treatment charts — N day(s)"`, `at` = latest chart createdAt, params.count = N). Nursing entry gains kind `nursingLog` + params.count.
- `BedStayFormsHistoryContributor`: for result attachments, resolve the stay via `stayForOrderVisit(case.getVisitId())` across the injected `List<StayDirectory>` (inject the registry or the list — match existing style); when resolved, fileUrl = `/api/bed-stays/{dept}/{stayId}/documents/results/{attachmentId}/file`; else keep the legacy dept-cases URL. Kind `resultDocument`.
- ArchUnit must stay green (no new module edges; StayRef lives in bed-stay-forms, used only by its own contributor + impls).

**IT additions to FullHistoryIT:** after placing a lab order + uploading a result doc on the stay (reuse the StayDocumentsIT helper flow): timeline contains NO entry titled with the forwarded visit's display id and NO `PREMATURE visit` entry; exactly one `treatmentCharts` entry after PUTting charts on 2 distinct dates (params.count == "2"); every entry has non-null `kind`; the result document's fileUrl starts with `/api/bed-stays/PREMATURE/`.

**Verify:** `-Dit.test='FullHistoryIT,StayDocumentsIT'` green; ArchitectureTest 9/9. **Commit:** `fix(history): de-duplicated localizable timeline — kinds/params, chart roll-up, stay-scoped result URLs`

---

### Task 3: Frontend — expandable order rows + status pills

**Files:** Modify `frontend/src/features/beds/case/BedStayCasePage.tsx` (OrdersTab), `frontend/src/features/beds/case/forms/documentsApi.ts` (+`getOrderResults(dept, stayId, visitId)` + types), `frontend/src/features/beds/case/types.ts` if needed; locales en/ar.

**Contract:** OrdersTab needs `department` + `stayId` props (thread from both case pages — they already have them; check how `clinical` content receives them). Each order row: status pill `t('caseView.orderStatus.awaitingResults')` (amber) when no `resultsAt`, `t('caseView.orderStatus.resultsReady')` (emerald) when present — raw status code moves to `title=` tooltip; render `resultsAt` formatted next to the summary; chevron toggles an expanded panel (`data-testid="order-expand-{visitId}"` on the toggle, `order-results-{visitId}` on the panel) that lazily queries `['order-results', dept, stayId, visitId]` → findings list (serviceName bold + findings text, em-dash when blank) + documents (file icon, name, View via shared `DocumentPreview`, sizes) + empty state `t('caseView.orderStatus.nothingYet')`. RTL-safe. i18n en/ar: `caseView.orderStatus.{awaitingResults,resultsReady,nothingYet}`, `caseView.orderResults.{findings,documents}` (ar: بانتظار النتائج / النتائج جاهزة / لا توجد نتائج بعد / النتائج / المستندات).

**Verify:** `npx tsc -b` clean; existing e2e `bed-stay-orders` + `case-documents` still pass against the running stack (restart backend first — it predates Task 1-2). **Commit:** `feat(bed-stay-ui): expandable order rows — findings, result documents, friendly status`

---

### Task 4: Frontend — timeline kinds i18n, exams pill, visits chip

**Files:** Modify `frontend/src/features/clinical/api.ts` (TimelineEntry +`kind: string | null; params: Record<string,string> | null`), `frontend/src/features/patients/history/UnifiedTimeline.tsx`, `.../SummaryChips.tsx`, locales.

**Contract:** UnifiedTimeline renders `entry.kind && i18n has key patientProfile.timeline.kind.{kind} ? t(key, params) : entry.title` (use `i18n.exists` or a known-kinds set). Locale blocks (BOTH en/ar) for all 9 kinds with interpolations, e.g. en `admissionOpened: 'Admitted to {{bed}}'`, ar `admissionOpened: 'رقود في سرير {{bed}}'`, `treatmentCharts: 'Treatment charts — {{count}} day(s)'` / `'جداول العلاج — {{count}} يوم'`, `resultDocument: '{{fileName}} ({{visitType}})'` etc. — sensible hospital Arabic, mirror the paper-form register used elsewhere. Add `exams` pill (EXAM type, label en 'Exams' / ar 'الفحوصات') between visits and admissions. SummaryChips: visits chip counts `timeline.filter(t => t.type === 'VISIT').length` instead of totalVisits.

**Verify:** `npx tsc -b`; `npx playwright test patient-full-history vip-toggle --reporter=line` green (restart not needed if backend already restarted in Task 3). **Commit:** `feat(patients-ui): localized timeline kinds, exams filter, accurate visits chip`

---

### Task 5: E2E extensions

**Files:** extend `frontend/e2e/case-documents.spec.ts` (or new `case-order-results.spec.ts`) + `frontend/e2e/patient-full-history.spec.ts`.

**Contract:** (a) case page: after the lab-result seeding (existing helper), expand the lab order row (`order-expand-*`) → findings text visible + result.png in the panel + 'Results ready' pill; (b) profile: exams pill exists; with a lab order seeded, NO timeline entry contains the forwarded visit display id; (c) Arabic check: follow the pattern in `frontend/e2e/i18n-rtl.spec.ts` (read it) to switch language and assert one timeline title renders in Arabic (e.g. رقود). Run the touched specs → green.

**Commit:** `test(e2e): order-results expansion + timeline de-dup and Arabic titles`

---

### Task 6: Full verification

Backend `mvn verify` (all modules), `npm run build`, restart backend, full `npx playwright test`. All green; report honestly.
