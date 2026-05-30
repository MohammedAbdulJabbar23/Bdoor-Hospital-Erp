# Premature Form + Tour Vitals — Implementation Plan (Sub-project B)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Capture the BRD §6.5 Premature Form (per-admission neonatal data + clinical-pharmacy + culture + Rx + signatures) and a repeating Morning/Night tour-vitals log, on a dedicated premature case page.

**Architecture:** Extend the existing `premature` module with `PrematureForm` (1:1 with an admission) and `PrematureTour` (many) aggregates + vertical slices (get-case, upsert-form, record-tour, signatures). Signatures reuse the platform `FileStorage` exactly as `CaseAttachment` does. Frontend adds a tabbed `PrematureCasePage`. No changes to the admission spine's behavior.

**Tech Stack:** Java 21 / Spring Boot 3.3 / JPA / Flyway / Postgres; React 18 + TS + Vite + react-query + react-hook-form + zod + react-i18next; JUnit 5 + Testcontainers (Failsafe) + Playwright.

**Spec:** `docs/superpowers/specs/2026-05-30-premature-form-design.md`

---

## Environment (every task)
- Backend build: `cd /home/kira/Documents/javaFreelance/backend && JAVA_HOME=/usr/lib/jvm/java-25-openjdk mvn <args>` (no `./mvnw`; set JAVA_HOME inline every call; shell env does not persist).
- Use `-am` on `-pl premature` builds. App-module test selection needs `-Dsurefire.failIfNoSpecifiedTests=false`. ITs are `*IT` and run via **Failsafe in `verify`** (not `test`). Docker + Testcontainers work.
- Frontend: `cd /home/kira/Documents/javaFreelance/frontend`; `npx tsc -b`, `npm run build`, `npx playwright test`. Don't touch `frontend/src/features/clinical/*`.
- Conventions: aggregates extend `com.albudoor.hms.platform.domain.AggregateRoot` (UUID `@Id` from `UUID.randomUUID()`, audit + `@Version` inherited); errors via `DomainException`/`NotFoundException`; handlers `@Service` + `@Transactional`(`readOnly=true` for reads); controllers `@RestController`+`@RequestMapping`+`@PreAuthorize`+`@Valid`; DTO records with `from(...)`. Current user: `SecurityContextHolder.getContext().getAuthentication().getPrincipal()` is `com.albudoor.hms.identity.infrastructure.security.HmsUserPrincipal` → `.userId()`.

## File structure (new under `backend/premature/src/main/java/com/albudoor/hms/premature/`)
- `domain/`: `TourType`, `RespSupport`, `SignatureSlot`, `Signature` (@Embeddable), `PrematureForm` (aggregate), `PrematureFormData` (record), `PrematureTour` (entity), `TourVitals` (record)
- `infrastructure/`: `PrematureFormRepository`, `PrematureTourRepository`
- `api/`: `PrematureFormResponse`, `PrematureTourResponse`, `PrematureCaseResponse`
- slices: `getcase/`, `upsertform/`, `recordtour/`, `signature/`
- `src/main/resources/db/migration/V020__premature_form.sql`
- tests: `src/test/java/.../domain/{PrematureFormTest,PrematureTourTest}.java`
- IT (app module): `backend/app/src/test/java/com/albudoor/hms/app/premature/PrematureCaseIT.java`

Frontend: `frontend/src/features/premature/`: `PrematureCasePage.tsx`, `SignaturePad.tsx`, additions to `api.ts`; edits to `PrematureWorkspacePage.tsx`, `BedDetailPanel.tsx`, `App.tsx`, i18n `en.ts`/`ar.ts`. E2E `frontend/e2e/premature-form.spec.ts`.

---

# PHASE 1 — Schema migration V020

### Task 1.1: `V020__premature_form.sql`

**Files:** Create `backend/premature/src/main/resources/db/migration/V020__premature_form.sql`

- [ ] **Step 1: Write the migration**

```sql
-- HMS-BRD-REC-005 §6.5 Premature Form (per-admission neonatal data) + P7b tour vitals log.

CREATE TABLE prem_form (
    id                      UUID PRIMARY KEY,
    admission_id            UUID         NOT NULL,
    visit_id                UUID         NOT NULL,
    patient_id              UUID         NOT NULL,
    age_text                VARCHAR(60),
    birth_weight_kg         NUMERIC(5,3),
    birth_weight_date       DATE,
    current_weight_kg       NUMERIC(5,3),
    current_weight_date     DATE,
    gestational_age_weeks   INTEGER,
    gestational_age_days    INTEGER,
    corrected_ga_weeks      INTEGER,
    corrected_ga_days       INTEGER,
    length_cm               NUMERIC(5,2),
    length_date             DATE,
    ofc_cm                  NUMERIC(5,2),
    ofc_date                DATE,
    feeding_type            VARCHAR(120),
    kcal_per_oz             NUMERIC(7,2),
    enteral_per_kg          NUMERIC(7,2),
    kcal_per_kg             NUMERIC(7,2),
    gir                     NUMERIC(7,2),
    pharmacy_others         VARCHAR(2000),
    last_culture_date       DATE,
    sample_type             VARCHAR(120),
    culture_result          VARCHAR(500),
    prescription_notes      VARCHAR(2000),
    specialist_doctor_notes VARCHAR(2000),
    -- signature slots (image bytes live in FileStorage; key + metadata here)
    pharmacy_sign_key       VARCHAR(500),
    pharmacy_sign_name      VARCHAR(200),
    pharmacy_signed_by      UUID,
    pharmacy_signed_at      TIMESTAMPTZ,
    resident_sign_key       VARCHAR(500),
    resident_sign_name      VARCHAR(200),
    resident_signed_by      UUID,
    resident_signed_at      TIMESTAMPTZ,
    senior_sign_key         VARCHAR(500),
    senior_sign_name        VARCHAR(200),
    senior_signed_by        UUID,
    senior_signed_at        TIMESTAMPTZ,
    version                 BIGINT       NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ  NOT NULL,
    created_by              VARCHAR(100),
    updated_at              TIMESTAMPTZ,
    updated_by              VARCHAR(100),
    CONSTRAINT uk_prem_form_admission UNIQUE (admission_id),
    CONSTRAINT fk_prem_form_admission FOREIGN KEY (admission_id) REFERENCES prem_admission(id)
);

CREATE TABLE prem_tour (
    id              UUID PRIMARY KEY,
    admission_id    UUID         NOT NULL,
    tour_type       VARCHAR(10)  NOT NULL,
    recorded_at     TIMESTAMPTZ  NOT NULL,
    recorded_by     UUID,
    resp_rate       INTEGER,
    spo2            INTEGER,
    pulse_rate      INTEGER,
    bowel_motion    VARCHAR(120),
    uop             VARCHAR(120),
    feeding         VARCHAR(200),
    vomiting        VARCHAR(200),
    jaundice        VARCHAR(200),
    iv_access       VARCHAR(200),
    iv_fluid        VARCHAR(200),
    baby_temp_c     NUMERIC(4,1),
    incubator_temp_c NUMERIC(4,1),
    humidity        INTEGER,
    nasal_septum    VARCHAR(200),
    rbs             INTEGER,
    others          VARCHAR(2000),
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL,
    created_by      VARCHAR(100),
    updated_at      TIMESTAMPTZ,
    updated_by      VARCHAR(100),
    CONSTRAINT fk_prem_tour_admission FOREIGN KEY (admission_id) REFERENCES prem_admission(id),
    CONSTRAINT chk_prem_tour_type CHECK (tour_type IN ('MORNING', 'NIGHT'))
);
CREATE INDEX idx_prem_tour_admission ON prem_tour (admission_id, recorded_at DESC);

-- Respiratory support is multi-select (M.V./CPAP/HFNC/NC/Room Air) per tour.
CREATE TABLE prem_tour_resp_support (
    tour_id      UUID         NOT NULL REFERENCES prem_tour(id) ON DELETE CASCADE,
    resp_support VARCHAR(20)  NOT NULL,
    CONSTRAINT chk_resp_support CHECK (resp_support IN ('MV','CPAP','HFNC','NC','ROOM_AIR')),
    PRIMARY KEY (tour_id, resp_support)
);
```

- [ ] **Step 2: Verify Flyway applies it** (un-disabled context-load test runs all migrations on a fresh container)

Run: `cd /home/kira/Documents/javaFreelance/backend && JAVA_HOME=/usr/lib/jvm/java-25-openjdk mvn -q -pl app -am test -Dtest=HmsApplicationTests -Dsurefire.failIfNoSpecifiedTests=false`
Expected: PASS, logs "now at version v020".

- [ ] **Step 3: Commit**
```bash
cd /home/kira/Documents/javaFreelance
git add backend/premature/src/main/resources/db/migration/V020__premature_form.sql
git commit -m "feat(premature): V020 schema — premature form, tours, resp-support"
```

---

# PHASE 2 — Tour domain (enums + entity + repo + unit tests)

### Task 2.1: `TourType`, `RespSupport`, `TourVitals`, `PrematureTour`, repository + unit test

**Files:** Create `domain/TourType.java`, `domain/RespSupport.java`, `domain/TourVitals.java`, `domain/PrematureTour.java`, `infrastructure/PrematureTourRepository.java`; Test `src/test/java/com/albudoor/hms/premature/domain/PrematureTourTest.java`

- [ ] **Step 1: Write the failing test** `PrematureTourTest.java`

```java
package com.albudoor.hms.premature.domain;

import com.albudoor.hms.platform.exception.DomainException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PrematureTourTest {

    private TourVitals vitals() {
        return new TourVitals(40, 96, 140, Set.of(RespSupport.CPAP, RespSupport.NC),
                "Normal", "2 ml/kg", "EBM", "No", "No", "Right hand", "D10 4ml/h",
                new BigDecimal("36.8"), new BigDecimal("34.0"), 60, "Intact", 85, "stable");
    }

    @Test
    void records_a_morning_tour_with_vitals() {
        UUID adm = UUID.randomUUID();
        PrematureTour t = PrematureTour.record(adm, TourType.MORNING, UUID.randomUUID(), vitals());
        assertThat(t.getAdmissionId()).isEqualTo(adm);
        assertThat(t.getTourType()).isEqualTo(TourType.MORNING);
        assertThat(t.getRecordedAt()).isNotNull();
        assertThat(t.getRespRate()).isEqualTo(40);
        assertThat(t.getRespSupport()).containsExactlyInAnyOrder(RespSupport.CPAP, RespSupport.NC);
        assertThat(t.getBabyTempC()).isEqualByComparingTo("36.8");
    }

    @Test
    void requires_admission_and_type() {
        assertThatThrownBy(() -> PrematureTour.record(null, TourType.NIGHT, UUID.randomUUID(), vitals()))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> PrematureTour.record(UUID.randomUUID(), null, UUID.randomUUID(), vitals()))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void requires_mandatory_vitals_resp_rate_spo2_pulse_uop_temp() {
        TourVitals missing = new TourVitals(null, 96, 140, Set.of(RespSupport.NC),
                null, "x", null, null, null, null, null, new BigDecimal("36.8"), null, null, null, null, null);
        assertThatThrownBy(() -> PrematureTour.record(UUID.randomUUID(), TourType.MORNING, UUID.randomUUID(), missing))
                .isInstanceOf(DomainException.class);
    }
}
```

- [ ] **Step 2: Run — expect FAIL** `JAVA_HOME=/usr/lib/jvm/java-25-openjdk mvn -q -pl premature -am test -Dtest=PrematureTourTest -Dsurefire.failIfNoSpecifiedTests=false`

- [ ] **Step 3: Implement enums** `domain/TourType.java`:
```java
package com.albudoor.hms.premature.domain;

public enum TourType { MORNING, NIGHT }
```
`domain/RespSupport.java`:
```java
package com.albudoor.hms.premature.domain;

/** Respiratory support modes (BRD §6.5 tour grid, multi-select). */
public enum RespSupport { MV, CPAP, HFNC, NC, ROOM_AIR }
```

- [ ] **Step 4: Implement `TourVitals`** `domain/TourVitals.java`:
```java
package com.albudoor.hms.premature.domain;

import java.math.BigDecimal;
import java.util.Set;

/** The BRD tour-grid vitals captured per tour. Mandatory: respRate, spo2, pulseRate, uop, babyTempC. */
public record TourVitals(
        Integer respRate, Integer spo2, Integer pulseRate,
        Set<RespSupport> respSupport,
        String bowelMotion, String uop, String feeding, String vomiting, String jaundice,
        String ivAccess, String ivFluid,
        BigDecimal babyTempC, BigDecimal incubatorTempC, Integer humidity,
        String nasalSeptum, Integer rbs, String others
) {}
```

- [ ] **Step 5: Implement `PrematureTour`** `domain/PrematureTour.java`:
```java
package com.albudoor.hms.premature.domain;

import com.albudoor.hms.platform.domain.AggregateRoot;
import com.albudoor.hms.platform.exception.DomainException;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "prem_tour")
public class PrematureTour extends AggregateRoot {

    @Id
    private UUID id;

    @Column(name = "admission_id", nullable = false)
    private UUID admissionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tour_type", nullable = false, length = 10)
    private TourType tourType;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @Column(name = "recorded_by")
    private UUID recordedBy;

    @Column(name = "resp_rate") private Integer respRate;
    @Column private Integer spo2;
    @Column(name = "pulse_rate") private Integer pulseRate;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "prem_tour_resp_support", joinColumns = @JoinColumn(name = "tour_id"))
    @Column(name = "resp_support", length = 20)
    @Enumerated(EnumType.STRING)
    private Set<RespSupport> respSupport = new HashSet<>();

    @Column(name = "bowel_motion") private String bowelMotion;
    @Column private String uop;
    @Column private String feeding;
    @Column private String vomiting;
    @Column private String jaundice;
    @Column(name = "iv_access") private String ivAccess;
    @Column(name = "iv_fluid") private String ivFluid;
    @Column(name = "baby_temp_c", precision = 4, scale = 1) private BigDecimal babyTempC;
    @Column(name = "incubator_temp_c", precision = 4, scale = 1) private BigDecimal incubatorTempC;
    @Column private Integer humidity;
    @Column(name = "nasal_septum") private String nasalSeptum;
    @Column private Integer rbs;
    @Column(length = 2000) private String others;

    public static PrematureTour record(UUID admissionId, TourType tourType, UUID recordedBy, TourVitals v) {
        if (admissionId == null) throw new DomainException("ADMISSION_REQUIRED", "admission is required");
        if (tourType == null) throw new DomainException("TOUR_TYPE_REQUIRED", "tour type is required");
        if (v == null) throw new DomainException("VITALS_REQUIRED", "tour vitals are required");
        if (v.respRate() == null || v.spo2() == null || v.pulseRate() == null
                || v.uop() == null || v.uop().isBlank() || v.babyTempC() == null) {
            throw new DomainException("TOUR_VITALS_INCOMPLETE",
                    "respRate, SpO2, pulse, UOP and baby temp are mandatory per tour");
        }
        PrematureTour t = new PrematureTour();
        t.id = UUID.randomUUID();
        t.admissionId = admissionId;
        t.tourType = tourType;
        t.recordedBy = recordedBy;
        t.recordedAt = Instant.now();
        t.respRate = v.respRate();
        t.spo2 = v.spo2();
        t.pulseRate = v.pulseRate();
        t.respSupport = (v.respSupport() == null) ? new HashSet<>() : new HashSet<>(v.respSupport());
        t.bowelMotion = v.bowelMotion();
        t.uop = v.uop();
        t.feeding = v.feeding();
        t.vomiting = v.vomiting();
        t.jaundice = v.jaundice();
        t.ivAccess = v.ivAccess();
        t.ivFluid = v.ivFluid();
        t.babyTempC = v.babyTempC();
        t.incubatorTempC = v.incubatorTempC();
        t.humidity = v.humidity();
        t.nasalSeptum = v.nasalSeptum();
        t.rbs = v.rbs();
        t.others = v.others();
        return t;
    }
}
```

- [ ] **Step 6: Implement `PrematureTourRepository`** `infrastructure/PrematureTourRepository.java`:
```java
package com.albudoor.hms.premature.infrastructure;

import com.albudoor.hms.premature.domain.PrematureTour;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PrematureTourRepository extends JpaRepository<PrematureTour, UUID> {
    List<PrematureTour> findAllByAdmissionIdOrderByRecordedAtDesc(UUID admissionId);
}
```

- [ ] **Step 7: Run — expect PASS (3 tests)**; **Step 8: Commit**
```bash
cd /home/kira/Documents/javaFreelance
git add backend/premature/src/main/java/com/albudoor/hms/premature/domain/TourType.java backend/premature/src/main/java/com/albudoor/hms/premature/domain/RespSupport.java backend/premature/src/main/java/com/albudoor/hms/premature/domain/TourVitals.java backend/premature/src/main/java/com/albudoor/hms/premature/domain/PrematureTour.java backend/premature/src/main/java/com/albudoor/hms/premature/infrastructure/PrematureTourRepository.java backend/premature/src/test/java/com/albudoor/hms/premature/domain/PrematureTourTest.java
git commit -m "feat(premature): PrematureTour entity + vitals with unit tests"
```

---

# PHASE 3 — Form domain (Signature, PrematureForm, data record, repo + unit tests)

### Task 3.1: `SignatureSlot`, `Signature`, `PrematureFormData`, `PrematureForm`, repo + unit test

**Files:** Create `domain/SignatureSlot.java`, `domain/Signature.java`, `domain/PrematureFormData.java`, `domain/PrematureForm.java`, `infrastructure/PrematureFormRepository.java`; Test `src/test/java/com/albudoor/hms/premature/domain/PrematureFormTest.java`

- [ ] **Step 1: Write the failing test** `PrematureFormTest.java`

```java
package com.albudoor.hms.premature.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PrematureFormTest {

    private PrematureFormData data() {
        return new PrematureFormData(
                "12 days", new BigDecimal("1.200"), null, new BigDecimal("1.450"), null,
                32, 4, 34, 1, new BigDecimal("42.0"), null, new BigDecimal("30.0"), null,
                "EBM", new BigDecimal("20"), new BigDecimal("150"), new BigDecimal("110"),
                new BigDecimal("6.0"), "notes", null, "Blood", "No growth", "Rx text", "spec notes");
    }

    @Test
    void create_then_update_holds_fields() {
        PrematureForm f = PrematureForm.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        f.update(data());
        assertThat(f.getCurrentWeightKg()).isEqualByComparingTo("1.450");
        assertThat(f.getCorrectedGaWeeks()).isEqualTo(34);
        assertThat(f.getFeedingType()).isEqualTo("EBM");
        assertThat(f.getGir()).isEqualByComparingTo("6.0");
    }

    @Test
    void apply_signature_sets_slot_with_metadata() {
        PrematureForm f = PrematureForm.create(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        UUID user = UUID.randomUUID();
        f.applySignature(SignatureSlot.RESIDENT, "2026-05-30/abc.png", "Dr. Noor", user);
        assertThat(f.getResidentSignature()).isNotNull();
        assertThat(f.getResidentSignature().getImageKey()).isEqualTo("2026-05-30/abc.png");
        assertThat(f.getResidentSignature().getSignerName()).isEqualTo("Dr. Noor");
        assertThat(f.getResidentSignature().getSignedBy()).isEqualTo(user);
        assertThat(f.getResidentSignature().getSignedAt()).isNotNull();
        // other slots remain null
        assertThat(f.getClinicalPharmacySignature().getImageKey()).isNull();
    }
}
```

- [ ] **Step 2: Run — expect FAIL**.

- [ ] **Step 3: Implement `SignatureSlot`** `domain/SignatureSlot.java`:
```java
package com.albudoor.hms.premature.domain;

public enum SignatureSlot { CLINICAL_PHARMACY, RESIDENT, SENIOR_RESIDENT }
```

- [ ] **Step 4: Implement `Signature` @Embeddable** `domain/Signature.java`:
```java
package com.albudoor.hms.premature.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/** A captured signature: image stored in FileStorage (imageKey) + who/when metadata. */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Signature {
    @Column(name = "sign_key", length = 500)
    private String imageKey;
    @Column(name = "sign_name", length = 200)
    private String signerName;
    @Column(name = "signed_by")
    private UUID signedBy;
    @Column(name = "signed_at")
    private Instant signedAt;

    static Signature empty() { return new Signature(null, null, null, null); }
}
```

- [ ] **Step 5: Implement `PrematureFormData`** `domain/PrematureFormData.java`:
```java
package com.albudoor.hms.premature.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Editable Premature Form fields (BRD §6.5), carried from the upsert command into the aggregate. */
public record PrematureFormData(
        String ageText,
        BigDecimal birthWeightKg, LocalDate birthWeightDate,
        BigDecimal currentWeightKg, LocalDate currentWeightDate,
        Integer gestationalAgeWeeks, Integer gestationalAgeDays,
        Integer correctedGaWeeks, Integer correctedGaDays,
        BigDecimal lengthCm, LocalDate lengthDate,
        BigDecimal ofcCm, LocalDate ofcDate,
        String feedingType,
        BigDecimal kcalPerOz, BigDecimal enteralPerKg, BigDecimal kcalPerKg, BigDecimal gir,
        String pharmacyOthers,
        LocalDate lastCultureDate, String sampleType, String cultureResult,
        String prescriptionNotes, String specialistDoctorNotes
) {}
```

- [ ] **Step 6: Implement `PrematureForm`** `domain/PrematureForm.java`:
```java
package com.albudoor.hms.premature.domain;

import com.albudoor.hms.platform.domain.AggregateRoot;
import com.albudoor.hms.platform.exception.DomainException;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "prem_form")
public class PrematureForm extends AggregateRoot {

    @Id
    private UUID id;
    @Column(name = "admission_id", nullable = false)
    private UUID admissionId;
    @Column(name = "visit_id", nullable = false)
    private UUID visitId;
    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "age_text", length = 60) private String ageText;
    @Column(name = "birth_weight_kg", precision = 5, scale = 3) private BigDecimal birthWeightKg;
    @Column(name = "birth_weight_date") private LocalDate birthWeightDate;
    @Column(name = "current_weight_kg", precision = 5, scale = 3) private BigDecimal currentWeightKg;
    @Column(name = "current_weight_date") private LocalDate currentWeightDate;
    @Column(name = "gestational_age_weeks") private Integer gestationalAgeWeeks;
    @Column(name = "gestational_age_days") private Integer gestationalAgeDays;
    @Column(name = "corrected_ga_weeks") private Integer correctedGaWeeks;
    @Column(name = "corrected_ga_days") private Integer correctedGaDays;
    @Column(name = "length_cm", precision = 5, scale = 2) private BigDecimal lengthCm;
    @Column(name = "length_date") private LocalDate lengthDate;
    @Column(name = "ofc_cm", precision = 5, scale = 2) private BigDecimal ofcCm;
    @Column(name = "ofc_date") private LocalDate ofcDate;
    @Column(name = "feeding_type", length = 120) private String feedingType;
    @Column(name = "kcal_per_oz", precision = 7, scale = 2) private BigDecimal kcalPerOz;
    @Column(name = "enteral_per_kg", precision = 7, scale = 2) private BigDecimal enteralPerKg;
    @Column(name = "kcal_per_kg", precision = 7, scale = 2) private BigDecimal kcalPerKg;
    @Column(precision = 7, scale = 2) private BigDecimal gir;
    @Column(name = "pharmacy_others", length = 2000) private String pharmacyOthers;
    @Column(name = "last_culture_date") private LocalDate lastCultureDate;
    @Column(name = "sample_type", length = 120) private String sampleType;
    @Column(name = "culture_result", length = 500) private String cultureResult;
    @Column(name = "prescription_notes", length = 2000) private String prescriptionNotes;
    @Column(name = "specialist_doctor_notes", length = 2000) private String specialistDoctorNotes;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "imageKey", column = @Column(name = "pharmacy_sign_key", length = 500)),
            @AttributeOverride(name = "signerName", column = @Column(name = "pharmacy_sign_name", length = 200)),
            @AttributeOverride(name = "signedBy", column = @Column(name = "pharmacy_signed_by")),
            @AttributeOverride(name = "signedAt", column = @Column(name = "pharmacy_signed_at")),
    })
    private Signature clinicalPharmacySignature = Signature.empty();

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "imageKey", column = @Column(name = "resident_sign_key", length = 500)),
            @AttributeOverride(name = "signerName", column = @Column(name = "resident_sign_name", length = 200)),
            @AttributeOverride(name = "signedBy", column = @Column(name = "resident_signed_by")),
            @AttributeOverride(name = "signedAt", column = @Column(name = "resident_signed_at")),
    })
    private Signature residentSignature = Signature.empty();

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "imageKey", column = @Column(name = "senior_sign_key", length = 500)),
            @AttributeOverride(name = "signerName", column = @Column(name = "senior_sign_name", length = 200)),
            @AttributeOverride(name = "signedBy", column = @Column(name = "senior_signed_by")),
            @AttributeOverride(name = "signedAt", column = @Column(name = "senior_signed_at")),
    })
    private Signature seniorResidentSignature = Signature.empty();

    public static PrematureForm create(UUID admissionId, UUID visitId, UUID patientId) {
        if (admissionId == null || visitId == null || patientId == null) {
            throw new DomainException("FORM_REFS_REQUIRED", "admission, visit and patient are required");
        }
        PrematureForm f = new PrematureForm();
        f.id = UUID.randomUUID();
        f.admissionId = admissionId;
        f.visitId = visitId;
        f.patientId = patientId;
        return f;
    }

    public void update(PrematureFormData d) {
        this.ageText = d.ageText();
        this.birthWeightKg = d.birthWeightKg();
        this.birthWeightDate = d.birthWeightDate();
        this.currentWeightKg = d.currentWeightKg();
        this.currentWeightDate = d.currentWeightDate();
        this.gestationalAgeWeeks = d.gestationalAgeWeeks();
        this.gestationalAgeDays = d.gestationalAgeDays();
        this.correctedGaWeeks = d.correctedGaWeeks();
        this.correctedGaDays = d.correctedGaDays();
        this.lengthCm = d.lengthCm();
        this.lengthDate = d.lengthDate();
        this.ofcCm = d.ofcCm();
        this.ofcDate = d.ofcDate();
        this.feedingType = d.feedingType();
        this.kcalPerOz = d.kcalPerOz();
        this.enteralPerKg = d.enteralPerKg();
        this.kcalPerKg = d.kcalPerKg();
        this.gir = d.gir();
        this.pharmacyOthers = d.pharmacyOthers();
        this.lastCultureDate = d.lastCultureDate();
        this.sampleType = d.sampleType();
        this.cultureResult = d.cultureResult();
        this.prescriptionNotes = d.prescriptionNotes();
        this.specialistDoctorNotes = d.specialistDoctorNotes();
    }

    public void applySignature(SignatureSlot slot, String imageKey, String signerName, UUID userId) {
        Signature sig = new Signature(imageKey, signerName, userId, Instant.now());
        switch (slot) {
            case CLINICAL_PHARMACY -> this.clinicalPharmacySignature = sig;
            case RESIDENT -> this.residentSignature = sig;
            case SENIOR_RESIDENT -> this.seniorResidentSignature = sig;
        }
    }

    public Signature signature(SignatureSlot slot) {
        return switch (slot) {
            case CLINICAL_PHARMACY -> clinicalPharmacySignature;
            case RESIDENT -> residentSignature;
            case SENIOR_RESIDENT -> seniorResidentSignature;
        };
    }
}
```

- [ ] **Step 7: Implement `PrematureFormRepository`** `infrastructure/PrematureFormRepository.java`:
```java
package com.albudoor.hms.premature.infrastructure;

import com.albudoor.hms.premature.domain.PrematureForm;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PrematureFormRepository extends JpaRepository<PrematureForm, UUID> {
    Optional<PrematureForm> findByAdmissionId(UUID admissionId);
}
```

- [ ] **Step 8: Run — expect PASS (2 tests)**; **Step 9: Commit**
```bash
cd /home/kira/Documents/javaFreelance
git add backend/premature/src/main/java/com/albudoor/hms/premature/domain/SignatureSlot.java backend/premature/src/main/java/com/albudoor/hms/premature/domain/Signature.java backend/premature/src/main/java/com/albudoor/hms/premature/domain/PrematureFormData.java backend/premature/src/main/java/com/albudoor/hms/premature/domain/PrematureForm.java backend/premature/src/main/java/com/albudoor/hms/premature/infrastructure/PrematureFormRepository.java backend/premature/src/test/java/com/albudoor/hms/premature/domain/PrematureFormTest.java
git commit -m "feat(premature): PrematureForm aggregate + signatures with unit tests"
```

---

# PHASE 4 — Response DTOs + get-case (with pre-fill)

### Task 4.1: DTOs + `getcase` slice

**Files:** Create `api/PrematureFormResponse.java`, `api/PrematureTourResponse.java`, `api/PrematureCaseResponse.java`, `getcase/GetCaseHandler.java`, `getcase/GetCaseController.java`

- [ ] **Step 1: `api/PrematureTourResponse.java`**
```java
package com.albudoor.hms.premature.api;

import com.albudoor.hms.premature.domain.PrematureTour;
import com.albudoor.hms.premature.domain.RespSupport;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PrematureTourResponse(
        UUID id, String tourType, Instant recordedAt, UUID recordedBy,
        Integer respRate, Integer spo2, Integer pulseRate, List<String> respSupport,
        String bowelMotion, String uop, String feeding, String vomiting, String jaundice,
        String ivAccess, String ivFluid, BigDecimal babyTempC, BigDecimal incubatorTempC,
        Integer humidity, String nasalSeptum, Integer rbs, String others
) {
    public static PrematureTourResponse from(PrematureTour t) {
        return new PrematureTourResponse(
                t.getId(), t.getTourType().name(), t.getRecordedAt(), t.getRecordedBy(),
                t.getRespRate(), t.getSpo2(), t.getPulseRate(),
                t.getRespSupport().stream().map(RespSupport::name).sorted().toList(),
                t.getBowelMotion(), t.getUop(), t.getFeeding(), t.getVomiting(), t.getJaundice(),
                t.getIvAccess(), t.getIvFluid(), t.getBabyTempC(), t.getIncubatorTempC(),
                t.getHumidity(), t.getNasalSeptum(), t.getRbs(), t.getOthers());
    }
}
```

- [ ] **Step 2: `api/PrematureFormResponse.java`**
```java
package com.albudoor.hms.premature.api;

import com.albudoor.hms.premature.domain.PrematureForm;
import com.albudoor.hms.premature.domain.Signature;
import com.albudoor.hms.premature.domain.SignatureSlot;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PrematureFormResponse(
        UUID id, UUID admissionId,
        String ageText,
        BigDecimal birthWeightKg, LocalDate birthWeightDate,
        BigDecimal currentWeightKg, LocalDate currentWeightDate,
        Integer gestationalAgeWeeks, Integer gestationalAgeDays,
        Integer correctedGaWeeks, Integer correctedGaDays,
        BigDecimal lengthCm, LocalDate lengthDate, BigDecimal ofcCm, LocalDate ofcDate,
        String feedingType, BigDecimal kcalPerOz, BigDecimal enteralPerKg, BigDecimal kcalPerKg,
        BigDecimal gir, String pharmacyOthers,
        LocalDate lastCultureDate, String sampleType, String cultureResult,
        String prescriptionNotes, String specialistDoctorNotes,
        Sig clinicalPharmacySignature, Sig residentSignature, Sig seniorResidentSignature
) {
    public record Sig(boolean present, String signerName, UUID signedBy, Instant signedAt) {
        static Sig from(Signature s) {
            boolean present = s != null && s.getImageKey() != null;
            return new Sig(present, s == null ? null : s.getSignerName(),
                    s == null ? null : s.getSignedBy(), s == null ? null : s.getSignedAt());
        }
    }

    public static PrematureFormResponse from(PrematureForm f) {
        return new PrematureFormResponse(
                f.getId(), f.getAdmissionId(), f.getAgeText(),
                f.getBirthWeightKg(), f.getBirthWeightDate(), f.getCurrentWeightKg(), f.getCurrentWeightDate(),
                f.getGestationalAgeWeeks(), f.getGestationalAgeDays(), f.getCorrectedGaWeeks(), f.getCorrectedGaDays(),
                f.getLengthCm(), f.getLengthDate(), f.getOfcCm(), f.getOfcDate(),
                f.getFeedingType(), f.getKcalPerOz(), f.getEnteralPerKg(), f.getKcalPerKg(), f.getGir(),
                f.getPharmacyOthers(), f.getLastCultureDate(), f.getSampleType(), f.getCultureResult(),
                f.getPrescriptionNotes(), f.getSpecialistDoctorNotes(),
                Sig.from(f.signature(SignatureSlot.CLINICAL_PHARMACY)),
                Sig.from(f.signature(SignatureSlot.RESIDENT)),
                Sig.from(f.signature(SignatureSlot.SENIOR_RESIDENT)));
    }
}
```

- [ ] **Step 3: `api/PrematureCaseResponse.java`**
```java
package com.albudoor.hms.premature.api;

import java.math.BigDecimal;
import java.util.List;

public record PrematureCaseResponse(
        AdmissionResponse admission,
        PrematureFormResponse form,   // null if not yet created
        Prefill prefill,
        List<PrematureTourResponse> tours
) {
    /** Suggested defaults for a fresh form, from the infant's registration record. */
    public record Prefill(
            String ageText,
            BigDecimal birthWeightKg,
            Integer gestationalAgeWeeks, Integer gestationalAgeDays,
            BigDecimal lengthCm, BigDecimal ofcCm
    ) {}
}
```

- [ ] **Step 4: `getcase/GetCaseHandler.java`** (computes pre-fill from the patient's infant record + admission)
```java
package com.albudoor.hms.premature.getcase;

import com.albudoor.hms.patientregistry.domain.InfantDetails;
import com.albudoor.hms.patientregistry.domain.Patient;
import com.albudoor.hms.patientregistry.infrastructure.PatientRepository;
import com.albudoor.hms.platform.exception.NotFoundException;
import com.albudoor.hms.premature.api.AdmissionResponse;
import com.albudoor.hms.premature.api.PrematureCaseResponse;
import com.albudoor.hms.premature.api.PrematureFormResponse;
import com.albudoor.hms.premature.api.PrematureTourResponse;
import com.albudoor.hms.premature.domain.PrematureAdmission;
import com.albudoor.hms.premature.infrastructure.PrematureAdmissionRepository;
import com.albudoor.hms.premature.infrastructure.PrematureFormRepository;
import com.albudoor.hms.premature.infrastructure.PrematureTourRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class GetCaseHandler {

    private final PrematureAdmissionRepository admissions;
    private final PrematureFormRepository forms;
    private final PrematureTourRepository tours;
    private final PatientRepository patients;

    public GetCaseHandler(PrematureAdmissionRepository admissions, PrematureFormRepository forms,
                          PrematureTourRepository tours, PatientRepository patients) {
        this.admissions = admissions;
        this.forms = forms;
        this.tours = tours;
        this.patients = patients;
    }

    @Transactional(readOnly = true)
    public PrematureCaseResponse handle(UUID admissionId) {
        PrematureAdmission adm = admissions.findById(admissionId)
                .orElseThrow(() -> new NotFoundException("Admission not found: " + admissionId));
        PrematureFormResponse form = forms.findByAdmissionId(admissionId)
                .map(PrematureFormResponse::from).orElse(null);
        var tourList = tours.findAllByAdmissionIdOrderByRecordedAtDesc(admissionId)
                .stream().map(PrematureTourResponse::from).toList();
        return new PrematureCaseResponse(AdmissionResponse.from(adm), form, prefill(adm), tourList);
    }

    private PrematureCaseResponse.Prefill prefill(PrematureAdmission adm) {
        Patient p = patients.findById(adm.getPatientId()).orElse(null);
        InfantDetails d = (p == null) ? null : p.getInfantDetails();
        String age = (p == null) ? null : deriveAge(p.getDateOfBirth(),
                adm.getAdmittedAt().atZone(ZoneOffset.UTC).toLocalDate());
        if (d == null) return new PrematureCaseResponse.Prefill(age, null, null, null, null, null);
        return new PrematureCaseResponse.Prefill(age,
                d.getBirthWeightKg(), d.getGestationalAgeWeeks(), d.getGestationalAgeDays(),
                d.getLengthCm(), d.getOfcCm());
    }

    /** Human-readable age at admission, e.g. "12 days" / "3 weeks". Package-visible for testing. */
    static String deriveAge(LocalDate dob, LocalDate at) {
        if (dob == null || at == null || at.isBefore(dob)) return null;
        long days = ChronoUnit.DAYS.between(dob, at);
        if (days < 14) return days + (days == 1 ? " day" : " days");
        return (days / 7) + " weeks";
    }
}
```

- [ ] **Step 5: `getcase/GetCaseController.java`**
```java
package com.albudoor.hms.premature.getcase;

import com.albudoor.hms.premature.api.PrematureCaseResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/premature/admissions")
@PreAuthorize("isAuthenticated()")
public class GetCaseController {

    private final GetCaseHandler handler;

    public GetCaseController(GetCaseHandler handler) {
        this.handler = handler;
    }

    @GetMapping("/{id}/case")
    public PrematureCaseResponse getCase(@PathVariable UUID id) {
        return handler.handle(id);
    }
}
```

> Note: `getcase` depends on `patient-registry` (PatientRepository, Patient, InfantDetails). The `premature` pom already declares `patient-registry` (added in sub-project A). Verify the import resolves; if not, add the dependency.

- [ ] **Step 6: Compile + unit-test the age helper.** Add to `PrematureFormTest` (or a tiny new test) — actually add a focused test in Phase 4 to keep it covered:

Create `backend/premature/src/test/java/com/albudoor/hms/premature/getcase/DeriveAgeTest.java`:
```java
package com.albudoor.hms.premature.getcase;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.assertThat;

class DeriveAgeTest {
    @Test
    void days_then_weeks() {
        assertThat(GetCaseHandler.deriveAge(LocalDate.of(2026,5,1), LocalDate.of(2026,5,2))).isEqualTo("1 day");
        assertThat(GetCaseHandler.deriveAge(LocalDate.of(2026,5,1), LocalDate.of(2026,5,6))).isEqualTo("5 days");
        assertThat(GetCaseHandler.deriveAge(LocalDate.of(2026,5,1), LocalDate.of(2026,5,22))).isEqualTo("3 weeks");
        assertThat(GetCaseHandler.deriveAge(null, LocalDate.of(2026,5,2))).isNull();
    }
}
```
Run: `JAVA_HOME=/usr/lib/jvm/java-25-openjdk mvn -q -pl premature -am test -Dtest=DeriveAgeTest -Dsurefire.failIfNoSpecifiedTests=false` → PASS.

- [ ] **Step 7: Commit**
```bash
cd /home/kira/Documents/javaFreelance
git add backend/premature/src/main/java/com/albudoor/hms/premature/api/PrematureTourResponse.java backend/premature/src/main/java/com/albudoor/hms/premature/api/PrematureFormResponse.java backend/premature/src/main/java/com/albudoor/hms/premature/api/PrematureCaseResponse.java backend/premature/src/main/java/com/albudoor/hms/premature/getcase backend/premature/src/test/java/com/albudoor/hms/premature/getcase/DeriveAgeTest.java
git commit -m "feat(premature): case GET with form, tours, and registration pre-fill"
```

---

# PHASE 5 — Upsert form slice

### Task 5.1: `upsertform` slice

**Files:** Create `upsertform/UpsertFormCommand.java`, `upsertform/UpsertFormHandler.java`, `upsertform/UpsertFormController.java`

- [ ] **Step 1: `UpsertFormCommand`** (mandatory fields enforced via Bean Validation)
```java
package com.albudoor.hms.premature.upsertform;

import com.albudoor.hms.premature.domain.PrematureFormData;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UpsertFormCommand(
        @NotBlank String ageText,
        @NotNull @PositiveOrZero BigDecimal birthWeightKg, LocalDate birthWeightDate,
        @NotNull @PositiveOrZero BigDecimal currentWeightKg, LocalDate currentWeightDate,
        @NotNull Integer gestationalAgeWeeks, @NotNull Integer gestationalAgeDays,
        @NotNull Integer correctedGaWeeks, @NotNull Integer correctedGaDays,
        @NotNull @PositiveOrZero BigDecimal lengthCm, LocalDate lengthDate,
        @NotNull @PositiveOrZero BigDecimal ofcCm, LocalDate ofcDate,
        @NotBlank String feedingType,
        BigDecimal kcalPerOz, BigDecimal enteralPerKg, BigDecimal kcalPerKg, BigDecimal gir,
        String pharmacyOthers,
        LocalDate lastCultureDate, String sampleType, String cultureResult,
        String prescriptionNotes, String specialistDoctorNotes
) {
    public PrematureFormData toData() {
        return new PrematureFormData(ageText, birthWeightKg, birthWeightDate, currentWeightKg, currentWeightDate,
                gestationalAgeWeeks, gestationalAgeDays, correctedGaWeeks, correctedGaDays,
                lengthCm, lengthDate, ofcCm, ofcDate, feedingType, kcalPerOz, enteralPerKg, kcalPerKg, gir,
                pharmacyOthers, lastCultureDate, sampleType, cultureResult, prescriptionNotes, specialistDoctorNotes);
    }
}
```

- [ ] **Step 2: `UpsertFormHandler`** (create-or-update; 1:1 with admission)
```java
package com.albudoor.hms.premature.upsertform;

import com.albudoor.hms.platform.exception.NotFoundException;
import com.albudoor.hms.premature.domain.PrematureAdmission;
import com.albudoor.hms.premature.domain.PrematureForm;
import com.albudoor.hms.premature.infrastructure.PrematureAdmissionRepository;
import com.albudoor.hms.premature.infrastructure.PrematureFormRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UpsertFormHandler {

    private final PrematureFormRepository forms;
    private final PrematureAdmissionRepository admissions;

    public UpsertFormHandler(PrematureFormRepository forms, PrematureAdmissionRepository admissions) {
        this.forms = forms;
        this.admissions = admissions;
    }

    @Transactional
    public PrematureForm handle(UUID admissionId, UpsertFormCommand cmd) {
        PrematureAdmission adm = admissions.findById(admissionId)
                .orElseThrow(() -> new NotFoundException("Admission not found: " + admissionId));
        PrematureForm form = forms.findByAdmissionId(admissionId)
                .orElseGet(() -> PrematureForm.create(adm.getId(), adm.getVisitId(), adm.getPatientId()));
        form.update(cmd.toData());
        return forms.save(form);
    }
}
```

- [ ] **Step 3: `UpsertFormController`**
```java
package com.albudoor.hms.premature.upsertform;

import com.albudoor.hms.premature.api.PrematureFormResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/premature/admissions")
public class UpsertFormController {

    private final UpsertFormHandler handler;

    public UpsertFormController(UpsertFormHandler handler) {
        this.handler = handler;
    }

    @PutMapping("/{id}/form")
    @PreAuthorize("hasAnyRole('PREMATURE_STAFF', 'DOCTOR', 'ADMIN')")
    public PrematureFormResponse upsert(@PathVariable UUID id, @Valid @RequestBody UpsertFormCommand cmd) {
        return PrematureFormResponse.from(handler.handle(id, cmd));
    }
}
```

- [ ] **Step 4: Compile + commit**
Run: `JAVA_HOME=/usr/lib/jvm/java-25-openjdk mvn -q -pl premature -am test-compile` → SUCCESS.
```bash
cd /home/kira/Documents/javaFreelance
git add backend/premature/src/main/java/com/albudoor/hms/premature/upsertform
git commit -m "feat(premature): upsert Premature Form slice"
```

---

# PHASE 6 — Record tour slice

### Task 6.1: `recordtour` slice

**Files:** Modify `backend/premature/pom.xml`; Create `recordtour/RecordTourCommand.java`, `recordtour/RecordTourHandler.java`, `recordtour/RecordTourController.java`

- [ ] **Step 0: Add the `identity` dependency to `backend/premature/pom.xml`** (RecordTourHandler + SignatureController import `com.albudoor.hms.identity.infrastructure.security.HmsUserPrincipal`). It currently arrives only transitively via patient-registry; make it direct. Add, alongside the other `com.albudoor.hms` deps:
```xml
        <dependency>
            <groupId>com.albudoor.hms</groupId>
            <artifactId>identity</artifactId>
        </dependency>
```
Confirm `HmsUserPrincipal.userId()` exists (it's used by `CaseAttachmentController`).

- [ ] **Step 1: `RecordTourCommand`**
```java
package com.albudoor.hms.premature.recordtour;

import com.albudoor.hms.premature.domain.RespSupport;
import com.albudoor.hms.premature.domain.TourType;
import com.albudoor.hms.premature.domain.TourVitals;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record RecordTourCommand(
        @NotNull TourType tourType,
        @NotNull Integer respRate, @NotNull Integer spo2, @NotNull Integer pulseRate,
        List<RespSupport> respSupport,
        String bowelMotion, @NotNull String uop, String feeding, String vomiting, String jaundice,
        String ivAccess, String ivFluid,
        @NotNull BigDecimal babyTempC, BigDecimal incubatorTempC, Integer humidity,
        String nasalSeptum, Integer rbs, String others
) {
    public TourVitals toVitals() {
        Set<RespSupport> rs = (respSupport == null) ? new HashSet<>() : new HashSet<>(respSupport);
        return new TourVitals(respRate, spo2, pulseRate, rs, bowelMotion, uop, feeding, vomiting, jaundice,
                ivAccess, ivFluid, babyTempC, incubatorTempC, humidity, nasalSeptum, rbs, others);
    }
}
```

- [ ] **Step 2: `RecordTourHandler`** (captures current user as recordedBy)
```java
package com.albudoor.hms.premature.recordtour;

import com.albudoor.hms.identity.infrastructure.security.HmsUserPrincipal;
import com.albudoor.hms.platform.exception.NotFoundException;
import com.albudoor.hms.premature.domain.PrematureTour;
import com.albudoor.hms.premature.infrastructure.PrematureAdmissionRepository;
import com.albudoor.hms.premature.infrastructure.PrematureTourRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class RecordTourHandler {

    private final PrematureTourRepository tours;
    private final PrematureAdmissionRepository admissions;

    public RecordTourHandler(PrematureTourRepository tours, PrematureAdmissionRepository admissions) {
        this.tours = tours;
        this.admissions = admissions;
    }

    @Transactional
    public PrematureTour handle(UUID admissionId, RecordTourCommand cmd) {
        if (!admissions.existsById(admissionId)) {
            throw new NotFoundException("Admission not found: " + admissionId);
        }
        PrematureTour tour = PrematureTour.record(admissionId, cmd.tourType(), currentUserId(), cmd.toVitals());
        return tours.save(tour);
    }

    private static UUID currentUserId() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a != null && a.getPrincipal() instanceof HmsUserPrincipal p) return p.userId();
        return null;
    }
}
```
> `HmsUserPrincipal` comes from the `identity` dependency added in Step 0.

- [ ] **Step 3: `RecordTourController`**
```java
package com.albudoor.hms.premature.recordtour;

import com.albudoor.hms.premature.api.PrematureTourResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/premature/admissions")
public class RecordTourController {

    private final RecordTourHandler handler;

    public RecordTourController(RecordTourHandler handler) {
        this.handler = handler;
    }

    @PostMapping("/{id}/tours")
    @PreAuthorize("hasAnyRole('PREMATURE_STAFF', 'DOCTOR', 'NURSE', 'ADMIN')")
    public ResponseEntity<PrematureTourResponse> record(@PathVariable UUID id, @Valid @RequestBody RecordTourCommand cmd) {
        return ResponseEntity.status(HttpStatus.CREATED).body(PrematureTourResponse.from(handler.handle(id, cmd)));
    }
}
```

- [ ] **Step 4: Compile + commit**
```bash
cd /home/kira/Documents/javaFreelance
git add backend/premature/src/main/java/com/albudoor/hms/premature/recordtour
git commit -m "feat(premature): record-tour slice (Morning/Night vitals log)"
```

---

# PHASE 7 — Signature upload/download slice

### Task 7.1: `signature` slice (FileStorage, mirrors CaseAttachment)

**Files:** Create `signature/SignatureController.java`

- [ ] **Step 1: `SignatureController`** (upload PNG → FileStorage → form slot; stream back)
```java
package com.albudoor.hms.premature.signature;

import com.albudoor.hms.identity.infrastructure.security.HmsUserPrincipal;
import com.albudoor.hms.platform.exception.DomainException;
import com.albudoor.hms.platform.exception.NotFoundException;
import com.albudoor.hms.platform.storage.FileStorage;
import com.albudoor.hms.premature.domain.PrematureAdmission;
import com.albudoor.hms.premature.domain.PrematureForm;
import com.albudoor.hms.premature.domain.Signature;
import com.albudoor.hms.premature.domain.SignatureSlot;
import com.albudoor.hms.premature.infrastructure.PrematureAdmissionRepository;
import com.albudoor.hms.premature.infrastructure.PrematureFormRepository;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/premature/admissions")
public class SignatureController {

    private final PrematureFormRepository forms;
    private final PrematureAdmissionRepository admissions;
    private final FileStorage storage;

    public SignatureController(PrematureFormRepository forms, PrematureAdmissionRepository admissions, FileStorage storage) {
        this.forms = forms;
        this.admissions = admissions;
        this.storage = storage;
    }

    /** Ack returned after a signature upload. */
    public record Ack(String slot, String signerName, java.time.Instant signedAt) {}

    @PostMapping("/{id}/form/signatures/{slot}")
    @PreAuthorize("hasAnyRole('PREMATURE_STAFF', 'DOCTOR', 'ADMIN')")
    @Transactional
    public Ack upload(
            @PathVariable UUID id, @PathVariable SignatureSlot slot,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "signerName", required = false) String signerName
    ) throws IOException {
        if (file.isEmpty()) throw new DomainException("SIGNATURE_EMPTY", "Signature image is empty");
        PrematureForm form = forms.findByAdmissionId(id).orElseGet(() -> {
            PrematureAdmission adm = admissions.findById(id)
                    .orElseThrow(() -> new NotFoundException("Admission not found: " + id));
            return PrematureForm.create(adm.getId(), adm.getVisitId(), adm.getPatientId());
        });
        String key;
        try (var in = file.getInputStream()) {
            key = storage.save(in, "signature.png", file.getSize());
        }
        form.applySignature(slot, key, signerName, currentUserId());
        forms.save(form);
        Signature s = form.signature(slot);
        return new Ack(slot.name(), s.getSignerName(), s.getSignedAt());
    }

    @GetMapping("/{id}/form/signatures/{slot}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Resource> download(@PathVariable UUID id, @PathVariable SignatureSlot slot) throws IOException {
        PrematureForm form = forms.findByAdmissionId(id)
                .orElseThrow(() -> new NotFoundException("Form not found for admission: " + id));
        Signature s = form.signature(slot);
        if (s == null || s.getImageKey() == null) throw new NotFoundException("No signature in slot " + slot);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(new InputStreamResource(storage.open(s.getImageKey())));
    }

    private static UUID currentUserId() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a != null && a.getPrincipal() instanceof HmsUserPrincipal p) return p.userId();
        return null;
    }
}
```

- [ ] **Step 2: Compile + commit**
Run: `JAVA_HOME=/usr/lib/jvm/java-25-openjdk mvn -q -pl premature -am test-compile` → SUCCESS.
```bash
cd /home/kira/Documents/javaFreelance
git add backend/premature/src/main/java/com/albudoor/hms/premature/signature
git commit -m "feat(premature): signature upload/download via FileStorage"
```

---

# PHASE 8 — ArchUnit + integration test

### Task 8.1: Extend ArchUnit + write the case integration test

**Files:** Modify `backend/app/src/test/java/com/albudoor/hms/app/ArchitectureTest.java`; Create `backend/app/src/test/java/com/albudoor/hms/app/premature/PrematureCaseIT.java`

- [ ] **Step 1: Add the new slice packages to `layeredWithinPremature`** application layer `.definedBy(...)` list:
```java
                        "..premature.getcase..",
                        "..premature.upsertform..",
                        "..premature.recordtour..",
                        "..premature.signature..",
```
(append these to the existing list of premature application packages).

- [ ] **Step 2: Write `PrematureCaseIT`** (Testcontainers; admit→under-care helper like AdmitFlowIT)
```java
package com.albudoor.hms.app.premature;

import com.albudoor.hms.app.IntegrationTest;
import com.albudoor.hms.cashier.domain.PaymentStatus;
import com.albudoor.hms.cashier.infrastructure.PaymentRepository;
import com.albudoor.hms.premature.domain.Bed;
import com.albudoor.hms.premature.infrastructure.BedRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PrematureCaseIT extends IntegrationTest {

    @Autowired TestRestTemplate rest;
    @Autowired BedRepository beds;
    @Autowired PaymentRepository payments;

    private HttpHeaders auth(String user) {
        var login = rest.postForEntity("/api/auth/login", Map.of("username", user, "password", user), Map.class);
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setBearerAuth((String) login.getBody().get("token"));
        return h;
    }
    private <T> T post(String path, Object body, String user, Class<T> type) {
        var r = rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body == null ? Map.of() : body, auth(user)), type);
        assertThat(r.getStatusCode().is2xxSuccessful()).as("POST %s -> %s : %s", path, r.getStatusCode(), r.getBody()).isTrue();
        return r.getBody();
    }

    /** admit + approve initial → returns admissionId. */
    @SuppressWarnings("unchecked")
    private String admitUnderCare() {
        var patient = post("/api/patients", Map.of("fullName", "Baby F " + System.nanoTime(), "gender", "MALE",
                "dateOfBirth", "2026-05-01", "mobileNumber", "0773" + (System.nanoTime() % 10_000_000L), "vip", false),
                "receptionist", Map.class);
        var visit = post("/api/visits", Map.of("patientId", patient.get("id"), "visitType", "PREMATURE"), "receptionist", Map.class);
        String visitId = (String) visit.get("id");
        Bed bed = beds.save(Bed.create("PREM-FORM-" + System.nanoTime(), "IT"));
        var adm = post("/api/premature/admissions",
                Map.of("visitId", visitId, "bedId", bed.getId().toString(), "stayValue", 3, "stayUnit", "DAYS"),
                "premature", Map.class);
        var initial = payments.findAllByVisitIdOrderByCreatedAtDesc(UUID.fromString(visitId)).stream()
                .filter(p -> p.getStatus() == PaymentStatus.PENDING).findFirst().orElseThrow();
        post("/api/payments/" + initial.getId() + "/approve", Map.of("paymentMethod", "CASH"), "cashier", Map.class);
        return (String) adm.get("id");
    }

    @Test @SuppressWarnings("unchecked")
    void case_get_prefills_then_upsert_form_and_record_tour_persist() {
        String adm = admitUnderCare();

        // GET case → form null, prefill present (birth weight pre-filled? our seed patient is an ADULT
        // registration so infantDetails is null; prefill.ageText derives from DOB regardless).
        var caseBody = rest.exchange("/api/premature/admissions/" + adm + "/case", HttpMethod.GET,
                new HttpEntity<>(auth("premature")), Map.class).getBody();
        assertThat(caseBody.get("form")).isNull();
        assertThat(((Map<String,Object>) caseBody.get("prefill"))).isNotNull();

        // Upsert the form.
        var form = rest.exchange("/api/premature/admissions/" + adm + "/form", HttpMethod.PUT,
                new HttpEntity<>(Map.ofEntries(
                        Map.entry("ageText", "12 days"),
                        Map.entry("birthWeightKg", 1.2), Map.entry("currentWeightKg", 1.45),
                        Map.entry("gestationalAgeWeeks", 32), Map.entry("gestationalAgeDays", 4),
                        Map.entry("correctedGaWeeks", 34), Map.entry("correctedGaDays", 1),
                        Map.entry("lengthCm", 42.0), Map.entry("ofcCm", 30.0),
                        Map.entry("feedingType", "EBM"), Map.entry("gir", 6.0)), auth("premature")), Map.class);
        assertThat(form.getStatusCode().value()).isEqualTo(200);
        assertThat(((Map<String,Object>) form.getBody()).get("feedingType")).isEqualTo("EBM");

        // Record a Morning tour.
        post("/api/premature/admissions/" + adm + "/tours", Map.ofEntries(
                Map.entry("tourType", "MORNING"), Map.entry("respRate", 40), Map.entry("spo2", 96),
                Map.entry("pulseRate", 140), Map.entry("respSupport", List.of("CPAP", "NC")),
                Map.entry("uop", "2 ml/kg"), Map.entry("babyTempC", 36.8)), "nurse", Map.class);

        // GET case again → form present, one tour.
        var after = rest.exchange("/api/premature/admissions/" + adm + "/case", HttpMethod.GET,
                new HttpEntity<>(auth("premature")), Map.class).getBody();
        assertThat(after.get("form")).isNotNull();
        assertThat((List<?>) after.get("tours")).hasSize(1);
    }

    @Test
    void upsert_form_rejects_missing_mandatory_field() {
        String adm = admitUnderCare();
        var res = rest.exchange("/api/premature/admissions/" + adm + "/form", HttpMethod.PUT,
                new HttpEntity<>(Map.of("ageText", "12 days"), auth("premature")), String.class); // missing required
        assertThat(res.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void recording_a_tour_is_forbidden_for_cashier() {
        String adm = admitUnderCare();
        var res = rest.exchange("/api/premature/admissions/" + adm + "/tours", HttpMethod.POST,
                new HttpEntity<>(Map.of("tourType","MORNING","respRate",40,"spo2",96,"pulseRate",140,"uop","x","babyTempC",36.8),
                        auth("cashier")), String.class);
        assertThat(res.getStatusCode().value()).isEqualTo(403);
    }
}
```

- [ ] **Step 3: Run the IT + ArchUnit (Failsafe + Surefire)**
Run: `cd /home/kira/Documents/javaFreelance/backend && JAVA_HOME=/usr/lib/jvm/java-25-openjdk mvn -q -pl premature,app -am verify -Dsurefire.failIfNoSpecifiedTests=false 2>&1 | tail -40`
Expected: BUILD SUCCESS — premature unit tests (PrematureTourTest, PrematureFormTest, DeriveAgeTest) + all app ITs incl. `PrematureCaseIT` + ArchitectureTest all green.

- [ ] **Step 4: Commit**
```bash
cd /home/kira/Documents/javaFreelance
git add backend/app/src/test/java/com/albudoor/hms/app/ArchitectureTest.java backend/app/src/test/java/com/albudoor/hms/app/premature/PrematureCaseIT.java
git commit -m "test(premature): case integration test + ArchUnit for form/tour/signature slices"
```

---

# PHASE 9 — Frontend (case page, form, tours, signature pad)

### Task 9.1: API client additions

**Files:** Modify `frontend/src/features/premature/api.ts`

- [ ] **Step 1: Append types + functions** to `api.ts`:
```typescript
export type RespSupport = 'MV' | 'CPAP' | 'HFNC' | 'NC' | 'ROOM_AIR';
export type TourType = 'MORNING' | 'NIGHT';
export type SignatureSlot = 'CLINICAL_PHARMACY' | 'RESIDENT' | 'SENIOR_RESIDENT';

export type PrematureForm = {
  id: string; admissionId: string; ageText: string | null;
  birthWeightKg: number | null; birthWeightDate: string | null;
  currentWeightKg: number | null; currentWeightDate: string | null;
  gestationalAgeWeeks: number | null; gestationalAgeDays: number | null;
  correctedGaWeeks: number | null; correctedGaDays: number | null;
  lengthCm: number | null; lengthDate: string | null; ofcCm: number | null; ofcDate: string | null;
  feedingType: string | null; kcalPerOz: number | null; enteralPerKg: number | null;
  kcalPerKg: number | null; gir: number | null; pharmacyOthers: string | null;
  lastCultureDate: string | null; sampleType: string | null; cultureResult: string | null;
  prescriptionNotes: string | null; specialistDoctorNotes: string | null;
  clinicalPharmacySignature: SigMeta; residentSignature: SigMeta; seniorResidentSignature: SigMeta;
};
export type SigMeta = { present: boolean; signerName: string | null; signedBy: string | null; signedAt: string | null };

export type Tour = {
  id: string; tourType: TourType; recordedAt: string; recordedBy: string | null;
  respRate: number | null; spo2: number | null; pulseRate: number | null; respSupport: RespSupport[];
  bowelMotion: string | null; uop: string | null; feeding: string | null; vomiting: string | null;
  jaundice: string | null; ivAccess: string | null; ivFluid: string | null;
  babyTempC: number | null; incubatorTempC: number | null; humidity: number | null;
  nasalSeptum: string | null; rbs: number | null; others: string | null;
};

export type Prefill = {
  ageText: string | null; birthWeightKg: number | null;
  gestationalAgeWeeks: number | null; gestationalAgeDays: number | null;
  lengthCm: number | null; ofcCm: number | null;
};

export type PrematureCase = { admission: Admission; form: PrematureForm | null; prefill: Prefill; tours: Tour[] };

export async function getPrematureCase(admissionId: string): Promise<PrematureCase> {
  const res = await api.get(`/premature/admissions/${admissionId}/case`);
  return res.data;
}
export async function upsertPrematureForm(admissionId: string, body: Record<string, unknown>): Promise<PrematureForm> {
  const res = await api.put(`/premature/admissions/${admissionId}/form`, body);
  return res.data;
}
export async function recordTour(admissionId: string, body: Record<string, unknown>): Promise<Tour> {
  const res = await api.post(`/premature/admissions/${admissionId}/tours`, body);
  return res.data;
}
export async function uploadSignature(admissionId: string, slot: SignatureSlot, file: Blob, signerName: string): Promise<void> {
  const fd = new FormData();
  fd.append('file', file, 'signature.png');
  fd.append('signerName', signerName);
  await api.post(`/premature/admissions/${admissionId}/form/signatures/${slot}`, fd,
    { headers: { 'Content-Type': 'multipart/form-data' } });
}
/** Fetch a stored signature as a blob object-URL (the <img> can't carry the Bearer token). */
export async function fetchSignatureUrl(admissionId: string, slot: SignatureSlot): Promise<string> {
  const res = await api.get(`/premature/admissions/${admissionId}/form/signatures/${slot}`, { responseType: 'blob' });
  return URL.createObjectURL(res.data as Blob);
}
```
(`Admission` type already exists in this file from sub-project A.)

- [ ] **Step 2: tsc + commit**
Run: `cd /home/kira/Documents/javaFreelance/frontend && npx tsc -b` → clean.
```bash
cd /home/kira/Documents/javaFreelance
git add frontend/src/features/premature/api.ts
git commit -m "feat(premature-ui): case/form/tour/signature api client"
```

### Task 9.2: `SignaturePad` component

**Files:** Create `frontend/src/features/premature/SignaturePad.tsx`

- [ ] **Step 1: Implement** (canvas draw → PNG blob; shows saved signature via blob URL)
```tsx
import { useEffect, useRef, useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import toast from 'react-hot-toast';
import { extractApiError } from '@/shared/api/client';
import { uploadSignature, fetchSignatureUrl, type SignatureSlot, type SigMeta } from './api';

export function SignaturePad({
  admissionId, slot, label, meta, onSaved, t,
}: {
  admissionId: string; slot: SignatureSlot; label: string; meta: SigMeta;
  onSaved: () => void; t: (k: string) => string;
}) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const drawing = useRef(false);
  const [signerName, setSignerName] = useState(meta.signerName ?? '');
  const [savedUrl, setSavedUrl] = useState<string | null>(null);
  const [editing, setEditing] = useState(!meta.present);

  useEffect(() => {
    if (meta.present) fetchSignatureUrl(admissionId, slot).then(setSavedUrl).catch(() => {});
  }, [admissionId, slot, meta.present]);

  const pos = (e: React.PointerEvent) => {
    const r = canvasRef.current!.getBoundingClientRect();
    return { x: e.clientX - r.left, y: e.clientY - r.top };
  };
  const start = (e: React.PointerEvent) => {
    drawing.current = true;
    const ctx = canvasRef.current!.getContext('2d')!;
    ctx.beginPath();
    const { x, y } = pos(e);
    ctx.moveTo(x, y);
  };
  const move = (e: React.PointerEvent) => {
    if (!drawing.current) return;
    const ctx = canvasRef.current!.getContext('2d')!;
    ctx.lineWidth = 2; ctx.lineCap = 'round'; ctx.strokeStyle = '#0f172a';
    const { x, y } = pos(e);
    ctx.lineTo(x, y); ctx.stroke();
  };
  const end = () => { drawing.current = false; };
  const clear = () => {
    const c = canvasRef.current!;
    c.getContext('2d')!.clearRect(0, 0, c.width, c.height);
  };

  const saveMut = useMutation({
    mutationFn: () => new Promise<void>((resolve, reject) => {
      canvasRef.current!.toBlob(async (blob) => {
        if (!blob) return reject(new Error('empty'));
        try { await uploadSignature(admissionId, slot, blob, signerName.trim()); resolve(); }
        catch (e) { reject(e); }
      }, 'image/png');
    }),
    onSuccess: async () => {
      toast.success(t('premature.form.signatureSaved'));
      const url = await fetchSignatureUrl(admissionId, slot).catch(() => null);
      setSavedUrl(url); setEditing(false); onSaved();
    },
    onError: (e) => toast.error(extractApiError(e)?.message ?? t('premature.toast.error')),
  });

  return (
    <div className="rounded-lg border border-ink-200 p-3" data-testid={`signature-${slot}`}>
      <div className="mb-2 flex items-center justify-between">
        <span className="text-xs font-medium text-ink-700">{label}</span>
        {meta.present && !editing && (
          <button type="button" className="text-[11px] text-brand-700 hover:underline" onClick={() => setEditing(true)}>
            {t('premature.form.reSign')}
          </button>
        )}
      </div>
      {!editing && savedUrl ? (
        <div>
          <img src={savedUrl} alt={label} className="h-20 w-full rounded border border-ink-100 object-contain" />
          {meta.signerName && <p className="mt-1 text-[11px] text-ink-500">{meta.signerName}</p>}
        </div>
      ) : (
        <div className="space-y-2">
          <input
            value={signerName} onChange={(e) => setSignerName(e.target.value)}
            placeholder={t('premature.form.signerName')}
            className="w-full rounded-md border border-ink-200 px-2 py-1 text-xs"
            data-testid={`signer-${slot}`}
          />
          <canvas
            ref={canvasRef} width={300} height={90}
            className="w-full touch-none rounded border border-dashed border-ink-300 bg-white"
            onPointerDown={start} onPointerMove={move} onPointerUp={end} onPointerLeave={end}
            data-testid={`canvas-${slot}`}
          />
          <div className="flex gap-2">
            <button type="button" onClick={clear} className="rounded border border-ink-200 px-2 py-1 text-[11px] hover:bg-ink-50">
              {t('premature.form.clear')}
            </button>
            <button type="button" disabled={saveMut.isPending} onClick={() => saveMut.mutate()}
              className="rounded bg-brand-600 px-2 py-1 text-[11px] font-medium text-white hover:bg-brand-700 disabled:opacity-50"
              data-testid={`save-sign-${slot}`}>
              {t('premature.form.saveSignature')}
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
```

- [ ] **Step 2: Commit**
```bash
cd /home/kira/Documents/javaFreelance
git add frontend/src/features/premature/SignaturePad.tsx
git commit -m "feat(premature-ui): signature pad (canvas draw + upload)"
```

### Task 9.3: `PrematureCasePage` (Overview / Form / Tours tabs)

**Files:** Create `frontend/src/features/premature/PrematureCasePage.tsx`

- [ ] **Step 1: Implement the page** (react-query loads `/case`; tabs; form via react-hook-form; tours list + add; signatures)

```tsx
import { useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import toast from 'react-hot-toast';
import { ArrowLeft, Baby, ChevronRight } from 'lucide-react';
import { extractApiError } from '@/shared/api/client';
import { cn } from '@/shared/ui/cn';
import { SignaturePad } from './SignaturePad';
import {
  getPrematureCase, upsertPrematureForm, recordTour,
  type PrematureCase, type RespSupport, type TourType,
} from './api';

const RESP = ['MV', 'CPAP', 'HFNC', 'NC', 'ROOM_AIR'] as const;
const num = (v: FormDataEntryValue | null) => (v === null || v === '' ? null : Number(v));
const str = (v: FormDataEntryValue | null) => (v === null || v === '' ? null : String(v));

export function PrematureCasePage() {
  const { id } = useParams<{ id: string }>();
  const { t } = useTranslation();
  const navigate = useNavigate();
  const qc = useQueryClient();
  const [tab, setTab] = useState<'overview' | 'form' | 'tours'>('form');

  const { data: c, isLoading } = useQuery({
    queryKey: ['prem-case', id], queryFn: () => getPrematureCase(id!), enabled: !!id,
  });

  if (isLoading || !c) return <div className="p-6 text-sm text-ink-500">{t('common.loading')}</div>;

  return (
    <div className="space-y-4 p-1">
      <button type="button" onClick={() => navigate('/departments/premature')}
        className="inline-flex items-center gap-1 text-xs text-ink-500 hover:text-ink-900">
        <ArrowLeft size={14} className="rtl:rotate-180" /> {t('premature.detail.title')}
      </button>

      <header className="flex items-center justify-between">
        <button type="button" onClick={() => navigate(`/patients/${c.admission.patientId}`)}
          className="group flex items-center gap-3 text-start" data-testid="case-patient">
          <span className="flex h-10 w-10 items-center justify-center rounded-full bg-brand-50 text-brand-700"><Baby size={18} /></span>
          <span>
            <span className="block font-semibold text-ink-900">{c.admission.patientName}</span>
            <span className="block font-mono text-[11px] text-ink-500">
              {c.admission.patientMrn} · {c.admission.bedCode} · {t(`premature.admissionStatus.${c.admission.status}`)}
            </span>
          </span>
          <ChevronRight size={16} className="text-ink-300 group-hover:text-brand-600 rtl:rotate-180" />
        </button>
      </header>

      <div className="flex gap-1 border-b border-ink-100">
        {(['form', 'tours', 'overview'] as const).map((tb) => (
          <button key={tb} type="button" onClick={() => setTab(tb)}
            className={cn('border-b-2 px-4 py-2.5 text-sm font-medium transition-colors',
              tab === tb ? 'border-brand-600 text-brand-700' : 'border-transparent text-ink-600 hover:text-ink-900')}
            data-testid={`case-tab-${tb}`}>
            {t(`premature.case.tab.${tb}`)}
          </button>
        ))}
      </div>

      {tab === 'form' && <FormTab c={c} onSaved={() => qc.invalidateQueries({ queryKey: ['prem-case', id] })} t={t} admissionId={id!} />}
      {tab === 'tours' && <ToursTab c={c} onSaved={() => qc.invalidateQueries({ queryKey: ['prem-case', id] })} t={t} admissionId={id!} />}
      {tab === 'overview' && <OverviewTab c={c} t={t} />}
    </div>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return <label className="block"><span className="mb-1 block text-[11px] font-medium text-ink-600">{label}</span>{children}</label>;
}
const input = 'w-full rounded-md border border-ink-200 px-2 py-1.5 text-sm';

function FormTab({ c, admissionId, onSaved, t }: { c: PrematureCase; admissionId: string; onSaved: () => void; t: (k: string) => string }) {
  const f = c.form;
  const p = c.prefill;
  const { register, handleSubmit } = useForm({
    defaultValues: {
      ageText: f?.ageText ?? p.ageText ?? '',
      birthWeightKg: f?.birthWeightKg ?? p.birthWeightKg ?? '',
      currentWeightKg: f?.currentWeightKg ?? '',
      gestationalAgeWeeks: f?.gestationalAgeWeeks ?? p.gestationalAgeWeeks ?? '',
      gestationalAgeDays: f?.gestationalAgeDays ?? p.gestationalAgeDays ?? '',
      correctedGaWeeks: f?.correctedGaWeeks ?? '', correctedGaDays: f?.correctedGaDays ?? '',
      lengthCm: f?.lengthCm ?? p.lengthCm ?? '', ofcCm: f?.ofcCm ?? p.ofcCm ?? '',
      feedingType: f?.feedingType ?? '', kcalPerOz: f?.kcalPerOz ?? '', enteralPerKg: f?.enteralPerKg ?? '',
      kcalPerKg: f?.kcalPerKg ?? '', gir: f?.gir ?? '', pharmacyOthers: f?.pharmacyOthers ?? '',
      lastCultureDate: f?.lastCultureDate ?? '', sampleType: f?.sampleType ?? '', cultureResult: f?.cultureResult ?? '',
      prescriptionNotes: f?.prescriptionNotes ?? '', specialistDoctorNotes: f?.specialistDoctorNotes ?? '',
    },
  });
  const mut = useMutation({
    mutationFn: (v: Record<string, unknown>) => upsertPrematureForm(admissionId, clean(v)),
    onSuccess: () => { toast.success(t('premature.form.saved')); onSaved(); },
    onError: (e) => toast.error(extractApiError(e)?.message ?? t('premature.toast.error')),
  });
  // Convert blank strings → null/number for the API.
  const clean = (v: Record<string, unknown>) => {
    const numKeys = ['birthWeightKg','currentWeightKg','gestationalAgeWeeks','gestationalAgeDays','correctedGaWeeks','correctedGaDays','lengthCm','ofcCm','kcalPerOz','enteralPerKg','kcalPerKg','gir'];
    const out: Record<string, unknown> = {};
    for (const [k, val] of Object.entries(v)) {
      if (val === '' || val == null) { out[k] = null; continue; }
      out[k] = numKeys.includes(k) ? Number(val) : val;
    }
    return out;
  };

  return (
    <form onSubmit={handleSubmit((v) => mut.mutate(v))} className="space-y-5" data-testid="prem-form">
      <Section title={t('premature.form.measurements')}>
        <Field label={t('premature.form.age') + ' *'}><input className={input} {...register('ageText')} data-testid="f-ageText" /></Field>
        <Field label={t('premature.form.birthWeight') + ' (kg) *'}><input className={input} type="number" step="0.001" {...register('birthWeightKg')} /></Field>
        <Field label={t('premature.form.currentWeight') + ' (kg) *'}><input className={input} type="number" step="0.001" {...register('currentWeightKg')} data-testid="f-currentWeightKg" /></Field>
        <Field label={t('premature.form.gaWeeks') + ' *'}><input className={input} type="number" {...register('gestationalAgeWeeks')} /></Field>
        <Field label={t('premature.form.gaDays') + ' *'}><input className={input} type="number" {...register('gestationalAgeDays')} /></Field>
        <Field label={t('premature.form.correctedGaWeeks') + ' *'}><input className={input} type="number" {...register('correctedGaWeeks')} /></Field>
        <Field label={t('premature.form.correctedGaDays') + ' *'}><input className={input} type="number" {...register('correctedGaDays')} /></Field>
        <Field label={t('premature.form.length') + ' (cm) *'}><input className={input} type="number" step="0.1" {...register('lengthCm')} /></Field>
        <Field label={t('premature.form.ofc') + ' (cm) *'}><input className={input} type="number" step="0.1" {...register('ofcCm')} /></Field>
      </Section>
      <Section title={t('premature.form.clinicalPharmacy')}>
        <Field label={t('premature.form.feedingType') + ' *'}><input className={input} {...register('feedingType')} data-testid="f-feedingType" /></Field>
        <Field label="kCal/oz"><input className={input} type="number" step="0.01" {...register('kcalPerOz')} /></Field>
        <Field label="Enteral/Kg"><input className={input} type="number" step="0.01" {...register('enteralPerKg')} /></Field>
        <Field label="kCal/kg"><input className={input} type="number" step="0.01" {...register('kcalPerKg')} /></Field>
        <Field label="GIR"><input className={input} type="number" step="0.01" {...register('gir')} /></Field>
        <Field label={t('premature.form.others')}><input className={input} {...register('pharmacyOthers')} /></Field>
      </Section>
      <Section title={t('premature.form.culture')}>
        <Field label={t('premature.form.lastCultureDate')}><input className={input} type="date" {...register('lastCultureDate')} /></Field>
        <Field label={t('premature.form.sampleType')}><input className={input} {...register('sampleType')} /></Field>
        <Field label={t('premature.form.cultureResult')}><input className={input} {...register('cultureResult')} /></Field>
      </Section>
      <Section title={t('premature.form.notes')}>
        <Field label={t('premature.form.rx')}><textarea className={input} rows={2} {...register('prescriptionNotes')} /></Field>
        <Field label={t('premature.form.specialistNotes')}><textarea className={input} rows={2} {...register('specialistDoctorNotes')} /></Field>
      </Section>
      <button type="submit" disabled={mut.isPending}
        className="rounded-md bg-brand-600 px-4 py-2 text-sm font-medium text-white hover:bg-brand-700 disabled:opacity-50"
        data-testid="save-form">
        {mut.isPending ? t('common.loading') : t('premature.form.save')}
      </button>

      {f && (
        <Section title={t('premature.form.signatures')}>
          <SignaturePad admissionId={admissionId} slot="CLINICAL_PHARMACY" label={t('premature.form.pharmacySign')} meta={f.clinicalPharmacySignature} onSaved={onSaved} t={t} />
          <SignaturePad admissionId={admissionId} slot="RESIDENT" label={t('premature.form.residentSign')} meta={f.residentSignature} onSaved={onSaved} t={t} />
          <SignaturePad admissionId={admissionId} slot="SENIOR_RESIDENT" label={t('premature.form.seniorSign')} meta={f.seniorResidentSignature} onSaved={onSaved} t={t} />
        </Section>
      )}
    </form>
  );
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="rounded-xl border border-ink-100 bg-white p-4">
      <h3 className="mb-3 text-sm font-semibold text-ink-900">{title}</h3>
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-3">{children}</div>
    </section>
  );
}

function ToursTab({ c, admissionId, onSaved, t }: { c: PrematureCase; admissionId: string; onSaved: () => void; t: (k: string) => string }) {
  const { register, handleSubmit, reset } = useForm<{ tourType: TourType; respSupport: RespSupport[] } & Record<string, unknown>>({
    defaultValues: { tourType: 'MORNING', respSupport: [] },
  });
  const mut = useMutation({
    mutationFn: (v: Record<string, unknown>) => recordTour(admissionId, cleanTour(v)),
    onSuccess: () => { toast.success(t('premature.form.tourAdded')); reset({ tourType: 'MORNING', respSupport: [] }); onSaved(); },
    onError: (e) => toast.error(extractApiError(e)?.message ?? t('premature.toast.error')),
  });
  const cleanTour = (v: Record<string, unknown>) => {
    const numKeys = ['respRate','spo2','pulseRate','babyTempC','incubatorTempC','humidity','rbs'];
    const out: Record<string, unknown> = { tourType: v.tourType, respSupport: v.respSupport ?? [] };
    for (const k of ['respRate','spo2','pulseRate','bowelMotion','uop','feeding','vomiting','jaundice','ivAccess','ivFluid','babyTempC','incubatorTempC','humidity','nasalSeptum','rbs','others']) {
      const val = (v as Record<string, unknown>)[k];
      out[k] = (val === '' || val == null) ? null : (numKeys.includes(k) ? Number(val) : val);
    }
    return out;
  };

  return (
    <div className="space-y-4">
      <form onSubmit={handleSubmit((v) => mut.mutate(v))} className="rounded-xl border border-ink-100 bg-white p-4" data-testid="tour-form">
        <h3 className="mb-3 text-sm font-semibold text-ink-900">{t('premature.case.addTour')}</h3>
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
          <Field label={t('premature.case.tourType')}>
            <select className={input} {...register('tourType')} data-testid="tour-type">
              <option value="MORNING">{t('premature.case.morning')}</option>
              <option value="NIGHT">{t('premature.case.night')}</option>
            </select>
          </Field>
          <Field label={t('premature.case.respRate') + ' *'}><input className={input} type="number" {...register('respRate')} data-testid="tour-respRate" /></Field>
          <Field label="SpO₂ (%) *"><input className={input} type="number" {...register('spo2')} data-testid="tour-spo2" /></Field>
          <Field label={t('premature.case.pulse') + ' *'}><input className={input} type="number" {...register('pulseRate')} data-testid="tour-pulse" /></Field>
          <Field label={t('premature.case.uop') + ' *'}><input className={input} {...register('uop')} data-testid="tour-uop" /></Field>
          <Field label={t('premature.case.babyTemp') + ' (°C) *'}><input className={input} type="number" step="0.1" {...register('babyTempC')} data-testid="tour-temp" /></Field>
          <Field label={t('premature.case.incuTemp') + ' (°C)'}><input className={input} type="number" step="0.1" {...register('incubatorTempC')} /></Field>
          <Field label={t('premature.case.humidity') + ' (%)'}><input className={input} type="number" {...register('humidity')} /></Field>
          <Field label="RBS"><input className={input} type="number" {...register('rbs')} /></Field>
          <Field label={t('premature.case.feeding')}><input className={input} {...register('feeding')} /></Field>
          <Field label={t('premature.case.bowel')}><input className={input} {...register('bowelMotion')} /></Field>
          <Field label={t('premature.case.vomiting')}><input className={input} {...register('vomiting')} /></Field>
          <Field label={t('premature.case.jaundice')}><input className={input} {...register('jaundice')} /></Field>
          <Field label={t('premature.case.ivAccess')}><input className={input} {...register('ivAccess')} /></Field>
          <Field label={t('premature.case.ivFluid')}><input className={input} {...register('ivFluid')} /></Field>
          <Field label={t('premature.case.nasalSeptum')}><input className={input} {...register('nasalSeptum')} /></Field>
        </div>
        <div className="mt-3">
          <span className="mb-1 block text-[11px] font-medium text-ink-600">{t('premature.case.respSupport')} *</span>
          <div className="flex flex-wrap gap-3">
            {RESP.map((r) => (
              <label key={r} className="inline-flex items-center gap-1 text-xs">
                <input type="checkbox" value={r} {...register('respSupport')} className="accent-brand-600" /> {r}
              </label>
            ))}
          </div>
        </div>
        <Field label={t('premature.form.others')}><textarea className={input + ' mt-3'} rows={2} {...register('others')} /></Field>
        <button type="submit" disabled={mut.isPending}
          className="mt-3 rounded-md bg-brand-600 px-4 py-2 text-sm font-medium text-white hover:bg-brand-700 disabled:opacity-50"
          data-testid="save-tour">
          {mut.isPending ? t('common.loading') : t('premature.case.addTour')}
        </button>
      </form>

      <div className="rounded-xl border border-ink-100 bg-white" data-testid="tour-list">
        <div className="border-b border-ink-100 px-4 py-3 text-sm font-semibold text-ink-900">{t('premature.case.tourLog')}</div>
        {c.tours.length === 0 ? (
          <p className="p-4 text-xs text-ink-500">{t('premature.case.noTours')}</p>
        ) : (
          <ul className="divide-y divide-ink-100 text-xs">
            {c.tours.map((tr) => (
              <li key={tr.id} className="flex flex-wrap items-center gap-x-4 gap-y-1 px-4 py-2">
                <span className="font-medium text-ink-900">{t('premature.case.' + (tr.tourType === 'MORNING' ? 'morning' : 'night'))}</span>
                <span className="text-ink-500">{new Date(tr.recordedAt).toLocaleString()}</span>
                <span>RR {tr.respRate}</span><span>SpO₂ {tr.spo2}%</span><span>HR {tr.pulseRate}</span>
                <span>T {String(tr.babyTempC)}°C</span><span>UOP {tr.uop}</span>
                <span className="text-ink-500">{tr.respSupport.join(', ')}</span>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}

function OverviewTab({ c, t }: { c: PrematureCase; t: (k: string) => string }) {
  return (
    <div className="rounded-xl border border-ink-100 bg-white p-4 text-sm">
      <dl className="grid grid-cols-2 gap-3">
        <div><dt className="text-ink-500">{t('premature.detail.status')}</dt><dd className="font-medium">{t(`premature.admissionStatus.${c.admission.status}`)}</dd></div>
        <div><dt className="text-ink-500">{t('premature.bed')}</dt><dd className="font-medium">{c.admission.bedCode}</dd></div>
        <div><dt className="text-ink-500">{t('premature.stayValue')}</dt><dd className="font-medium">{c.admission.stayValue} {t(`premature.${c.admission.stayUnit === 'DAYS' ? 'days' : 'hours'}`)}</dd></div>
        <div><dt className="text-ink-500">{t('premature.detail.admittedAt')}</dt><dd className="font-medium">{new Date(c.admission.admittedAt).toLocaleString()}</dd></div>
      </dl>
    </div>
  );
}
```

- [ ] **Step 2: tsc + commit**
Run: `cd /home/kira/Documents/javaFreelance/frontend && npx tsc -b` → clean.
```bash
cd /home/kira/Documents/javaFreelance
git add frontend/src/features/premature/PrematureCasePage.tsx
git commit -m "feat(premature-ui): premature case page (form + tours + signatures tabs)"
```

### Task 9.4: Wire route + "Open case" entry points

**Files:** Modify `frontend/src/App.tsx`, `frontend/src/features/premature/BedDetailPanel.tsx`, `frontend/src/features/premature/PrematureWorkspacePage.tsx`

- [ ] **Step 1: `App.tsx`** — import + route:
```tsx
import { PrematureCasePage } from '@/features/premature/PrematureCasePage';
```
Add inside the authenticated routes:
```tsx
    <Route path="/premature/admissions/:id" element={<PrematureCasePage />} />
```

- [ ] **Step 2: `BedDetailPanel.tsx`** — add an "Open case →" button under the patient row (navigates to the case page). After the existing `bed-detail-history` button, add:
```tsx
              <button
                type="button" onClick={() => navigate(`/premature/admissions/${admission.id}`)}
                className="mt-2 ms-3 inline-flex items-center gap-1 text-xs font-medium text-brand-700 hover:underline"
                data-testid="bed-detail-open-case"
              >
                {t('premature.openCase')}
                <ChevronRight size={13} className="rtl:rotate-180" />
              </button>
```

- [ ] **Step 3: tsc + commit**
Run: `cd /home/kira/Documents/javaFreelance/frontend && npx tsc -b` → clean.
```bash
cd /home/kira/Documents/javaFreelance
git add frontend/src/App.tsx frontend/src/features/premature/BedDetailPanel.tsx frontend/src/features/premature/PrematureWorkspacePage.tsx
git commit -m "feat(premature-ui): route the case page + Open case entry point"
```

### Task 9.5: i18n keys (en + ar)

**Files:** Modify `frontend/src/shared/i18n/locales/en.ts`, `ar.ts`

- [ ] **Step 1: Add to BOTH locales** under the `premature` object — `openCase`, a `case: {...}` block, and a `form: {...}` block. EN:
```typescript
    openCase: 'Open case',
    case: {
      tab: { form: 'Premature form', tours: 'Tours', overview: 'Overview' },
      addTour: 'Add tour', tourType: 'Tour', morning: 'Morning', night: 'Night',
      respRate: 'Resp. rate', pulse: 'Pulse', uop: 'UOP', babyTemp: 'Baby temp',
      incuTemp: 'Incubator temp', humidity: 'Humidity', feeding: 'Feeding', bowel: 'Bowel motion',
      vomiting: 'Vomiting', jaundice: 'Jaundice', ivAccess: 'IV access', ivFluid: 'IV fluid',
      nasalSeptum: 'Nasal septum', respSupport: 'Resp. support', tourLog: 'Tour log', noTours: 'No tours recorded yet.',
    },
    form: {
      save: 'Save form', saved: 'Premature form saved', tourAdded: 'Tour recorded',
      measurements: 'Measurements', clinicalPharmacy: 'Clinical pharmacy', culture: 'Culture',
      notes: 'Notes & prescription', signatures: 'Signatures',
      age: 'Age at admission', birthWeight: 'Birth weight', currentWeight: 'Current weight',
      gaWeeks: 'GA weeks', gaDays: 'GA days', correctedGaWeeks: 'Corrected GA weeks', correctedGaDays: 'Corrected GA days',
      length: 'Length', ofc: 'OFC (head circ.)', feedingType: 'Feeding type', others: 'Others',
      lastCultureDate: 'Last culture date', sampleType: 'Sample type', cultureResult: 'Culture result',
      rx: 'Prescription (Rx)', specialistNotes: 'Specialist doctor notes',
      pharmacySign: 'Clinical pharmacy signature', residentSign: 'Resident doctor signature', seniorSign: 'Senior resident signature',
      signerName: 'Signer name', clear: 'Clear', saveSignature: 'Save signature', reSign: 'Re-sign', signatureSaved: 'Signature saved',
    },
```
AR (mirror; translate values), e.g. `openCase: 'فتح الحالة'`, `case.tab.form: 'استمارة الخدج'`, `case.tab.tours: 'الجولات'`, `case.tab.overview: 'نظرة عامة'`, `form.save: 'حفظ الاستمارة'`, etc. Provide Arabic for every key above (the implementer translates each; keep keys identical).

- [ ] **Step 2: build + commit**
Run: `cd /home/kira/Documents/javaFreelance/frontend && npm run build` → success.
```bash
cd /home/kira/Documents/javaFreelance
git add frontend/src/shared/i18n/locales/en.ts frontend/src/shared/i18n/locales/ar.ts
git commit -m "feat(premature-ui): en/ar i18n for case page, form, tours, signatures"
```

---

# PHASE 10 — E2E + final verification

### Task 10.1: Playwright E2E

**Files:** Create `frontend/e2e/premature-form.spec.ts`

- [ ] **Step 1: Write the spec** (seed under-care bed via API → open case → fill+save form → add tour → assert)
```typescript
import { test, expect } from '@playwright/test';
import { login } from './helpers/auth';
import { authedContext } from './helpers/api';
import { registerPatient, API_BASE } from './helpers/seeds';

async function seedUnderCare(): Promise<{ admissionId: string; bedCode: string }> {
  const admin = await authedContext('admin');
  const premature = await authedContext('premature');
  const cashier = await authedContext('cashier');
  const bedCode = `PREM-FRM-${Date.now()}`;
  const bed = await (await premature.post(`${API_BASE}/premature/beds`, { data: { code: bedCode, room: 'Form' } })).json();
  const patient = await registerPatient(admin, { gender: 'MALE' });
  const visit = await (await admin.post(`${API_BASE}/visits`, { data: { patientId: patient.id, visitType: 'PREMATURE' } })).json();
  const adm = await (await premature.post(`${API_BASE}/premature/admissions`, {
    data: { visitId: visit.id, bedId: bed.id, stayValue: 3, stayUnit: 'DAYS' } })).json();
  const pending = await (await cashier.get(`${API_BASE}/payments?status=PENDING&size=100`)).json();
  const initial = pending.content.find((p: any) => p.visitId === visit.id && p.stage === 'INITIAL');
  await cashier.post(`${API_BASE}/payments/${initial.id}/approve`, { data: { paymentMethod: 'CASH' } });
  await expect(async () => {
    const beds = await (await premature.get(`${API_BASE}/premature/beds`)).json();
    expect(beds.find((b: any) => b.id === bed.id).status).toBe('OCCUPIED');
  }).toPass({ timeout: 10_000 });
  await admin.dispose(); await premature.dispose(); await cashier.dispose();
  return { admissionId: adm.id, bedCode };
}

test('premature staff fill the Premature Form and record a tour', async ({ page }) => {
  const { admissionId } = await seedUnderCare();
  await login(page, 'premature');
  await page.goto(`/premature/admissions/${admissionId}`);

  // Form tab (default). Fill the mandatory fields and save.
  await page.getByTestId('case-tab-form').click();
  await page.getByTestId('f-currentWeightKg').fill('1.45');
  await page.getByTestId('f-feedingType').fill('EBM');
  // Some mandatory fields pre-fill from registration; fill any that are empty to be safe.
  for (const id of ['f-ageText']) {
    const el = page.getByTestId(id);
    if (await el.count() && !(await el.inputValue())) await el.fill('12 days');
  }
  await page.getByTestId('save-form').click();
  await expect(page.getByTestId('save-form')).toBeEnabled(); // mutation settled

  // Reload → form persisted (current weight retained).
  await page.reload();
  await expect(page.getByTestId('f-currentWeightKg')).toHaveValue('1.45');

  // Tours tab → add a Morning tour.
  await page.getByTestId('case-tab-tours').click();
  await page.getByTestId('tour-respRate').fill('40');
  await page.getByTestId('tour-spo2').fill('96');
  await page.getByTestId('tour-pulse').fill('140');
  await page.getByTestId('tour-uop').fill('2 ml/kg');
  await page.getByTestId('tour-temp').fill('36.8');
  await page.getByRole('checkbox', { name: 'CPAP' }).check();
  await page.getByTestId('save-tour').click();
  await expect(page.getByTestId('tour-list')).toContainText(/RR 40/);
});

test('the bed detail drawer links to the case page', async ({ page }) => {
  const { admissionId, bedCode } = await seedUnderCare();
  await login(page, 'premature');
  await page.goto('/departments/premature');
  await page.getByTestId(`bed-${bedCode}`).click();
  await page.getByTestId('bed-detail-open-case').click();
  await expect(page).toHaveURL(new RegExp(`/premature/admissions/${admissionId}`));
  await expect(page.getByTestId('prem-form')).toBeVisible();
});
```

- [ ] **Step 2: Run (stack must be up)** `cd /home/kira/Documents/javaFreelance/frontend && npx playwright test premature-form`
Expected: 2 passing. Commit:
```bash
cd /home/kira/Documents/javaFreelance
git add frontend/e2e/premature-form.spec.ts
git commit -m "test(premature): E2E for premature form + tours + open-case"
```

### Task 10.2: Full verification

- [ ] **Step 1: Backend** `cd /home/kira/Documents/javaFreelance/backend && JAVA_HOME=/usr/lib/jvm/java-25-openjdk mvn -pl premature,app -am verify -Dsurefire.failIfNoSpecifiedTests=false` → BUILD SUCCESS (all unit + ITs + ArchUnit).
- [ ] **Step 2: Frontend** `cd /home/kira/Documents/javaFreelance/frontend && npx tsc -b && npm run build` → clean.
- [ ] **Step 3: Full E2E** (stack up) `npx playwright test` → all green (existing + new premature-form).
- [ ] **Step 4: Final commit if needed.**

## Definition of Done
`mvn -pl premature,app -am verify` green; `tsc`/`build` clean; full Playwright suite green; a premature staff can open a case, fill & save the Premature Form (pre-filled from registration), record Morning/Night tours, and draw/save signatures; en/ar localized.
