# Emergency Admission Spine — Implementation Plan (Sub-project EA)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Build the Emergency admission spine (HMS-BRD-REC-004): reception → Emergency visit → bed + period of stay + **service-type selection (1 of 40)** → initial payment (bills the service) → Occupied/Under-Treatment → finish → final discharge payment → Closed/Discharged (P12b retry), with a bed dashboard + bed-detail drawer.

**Architecture:** A new `emergency` bounded-context module that **mirrors the existing `premature` module** (proven template). `EmergencyBed` + `EmergencyCase` aggregates, vertical slices, own Flyway migration, an `EmergencyPaymentBridge` in the app composition root. Reuses the cashier `CreatePaymentHandler`, the visit/queue plumbing, and the 40 EMERGENCY catalogue items (already seeded). The generic `PaymentVisitBridge` FINAL-reject guard is extended to skip `EMERGENCY` (P12b).

**Tech Stack:** Java 21 / Spring Boot 3.3 / JPA / Flyway / Postgres; React 18 + TS + Vite + react-query + react-hook-form + react-i18next; JUnit 5 + Testcontainers (Failsafe) + Playwright.

**Spec:** `docs/superpowers/specs/2026-05-31-emergency-admission-spine-design.md`

---

## Environment (every task)
- Backend: NO `./mvnw`; `mvn` with `JAVA_HOME=/usr/lib/jvm/java-25-openjdk` inline EVERY call from `backend/` (shell env doesn't persist). Use `-am` on `-pl emergency` builds. App-module test selection needs `-Dsurefire.failIfNoSpecifiedTests=false`. ITs are `*IT` run via **Failsafe in `verify`**. Docker + Testcontainers work.
- Frontend: `cd frontend`; `npx tsc -b`, `npm run build`, `npx playwright test`. Don't touch `frontend/src/features/clinical/*`.
- Conventions identical to the premature module (aggregates extend `platform.domain.AggregateRoot`; `DomainException`/`NotFoundException`; `@Service`+`@Transactional`; `@RestController`+`@PreAuthorize`+`@Valid`; DTO records with `from(...)`). Current user: `SecurityContextHolder...getPrincipal()` is `HmsUserPrincipal` → `.userId()`.

## THE MIRROR — rename map (premature → emergency)
Most of EA is structurally identical to the **premature admission spine** already in the repo. For each "mirror" task, READ the cited premature file and reproduce it for emergency applying this rename map:

| premature | emergency |
|---|---|
| package `com.albudoor.hms.premature` | `com.albudoor.hms.emergency` |
| `Bed` / `prem_bed` | `EmergencyBed` / `emerg_bed` |
| `PrematureAdmission` / `prem_admission` | `EmergencyCase` / `emerg_case` |
| `AdmissionStatus` (AWAITING_ADMISSION_PAYMENT, UNDER_CARE, …) | `EmergencyCaseStatus` (AWAITING_INITIAL_PAYMENT, **UNDER_TREATMENT**, TREATMENT_FINISHED, AWAITING_DISCHARGE_PAYMENT, CLOSED, CANCELLED) |
| `PrematurePaymentBridge` | `EmergencyPaymentBridge` |
| `/api/premature/admissions` | `/api/emergency/cases` |
| `/api/premature/beds` | `/api/emergency/beds` |
| `AdmissionResponse` | `CaseResponse` |
| role `PREMATURE_STAFF` | `EMERGENCY_STAFF` |
| frontend `features/premature` | `features/emergency` |
| i18n `premature.*` | `emergency.*` |

**Emergency-only additions** (NOT in premature): `EmergencyCase` carries `serviceItemId` + `serviceCode` + `serviceName`; the admit command/handler take a `serviceItemId` and bill THAT catalogue item for the INITIAL payment; a `GET /api/emergency/services` slice; the admit dialog has a service-type dropdown. Case status `markUnderCare()` → `markUnderTreatment()`.

## File structure (new `backend/emergency/src/main/java/com/albudoor/hms/emergency/`)
`EmergencyAutoConfig`; `domain/`: `EmergencyBed`, `BedStatus`, `EmergencyCase`, `EmergencyCaseStatus`, `StayUnit`; `infrastructure/`: `EmergencyBedRepository`, `EmergencyCaseRepository`; `api/`: `BedResponse`, `CaseResponse`, `EmergencyServiceResponse`; slices: `createbed/`, `updatebed/`, `listbeds/`, `listservices/`, `admitpatient/`, `extendstay/`, `finishtreatment/`, `reissuedischargepayment/`, `listcases/`; `bridge/EmergencyPaymentBridge`; `resources/db/migration/V021__emergency_init.sql`. Edited: root `pom.xml`, `app/pom.xml`, `HmsApplication`, `PaymentVisitBridge`, `ArchitectureTest`. Frontend `features/emergency/{api.ts,EmergencyWorkspacePage.tsx,BedDetailPanel.tsx,BedAdminPage.tsx}` + `App.tsx`, `routes.ts`, `PatientProfilePage.tsx`, i18n. Tests: `emergency/src/test/.../domain/{EmergencyBedTest,EmergencyCaseTest}.java`, `app/src/test/.../emergency/{BedAdminIT,AdmitFlowIT,DischargeFlowIT}.java`, `frontend/e2e/{brd-rec-004-emergency,emergency-ui}.spec.ts`.

---

# PHASE 1 — Module skeleton

### Task 1.1: Create the `emergency` module and wire it in
**Files:** Create `backend/emergency/pom.xml`, `backend/emergency/src/main/java/com/albudoor/hms/emergency/EmergencyAutoConfig.java`; modify `backend/pom.xml`, `backend/app/pom.xml`, `HmsApplication.java`.

- [ ] **Step 1: `backend/emergency/pom.xml`** — copy `backend/premature/pom.xml` verbatim, changing only `<artifactId>premature</artifactId>` → `emergency` and `<name>HMS — Premature</name>` → `HMS — Emergency`. (Keeps the same deps: platform, visit-management, cashier, catalogue, patient-registry, identity, spring web/data-jpa/validation/security, lombok, test.)
- [ ] **Step 2: `EmergencyAutoConfig.java`** — mirror `premature/.../PrematureAutoConfig.java`, package `com.albudoor.hms.emergency`, scanning `com.albudoor.hms.emergency`, `.emergency.domain`, `.emergency.infrastructure`.
- [ ] **Step 3: root `backend/pom.xml`** — add `<module>emergency</module>` before `<module>app</module>`; add the `emergency` entry to `<dependencyManagement>` (mirror the `premature` entry).
- [ ] **Step 4: `backend/app/pom.xml`** — add the `emergency` dependency (mirror the `premature` one).
- [ ] **Step 5: `HmsApplication.java`** — add `import com.albudoor.hms.emergency.EmergencyAutoConfig;` and add `EmergencyAutoConfig.class` to the `@Import({...})` list.
- [ ] **Step 6: Compile** `cd backend && JAVA_HOME=/usr/lib/jvm/java-25-openjdk mvn -q -pl app -am test-compile` → BUILD SUCCESS.
- [ ] **Step 7: Commit**
```bash
cd /home/kira/Documents/javaFreelance
git add backend/pom.xml backend/emergency/pom.xml backend/emergency/src/main/java backend/app/pom.xml backend/app/src/main/java/com/albudoor/hms/app/HmsApplication.java
git commit -m "feat(emergency): scaffold emergency module + wire into app"
```

---

# PHASE 2 — V021 migration

### Task 2.1: `V021__emergency_init.sql`
**Files:** Create `backend/emergency/src/main/resources/db/migration/V021__emergency_init.sql`

- [ ] **Step 1: Write the migration**
```sql
-- HMS-BRD-REC-004 Emergency admission spine: bed inventory + bed-stay case.

CREATE TABLE emerg_bed (
    id          UUID PRIMARY KEY,
    code        VARCHAR(30)  NOT NULL,
    room        VARCHAR(100),
    status      VARCHAR(20)  NOT NULL DEFAULT 'AVAILABLE',
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    version     BIGINT       NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL,
    created_by  VARCHAR(100),
    updated_at  TIMESTAMPTZ,
    updated_by  VARCHAR(100),
    CONSTRAINT uk_emerg_bed_code UNIQUE (code),
    CONSTRAINT chk_emerg_bed_status CHECK (status IN ('AVAILABLE', 'PENDING_PAYMENT', 'OCCUPIED'))
);

CREATE TABLE emerg_case (
    id                    UUID PRIMARY KEY,
    visit_id              UUID         NOT NULL,
    visit_display_id      VARCHAR(30)  NOT NULL,
    patient_id            UUID         NOT NULL,
    patient_mrn           VARCHAR(30)  NOT NULL,
    patient_name          VARCHAR(300) NOT NULL,
    bed_id                UUID         NOT NULL,
    bed_code              VARCHAR(30)  NOT NULL,
    service_item_id       UUID         NOT NULL,
    service_code          VARCHAR(50)  NOT NULL,
    service_name          VARCHAR(300) NOT NULL,
    status                VARCHAR(30)  NOT NULL,
    stay_value            INTEGER      NOT NULL,
    stay_unit             VARCHAR(10)  NOT NULL,
    admitted_at           TIMESTAMPTZ  NOT NULL,
    stay_expires_at       TIMESTAMPTZ  NOT NULL,
    treatment_finished_at TIMESTAMPTZ,
    closed_at             TIMESTAMPTZ,
    initial_payment_id    UUID,
    final_payment_id      UUID,
    version               BIGINT       NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ  NOT NULL,
    created_by            VARCHAR(100),
    updated_at            TIMESTAMPTZ,
    updated_by            VARCHAR(100),
    CONSTRAINT uk_emerg_case_visit UNIQUE (visit_id),
    CONSTRAINT fk_emerg_case_bed FOREIGN KEY (bed_id) REFERENCES emerg_bed(id),
    CONSTRAINT chk_emerg_case_status CHECK (status IN
        ('AWAITING_INITIAL_PAYMENT', 'UNDER_TREATMENT', 'TREATMENT_FINISHED',
         'AWAITING_DISCHARGE_PAYMENT', 'CLOSED', 'CANCELLED')),
    CONSTRAINT chk_emerg_case_stay_unit CHECK (stay_unit IN ('HOURS', 'DAYS'))
);
CREATE INDEX idx_emerg_case_status ON emerg_case (status);
CREATE INDEX idx_emerg_case_bed ON emerg_case (bed_id);
CREATE INDEX idx_emerg_case_initial_payment ON emerg_case (initial_payment_id);
CREATE INDEX idx_emerg_case_final_payment ON emerg_case (final_payment_id);

-- Dedicated discharge fee item (admin-configurable; default 0), billed for the FINAL payment
-- at Finish Treatment — mirrors premature's PREM-DIS. The EMERGENCY category + 40 service items
-- already exist from V004.
INSERT INTO service_item (id, category, code, name_en, name_ar, fee, currency, sort_order, active, forward_to, created_at, created_by) VALUES
(gen_random_uuid(), 'EMERGENCY', 'EM-DISCHARGE', 'Emergency Discharge', 'خروج الطوارئ', 0, 'IQD', 99, TRUE, NULL, NOW(), 'flyway');

INSERT INTO emerg_bed (id, code, room, status, active, created_at, created_by) VALUES
(gen_random_uuid(), 'EMRG-01', 'Bay 1', 'AVAILABLE', TRUE, NOW(), 'flyway'),
(gen_random_uuid(), 'EMRG-02', 'Bay 1', 'AVAILABLE', TRUE, NOW(), 'flyway'),
(gen_random_uuid(), 'EMRG-03', 'Bay 1', 'AVAILABLE', TRUE, NOW(), 'flyway'),
(gen_random_uuid(), 'EMRG-04', 'Bay 2', 'AVAILABLE', TRUE, NOW(), 'flyway'),
(gen_random_uuid(), 'EMRG-05', 'Bay 2', 'AVAILABLE', TRUE, NOW(), 'flyway'),
(gen_random_uuid(), 'EMRG-06', 'Bay 2', 'AVAILABLE', TRUE, NOW(), 'flyway'),
(gen_random_uuid(), 'EMRG-07', 'Resus', 'AVAILABLE', TRUE, NOW(), 'flyway'),
(gen_random_uuid(), 'EMRG-08', 'Resus', 'AVAILABLE', TRUE, NOW(), 'flyway');
```
- [ ] **Step 2: Verify Flyway applies** `cd backend && JAVA_HOME=/usr/lib/jvm/java-25-openjdk mvn -q -pl app -am test -Dtest=HmsApplicationTests -Dsurefire.failIfNoSpecifiedTests=false` → PASS, "now at version v021".
- [ ] **Step 3: Commit** `git add backend/emergency/src/main/resources/db/migration/V021__emergency_init.sql && git commit -m "feat(emergency): V021 schema — beds + cases"`

---

# PHASE 3 — EmergencyBed aggregate (mirror)

### Task 3.1: `BedStatus`, `EmergencyBed`, `EmergencyBedRepository` + unit test
**Mirror** premature's `domain/BedStatus.java`, `domain/Bed.java`, `infrastructure/BedRepository.java`, `src/test/.../domain/BedTest.java` applying the rename map (`Bed`→`EmergencyBed`, table `prem_bed`→`emerg_bed`, package `premature`→`emergency`, `BedTest`→`EmergencyBedTest`). `EmergencyBed` is otherwise identical (status AVAILABLE/PENDING_PAYMENT/OCCUPIED; `create/reserve/occupy/release/discharge/updateDetails/deactivate`). `EmergencyBedRepository`: `existsByCode`, `findByCode`, `findAllByOrderByCodeAsc`.

- [ ] **Step 1:** Read `backend/premature/src/main/java/com/albudoor/hms/premature/domain/Bed.java`, `BedStatus.java`, `infrastructure/BedRepository.java`, and `src/test/java/com/albudoor/hms/premature/domain/BedTest.java`. Reproduce each as the emergency equivalent with the rename map. (Keep the 7 `EmergencyBedTest` cases byte-for-byte modulo `Bed`→`EmergencyBed`.)
- [ ] **Step 2: Run** `JAVA_HOME=/usr/lib/jvm/java-25-openjdk mvn -q -pl emergency -am test -Dtest=EmergencyBedTest -Dsurefire.failIfNoSpecifiedTests=false` → 7 pass.
- [ ] **Step 3: Commit** `git add backend/emergency/src/main/java/com/albudoor/hms/emergency/domain/EmergencyBed.java backend/emergency/src/main/java/com/albudoor/hms/emergency/domain/BedStatus.java backend/emergency/src/main/java/com/albudoor/hms/emergency/infrastructure/EmergencyBedRepository.java backend/emergency/src/test/java/com/albudoor/hms/emergency/domain/EmergencyBedTest.java && git commit -m "feat(emergency): EmergencyBed aggregate + repository with unit tests"`

---

# PHASE 4 — Bed admin CRUD + IT (mirror)

### Task 4.1: `BedResponse` + createbed/updatebed/listbeds slices
**Mirror** premature's `api/BedResponse.java` (rename `Occupant.admissionId`→`caseId`; keep occupant fields) and the `createbed`/`updatebed`/`listbeds` slices, rename map applied, base path `/api/emergency/beds`, roles `ADMIN`/`EMERGENCY_STAFF`. The first `ListBedsHandler` returns beds without occupants (occupant enrichment added in Phase 7, mirroring premature).
- [ ] **Step 1:** Read the premature `api/BedResponse.java`, `createbed/*`, `updatebed/*`, `listbeds/*`; reproduce for emergency. In `BedResponse.Occupant`, name the case reference `caseId` (UUID) and keep `visitId, visitDisplayId, patientName, patientMrn, caseStatus, stayExpiresAt`.
- [ ] **Step 2: Compile** `mvn -q -pl emergency -am test-compile` → SUCCESS. Commit `git add backend/emergency/src/main/java/com/albudoor/hms/emergency/api/BedResponse.java backend/emergency/src/main/java/com/albudoor/hms/emergency/createbed backend/emergency/src/main/java/com/albudoor/hms/emergency/updatebed backend/emergency/src/main/java/com/albudoor/hms/emergency/listbeds && git commit -m "feat(emergency): bed admin CRUD slices"`

### Task 4.2: Bed admin IT
**Mirror** `backend/app/src/test/java/com/albudoor/hms/app/premature/BedAdminIT.java` → `.../emergency/BedAdminIT.java` (autowire emergency CreateBedHandler/UpdateBedHandler/EmergencyBedRepository; seeded bed `EMRG-01`).
- [ ] **Step 1:** Reproduce the IT for emergency. **Step 2: Run** `mvn -q -pl app -am test -Dtest=BedAdminIT -Dsurefire.failIfNoSpecifiedTests=false`. NOTE there is also a premature `BedAdminIT`; run the emergency one specifically by its package or rename the class is unique enough (it's a different package — `-Dtest='com.albudoor.hms.app.emergency.BedAdminIT'`). → 3 pass. **Step 3: Commit** `git commit -m "test(emergency): bed admin integration tests"`.

---

# PHASE 5 — EmergencyCase aggregate

### Task 5.1: `EmergencyCaseStatus`, `StayUnit`, `EmergencyCase`, repo + unit test
**Files:** Create `domain/EmergencyCaseStatus.java`, `domain/StayUnit.java`, `domain/EmergencyCase.java`, `infrastructure/EmergencyCaseRepository.java`; Test `src/test/.../domain/EmergencyCaseTest.java`.

`StayUnit`: mirror premature's `domain/StayUnit.java` exactly (HOURS/DAYS + `chronoUnit()`), package emergency.

- [ ] **Step 1: `domain/EmergencyCaseStatus.java`**
```java
package com.albudoor.hms.emergency.domain;

public enum EmergencyCaseStatus {
    AWAITING_INITIAL_PAYMENT,
    UNDER_TREATMENT,
    TREATMENT_FINISHED,
    AWAITING_DISCHARGE_PAYMENT,
    CLOSED,
    CANCELLED
}
```

- [ ] **Step 2: Write the failing test `EmergencyCaseTest.java`** (mirror premature's PrematureAdmissionTest, renamed + UNDER_TREATMENT + service fields in `open`):
```java
package com.albudoor.hms.emergency.domain;

import com.albudoor.hms.platform.exception.DomainException;
import org.junit.jupiter.api.Test;

import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmergencyCaseTest {

    private EmergencyCase open(StayUnit unit, int value) {
        return EmergencyCase.open(
                UUID.randomUUID(), "VST-2026-000200",
                UUID.randomUUID(), "ALB-2026-000200", "Adult Test",
                UUID.randomUUID(), "EMRG-01",
                UUID.randomUUID(), "EM-001", "Emergency Admission",
                value, unit);
    }

    @Test
    void open_starts_awaiting_payment_with_expiry_and_service() {
        EmergencyCase c = open(StayUnit.HOURS, 6);
        assertThat(c.getStatus()).isEqualTo(EmergencyCaseStatus.AWAITING_INITIAL_PAYMENT);
        assertThat(c.getServiceCode()).isEqualTo("EM-001");
        assertThat(c.getStayExpiresAt()).isEqualTo(c.getAdmittedAt().plus(6, ChronoUnit.HOURS));
    }

    @Test
    void open_requires_positive_stay_and_service() {
        assertThatThrownBy(() -> open(StayUnit.HOURS, 0)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> EmergencyCase.open(UUID.randomUUID(), "V", UUID.randomUUID(), "M", "N",
                UUID.randomUUID(), "EMRG-01", null, null, null, 6, StayUnit.HOURS))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void full_happy_path() {
        EmergencyCase c = open(StayUnit.DAYS, 1);
        c.linkInitialPayment(UUID.randomUUID());
        c.markUnderTreatment();
        assertThat(c.getStatus()).isEqualTo(EmergencyCaseStatus.UNDER_TREATMENT);
        c.finishTreatment();
        assertThat(c.getStatus()).isEqualTo(EmergencyCaseStatus.TREATMENT_FINISHED);
        assertThat(c.getTreatmentFinishedAt()).isNotNull();
        c.scheduleDischargePayment(UUID.randomUUID());
        assertThat(c.getStatus()).isEqualTo(EmergencyCaseStatus.AWAITING_DISCHARGE_PAYMENT);
        c.close();
        assertThat(c.getStatus()).isEqualTo(EmergencyCaseStatus.CLOSED);
        assertThat(c.getClosedAt()).isNotNull();
    }

    @Test
    void extend_pushes_expiry_only_under_treatment() {
        EmergencyCase c = open(StayUnit.DAYS, 1);
        assertThatThrownBy(() -> c.extendStay(1, StayUnit.DAYS)).isInstanceOf(DomainException.class);
        c.linkInitialPayment(UUID.randomUUID());
        c.markUnderTreatment();
        var before = c.getStayExpiresAt();
        c.extendStay(1, StayUnit.DAYS);
        assertThat(c.getStayExpiresAt()).isEqualTo(before.plus(1, ChronoUnit.DAYS));
    }

    @Test
    void cancel_only_from_awaiting_initial_payment() {
        EmergencyCase c = open(StayUnit.DAYS, 1);
        c.cancel();
        assertThat(c.getStatus()).isEqualTo(EmergencyCaseStatus.CANCELLED);
    }

    @Test
    void reissue_from_awaiting_discharge_payment() {
        EmergencyCase c = open(StayUnit.DAYS, 1);
        c.linkInitialPayment(UUID.randomUUID());
        c.markUnderTreatment();
        c.finishTreatment();
        c.scheduleDischargePayment(UUID.randomUUID());
        UUID p2 = UUID.randomUUID();
        c.reissueDischargePayment(p2);
        assertThat(c.getStatus()).isEqualTo(EmergencyCaseStatus.AWAITING_DISCHARGE_PAYMENT);
        assertThat(c.getFinalPaymentId()).isEqualTo(p2);
    }

    @Test
    void cannot_finish_unless_under_treatment() {
        EmergencyCase c = open(StayUnit.DAYS, 1);
        assertThatThrownBy(c::finishTreatment).isInstanceOf(DomainException.class);
    }
}
```

- [ ] **Step 3: Run — expect FAIL.**

- [ ] **Step 4: `domain/EmergencyCase.java`** (mirror premature `PrematureAdmission` + service fields + UNDER_TREATMENT + reissue):
```java
package com.albudoor.hms.emergency.domain;

import com.albudoor.hms.platform.domain.AggregateRoot;
import com.albudoor.hms.platform.exception.DomainException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "emerg_case")
public class EmergencyCase extends AggregateRoot {

    @Id private UUID id;
    @Column(name = "visit_id", nullable = false) private UUID visitId;
    @Column(name = "visit_display_id", nullable = false, length = 30) private String visitDisplayId;
    @Column(name = "patient_id", nullable = false) private UUID patientId;
    @Column(name = "patient_mrn", nullable = false, length = 30) private String patientMrn;
    @Column(name = "patient_name", nullable = false, length = 300) private String patientName;
    @Column(name = "bed_id", nullable = false) private UUID bedId;
    @Column(name = "bed_code", nullable = false, length = 30) private String bedCode;
    @Column(name = "service_item_id", nullable = false) private UUID serviceItemId;
    @Column(name = "service_code", nullable = false, length = 50) private String serviceCode;
    @Column(name = "service_name", nullable = false, length = 300) private String serviceName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30) private EmergencyCaseStatus status;

    @Column(name = "stay_value", nullable = false) private int stayValue;
    @Enumerated(EnumType.STRING)
    @Column(name = "stay_unit", nullable = false, length = 10) private StayUnit stayUnit;
    @Column(name = "admitted_at", nullable = false) private Instant admittedAt;
    @Column(name = "stay_expires_at", nullable = false) private Instant stayExpiresAt;
    @Column(name = "treatment_finished_at") private Instant treatmentFinishedAt;
    @Column(name = "closed_at") private Instant closedAt;
    @Column(name = "initial_payment_id") private UUID initialPaymentId;
    @Column(name = "final_payment_id") private UUID finalPaymentId;

    public static EmergencyCase open(
            UUID visitId, String visitDisplayId,
            UUID patientId, String patientMrn, String patientName,
            UUID bedId, String bedCode,
            UUID serviceItemId, String serviceCode, String serviceName,
            int stayValue, StayUnit stayUnit
    ) {
        if (visitId == null || patientId == null || bedId == null) {
            throw new DomainException("CASE_REFS_REQUIRED", "visit, patient and bed are required");
        }
        if (serviceItemId == null || serviceCode == null || serviceName == null) {
            throw new DomainException("SERVICE_REQUIRED", "an emergency service type is required");
        }
        if (stayUnit == null) throw new DomainException("STAY_UNIT_REQUIRED", "stay unit is required");
        if (stayValue <= 0) throw new DomainException("STAY_VALUE_INVALID", "stay value must be positive");
        EmergencyCase c = new EmergencyCase();
        c.id = UUID.randomUUID();
        c.visitId = visitId; c.visitDisplayId = visitDisplayId;
        c.patientId = patientId; c.patientMrn = patientMrn; c.patientName = patientName;
        c.bedId = bedId; c.bedCode = bedCode;
        c.serviceItemId = serviceItemId; c.serviceCode = serviceCode; c.serviceName = serviceName;
        c.status = EmergencyCaseStatus.AWAITING_INITIAL_PAYMENT;
        c.stayValue = stayValue; c.stayUnit = stayUnit;
        c.admittedAt = Instant.now();
        c.stayExpiresAt = c.admittedAt.plus(stayValue, stayUnit.chronoUnit());
        return c;
    }

    public void linkInitialPayment(UUID paymentId) { this.initialPaymentId = paymentId; }

    public void markUnderTreatment() {
        require(EmergencyCaseStatus.AWAITING_INITIAL_PAYMENT, "mark under treatment");
        this.status = EmergencyCaseStatus.UNDER_TREATMENT;
    }

    public void cancel() {
        require(EmergencyCaseStatus.AWAITING_INITIAL_PAYMENT, "cancel");
        this.status = EmergencyCaseStatus.CANCELLED;
    }

    public void extendStay(int value, StayUnit unit) {
        if (status != EmergencyCaseStatus.UNDER_TREATMENT && status != EmergencyCaseStatus.TREATMENT_FINISHED) {
            throw new DomainException("CASE_NOT_EXTENDABLE",
                    "Can only extend while UNDER_TREATMENT or TREATMENT_FINISHED (status=" + status + ")");
        }
        if (value <= 0 || unit == null) throw new DomainException("STAY_VALUE_INVALID", "extension must be positive with a unit");
        this.stayExpiresAt = this.stayExpiresAt.plus(value, unit.chronoUnit());
    }

    public void finishTreatment() {
        require(EmergencyCaseStatus.UNDER_TREATMENT, "finish treatment");
        this.status = EmergencyCaseStatus.TREATMENT_FINISHED;
        this.treatmentFinishedAt = Instant.now();
    }

    public void scheduleDischargePayment(UUID finalPaymentId) {
        require(EmergencyCaseStatus.TREATMENT_FINISHED, "schedule discharge payment");
        this.finalPaymentId = finalPaymentId;
        this.status = EmergencyCaseStatus.AWAITING_DISCHARGE_PAYMENT;
    }

    public void reissueDischargePayment(UUID newFinalPaymentId) {
        require(EmergencyCaseStatus.AWAITING_DISCHARGE_PAYMENT, "re-issue discharge payment");
        this.finalPaymentId = newFinalPaymentId;
    }

    public void close() {
        require(EmergencyCaseStatus.AWAITING_DISCHARGE_PAYMENT, "close");
        this.status = EmergencyCaseStatus.CLOSED;
        this.closedAt = Instant.now();
    }

    private void require(EmergencyCaseStatus expected, String action) {
        if (this.status != expected) {
            throw new DomainException("CASE_INVALID_STATE",
                    "Cannot " + action + " — case is " + this.status + ", expected " + expected);
        }
    }
}
```

- [ ] **Step 5: `infrastructure/EmergencyCaseRepository.java`**
```java
package com.albudoor.hms.emergency.infrastructure;

import com.albudoor.hms.emergency.domain.EmergencyCase;
import com.albudoor.hms.emergency.domain.EmergencyCaseStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmergencyCaseRepository extends JpaRepository<EmergencyCase, UUID> {
    Optional<EmergencyCase> findByVisitId(UUID visitId);
    Optional<EmergencyCase> findByInitialPaymentId(UUID paymentId);
    Optional<EmergencyCase> findByFinalPaymentId(UUID paymentId);
    Optional<EmergencyCase> findByBedIdAndStatusIn(UUID bedId, List<EmergencyCaseStatus> statuses);
    List<EmergencyCase> findAllByStatusInOrderByAdmittedAtDesc(List<EmergencyCaseStatus> statuses);
}
```

- [ ] **Step 6: Run** `mvn -q -pl emergency -am test -Dtest=EmergencyCaseTest -Dsurefire.failIfNoSpecifiedTests=false` → 7 pass.
- [ ] **Step 7: Commit** `git add backend/emergency/src/main/java/com/albudoor/hms/emergency/domain/EmergencyCase.java backend/emergency/src/main/java/com/albudoor/hms/emergency/domain/EmergencyCaseStatus.java backend/emergency/src/main/java/com/albudoor/hms/emergency/domain/StayUnit.java backend/emergency/src/main/java/com/albudoor/hms/emergency/infrastructure/EmergencyCaseRepository.java backend/emergency/src/test/java/com/albudoor/hms/emergency/domain/EmergencyCaseTest.java && git commit -m "feat(emergency): EmergencyCase aggregate + repository with unit tests"`

---

# PHASE 6 — Services list + admit slice + bridge (initial) + IT

### Task 6.1: `EmergencyServiceResponse` + `listservices` slice
**Files:** Create `api/EmergencyServiceResponse.java`, `listservices/ListServicesHandler.java`, `listservices/ListServicesController.java`.

- [ ] **Step 1: `api/EmergencyServiceResponse.java`**
```java
package com.albudoor.hms.emergency.api;

import com.albudoor.hms.catalogue.domain.ServiceItem;

import java.math.BigDecimal;
import java.util.UUID;

public record EmergencyServiceResponse(UUID id, String code, String nameEn, String nameAr,
                                       BigDecimal fee, String currency) {
    public static EmergencyServiceResponse from(ServiceItem s) {
        return new EmergencyServiceResponse(s.getId(), s.getCode(), s.getNameEn(), s.getNameAr(),
                s.getFee(), s.getCurrency());
    }
}
```
- [ ] **Step 2: `listservices/ListServicesHandler.java`** (active EMERGENCY items, billable = forward_to null)
```java
package com.albudoor.hms.emergency.listservices;

import com.albudoor.hms.catalogue.domain.ServiceCategory;
import com.albudoor.hms.catalogue.infrastructure.ServiceItemRepository;
import com.albudoor.hms.emergency.api.EmergencyServiceResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ListServicesHandler {
    private final ServiceItemRepository catalogue;
    public ListServicesHandler(ServiceItemRepository catalogue) { this.catalogue = catalogue; }

    @Transactional(readOnly = true)
    public List<EmergencyServiceResponse> list() {
        return catalogue.findAllByCategoryAndActiveOrderBySortOrderAscNameEnAsc(ServiceCategory.EMERGENCY, true)
                .stream().filter(s -> s.getForwardTo() == null)
                .map(EmergencyServiceResponse::from).toList();
    }
}
```
- [ ] **Step 3: `listservices/ListServicesController.java`**
```java
package com.albudoor.hms.emergency.listservices;

import com.albudoor.hms.emergency.api.EmergencyServiceResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/emergency/services")
@PreAuthorize("isAuthenticated()")
public class ListServicesController {
    private final ListServicesHandler handler;
    public ListServicesController(ListServicesHandler handler) { this.handler = handler; }
    @GetMapping public List<EmergencyServiceResponse> list() { return handler.list(); }
}
```
- [ ] **Step 4: Compile + commit** `git add backend/emergency/src/main/java/com/albudoor/hms/emergency/api/EmergencyServiceResponse.java backend/emergency/src/main/java/com/albudoor/hms/emergency/listservices && git commit -m "feat(emergency): list billable emergency services"`

### Task 6.2: `CaseResponse` + admit slice (bills the selected service)
**Files:** Create `api/CaseResponse.java`, `admitpatient/{AdmitPatientCommand,AdmitPatientHandler,AdmitPatientController}.java`.

- [ ] **Step 1: `api/CaseResponse.java`** (mirror premature AdmissionResponse + service fields)
```java
package com.albudoor.hms.emergency.api;

import com.albudoor.hms.emergency.domain.EmergencyCase;

import java.time.Instant;
import java.util.UUID;

public record CaseResponse(
        UUID id, UUID visitId, String visitDisplayId,
        UUID patientId, String patientMrn, String patientName,
        UUID bedId, String bedCode,
        UUID serviceItemId, String serviceCode, String serviceName,
        String status, int stayValue, String stayUnit,
        Instant admittedAt, Instant stayExpiresAt, Instant treatmentFinishedAt, Instant closedAt,
        UUID initialPaymentId, UUID finalPaymentId
) {
    public static CaseResponse from(EmergencyCase c) {
        return new CaseResponse(c.getId(), c.getVisitId(), c.getVisitDisplayId(),
                c.getPatientId(), c.getPatientMrn(), c.getPatientName(),
                c.getBedId(), c.getBedCode(),
                c.getServiceItemId(), c.getServiceCode(), c.getServiceName(),
                c.getStatus().name(), c.getStayValue(), c.getStayUnit().name(),
                c.getAdmittedAt(), c.getStayExpiresAt(), c.getTreatmentFinishedAt(), c.getClosedAt(),
                c.getInitialPaymentId(), c.getFinalPaymentId());
    }
}
```
- [ ] **Step 2: `admitpatient/AdmitPatientCommand.java`**
```java
package com.albudoor.hms.emergency.admitpatient;

import com.albudoor.hms.emergency.domain.StayUnit;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record AdmitPatientCommand(
        @NotNull UUID visitId,
        @NotNull UUID bedId,
        @NotNull UUID serviceItemId,
        @Positive int stayValue,
        @NotNull StayUnit stayUnit
) {}
```
- [ ] **Step 3: `admitpatient/AdmitPatientHandler.java`** (validate visit EMERGENCY+CREATED, bed AVAILABLE, service = active non-forward EMERGENCY item; reserve bed; create case; bill the service as INITIAL payment; visit→AWAITING_PAYMENT)
```java
package com.albudoor.hms.emergency.admitpatient;

import com.albudoor.hms.cashier.createpayment.CreatePaymentCommand;
import com.albudoor.hms.cashier.createpayment.CreatePaymentHandler;
import com.albudoor.hms.cashier.domain.Payment;
import com.albudoor.hms.cashier.domain.PaymentStage;
import com.albudoor.hms.catalogue.domain.ServiceCategory;
import com.albudoor.hms.catalogue.domain.ServiceItem;
import com.albudoor.hms.catalogue.infrastructure.ServiceItemRepository;
import com.albudoor.hms.emergency.domain.EmergencyBed;
import com.albudoor.hms.emergency.domain.EmergencyCase;
import com.albudoor.hms.emergency.infrastructure.EmergencyBedRepository;
import com.albudoor.hms.emergency.infrastructure.EmergencyCaseRepository;
import com.albudoor.hms.platform.exception.DomainException;
import com.albudoor.hms.platform.exception.NotFoundException;
import com.albudoor.hms.visitmanagement.domain.Visit;
import com.albudoor.hms.visitmanagement.domain.VisitStatus;
import com.albudoor.hms.visitmanagement.domain.VisitType;
import com.albudoor.hms.visitmanagement.infrastructure.VisitRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AdmitPatientHandler {

    private final EmergencyCaseRepository cases;
    private final EmergencyBedRepository beds;
    private final VisitRepository visits;
    private final ServiceItemRepository catalogue;
    private final CreatePaymentHandler createPayment;
    private final ApplicationEventPublisher events;

    public AdmitPatientHandler(EmergencyCaseRepository cases, EmergencyBedRepository beds,
                               VisitRepository visits, ServiceItemRepository catalogue,
                               CreatePaymentHandler createPayment, ApplicationEventPublisher events) {
        this.cases = cases; this.beds = beds; this.visits = visits;
        this.catalogue = catalogue; this.createPayment = createPayment; this.events = events;
    }

    @Transactional
    public EmergencyCase handle(AdmitPatientCommand cmd) {
        Visit visit = visits.findById(cmd.visitId())
                .orElseThrow(() -> new NotFoundException("Visit not found: " + cmd.visitId()));
        if (visit.getVisitType() != VisitType.EMERGENCY) {
            throw new DomainException("NOT_EMERGENCY_VISIT", "Visit is not an EMERGENCY visit");
        }
        if (visit.getStatus() != VisitStatus.CREATED) {
            throw new DomainException("VISIT_NOT_ADMITTABLE", "Visit must be CREATED to admit (status=" + visit.getStatus() + ")");
        }
        if (cases.findByVisitId(visit.getId()).isPresent()) {
            throw new DomainException("ALREADY_ADMITTED", "Visit already has an emergency case");
        }
        ServiceItem service = catalogue.findById(cmd.serviceItemId())
                .orElseThrow(() -> new NotFoundException("Service not found: " + cmd.serviceItemId()));
        if (service.getCategory() != ServiceCategory.EMERGENCY || !service.isActive() || service.getForwardTo() != null) {
            throw new DomainException("INVALID_EMERGENCY_SERVICE", "Not a billable emergency service: " + cmd.serviceItemId());
        }

        EmergencyBed bed = beds.findById(cmd.bedId())
                .orElseThrow(() -> new NotFoundException("Bed not found: " + cmd.bedId()));
        bed.reserve();
        beds.save(bed);

        EmergencyCase ec = EmergencyCase.open(
                visit.getId(), visit.getVisitDisplayId(),
                visit.getPatientId(), visit.getPatientMrn(), visit.getPatientName(),
                bed.getId(), bed.getCode(),
                service.getId(), service.getCode(), service.getNameEn(),
                cmd.stayValue(), cmd.stayUnit());
        cases.save(ec);

        Payment payment = createPayment.handle(new CreatePaymentCommand(
                visit.getId(), PaymentStage.INITIAL,
                List.of(new CreatePaymentCommand.Line(service.getId(), 1)), null));
        ec.linkInitialPayment(payment.getId());
        cases.save(ec);

        visit.transitionTo(VisitStatus.AWAITING_PAYMENT);
        visit.pullDomainEvents().forEach(events::publishEvent);
        return ec;
    }
}
```
- [ ] **Step 4: `admitpatient/AdmitPatientController.java`**
```java
package com.albudoor.hms.emergency.admitpatient;

import com.albudoor.hms.emergency.api.CaseResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/emergency/cases")
public class AdmitPatientController {
    private final AdmitPatientHandler handler;
    public AdmitPatientController(AdmitPatientHandler handler) { this.handler = handler; }

    @PostMapping
    @PreAuthorize("hasAnyRole('EMERGENCY_STAFF', 'ADMIN')")
    public ResponseEntity<CaseResponse> admit(@Valid @RequestBody AdmitPatientCommand cmd) {
        return ResponseEntity.status(HttpStatus.CREATED).body(CaseResponse.from(handler.handle(cmd)));
    }
}
```
- [ ] **Step 5: Compile + commit** `git add backend/emergency/src/main/java/com/albudoor/hms/emergency/api/CaseResponse.java backend/emergency/src/main/java/com/albudoor/hms/emergency/admitpatient && git commit -m "feat(emergency): admit slice (bed + service + initial payment)"`

### Task 6.3: `EmergencyPaymentBridge` (initial approve/reject)
**Mirror** premature `bridge/PrematurePaymentBridge.java` → `emergency/bridge/EmergencyPaymentBridge.java`, rename map, using `cases.findByInitialPaymentId`, `case.markUnderTreatment()`/`cancel()`, `bed.occupy()`/`release()`, and `visits` to cancel the visit on initial reject. (FINAL approve/reject added in Phase 7.) Read the premature bridge and reproduce.
- [ ] **Step 1:** Reproduce the bridge for emergency (initial INITIAL-approve → `markUnderTreatment` + `bed.occupy`; INITIAL-reject → `bed.release` + `case.cancel` + `visit.cancel`). **Step 2: Compile.** **Step 3: Commit** `git add backend/emergency/src/main/java/com/albudoor/hms/emergency/bridge/EmergencyPaymentBridge.java && git commit -m "feat(emergency): payment bridge — initial approve/reject"`

### Task 6.4: Admit-flow IT
**Mirror** premature `app/.../premature/AdmitFlowIT.java` → `.../emergency/AdmitFlowIT.java`. Differences: seed an EMERGENCY visit; pick a billable service id via `GET /api/emergency/services` (first item); admit posts `{visitId, bedId, serviceItemId, stayValue, stayUnit:"HOURS"}`; assert case `UNDER_TREATMENT`, bed `OCCUPIED`, visit `IN_PROGRESS` on approve; `CANCELLED`/`AVAILABLE`/`CANCELLED` on reject. Create a bed via `EmergencyBedRepository.save(EmergencyBed.create(...))`.
- [ ] **Step 1:** Reproduce the IT for emergency (use the `auth/post` helper pattern; for the service id, GET `/api/emergency/services` as `emergency` and take `[0].id`). **Step 2: Run** `mvn -q -pl app -am test -Dtest='com.albudoor.hms.app.emergency.AdmitFlowIT' -Dsurefire.failIfNoSpecifiedTests=false` → 2 pass. **Step 3: Commit** `git commit -m "test(emergency): admit + initial-payment flow IT"`

---

# PHASE 7 — Extend/finish/reissue/list + discharge bridge + IT

### Task 7.1: extend-stay, finish-treatment, reissue, list-cases slices + bed-dashboard occupant
**Mirror** premature's `extendstay/`, `finishtreatment/`, `reissuedischargepayment/`, `listadmissions/` (→ `listcases/`), and the occupant-enriched `listbeds/ListBedsHandler.java`, with the rename map. The **FINAL** discharge payment bills the dedicated `EM-DISCHARGE` catalogue item (seeded in V021, Phase 2) via `catalogue.findByCategoryAndCode(ServiceCategory.EMERGENCY, "EM-DISCHARGE")` — exactly mirroring premature's `PREM-DIS`. (Do NOT re-bill the admission service; do NOT edit the already-applied V021.)
- [ ] **Step 1:** Reproduce `extendstay/` (path `/api/emergency/cases/{id}/extend-stay`, roles EMERGENCY_STAFF/NURSE/DOCTOR/ADMIN), `reissuedischargepayment/` (bills `EM-DISCHARGE` FINAL, `case.reissueDischargePayment`), `listcases/` (`GET /api/emergency/cases?status=`), and the occupant-enriched `listbeds/ListBedsHandler` (find active case by bed via `findByBedIdAndStatusIn`, attach `BedResponse.Occupant`).
- [ ] **Step 2: `finishtreatment/FinishTreatmentHandler.java`** — mirror premature's, but: `case.finishTreatment()`; visit→TREATMENT_FINISHED; create FINAL payment billing `catalogue.findByCategoryAndCode(ServiceCategory.EMERGENCY, "EM-DISCHARGE")`; `case.scheduleDischargePayment(payment.id)`; visit→AWAITING_FINAL_PAYMENT. (No results gate — that's EC.) Controller `POST /api/emergency/cases/{id}/finish-treatment`, roles EMERGENCY_STAFF/DOCTOR/ADMIN.
- [ ] **Step 3: Compile + commit** `git add backend/emergency/src/main/java/com/albudoor/hms/emergency/extendstay backend/emergency/src/main/java/com/albudoor/hms/emergency/finishtreatment backend/emergency/src/main/java/com/albudoor/hms/emergency/reissuedischargepayment backend/emergency/src/main/java/com/albudoor/hms/emergency/listcases backend/emergency/src/main/java/com/albudoor/hms/emergency/listbeds && git commit -m "feat(emergency): extend/finish/reissue/list-cases + bed occupants"`

### Task 7.2: FINAL approve/reject in the bridge + generic-bridge guard
- [ ] **Step 1:** Add the FINAL block to `EmergencyPaymentBridge.onApproved` (find by `finalPaymentId` → `case.close()` + `bed.discharge()`) and the P12b no-op comment in `onRejected`, mirroring premature Phase 7.3.
- [ ] **Step 2:** In `backend/app/src/main/java/com/albudoor/hms/app/PaymentVisitBridge.java`, line ~97, change the premature guard to also skip EMERGENCY:
```java
        if (visit.getVisitType() == com.albudoor.hms.visitmanagement.domain.VisitType.PREMATURE
                || visit.getVisitType() == com.albudoor.hms.visitmanagement.domain.VisitType.EMERGENCY) return;
```
- [ ] **Step 3: Compile + commit** `git add backend/emergency/src/main/java/com/albudoor/hms/emergency/bridge/EmergencyPaymentBridge.java backend/app/src/main/java/com/albudoor/hms/app/PaymentVisitBridge.java && git commit -m "feat(emergency): FINAL approve closes case; guard generic bridge for P12b"`

### Task 7.3: Discharge-flow IT
**Mirror** premature `DischargeFlowIT.java` → `.../emergency/DischargeFlowIT.java` (admit-under-treatment helper picks a service via `/api/emergency/services`; finish → FINAL payment (`EM-DISCHARGE`); approve → CLOSED/AVAILABLE/COMPLETED; reject → stays AWAITING_DISCHARGE_PAYMENT + re-issue → approve → CLOSED).
- [ ] **Step 1:** Reproduce for emergency. **Step 2: Run** `mvn -q -pl app -am test -Dtest='com.albudoor.hms.app.emergency.DischargeFlowIT' -Dsurefire.failIfNoSpecifiedTests=false` → pass. **Step 3: Commit** `git commit -m "test(emergency): discharge flow + P12b retry IT"`

---

# PHASE 8 — ArchUnit + full backend verify

### Task 8.1: `layeredWithinEmergency` + verify
- [ ] **Step 1:** In `ArchitectureTest.java`, add a `layeredWithinEmergency` test mirroring `layeredWithinPremature`, with application layer `.definedBy("..emergency.createbed..","..emergency.updatebed..","..emergency.listbeds..","..emergency.listservices..","..emergency.admitpatient..","..emergency.extendstay..","..emergency.finishtreatment..","..emergency.reissuedischargepayment..","..emergency.listcases..","..emergency.bridge..")`, domain `..emergency.domain..`, infrastructure `..emergency.infrastructure..`, `.consideringOnlyDependenciesInLayers()`.
- [ ] **Step 2: Full verify** `cd backend && JAVA_HOME=/usr/lib/jvm/java-25-openjdk mvn -pl emergency,app -am verify -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | tail -50` → BUILD SUCCESS: emergency unit tests + emergency ITs (BedAdminIT, AdmitFlowIT, DischargeFlowIT) + ArchitectureTest + all pre-existing premature ITs all green together.
- [ ] **Step 3: Commit** `git add backend/app/src/test/java/com/albudoor/hms/app/ArchitectureTest.java && git commit -m "test(emergency): ArchUnit slice-boundary rule"`

---

# PHASE 9 — Frontend (mirror premature)

### Task 9.1: `features/emergency/api.ts`
**Mirror** `frontend/src/features/premature/api.ts` (the admission-spine parts only — beds, cases, admit, extend, finish, reissue, incoming) with rename map. Additions: `EmergencyService` type + `listEmergencyServices()`; `admitPatient` body includes `serviceItemId`; `Case` type has `serviceCode`/`serviceName`. Incoming queue: `listIncomingEmergency()` → `GET /visits?type=EMERGENCY&status=CREATED&size=50`.
- [ ] **Step 1:** Reproduce `api.ts` for emergency. **Step 2:** `cd frontend && npx tsc -b` clean. **Step 3: Commit** `git add frontend/src/features/emergency/api.ts && git commit -m "feat(emergency-ui): api client"`

### Task 9.2: `EmergencyWorkspacePage.tsx` (+ bed-detail drawer + admit dialog with service select)
**Mirror** `frontend/src/features/premature/PrematureWorkspacePage.tsx` and `BedDetailPanel.tsx` with rename map. Changes: the admit dialog adds a **service `<select>`** populated from `listEmergencyServices()` (show `code — nameEn (fee currency)`); `admitPatient` passes `serviceItemId`; the drawer shows `case.serviceName` and uses case-status labels. The drawer's "Open case" link is **omitted in EA** (added in EB) — keep the extend/finish/reissue actions inline as premature's pre-case-page drawer did.
- [ ] **Step 1:** Reproduce `EmergencyWorkspacePage.tsx` + `BedDetailPanel.tsx`, adding the service dropdown to the admit dialog (required; disable confirm until a service is chosen). **Step 2:** `npx tsc -b` clean. **Step 3: Commit** `git add frontend/src/features/emergency/EmergencyWorkspacePage.tsx frontend/src/features/emergency/BedDetailPanel.tsx && git commit -m "feat(emergency-ui): workspace + bed dashboard + admit (service select) + drawer"`

### Task 9.3: `BedAdminPage.tsx`
**Mirror** premature `BedAdminPage.tsx` with rename map. **Step 1:** reproduce. **Step 2:** tsc clean. **Step 3: Commit** `git add frontend/src/features/emergency/BedAdminPage.tsx && git commit -m "feat(emergency-ui): bed admin page"`

### Task 9.4: Routes + nav + reception EMERGENCY start-visit
**Files:** `frontend/src/App.tsx`, `routes.ts`, `features/patients/PatientProfilePage.tsx`.
- [ ] **Step 1:** `App.tsx` — import `EmergencyWorkspacePage` + `BedAdminPage`; replace `<Route path="/departments/emergency" element={<ComingSoonPage />} />` with `<EmergencyWorkspacePage />`; add `<Route path="/emergency/beds" element={<BedAdminPage />} />`.
- [ ] **Step 2:** `routes.ts` — remove `comingSoon: true` from the emergency nav item; gate `roles: ['EMERGENCY_STAFF','ADMIN','NURSE','DOCTOR']`; add an `/emergency/beds` admin nav item (`roles: ['ADMIN','EMERGENCY_STAFF']`).
- [ ] **Step 3:** `PatientProfilePage.tsx` — add `'EMERGENCY'` to the start-visit `VisitType[]` array (currently `['LABORATORY','RADIOLOGY','ECO','PREMATURE']`); the `onSuccess` navigate already routes PREMATURE → premature; add `EMERGENCY` → `/departments/emergency` (others → `/reception/queue`). `TYPE_ICON`/`TYPE_LABEL` already include EMERGENCY (Siren/'Emergency').
- [ ] **Step 4:** tsc clean. **Step 5: Commit** `git add frontend/src/App.tsx frontend/src/shared/nav/routes.ts frontend/src/features/patients/PatientProfilePage.tsx && git commit -m "feat(emergency-ui): wire workspace + bed admin routes; EMERGENCY start-visit"`

### Task 9.5: i18n (en + ar)
- [ ] **Step 1:** Add an `emergency: {...}` section to BOTH `en.ts` and `ar.ts`, mirroring the `premature` keys used by the emergency workspace/drawer/bed-admin (title, subtitle, incoming, noIncoming, bedDashboard, bed, assignBed, confirmAdmit, stayValue/Unit, days/hours, finishTreatment, awaitingDischarge, reissueDischarge, expiringSoon, extend1Day, **service**, **selectService**, bedStatus.{AVAILABLE,PENDING_PAYMENT,OCCUPIED}, caseStatus.{AWAITING_INITIAL_PAYMENT,UNDER_TREATMENT,TREATMENT_FINISHED,AWAITING_DISCHARGE_PAYMENT,CLOSED,CANCELLED}, detail.*, bedAdmin/bedCode/room/addBed/activate/deactivate/inactive/status, toast.{admitted,finished,extended,reissued,bedCreated,error}). Add `nav.emergencyBeds` to both. Use the premature blocks as the template; translate Arabic. **Step 2:** `npm run build` succeeds. **Step 3: Commit** `git add frontend/src/shared/i18n/locales/en.ts frontend/src/shared/i18n/locales/ar.ts && git commit -m "feat(emergency-ui): en/ar i18n"`

---

# PHASE 10 — E2E + final verification

### Task 10.1: API + UI E2E
**Files:** Create `frontend/e2e/brd-rec-004-emergency.spec.ts` and `frontend/e2e/emergency-ui.spec.ts`; extend `frontend/e2e/helpers/auth.ts` Role union with `'emergency'` if absent (the seeded `emergency` user exists).

- [ ] **Step 1: `brd-rec-004-emergency.spec.ts`** — mirror `brd-rec-005-premature.spec.ts` with the emergency endpoints + a per-test bed created via `POST /api/emergency/beds` (as `emergency`), and a service id from `GET /api/emergency/services`. Tests:
  - R1/R4: receptionist starts an EMERGENCY visit; appears in incoming queue.
  - E1–E5a: admit (bed + service) → initial approve → bed OCCUPIED, case UNDER_TREATMENT, visit IN_PROGRESS; assert the INITIAL payment's `serviceCode`/amount matches the chosen service.
  - E5b: initial reject → bed AVAILABLE, visit CANCELLED.
  - E8–E11a: finish → final payment approve → case CLOSED, bed AVAILABLE, visit COMPLETED.
  - E11b: final reject → case stays AWAITING_DISCHARGE_PAYMENT + visit AWAITING_FINAL_PAYMENT; then reissue + approve → CLOSED.
  Use the `authedContext('emergency'|'receptionist'|'cashier')` + `registerPatient` + `POST /api/visits {visitType:'EMERGENCY'}` pattern from the premature spec.
- [ ] **Step 2: `emergency-ui.spec.ts`** — mirror `premature-ui.spec.ts`: seed an EMERGENCY visit + a fresh bed via API; login `emergency`; goto `/departments/emergency`; click "Assign bed" for the queued patient; in the admit dialog pick a **service** + stay; confirm; assert the patient leaves the queue and a bed shows "Pending payment".
- [ ] **Step 3: Verify parses** `cd frontend && npx playwright test --list brd-rec-004-emergency emergency-ui`. **Step 4: Commit** `git add frontend/e2e/helpers/auth.ts frontend/e2e/brd-rec-004-emergency.spec.ts frontend/e2e/emergency-ui.spec.ts && git commit -m "test(emergency): API + UI E2E for the admission spine"`

### Task 10.2: Full verification (controller runs the stack)
- [ ] **Step 1: Backend** `cd backend && JAVA_HOME=/usr/lib/jvm/java-25-openjdk mvn -pl emergency,app -am verify -Dsurefire.failIfNoSpecifiedTests=false` → BUILD SUCCESS.
- [ ] **Step 2: Frontend** `cd frontend && npx tsc -b && npm run build` → clean.
- [ ] **Step 3: E2E** (rebuild + restart backend with the new module, frontend dev server up): `npx playwright test` → all green incl. `brd-rec-004-emergency` + `emergency-ui`; existing premature/other specs unaffected.

## Definition of Done
`mvn -pl emergency,app -am verify` green; `tsc`/`build` clean; full Playwright suite green; a user can start an EMERGENCY visit, assign a bed + pick a service, pay (Occupied/Under-Treatment), finish, and discharge (Closed) with the P12b retry; bed dashboard + drawer + bed admin work; en/ar localized.
