# Premature Admission Spine — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the end-to-end Premature admission spine (HMS-BRD-REC-005 sub-project A): reception can start a PREMATURE visit, premature staff assign a bed + period of stay, two-stage cashier payment drives bed/case state, and discharge closes the case — all production-ready with unit, integration, and Playwright E2E tests.

**Architecture:** New `premature` bounded-context Maven module (mirrors `patient-registry`/`department-services`) owning `Bed` and `PrematureAdmission` aggregates. The generic `Visit` (visit-management) is the reception entry/queue token; `PrematureAdmission` is the source of truth for the bed-stay case and references the visit by id. Payments reuse the cashier `INITIAL`/`FINAL` stages; a `PrematurePaymentBridge` reacts to payment events to drive bed/admission state. The generic `PaymentVisitBridge` already maps INITIAL→IN_PROGRESS and FINAL→COMPLETED; we guard its FINAL-reject→OUTSTANDING_BALANCE so it doesn't apply to PREMATURE (P12b fix).

**Tech Stack:** Java 21, Spring Boot 3.3, Spring Data JPA, PostgreSQL 16 + Flyway, Maven multi-module; React 18 + TS + Vite + Tailwind + shadcn/ui + react-query + react-hook-form + zod + react-i18next; JUnit 5 + Testcontainers (backend), Playwright (E2E).

**Spec:** `docs/superpowers/specs/2026-05-29-premature-admission-spine-design.md`

---

## Conventions (apply to every backend file)

- Aggregates extend `com.albudoor.hms.platform.domain.AggregateRoot` (UUID `@Id` assigned in factory via `UUID.randomUUID()`; audit + `@Version` inherited from `BaseEntity`).
- Domain errors throw `com.albudoor.hms.platform.exception.DomainException(code, msg)` (→ HTTP 422), `NotFoundException(msg)` (→ 404), `ConflictException(code, msg)` (→ 409). Handled centrally by `GlobalExceptionHandler`.
- Handlers are `@Service` with constructor injection; `@Transactional` for writes, `@Transactional(readOnly = true)` for reads. After save, publish events: `aggregate.pullDomainEvents().forEach(events::publishEvent)` (pull from the source reference, not the saved one).
- Controllers: `@RestController`, `@RequestMapping("/api/...")`, `@PreAuthorize(...)`, `@Valid @RequestBody`, return `ResponseEntity`/DTO records with static `from(...)` mappers.
- Migrations: `src/main/resources/db/migration/V0xx__name.sql`, UUID PK, snake_case columns, audit columns (`created_at TIMESTAMPTZ NOT NULL, created_by VARCHAR(100), updated_at TIMESTAMPTZ, updated_by VARCHAR(100)`), and `version BIGINT NOT NULL DEFAULT 0` on every aggregate table.
- Commit after each task with the message shown.

## File Structure

**New backend module** `backend/premature/`:
- `pom.xml`, `src/main/java/com/albudoor/hms/premature/PrematureAutoConfig.java`
- `domain/`: `Bed.java`, `BedStatus.java`, `PrematureAdmission.java`, `AdmissionStatus.java`, `StayUnit.java`
- `infrastructure/`: `BedRepository.java`, `PrematureAdmissionRepository.java`
- `api/`: `BedResponse.java`, `AdmissionResponse.java`
- slices: `createbed/`, `updatebed/`, `listbeds/`, `admitpatient/`, `extendstay/`, `finishtreatment/`, `listadmissions/`
- `bridge/PrematurePaymentBridge.java`
- `src/main/resources/db/migration/V019__premature_init.sql`
- `src/test/java/com/albudoor/hms/premature/domain/`: `BedTest.java`, `PrematureAdmissionTest.java`

**Edited backend files:**
- `backend/pom.xml` (add module + dependencyManagement), `backend/app/pom.xml` (add dependency)
- `backend/app/src/main/java/com/albudoor/hms/app/HmsApplication.java` (add `@Import`)
- `backend/app/src/main/java/com/albudoor/hms/app/PaymentVisitBridge.java` (guard PREMATURE final-reject)
- `backend/catalogue/src/main/java/com/albudoor/hms/catalogue/domain/ServiceCategory.java` (add `PREMATURE`)
- `backend/app/src/test/java/com/albudoor/hms/app/ArchitectureTest.java` (add `layeredWithinPremature`)
- Test foundation: `backend/app/pom.xml` (testcontainers deps), `backend/app/src/test/java/com/albudoor/hms/app/IntegrationTest.java`, `backend/app/src/test/resources/application-test.yml`, enable `HmsApplicationTests`
- Integration tests: `backend/app/src/test/java/com/albudoor/hms/app/premature/{BedAdminIT,AdmitFlowIT,DischargeFlowIT}.java`

**Frontend** `frontend/src/features/premature/`: `api.ts`, `PrematureWorkspacePage.tsx`, `BedAdminPage.tsx`
**Edited frontend:** `App.tsx`, `shared/nav/routes.ts`, `features/patients/PatientProfilePage.tsx`, `shared/i18n/locales/en.ts`, `shared/i18n/locales/ar.ts`
**E2E:** `frontend/e2e/helpers/auth.ts` (extend Role), `frontend/e2e/brd-rec-005-premature.spec.ts`, `frontend/e2e/premature-ui.spec.ts`

---

# PHASE 0 — Test foundation (Testcontainers)

### Task 0.1: Add Testcontainers Postgres profile + base integration test

**Files:**
- Modify: `backend/app/pom.xml` (add test deps)
- Create: `backend/app/src/test/java/com/albudoor/hms/app/IntegrationTest.java`
- Create: `backend/app/src/test/resources/application-test.yml`
- Modify: `backend/app/src/test/java/com/albudoor/hms/app/HmsApplicationTests.java`

- [ ] **Step 1: Add Testcontainers + security-test deps to `backend/app/pom.xml`**

Inside the existing `<dependencies>` block, add (test scope):

```xml
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-testcontainers</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>postgresql</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.security</groupId>
            <artifactId>spring-security-test</artifactId>
            <scope>test</scope>
        </dependency>
```

> Versions are managed by the Spring Boot parent BOM (Boot 3.3 manages Testcontainers). No explicit `<version>` needed. If the reactor complains a version is missing, add `<dependencyManagement>` importing `org.testcontainers:testcontainers-bom:1.20.4` to the **root** `backend/pom.xml`.

- [ ] **Step 2: Create the base integration-test class**

`backend/app/src/test/java/com/albudoor/hms/app/IntegrationTest.java`:

```java
package com.albudoor.hms.app;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base for full-context integration tests. Boots the whole HMS app against a real
 * Postgres 16 container (Flyway migrates the schema), so cross-module bridges and
 * @TransactionalEventListener wiring are exercised exactly as in production.
 *
 * <p>Subclasses are intentionally NOT @Transactional: AFTER_COMMIT event listeners
 * (e.g. PaymentVisitBridge, PrematurePaymentBridge) only fire once the producing
 * transaction commits. Use unique data per test and assert on specific ids.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
public abstract class IntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");
}
```

- [ ] **Step 3: Create the test profile**

`backend/app/src/test/resources/application-test.yml`:

```yaml
# Datasource is provided by the Testcontainers @ServiceConnection. Keep Flyway on and
# ddl-auto=validate so tests run against the real migrated schema, exactly like prod.
spring:
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
  flyway:
    enabled: true
    baseline-on-migrate: true
    locations: classpath:db/migration

hms:
  security:
    jwt:
      secret: ZGV2LW9ubHktc2VjcmV0LXJlcGxhY2UtaW4tcHJvZHVjdGlvbi1tdXN0LWJlLTI1Ni1iaXRz
      ttl: PT8H
      issuer: hms.albudoor

logging:
  level:
    com.albudoor.hms: INFO
    org.testcontainers: INFO
```

- [ ] **Step 4: Enable the context-load test**

Replace `backend/app/src/test/java/com/albudoor/hms/app/HmsApplicationTests.java` with:

```java
package com.albudoor.hms.app;

import org.junit.jupiter.api.Test;

class HmsApplicationTests extends IntegrationTest {

    @Test
    void contextLoads() {
        // Boots the full application against Testcontainers Postgres + Flyway.
    }
}
```

- [ ] **Step 5: Run it (requires Docker)**

Run: `cd backend && ./mvnw -q -pl app -am test -Dtest=HmsApplicationTests`
Expected: PASS — container starts, Flyway migrates, context loads.

- [ ] **Step 6: Commit**

```bash
git add backend/app/pom.xml backend/app/src/test/java/com/albudoor/hms/app/IntegrationTest.java backend/app/src/test/resources/application-test.yml backend/app/src/test/java/com/albudoor/hms/app/HmsApplicationTests.java
git commit -m "test: add Testcontainers Postgres integration-test foundation"
```

---

# PHASE 1 — Module skeleton + catalogue PREMATURE category

### Task 1.1: Create the `premature` module and wire it in

**Files:**
- Create: `backend/premature/pom.xml`
- Create: `backend/premature/src/main/java/com/albudoor/hms/premature/PrematureAutoConfig.java`
- Modify: `backend/pom.xml`, `backend/app/pom.xml`, `backend/app/src/main/java/com/albudoor/hms/app/HmsApplication.java`
- Modify: `backend/catalogue/src/main/java/com/albudoor/hms/catalogue/domain/ServiceCategory.java`

- [ ] **Step 1: Create `backend/premature/pom.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.albudoor.hms</groupId>
        <artifactId>hms-backend</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </parent>

    <artifactId>premature</artifactId>
    <name>HMS — Premature</name>

    <dependencies>
        <dependency>
            <groupId>com.albudoor.hms</groupId>
            <artifactId>platform</artifactId>
        </dependency>
        <dependency>
            <groupId>com.albudoor.hms</groupId>
            <artifactId>visit-management</artifactId>
        </dependency>
        <dependency>
            <groupId>com.albudoor.hms</groupId>
            <artifactId>cashier</artifactId>
        </dependency>
        <dependency>
            <groupId>com.albudoor.hms</groupId>
            <artifactId>catalogue</artifactId>
        </dependency>
        <dependency>
            <groupId>com.albudoor.hms</groupId>
            <artifactId>patient-registry</artifactId>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>

        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

> `spring-boot-starter-security` is included because controllers use `@PreAuthorize`. Verify whether sibling modules (e.g. `department-services/pom.xml`) declare it; if they rely on a transitive bring-in, match that instead. Cross-checking one sibling pom before finalizing avoids a missing-bean surprise.

- [ ] **Step 2: Create `PrematureAutoConfig`**

`backend/premature/src/main/java/com/albudoor/hms/premature/PrematureAutoConfig.java`:

```java
package com.albudoor.hms.premature;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@ComponentScan(basePackages = "com.albudoor.hms.premature")
@EntityScan(basePackages = "com.albudoor.hms.premature.domain")
@EnableJpaRepositories(basePackages = "com.albudoor.hms.premature.infrastructure")
public class PrematureAutoConfig {
}
```

- [ ] **Step 3: Register module in `backend/pom.xml`**

In `<modules>`, add `<module>premature</module>` immediately before `<module>app</module>`. In `<dependencyManagement><dependencies>`, add:

```xml
            <dependency>
                <groupId>com.albudoor.hms</groupId>
                <artifactId>premature</artifactId>
                <version>${project.version}</version>
            </dependency>
```

- [ ] **Step 4: Add dependency in `backend/app/pom.xml`**

After the `clinical-case`/`pharmacy` `com.albudoor.hms` dependencies, add:

```xml
        <dependency>
            <groupId>com.albudoor.hms</groupId>
            <artifactId>premature</artifactId>
        </dependency>
```

- [ ] **Step 5: Register AutoConfig in `HmsApplication.java`**

Add import `import com.albudoor.hms.premature.PrematureAutoConfig;` and add `PrematureAutoConfig.class` as the last entry in the `@Import({...})` list.

- [ ] **Step 6: Add `PREMATURE` to `ServiceCategory`**

In `backend/catalogue/.../domain/ServiceCategory.java`, add after `DRUG`:

```java
    DRUG,
    /** Premature unit admission / discharge / stay billing (HMS-BRD-REC-005). */
    PREMATURE
```

(Move the trailing `;`/comma appropriately so the enum compiles.)

- [ ] **Step 7: Compile**

Run: `cd backend && ./mvnw -q -pl app -am test-compile`
Expected: BUILD SUCCESS (premature module compiles; app sees the new bean).

- [ ] **Step 8: Commit**

```bash
git add backend/pom.xml backend/premature/pom.xml backend/premature/src/main/java backend/app/pom.xml backend/app/src/main/java/com/albudoor/hms/app/HmsApplication.java backend/catalogue/src/main/java/com/albudoor/hms/catalogue/domain/ServiceCategory.java
git commit -m "feat(premature): scaffold premature module + PREMATURE service category"
```

### Task 1.2: Make visit origin explicit (R2 fix)

The BRD R2 requires New-vs-Returning to be recorded; today `CreateVisitHandler:45` hardcodes `VisitOrigin.DIRECT_RETURNING`. This task makes origin a command parameter (backward-compatible: null defaults to `DIRECT_RETURNING`, so every existing caller and E2E keeps working). Wiring the reception UI to *choose* New vs Returning is a thin reception-flow refinement noted as out-of-scope for the spine; this task removes the hardcode and gives the capability.

**Files:**
- Modify: `backend/visit-management/.../createvisit/CreateVisitCommand.java`
- Modify: `backend/visit-management/.../createvisit/CreateVisitHandler.java`
- Test: `backend/app/src/test/java/com/albudoor/hms/app/premature/VisitOriginIT.java`

- [ ] **Step 1: Write a failing IT**

```java
package com.albudoor.hms.app.premature;

import com.albudoor.hms.app.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class VisitOriginIT extends IntegrationTest {

    @Autowired TestRestTemplate rest;

    private HttpHeaders auth(String user) {
        var login = rest.postForEntity("/api/auth/login",
                Map.of("username", user, "password", user), Map.class);
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setBearerAuth((String) login.getBody().get("token"));
        return h;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> post(String path, Map<String, Object> body, String user) {
        return rest.exchange(path, org.springframework.http.HttpMethod.POST,
                new HttpEntity<>(body, auth(user)), Map.class).getBody();
    }

    @Test
    void origin_defaults_to_returning_but_can_be_set_new() {
        var patient = post("/api/patients", Map.of(
                "fullName", "Origin Test " + System.nanoTime(), "gender", "MALE",
                "dateOfBirth", "1990-01-01",
                "mobileNumber", "0772" + (System.nanoTime() % 10_000_000L), "vip", false), "receptionist");

        var defaulted = post("/api/visits",
                Map.of("patientId", patient.get("id"), "visitType", "PREMATURE"), "receptionist");
        assertThat(defaulted.get("origin")).isEqualTo("DIRECT_RETURNING");

        var explicitNew = post("/api/visits",
                Map.of("patientId", patient.get("id"), "visitType", "PREMATURE", "origin", "DIRECT_NEW"),
                "receptionist");
        assertThat(explicitNew.get("origin")).isEqualTo("DIRECT_NEW");
    }
}
```

- [ ] **Step 2: Run — expect FAIL** (`origin` is ignored / always DIRECT_RETURNING)

Run: `cd backend && ./mvnw -q -pl app -am test -Dtest=VisitOriginIT`
Expected: FAIL on the `DIRECT_NEW` assertion.

- [ ] **Step 3: Add `origin` to `CreateVisitCommand`**

```java
public record CreateVisitCommand(
        @NotNull UUID patientId,
        @NotNull VisitType visitType,
        VisitOrigin origin,
        UUID assignedDoctorId
) {}
```

Add `import com.albudoor.hms.visitmanagement.domain.VisitOrigin;`. Note: a record with a new component is still constructed positionally; JSON binding maps by field name, and the existing single caller passes named JSON fields, so omitting `origin` deserializes to null.

- [ ] **Step 4: Use it in `CreateVisitHandler`**

Replace the hardcoded `VisitOrigin.DIRECT_RETURNING` argument with:

```java
                cmd.origin() == null ? VisitOrigin.DIRECT_RETURNING : cmd.origin(),
```

(`Visit.createDirect` already rejects `FORWARDED`, so an invalid origin yields a clean 422.)

- [ ] **Step 5: Run — expect PASS**

Run: `cd backend && ./mvnw -q -pl app -am test -Dtest=VisitOriginIT`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/visit-management/src/main/java/com/albudoor/hms/visitmanagement/createvisit/CreateVisitCommand.java backend/visit-management/src/main/java/com/albudoor/hms/visitmanagement/createvisit/CreateVisitHandler.java backend/app/src/test/java/com/albudoor/hms/app/premature/VisitOriginIT.java
git commit -m "feat(visit): make visit origin explicit (R2) — removes hardcoded DIRECT_RETURNING"
```

---

# PHASE 2 — Schema migration + seed

### Task 2.1: Create the `V019__premature_init.sql` migration

**Files:**
- Create: `backend/premature/src/main/resources/db/migration/V019__premature_init.sql`

- [ ] **Step 1: Write the migration**

```sql
-- HMS-BRD-REC-005 Premature admission spine.
-- 1) Extend the service catalogue to allow PREMATURE billing items + seed admission/discharge fees.
-- 2) Bed inventory (admin-managed).
-- 3) Premature admission (the bed-stay case), referencing the visit by id.

-- ---- 1. Catalogue: allow PREMATURE category + seed fee items -----------------------------
ALTER TABLE service_item DROP CONSTRAINT chk_service_category;
ALTER TABLE service_item ADD CONSTRAINT chk_service_category
    CHECK (category IN ('LAB', 'IMAGING', 'ECO', 'EMERGENCY', 'DRUG', 'PREMATURE'));
ALTER TABLE service_item DROP CONSTRAINT chk_service_forward_to;
ALTER TABLE service_item ADD CONSTRAINT chk_service_forward_to
    CHECK (forward_to IS NULL OR forward_to IN ('LAB', 'IMAGING', 'ECO', 'EMERGENCY', 'DRUG', 'PREMATURE'));

INSERT INTO service_item (id, category, code, name_en, name_ar, fee, currency, sort_order, active, forward_to, created_at, created_by) VALUES
(gen_random_uuid(), 'PREMATURE', 'PREM-ADM', 'Premature Admission', 'دخول الخدج',  50000, 'IQD', 1, TRUE, NULL, NOW(), 'flyway'),
(gen_random_uuid(), 'PREMATURE', 'PREM-DIS', 'Premature Discharge', 'خروج الخدج', 30000, 'IQD', 2, TRUE, NULL, NOW(), 'flyway');

-- ---- 2. Bed inventory --------------------------------------------------------------------
CREATE TABLE prem_bed (
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
    CONSTRAINT uk_prem_bed_code UNIQUE (code),
    CONSTRAINT chk_prem_bed_status CHECK (status IN ('AVAILABLE', 'PENDING_PAYMENT', 'OCCUPIED'))
);

-- ---- 3. Premature admission (the bed-stay case) ------------------------------------------
CREATE TABLE prem_admission (
    id                    UUID PRIMARY KEY,
    visit_id              UUID         NOT NULL,
    visit_display_id      VARCHAR(30)  NOT NULL,
    patient_id            UUID         NOT NULL,
    patient_mrn           VARCHAR(30)  NOT NULL,
    patient_name          VARCHAR(300) NOT NULL,
    bed_id                UUID         NOT NULL,
    bed_code              VARCHAR(30)  NOT NULL,
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
    CONSTRAINT uk_prem_admission_visit UNIQUE (visit_id),
    CONSTRAINT fk_prem_admission_bed FOREIGN KEY (bed_id) REFERENCES prem_bed(id),
    CONSTRAINT chk_prem_admission_status CHECK (status IN
        ('AWAITING_ADMISSION_PAYMENT', 'UNDER_CARE', 'TREATMENT_FINISHED',
         'AWAITING_DISCHARGE_PAYMENT', 'CLOSED', 'CANCELLED')),
    CONSTRAINT chk_prem_admission_stay_unit CHECK (stay_unit IN ('HOURS', 'DAYS'))
);

CREATE INDEX idx_prem_admission_status ON prem_admission (status);
CREATE INDEX idx_prem_admission_bed ON prem_admission (bed_id);
CREATE INDEX idx_prem_admission_initial_payment ON prem_admission (initial_payment_id);
CREATE INDEX idx_prem_admission_final_payment ON prem_admission (final_payment_id);

-- ---- Seed a starter set of beds (admin can add/edit more) ---------------------------------
INSERT INTO prem_bed (id, code, room, status, active, created_at, created_by) VALUES
(gen_random_uuid(), 'PREM-01', 'Room A', 'AVAILABLE', TRUE, NOW(), 'flyway'),
(gen_random_uuid(), 'PREM-02', 'Room A', 'AVAILABLE', TRUE, NOW(), 'flyway'),
(gen_random_uuid(), 'PREM-03', 'Room A', 'AVAILABLE', TRUE, NOW(), 'flyway'),
(gen_random_uuid(), 'PREM-04', 'Room B', 'AVAILABLE', TRUE, NOW(), 'flyway'),
(gen_random_uuid(), 'PREM-05', 'Room B', 'AVAILABLE', TRUE, NOW(), 'flyway'),
(gen_random_uuid(), 'PREM-06', 'Room B', 'AVAILABLE', TRUE, NOW(), 'flyway');
```

- [ ] **Step 2: Verify it applies (context-load test re-runs Flyway on a fresh container)**

Run: `cd backend && ./mvnw -q -pl app -am test -Dtest=HmsApplicationTests`
Expected: PASS (Flyway applies V019 cleanly; no validation errors).

- [ ] **Step 3: Commit**

```bash
git add backend/premature/src/main/resources/db/migration/V019__premature_init.sql
git commit -m "feat(premature): V019 schema — beds, admissions, PREMATURE catalogue items"
```

---

# PHASE 3 — Bed aggregate + repository (with unit tests)

### Task 3.1: `BedStatus`, `Bed`, `BedRepository` + unit tests

**Files:**
- Create: `domain/BedStatus.java`, `domain/Bed.java`, `infrastructure/BedRepository.java`
- Test: `src/test/java/com/albudoor/hms/premature/domain/BedTest.java`

- [ ] **Step 1: Write `BedTest` (failing)**

`backend/premature/src/test/java/com/albudoor/hms/premature/domain/BedTest.java`:

```java
package com.albudoor.hms.premature.domain;

import com.albudoor.hms.platform.exception.DomainException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BedTest {

    @Test
    void created_bed_is_available_and_active() {
        Bed bed = Bed.create("PREM-09", "Room C");
        assertThat(bed.getCode()).isEqualTo("PREM-09");
        assertThat(bed.getStatus()).isEqualTo(BedStatus.AVAILABLE);
        assertThat(bed.isActive()).isTrue();
    }

    @Test
    void create_requires_code() {
        assertThatThrownBy(() -> Bed.create("  ", "Room C"))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void reserve_then_occupy_then_discharge_cycles_back_to_available() {
        Bed bed = Bed.create("PREM-09", null);
        bed.reserve();
        assertThat(bed.getStatus()).isEqualTo(BedStatus.PENDING_PAYMENT);
        bed.occupy();
        assertThat(bed.getStatus()).isEqualTo(BedStatus.OCCUPIED);
        bed.discharge();
        assertThat(bed.getStatus()).isEqualTo(BedStatus.AVAILABLE);
    }

    @Test
    void reserve_releases_back_to_available_on_rejection() {
        Bed bed = Bed.create("PREM-09", null);
        bed.reserve();
        bed.release();
        assertThat(bed.getStatus()).isEqualTo(BedStatus.AVAILABLE);
    }

    @Test
    void cannot_reserve_an_occupied_bed() {
        Bed bed = Bed.create("PREM-09", null);
        bed.reserve();
        bed.occupy();
        assertThatThrownBy(bed::reserve).isInstanceOf(DomainException.class);
    }

    @Test
    void cannot_reserve_an_inactive_bed() {
        Bed bed = Bed.create("PREM-09", null);
        bed.deactivate();
        assertThatThrownBy(bed::reserve).isInstanceOf(DomainException.class);
    }

    @Test
    void cannot_occupy_unless_pending_payment() {
        Bed bed = Bed.create("PREM-09", null);
        assertThatThrownBy(bed::occupy).isInstanceOf(DomainException.class);
    }
}
```

- [ ] **Step 2: Run — expect FAIL (Bed/BedStatus don't exist)**

Run: `cd backend && ./mvnw -q -pl premature test -Dtest=BedTest`
Expected: compile failure / FAIL.

- [ ] **Step 3: Implement `BedStatus`**

`backend/premature/src/main/java/com/albudoor/hms/premature/domain/BedStatus.java`:

```java
package com.albudoor.hms.premature.domain;

public enum BedStatus {
    /** Free for assignment. */
    AVAILABLE,
    /** Assigned to an admission whose initial payment is not yet approved. */
    PENDING_PAYMENT,
    /** Initial payment approved; patient under care. */
    OCCUPIED
}
```

- [ ] **Step 4: Implement `Bed`**

`backend/premature/src/main/java/com/albudoor/hms/premature/domain/Bed.java`:

```java
package com.albudoor.hms.premature.domain;

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

import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "prem_bed")
public class Bed extends AggregateRoot {

    @Id
    private UUID id;

    @Column(nullable = false, length = 30)
    private String code;

    @Column(length = 100)
    private String room;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BedStatus status;

    @Column(nullable = false)
    private boolean active;

    public static Bed create(String code, String room) {
        if (code == null || code.isBlank()) {
            throw new DomainException("BED_CODE_REQUIRED", "Bed code is required");
        }
        Bed bed = new Bed();
        bed.id = UUID.randomUUID();
        bed.code = code.trim();
        bed.room = (room == null || room.isBlank()) ? null : room.trim();
        bed.status = BedStatus.AVAILABLE;
        bed.active = true;
        return bed;
    }

    /** Admin edits. */
    public void updateDetails(String room, boolean active) {
        this.room = (room == null || room.isBlank()) ? null : room.trim();
        this.active = active;
    }

    public void deactivate() {
        this.active = false;
    }

    /** Assign to an admission; bed must be free and active. */
    public void reserve() {
        if (!active) {
            throw new DomainException("BED_INACTIVE", "Bed " + code + " is inactive");
        }
        if (status != BedStatus.AVAILABLE) {
            throw new DomainException("BED_NOT_AVAILABLE",
                    "Bed " + code + " is not available (status=" + status + ")");
        }
        this.status = BedStatus.PENDING_PAYMENT;
    }

    /** Initial payment approved. */
    public void occupy() {
        if (status != BedStatus.PENDING_PAYMENT) {
            throw new DomainException("BED_NOT_PENDING",
                    "Bed " + code + " is not pending payment (status=" + status + ")");
        }
        this.status = BedStatus.OCCUPIED;
    }

    /** Initial payment rejected — free the bed. */
    public void release() {
        if (status != BedStatus.PENDING_PAYMENT) {
            throw new DomainException("BED_NOT_PENDING",
                    "Bed " + code + " is not pending payment (status=" + status + ")");
        }
        this.status = BedStatus.AVAILABLE;
    }

    /** Discharge — free the bed for reuse. */
    public void discharge() {
        if (status != BedStatus.OCCUPIED) {
            throw new DomainException("BED_NOT_OCCUPIED",
                    "Bed " + code + " is not occupied (status=" + status + ")");
        }
        this.status = BedStatus.AVAILABLE;
    }
}
```

- [ ] **Step 5: Implement `BedRepository`**

`backend/premature/src/main/java/com/albudoor/hms/premature/infrastructure/BedRepository.java`:

```java
package com.albudoor.hms.premature.infrastructure;

import com.albudoor.hms.premature.domain.Bed;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BedRepository extends JpaRepository<Bed, UUID> {

    boolean existsByCode(String code);

    Optional<Bed> findByCode(String code);

    List<Bed> findAllByOrderByCodeAsc();
}
```

- [ ] **Step 6: Run — expect PASS**

Run: `cd backend && ./mvnw -q -pl premature test -Dtest=BedTest`
Expected: PASS (7 tests).

- [ ] **Step 7: Commit**

```bash
git add backend/premature/src/main/java/com/albudoor/hms/premature/domain/Bed.java backend/premature/src/main/java/com/albudoor/hms/premature/domain/BedStatus.java backend/premature/src/main/java/com/albudoor/hms/premature/infrastructure/BedRepository.java backend/premature/src/test/java/com/albudoor/hms/premature/domain/BedTest.java
git commit -m "feat(premature): Bed aggregate + repository with unit tests"
```

---

# PHASE 4 — Bed admin CRUD

### Task 4.1: `BedResponse` + createbed / listbeds / updatebed slices

**Files:**
- Create: `api/BedResponse.java`
- Create: `createbed/{CreateBedCommand,CreateBedHandler,CreateBedController}.java`
- Create: `updatebed/{UpdateBedCommand,UpdateBedHandler,UpdateBedController}.java`
- Create: `listbeds/{ListBedsHandler,ListBedsController}.java`

> Note: `ListBedsHandler` attaches occupant info, which needs `PrematureAdmissionRepository` (Phase 5). To keep this task self-contained, the **first** version of `ListBedsHandler` returns beds without occupants; Task 5.2 enriches it. The `BedResponse.occupant` field is defined now (nullable).

- [ ] **Step 1: Implement `BedResponse`**

`backend/premature/src/main/java/com/albudoor/hms/premature/api/BedResponse.java`:

```java
package com.albudoor.hms.premature.api;

import com.albudoor.hms.premature.domain.Bed;

import java.time.Instant;
import java.util.UUID;

public record BedResponse(
        UUID id,
        String code,
        String room,
        String status,
        boolean active,
        Occupant occupant
) {
    /** Present when the bed is PENDING_PAYMENT or OCCUPIED. */
    public record Occupant(
            UUID admissionId,
            UUID visitId,
            String visitDisplayId,
            String patientName,
            String patientMrn,
            String admissionStatus,
            Instant stayExpiresAt
    ) {}

    public static BedResponse from(Bed b) {
        return new BedResponse(b.getId(), b.getCode(), b.getRoom(),
                b.getStatus().name(), b.isActive(), null);
    }

    public BedResponse withOccupant(Occupant occ) {
        return new BedResponse(id, code, room, status, active, occ);
    }
}
```

- [ ] **Step 2: Implement createbed slice**

`createbed/CreateBedCommand.java`:

```java
package com.albudoor.hms.premature.createbed;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateBedCommand(
        @NotBlank @Size(max = 30) String code,
        @Size(max = 100) String room
) {}
```

`createbed/CreateBedHandler.java`:

```java
package com.albudoor.hms.premature.createbed;

import com.albudoor.hms.platform.exception.ConflictException;
import com.albudoor.hms.premature.domain.Bed;
import com.albudoor.hms.premature.infrastructure.BedRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateBedHandler {

    private final BedRepository beds;

    public CreateBedHandler(BedRepository beds) {
        this.beds = beds;
    }

    @Transactional
    public Bed handle(CreateBedCommand cmd) {
        if (beds.existsByCode(cmd.code().trim())) {
            throw new ConflictException("BED_CODE_TAKEN", "Bed code already exists: " + cmd.code());
        }
        return beds.save(Bed.create(cmd.code(), cmd.room()));
    }
}
```

`createbed/CreateBedController.java`:

```java
package com.albudoor.hms.premature.createbed;

import com.albudoor.hms.premature.api.BedResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/premature/beds")
public class CreateBedController {

    private final CreateBedHandler handler;

    public CreateBedController(CreateBedHandler handler) {
        this.handler = handler;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'PREMATURE_STAFF')")
    public ResponseEntity<BedResponse> create(@Valid @RequestBody CreateBedCommand cmd) {
        return ResponseEntity.status(HttpStatus.CREATED).body(BedResponse.from(handler.handle(cmd)));
    }
}
```

- [ ] **Step 3: Implement updatebed slice**

`updatebed/UpdateBedCommand.java`:

```java
package com.albudoor.hms.premature.updatebed;

import jakarta.validation.constraints.Size;

public record UpdateBedCommand(
        @Size(max = 100) String room,
        boolean active
) {}
```

`updatebed/UpdateBedHandler.java`:

```java
package com.albudoor.hms.premature.updatebed;

import com.albudoor.hms.platform.exception.NotFoundException;
import com.albudoor.hms.premature.domain.Bed;
import com.albudoor.hms.premature.infrastructure.BedRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UpdateBedHandler {

    private final BedRepository beds;

    public UpdateBedHandler(BedRepository beds) {
        this.beds = beds;
    }

    @Transactional
    public Bed handle(UUID id, UpdateBedCommand cmd) {
        Bed bed = beds.findById(id)
                .orElseThrow(() -> new NotFoundException("Bed not found: " + id));
        bed.updateDetails(cmd.room(), cmd.active());
        return beds.save(bed);
    }
}
```

`updatebed/UpdateBedController.java`:

```java
package com.albudoor.hms.premature.updatebed;

import com.albudoor.hms.premature.api.BedResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/premature/beds")
public class UpdateBedController {

    private final UpdateBedHandler handler;

    public UpdateBedController(UpdateBedHandler handler) {
        this.handler = handler;
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'PREMATURE_STAFF')")
    public BedResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateBedCommand cmd) {
        return BedResponse.from(handler.handle(id, cmd));
    }
}
```

- [ ] **Step 4: Implement listbeds slice (no occupant yet)**

`listbeds/ListBedsHandler.java`:

```java
package com.albudoor.hms.premature.listbeds;

import com.albudoor.hms.premature.api.BedResponse;
import com.albudoor.hms.premature.infrastructure.BedRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ListBedsHandler {

    private final BedRepository beds;

    public ListBedsHandler(BedRepository beds) {
        this.beds = beds;
    }

    @Transactional(readOnly = true)
    public List<BedResponse> list() {
        return beds.findAllByOrderByCodeAsc().stream().map(BedResponse::from).toList();
    }
}
```

`listbeds/ListBedsController.java`:

```java
package com.albudoor.hms.premature.listbeds;

import com.albudoor.hms.premature.api.BedResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/premature/beds")
@PreAuthorize("isAuthenticated()")
public class ListBedsController {

    private final ListBedsHandler handler;

    public ListBedsController(ListBedsHandler handler) {
        this.handler = handler;
    }

    @GetMapping
    public List<BedResponse> list() {
        return handler.list();
    }
}
```

- [ ] **Step 5: Compile**

Run: `cd backend && ./mvnw -q -pl premature test-compile`
Expected: BUILD SUCCESS.

- [ ] **Step 6: Commit**

```bash
git add backend/premature/src/main/java/com/albudoor/hms/premature/api/BedResponse.java backend/premature/src/main/java/com/albudoor/hms/premature/createbed backend/premature/src/main/java/com/albudoor/hms/premature/updatebed backend/premature/src/main/java/com/albudoor/hms/premature/listbeds
git commit -m "feat(premature): bed admin CRUD slices"
```

### Task 4.2: Bed admin integration test

**Files:**
- Create: `backend/app/src/test/java/com/albudoor/hms/app/premature/BedAdminIT.java`

- [ ] **Step 1: Write the IT**

```java
package com.albudoor.hms.app.premature;

import com.albudoor.hms.app.IntegrationTest;
import com.albudoor.hms.premature.createbed.CreateBedCommand;
import com.albudoor.hms.premature.createbed.CreateBedHandler;
import com.albudoor.hms.premature.domain.Bed;
import com.albudoor.hms.premature.domain.BedStatus;
import com.albudoor.hms.premature.infrastructure.BedRepository;
import com.albudoor.hms.premature.updatebed.UpdateBedCommand;
import com.albudoor.hms.premature.updatebed.UpdateBedHandler;
import com.albudoor.hms.platform.exception.ConflictException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BedAdminIT extends IntegrationTest {

    @Autowired CreateBedHandler createBed;
    @Autowired UpdateBedHandler updateBed;
    @Autowired BedRepository beds;

    @Test
    void seeded_beds_exist_and_are_available() {
        // V019 seeds PREM-01..PREM-06.
        assertThat(beds.findByCode("PREM-01")).isPresent();
        assertThat(beds.findByCode("PREM-01").get().getStatus()).isEqualTo(BedStatus.AVAILABLE);
    }

    @Test
    void create_then_update_bed() {
        String code = "PREM-IT-" + System.nanoTime();
        Bed created = createBed.handle(new CreateBedCommand(code, "Room Z"));
        assertThat(created.getStatus()).isEqualTo(BedStatus.AVAILABLE);

        Bed updated = updateBed.handle(created.getId(), new UpdateBedCommand("Room Y", false));
        assertThat(updated.getRoom()).isEqualTo("Room Y");
        assertThat(updated.isActive()).isFalse();
    }

    @Test
    void duplicate_bed_code_is_rejected() {
        String code = "PREM-DUP-" + System.nanoTime();
        createBed.handle(new CreateBedCommand(code, null));
        assertThatThrownBy(() -> createBed.handle(new CreateBedCommand(code, null)))
                .isInstanceOf(ConflictException.class);
    }
}
```

- [ ] **Step 2: Run — expect PASS**

Run: `cd backend && ./mvnw -q -pl app -am test -Dtest=BedAdminIT`
Expected: PASS (3 tests).

- [ ] **Step 3: Commit**

```bash
git add backend/app/src/test/java/com/albudoor/hms/app/premature/BedAdminIT.java
git commit -m "test(premature): bed admin integration tests"
```

---

# PHASE 5 — PrematureAdmission aggregate

### Task 5.1: `AdmissionStatus`, `StayUnit`, `PrematureAdmission`, repository + unit tests

**Files:**
- Create: `domain/AdmissionStatus.java`, `domain/StayUnit.java`, `domain/PrematureAdmission.java`
- Create: `infrastructure/PrematureAdmissionRepository.java`
- Test: `src/test/java/com/albudoor/hms/premature/domain/PrematureAdmissionTest.java`

- [ ] **Step 1: Write `PrematureAdmissionTest` (failing)**

```java
package com.albudoor.hms.premature.domain;

import com.albudoor.hms.platform.exception.DomainException;
import org.junit.jupiter.api.Test;

import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PrematureAdmissionTest {

    private PrematureAdmission open(StayUnit unit, int value) {
        return PrematureAdmission.open(
                UUID.randomUUID(), "VST-2026-000123",
                UUID.randomUUID(), "ALB-2026-000123", "Baby Test",
                UUID.randomUUID(), "PREM-01", value, unit);
    }

    @Test
    void open_starts_awaiting_payment_with_computed_expiry() {
        PrematureAdmission a = open(StayUnit.DAYS, 3);
        assertThat(a.getStatus()).isEqualTo(AdmissionStatus.AWAITING_ADMISSION_PAYMENT);
        assertThat(a.getStayExpiresAt())
                .isEqualTo(a.getAdmittedAt().plus(3, ChronoUnit.DAYS));
    }

    @Test
    void open_requires_positive_stay() {
        assertThatThrownBy(() -> open(StayUnit.HOURS, 0)).isInstanceOf(DomainException.class);
    }

    @Test
    void full_happy_path_transitions() {
        PrematureAdmission a = open(StayUnit.DAYS, 2);
        a.linkInitialPayment(UUID.randomUUID());
        a.markUnderCare();
        assertThat(a.getStatus()).isEqualTo(AdmissionStatus.UNDER_CARE);
        a.finishTreatment();
        assertThat(a.getStatus()).isEqualTo(AdmissionStatus.TREATMENT_FINISHED);
        assertThat(a.getTreatmentFinishedAt()).isNotNull();
        a.scheduleDischargePayment(UUID.randomUUID());
        assertThat(a.getStatus()).isEqualTo(AdmissionStatus.AWAITING_DISCHARGE_PAYMENT);
        a.close();
        assertThat(a.getStatus()).isEqualTo(AdmissionStatus.CLOSED);
        assertThat(a.getClosedAt()).isNotNull();
    }

    @Test
    void extend_stay_pushes_expiry() {
        PrematureAdmission a = open(StayUnit.DAYS, 2);
        a.linkInitialPayment(UUID.randomUUID());
        a.markUnderCare();
        var before = a.getStayExpiresAt();
        a.extendStay(1, StayUnit.DAYS);
        assertThat(a.getStayExpiresAt()).isEqualTo(before.plus(1, ChronoUnit.DAYS));
    }

    @Test
    void cannot_extend_before_under_care() {
        PrematureAdmission a = open(StayUnit.DAYS, 2);
        assertThatThrownBy(() -> a.extendStay(1, StayUnit.DAYS)).isInstanceOf(DomainException.class);
    }

    @Test
    void cancel_only_from_awaiting_admission_payment() {
        PrematureAdmission a = open(StayUnit.DAYS, 2);
        a.cancel();
        assertThat(a.getStatus()).isEqualTo(AdmissionStatus.CANCELLED);
    }

    @Test
    void cannot_finish_treatment_unless_under_care() {
        PrematureAdmission a = open(StayUnit.DAYS, 2);
        assertThatThrownBy(a::finishTreatment).isInstanceOf(DomainException.class);
    }

    @Test
    void close_only_from_awaiting_discharge_payment() {
        PrematureAdmission a = open(StayUnit.DAYS, 2);
        a.linkInitialPayment(UUID.randomUUID());
        a.markUnderCare();
        assertThatThrownBy(a::close).isInstanceOf(DomainException.class);
    }
}
```

- [ ] **Step 2: Run — expect FAIL**

Run: `cd backend && ./mvnw -q -pl premature test -Dtest=PrematureAdmissionTest`
Expected: compile failure / FAIL.

- [ ] **Step 3: Implement `StayUnit` and `AdmissionStatus`**

`domain/StayUnit.java`:

```java
package com.albudoor.hms.premature.domain;

import java.time.temporal.ChronoUnit;

public enum StayUnit {
    HOURS(ChronoUnit.HOURS),
    DAYS(ChronoUnit.DAYS);

    private final ChronoUnit chronoUnit;

    StayUnit(ChronoUnit chronoUnit) {
        this.chronoUnit = chronoUnit;
    }

    public ChronoUnit chronoUnit() {
        return chronoUnit;
    }
}
```

`domain/AdmissionStatus.java`:

```java
package com.albudoor.hms.premature.domain;

public enum AdmissionStatus {
    /** Bed assigned; initial admission payment pending. */
    AWAITING_ADMISSION_PAYMENT,
    /** Initial payment approved; infant under care. */
    UNDER_CARE,
    /** Doctor marked treatment finished; discharge payment not yet generated. */
    TREATMENT_FINISHED,
    /** Final discharge payment generated; awaiting cashier. */
    AWAITING_DISCHARGE_PAYMENT,
    /** Discharged and closed. */
    CLOSED,
    /** Cancelled before care began (initial payment rejected). */
    CANCELLED
}
```

- [ ] **Step 4: Implement `PrematureAdmission`**

`domain/PrematureAdmission.java`:

```java
package com.albudoor.hms.premature.domain;

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
@Table(name = "prem_admission")
public class PrematureAdmission extends AggregateRoot {

    @Id
    private UUID id;

    @Column(name = "visit_id", nullable = false)
    private UUID visitId;

    @Column(name = "visit_display_id", nullable = false, length = 30)
    private String visitDisplayId;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "patient_mrn", nullable = false, length = 30)
    private String patientMrn;

    @Column(name = "patient_name", nullable = false, length = 300)
    private String patientName;

    @Column(name = "bed_id", nullable = false)
    private UUID bedId;

    @Column(name = "bed_code", nullable = false, length = 30)
    private String bedCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AdmissionStatus status;

    @Column(name = "stay_value", nullable = false)
    private int stayValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "stay_unit", nullable = false, length = 10)
    private StayUnit stayUnit;

    @Column(name = "admitted_at", nullable = false)
    private Instant admittedAt;

    @Column(name = "stay_expires_at", nullable = false)
    private Instant stayExpiresAt;

    @Column(name = "treatment_finished_at")
    private Instant treatmentFinishedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "initial_payment_id")
    private UUID initialPaymentId;

    @Column(name = "final_payment_id")
    private UUID finalPaymentId;

    public static PrematureAdmission open(
            UUID visitId, String visitDisplayId,
            UUID patientId, String patientMrn, String patientName,
            UUID bedId, String bedCode,
            int stayValue, StayUnit stayUnit
    ) {
        if (visitId == null || patientId == null || bedId == null) {
            throw new DomainException("ADMISSION_REFS_REQUIRED", "visit, patient and bed are required");
        }
        if (stayUnit == null) {
            throw new DomainException("STAY_UNIT_REQUIRED", "stay unit is required");
        }
        if (stayValue <= 0) {
            throw new DomainException("STAY_VALUE_INVALID", "stay value must be positive");
        }
        PrematureAdmission a = new PrematureAdmission();
        a.id = UUID.randomUUID();
        a.visitId = visitId;
        a.visitDisplayId = visitDisplayId;
        a.patientId = patientId;
        a.patientMrn = patientMrn;
        a.patientName = patientName;
        a.bedId = bedId;
        a.bedCode = bedCode;
        a.status = AdmissionStatus.AWAITING_ADMISSION_PAYMENT;
        a.stayValue = stayValue;
        a.stayUnit = stayUnit;
        a.admittedAt = Instant.now();
        a.stayExpiresAt = a.admittedAt.plus(stayValue, stayUnit.chronoUnit());
        return a;
    }

    public void linkInitialPayment(UUID paymentId) {
        this.initialPaymentId = paymentId;
    }

    public void markUnderCare() {
        require(AdmissionStatus.AWAITING_ADMISSION_PAYMENT, "mark under care");
        this.status = AdmissionStatus.UNDER_CARE;
    }

    public void cancel() {
        require(AdmissionStatus.AWAITING_ADMISSION_PAYMENT, "cancel");
        this.status = AdmissionStatus.CANCELLED;
    }

    public void extendStay(int value, StayUnit unit) {
        if (status != AdmissionStatus.UNDER_CARE && status != AdmissionStatus.TREATMENT_FINISHED) {
            throw new DomainException("ADMISSION_NOT_EXTENDABLE",
                    "Can only extend stay while UNDER_CARE or TREATMENT_FINISHED (status=" + status + ")");
        }
        if (value <= 0 || unit == null) {
            throw new DomainException("STAY_VALUE_INVALID", "extension must be positive with a unit");
        }
        this.stayExpiresAt = this.stayExpiresAt.plus(value, unit.chronoUnit());
    }

    public void finishTreatment() {
        require(AdmissionStatus.UNDER_CARE, "finish treatment");
        this.status = AdmissionStatus.TREATMENT_FINISHED;
        this.treatmentFinishedAt = Instant.now();
    }

    public void scheduleDischargePayment(UUID finalPaymentId) {
        require(AdmissionStatus.TREATMENT_FINISHED, "schedule discharge payment");
        this.finalPaymentId = finalPaymentId;
        this.status = AdmissionStatus.AWAITING_DISCHARGE_PAYMENT;
    }

    public void close() {
        require(AdmissionStatus.AWAITING_DISCHARGE_PAYMENT, "close");
        this.status = AdmissionStatus.CLOSED;
        this.closedAt = Instant.now();
    }

    private void require(AdmissionStatus expected, String action) {
        if (this.status != expected) {
            throw new DomainException("ADMISSION_INVALID_STATE",
                    "Cannot " + action + " — admission is " + this.status + ", expected " + expected);
        }
    }
}
```

- [ ] **Step 5: Implement `PrematureAdmissionRepository`**

`infrastructure/PrematureAdmissionRepository.java`:

```java
package com.albudoor.hms.premature.infrastructure;

import com.albudoor.hms.premature.domain.AdmissionStatus;
import com.albudoor.hms.premature.domain.PrematureAdmission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PrematureAdmissionRepository extends JpaRepository<PrematureAdmission, UUID> {

    Optional<PrematureAdmission> findByVisitId(UUID visitId);

    Optional<PrematureAdmission> findByInitialPaymentId(UUID paymentId);

    Optional<PrematureAdmission> findByFinalPaymentId(UUID paymentId);

    Optional<PrematureAdmission> findByBedIdAndStatusIn(UUID bedId, List<AdmissionStatus> statuses);

    List<PrematureAdmission> findAllByStatusInOrderByAdmittedAtDesc(List<AdmissionStatus> statuses);

    boolean existsByVisitIdAndStatusIn(UUID visitId, List<AdmissionStatus> statuses);
}
```

- [ ] **Step 6: Run — expect PASS**

Run: `cd backend && ./mvnw -q -pl premature test -Dtest=PrematureAdmissionTest`
Expected: PASS (8 tests).

- [ ] **Step 7: Commit**

```bash
git add backend/premature/src/main/java/com/albudoor/hms/premature/domain/AdmissionStatus.java backend/premature/src/main/java/com/albudoor/hms/premature/domain/StayUnit.java backend/premature/src/main/java/com/albudoor/hms/premature/domain/PrematureAdmission.java backend/premature/src/main/java/com/albudoor/hms/premature/infrastructure/PrematureAdmissionRepository.java backend/premature/src/test/java/com/albudoor/hms/premature/domain/PrematureAdmissionTest.java
git commit -m "feat(premature): PrematureAdmission aggregate + repository with unit tests"
```

---

# PHASE 6 — Admit slice + payment bridge (initial)

### Task 6.1: `AdmissionResponse` + admit slice

**Files:**
- Create: `api/AdmissionResponse.java`
- Create: `admitpatient/{AdmitPatientCommand,AdmitPatientHandler,AdmitPatientController}.java`

- [ ] **Step 1: Implement `AdmissionResponse`**

`api/AdmissionResponse.java`:

```java
package com.albudoor.hms.premature.api;

import com.albudoor.hms.premature.domain.PrematureAdmission;

import java.time.Instant;
import java.util.UUID;

public record AdmissionResponse(
        UUID id,
        UUID visitId,
        String visitDisplayId,
        UUID patientId,
        String patientMrn,
        String patientName,
        UUID bedId,
        String bedCode,
        String status,
        int stayValue,
        String stayUnit,
        Instant admittedAt,
        Instant stayExpiresAt,
        Instant treatmentFinishedAt,
        Instant closedAt,
        UUID initialPaymentId,
        UUID finalPaymentId
) {
    public static AdmissionResponse from(PrematureAdmission a) {
        return new AdmissionResponse(
                a.getId(), a.getVisitId(), a.getVisitDisplayId(),
                a.getPatientId(), a.getPatientMrn(), a.getPatientName(),
                a.getBedId(), a.getBedCode(), a.getStatus().name(),
                a.getStayValue(), a.getStayUnit().name(),
                a.getAdmittedAt(), a.getStayExpiresAt(),
                a.getTreatmentFinishedAt(), a.getClosedAt(),
                a.getInitialPaymentId(), a.getFinalPaymentId());
    }
}
```

- [ ] **Step 2: Implement `AdmitPatientCommand`**

`admitpatient/AdmitPatientCommand.java`:

```java
package com.albudoor.hms.premature.admitpatient;

import com.albudoor.hms.premature.domain.StayUnit;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record AdmitPatientCommand(
        @NotNull UUID visitId,
        @NotNull UUID bedId,
        @Positive int stayValue,
        @NotNull StayUnit stayUnit
) {}
```

- [ ] **Step 3: Implement `AdmitPatientHandler`**

This is the integration crux. It mirrors `OpenCaseHandler`: reserve the bed, create the admission, create the INITIAL payment via `CreatePaymentHandler`, and move the visit to `AWAITING_PAYMENT`.

`admitpatient/AdmitPatientHandler.java`:

```java
package com.albudoor.hms.premature.admitpatient;

import com.albudoor.hms.cashier.createpayment.CreatePaymentCommand;
import com.albudoor.hms.cashier.createpayment.CreatePaymentHandler;
import com.albudoor.hms.cashier.domain.Payment;
import com.albudoor.hms.cashier.domain.PaymentStage;
import com.albudoor.hms.catalogue.domain.ServiceCategory;
import com.albudoor.hms.catalogue.domain.ServiceItem;
import com.albudoor.hms.catalogue.infrastructure.ServiceItemRepository;
import com.albudoor.hms.platform.exception.DomainException;
import com.albudoor.hms.platform.exception.NotFoundException;
import com.albudoor.hms.premature.domain.Bed;
import com.albudoor.hms.premature.domain.PrematureAdmission;
import com.albudoor.hms.premature.infrastructure.BedRepository;
import com.albudoor.hms.premature.infrastructure.PrematureAdmissionRepository;
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

    /** Well-known catalogue code for the premature admission fee (seeded in V019). */
    static final String ADMISSION_ITEM_CODE = "PREM-ADM";

    private final PrematureAdmissionRepository admissions;
    private final BedRepository beds;
    private final VisitRepository visits;
    private final ServiceItemRepository catalogue;
    private final CreatePaymentHandler createPayment;
    private final ApplicationEventPublisher events;

    public AdmitPatientHandler(
            PrematureAdmissionRepository admissions,
            BedRepository beds,
            VisitRepository visits,
            ServiceItemRepository catalogue,
            CreatePaymentHandler createPayment,
            ApplicationEventPublisher events
    ) {
        this.admissions = admissions;
        this.beds = beds;
        this.visits = visits;
        this.catalogue = catalogue;
        this.createPayment = createPayment;
        this.events = events;
    }

    @Transactional
    public PrematureAdmission handle(AdmitPatientCommand cmd) {
        Visit visit = visits.findById(cmd.visitId())
                .orElseThrow(() -> new NotFoundException("Visit not found: " + cmd.visitId()));
        if (visit.getVisitType() != VisitType.PREMATURE) {
            throw new DomainException("NOT_PREMATURE_VISIT",
                    "Visit " + visit.getVisitDisplayId() + " is not a PREMATURE visit");
        }
        if (visit.getStatus() != VisitStatus.CREATED) {
            throw new DomainException("VISIT_NOT_ADMITTABLE",
                    "Visit must be CREATED to admit (status=" + visit.getStatus() + ")");
        }
        if (admissions.findByVisitId(visit.getId()).isPresent()) {
            throw new DomainException("ALREADY_ADMITTED",
                    "Visit already has a premature admission");
        }

        Bed bed = beds.findById(cmd.bedId())
                .orElseThrow(() -> new NotFoundException("Bed not found: " + cmd.bedId()));
        bed.reserve(); // AVAILABLE -> PENDING_PAYMENT (throws if not free/active)
        beds.save(bed);

        PrematureAdmission admission = PrematureAdmission.open(
                visit.getId(), visit.getVisitDisplayId(),
                visit.getPatientId(), visit.getPatientMrn(), visit.getPatientName(),
                bed.getId(), bed.getCode(),
                cmd.stayValue(), cmd.stayUnit());
        admissions.save(admission);

        ServiceItem admissionFee = catalogue
                .findByCategoryAndCode(ServiceCategory.PREMATURE, ADMISSION_ITEM_CODE)
                .orElseThrow(() -> new DomainException("ADMISSION_FEE_MISSING",
                        "Catalogue item " + ADMISSION_ITEM_CODE + " is not configured"));

        Payment payment = createPayment.handle(new CreatePaymentCommand(
                visit.getId(), PaymentStage.INITIAL,
                List.of(new CreatePaymentCommand.Line(admissionFee.getId(), 1)), null));
        admission.linkInitialPayment(payment.getId());
        admissions.save(admission);

        visit.transitionTo(VisitStatus.AWAITING_PAYMENT);
        visit.pullDomainEvents().forEach(events::publishEvent);

        return admission;
    }
}
```

- [ ] **Step 4: Implement `AdmitPatientController`**

`admitpatient/AdmitPatientController.java`:

```java
package com.albudoor.hms.premature.admitpatient;

import com.albudoor.hms.premature.api.AdmissionResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/premature/admissions")
public class AdmitPatientController {

    private final AdmitPatientHandler handler;

    public AdmitPatientController(AdmitPatientHandler handler) {
        this.handler = handler;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('PREMATURE_STAFF', 'ADMIN')")
    public ResponseEntity<AdmissionResponse> admit(@Valid @RequestBody AdmitPatientCommand cmd) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(AdmissionResponse.from(handler.handle(cmd)));
    }
}
```

- [ ] **Step 5: Compile**

Run: `cd backend && ./mvnw -q -pl premature test-compile`
Expected: BUILD SUCCESS.

- [ ] **Step 6: Commit**

```bash
git add backend/premature/src/main/java/com/albudoor/hms/premature/api/AdmissionResponse.java backend/premature/src/main/java/com/albudoor/hms/premature/admitpatient
git commit -m "feat(premature): admit-patient slice (assign bed + initial payment)"
```

### Task 6.2: `PrematurePaymentBridge` — initial approve/reject

**Files:**
- Create: `bridge/PrematurePaymentBridge.java`

- [ ] **Step 1: Implement the bridge (initial handling now; final added in Task 7.3)**

`bridge/PrematurePaymentBridge.java`:

```java
package com.albudoor.hms.premature.bridge;

import com.albudoor.hms.cashier.domain.PaymentApprovedEvent;
import com.albudoor.hms.cashier.domain.PaymentRejectedEvent;
import com.albudoor.hms.cashier.domain.PaymentStage;
import com.albudoor.hms.premature.domain.Bed;
import com.albudoor.hms.premature.domain.PrematureAdmission;
import com.albudoor.hms.premature.infrastructure.BedRepository;
import com.albudoor.hms.premature.infrastructure.PrematureAdmissionRepository;
import com.albudoor.hms.visitmanagement.domain.Visit;
import com.albudoor.hms.visitmanagement.infrastructure.VisitRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Drives the premature bed-stay case off cashier payment decisions. The generic
 * PaymentVisitBridge already advances the VISIT (INITIAL→IN_PROGRESS, FINAL→COMPLETED);
 * this bridge advances the ADMISSION + BED, and owns the premature-specific rejection rules.
 *
 * <p>INITIAL approved → admission UNDER_CARE, bed OCCUPIED.
 * <br>INITIAL rejected → bed released, admission CANCELLED, visit cancelled.
 * <br>FINAL approved → admission CLOSED, bed discharged (freed).
 * <br>FINAL rejected → no-op: admission stays AWAITING_DISCHARGE_PAYMENT (P12b — re-issuable).
 */
@Component
public class PrematurePaymentBridge {

    private static final Logger log = LoggerFactory.getLogger(PrematurePaymentBridge.class);

    private final PrematureAdmissionRepository admissions;
    private final BedRepository beds;
    private final VisitRepository visits;

    public PrematurePaymentBridge(
            PrematureAdmissionRepository admissions,
            BedRepository beds,
            VisitRepository visits
    ) {
        this.admissions = admissions;
        this.beds = beds;
        this.visits = visits;
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onApproved(PaymentApprovedEvent event) {
        if (event.stage() == PaymentStage.INITIAL) {
            admissions.findByInitialPaymentId(event.paymentId()).ifPresent(admission -> {
                admission.markUnderCare();
                admissions.save(admission);
                beds.findById(admission.getBedId()).ifPresent(bed -> {
                    bed.occupy();
                    beds.save(bed);
                });
                log.info("Premature admission {} → UNDER_CARE, bed {} OCCUPIED (initial payment {})",
                        admission.getId(), admission.getBedCode(), event.paymentId());
            });
        }
        // FINAL approval handled in Task 7.3.
    }

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onRejected(PaymentRejectedEvent event) {
        if (event.stage() == PaymentStage.INITIAL) {
            admissions.findByInitialPaymentId(event.paymentId()).ifPresent(admission -> {
                beds.findById(admission.getBedId()).ifPresent(bed -> {
                    bed.release();
                    beds.save(bed);
                });
                admission.cancel();
                admissions.save(admission);
                Visit visit = visits.findById(admission.getVisitId()).orElse(null);
                if (visit != null && !visit.getStatus().isTerminal()) {
                    visit.cancel("Initial premature payment rejected");
                    visits.save(visit);
                }
                log.info("Premature admission {} CANCELLED, bed {} released (initial payment {} rejected)",
                        admission.getId(), admission.getBedCode(), event.paymentId());
            });
        }
        // FINAL rejection handled in Task 7.3 (P12b: intentionally no-op).
    }
}
```

- [ ] **Step 2: Compile**

Run: `cd backend && ./mvnw -q -pl premature test-compile`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add backend/premature/src/main/java/com/albudoor/hms/premature/bridge/PrematurePaymentBridge.java
git commit -m "feat(premature): payment bridge — initial approve/reject drives bed + admission"
```

### Task 6.3: Admit-flow integration test

**Files:**
- Create: `backend/app/src/test/java/com/albudoor/hms/app/premature/AdmitFlowIT.java`

> These tests are NOT @Transactional (so AFTER_COMMIT bridges fire). They use a JWT-authenticated `TestRestTemplate` against the random port to set the `created_by`/security context and exercise controllers + bridges exactly like production. A small helper logs in via `/api/auth/login`.

- [ ] **Step 1: Write the IT**

```java
package com.albudoor.hms.app.premature;

import com.albudoor.hms.app.IntegrationTest;
import com.albudoor.hms.cashier.domain.PaymentStatus;
import com.albudoor.hms.cashier.infrastructure.PaymentRepository;
import com.albudoor.hms.premature.domain.AdmissionStatus;
import com.albudoor.hms.premature.domain.BedStatus;
import com.albudoor.hms.premature.infrastructure.BedRepository;
import com.albudoor.hms.premature.infrastructure.PrematureAdmissionRepository;
import com.albudoor.hms.visitmanagement.domain.VisitStatus;
import com.albudoor.hms.visitmanagement.infrastructure.VisitRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static java.time.Duration.ofSeconds;

class AdmitFlowIT extends IntegrationTest {

    @Autowired TestRestTemplate rest;
    @Autowired BedRepository beds;
    @Autowired PrematureAdmissionRepository admissions;
    @Autowired VisitRepository visits;
    @Autowired PaymentRepository payments;

    private String token(String user) {
        var res = rest.postForEntity("/api/auth/login",
                Map.of("username", user, "password", user), Map.class);
        return (String) res.getBody().get("token");
    }

    private HttpHeaders auth(String user) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setBearerAuth(token(user));
        return h;
    }

    private <T> T post(String path, Object body, String user, Class<T> type) {
        var res = rest.exchange(path, HttpMethod.POST,
                new HttpEntity<>(body, auth(user)), type);
        assertThat(res.getStatusCode().is2xxSuccessful())
                .as("POST %s -> %s : %s", path, res.getStatusCode(), res.getBody()).isTrue();
        return res.getBody();
    }

    /** Seeds an ADULT patient + a CREATED PREMATURE visit, returns [patientId, mrn, visitId]. */
    private String[] seedPrematureVisit() {
        Map<?, ?> patient = post("/api/patients", Map.of(
                "fullName", "Baby Test " + System.nanoTime(),
                "gender", "MALE", "dateOfBirth", "2026-05-01",
                "mobileNumber", "0770" + (System.nanoTime() % 10_000_000L), "vip", false),
                "receptionist", Map.class);
        String patientId = (String) patient.get("id");
        String mrn = (String) patient.get("mrn");
        Map<?, ?> visit = post("/api/visits",
                Map.of("patientId", patientId, "visitType", "PREMATURE"),
                "receptionist", Map.class);
        return new String[]{patientId, mrn, (String) visit.get("id")};
    }

    private String availableBedId() {
        return beds.findAllByOrderByCodeAsc().stream()
                .filter(b -> b.getStatus() == BedStatus.AVAILABLE && b.isActive())
                .findFirst().orElseThrow().getId().toString();
    }

    @Test
    void admit_then_approve_initial_marks_under_care_and_occupies_bed() {
        String[] s = seedPrematureVisit();
        String visitId = s[2];
        String bedId = availableBedId();

        Map<?, ?> admission = post("/api/premature/admissions",
                Map.of("visitId", visitId, "bedId", bedId, "stayValue", 3, "stayUnit", "DAYS"),
                "premature", Map.class);
        String admissionId = (String) admission.get("id");

        assertThat(beds.findById(UUID.fromString(bedId)).get().getStatus())
                .isEqualTo(BedStatus.PENDING_PAYMENT);
        assertThat(visits.findById(UUID.fromString(visitId)).get().getStatus())
                .isEqualTo(VisitStatus.AWAITING_PAYMENT);

        // Approve the INITIAL payment as cashier.
        var pending = payments.findAllByVisitIdOrderByCreatedAtDesc(UUID.fromString(visitId)).stream()
                .filter(p -> p.getStatus() == PaymentStatus.PENDING).findFirst().orElseThrow();
        post("/api/payments/" + pending.getId() + "/approve",
                Map.of("paymentMethod", "CASH"), "cashier", Map.class);

        // Bridges fire AFTER_COMMIT — assert with a short poll.
        await().atMost(ofSeconds(5)).untilAsserted(() -> {
            assertThat(admissions.findById(UUID.fromString(admissionId)).get().getStatus())
                    .isEqualTo(AdmissionStatus.UNDER_CARE);
            assertThat(beds.findById(UUID.fromString(bedId)).get().getStatus())
                    .isEqualTo(BedStatus.OCCUPIED);
            assertThat(visits.findById(UUID.fromString(visitId)).get().getStatus())
                    .isEqualTo(VisitStatus.IN_PROGRESS);
        });
    }

    @Test
    void admit_then_reject_initial_releases_bed_and_cancels() {
        String[] s = seedPrematureVisit();
        String visitId = s[2];
        String bedId = availableBedId();

        Map<?, ?> admission = post("/api/premature/admissions",
                Map.of("visitId", visitId, "bedId", bedId, "stayValue", 2, "stayUnit", "DAYS"),
                "premature", Map.class);
        String admissionId = (String) admission.get("id");

        var pending = payments.findAllByVisitIdOrderByCreatedAtDesc(UUID.fromString(visitId)).stream()
                .filter(p -> p.getStatus() == PaymentStatus.PENDING).findFirst().orElseThrow();
        post("/api/payments/" + pending.getId() + "/reject",
                Map.of("reason", "Cannot pay"), "cashier", Map.class);

        await().atMost(ofSeconds(5)).untilAsserted(() -> {
            assertThat(admissions.findById(UUID.fromString(admissionId)).get().getStatus())
                    .isEqualTo(AdmissionStatus.CANCELLED);
            assertThat(beds.findById(UUID.fromString(bedId)).get().getStatus())
                    .isEqualTo(BedStatus.AVAILABLE);
            assertThat(visits.findById(UUID.fromString(visitId)).get().getStatus())
                    .isEqualTo(VisitStatus.CANCELLED);
        });
    }
}
```

- [ ] **Step 2: Add Awaitility test dep to `backend/app/pom.xml`** (if not already managed)

```xml
        <dependency>
            <groupId>org.awaitility</groupId>
            <artifactId>awaitility</artifactId>
            <scope>test</scope>
        </dependency>
```

(Version managed by the Boot BOM.)

- [ ] **Step 3: Run — expect PASS**

Run: `cd backend && ./mvnw -q -pl app -am test -Dtest=AdmitFlowIT`
Expected: PASS (2 tests). If `/api/auth/login` payload differs, adjust to the real `AuthController` contract (check `backend/identity` login DTO).

- [ ] **Step 4: Commit**

```bash
git add backend/app/pom.xml backend/app/src/test/java/com/albudoor/hms/app/premature/AdmitFlowIT.java
git commit -m "test(premature): admit + initial-payment flow integration tests"
```

---

# PHASE 7 — Extend stay, finish treatment, discharge + P12b

### Task 7.1: extend-stay slice + list-admissions + enrich bed dashboard

**Files:**
- Create: `extendstay/{ExtendStayCommand,ExtendStayHandler,ExtendStayController}.java`
- Create: `listadmissions/{ListAdmissionsHandler,ListAdmissionsController}.java`
- Modify: `listbeds/ListBedsHandler.java` (attach occupant)

- [ ] **Step 1: extend-stay slice**

`extendstay/ExtendStayCommand.java`:

```java
package com.albudoor.hms.premature.extendstay;

import com.albudoor.hms.premature.domain.StayUnit;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ExtendStayCommand(
        @Positive int value,
        @NotNull StayUnit unit
) {}
```

`extendstay/ExtendStayHandler.java`:

```java
package com.albudoor.hms.premature.extendstay;

import com.albudoor.hms.platform.exception.NotFoundException;
import com.albudoor.hms.premature.domain.PrematureAdmission;
import com.albudoor.hms.premature.infrastructure.PrematureAdmissionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ExtendStayHandler {

    private final PrematureAdmissionRepository admissions;

    public ExtendStayHandler(PrematureAdmissionRepository admissions) {
        this.admissions = admissions;
    }

    @Transactional
    public PrematureAdmission handle(UUID admissionId, ExtendStayCommand cmd) {
        PrematureAdmission a = admissions.findById(admissionId)
                .orElseThrow(() -> new NotFoundException("Admission not found: " + admissionId));
        a.extendStay(cmd.value(), cmd.unit());
        return admissions.save(a);
    }
}
```

`extendstay/ExtendStayController.java`:

```java
package com.albudoor.hms.premature.extendstay;

import com.albudoor.hms.premature.api.AdmissionResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/premature/admissions")
public class ExtendStayController {

    private final ExtendStayHandler handler;

    public ExtendStayController(ExtendStayHandler handler) {
        this.handler = handler;
    }

    @PostMapping("/{id}/extend-stay")
    @PreAuthorize("hasAnyRole('PREMATURE_STAFF', 'NURSE', 'DOCTOR', 'ADMIN')")
    public AdmissionResponse extend(@PathVariable UUID id, @Valid @RequestBody ExtendStayCommand cmd) {
        return AdmissionResponse.from(handler.handle(id, cmd));
    }
}
```

- [ ] **Step 2: list-admissions slice**

`listadmissions/ListAdmissionsHandler.java`:

```java
package com.albudoor.hms.premature.listadmissions;

import com.albudoor.hms.premature.api.AdmissionResponse;
import com.albudoor.hms.premature.domain.AdmissionStatus;
import com.albudoor.hms.premature.infrastructure.PrematureAdmissionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

@Service
public class ListAdmissionsHandler {

    private static final List<AdmissionStatus> ACTIVE = List.of(
            AdmissionStatus.AWAITING_ADMISSION_PAYMENT,
            AdmissionStatus.UNDER_CARE,
            AdmissionStatus.TREATMENT_FINISHED,
            AdmissionStatus.AWAITING_DISCHARGE_PAYMENT);

    private final PrematureAdmissionRepository admissions;

    public ListAdmissionsHandler(PrematureAdmissionRepository admissions) {
        this.admissions = admissions;
    }

    @Transactional(readOnly = true)
    public List<AdmissionResponse> list(AdmissionStatus status) {
        List<AdmissionStatus> filter = (status == null) ? ACTIVE : List.of(status);
        return admissions.findAllByStatusInOrderByAdmittedAtDesc(filter)
                .stream().map(AdmissionResponse::from).toList();
    }
}
```

`listadmissions/ListAdmissionsController.java`:

```java
package com.albudoor.hms.premature.listadmissions;

import com.albudoor.hms.premature.api.AdmissionResponse;
import com.albudoor.hms.premature.domain.AdmissionStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/premature/admissions")
@PreAuthorize("isAuthenticated()")
public class ListAdmissionsController {

    private final ListAdmissionsHandler handler;

    public ListAdmissionsController(ListAdmissionsHandler handler) {
        this.handler = handler;
    }

    @GetMapping
    public List<AdmissionResponse> list(
            @RequestParam(value = "status", required = false) AdmissionStatus status) {
        return handler.list(status);
    }
}
```

- [ ] **Step 3: Enrich `ListBedsHandler` with occupant info**

Replace `listbeds/ListBedsHandler.java` with:

```java
package com.albudoor.hms.premature.listbeds;

import com.albudoor.hms.premature.api.BedResponse;
import com.albudoor.hms.premature.domain.AdmissionStatus;
import com.albudoor.hms.premature.domain.Bed;
import com.albudoor.hms.premature.domain.BedStatus;
import com.albudoor.hms.premature.domain.PrematureAdmission;
import com.albudoor.hms.premature.infrastructure.BedRepository;
import com.albudoor.hms.premature.infrastructure.PrematureAdmissionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ListBedsHandler {

    private static final List<AdmissionStatus> ACTIVE = List.of(
            AdmissionStatus.AWAITING_ADMISSION_PAYMENT,
            AdmissionStatus.UNDER_CARE,
            AdmissionStatus.TREATMENT_FINISHED,
            AdmissionStatus.AWAITING_DISCHARGE_PAYMENT);

    private final BedRepository beds;
    private final PrematureAdmissionRepository admissions;

    public ListBedsHandler(BedRepository beds, PrematureAdmissionRepository admissions) {
        this.beds = beds;
        this.admissions = admissions;
    }

    @Transactional(readOnly = true)
    public List<BedResponse> list() {
        return beds.findAllByOrderByCodeAsc().stream().map(this::toResponse).toList();
    }

    private BedResponse toResponse(Bed bed) {
        BedResponse base = BedResponse.from(bed);
        if (bed.getStatus() == BedStatus.AVAILABLE) {
            return base;
        }
        return admissions.findByBedIdAndStatusIn(bed.getId(), ACTIVE)
                .map(a -> base.withOccupant(toOccupant(a)))
                .orElse(base);
    }

    private BedResponse.Occupant toOccupant(PrematureAdmission a) {
        return new BedResponse.Occupant(
                a.getId(), a.getVisitId(), a.getVisitDisplayId(),
                a.getPatientName(), a.getPatientMrn(),
                a.getStatus().name(), a.getStayExpiresAt());
    }
}
```

- [ ] **Step 4: Compile**

Run: `cd backend && ./mvnw -q -pl premature test-compile`
Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add backend/premature/src/main/java/com/albudoor/hms/premature/extendstay backend/premature/src/main/java/com/albudoor/hms/premature/listadmissions backend/premature/src/main/java/com/albudoor/hms/premature/listbeds/ListBedsHandler.java
git commit -m "feat(premature): extend-stay, list-admissions, bed dashboard occupants"
```

### Task 7.2: finish-treatment slice (generates FINAL payment)

**Files:**
- Create: `finishtreatment/{FinishTreatmentHandler,FinishTreatmentController}.java`

- [ ] **Step 1: Implement `FinishTreatmentHandler`**

`finishtreatment/FinishTreatmentHandler.java`:

```java
package com.albudoor.hms.premature.finishtreatment;

import com.albudoor.hms.cashier.createpayment.CreatePaymentCommand;
import com.albudoor.hms.cashier.createpayment.CreatePaymentHandler;
import com.albudoor.hms.cashier.domain.Payment;
import com.albudoor.hms.cashier.domain.PaymentStage;
import com.albudoor.hms.catalogue.domain.ServiceCategory;
import com.albudoor.hms.catalogue.domain.ServiceItem;
import com.albudoor.hms.catalogue.infrastructure.ServiceItemRepository;
import com.albudoor.hms.platform.exception.DomainException;
import com.albudoor.hms.platform.exception.NotFoundException;
import com.albudoor.hms.premature.domain.PrematureAdmission;
import com.albudoor.hms.premature.infrastructure.PrematureAdmissionRepository;
import com.albudoor.hms.visitmanagement.domain.Visit;
import com.albudoor.hms.visitmanagement.domain.VisitStatus;
import com.albudoor.hms.visitmanagement.infrastructure.VisitRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class FinishTreatmentHandler {

    static final String DISCHARGE_ITEM_CODE = "PREM-DIS";

    private final PrematureAdmissionRepository admissions;
    private final VisitRepository visits;
    private final ServiceItemRepository catalogue;
    private final CreatePaymentHandler createPayment;
    private final ApplicationEventPublisher events;

    public FinishTreatmentHandler(
            PrematureAdmissionRepository admissions,
            VisitRepository visits,
            ServiceItemRepository catalogue,
            CreatePaymentHandler createPayment,
            ApplicationEventPublisher events
    ) {
        this.admissions = admissions;
        this.visits = visits;
        this.catalogue = catalogue;
        this.createPayment = createPayment;
        this.events = events;
    }

    @Transactional
    public PrematureAdmission handle(UUID admissionId) {
        PrematureAdmission admission = admissions.findById(admissionId)
                .orElseThrow(() -> new NotFoundException("Admission not found: " + admissionId));

        // NOTE: the "block finish if lab/radiology results pending" gate is sub-project C.
        admission.finishTreatment(); // UNDER_CARE -> TREATMENT_FINISHED
        admissions.save(admission);

        Visit visit = visits.findById(admission.getVisitId())
                .orElseThrow(() -> new NotFoundException("Visit not found: " + admission.getVisitId()));
        visit.transitionTo(VisitStatus.TREATMENT_FINISHED);
        visit.pullDomainEvents().forEach(events::publishEvent);

        ServiceItem dischargeFee = catalogue
                .findByCategoryAndCode(ServiceCategory.PREMATURE, DISCHARGE_ITEM_CODE)
                .orElseThrow(() -> new DomainException("DISCHARGE_FEE_MISSING",
                        "Catalogue item " + DISCHARGE_ITEM_CODE + " is not configured"));
        Payment payment = createPayment.handle(new CreatePaymentCommand(
                visit.getId(), PaymentStage.FINAL,
                List.of(new CreatePaymentCommand.Line(dischargeFee.getId(), 1)), null));

        admission.scheduleDischargePayment(payment.getId()); // -> AWAITING_DISCHARGE_PAYMENT
        admissions.save(admission);

        visit.transitionTo(VisitStatus.AWAITING_FINAL_PAYMENT);
        visit.pullDomainEvents().forEach(events::publishEvent);

        return admission;
    }
}
```

- [ ] **Step 2: Implement `FinishTreatmentController`**

`finishtreatment/FinishTreatmentController.java`:

```java
package com.albudoor.hms.premature.finishtreatment;

import com.albudoor.hms.premature.api.AdmissionResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/premature/admissions")
public class FinishTreatmentController {

    private final FinishTreatmentHandler handler;

    public FinishTreatmentController(FinishTreatmentHandler handler) {
        this.handler = handler;
    }

    @PostMapping("/{id}/finish-treatment")
    @PreAuthorize("hasAnyRole('PREMATURE_STAFF', 'DOCTOR', 'ADMIN')")
    public AdmissionResponse finish(@PathVariable UUID id) {
        return AdmissionResponse.from(handler.handle(id));
    }
}
```

- [ ] **Step 3: Compile + commit**

Run: `cd backend && ./mvnw -q -pl premature test-compile` → BUILD SUCCESS.

```bash
git add backend/premature/src/main/java/com/albudoor/hms/premature/finishtreatment
git commit -m "feat(premature): finish-treatment slice (generates FINAL discharge payment)"
```

### Task 7.3: FINAL approve/reject in the bridge + P12b guard in the generic bridge

**Files:**
- Modify: `bridge/PrematurePaymentBridge.java`
- Modify: `backend/app/src/main/java/com/albudoor/hms/app/PaymentVisitBridge.java`

- [ ] **Step 1: Add FINAL handling to `PrematurePaymentBridge.onApproved`**

In `onApproved`, after the INITIAL block, add:

```java
        if (event.stage() == PaymentStage.FINAL) {
            admissions.findByFinalPaymentId(event.paymentId()).ifPresent(admission -> {
                admission.close();
                admissions.save(admission);
                beds.findById(admission.getBedId()).ifPresent(bed -> {
                    bed.discharge();
                    beds.save(bed);
                });
                log.info("Premature admission {} CLOSED, bed {} discharged (final payment {})",
                        admission.getId(), admission.getBedCode(), event.paymentId());
            });
        }
```

- [ ] **Step 2: Document the FINAL no-op in `onRejected`**

In `onRejected`, after the INITIAL block, add (explicit no-op for clarity):

```java
        // FINAL rejection (P12b): the generic PaymentVisitBridge is guarded to skip PREMATURE
        // visits, so the visit stays AWAITING_FINAL_PAYMENT and the admission stays
        // AWAITING_DISCHARGE_PAYMENT — the case remains open and a new FINAL payment can be
        // issued via finish-treatment retry. No state change here by design.
```

- [ ] **Step 3: Guard the generic bridge against PREMATURE final-reject**

In `backend/app/.../PaymentVisitBridge.java`, method `onRejected`, change the early body so PREMATURE visits are excluded from the `OUTSTANDING_BALANCE` transition. Current:

```java
        if (event.stage() != PaymentStage.FINAL) return;

        Visit visit = visits.findById(event.visitId()).orElse(null);
        if (visit == null) return;
        if (visit.getStatus() != VisitStatus.AWAITING_FINAL_PAYMENT) return;
```

Replace with:

```java
        if (event.stage() != PaymentStage.FINAL) return;

        Visit visit = visits.findById(event.visitId()).orElse(null);
        if (visit == null) return;
        // Premature discharge follows BRD P12b: a rejected final payment leaves the case OPEN
        // and re-issuable, rather than moving to OUTSTANDING_BALANCE. The premature bridge owns
        // this; do not transition premature visits here.
        if (visit.getVisitType() == com.albudoor.hms.visitmanagement.domain.VisitType.PREMATURE) return;
        if (visit.getStatus() != VisitStatus.AWAITING_FINAL_PAYMENT) return;
```

- [ ] **Step 4: Compile**

Run: `cd backend && ./mvnw -q -pl app -am test-compile`
Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add backend/premature/src/main/java/com/albudoor/hms/premature/bridge/PrematurePaymentBridge.java backend/app/src/main/java/com/albudoor/hms/app/PaymentVisitBridge.java
git commit -m "feat(premature): FINAL approve closes case; guard generic bridge for P12b retry"
```

### Task 7.4: Discharge-flow integration test (incl. P12b retry)

**Files:**
- Create: `backend/app/src/test/java/com/albudoor/hms/app/premature/DischargeFlowIT.java`

- [ ] **Step 1: Write the IT** (reuses the helper pattern from `AdmitFlowIT`)

```java
package com.albudoor.hms.app.premature;

import com.albudoor.hms.app.IntegrationTest;
import com.albudoor.hms.cashier.domain.PaymentStage;
import com.albudoor.hms.cashier.domain.PaymentStatus;
import com.albudoor.hms.cashier.infrastructure.PaymentRepository;
import com.albudoor.hms.premature.domain.AdmissionStatus;
import com.albudoor.hms.premature.domain.BedStatus;
import com.albudoor.hms.premature.infrastructure.BedRepository;
import com.albudoor.hms.premature.infrastructure.PrematureAdmissionRepository;
import com.albudoor.hms.visitmanagement.domain.VisitStatus;
import com.albudoor.hms.visitmanagement.infrastructure.VisitRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

import java.util.Map;
import java.util.UUID;

import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class DischargeFlowIT extends IntegrationTest {

    @Autowired TestRestTemplate rest;
    @Autowired BedRepository beds;
    @Autowired PrematureAdmissionRepository admissions;
    @Autowired VisitRepository visits;
    @Autowired PaymentRepository payments;

    private String token(String user) {
        var res = rest.postForEntity("/api/auth/login",
                Map.of("username", user, "password", user), Map.class);
        return (String) res.getBody().get("token");
    }

    private HttpHeaders auth(String user) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setBearerAuth(token(user));
        return h;
    }

    private <T> T post(String path, Object body, String user, Class<T> type) {
        var res = rest.exchange(path, HttpMethod.POST,
                new HttpEntity<>(body == null ? Map.of() : body, auth(user)), type);
        assertThat(res.getStatusCode().is2xxSuccessful())
                .as("POST %s -> %s : %s", path, res.getStatusCode(), res.getBody()).isTrue();
        return res.getBody();
    }

    /** Admit + approve initial → returns [admissionId, visitId, bedId]. */
    private String[] admitUnderCare() {
        Map<?, ?> patient = post("/api/patients", Map.of(
                "fullName", "Baby D " + System.nanoTime(), "gender", "FEMALE",
                "dateOfBirth", "2026-05-01",
                "mobileNumber", "0771" + (System.nanoTime() % 10_000_000L), "vip", false),
                "receptionist", Map.class);
        Map<?, ?> visit = post("/api/visits",
                Map.of("patientId", patient.get("id"), "visitType", "PREMATURE"),
                "receptionist", Map.class);
        String visitId = (String) visit.get("id");
        String bedId = beds.findAllByOrderByCodeAsc().stream()
                .filter(b -> b.getStatus() == BedStatus.AVAILABLE && b.isActive())
                .findFirst().orElseThrow().getId().toString();
        Map<?, ?> adm = post("/api/premature/admissions",
                Map.of("visitId", visitId, "bedId", bedId, "stayValue", 2, "stayUnit", "DAYS"),
                "premature", Map.class);
        var initial = payments.findAllByVisitIdOrderByCreatedAtDesc(UUID.fromString(visitId)).stream()
                .filter(p -> p.getStatus() == PaymentStatus.PENDING).findFirst().orElseThrow();
        post("/api/payments/" + initial.getId() + "/approve", Map.of("paymentMethod", "CASH"),
                "cashier", Map.class);
        return new String[]{(String) adm.get("id"), visitId, bedId};
    }

    private UUID pendingFinal(String visitId) {
        return payments.findAllByVisitIdOrderByCreatedAtDesc(UUID.fromString(visitId)).stream()
                .filter(p -> p.getStage() == PaymentStage.FINAL && p.getStatus() == PaymentStatus.PENDING)
                .findFirst().orElseThrow().getId();
    }

    @Test
    void finish_then_approve_final_closes_case_and_discharges_bed() {
        String[] s = admitUnderCare();
        String admissionId = s[0], visitId = s[1], bedId = s[2];
        await().atMost(ofSeconds(5)).untilAsserted(() ->
                assertThat(admissions.findById(UUID.fromString(admissionId)).get().getStatus())
                        .isEqualTo(AdmissionStatus.UNDER_CARE));

        post("/api/premature/admissions/" + admissionId + "/finish-treatment", null, "premature", Map.class);
        assertThat(visits.findById(UUID.fromString(visitId)).get().getStatus())
                .isEqualTo(VisitStatus.AWAITING_FINAL_PAYMENT);

        post("/api/payments/" + pendingFinal(visitId) + "/approve",
                Map.of("paymentMethod", "CASH"), "cashier", Map.class);

        await().atMost(ofSeconds(5)).untilAsserted(() -> {
            assertThat(admissions.findById(UUID.fromString(admissionId)).get().getStatus())
                    .isEqualTo(AdmissionStatus.CLOSED);
            assertThat(beds.findById(UUID.fromString(bedId)).get().getStatus())
                    .isEqualTo(BedStatus.AVAILABLE);
            assertThat(visits.findById(UUID.fromString(visitId)).get().getStatus())
                    .isEqualTo(VisitStatus.COMPLETED);
        });
    }

    @Test
    void rejected_final_payment_keeps_case_open_for_retry_p12b() {
        String[] s = admitUnderCare();
        String admissionId = s[0], visitId = s[1];
        await().atMost(ofSeconds(5)).untilAsserted(() ->
                assertThat(admissions.findById(UUID.fromString(admissionId)).get().getStatus())
                        .isEqualTo(AdmissionStatus.UNDER_CARE));

        post("/api/premature/admissions/" + admissionId + "/finish-treatment", null, "premature", Map.class);
        post("/api/payments/" + pendingFinal(visitId) + "/reject",
                Map.of("reason", "Family will pay tomorrow"), "cashier", Map.class);

        // P12b: case stays OPEN; visit stays AWAITING_FINAL_PAYMENT (NOT OUTSTANDING_BALANCE).
        await().atMost(ofSeconds(5)).untilAsserted(() -> {
            assertThat(admissions.findById(UUID.fromString(admissionId)).get().getStatus())
                    .isEqualTo(AdmissionStatus.AWAITING_DISCHARGE_PAYMENT);
            assertThat(visits.findById(UUID.fromString(visitId)).get().getStatus())
                    .isEqualTo(VisitStatus.AWAITING_FINAL_PAYMENT);
        });
    }
}
```

- [ ] **Step 2: Run — expect PASS**

Run: `cd backend && ./mvnw -q -pl app -am test -Dtest=DischargeFlowIT`
Expected: PASS (2 tests).

- [ ] **Step 3: Commit**

```bash
git add backend/app/src/test/java/com/albudoor/hms/app/premature/DischargeFlowIT.java
git commit -m "test(premature): discharge flow + P12b retry integration tests"
```

---

# PHASE 8 — ArchUnit boundary rule

### Task 8.1: Add `layeredWithinPremature`

**Files:**
- Modify: `backend/app/src/test/java/com/albudoor/hms/app/ArchitectureTest.java`

- [ ] **Step 1: Add the test method** (after `layeredWithinPatientRegistry`)

```java
    @Test
    void layeredWithinPremature() {
        ArchRule rule = layeredArchitecture()
                .consideringOnlyDependenciesInLayers()
                .layer("domain").definedBy("..premature.domain..")
                .layer("application").definedBy(
                        "..premature.createbed..",
                        "..premature.updatebed..",
                        "..premature.listbeds..",
                        "..premature.admitpatient..",
                        "..premature.extendstay..",
                        "..premature.finishtreatment..",
                        "..premature.listadmissions..",
                        "..premature.bridge..")
                .layer("infrastructure").definedBy("..premature.infrastructure..")
                .whereLayer("application").mayOnlyAccessLayers("domain", "infrastructure")
                .whereLayer("infrastructure").mayOnlyAccessLayers("domain");
        rule.check(CLASSES);
    }
```

> If this rule fails because a slice legitimately depends on another module's application layer (e.g. `admitpatient` → cashier `CreatePaymentHandler`), use `.consideringOnlyDependenciesInLayers()` (already present) which restricts the check to the declared premature layers only — cross-module deps are ignored. Confirm the rule passes; if a premature slice imports a cashier/catalogue/visit class, that's outside the declared layers and thus allowed.

- [ ] **Step 2: Run the full ArchUnit suite**

Run: `cd backend && ./mvnw -q -pl app -am test -Dtest=ArchitectureTest`
Expected: PASS (all rules, including the new one).

- [ ] **Step 3: Commit**

```bash
git add backend/app/src/test/java/com/albudoor/hms/app/ArchitectureTest.java
git commit -m "test(premature): ArchUnit slice-boundary rule for premature module"
```

---

# PHASE 9 — Frontend

### Task 9.1: Premature API client

**Files:**
- Create: `frontend/src/features/premature/api.ts`

- [ ] **Step 1: Implement `api.ts`**

```typescript
import { api } from '@/shared/api/client';
import type { VisitType } from '@/features/reception/visits/api';

export type BedStatus = 'AVAILABLE' | 'PENDING_PAYMENT' | 'OCCUPIED';
export type StayUnit = 'HOURS' | 'DAYS';
export type AdmissionStatus =
  | 'AWAITING_ADMISSION_PAYMENT'
  | 'UNDER_CARE'
  | 'TREATMENT_FINISHED'
  | 'AWAITING_DISCHARGE_PAYMENT'
  | 'CLOSED'
  | 'CANCELLED';

export type BedOccupant = {
  admissionId: string;
  visitId: string;
  visitDisplayId: string;
  patientName: string;
  patientMrn: string;
  admissionStatus: AdmissionStatus;
  stayExpiresAt: string;
};

export type Bed = {
  id: string;
  code: string;
  room: string | null;
  status: BedStatus;
  active: boolean;
  occupant: BedOccupant | null;
};

export type Admission = {
  id: string;
  visitId: string;
  visitDisplayId: string;
  patientId: string;
  patientMrn: string;
  patientName: string;
  bedId: string;
  bedCode: string;
  status: AdmissionStatus;
  stayValue: number;
  stayUnit: StayUnit;
  admittedAt: string;
  stayExpiresAt: string;
  treatmentFinishedAt: string | null;
  closedAt: string | null;
  initialPaymentId: string | null;
  finalPaymentId: string | null;
};

export type PrematureVisit = {
  id: string;
  visitDisplayId: string;
  patientId: string;
  patientMrn: string;
  patientName: string;
  visitType: VisitType;
  status: string;
  startedAt: string;
};

export async function listBeds(): Promise<Bed[]> {
  const res = await api.get('/premature/beds');
  return res.data;
}

export async function createBed(body: { code: string; room?: string }): Promise<Bed> {
  const res = await api.post('/premature/beds', body);
  return res.data;
}

export async function updateBed(id: string, body: { room?: string; active: boolean }): Promise<Bed> {
  const res = await api.put(`/premature/beds/${id}`, body);
  return res.data;
}

export async function listAdmissions(status?: AdmissionStatus): Promise<Admission[]> {
  const params = status ? { status } : {};
  const res = await api.get('/premature/admissions', { params });
  return res.data;
}

/** Incoming queue = PREMATURE visits not yet admitted (CREATED). */
export async function listIncomingPremature(): Promise<PrematureVisit[]> {
  const res = await api.get('/visits', { params: { type: 'PREMATURE', status: 'CREATED', size: 50 } });
  return (res.data.content ?? []) as PrematureVisit[];
}

export async function admitPatient(body: {
  visitId: string;
  bedId: string;
  stayValue: number;
  stayUnit: StayUnit;
}): Promise<Admission> {
  const res = await api.post('/premature/admissions', body);
  return res.data;
}

export async function extendStay(admissionId: string, value: number, unit: StayUnit): Promise<Admission> {
  const res = await api.post(`/premature/admissions/${admissionId}/extend-stay`, { value, unit });
  return res.data;
}

export async function finishTreatment(admissionId: string): Promise<Admission> {
  const res = await api.post(`/premature/admissions/${admissionId}/finish-treatment`, {});
  return res.data;
}
```

- [ ] **Step 2: Type-check + commit**

Run: `cd frontend && npx tsc -b` → no errors for this file.

```bash
git add frontend/src/features/premature/api.ts
git commit -m "feat(premature-ui): api client for beds + admissions"
```

### Task 9.2: Premature workspace page

**Files:**
- Create: `frontend/src/features/premature/PrematureWorkspacePage.tsx`

> Mirror `DepartmentWorkspace.tsx` conventions: `PageHeader`, `Card`, react-query with `refetchInterval`, `useMutation` + `invalidateQueries`, toast on success/error via `extractApiError`. Compute the "expiring soon" badge client-side from `stayExpiresAt` (decision: computed-on-read).

- [ ] **Step 1: Implement the page**

```tsx
import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import toast from 'react-hot-toast';
import { Baby, BedDouble, Clock, CheckCircle2 } from 'lucide-react';
import { extractApiError } from '@/shared/api/client';
import {
  listBeds, listAdmissions, listIncomingPremature, admitPatient, extendStay, finishTreatment,
  type Bed, type Admission, type PrematureVisit, type StayUnit,
} from './api';

function isExpiringSoon(iso: string): boolean {
  const expires = new Date(iso).getTime();
  return expires - Date.now() < 2 * 60 * 60 * 1000; // within 2h
}

export function PrematureWorkspacePage() {
  const { t } = useTranslation();
  const qc = useQueryClient();
  const [admitFor, setAdmitFor] = useState<PrematureVisit | null>(null);

  const { data: beds } = useQuery({ queryKey: ['prem-beds'], queryFn: listBeds, refetchInterval: 15000 });
  const { data: admissions } = useQuery({
    queryKey: ['prem-admissions'], queryFn: () => listAdmissions(), refetchInterval: 15000,
  });
  const { data: incoming } = useQuery({
    queryKey: ['prem-incoming'], queryFn: listIncomingPremature, refetchInterval: 20000,
  });

  const admittedVisitIds = useMemo(
    () => new Set((admissions ?? []).map((a) => a.visitId)),
    [admissions],
  );
  const queue = useMemo(
    () => (incoming ?? []).filter((v) => !admittedVisitIds.has(v.id)),
    [incoming, admittedVisitIds],
  );

  const invalidate = async () => {
    await Promise.all([
      qc.invalidateQueries({ queryKey: ['prem-beds'] }),
      qc.invalidateQueries({ queryKey: ['prem-admissions'] }),
      qc.invalidateQueries({ queryKey: ['prem-incoming'] }),
      qc.invalidateQueries({ queryKey: ['payments'] }),
      qc.invalidateQueries({ queryKey: ['visits'] }),
    ]);
  };

  const finishMut = useMutation({
    mutationFn: (id: string) => finishTreatment(id),
    onSuccess: async () => { toast.success(t('premature.toast.finished')); await invalidate(); },
    onError: (e) => toast.error(extractApiError(e)?.message ?? t('premature.toast.error')),
  });

  const extendMut = useMutation({
    mutationFn: ({ id, value, unit }: { id: string; value: number; unit: StayUnit }) =>
      extendStay(id, value, unit),
    onSuccess: async () => { toast.success(t('premature.toast.extended')); await invalidate(); },
    onError: (e) => toast.error(extractApiError(e)?.message ?? t('premature.toast.error')),
  });

  return (
    <div className="space-y-6 p-1">
      <header className="flex items-center gap-3">
        <span className="flex h-10 w-10 items-center justify-center rounded-lg bg-brand-50 text-brand-700">
          <Baby size={20} />
        </span>
        <div>
          <h1 className="text-lg font-semibold text-ink-900">{t('premature.title')}</h1>
          <p className="text-sm text-ink-500">{t('premature.subtitle')}</p>
        </div>
      </header>

      {/* Incoming queue */}
      <section className="rounded-xl border border-ink-100 bg-white" data-testid="prem-incoming">
        <div className="border-b border-ink-100 px-5 py-3">
          <h2 className="text-sm font-semibold text-ink-900">{t('premature.incoming')}</h2>
        </div>
        {queue.length === 0 ? (
          <p className="p-5 text-sm text-ink-500">{t('premature.noIncoming')}</p>
        ) : (
          <ul className="divide-y divide-ink-100">
            {queue.map((v) => (
              <li key={v.id} className="flex items-center justify-between px-5 py-3">
                <div>
                  <div className="font-medium text-ink-900">{v.patientName}</div>
                  <div className="font-mono text-xs text-ink-500">{v.patientMrn} · {v.visitDisplayId}</div>
                </div>
                <button
                  type="button"
                  onClick={() => setAdmitFor(v)}
                  className="rounded-md bg-brand-600 px-3 py-1.5 text-xs font-medium text-white hover:bg-brand-700"
                  data-testid={`admit-${v.id}`}
                >
                  {t('premature.assignBed')}
                </button>
              </li>
            ))}
          </ul>
        )}
      </section>

      {/* Bed dashboard */}
      <section className="rounded-xl border border-ink-100 bg-white" data-testid="prem-beds">
        <div className="border-b border-ink-100 px-5 py-3">
          <h2 className="text-sm font-semibold text-ink-900">{t('premature.bedDashboard')}</h2>
        </div>
        <div className="grid grid-cols-1 gap-3 p-4 sm:grid-cols-2 lg:grid-cols-3">
          {(beds ?? []).map((bed) => (
            <BedCard key={bed.id} bed={bed} onFinish={finishMut.mutate} onExtend={extendMut.mutate} t={t} />
          ))}
        </div>
      </section>

      {admitFor && (
        <AdmitDialog
          visit={admitFor}
          beds={(beds ?? []).filter((b) => b.status === 'AVAILABLE' && b.active)}
          onClose={() => setAdmitFor(null)}
          onAdmitted={async () => { setAdmitFor(null); await invalidate(); }}
          t={t}
        />
      )}
    </div>
  );
}

function BedCard({
  bed, onFinish, onExtend, t,
}: {
  bed: Bed;
  onFinish: (id: string) => void;
  onExtend: (a: { id: string; value: number; unit: StayUnit }) => void;
  t: (k: string) => string;
}) {
  const occ = bed.occupant;
  const expiring = occ && isExpiringSoon(occ.stayExpiresAt);
  return (
    <div className="rounded-lg border border-ink-100 p-3" data-testid={`bed-${bed.code}`}>
      <div className="flex items-center justify-between">
        <span className="flex items-center gap-1.5 font-mono text-sm font-semibold text-ink-900">
          <BedDouble size={14} /> {bed.code}
        </span>
        <span
          className={
            'rounded-full px-2 py-0.5 text-[11px] font-medium ' +
            (bed.status === 'AVAILABLE'
              ? 'bg-emerald-50 text-emerald-700'
              : bed.status === 'OCCUPIED'
              ? 'bg-brand-50 text-brand-700'
              : 'bg-amber-50 text-amber-700')
          }
          data-testid={`bed-status-${bed.code}`}
        >
          {t(`premature.bedStatus.${bed.status}`)}
        </span>
      </div>
      {occ && (
        <div className="mt-2 text-xs text-ink-600">
          <div className="font-medium text-ink-900">{occ.patientName}</div>
          <div className="font-mono text-[11px] text-ink-500">{occ.patientMrn}</div>
          {expiring && (
            <div className="mt-1 inline-flex items-center gap-1 rounded bg-red-50 px-1.5 py-0.5 text-[11px] font-medium text-red-700">
              <Clock size={11} /> {t('premature.expiringSoon')}
            </div>
          )}
          {occ.admissionStatus === 'UNDER_CARE' && (
            <div className="mt-2 flex gap-2">
              <button
                type="button"
                onClick={() => onExtend({ id: occ.admissionId, value: 1, unit: 'DAYS' })}
                className="rounded border border-ink-200 px-2 py-1 text-[11px] hover:bg-ink-50"
                data-testid={`extend-${bed.code}`}
              >
                {t('premature.extend1Day')}
              </button>
              <button
                type="button"
                onClick={() => onFinish(occ.admissionId)}
                className="inline-flex items-center gap-1 rounded bg-emerald-600 px-2 py-1 text-[11px] font-medium text-white hover:bg-emerald-700"
                data-testid={`finish-${bed.code}`}
              >
                <CheckCircle2 size={11} /> {t('premature.finishTreatment')}
              </button>
            </div>
          )}
          {occ.admissionStatus === 'AWAITING_DISCHARGE_PAYMENT' && (
            <div className="mt-1 text-[11px] text-amber-700">{t('premature.awaitingDischarge')}</div>
          )}
        </div>
      )}
    </div>
  );
}

function AdmitDialog({
  visit, beds, onClose, onAdmitted, t,
}: {
  visit: PrematureVisit;
  beds: Bed[];
  onClose: () => void;
  onAdmitted: () => void;
  t: (k: string) => string;
}) {
  const [bedId, setBedId] = useState(beds[0]?.id ?? '');
  const [stayValue, setStayValue] = useState(3);
  const [stayUnit, setStayUnit] = useState<StayUnit>('DAYS');

  const mut = useMutation({
    mutationFn: () => admitPatient({ visitId: visit.id, bedId, stayValue, stayUnit }),
    onSuccess: async () => { toast.success(t('premature.toast.admitted')); onAdmitted(); },
    onError: (e) => toast.error(extractApiError(e)?.message ?? t('premature.toast.error')),
  });

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/30 p-4">
      <div className="w-full max-w-md rounded-xl bg-white p-5 shadow-xl" data-testid="admit-dialog">
        <h3 className="text-sm font-semibold text-ink-900">{t('premature.assignBed')} — {visit.patientName}</h3>
        <label className="mt-4 block text-xs font-medium text-ink-700">{t('premature.bed')}</label>
        <select
          value={bedId} onChange={(e) => setBedId(e.target.value)}
          className="mt-1 w-full rounded-md border border-ink-200 px-2 py-1.5 text-sm"
          data-testid="admit-bed-select"
        >
          {beds.map((b) => <option key={b.id} value={b.id}>{b.code}{b.room ? ` · ${b.room}` : ''}</option>)}
        </select>
        <div className="mt-3 flex gap-2">
          <div className="flex-1">
            <label className="block text-xs font-medium text-ink-700">{t('premature.stayValue')}</label>
            <input
              type="number" min={1} value={stayValue}
              onChange={(e) => setStayValue(Number(e.target.value))}
              className="mt-1 w-full rounded-md border border-ink-200 px-2 py-1.5 text-sm"
              data-testid="admit-stay-value"
            />
          </div>
          <div className="flex-1">
            <label className="block text-xs font-medium text-ink-700">{t('premature.stayUnit')}</label>
            <select
              value={stayUnit} onChange={(e) => setStayUnit(e.target.value as StayUnit)}
              className="mt-1 w-full rounded-md border border-ink-200 px-2 py-1.5 text-sm"
              data-testid="admit-stay-unit"
            >
              <option value="DAYS">{t('premature.days')}</option>
              <option value="HOURS">{t('premature.hours')}</option>
            </select>
          </div>
        </div>
        <div className="mt-5 flex justify-end gap-2">
          <button type="button" onClick={onClose} className="rounded-md px-3 py-1.5 text-sm text-ink-600 hover:bg-ink-100">
            {t('common.cancel')}
          </button>
          <button
            type="button" disabled={!bedId || mut.isPending} onClick={() => mut.mutate()}
            className="rounded-md bg-brand-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-brand-700 disabled:opacity-50"
            data-testid="admit-confirm"
          >
            {mut.isPending ? t('common.loading') : t('premature.confirmAdmit')}
          </button>
        </div>
      </div>
    </div>
  );
}
```

- [ ] **Step 2: Type-check + commit**

Run: `cd frontend && npx tsc -b` (errors about missing i18n keys won't appear — t() is untyped; ensure no TS errors).

```bash
git add frontend/src/features/premature/PrematureWorkspacePage.tsx
git commit -m "feat(premature-ui): workspace page (incoming queue + bed dashboard + actions)"
```

### Task 9.3: Bed admin page

**Files:**
- Create: `frontend/src/features/premature/BedAdminPage.tsx`

- [ ] **Step 1: Implement a minimal admin CRUD page**

```tsx
import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import toast from 'react-hot-toast';
import { extractApiError } from '@/shared/api/client';
import { listBeds, createBed, updateBed } from './api';

export function BedAdminPage() {
  const { t } = useTranslation();
  const qc = useQueryClient();
  const [code, setCode] = useState('');
  const [room, setRoom] = useState('');

  const { data: beds } = useQuery({ queryKey: ['prem-beds-admin'], queryFn: listBeds });

  const refresh = () => qc.invalidateQueries({ queryKey: ['prem-beds-admin'] });

  const createMut = useMutation({
    mutationFn: () => createBed({ code: code.trim(), room: room.trim() || undefined }),
    onSuccess: async () => { toast.success(t('premature.toast.bedCreated')); setCode(''); setRoom(''); await refresh(); },
    onError: (e) => toast.error(extractApiError(e)?.message ?? t('premature.toast.error')),
  });

  const toggleMut = useMutation({
    mutationFn: ({ id, room, active }: { id: string; room: string | null; active: boolean }) =>
      updateBed(id, { room: room ?? undefined, active }),
    onSuccess: async () => { await refresh(); },
    onError: (e) => toast.error(extractApiError(e)?.message ?? t('premature.toast.error')),
  });

  return (
    <div className="space-y-6 p-1">
      <h1 className="text-lg font-semibold text-ink-900">{t('premature.bedAdmin')}</h1>

      <div className="flex flex-wrap items-end gap-2 rounded-xl border border-ink-100 bg-white p-4">
        <div>
          <label className="block text-xs font-medium text-ink-700">{t('premature.bedCode')}</label>
          <input value={code} onChange={(e) => setCode(e.target.value)}
            className="mt-1 rounded-md border border-ink-200 px-2 py-1.5 text-sm" data-testid="bed-code-input" />
        </div>
        <div>
          <label className="block text-xs font-medium text-ink-700">{t('premature.room')}</label>
          <input value={room} onChange={(e) => setRoom(e.target.value)}
            className="mt-1 rounded-md border border-ink-200 px-2 py-1.5 text-sm" data-testid="bed-room-input" />
        </div>
        <button type="button" disabled={!code.trim() || createMut.isPending} onClick={() => createMut.mutate()}
          className="rounded-md bg-brand-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-brand-700 disabled:opacity-50"
          data-testid="bed-create">
          {t('premature.addBed')}
        </button>
      </div>

      <table className="w-full rounded-xl border border-ink-100 bg-white text-sm">
        <thead className="border-b border-ink-100 text-[11px] uppercase text-ink-500">
          <tr><th className="px-4 py-2 text-start">{t('premature.bedCode')}</th>
            <th className="px-4 py-2 text-start">{t('premature.room')}</th>
            <th className="px-4 py-2 text-start">{t('premature.status')}</th>
            <th className="px-4 py-2 text-start">{t('common.actions')}</th></tr>
        </thead>
        <tbody className="divide-y divide-ink-100">
          {(beds ?? []).map((b) => (
            <tr key={b.id} data-testid={`admin-bed-${b.code}`}>
              <td className="px-4 py-2 font-mono">{b.code}</td>
              <td className="px-4 py-2">{b.room ?? '—'}</td>
              <td className="px-4 py-2">{t(`premature.bedStatus.${b.status}`)} {b.active ? '' : `(${t('premature.inactive')})`}</td>
              <td className="px-4 py-2">
                <button type="button"
                  onClick={() => toggleMut.mutate({ id: b.id, room: b.room, active: !b.active })}
                  className="rounded border border-ink-200 px-2 py-1 text-xs hover:bg-ink-50">
                  {b.active ? t('premature.deactivate') : t('premature.activate')}
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/features/premature/BedAdminPage.tsx
git commit -m "feat(premature-ui): bed admin CRUD page"
```

### Task 9.4: Wire routes, nav, and the reception visit-start button

**Files:**
- Modify: `frontend/src/App.tsx`, `frontend/src/shared/nav/routes.ts`, `frontend/src/features/patients/PatientProfilePage.tsx`

- [ ] **Step 1: `App.tsx` — import + routes**

Add imports near the other feature imports:

```tsx
import { PrematureWorkspacePage } from '@/features/premature/PrematureWorkspacePage';
import { BedAdminPage } from '@/features/premature/BedAdminPage';
```

Replace the line `<Route path="/departments/premature" element={<ComingSoonPage />} />` with:

```tsx
    <Route path="/departments/premature" element={<PrematureWorkspacePage />} />
    <Route path="/premature/beds" element={<BedAdminPage />} />
```

- [ ] **Step 2: `routes.ts` — remove comingSoon + add bed-admin nav**

Change the premature nav item (remove `comingSoon: true`, gate to premature/admin):

```typescript
      { to: '/departments/premature', i18nKey: 'nav.premature', icon: Baby, roles: ['PREMATURE_STAFF', 'ADMIN', 'NURSE', 'DOCTOR'] },
```

In the `admin` group items, add after `catalogues`:

```typescript
      { to: '/premature/beds', i18nKey: 'nav.prematureBeds', icon: BedDouble, roles: ['ADMIN', 'PREMATURE_STAFF'] },
```

Add `BedDouble` to the lucide-react import at the top of `routes.ts`.

- [ ] **Step 3: `PatientProfilePage.tsx` — add PREMATURE to the start-visit options**

Change the hardcoded array (currently `(['LABORATORY', 'RADIOLOGY', 'ECO'] as VisitType[])`) to:

```tsx
      {(['LABORATORY', 'RADIOLOGY', 'ECO', 'PREMATURE'] as VisitType[]).map((vt) => {
```

And update the `onSuccess` navigation so a PREMATURE visit lands in the premature workspace. Change the `startVisitMut` `onSuccess` to:

```tsx
  const startVisitMut = useMutation({
    mutationFn: (visitType: VisitType) => createVisit(id!, visitType),
    onSuccess: async (visit) => {
      toast.success(`Visit ${visit.visitDisplayId} started`);
      await queryClient.invalidateQueries({ queryKey: ['clinical-history', id] });
      navigate(visit.visitType === 'PREMATURE' ? '/departments/premature' : '/reception/queue');
    },
    onError: (err) => toast.error(extractApiError(err)?.message ?? 'Could not start visit'),
  });
```

(`TYPE_ICON`/`TYPE_LABEL` already include `PREMATURE: Baby` / `'Premature'`, so no change there.)

- [ ] **Step 4: Type-check**

Run: `cd frontend && npx tsc -b`
Expected: no errors.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/App.tsx frontend/src/shared/nav/routes.ts frontend/src/features/patients/PatientProfilePage.tsx
git commit -m "feat(premature-ui): wire premature workspace + bed admin routes; PREMATURE start-visit"
```

### Task 9.5: i18n keys (en + ar)

**Files:**
- Modify: `frontend/src/shared/i18n/locales/en.ts`, `frontend/src/shared/i18n/locales/ar.ts`

- [ ] **Step 1: Add `nav.prematureBeds` to both locales**

In `en.ts` `nav`: add `prematureBeds: 'Premature beds',`
In `ar.ts` `nav`: add `prematureBeds: 'أسرّة الخدج',`

- [ ] **Step 2: Add a `premature` section to `en.ts`** (top-level key, e.g. after `patient`)

```typescript
  premature: {
    title: 'Premature unit',
    subtitle: 'Admit infants, manage beds and period of stay, and discharge.',
    incoming: 'Incoming — assign a bed',
    noIncoming: 'No premature patients waiting for a bed.',
    bedDashboard: 'Bed dashboard',
    bed: 'Bed',
    bedAdmin: 'Premature beds',
    bedCode: 'Bed code',
    room: 'Room',
    status: 'Status',
    addBed: 'Add bed',
    activate: 'Activate',
    deactivate: 'Deactivate',
    inactive: 'inactive',
    assignBed: 'Assign bed',
    confirmAdmit: 'Admit & send to cashier',
    stayValue: 'Period of stay',
    stayUnit: 'Unit',
    days: 'Days',
    hours: 'Hours',
    extend1Day: 'Extend +1 day',
    finishTreatment: 'Finish treatment',
    awaitingDischarge: 'Awaiting discharge payment',
    expiringSoon: 'Stay expiring soon',
    bedStatus: {
      AVAILABLE: 'Available',
      PENDING_PAYMENT: 'Pending payment',
      OCCUPIED: 'Occupied',
    },
    toast: {
      admitted: 'Patient admitted; initial payment sent to cashier',
      finished: 'Treatment finished; discharge payment sent to cashier',
      extended: 'Period of stay extended',
      bedCreated: 'Bed created',
      error: 'Action failed',
    },
  },
```

- [ ] **Step 3: Add the mirrored `premature` section to `ar.ts`**

```typescript
  premature: {
    title: 'وحدة الخدج',
    subtitle: 'إدخال الرضّع، إدارة الأسرّة ومدة الإقامة، والخروج.',
    incoming: 'الوافدون — خصّص سريراً',
    noIncoming: 'لا يوجد مرضى خدج بانتظار سرير.',
    bedDashboard: 'لوحة الأسرّة',
    bed: 'السرير',
    bedAdmin: 'أسرّة الخدج',
    bedCode: 'رمز السرير',
    room: 'الغرفة',
    status: 'الحالة',
    addBed: 'إضافة سرير',
    activate: 'تفعيل',
    deactivate: 'إلغاء التفعيل',
    inactive: 'غير مفعّل',
    assignBed: 'تخصيص سرير',
    confirmAdmit: 'إدخال وإرسال إلى الصندوق',
    stayValue: 'مدة الإقامة',
    stayUnit: 'الوحدة',
    days: 'أيام',
    hours: 'ساعات',
    extend1Day: 'تمديد +يوم',
    finishTreatment: 'إنهاء العلاج',
    awaitingDischarge: 'بانتظار دفعة الخروج',
    expiringSoon: 'الإقامة على وشك الانتهاء',
    bedStatus: {
      AVAILABLE: 'متاح',
      PENDING_PAYMENT: 'بانتظار الدفع',
      OCCUPIED: 'مشغول',
    },
    toast: {
      admitted: 'تم إدخال المريض؛ أُرسلت دفعة الإدخال إلى الصندوق',
      finished: 'انتهى العلاج؛ أُرسلت دفعة الخروج إلى الصندوق',
      extended: 'تم تمديد مدة الإقامة',
      bedCreated: 'تم إنشاء السرير',
      error: 'فشل الإجراء',
    },
  },
```

- [ ] **Step 4: Build the frontend**

Run: `cd frontend && npm run build`
Expected: build succeeds.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/shared/i18n/locales/en.ts frontend/src/shared/i18n/locales/ar.ts
git commit -m "feat(premature-ui): en/ar i18n for premature workspace + bed admin"
```

---

# PHASE 10 — E2E (Playwright) + final verification

### Task 10.1: API-level E2E workflow spec

**Files:**
- Modify: `frontend/e2e/helpers/auth.ts` (extend `Role`)
- Create: `frontend/e2e/brd-rec-005-premature.spec.ts`

- [ ] **Step 1: Extend the `Role` type in `e2e/helpers/auth.ts`**

Add `'premature'` and `'receptionist'` to the `Role` union (the seeded users exist in `DevDataSeeder`):

```typescript
export type Role =
  | 'admin'
  | 'cashier'
  | 'doctor'
  | 'dr.layla'
  | 'eco'
  | 'emergency'
  | 'lab'
  | 'nurse'
  | 'pharmacist'
  | 'radiology'
  | 'premature'
  | 'receptionist';
```

- [ ] **Step 2: Write `frontend/e2e/brd-rec-005-premature.spec.ts`**

```typescript
import { test, expect } from '@playwright/test';
import { authedContext } from './helpers/api';
import { registerPatient, API_BASE } from './helpers/seeds';

/**
 * HMS-BRD-REC-005 — Premature Unit Visit (admission spine, sub-project A).
 */

async function startPrematureVisit(admin: import('@playwright/test').APIRequestContext) {
  const patient = await registerPatient(admin, { gender: 'MALE' });
  const vr = await admin.post(`${API_BASE}/visits`, {
    data: { patientId: patient.id, visitType: 'PREMATURE' },
  });
  expect(vr.status()).toBe(201);
  return { patient, visit: await vr.json() };
}

async function firstAvailableBedId(api: import('@playwright/test').APIRequestContext): Promise<string> {
  const beds = await (await api.get(`${API_BASE}/premature/beds`)).json();
  const free = beds.find((b: any) => b.status === 'AVAILABLE' && b.active);
  if (!free) throw new Error('no available premature bed');
  return free.id;
}

async function pendingPayment(api: import('@playwright/test').APIRequestContext, visitId: string, stage: string) {
  const body = await (await api.get(`${API_BASE}/payments?status=PENDING&size=100`)).json();
  const p = body.content.find((x: any) => x.visitId === visitId && x.stage === stage);
  if (!p) throw new Error(`no pending ${stage} payment for visit ${visitId}`);
  return p;
}

test.describe('REC-005 Premature admission spine', () => {
  test('R1/R4: receptionist starts a PREMATURE visit; it appears in the incoming queue', async () => {
    const admin = await authedContext('admin');
    const premature = await authedContext('premature');
    const { visit } = await startPrematureVisit(admin);
    expect(visit.visitType).toBe('PREMATURE');
    expect(visit.status).toBe('CREATED');

    const incoming = await (await premature.get(`${API_BASE}/visits?type=PREMATURE&status=CREATED&size=50`)).json();
    expect(incoming.content.some((v: any) => v.id === visit.id)).toBeTruthy();
    await admin.dispose(); await premature.dispose();
  });

  test('P1–P4a: assign bed → initial payment approved → bed OCCUPIED, visit IN_PROGRESS', async () => {
    const admin = await authedContext('admin');
    const premature = await authedContext('premature');
    const cashier = await authedContext('cashier');

    const { visit } = await startPrematureVisit(admin);
    const bedId = await firstAvailableBedId(premature);

    const ar = await premature.post(`${API_BASE}/premature/admissions`, {
      data: { visitId: visit.id, bedId, stayValue: 3, stayUnit: 'DAYS' },
    });
    expect(ar.status()).toBe(201);
    const admission = await ar.json();
    expect(admission.status).toBe('AWAITING_ADMISSION_PAYMENT');

    // Bed is reserved.
    const bedsAfterAssign = await (await premature.get(`${API_BASE}/premature/beds`)).json();
    expect(bedsAfterAssign.find((b: any) => b.id === bedId).status).toBe('PENDING_PAYMENT');

    // Approve initial payment.
    const pay = await pendingPayment(cashier, visit.id, 'INITIAL');
    expect((await cashier.post(`${API_BASE}/payments/${pay.id}/approve`, { data: { paymentMethod: 'CASH' } })).ok()).toBeTruthy();

    await expect(async () => {
      const adm = await (await premature.get(`${API_BASE}/premature/admissions?status=UNDER_CARE`)).json();
      expect(adm.some((a: any) => a.id === admission.id)).toBeTruthy();
      const beds = await (await premature.get(`${API_BASE}/premature/beds`)).json();
      expect(beds.find((b: any) => b.id === bedId).status).toBe('OCCUPIED');
      const v = await (await admin.get(`${API_BASE}/visits/${visit.id}`)).json();
      expect(v.status).toBe('IN_PROGRESS');
    }).toPass({ timeout: 10_000 });

    await admin.dispose(); await premature.dispose(); await cashier.dispose();
  });

  test('P4b: rejected initial payment releases the bed and cancels the visit', async () => {
    const admin = await authedContext('admin');
    const premature = await authedContext('premature');
    const cashier = await authedContext('cashier');

    const { visit } = await startPrematureVisit(admin);
    const bedId = await firstAvailableBedId(premature);
    await premature.post(`${API_BASE}/premature/admissions`, {
      data: { visitId: visit.id, bedId, stayValue: 2, stayUnit: 'DAYS' },
    });
    const pay = await pendingPayment(cashier, visit.id, 'INITIAL');
    await cashier.post(`${API_BASE}/payments/${pay.id}/reject`, { data: { reason: 'cannot pay' } });

    await expect(async () => {
      const beds = await (await premature.get(`${API_BASE}/premature/beds`)).json();
      expect(beds.find((b: any) => b.id === bedId).status).toBe('AVAILABLE');
      const v = await (await admin.get(`${API_BASE}/visits/${visit.id}`)).json();
      expect(v.status).toBe('CANCELLED');
    }).toPass({ timeout: 10_000 });

    await admin.dispose(); await premature.dispose(); await cashier.dispose();
  });

  test('P8: doctor/nurse extends the period of stay', async () => {
    const admin = await authedContext('admin');
    const premature = await authedContext('premature');
    const cashier = await authedContext('cashier');
    const nurse = await authedContext('nurse');

    const { visit } = await startPrematureVisit(admin);
    const bedId = await firstAvailableBedId(premature);
    const admission = await (await premature.post(`${API_BASE}/premature/admissions`, {
      data: { visitId: visit.id, bedId, stayValue: 2, stayUnit: 'DAYS' },
    })).json();
    const pay = await pendingPayment(cashier, visit.id, 'INITIAL');
    await cashier.post(`${API_BASE}/payments/${pay.id}/approve`, { data: { paymentMethod: 'CASH' } });
    await expect(async () => {
      const adm = await (await premature.get(`${API_BASE}/premature/admissions?status=UNDER_CARE`)).json();
      expect(adm.some((a: any) => a.id === admission.id)).toBeTruthy();
    }).toPass({ timeout: 10_000 });

    const ext = await nurse.post(`${API_BASE}/premature/admissions/${admission.id}/extend-stay`, {
      data: { value: 1, unit: 'DAYS' },
    });
    expect(ext.ok()).toBeTruthy();
    const after = await ext.json();
    expect(new Date(after.stayExpiresAt).getTime()).toBeGreaterThan(new Date(admission.stayExpiresAt).getTime());

    await admin.dispose(); await premature.dispose(); await cashier.dispose(); await nurse.dispose();
  });

  test('P9–P12a: finish treatment → final payment approved → case CLOSED, bed freed, visit COMPLETED', async () => {
    const admin = await authedContext('admin');
    const premature = await authedContext('premature');
    const cashier = await authedContext('cashier');

    const { visit } = await startPrematureVisit(admin);
    const bedId = await firstAvailableBedId(premature);
    const admission = await (await premature.post(`${API_BASE}/premature/admissions`, {
      data: { visitId: visit.id, bedId, stayValue: 1, stayUnit: 'DAYS' },
    })).json();
    const initial = await pendingPayment(cashier, visit.id, 'INITIAL');
    await cashier.post(`${API_BASE}/payments/${initial.id}/approve`, { data: { paymentMethod: 'CASH' } });
    await expect(async () => {
      const adm = await (await premature.get(`${API_BASE}/premature/admissions?status=UNDER_CARE`)).json();
      expect(adm.some((a: any) => a.id === admission.id)).toBeTruthy();
    }).toPass({ timeout: 10_000 });

    const fin = await premature.post(`${API_BASE}/premature/admissions/${admission.id}/finish-treatment`, { data: {} });
    expect(fin.ok()).toBeTruthy();
    expect((await fin.json()).status).toBe('AWAITING_DISCHARGE_PAYMENT');

    const finalPay = await pendingPayment(cashier, visit.id, 'FINAL');
    await cashier.post(`${API_BASE}/payments/${finalPay.id}/approve`, { data: { paymentMethod: 'CASH' } });

    await expect(async () => {
      const closed = await (await premature.get(`${API_BASE}/premature/admissions?status=CLOSED`)).json();
      expect(closed.some((a: any) => a.id === admission.id)).toBeTruthy();
      const beds = await (await premature.get(`${API_BASE}/premature/beds`)).json();
      expect(beds.find((b: any) => b.id === bedId).status).toBe('AVAILABLE');
      const v = await (await admin.get(`${API_BASE}/visits/${visit.id}`)).json();
      expect(v.status).toBe('COMPLETED');
    }).toPass({ timeout: 10_000 });

    await admin.dispose(); await premature.dispose(); await cashier.dispose();
  });

  test('P12b: rejected final payment keeps the case open and re-issuable', async () => {
    const admin = await authedContext('admin');
    const premature = await authedContext('premature');
    const cashier = await authedContext('cashier');

    const { visit } = await startPrematureVisit(admin);
    const bedId = await firstAvailableBedId(premature);
    const admission = await (await premature.post(`${API_BASE}/premature/admissions`, {
      data: { visitId: visit.id, bedId, stayValue: 1, stayUnit: 'DAYS' },
    })).json();
    const initial = await pendingPayment(cashier, visit.id, 'INITIAL');
    await cashier.post(`${API_BASE}/payments/${initial.id}/approve`, { data: { paymentMethod: 'CASH' } });
    await expect(async () => {
      const adm = await (await premature.get(`${API_BASE}/premature/admissions?status=UNDER_CARE`)).json();
      expect(adm.some((a: any) => a.id === admission.id)).toBeTruthy();
    }).toPass({ timeout: 10_000 });

    await premature.post(`${API_BASE}/premature/admissions/${admission.id}/finish-treatment`, { data: {} });
    const finalPay = await pendingPayment(cashier, visit.id, 'FINAL');
    await cashier.post(`${API_BASE}/payments/${finalPay.id}/reject`, { data: { reason: 'pay tomorrow' } });

    // Case remains open; visit stays AWAITING_FINAL_PAYMENT (not OUTSTANDING_BALANCE).
    await expect(async () => {
      const open = await (await premature.get(`${API_BASE}/premature/admissions?status=AWAITING_DISCHARGE_PAYMENT`)).json();
      expect(open.some((a: any) => a.id === admission.id)).toBeTruthy();
      const v = await (await admin.get(`${API_BASE}/visits/${visit.id}`)).json();
      expect(v.status).toBe('AWAITING_FINAL_PAYMENT');
    }).toPass({ timeout: 10_000 });

    await admin.dispose(); await premature.dispose(); await cashier.dispose();
  });
});
```

- [ ] **Step 2: Run (requires the full stack running — see "Running E2E" below)**

Run: `cd frontend && npx playwright test brd-rec-005-premature`
Expected: 6 passing tests.

- [ ] **Step 3: Commit**

```bash
git add frontend/e2e/helpers/auth.ts frontend/e2e/brd-rec-005-premature.spec.ts
git commit -m "test(premature): API-level E2E for the admission spine (R1–P12b)"
```

### Task 10.2: UI E2E spec

**Files:**
- Create: `frontend/e2e/premature-ui.spec.ts`

- [ ] **Step 1: Write the UI spec** (mirrors `infant-register`/`vip-toggle` UI patterns)

```typescript
import { test, expect } from '@playwright/test';
import { login } from './helpers/auth';
import { authedContext } from './helpers/api';
import { registerPatient, API_BASE } from './helpers/seeds';

/**
 * HMS-BRD-REC-005 — Premature workspace UI: premature staff assigns a bed from the queue.
 */
test('premature staff can admit an incoming patient to a bed via the workspace UI', async ({ page }) => {
  // Seed a PREMATURE visit through the API.
  const admin = await authedContext('admin');
  const patient = await registerPatient(admin, { gender: 'FEMALE' });
  await admin.post(`${API_BASE}/visits`, { data: { patientId: patient.id, visitType: 'PREMATURE' } });
  await admin.dispose();

  // Premature staff opens the workspace.
  await login(page, 'premature');
  await page.goto('/departments/premature');

  await expect(page.getByTestId('prem-incoming')).toContainText(patient.mrn);

  // Click "Assign bed" for this patient's row.
  await page.getByText(patient.mrn).locator('xpath=ancestor::li').getByRole('button').click();
  await expect(page.getByTestId('admit-dialog')).toBeVisible();
  await page.getByTestId('admit-stay-value').fill('3');
  await page.getByTestId('admit-confirm').click();

  // The patient leaves the incoming queue (now admitted).
  await expect(page.getByTestId('prem-incoming')).not.toContainText(patient.mrn, { timeout: 10_000 });
  // At least one bed shows PENDING_PAYMENT.
  await expect(page.getByTestId('prem-beds')).toContainText(/Pending payment/i);
});
```

> If the row-button selector proves brittle, switch to the `data-testid={admit-${v.id}}` button — fetch the visit id from the seeded visit response and target `getByTestId('admit-' + visitId)`.

- [ ] **Step 2: Run**

Run: `cd frontend && npx playwright test premature-ui`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add frontend/e2e/premature-ui.spec.ts
git commit -m "test(premature): UI E2E — assign bed from the workspace"
```

### Task 10.3: Full verification

- [ ] **Step 1: Backend — full module + app build & tests (Docker required for ITs)**

Run: `cd backend && ./mvnw -q -pl premature,app -am test`
Expected: all unit + integration tests PASS; ArchUnit green.

- [ ] **Step 2: Frontend build + typecheck**

Run: `cd frontend && npm run build`
Expected: success.

- [ ] **Step 3: Full E2E (start the stack first)**

```bash
# Terminal 1
docker compose up -d db
# Terminal 2
cd backend && ./mvnw spring-boot:run -pl app
# Terminal 3
cd frontend && npm run dev
# Terminal 4
cd frontend && npm run e2e
```
Expected: all specs, including `brd-rec-005-premature` and `premature-ui`, PASS. Existing specs remain green (the PaymentVisitBridge change only affects PREMATURE visits — confirm `brd-rec-002`/`004` still pass).

- [ ] **Step 4: Final commit (if any cleanup)**

```bash
git add -A
git commit -m "chore(premature): slice A admission spine complete — all tests green"
```

---

## Running E2E (reference)

No `webServer` block in `playwright.config.ts`; the stack runs manually. E2E helpers hit the backend directly at `http://localhost:8080/api`; the browser app uses the Vite proxy. Seeded users (username == password): `admin`, `receptionist`, `cashier`, `premature` (PREMATURE_STAFF + DOCTOR), `nurse`, etc. (see `DevDataSeeder`).

## Definition of Done (Slice A)

- `./mvnw -pl premature,app -am test` green (unit + integration + ArchUnit).
- `npm run build` clean; `npm run e2e` green (new premature specs + existing specs).
- Reception can start a PREMATURE visit; premature staff assign a bed + stay; cashier drives Occupied/Under-Care and Closed/Discharged; P12b retry works; bed dashboard + admin CRUD function; en/ar localized.
