# Bed-Stay Orders + Results-Pending Gate + Discharge Note — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let premature/emergency bed-stay staff order Lab/Radiology/ECO for an admitted patient, warn (with override) on Finish-Treatment when ordered results are still outstanding, and record a free-text discharge note — for **both** departments.

**Architecture:** Bed-stay orders create a `FORWARDED` child visit **without** pausing the parent (which stays `IN_PROGRESS`); the child→parent return path is made tolerant of a non-paused parent. The finish gate checks child-visit statuses and throws a 409 `ResultsPendingException` unless `override=true`. Discharge note + override reason are new columns on `prem_admission`/`emerg_case`. Reuses the existing department opencase→payment→findings→finalize flow unchanged.

**Tech Stack:** Java 21 / Spring Boot 3.3 (modular monolith, vertical slices), PostgreSQL + Flyway, JUnit5/AssertJ + Testcontainers (Failsafe ITs in `app`), React 18 + TS + react-query + Playwright.

**Spec:** `docs/superpowers/specs/2026-06-02-bed-stay-orders-discharge-gate-design.md`

**Build/test:** `JAVA_HOME=/usr/lib/jvm/java-25-openjdk mvn -pl <modules> -am verify`. Live stack already runs (backend :8080 from `backend/app/target/hms-app.jar`, vite :5173, db `hms-db` on 5432). E2E: `cd frontend && npx playwright test <spec>`.

**Branch:** `bed-stay-orders-discharge-gate` (already created). NEVER stage `frontend/src/features/clinical/*`, `*.tsbuildinfo`, `backend/app/data/`, `backend/data/`, or `frontend/playwright-report/`.

---

## File Structure

**Shared (visit-management):**
- Modify `backend/visit-management/.../forwardvisit/ForwardVisitHandler.java` — add `pauseParent` overload.
- Modify `backend/visit-management/.../domain/Visit.java:206` — tolerant `receiveResultsFromChild`.

**Platform:**
- Create `backend/platform/.../exception/ResultsPendingException.java` — 409 carrier (extends `ConflictException`).

**Premature (`backend/premature/...`):**
- Modify `domain/PrematureAdmission.java` — `dischargeNote`, `finishOverrideReason` + `setDischargeNote`, `recordFinishOverride`.
- Create slice `orderworkup/` — `OrderWorkupCommand`, `OrderWorkupHandler`, `OrderWorkupController`.
- Create slice `listorders/` — `ListOrdersController` (+ `api/OrderResponse.java`).
- Create slice `setdischargenote/` — `SetDischargeNoteCommand`, `SetDischargeNoteHandler`, `SetDischargeNoteController`.
- Modify `finishtreatment/FinishTreatmentHandler.java` + `FinishTreatmentController.java` + new `FinishTreatmentCommand`.
- Modify `api/AdmissionResponse.java` — add the two fields.

**Emergency (`backend/emergency/...`):** mirror of the premature slices/domain/response, with explicit `@Service("emergencyXxx")`/`@RestController("emergencyXxx")` bean names.

**Migration:** `backend/premature/src/main/resources/db/migration/V022__bed_stay_orders_discharge.sql` (alters both tables; lives in premature module which already owns the migration sequence alongside emergency — verify highest version is V021 first).

**Tests (`backend/app/src/test/java/com/albudoor/hms/app/`):** `prematureorders/PrematureOrdersIT.java`, `emergency/EmergencyOrdersIT.java`, and a regression assertion in an existing/lab IT.

**Frontend:** `frontend/src/features/premature/api.ts` + `BedDetailPanel.tsx`; `frontend/src/features/emergency/api.ts` + `BedDetailPanel.tsx`; `frontend/src/shared/i18n/locales/{en,ar}.ts`.

**E2E:** `frontend/e2e/bed-stay-orders.spec.ts`.

---

## Task 1: Non-pausing forward + tolerant results return (shared)

**Files:**
- Modify: `backend/visit-management/src/main/java/com/albudoor/hms/visitmanagement/forwardvisit/ForwardVisitHandler.java`
- Modify: `backend/visit-management/src/main/java/com/albudoor/hms/visitmanagement/domain/Visit.java:206-214`
- Test: `backend/visit-management/src/test/java/com/albudoor/hms/visitmanagement/domain/VisitResultsReturnTest.java` (create)

- [ ] **Step 1: Write the failing unit test**

Create `VisitResultsReturnTest.java`:
```java
package com.albudoor.hms.visitmanagement.domain;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class VisitResultsReturnTest {

    private Visit forwardedParentInProgress() {
        Visit p = Visit.create("V-1", UUID.randomUUID(), "MRN1", "Pat", VisitType.PREMATURE,
                VisitOrigin.DIRECT, null);
        p.pullDomainEvents();
        p.transitionTo(VisitStatus.AWAITING_PAYMENT);
        p.transitionTo(VisitStatus.IN_PROGRESS);
        p.pullDomainEvents();
        return p;
    }

    @Test
    void receiveResults_whenParentInProgress_doesNotTransition_butEmitsReturned() {
        Visit parent = forwardedParentInProgress();
        UUID childId = UUID.randomUUID();
        parent.receiveResultsFromChild(childId, VisitType.LABORATORY, "WBC normal");
        assertThat(parent.getStatus()).isEqualTo(VisitStatus.IN_PROGRESS); // not paused → stays
        assertThat(parent.getResultsSummary()).isEqualTo("WBC normal");
        assertThat(parent.pullDomainEvents())
                .anyMatch(e -> e instanceof VisitReturnedEvent);
    }

    @Test
    void receiveResults_whenParentAwaitingResults_resumesToInProgress() {
        Visit parent = forwardedParentInProgress();
        parent.markAwaitingResults(); // doctor pattern
        parent.pullDomainEvents();
        parent.receiveResultsFromChild(UUID.randomUUID(), VisitType.LABORATORY, "ok");
        assertThat(parent.getStatus()).isEqualTo(VisitStatus.IN_PROGRESS);
    }
}
```
(Verify `Visit.create(...)` signature against `Visit.java` — adjust the factory call to the real one; the existing domain tests under `visit-management/src/test` show the exact constructor to copy.)

- [ ] **Step 2: Run, verify it fails**

Run: `JAVA_HOME=/usr/lib/jvm/java-25-openjdk mvn -pl visit-management test -Dtest=VisitResultsReturnTest`
Expected: FAIL — `test 1` throws `NOT_AWAITING_RESULTS` (current code rejects an `IN_PROGRESS` parent).

- [ ] **Step 3: Make `receiveResultsFromChild` tolerant**

Replace `Visit.java` lines 206-214 with:
```java
    public void receiveResultsFromChild(UUID childId, VisitType childType, String summary) {
        this.resultsSummary = summary;
        if (this.status == VisitStatus.AWAITING_RESULTS) {
            // Doctor "pause-and-wait" pattern: resume the parent.
            transitionTo(VisitStatus.IN_PROGRESS);
        }
        // Bed-stay pattern: parent was never paused (stays IN_PROGRESS); just record + notify.
        registerEvent(VisitReturnedEvent.of(this.id, childId, childType, summary));
    }
```

- [ ] **Step 4: Add the `pauseParent` overload to `ForwardVisitHandler`**

In `ForwardVisitHandler.java`, keep the existing `handle(UUID, ForwardVisitCommand)` but delegate, and add the overload:
```java
    @Transactional
    public ForwardResult handle(UUID parentVisitId, ForwardVisitCommand cmd) {
        return handle(parentVisitId, cmd, true);
    }

    /**
     * @param pauseParent true for the doctor "pause-and-wait" flow (parent → AWAITING_RESULTS);
     *                    false for bed-stay orders (parent stays IN_PROGRESS, concurrent orders allowed).
     */
    @Transactional
    public ForwardResult handle(UUID parentVisitId, ForwardVisitCommand cmd, boolean pauseParent) {
        Visit parent = visits.findById(parentVisitId)
                .orElseThrow(() -> new NotFoundException("Visit not found: " + parentVisitId));
        if (cmd.targetType() == parent.getVisitType()) {
            throw new DomainException("INVALID_FORWARD_TARGET",
                    "Cannot forward a visit to its own visit type");
        }
        Visit child = Visit.createForwarded(idGenerator.next(), parent.getPatientId(),
                parent.getPatientMrn(), parent.getPatientName(), cmd.targetType(),
                parent.getId(), parent.getVisitType());
        visits.save(child);
        if (pauseParent) {
            parent.markAwaitingResults();
        }
        parent.pullDomainEvents().forEach(events::publishEvent);
        child.pullDomainEvents().forEach(events::publishEvent);
        events.publishEvent(VisitForwardedEvent.of(parent.getId(), child.getId(), cmd.targetType()));
        return new ForwardResult(parent, child);
    }
```

- [ ] **Step 5: Run unit tests, verify pass**

Run: `JAVA_HOME=/usr/lib/jvm/java-25-openjdk mvn -pl visit-management test`
Expected: PASS (new test + existing visit-management tests).

- [ ] **Step 6: Commit**
```bash
git add backend/visit-management/src/main/java/com/albudoor/hms/visitmanagement/forwardvisit/ForwardVisitHandler.java backend/visit-management/src/main/java/com/albudoor/hms/visitmanagement/domain/Visit.java backend/visit-management/src/test/java/com/albudoor/hms/visitmanagement/domain/VisitResultsReturnTest.java
git commit -m "feat(visit): non-pausing forward + tolerant results return for bed-stay orders"
```

---

## Task 2: Migration V022 (discharge note + override reason)

**Files:**
- Create: `backend/premature/src/main/resources/db/migration/V022__bed_stay_orders_discharge.sql`

- [ ] **Step 1: Confirm next version**

Run: `ls backend/*/src/main/resources/db/migration/ | grep -oE 'V0[0-9]+' | sort -u | tail -3`
Expected: highest is `V021`. If not, rename this migration to the next free number and update references.

- [ ] **Step 2: Write the migration**
```sql
-- Bed-stay clinical loop: free-text discharge note + finish-treatment override reason.
ALTER TABLE prem_admission ADD COLUMN discharge_note text;
ALTER TABLE prem_admission ADD COLUMN finish_override_reason text;

ALTER TABLE emerg_case ADD COLUMN discharge_note text;
ALTER TABLE emerg_case ADD COLUMN finish_override_reason text;
```

- [ ] **Step 3: Commit** (migration verified by the ITs in later tasks, which boot Flyway)
```bash
git add backend/premature/src/main/resources/db/migration/V022__bed_stay_orders_discharge.sql
git commit -m "feat(db): V022 add discharge_note + finish_override_reason to bed-stay tables"
```

---

## Task 3: `ResultsPendingException` (platform, 409)

**Files:**
- Create: `backend/platform/src/main/java/com/albudoor/hms/platform/exception/ResultsPendingException.java`

`ConflictException extends DomainException(code, message)` and `GlobalExceptionHandler` already maps `ConflictException` → 409 with `ApiError.of(409, code, message)`. So extending it gives us 409 + `code="RESULTS_PENDING"` with no handler change.

- [ ] **Step 1: Create the exception**
```java
package com.albudoor.hms.platform.exception;

/** Thrown when finish-treatment is attempted while ordered department results are still open. */
public class ResultsPendingException extends ConflictException {
    public ResultsPendingException(String message) {
        super("RESULTS_PENDING", message);
    }
}
```

- [ ] **Step 2: Build to verify it compiles**

Run: `JAVA_HOME=/usr/lib/jvm/java-25-openjdk mvn -pl platform -am test-compile -q`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**
```bash
git add backend/platform/src/main/java/com/albudoor/hms/platform/exception/ResultsPendingException.java
git commit -m "feat(platform): ResultsPendingException (409 RESULTS_PENDING)"
```

---

## Task 4: Premature domain — discharge note + override reason

**Files:**
- Modify: `backend/premature/src/main/java/com/albudoor/hms/premature/domain/PrematureAdmission.java`
- Test: `backend/premature/src/test/java/com/albudoor/hms/premature/domain/PrematureAdmissionNotesTest.java` (create)

- [ ] **Step 1: Failing unit test**
```java
package com.albudoor.hms.premature.domain;

import org.junit.jupiter.api.Test;
import com.albudoor.hms.platform.exception.DomainException;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class PrematureAdmissionNotesTest {
    private PrematureAdmission open() {
        return PrematureAdmission.open(UUID.randomUUID(), "V-1", UUID.randomUUID(), "MRN", "Pat",
                UUID.randomUUID(), "BED-1", 3, StayUnit.DAYS);
    }
    @Test void setDischargeNote_persistsText() {
        PrematureAdmission a = open();
        a.setDischargeNote("Stable. Home on oral feeds. Review in 1 week.");
        assertThat(a.getDischargeNote()).contains("Stable");
    }
    @Test void recordFinishOverride_requiresReason() {
        PrematureAdmission a = open();
        assertThatThrownBy(() -> a.recordFinishOverride("  "))
                .isInstanceOf(DomainException.class);
        a.recordFinishOverride("Parents accept results will follow");
        assertThat(a.getFinishOverrideReason()).isNotBlank();
    }
}
```

- [ ] **Step 2: Run, verify fail**

Run: `JAVA_HOME=/usr/lib/jvm/java-25-openjdk mvn -pl premature test -Dtest=PrematureAdmissionNotesTest`
Expected: FAIL — methods/getters absent.

- [ ] **Step 3: Add fields + methods**

Add fields after `finalPaymentId` (line 75):
```java
    @Column(name = "discharge_note")
    private String dischargeNote;

    @Column(name = "finish_override_reason")
    private String finishOverrideReason;
```
Add methods (before `private void require`):
```java
    public void setDischargeNote(String note) {
        this.dischargeNote = note;
    }

    public void recordFinishOverride(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new DomainException("OVERRIDE_REASON_REQUIRED",
                    "An override reason is required to finish with results pending");
        }
        this.finishOverrideReason = reason;
    }
```

- [ ] **Step 4: Run, verify pass**

Run: `JAVA_HOME=/usr/lib/jvm/java-25-openjdk mvn -pl premature test -Dtest=PrematureAdmissionNotesTest`
Expected: PASS.

- [ ] **Step 5: Commit**
```bash
git add backend/premature/src/main/java/com/albudoor/hms/premature/domain/PrematureAdmission.java backend/premature/src/test/java/com/albudoor/hms/premature/domain/PrematureAdmissionNotesTest.java
git commit -m "feat(premature): discharge note + finish-override-reason on admission"
```

---

## Task 5: Premature order slice (`orderworkup`)

**Files:**
- Create: `backend/premature/.../orderworkup/OrderWorkupCommand.java`, `OrderWorkupHandler.java`, `OrderWorkupController.java`

`OrderWorkupHandler` injects the visit-management `ForwardVisitHandler` (already on premature's classpath — premature already imports `visitmanagement` types). It validates the admission is `UNDER_CARE`, then forwards **without pausing**.

- [ ] **Step 1: Command** (`OrderWorkupCommand.java`)
```java
package com.albudoor.hms.premature.orderworkup;

import com.albudoor.hms.visitmanagement.domain.VisitType;
import jakarta.validation.constraints.NotNull;

public record OrderWorkupCommand(@NotNull VisitType targetType) {}
```

- [ ] **Step 2: Handler** (`OrderWorkupHandler.java`)
```java
package com.albudoor.hms.premature.orderworkup;

import com.albudoor.hms.platform.exception.DomainException;
import com.albudoor.hms.platform.exception.NotFoundException;
import com.albudoor.hms.premature.domain.AdmissionStatus;
import com.albudoor.hms.premature.domain.PrematureAdmission;
import com.albudoor.hms.premature.infrastructure.PrematureAdmissionRepository;
import com.albudoor.hms.visitmanagement.domain.Visit;
import com.albudoor.hms.visitmanagement.domain.VisitType;
import com.albudoor.hms.visitmanagement.forwardvisit.ForwardVisitCommand;
import com.albudoor.hms.visitmanagement.forwardvisit.ForwardVisitHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

@Service
public class OrderWorkupHandler {

    private static final Set<VisitType> ORDERABLE = EnumSet.of(
            VisitType.LABORATORY, VisitType.RADIOLOGY, VisitType.ECO);

    private final PrematureAdmissionRepository admissions;
    private final ForwardVisitHandler forwardVisit;

    public OrderWorkupHandler(PrematureAdmissionRepository admissions, ForwardVisitHandler forwardVisit) {
        this.admissions = admissions;
        this.forwardVisit = forwardVisit;
    }

    @Transactional
    public Visit handle(UUID admissionId, OrderWorkupCommand cmd) {
        if (!ORDERABLE.contains(cmd.targetType())) {
            throw new DomainException("INVALID_ORDER_TARGET",
                    "Can only order LABORATORY, RADIOLOGY or ECO; got " + cmd.targetType());
        }
        PrematureAdmission a = admissions.findById(admissionId)
                .orElseThrow(() -> new NotFoundException("Admission not found: " + admissionId));
        if (a.getStatus() != AdmissionStatus.UNDER_CARE) {
            throw new DomainException("ADMISSION_NOT_ORDERABLE",
                    "Can only order while UNDER_CARE (status=" + a.getStatus() + ")");
        }
        // Non-pausing forward: the admission's visit stays IN_PROGRESS.
        return forwardVisit.handle(a.getVisitId(), new ForwardVisitCommand(cmd.targetType()), false).child();
    }
}
```

- [ ] **Step 3: Controller** (`OrderWorkupController.java`) — returns the new order
```java
package com.albudoor.hms.premature.orderworkup;

import com.albudoor.hms.premature.api.OrderResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/premature/admissions")
public class OrderWorkupController {
    private final OrderWorkupHandler handler;
    public OrderWorkupController(OrderWorkupHandler handler) { this.handler = handler; }

    @PostMapping("/{id}/orders")
    @PreAuthorize("hasAnyRole('PREMATURE_STAFF', 'NURSE', 'DOCTOR', 'ADMIN')")
    public OrderResponse order(@PathVariable UUID id, @Valid @RequestBody OrderWorkupCommand cmd) {
        return OrderResponse.from(handler.handle(id, cmd));
    }
}
```
(`OrderResponse` is created in Task 6.)

- [ ] **Step 4: Build (compile only; behavior covered by IT in Task 11)**

Run: `JAVA_HOME=/usr/lib/jvm/java-25-openjdk mvn -pl premature -am test-compile -q` (after Task 6 adds `OrderResponse`).
Expected: SUCCESS.

- [ ] **Step 5: Commit** (together with Task 6)

---

## Task 6: Premature list-orders slice + `OrderResponse`

**Files:**
- Create: `backend/premature/.../api/OrderResponse.java`
- Create: `backend/premature/.../listorders/ListOrdersController.java`

- [ ] **Step 1: `OrderResponse.java`**
```java
package com.albudoor.hms.premature.api;

import com.albudoor.hms.visitmanagement.domain.Visit;
import java.time.Instant;
import java.util.UUID;

public record OrderResponse(
        UUID visitId, String visitDisplayId, String visitType, String status,
        String resultsSummary, Instant startedAt
) {
    public static OrderResponse from(Visit v) {
        return new OrderResponse(v.getId(), v.getVisitDisplayId(), v.getVisitType().name(),
                v.getStatus().name(), v.getResultsSummary(), v.getStartedAt());
    }
}
```
(Confirm `Visit` getters `getResultsSummary()`/`getStartedAt()` exist — grep `Visit.java`; both are referenced elsewhere.)

- [ ] **Step 2: `ListOrdersController.java`**
```java
package com.albudoor.hms.premature.listorders;

import com.albudoor.hms.platform.exception.NotFoundException;
import com.albudoor.hms.premature.api.OrderResponse;
import com.albudoor.hms.premature.domain.PrematureAdmission;
import com.albudoor.hms.premature.infrastructure.PrematureAdmissionRepository;
import com.albudoor.hms.visitmanagement.infrastructure.VisitRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/premature/admissions")
public class ListOrdersController {
    private final PrematureAdmissionRepository admissions;
    private final VisitRepository visits;
    public ListOrdersController(PrematureAdmissionRepository admissions, VisitRepository visits) {
        this.admissions = admissions; this.visits = visits;
    }

    @GetMapping("/{id}/orders")
    @PreAuthorize("isAuthenticated()")
    public List<OrderResponse> list(@PathVariable UUID id) {
        PrematureAdmission a = admissions.findById(id)
                .orElseThrow(() -> new NotFoundException("Admission not found: " + id));
        return visits.findAllByParentVisitIdOrderByStartedAtDesc(a.getVisitId())
                .stream().map(OrderResponse::from).toList();
    }
}
```

- [ ] **Step 3: Compile**

Run: `JAVA_HOME=/usr/lib/jvm/java-25-openjdk mvn -pl premature -am test-compile -q`
Expected: SUCCESS (Tasks 5+6 compile together).

- [ ] **Step 4: Commit (Tasks 5+6)**
```bash
git add backend/premature/src/main/java/com/albudoor/hms/premature/orderworkup backend/premature/src/main/java/com/albudoor/hms/premature/listorders backend/premature/src/main/java/com/albudoor/hms/premature/api/OrderResponse.java
git commit -m "feat(premature): order Lab/Radiology/ECO from an admission (+ list orders)"
```

---

## Task 7: Premature discharge-note slice

**Files:**
- Create: `backend/premature/.../setdischargenote/SetDischargeNoteCommand.java`, `SetDischargeNoteHandler.java`, `SetDischargeNoteController.java`

- [ ] **Step 1: Command**
```java
package com.albudoor.hms.premature.setdischargenote;
import jakarta.validation.constraints.NotNull;
public record SetDischargeNoteCommand(@NotNull String note) {}
```

- [ ] **Step 2: Handler**
```java
package com.albudoor.hms.premature.setdischargenote;

import com.albudoor.hms.platform.exception.NotFoundException;
import com.albudoor.hms.premature.domain.PrematureAdmission;
import com.albudoor.hms.premature.infrastructure.PrematureAdmissionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
public class SetDischargeNoteHandler {
    private final PrematureAdmissionRepository admissions;
    public SetDischargeNoteHandler(PrematureAdmissionRepository admissions) { this.admissions = admissions; }

    @Transactional
    public PrematureAdmission handle(UUID admissionId, SetDischargeNoteCommand cmd) {
        PrematureAdmission a = admissions.findById(admissionId)
                .orElseThrow(() -> new NotFoundException("Admission not found: " + admissionId));
        a.setDischargeNote(cmd.note());
        return admissions.save(a);
    }
}
```

- [ ] **Step 3: Controller**
```java
package com.albudoor.hms.premature.setdischargenote;

import com.albudoor.hms.premature.api.AdmissionResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/premature/admissions")
public class SetDischargeNoteController {
    private final SetDischargeNoteHandler handler;
    public SetDischargeNoteController(SetDischargeNoteHandler handler) { this.handler = handler; }

    @PostMapping("/{id}/discharge-note")
    @PreAuthorize("hasAnyRole('PREMATURE_STAFF', 'NURSE', 'DOCTOR', 'ADMIN')")
    public AdmissionResponse set(@PathVariable UUID id, @Valid @RequestBody SetDischargeNoteCommand cmd) {
        return AdmissionResponse.from(handler.handle(id, cmd));
    }
}
```

- [ ] **Step 4: Commit** (after Task 9 updates `AdmissionResponse`, compile then commit)
```bash
git add backend/premature/src/main/java/com/albudoor/hms/premature/setdischargenote
git commit -m "feat(premature): set free-text discharge note on an admission"
```

---

## Task 8: Premature finish gate (warn + override)

**Files:**
- Create: `backend/premature/.../finishtreatment/FinishTreatmentCommand.java`
- Modify: `backend/premature/.../finishtreatment/FinishTreatmentHandler.java`
- Modify: `backend/premature/.../finishtreatment/FinishTreatmentController.java`

- [ ] **Step 1: Command**
```java
package com.albudoor.hms.premature.finishtreatment;
public record FinishTreatmentCommand(boolean override, String overrideReason) {}
```

- [ ] **Step 2: Handler — add the gate**

Change `handle(UUID admissionId)` to `handle(UUID admissionId, FinishTreatmentCommand cmd)`. Add `VisitRepository` injection (already imported). Insert the gate **before** `admission.finishTreatment()`:
```java
    @Transactional
    public PrematureAdmission handle(UUID admissionId, FinishTreatmentCommand cmd) {
        PrematureAdmission admission = admissions.findById(admissionId)
                .orElseThrow(() -> new NotFoundException("Admission not found: " + admissionId));

        // Results-pending gate (warn + override): open = child visit not COMPLETED/CANCELLED.
        List<Visit> children = visits.findAllByParentVisitIdOrderByStartedAtDesc(admission.getVisitId());
        List<Visit> open = children.stream()
                .filter(v -> v.getStatus() != VisitStatus.COMPLETED && v.getStatus() != VisitStatus.CANCELLED)
                .toList();
        if (!open.isEmpty()) {
            if (!cmd.override()) {
                String list = open.stream()
                        .map(v -> v.getVisitDisplayId() + " (" + v.getVisitType() + ", " + v.getStatus() + ")")
                        .collect(java.util.stream.Collectors.joining(", "));
                throw new ResultsPendingException("Results still pending: " + list);
            }
            admission.recordFinishOverride(cmd.overrideReason()); // throws if reason blank
        }

        admission.finishTreatment();
        admissions.save(admission);
        // ... rest unchanged (visit → TREATMENT_FINISHED, create FINAL payment, scheduleDischargePayment) ...
    }
```
Add imports: `com.albudoor.hms.platform.exception.ResultsPendingException;`, `java.util.List;` (Visit/VisitStatus already imported).

- [ ] **Step 3: Controller — accept the command**
```java
    @PostMapping("/{id}/finish-treatment")
    @PreAuthorize("hasAnyRole('PREMATURE_STAFF', 'DOCTOR', 'ADMIN')")
    public AdmissionResponse finish(@PathVariable UUID id,
                                    @RequestBody(required = false) FinishTreatmentCommand cmd) {
        return AdmissionResponse.from(handler.handle(id,
                cmd != null ? cmd : new FinishTreatmentCommand(false, null)));
    }
```
Add import `org.springframework.web.bind.annotation.RequestBody`.

- [ ] **Step 4: Compile**

Run: `JAVA_HOME=/usr/lib/jvm/java-25-openjdk mvn -pl premature -am test-compile -q`
Expected: SUCCESS.

- [ ] **Step 5: Commit**
```bash
git add backend/premature/src/main/java/com/albudoor/hms/premature/finishtreatment
git commit -m "feat(premature): results-pending gate on finish-treatment (warn + override)"
```

---

## Task 9: Premature `AdmissionResponse` — expose notes

**Files:**
- Modify: `backend/premature/src/main/java/com/albudoor/hms/premature/api/AdmissionResponse.java`

- [ ] **Step 1: Add two fields + map them**

Append `String dischargeNote, String finishOverrideReason` to the record components (after `finalPaymentId`) and to the `from(...)` constructor call: `a.getDischargeNote(), a.getFinishOverrideReason()`.

- [ ] **Step 2: Compile premature**

Run: `JAVA_HOME=/usr/lib/jvm/java-25-openjdk mvn -pl premature -am test-compile -q`
Expected: SUCCESS.

- [ ] **Step 3: Commit**
```bash
git add backend/premature/src/main/java/com/albudoor/hms/premature/api/AdmissionResponse.java
git commit -m "feat(premature): expose discharge note + override reason on AdmissionResponse"
```

---

## Task 10: Emergency mirror

Mirror Tasks 4–9 for emergency, against `EmergencyCase` (status enum `EmergencyCaseStatus`, orderable check requires `UNDER_TREATMENT`). **Every new bean gets an explicit name** to avoid the premature/emergency simple-name collision (`ConflictingBeanDefinitionException`) — e.g. `@Service("emergencyOrderWorkupHandler")`, `@RestController("emergencyOrderWorkupController")`, etc.

**Files (create unless noted):**
- Modify `domain/EmergencyCase.java` — add `dischargeNote`, `finishOverrideReason` columns + `setDischargeNote`, `recordFinishOverride` (identical bodies to Task 4, `DomainException` codes reused).
- `orderworkup/`: `OrderWorkupCommand` (same record), `OrderWorkupHandler` (`@Service("emergencyOrderWorkupHandler")`; injects emergency `EmergencyCaseRepository` + `ForwardVisitHandler`; requires status `UNDER_TREATMENT`; `forwardVisit.handle(c.getVisitId(), new ForwardVisitCommand(cmd.targetType()), false)`), `OrderWorkupController` (`@RestController("emergencyOrderWorkupController")`, `@RequestMapping("/api/emergency/cases")`, `POST /{id}/orders`, roles `EMERGENCY_STAFF,NURSE,DOCTOR,ADMIN`, returns `emergency` `OrderResponse`).
- `api/OrderResponse.java` — same shape as premature's, mapping from `Visit`.
- `listorders/ListOrdersController` (`@RestController("emergencyListOrdersController")`, `GET /api/emergency/cases/{id}/orders`).
- `setdischargenote/` — `SetDischargeNoteCommand`, `SetDischargeNoteHandler` (`@Service("emergencySetDischargeNoteHandler")`), `SetDischargeNoteController` (`@RestController("emergencySetDischargeNoteController")`, `POST /api/emergency/cases/{id}/discharge-note`).
- `finishtreatment/FinishTreatmentCommand` (same record); modify `FinishTreatmentHandler` (add `VisitRepository`, same gate, `EmergencyCaseStatus`); modify `FinishTreatmentController` (accept optional `FinishTreatmentCommand`).
- Modify `api/CaseResponse.java` — append `String dischargeNote, String finishOverrideReason` + map.

- [ ] **Step 1:** Create/modify all of the above mirroring Tasks 4–9 exactly, with the explicit bean names.
- [ ] **Step 2: Compile**

Run: `JAVA_HOME=/usr/lib/jvm/java-25-openjdk mvn -pl emergency -am test-compile -q`
Expected: SUCCESS.

- [ ] **Step 3: Boot-time bean-collision smoke check** (the real risk for mirror modules)

Run: `JAVA_HOME=/usr/lib/jvm/java-25-openjdk mvn -pl app -am test -Dtest=ArchitectureTest -q`
Expected: PASS (context-affecting compile of app; if a duplicate bean name slipped in, app ITs fail to start — caught fully in Task 11).

- [ ] **Step 4: Commit**
```bash
git add backend/emergency/src/main/java/com/albudoor/hms/emergency
git commit -m "feat(emergency): mirror orders + results-gate + discharge note (explicit bean names)"
```

---

## Task 11: Integration tests (Testcontainers, app module)

**Files:**
- Create: `backend/app/src/test/java/com/albudoor/hms/app/prematureorders/PrematureOrdersIT.java`
- Create: `backend/app/src/test/java/com/albudoor/hms/app/emergency/EmergencyOrdersIT.java`

Use the existing `IntegrationTest` base + MockMvc/`TestRestTemplate` patterns from `app/.../premature/AdmitFlowIT.java` and `emergency/AdmitFlowIT.java` (copy their setup: seed patient, create visit, admit, approve initial payment to reach UNDER_CARE/UNDER_TREATMENT). Reuse their auth helpers (login as roles).

- [ ] **Step 1: PrematureOrdersIT — write all scenarios** (each a `@Test`):
  1. `order_createsForwardedChild_parentStaysInProgress`: admit→pay→UNDER_CARE; `POST …/orders {LABORATORY}` → 200; assert a child LABORATORY visit exists for the parent (`GET …/orders` returns it) AND the parent visit status is still `IN_PROGRESS` (not `AWAITING_RESULTS`).
  2. `finish_blockedWhileOrderOpen`: with the open lab order, `POST …/finish-treatment {override:false}` → **409** with body `code == "RESULTS_PENDING"`; message contains the lab visit display id.
  3. `finish_withOverride_succeedsAndRecordsReason`: `POST …/finish-treatment {override:true, overrideReason:"will follow"}` → 200; `GET` admission shows status `AWAITING_DISCHARGE_PAYMENT` and `finishOverrideReason == "will follow"`. Also assert `{override:true, overrideReason:"  "}` → 422 (`OVERRIDE_REASON_REQUIRED`).
  4. `finish_afterResultsReturned_needsNoOverride`: open the child lab case, add findings, finalize it (drive the department flow exactly like `brd-rec-002` lab IT does, or call the finalize handler/endpoint); assert the child reaches `COMPLETED` and the parent stays `IN_PROGRESS` (tolerant return — no exception); then `POST …/finish-treatment {override:false}` → 200.
  5. `dischargeNote_persists`: `POST …/discharge-note {note:"Home on feeds"}` → 200; `GET` admission shows `dischargeNote`.
  6. `order_wrongRole_forbidden`: as `CASHIER`, `POST …/orders` → 403.
- [ ] **Step 2: EmergencyOrdersIT — mirror** scenarios 1–6 against `/api/emergency/cases/...` (UNDER_TREATMENT).
- [ ] **Step 3: Regression — doctor→lab pause/resume unchanged**

In the existing lab IT (or a new `DoctorForwardRegressionIT`), assert that forwarding a `DOCTOR_APPOINTMENT` visit to LABORATORY still sets the parent to `AWAITING_RESULTS`, and finalizing the lab case returns the parent to `IN_PROGRESS`. (If `brd-rec-002`/lab ITs already cover this, just confirm they still pass — no new test needed; note it.)

- [ ] **Step 4: Run the ITs**

Run: `JAVA_HOME=/usr/lib/jvm/java-25-openjdk mvn -pl app -am verify -Dit.test=PrematureOrdersIT,EmergencyOrdersIT`
Expected: BUILD SUCCESS, all scenarios pass.

- [ ] **Step 5: Full backend verify (no regressions)**

Run: `JAVA_HOME=/usr/lib/jvm/java-25-openjdk mvn -pl app -am verify`
Expected: BUILD SUCCESS (all modules' unit + ITs, incl. doctor→lab flow).

- [ ] **Step 6: Commit**
```bash
git add backend/app/src/test/java/com/albudoor/hms/app/prematureorders backend/app/src/test/java/com/albudoor/hms/app/emergency/EmergencyOrdersIT.java
git commit -m "test(it): bed-stay orders + results gate + discharge note (premature & emergency) + doctor-flow regression"
```

---

## Task 12: Frontend — drawer orders + discharge note + results-pending dialog

**Files:**
- Modify: `frontend/src/features/premature/api.ts`, `frontend/src/features/premature/BedDetailPanel.tsx`
- Modify: `frontend/src/features/emergency/api.ts`, `frontend/src/features/emergency/BedDetailPanel.tsx`
- Modify: `frontend/src/shared/i18n/locales/en.ts`, `frontend/src/shared/i18n/locales/ar.ts`

- [ ] **Step 1: api.ts (premature) — add functions; extend finish**
```ts
export type Order = { visitId: string; visitDisplayId: string; visitType: string; status: string; resultsSummary?: string | null; startedAt: string };

export async function listOrders(admissionId: string): Promise<Order[]> {
  const res = await api.get(`/premature/admissions/${admissionId}/orders`);
  return res.data;
}
export async function orderWorkup(admissionId: string, targetType: 'LABORATORY'|'RADIOLOGY'|'ECO'): Promise<Order> {
  const res = await api.post(`/premature/admissions/${admissionId}/orders`, { targetType });
  return res.data;
}
export async function setDischargeNote(admissionId: string, note: string): Promise<Admission> {
  const res = await api.post(`/premature/admissions/${admissionId}/discharge-note`, { note });
  return res.data;
}
// CHANGE finishTreatment signature:
export async function finishTreatment(admissionId: string, override = false, overrideReason?: string): Promise<Admission> {
  const res = await api.post(`/premature/admissions/${admissionId}/finish-treatment`, { override, overrideReason });
  return res.data;
}
```
Also add `dischargeNote?: string | null` to the `Admission` type.

- [ ] **Step 2: BedDetailPanel (premature) — Orders + discharge note + finish gate**

In the `UNDER_CARE` actions block, add (with `react-query` `useQuery(['prem-orders', case.id], () => listOrders(case.id))` and mutations):
- Three buttons `Send to Lab / Radiology / ECO` (testids `order-LABORATORY`, `order-RADIOLOGY`, `order-ECO`) → `orderWorkup` mutation → on success `toast.success(t('premature.orders.ordered'))` + invalidate orders.
- An order list (testid `order-list`) rendering each order `visitDisplayId — visitType — status`.
- A discharge-note textarea (`discharge-note-input`) + Save button (`discharge-note-save`) → `setDischargeNote`.
- Change the Finish button handler: call `finishTreatment(case.id)` and on error, if `extractApiError(err)?.code === 'RESULTS_PENDING'`, open a results-pending dialog (`results-pending-dialog`) showing the error message + a reason input (`override-reason`) + **Finish anyway** button (`finish-override`) → `finishTreatment(case.id, true, reason)`.

Follow the existing drawer styling and the emergency/premature i18n key conventions. Keep the panel's existing finish behaviour (success toast + invalidate) for the no-pending path.

- [ ] **Step 3: Mirror Steps 1–2 for emergency** (`/emergency/cases/...`, `emergency.*` i18n keys, `Case` type gains `dischargeNote`).

- [ ] **Step 4: i18n — add keys to en.ts AND ar.ts** under both `premature` and `emergency`:
```
orders: { title: 'Orders', sendToLab: 'Send to Lab', sendToRadiology: 'Send to Radiology', sendToEco: 'Send to ECO', ordered: 'Order sent', none: 'No orders', status: 'Status' },
dischargeNote: { label: 'Discharge note', save: 'Save note', saved: 'Discharge note saved', placeholder: 'Condition at discharge, medications, follow-up…' },
resultsPending: { title: 'Results still pending', body: 'These orders have not returned yet:', reason: 'Reason to finish anyway', finishAnyway: 'Finish anyway' },
```
(Arabic: provide proper translations, matching existing ar.ts tone. Keys MUST match en.ts exactly.)

- [ ] **Step 5: Typecheck + build**

Run: `cd frontend && npx tsc -b && npm run build`
Expected: clean (no TS errors).

- [ ] **Step 6: Commit**
```bash
git add frontend/src/features/premature/api.ts frontend/src/features/premature/BedDetailPanel.tsx frontend/src/features/emergency/api.ts frontend/src/features/emergency/BedDetailPanel.tsx frontend/src/shared/i18n/locales/en.ts frontend/src/shared/i18n/locales/ar.ts
git commit -m "feat(ui): bed-stay orders, discharge note, results-pending dialog (premature & emergency)"
```
(NEVER stage `frontend/src/features/clinical/*` or `*.tsbuildinfo`.)

---

## Task 13: E2E + final verification

**Files:**
- Create: `frontend/e2e/bed-stay-orders.spec.ts`

Live stack must be running (rebuild backend jar with the new code first, restart it; vite picks up frontend automatically). Rebuild: `cd backend && JAVA_HOME=/usr/lib/jvm/java-25-openjdk mvn -pl app -am install -DskipTests -q` then restart the jar.

- [ ] **Step 1: Write the e2e spec** (two tests, premature + emergency). Pattern from `emergency-lifecycle-ui.spec.ts` (seed to OCCUPIED/UNDER_CARE via API; relogin helper; cashier helpers reused). Each test:
  1. Seed admit→approve initial→UNDER_CARE (API).
  2. Open workspace, click the bed → drawer.
  3. Click `order-LABORATORY` → assert `order-list` shows a LABORATORY order.
  4. Click `detail-finish` → assert `results-pending-dialog` appears listing the order.
  5. Fill `override-reason`, click `finish-override` → assert success (case → awaiting discharge; `bed-status-*` still Occupied).
  6. (Optional) set a discharge note via `discharge-note-input` + `discharge-note-save` → assert saved toast.

- [ ] **Step 2: Run the new spec**

Run: `cd frontend && npx playwright test bed-stay-orders --reporter=list`
Expected: 2 passed. Iterate selectors/timeouts until green.

- [ ] **Step 3: Full Playwright regression**

Run: `cd frontend && npx playwright test --reporter=list`
Expected: all green (existing 65 + new). Re-run any flake in isolation to confirm.

- [ ] **Step 4: Commit + final**
```bash
git add frontend/e2e/bed-stay-orders.spec.ts
git commit -m "test(e2e): bed-stay order → results-pending warn → override → discharge note"
```
Then hand off to finishing-a-development-branch (merge to master after a final review).

---

## Self-Review notes (for the executor)
- **Spec coverage:** O1/O2 → T5/T10; O3 → T1; G1–G4 → T8/T10/T11; D1 → T4/T7/T9/T10; UI → T12; i18n → T12.5; migration → T2; tests → T11/T13. All spec sections mapped.
- **Type consistency:** `OrderWorkupCommand.targetType` (VisitType) ↔ `orderWorkup(...targetType)` (TS union) ↔ `ForwardVisitCommand(targetType)`. `FinishTreatmentCommand(override, overrideReason)` ↔ TS `finishTreatment(id, override, overrideReason)`. `RESULTS_PENDING` code identical in `ResultsPendingException`, the IT assertion, and the FE `extractApiError` check. `OrderResponse` fields identical FE/BE.
- **Risk:** mirror-module bean-name collision — Task 10 mandates explicit `@Service`/`@RestController` names; Task 11 catches any miss at app boot.
- **Do not touch:** `frontend/src/features/clinical/ClinicalExamPage.tsx`, `api.ts`, `*.tsbuildinfo`, `backend/app/data/`, `backend/data/`, `frontend/playwright-report/`.
