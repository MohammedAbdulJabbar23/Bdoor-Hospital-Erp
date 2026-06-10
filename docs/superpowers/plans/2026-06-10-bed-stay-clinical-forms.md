# Bed-Stay Clinical Forms + Patient Case Form Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Digitise the three shared clinical paper forms (Medical History & Physical Examination Sheet, Nursing Procedures log, Treatment Chart) for both Premature and Emergency bed-stays, plus the premature-only Patient Case Form (P6), per spec `docs/superpowers/specs/2026-06-10-bed-stay-clinical-forms-design.md`.

**Architecture:** New backend module `bed-stay-forms` holds the three shared aggregates keyed by `(StayDepartment, stayId)`. A `StayDirectory` port (implemented inside `premature` and `emergency`) validates stays and supplies patient prefill. The premature-only `PatientCaseForm` lives in `backend/premature`. Frontend: `BedStayCasePage` gains an `extraTabs` prop; shared tab components live in `frontend/src/features/beds/case/forms/`.

**Tech Stack:** Spring Boot 3.3 vertical-slice modules, JPA + Flyway (Postgres), Maven Failsafe ITs (Testcontainers), React + TanStack Query + Tailwind, Playwright e2e.

**Build commands (this machine — see memory `hms-build-test-env`):**
- Always `cd backend && JAVA_HOME=/usr/lib/jvm/java-25-openjdk mvn …` (no `./mvnw`; JAVA_HOME inline on EVERY call).
- Unit tests: `mvn -pl bed-stay-forms -am test`. ITs (Failsafe): `mvn -pl app -am verify` (ITs are `*IT`, they do NOT run under plain `test`). Add `-Dsurefire.failIfNoSpecifiedTests=false` whenever using `-Dtest=`/`-Dit.test=` filters.
- If Testcontainers fails with "client version 1.32 is too old": create `~/.docker-java.properties` containing `api.version=1.40`.
- E2E stack: `docker compose up -d --wait db` → `mvn -pl app spring-boot:run` (8080) → `cd frontend && npm run dev` (5173) → `npx playwright test <spec>`.

---

## File structure

**New backend module `backend/bed-stay-forms`** (package root `com.albudoor.hms.bedstayforms`):
```
pom.xml
src/main/java/com/albudoor/hms/bedstayforms/
  BedStayFormsAutoConfig.java
  domain/      StayDepartment, FormSignature, MhSignatureSlot, MedicalHistoryData,
               MedicalHistorySheet, NursingProcedureEntry, TreatmentRow, TreatmentChart
  directory/   StayDirectory, StayInfo, AgeText, StayDirectoryRegistry
  access/      BedStayAccess, CurrentUser
  infrastructure/  MedicalHistoryRepository, NursingProcedureRepository, TreatmentChartRepository
  api/         StayPrefillDto, SignatureView, MedicalHistoryDto, MedicalHistoryResponse,
               NursingProcedureDto, TreatmentRowDto, TreatmentChartDto
  medicalhistory/    MedicalHistoryController, MedicalHistoryHandler, UpsertMedicalHistoryCommand
  nursingprocedures/ NursingProceduresController, NursingProceduresHandler, AddNursingProcedureCommand
  treatmentcharts/   TreatmentChartsController, TreatmentChartsHandler, UpsertTreatmentChartCommand
src/main/resources/db/migration/V027__bed_stay_forms.sql
src/test/java/com/albudoor/hms/bedstayforms/domain/  TreatmentChartTest, MedicalHistorySheetTest
```

**Modified backend:** root `backend/pom.xml` (modules list); `backend/{premature,emergency,app}/pom.xml` (+dep); `HmsApplication` (+import); `ArchitectureTest` (+rule); premature module gains `staydirectory/PrematureStayDirectory`, `domain/{PatientCaseForm,PatientCaseData}`, `infrastructure/PatientCaseFormRepository`, `upsertcaseform/` slice, `api/PatientCaseFormResponse`, changes to `api/PrematureCaseResponse` + `getcase/GetCaseHandler`, `src/main/resources/db/migration/V028__premature_patient_case.sql`; emergency module gains `staydirectory/EmergencyStayDirectory`.

**New ITs:** `backend/app/src/test/java/com/albudoor/hms/app/bedstayforms/{BedStayFormsIT,BedStayFormsEmergencyIT,BedStayFormsAuthzIT}.java`, `backend/app/src/test/java/com/albudoor/hms/app/premature/PatientCaseFormIT.java`.

**Frontend:** move `features/premature/SignaturePad.tsx` → `shared/ui/SignaturePad.tsx`; new `features/beds/case/forms/{api.ts,MedicalHistoryTab.tsx,NursingTab.tsx,TreatmentTab.tsx}`; new `features/premature/CaseFileTab.tsx`; modify `features/beds/case/BedStayCasePage.tsx` (extraTabs), `features/premature/{PrematureCasePage.tsx,api.ts}`, `features/emergency/EmergencyCasePage.tsx`, `shared/i18n/locales/{en.ts,ar.ts}`; new e2e `frontend/e2e/bed-stay-clinical-forms.spec.ts`.

**Migration numbers:** V027/V028 are next-free as of 2026-06-10 (`find backend -path "*db/migration*" -name "*.sql" -not -path "*/target/*"` tops out at V026). Re-check before creating; bump both if taken.

---

### Task 1: Module skeleton + wiring

**Files:**
- Create: `backend/bed-stay-forms/pom.xml`
- Create: `backend/bed-stay-forms/src/main/java/com/albudoor/hms/bedstayforms/BedStayFormsAutoConfig.java`
- Modify: `backend/pom.xml` (modules list)
- Modify: `backend/premature/pom.xml`, `backend/emergency/pom.xml`, `backend/app/pom.xml` (add dependency)
- Modify: `backend/app/src/main/java/com/albudoor/hms/app/HmsApplication.java`

- [ ] **Step 1: Create the module pom**

Copy `backend/premature/pom.xml` to `backend/bed-stay-forms/pom.xml` (keeps the parent block + plugin config identical), then set `<artifactId>bed-stay-forms</artifactId>` and reduce the `com.albudoor.hms` dependencies to exactly two: `platform` and `identity` (delete the `visit-management`, `cashier`, `catalogue`, `patient-registry` dependency blocks). Keep all `spring-boot-starter-*`, `lombok`, and test deps as-is.

- [ ] **Step 2: Register the module in the reactor**

In `backend/pom.xml`, add inside `<modules>` directly before `<module>premature</module>`:
```xml
        <module>bed-stay-forms</module>
```
And in the `<dependencyManagement>` section, add alongside the other module entries (mirror the `premature` entry exactly, same version property):
```xml
            <dependency>
                <groupId>com.albudoor.hms</groupId>
                <artifactId>bed-stay-forms</artifactId>
                <version>${project.version}</version>
            </dependency>
```
(Check how the sibling managed entries declare `<version>` and copy that form.)

- [ ] **Step 3: AutoConfig class**

```java
package com.albudoor.hms.bedstayforms;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@ComponentScan(basePackages = "com.albudoor.hms.bedstayforms")
@EntityScan(basePackages = "com.albudoor.hms.bedstayforms.domain")
@EnableJpaRepositories(basePackages = "com.albudoor.hms.bedstayforms.infrastructure")
public class BedStayFormsAutoConfig {
}
```

- [ ] **Step 4: Wire dependent modules**

In `backend/premature/pom.xml`, `backend/emergency/pom.xml`, and `backend/app/pom.xml`, add (next to their existing `com.albudoor.hms` deps):
```xml
        <dependency>
            <groupId>com.albudoor.hms</groupId>
            <artifactId>bed-stay-forms</artifactId>
        </dependency>
```
In `backend/app/src/main/java/com/albudoor/hms/app/HmsApplication.java`, add to the `@Import({...})` list (and the matching import statement):
```java
import com.albudoor.hms.bedstayforms.BedStayFormsAutoConfig;
...
        BedStayFormsAutoConfig.class,
```

- [ ] **Step 5: Verify the reactor builds**

Run: `cd backend && JAVA_HOME=/usr/lib/jvm/java-25-openjdk mvn -pl bed-stay-forms,app -am install -DskipTests -q`
Expected: BUILD SUCCESS (empty module compiles, app still boots its context at compile level).

- [ ] **Step 6: Commit**

```bash
git add backend/pom.xml backend/bed-stay-forms backend/premature/pom.xml backend/emergency/pom.xml backend/app/pom.xml backend/app/src/main/java/com/albudoor/hms/app/HmsApplication.java
git commit -m "feat(bed-stay-forms): new shared module skeleton wired into reactor + app"
```

---

### Task 2: Migration + domain aggregates (TDD on domain behavior)

**Files:**
- Create: `backend/bed-stay-forms/src/main/resources/db/migration/V027__bed_stay_forms.sql`
- Create: `backend/bed-stay-forms/src/main/java/com/albudoor/hms/bedstayforms/domain/{StayDepartment,FormSignature,MhSignatureSlot,MedicalHistoryData,MedicalHistorySheet,NursingProcedureEntry,TreatmentRow,TreatmentChart}.java`
- Test: `backend/bed-stay-forms/src/test/java/com/albudoor/hms/bedstayforms/domain/{TreatmentChartTest,MedicalHistorySheetTest}.java`

- [ ] **Step 1: Write the failing domain unit tests**

`TreatmentChartTest.java`:
```java
package com.albudoor.hms.bedstayforms.domain;

import com.albudoor.hms.platform.exception.DomainException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TreatmentChartTest {

    private TreatmentChart chart() {
        return TreatmentChart.create(StayDepartment.PREMATURE, UUID.randomUUID(), LocalDate.of(2026, 6, 10));
    }

    @Test
    void replaceRows_replaces_the_whole_row_set() {
        TreatmentChart c = chart();
        c.replaceRows(List.of(
                new TreatmentRow("Ampicillin", "50mg/kg", "q12h", "08", null, null, null, null, "20"),
                new TreatmentRow("Gentamicin", "4mg/kg", "q24h", "10", null, null, null, null, null)));
        assertThat(c.getRows()).hasSize(2);

        c.replaceRows(List.of(new TreatmentRow("Caffeine citrate", "5mg/kg", "q24h", null, null, null, null, null, null)));
        assertThat(c.getRows()).hasSize(1);
        assertThat(c.getRows().get(0).getMedicineName()).isEqualTo("Caffeine citrate");
    }

    @Test
    void replaceRows_rejects_blank_medicine_name() {
        assertThatThrownBy(() -> chart().replaceRows(
                List.of(new TreatmentRow("  ", null, null, null, null, null, null, null, null))))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void create_requires_chart_date() {
        assertThatThrownBy(() -> TreatmentChart.create(StayDepartment.PREMATURE, UUID.randomUUID(), null))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void doctor_signature_starts_empty_and_can_be_applied() {
        TreatmentChart c = chart();
        assertThat(c.getDoctorSignature().present()).isFalse();
        c.applyDoctorSignature("key-1", "Dr. A", UUID.randomUUID());
        assertThat(c.getDoctorSignature().present()).isTrue();
        assertThat(c.getDoctorSignature().getSignerName()).isEqualTo("Dr. A");
    }
}
```

`MedicalHistorySheetTest.java`:
```java
package com.albudoor.hms.bedstayforms.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MedicalHistorySheetTest {

    @Test
    void update_sets_all_fields_and_signatures_apply_per_slot() {
        MedicalHistorySheet s = MedicalHistorySheet.create(StayDepartment.EMERGENCY, UUID.randomUUID());
        s.update(new MedicalHistoryData(
                new BigDecimal("3.2"), new BigDecimal("49"), "Dr. B",
                "Fever 2 days", "Started gradually", "None", "Neonatal jaundice",
                "Diabetes (mother)", "No known allergies",
                "No", "No", "Normal",
                "None", "Chest clear, abdomen soft"));
        assertThat(s.getChiefComplaint()).isEqualTo("Fever 2 days");
        assertThat(s.getWeightKg()).isEqualByComparingTo("3.2");
        assertThat(s.getSocialSleep()).isEqualTo("Normal");

        assertThat(s.signature(MhSignatureSlot.SPECIALIST).present()).isFalse();
        s.applySignature(MhSignatureSlot.SPECIALIST, "k1", "Dr. Spec", UUID.randomUUID());
        assertThat(s.signature(MhSignatureSlot.SPECIALIST).present()).isTrue();
        assertThat(s.signature(MhSignatureSlot.PERMANENT).present()).isFalse();
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend && JAVA_HOME=/usr/lib/jvm/java-25-openjdk mvn -pl bed-stay-forms -am test -q`
Expected: COMPILATION ERROR (classes don't exist yet).

- [ ] **Step 3: Write the domain classes**

`StayDepartment.java`:
```java
package com.albudoor.hms.bedstayforms.domain;

/** Which bed-stay module a clinical form belongs to. Matches the <DEPT>_STAFF role prefix. */
public enum StayDepartment { PREMATURE, EMERGENCY }
```

`MhSignatureSlot.java`:
```java
package com.albudoor.hms.bedstayforms.domain;

/** The three signature lines on the Medical History & Physical Examination Sheet. */
public enum MhSignatureSlot { SPECIALIST, PERMANENT, RESIDENT }
```

`FormSignature.java` (same shape as `com.albudoor.hms.premature.domain.Signature`):
```java
package com.albudoor.hms.bedstayforms.domain;

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
public class FormSignature {
    @Column(name = "sign_key", length = 500)
    private String imageKey;
    @Column(name = "sign_name", length = 200)
    private String signerName;
    @Column(name = "signed_by")
    private UUID signedBy;
    @Column(name = "signed_at")
    private Instant signedAt;

    public static FormSignature empty() { return new FormSignature(null, null, null, null); }
    public boolean present() { return imageKey != null; }
}
```

`MedicalHistoryData.java`:
```java
package com.albudoor.hms.bedstayforms.domain;

import java.math.BigDecimal;

/** Editable Medical History Sheet fields (BRD REC-005 §6.6.1), all optional. */
public record MedicalHistoryData(
        BigDecimal weightKg, BigDecimal heightCm, String doctorName,
        String chiefComplaint, String presentIllnessHx, String psHx, String pmHx,
        String familyHx, String allergicHx,
        String socialSmoker, String socialAlcohol, String socialSleep,
        String drugHx, String physicalExamination
) {}
```

`MedicalHistorySheet.java`:
```java
package com.albudoor.hms.bedstayforms.domain;

import com.albudoor.hms.platform.domain.AggregateRoot;
import com.albudoor.hms.platform.exception.DomainException;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** BRD REC-005 §6.6.1 — Medical History and Physical Examination Sheet, one per bed-stay. */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "stay_medical_history")
public class MedicalHistorySheet extends AggregateRoot {

    @Id
    private UUID id;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StayDepartment department;
    @Column(name = "stay_id", nullable = false)
    private UUID stayId;

    @Column(name = "weight_kg", precision = 6, scale = 2) private BigDecimal weightKg;
    @Column(name = "height_cm", precision = 6, scale = 2) private BigDecimal heightCm;
    @Column(name = "doctor_name", length = 200) private String doctorName;
    @Column(name = "chief_complaint", length = 2000) private String chiefComplaint;
    @Column(name = "present_illness_hx", length = 4000) private String presentIllnessHx;
    @Column(name = "ps_hx", length = 2000) private String psHx;
    @Column(name = "pm_hx", length = 2000) private String pmHx;
    @Column(name = "family_hx", length = 2000) private String familyHx;
    @Column(name = "allergic_hx", length = 2000) private String allergicHx;
    @Column(name = "social_smoker", length = 200) private String socialSmoker;
    @Column(name = "social_alcohol", length = 200) private String socialAlcohol;
    @Column(name = "social_sleep", length = 200) private String socialSleep;
    @Column(name = "drug_hx", length = 2000) private String drugHx;
    @Column(name = "physical_examination", length = 4000) private String physicalExamination;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "imageKey", column = @Column(name = "specialist_sign_key", length = 500)),
            @AttributeOverride(name = "signerName", column = @Column(name = "specialist_sign_name", length = 200)),
            @AttributeOverride(name = "signedBy", column = @Column(name = "specialist_signed_by")),
            @AttributeOverride(name = "signedAt", column = @Column(name = "specialist_signed_at")),
    })
    private FormSignature specialistSignature = FormSignature.empty();

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "imageKey", column = @Column(name = "permanent_sign_key", length = 500)),
            @AttributeOverride(name = "signerName", column = @Column(name = "permanent_sign_name", length = 200)),
            @AttributeOverride(name = "signedBy", column = @Column(name = "permanent_signed_by")),
            @AttributeOverride(name = "signedAt", column = @Column(name = "permanent_signed_at")),
    })
    private FormSignature permanentSignature = FormSignature.empty();

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "imageKey", column = @Column(name = "resident_sign_key", length = 500)),
            @AttributeOverride(name = "signerName", column = @Column(name = "resident_sign_name", length = 200)),
            @AttributeOverride(name = "signedBy", column = @Column(name = "resident_signed_by")),
            @AttributeOverride(name = "signedAt", column = @Column(name = "resident_signed_at")),
    })
    private FormSignature residentSignature = FormSignature.empty();

    public static MedicalHistorySheet create(StayDepartment department, UUID stayId) {
        if (department == null || stayId == null) {
            throw new DomainException("STAY_REF_REQUIRED", "department and stay are required");
        }
        MedicalHistorySheet s = new MedicalHistorySheet();
        s.id = UUID.randomUUID();
        s.department = department;
        s.stayId = stayId;
        return s;
    }

    public void update(MedicalHistoryData d) {
        this.weightKg = d.weightKg();
        this.heightCm = d.heightCm();
        this.doctorName = d.doctorName();
        this.chiefComplaint = d.chiefComplaint();
        this.presentIllnessHx = d.presentIllnessHx();
        this.psHx = d.psHx();
        this.pmHx = d.pmHx();
        this.familyHx = d.familyHx();
        this.allergicHx = d.allergicHx();
        this.socialSmoker = d.socialSmoker();
        this.socialAlcohol = d.socialAlcohol();
        this.socialSleep = d.socialSleep();
        this.drugHx = d.drugHx();
        this.physicalExamination = d.physicalExamination();
    }

    public void applySignature(MhSignatureSlot slot, String imageKey, String signerName, UUID signedBy) {
        FormSignature s = new FormSignature(imageKey, signerName, signedBy, Instant.now());
        switch (slot) {
            case SPECIALIST -> this.specialistSignature = s;
            case PERMANENT -> this.permanentSignature = s;
            case RESIDENT -> this.residentSignature = s;
        }
    }

    public FormSignature signature(MhSignatureSlot slot) {
        return switch (slot) {
            case SPECIALIST -> specialistSignature == null ? FormSignature.empty() : specialistSignature;
            case PERMANENT -> permanentSignature == null ? FormSignature.empty() : permanentSignature;
            case RESIDENT -> residentSignature == null ? FormSignature.empty() : residentSignature;
        };
    }
}
```

`NursingProcedureEntry.java`:
```java
package com.albudoor.hms.bedstayforms.domain;

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

/**
 * BRD REC-005 §6.6.2 — one row of the Nursing Procedures log. Append-only (no edit/delete
 * slice; matches the paper log + the BRD auditability rule). The nurse is auto-attributed
 * from the authenticated user instead of a drawn per-row sign.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "stay_nursing_procedure")
public class NursingProcedureEntry extends AggregateRoot {

    @Id
    private UUID id;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StayDepartment department;
    @Column(name = "stay_id", nullable = false)
    private UUID stayId;

    @Column(name = "procedure_name", nullable = false, length = 300)
    private String procedureName;
    @Column(name = "performed_at", nullable = false)
    private Instant performedAt;
    @Column(length = 2000)
    private String note;
    @Column(name = "nurse_name", length = 200)
    private String nurseName;
    @Column(name = "recorded_by")
    private UUID recordedBy;

    public static NursingProcedureEntry record(StayDepartment department, UUID stayId,
                                               String procedureName, Instant performedAt,
                                               String note, String nurseName, UUID recordedBy) {
        if (department == null || stayId == null) {
            throw new DomainException("STAY_REF_REQUIRED", "department and stay are required");
        }
        if (procedureName == null || procedureName.isBlank()) {
            throw new DomainException("PROCEDURE_NAME_REQUIRED", "procedure name is required");
        }
        if (performedAt == null) {
            throw new DomainException("PERFORMED_AT_REQUIRED", "performed-at time is required");
        }
        NursingProcedureEntry e = new NursingProcedureEntry();
        e.id = UUID.randomUUID();
        e.department = department;
        e.stayId = stayId;
        e.procedureName = procedureName.trim();
        e.performedAt = performedAt;
        e.note = note;
        e.nurseName = nurseName;
        e.recordedBy = recordedBy;
        return e;
    }
}
```

`TreatmentRow.java`:
```java
package com.albudoor.hms.bedstayforms.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One medicine line on the Treatment Chart. The six timing columns mirror the paper's
 * AM/AM/PM/PM/PM/AM grid; free short text because the paper is hand-annotated with times.
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class TreatmentRow {
    @Column(name = "medicine_name", nullable = false, length = 300) private String medicineName;
    @Column(length = 120) private String dose;
    @Column(length = 120) private String frequency;
    @Column(name = "timing1", length = 60) private String timing1;
    @Column(name = "timing2", length = 60) private String timing2;
    @Column(name = "timing3", length = 60) private String timing3;
    @Column(name = "timing4", length = 60) private String timing4;
    @Column(name = "timing5", length = 60) private String timing5;
    @Column(name = "timing6", length = 60) private String timing6;
}
```

`TreatmentChart.java`:
```java
package com.albudoor.hms.bedstayforms.domain;

import com.albudoor.hms.platform.domain.AggregateRoot;
import com.albudoor.hms.platform.exception.DomainException;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.AttributeOverrides;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * BRD REC-005 §6.6.3 — Treatment Chart: one chart per stay per date, N medicine rows
 * (the paper's 11-row limit is a page artifact — "additional pages supported").
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "stay_treatment_chart")
public class TreatmentChart extends AggregateRoot {

    @Id
    private UUID id;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StayDepartment department;
    @Column(name = "stay_id", nullable = false)
    private UUID stayId;
    @Column(name = "chart_date", nullable = false)
    private LocalDate chartDate;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "stay_treatment_chart_row", joinColumns = @JoinColumn(name = "chart_id"))
    @OrderColumn(name = "position")
    private List<TreatmentRow> rows = new ArrayList<>();

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name = "imageKey", column = @Column(name = "doctor_sign_key", length = 500)),
            @AttributeOverride(name = "signerName", column = @Column(name = "doctor_sign_name", length = 200)),
            @AttributeOverride(name = "signedBy", column = @Column(name = "doctor_signed_by")),
            @AttributeOverride(name = "signedAt", column = @Column(name = "doctor_signed_at")),
    })
    private FormSignature doctorSignature = FormSignature.empty();

    public static TreatmentChart create(StayDepartment department, UUID stayId, LocalDate chartDate) {
        if (department == null || stayId == null) {
            throw new DomainException("STAY_REF_REQUIRED", "department and stay are required");
        }
        if (chartDate == null) {
            throw new DomainException("CHART_DATE_REQUIRED", "chart date is required");
        }
        TreatmentChart c = new TreatmentChart();
        c.id = UUID.randomUUID();
        c.department = department;
        c.stayId = stayId;
        c.chartDate = chartDate;
        return c;
    }

    /** Form-style editing: each save replaces the full row set (like the premature form upsert). */
    public void replaceRows(List<TreatmentRow> newRows) {
        List<TreatmentRow> safe = newRows == null ? List.of() : newRows;
        for (TreatmentRow r : safe) {
            if (r.getMedicineName() == null || r.getMedicineName().isBlank()) {
                throw new DomainException("MEDICINE_NAME_REQUIRED", "each row needs a medicine name");
            }
        }
        this.rows.clear();
        this.rows.addAll(safe);
    }

    public void applyDoctorSignature(String imageKey, String signerName, UUID signedBy) {
        this.doctorSignature = new FormSignature(imageKey, signerName, signedBy, Instant.now());
    }

    public FormSignature getDoctorSignature() {
        return doctorSignature == null ? FormSignature.empty() : doctorSignature;
    }
}
```

- [ ] **Step 4: Write the Flyway migration `V027__bed_stay_forms.sql`**

```sql
-- HMS-BRD-REC-005 §6.6 / HMS-BRD-REC-004 §6.7 — clinical forms shared by Premature & Emergency
-- bed-stays: Medical History Sheet, Nursing Procedures log, Treatment Chart.

CREATE TABLE stay_medical_history (
    id                      UUID PRIMARY KEY,
    department              VARCHAR(20)  NOT NULL,
    stay_id                 UUID         NOT NULL,
    weight_kg               NUMERIC(6,2),
    height_cm               NUMERIC(6,2),
    doctor_name             VARCHAR(200),
    chief_complaint         VARCHAR(2000),
    present_illness_hx      VARCHAR(4000),
    ps_hx                   VARCHAR(2000),
    pm_hx                   VARCHAR(2000),
    family_hx               VARCHAR(2000),
    allergic_hx             VARCHAR(2000),
    social_smoker           VARCHAR(200),
    social_alcohol          VARCHAR(200),
    social_sleep            VARCHAR(200),
    drug_hx                 VARCHAR(2000),
    physical_examination    VARCHAR(4000),
    specialist_sign_key     VARCHAR(500),
    specialist_sign_name    VARCHAR(200),
    specialist_signed_by    UUID,
    specialist_signed_at    TIMESTAMPTZ,
    permanent_sign_key      VARCHAR(500),
    permanent_sign_name     VARCHAR(200),
    permanent_signed_by     UUID,
    permanent_signed_at     TIMESTAMPTZ,
    resident_sign_key       VARCHAR(500),
    resident_sign_name      VARCHAR(200),
    resident_signed_by      UUID,
    resident_signed_at      TIMESTAMPTZ,
    version                 BIGINT       NOT NULL DEFAULT 0,
    created_at              TIMESTAMPTZ  NOT NULL,
    created_by              VARCHAR(100),
    updated_at              TIMESTAMPTZ,
    updated_by              VARCHAR(100),
    CONSTRAINT uk_stay_mh UNIQUE (department, stay_id),
    CONSTRAINT chk_stay_mh_dept CHECK (department IN ('PREMATURE', 'EMERGENCY'))
);

CREATE TABLE stay_nursing_procedure (
    id              UUID PRIMARY KEY,
    department      VARCHAR(20)  NOT NULL,
    stay_id         UUID         NOT NULL,
    procedure_name  VARCHAR(300) NOT NULL,
    performed_at    TIMESTAMPTZ  NOT NULL,
    note            VARCHAR(2000),
    nurse_name      VARCHAR(200),
    recorded_by     UUID,
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL,
    created_by      VARCHAR(100),
    updated_at      TIMESTAMPTZ,
    updated_by      VARCHAR(100),
    CONSTRAINT chk_stay_np_dept CHECK (department IN ('PREMATURE', 'EMERGENCY'))
);
CREATE INDEX idx_stay_np_stay ON stay_nursing_procedure (department, stay_id, performed_at DESC);

CREATE TABLE stay_treatment_chart (
    id              UUID PRIMARY KEY,
    department      VARCHAR(20)  NOT NULL,
    stay_id         UUID         NOT NULL,
    chart_date      DATE         NOT NULL,
    doctor_sign_key VARCHAR(500),
    doctor_sign_name VARCHAR(200),
    doctor_signed_by UUID,
    doctor_signed_at TIMESTAMPTZ,
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL,
    created_by      VARCHAR(100),
    updated_at      TIMESTAMPTZ,
    updated_by      VARCHAR(100),
    CONSTRAINT uk_stay_tc UNIQUE (department, stay_id, chart_date),
    CONSTRAINT chk_stay_tc_dept CHECK (department IN ('PREMATURE', 'EMERGENCY'))
);

CREATE TABLE stay_treatment_chart_row (
    chart_id        UUID         NOT NULL REFERENCES stay_treatment_chart(id) ON DELETE CASCADE,
    position        INTEGER      NOT NULL,
    medicine_name   VARCHAR(300) NOT NULL,
    dose            VARCHAR(120),
    frequency       VARCHAR(120),
    timing1         VARCHAR(60),
    timing2         VARCHAR(60),
    timing3         VARCHAR(60),
    timing4         VARCHAR(60),
    timing5         VARCHAR(60),
    timing6         VARCHAR(60),
    PRIMARY KEY (chart_id, position)
);
```
Note: no FK from `stay_id` to a department table — the column points at either `prem_admission` or `emergency_case` depending on `department`, so referential integrity is enforced by the `StayDirectory` lookup, not the database.

- [ ] **Step 5: Run the domain tests**

Run: `cd backend && JAVA_HOME=/usr/lib/jvm/java-25-openjdk mvn -pl bed-stay-forms -am test -q`
Expected: PASS (4 + 1 tests green).

- [ ] **Step 6: Commit**

```bash
git add backend/bed-stay-forms
git commit -m "feat(bed-stay-forms): domain aggregates + V027 migration for the three shared clinical forms"
```

---

### Task 3: StayDirectory port, access checks, department implementations, ArchUnit rule

**Files:**
- Create: `backend/bed-stay-forms/src/main/java/com/albudoor/hms/bedstayforms/directory/{StayDirectory,StayInfo,AgeText,StayDirectoryRegistry}.java`
- Create: `backend/bed-stay-forms/src/main/java/com/albudoor/hms/bedstayforms/access/{BedStayAccess,CurrentUser}.java`
- Create: `backend/premature/src/main/java/com/albudoor/hms/premature/staydirectory/PrematureStayDirectory.java`
- Create: `backend/emergency/src/main/java/com/albudoor/hms/emergency/staydirectory/EmergencyStayDirectory.java`
- Modify: `backend/app/src/test/java/com/albudoor/hms/app/ArchitectureTest.java`

- [ ] **Step 1: Port + prefill types**

`StayDirectory.java`:
```java
package com.albudoor.hms.bedstayforms.directory;

import com.albudoor.hms.bedstayforms.domain.StayDepartment;

import java.util.Optional;
import java.util.UUID;

/**
 * Port answered by each bed-stay department module (premature, emergency): does this stay
 * exist, is it still open for writes, and the patient prefill the paper forms print
 * automatically (Pt. Name / Pt. Code / Age / DOA).
 */
public interface StayDirectory {
    StayDepartment department();
    Optional<StayInfo> find(UUID stayId);
}
```

`StayInfo.java`:
```java
package com.albudoor.hms.bedstayforms.directory;

import java.time.Instant;
import java.util.UUID;

public record StayInfo(
        UUID patientId, String patientName, String patientMrn,
        String ageText, Instant admittedAt, boolean open
) {}
```

`AgeText.java` (same derivation as `GetCaseHandler.deriveAge`, hoisted so both directory impls share it):
```java
package com.albudoor.hms.bedstayforms.directory;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Human-readable age at admission, e.g. "12 days" / "3 weeks". */
public final class AgeText {
    private AgeText() {}

    public static String derive(LocalDate dob, LocalDate at) {
        if (dob == null || at == null || at.isBefore(dob)) return null;
        long days = ChronoUnit.DAYS.between(dob, at);
        if (days < 14) return days + (days == 1 ? " day" : " days");
        return (days / 7) + " weeks";
    }
}
```

`StayDirectoryRegistry.java`:
```java
package com.albudoor.hms.bedstayforms.directory;

import com.albudoor.hms.bedstayforms.domain.StayDepartment;
import com.albudoor.hms.platform.exception.DomainException;
import com.albudoor.hms.platform.exception.NotFoundException;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class StayDirectoryRegistry {

    private final Map<StayDepartment, StayDirectory> byDepartment = new EnumMap<>(StayDepartment.class);

    public StayDirectoryRegistry(List<StayDirectory> directories) {
        for (StayDirectory d : directories) byDepartment.put(d.department(), d);
    }

    /** 404 if the department has no directory bean or the stay doesn't exist. */
    public StayInfo require(StayDepartment department, UUID stayId) {
        StayDirectory d = byDepartment.get(department);
        if (d == null) throw new NotFoundException("No bed-stay directory for " + department);
        return d.find(stayId)
                .orElseThrow(() -> new NotFoundException("Stay not found: " + department + "/" + stayId));
    }

    /** Writes are rejected once the case is closed/cancelled (forms become read-only). */
    public StayInfo requireOpen(StayDepartment department, UUID stayId) {
        StayInfo info = require(department, stayId);
        if (!info.open()) {
            throw new DomainException("STAY_CLOSED", "The case is closed; clinical forms are read-only");
        }
        return info;
    }
}
```

- [ ] **Step 2: Access checks + current-user helper**

`BedStayAccess.java`:
```java
package com.albudoor.hms.bedstayforms.access;

import com.albudoor.hms.bedstayforms.domain.StayDepartment;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Department-scoped authorization: <DEPT>_STAFF may act only on their own department's
 * stays; DOCTOR/NURSE/ADMIN are hospital-wide, with write level per the BRD actor table
 * (doctors own medical history + treatment chart, nurses own the procedures log).
 * A @PreAuthorize can't see the {department} path variable, hence this runtime check.
 */
@Component
public class BedStayAccess {

    public void checkRead(StayDepartment dept) { check(dept, "ROLE_DOCTOR", "ROLE_NURSE", "ROLE_ADMIN"); }
    public void checkDoctorWrite(StayDepartment dept) { check(dept, "ROLE_DOCTOR", "ROLE_ADMIN"); }
    public void checkNurseWrite(StayDepartment dept) { check(dept, "ROLE_NURSE", "ROLE_ADMIN"); }

    private void check(StayDepartment dept, String... globalRoles) {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null) throw new AccessDeniedException("Not authenticated");
        Set<String> roles = a.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
        if (roles.contains("ROLE_" + dept.name() + "_STAFF")) return;
        for (String r : globalRoles) if (roles.contains(r)) return;
        throw new AccessDeniedException("Not permitted for " + dept + " stays");
    }
}
```

`CurrentUser.java` (mirrors `SignatureController.currentUserId()` in the premature module; `HmsUserPrincipal` is a record with `userId()`, `username()`, `fullName()`):
```java
package com.albudoor.hms.bedstayforms.access;

import com.albudoor.hms.identity.infrastructure.security.HmsUserPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

public final class CurrentUser {
    private CurrentUser() {}

    public static UUID id() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a != null && a.getPrincipal() instanceof HmsUserPrincipal p) return p.userId();
        return null;
    }

    /** Display name for auto-attribution (nursing rows): full name, falling back to username. */
    public static String displayName() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a != null && a.getPrincipal() instanceof HmsUserPrincipal p) {
            return (p.fullName() != null && !p.fullName().isBlank()) ? p.fullName() : p.username();
        }
        return a == null ? null : a.getName();
    }
}
```

- [ ] **Step 3: Premature directory implementation**

`PrematureStayDirectory.java` — confirm getter names against `PrematureAdmission` (it snapshots `patientName`/`patientMrn` like `EmergencyCase`; check `getStatus()`, `getAdmittedAt()`):
```java
package com.albudoor.hms.premature.staydirectory;

import com.albudoor.hms.bedstayforms.directory.AgeText;
import com.albudoor.hms.bedstayforms.directory.StayDirectory;
import com.albudoor.hms.bedstayforms.directory.StayInfo;
import com.albudoor.hms.bedstayforms.domain.StayDepartment;
import com.albudoor.hms.patientregistry.infrastructure.PatientRepository;
import com.albudoor.hms.premature.domain.AdmissionStatus;
import com.albudoor.hms.premature.infrastructure.PrematureAdmissionRepository;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

@Component
public class PrematureStayDirectory implements StayDirectory {

    private final PrematureAdmissionRepository admissions;
    private final PatientRepository patients;

    public PrematureStayDirectory(PrematureAdmissionRepository admissions, PatientRepository patients) {
        this.admissions = admissions;
        this.patients = patients;
    }

    @Override
    public StayDepartment department() { return StayDepartment.PREMATURE; }

    @Override
    public Optional<StayInfo> find(UUID stayId) {
        return admissions.findById(stayId).map(a -> {
            String age = patients.findById(a.getPatientId())
                    .map(p -> AgeText.derive(p.getDateOfBirth(),
                            a.getAdmittedAt().atZone(ZoneOffset.UTC).toLocalDate()))
                    .orElse(null);
            boolean open = a.getStatus() != AdmissionStatus.CLOSED
                    && a.getStatus() != AdmissionStatus.CANCELLED;
            return new StayInfo(a.getPatientId(), a.getPatientName(), a.getPatientMrn(),
                    age, a.getAdmittedAt(), open);
        });
    }
}
```

- [ ] **Step 4: Emergency directory implementation**

`EmergencyStayDirectory.java` (same shape; `EmergencyCase` snapshots `patientName`/`patientMrn`; repository is in `com.albudoor.hms.emergency.infrastructure` — confirm its exact name, expected `EmergencyCaseRepository`):
```java
package com.albudoor.hms.emergency.staydirectory;

import com.albudoor.hms.bedstayforms.directory.AgeText;
import com.albudoor.hms.bedstayforms.directory.StayDirectory;
import com.albudoor.hms.bedstayforms.directory.StayInfo;
import com.albudoor.hms.bedstayforms.domain.StayDepartment;
import com.albudoor.hms.emergency.domain.EmergencyCaseStatus;
import com.albudoor.hms.emergency.infrastructure.EmergencyCaseRepository;
import com.albudoor.hms.patientregistry.infrastructure.PatientRepository;
import org.springframework.stereotype.Component;

import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

@Component
public class EmergencyStayDirectory implements StayDirectory {

    private final EmergencyCaseRepository cases;
    private final PatientRepository patients;

    public EmergencyStayDirectory(EmergencyCaseRepository cases, PatientRepository patients) {
        this.cases = cases;
        this.patients = patients;
    }

    @Override
    public StayDepartment department() { return StayDepartment.EMERGENCY; }

    @Override
    public Optional<StayInfo> find(UUID stayId) {
        return cases.findById(stayId).map(c -> {
            String age = patients.findById(c.getPatientId())
                    .map(p -> AgeText.derive(p.getDateOfBirth(),
                            c.getAdmittedAt().atZone(ZoneOffset.UTC).toLocalDate()))
                    .orElse(null);
            boolean open = c.getStatus() != EmergencyCaseStatus.CLOSED
                    && c.getStatus() != EmergencyCaseStatus.CANCELLED;
            return new StayInfo(c.getPatientId(), c.getPatientName(), c.getPatientMrn(),
                    age, c.getAdmittedAt(), open);
        });
    }
}
```
If `emergency/pom.xml` does not already depend on `patient-registry`, add that dependency block too.

- [ ] **Step 5: ArchUnit rule — dependencies must point INTO bed-stay-forms, never out of it**

Add to `backend/app/src/test/java/com/albudoor/hms/app/ArchitectureTest.java`:
```java
    @Test
    void bedStayFormsDoesNotDependOnDepartmentModules() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..bedstayforms..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("..premature..", "..emergency..");
        rule.check(CLASSES);
    }
```

- [ ] **Step 6: Compile + run ArchUnit**

Run: `cd backend && JAVA_HOME=/usr/lib/jvm/java-25-openjdk mvn -pl app -am install -DskipTests -q && JAVA_HOME=/usr/lib/jvm/java-25-openjdk mvn -pl app test -Dtest=ArchitectureTest -Dsurefire.failIfNoSpecifiedTests=false -q`
Expected: BUILD SUCCESS, all architecture rules green (including the new one).

- [ ] **Step 7: Commit**

```bash
git add backend/bed-stay-forms backend/premature backend/emergency backend/app/src/test/java/com/albudoor/hms/app/ArchitectureTest.java
git commit -m "feat(bed-stay-forms): StayDirectory port + premature/emergency impls, dept-scoped access checks"
```

---

### Task 4: Medical History slice (GET / PUT / signatures) + IT

**Files:**
- Create: `backend/bed-stay-forms/src/main/java/com/albudoor/hms/bedstayforms/api/{StayPrefillDto,SignatureView,MedicalHistoryDto,MedicalHistoryResponse}.java`
- Create: `backend/bed-stay-forms/src/main/java/com/albudoor/hms/bedstayforms/infrastructure/MedicalHistoryRepository.java`
- Create: `backend/bed-stay-forms/src/main/java/com/albudoor/hms/bedstayforms/medicalhistory/{UpsertMedicalHistoryCommand,MedicalHistoryHandler,MedicalHistoryController}.java`
- Test: `backend/app/src/test/java/com/albudoor/hms/app/bedstayforms/BedStayFormsIT.java`

- [ ] **Step 1: Write the failing IT (premature-side round-trip + signature)**

`BedStayFormsIT.java` — the `auth`/`post` helpers and the admit flow are copied from `PrematureCaseIT` (same file layout, seeded users `receptionist`/`premature`/`cashier`/`doctor`/`nurse`):
```java
package com.albudoor.hms.app.bedstayforms;

import com.albudoor.hms.app.IntegrationTest;
import com.albudoor.hms.cashier.domain.PaymentStatus;
import com.albudoor.hms.cashier.infrastructure.PaymentRepository;
import com.albudoor.hms.premature.domain.Bed;
import com.albudoor.hms.premature.infrastructure.BedRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BedStayFormsIT extends IntegrationTest {

    /** 1x1 transparent PNG. */
    static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    @Autowired TestRestTemplate rest;
    @Autowired BedRepository beds;
    @Autowired PaymentRepository payments;

    HttpHeaders auth(String user) {
        var login = rest.postForEntity("/api/auth/login", Map.of("username", user, "password", user), Map.class);
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setBearerAuth((String) login.getBody().get("token"));
        return h;
    }

    <T> T post(String path, Object body, String user, Class<T> type) {
        var r = rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body == null ? Map.of() : body, auth(user)), type);
        assertThat(r.getStatusCode().is2xxSuccessful()).as("POST %s -> %s : %s", path, r.getStatusCode(), r.getBody()).isTrue();
        return r.getBody();
    }

    <T> T put(String path, Object body, String user, Class<T> type) {
        var r = rest.exchange(path, HttpMethod.PUT, new HttpEntity<>(body, auth(user)), type);
        assertThat(r.getStatusCode().is2xxSuccessful()).as("PUT %s -> %s : %s", path, r.getStatusCode(), r.getBody()).isTrue();
        return r.getBody();
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> get(String path, String user) {
        var r = rest.exchange(path, HttpMethod.GET, new HttpEntity<>(auth(user)), Map.class);
        assertThat(r.getStatusCode().is2xxSuccessful()).as("GET %s -> %s : %s", path, r.getStatusCode(), r.getBody()).isTrue();
        return r.getBody();
    }

    /** Premature admission driven to UNDER_CARE — copied from PrematureCaseIT.admitUnderCare(). */
    @SuppressWarnings("unchecked")
    String admitUnderCare() {
        var patient = post("/api/patients", Map.of("fullName", "Baby BSF " + System.nanoTime(), "gender", "MALE",
                "dateOfBirth", "2026-05-01", "mobileNumber", "0773" + (System.nanoTime() % 10_000_000L), "vip", false),
                "receptionist", Map.class);
        var visit = post("/api/visits", Map.of("patientId", patient.get("id"), "visitType", "PREMATURE"), "receptionist", Map.class);
        String visitId = (String) visit.get("id");
        Bed bed = beds.save(Bed.create("BSF-" + System.nanoTime(), "IT"));
        var adm = post("/api/premature/admissions",
                Map.of("visitId", visitId, "bedId", bed.getId().toString(), "stayValue", 3, "stayUnit", "DAYS"),
                "premature", Map.class);
        var initial = payments.findAllByVisitIdOrderByCreatedAtDesc(UUID.fromString(visitId)).stream()
                .filter(p -> p.getStatus() == PaymentStatus.PENDING).findFirst().orElseThrow();
        post("/api/payments/" + initial.getId() + "/approve", Map.of("paymentMethod", "CASH"), "cashier", Map.class);
        return (String) adm.get("id");
    }

    String mhUrl(String stayId) { return "/api/bed-stays/PREMATURE/" + stayId + "/medical-history"; }

    @Test @SuppressWarnings("unchecked")
    void medical_history_upsert_get_roundtrip_with_prefill() {
        String stay = admitUnderCare();

        // GET before any save: prefill present, form null
        var before = get(mhUrl(stay), "premature");
        var prefill = (Map<String, Object>) before.get("prefill");
        assertThat(prefill.get("patientName")).asString().startsWith("Baby BSF");
        assertThat(prefill.get("patientMrn")).isNotNull();
        assertThat(prefill.get("admittedAt")).isNotNull();
        assertThat(before.get("form")).isNull();

        // Doctor saves the sheet
        put(mhUrl(stay), Map.ofEntries(
                Map.entry("weightKg", 3.2), Map.entry("heightCm", 49),
                Map.entry("doctorName", "Dr. House"),
                Map.entry("chiefComplaint", "Fever for 2 days"),
                Map.entry("presentIllnessHx", "Gradual onset"),
                Map.entry("psHx", "None"), Map.entry("pmHx", "Neonatal jaundice"),
                Map.entry("familyHx", "Diabetes (mother)"), Map.entry("allergicHx", "NKDA"),
                Map.entry("socialSmoker", "No"), Map.entry("socialAlcohol", "No"), Map.entry("socialSleep", "Normal"),
                Map.entry("drugHx", "None"), Map.entry("physicalExamination", "Chest clear")
        ), "doctor", Map.class);

        var after = get(mhUrl(stay), "premature");
        var form = (Map<String, Object>) after.get("form");
        assertThat(form.get("chiefComplaint")).isEqualTo("Fever for 2 days");
        assertThat(form.get("physicalExamination")).isEqualTo("Chest clear");
        var spec = (Map<String, Object>) form.get("specialistSignature");
        assertThat(spec.get("present")).isEqualTo(false);
    }

    @Test @SuppressWarnings("unchecked")
    void medical_history_signature_upload_and_stream() {
        String stay = admitUnderCare();

        HttpHeaders h = auth("doctor");
        h.setContentType(MediaType.MULTIPART_FORM_DATA);
        var filePart = new HttpHeaders();
        filePart.setContentType(MediaType.IMAGE_PNG);
        var form = new LinkedMultiValueMap<String, Object>();
        form.add("file", new HttpEntity<>(new ByteArrayResource(PNG) {
            @Override public String getFilename() { return "sig.png"; }
        }, filePart));
        form.add("signerName", "Dr. Specialist");
        var up = rest.exchange(mhUrl(stay) + "/signatures/SPECIALIST", HttpMethod.POST, new HttpEntity<>(form, h), Map.class);
        assertThat(up.getStatusCode().is2xxSuccessful()).as("upload: %s %s", up.getStatusCode(), up.getBody()).isTrue();

        var img = rest.exchange(mhUrl(stay) + "/signatures/SPECIALIST", HttpMethod.GET,
                new HttpEntity<>(auth("premature")), byte[].class);
        assertThat(img.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(img.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_PNG);
        assertThat(img.getBody()).isNotEmpty();

        var sheet = (Map<String, Object>) get(mhUrl(stay), "premature").get("form");
        var spec = (Map<String, Object>) sheet.get("specialistSignature");
        assertThat(spec.get("present")).isEqualTo(true);
        assertThat(spec.get("signerName")).isEqualTo("Dr. Specialist");
    }
}
```

- [ ] **Step 2: Run the IT to verify it fails**

Run: `cd backend && JAVA_HOME=/usr/lib/jvm/java-25-openjdk mvn -pl app -am verify -Dit.test=BedStayFormsIT -Dsurefire.failIfNoSpecifiedTests=false -q`
Expected: FAIL — 404/compile error (endpoints don't exist yet).

- [ ] **Step 3: DTOs in `api/`**

`StayPrefillDto.java`:
```java
package com.albudoor.hms.bedstayforms.api;

import com.albudoor.hms.bedstayforms.directory.StayInfo;

import java.time.Instant;
import java.util.UUID;

/** The auto fields the paper forms print: Pt. Name, Pt. Code (MRN), Age, DOA. Never stored. */
public record StayPrefillDto(UUID patientId, String patientName, String patientMrn,
                             String ageText, Instant admittedAt) {
    public static StayPrefillDto from(StayInfo i) {
        return new StayPrefillDto(i.patientId(), i.patientName(), i.patientMrn(), i.ageText(), i.admittedAt());
    }
}
```

`SignatureView.java`:
```java
package com.albudoor.hms.bedstayforms.api;

import com.albudoor.hms.bedstayforms.domain.FormSignature;

import java.time.Instant;

public record SignatureView(String signerName, Instant signedAt, boolean present) {
    public static SignatureView from(FormSignature s) {
        if (s == null) return new SignatureView(null, null, false);
        return new SignatureView(s.getSignerName(), s.getSignedAt(), s.present());
    }
}
```

`MedicalHistoryDto.java`:
```java
package com.albudoor.hms.bedstayforms.api;

import com.albudoor.hms.bedstayforms.domain.MedicalHistorySheet;
import com.albudoor.hms.bedstayforms.domain.MhSignatureSlot;

import java.math.BigDecimal;

public record MedicalHistoryDto(
        BigDecimal weightKg, BigDecimal heightCm, String doctorName,
        String chiefComplaint, String presentIllnessHx, String psHx, String pmHx,
        String familyHx, String allergicHx,
        String socialSmoker, String socialAlcohol, String socialSleep,
        String drugHx, String physicalExamination,
        SignatureView specialistSignature, SignatureView permanentSignature, SignatureView residentSignature
) {
    public static MedicalHistoryDto from(MedicalHistorySheet s) {
        return new MedicalHistoryDto(
                s.getWeightKg(), s.getHeightCm(), s.getDoctorName(),
                s.getChiefComplaint(), s.getPresentIllnessHx(), s.getPsHx(), s.getPmHx(),
                s.getFamilyHx(), s.getAllergicHx(),
                s.getSocialSmoker(), s.getSocialAlcohol(), s.getSocialSleep(),
                s.getDrugHx(), s.getPhysicalExamination(),
                SignatureView.from(s.signature(MhSignatureSlot.SPECIALIST)),
                SignatureView.from(s.signature(MhSignatureSlot.PERMANENT)),
                SignatureView.from(s.signature(MhSignatureSlot.RESIDENT)));
    }
}
```

`MedicalHistoryResponse.java`:
```java
package com.albudoor.hms.bedstayforms.api;

/** GET payload: prefill always present; form null until first save. */
public record MedicalHistoryResponse(StayPrefillDto prefill, MedicalHistoryDto form) {}
```

- [ ] **Step 4: Repository**

`MedicalHistoryRepository.java`:
```java
package com.albudoor.hms.bedstayforms.infrastructure;

import com.albudoor.hms.bedstayforms.domain.MedicalHistorySheet;
import com.albudoor.hms.bedstayforms.domain.StayDepartment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MedicalHistoryRepository extends JpaRepository<MedicalHistorySheet, UUID> {
    Optional<MedicalHistorySheet> findByDepartmentAndStayId(StayDepartment department, UUID stayId);
}
```

- [ ] **Step 5: Command, handler, controller**

`UpsertMedicalHistoryCommand.java`:
```java
package com.albudoor.hms.bedstayforms.medicalhistory;

import com.albudoor.hms.bedstayforms.domain.MedicalHistoryData;

import java.math.BigDecimal;

/** All fields optional — the paper sheet allows partial completion. */
public record UpsertMedicalHistoryCommand(
        BigDecimal weightKg, BigDecimal heightCm, String doctorName,
        String chiefComplaint, String presentIllnessHx, String psHx, String pmHx,
        String familyHx, String allergicHx,
        String socialSmoker, String socialAlcohol, String socialSleep,
        String drugHx, String physicalExamination
) {
    public MedicalHistoryData toData() {
        return new MedicalHistoryData(weightKg, heightCm, doctorName,
                chiefComplaint, presentIllnessHx, psHx, pmHx, familyHx, allergicHx,
                socialSmoker, socialAlcohol, socialSleep, drugHx, physicalExamination);
    }
}
```

`MedicalHistoryHandler.java`:
```java
package com.albudoor.hms.bedstayforms.medicalhistory;

import com.albudoor.hms.bedstayforms.api.MedicalHistoryDto;
import com.albudoor.hms.bedstayforms.api.MedicalHistoryResponse;
import com.albudoor.hms.bedstayforms.api.StayPrefillDto;
import com.albudoor.hms.bedstayforms.directory.StayDirectoryRegistry;
import com.albudoor.hms.bedstayforms.directory.StayInfo;
import com.albudoor.hms.bedstayforms.domain.MedicalHistorySheet;
import com.albudoor.hms.bedstayforms.domain.StayDepartment;
import com.albudoor.hms.bedstayforms.infrastructure.MedicalHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class MedicalHistoryHandler {

    private final MedicalHistoryRepository sheets;
    private final StayDirectoryRegistry stays;

    public MedicalHistoryHandler(MedicalHistoryRepository sheets, StayDirectoryRegistry stays) {
        this.sheets = sheets;
        this.stays = stays;
    }

    @Transactional(readOnly = true)
    public MedicalHistoryResponse get(StayDepartment dept, UUID stayId) {
        StayInfo info = stays.require(dept, stayId);
        MedicalHistoryDto form = sheets.findByDepartmentAndStayId(dept, stayId)
                .map(MedicalHistoryDto::from).orElse(null);
        return new MedicalHistoryResponse(StayPrefillDto.from(info), form);
    }

    @Transactional
    public MedicalHistoryDto upsert(StayDepartment dept, UUID stayId, UpsertMedicalHistoryCommand cmd) {
        stays.requireOpen(dept, stayId);
        MedicalHistorySheet sheet = sheets.findByDepartmentAndStayId(dept, stayId)
                .orElseGet(() -> MedicalHistorySheet.create(dept, stayId));
        sheet.update(cmd.toData());
        return MedicalHistoryDto.from(sheets.save(sheet));
    }
}
```

`MedicalHistoryController.java` (signature endpoints mirror the premature `SignatureController` FileStorage pattern):
```java
package com.albudoor.hms.bedstayforms.medicalhistory;

import com.albudoor.hms.bedstayforms.access.BedStayAccess;
import com.albudoor.hms.bedstayforms.access.CurrentUser;
import com.albudoor.hms.bedstayforms.api.MedicalHistoryDto;
import com.albudoor.hms.bedstayforms.api.MedicalHistoryResponse;
import com.albudoor.hms.bedstayforms.api.SignatureView;
import com.albudoor.hms.bedstayforms.directory.StayDirectoryRegistry;
import com.albudoor.hms.bedstayforms.domain.FormSignature;
import com.albudoor.hms.bedstayforms.domain.MedicalHistorySheet;
import com.albudoor.hms.bedstayforms.domain.MhSignatureSlot;
import com.albudoor.hms.bedstayforms.domain.StayDepartment;
import com.albudoor.hms.bedstayforms.infrastructure.MedicalHistoryRepository;
import com.albudoor.hms.platform.exception.DomainException;
import com.albudoor.hms.platform.exception.NotFoundException;
import com.albudoor.hms.platform.storage.FileStorage;
import jakarta.validation.Valid;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/bed-stays/{department}/{stayId}/medical-history")
public class MedicalHistoryController {

    private final MedicalHistoryHandler handler;
    private final BedStayAccess access;
    private final MedicalHistoryRepository sheets;
    private final StayDirectoryRegistry stays;
    private final FileStorage storage;

    public MedicalHistoryController(MedicalHistoryHandler handler, BedStayAccess access,
                                    MedicalHistoryRepository sheets, StayDirectoryRegistry stays,
                                    FileStorage storage) {
        this.handler = handler;
        this.access = access;
        this.sheets = sheets;
        this.stays = stays;
        this.storage = storage;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public MedicalHistoryResponse get(@PathVariable StayDepartment department, @PathVariable UUID stayId) {
        access.checkRead(department);
        return handler.get(department, stayId);
    }

    @PutMapping
    @PreAuthorize("isAuthenticated()")
    public MedicalHistoryDto upsert(@PathVariable StayDepartment department, @PathVariable UUID stayId,
                                    @Valid @RequestBody UpsertMedicalHistoryCommand cmd) {
        access.checkDoctorWrite(department);
        return handler.upsert(department, stayId, cmd);
    }

    @PostMapping("/signatures/{slot}")
    @PreAuthorize("isAuthenticated()")
    @Transactional
    public SignatureView sign(@PathVariable StayDepartment department, @PathVariable UUID stayId,
                              @PathVariable MhSignatureSlot slot,
                              @RequestParam("file") MultipartFile file,
                              @RequestParam(value = "signerName", required = false) String signerName)
            throws IOException {
        access.checkDoctorWrite(department);
        stays.requireOpen(department, stayId);
        if (file.isEmpty()) throw new DomainException("SIGNATURE_EMPTY", "Signature image is empty");
        String ct = file.getContentType();
        if (ct == null || !ct.startsWith("image/")) {
            throw new DomainException("SIGNATURE_NOT_IMAGE", "Signature must be an image");
        }
        MedicalHistorySheet sheet = sheets.findByDepartmentAndStayId(department, stayId)
                .orElseGet(() -> MedicalHistorySheet.create(department, stayId));
        String key;
        try (var in = file.getInputStream()) {
            key = storage.save(in, "signature.png", file.getSize());
        }
        sheet.applySignature(slot, key, signerName, CurrentUser.id());
        return SignatureView.from(sheets.save(sheet).signature(slot));
    }

    @GetMapping("/signatures/{slot}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Resource> signature(@PathVariable StayDepartment department, @PathVariable UUID stayId,
                                              @PathVariable MhSignatureSlot slot) throws IOException {
        access.checkRead(department);
        MedicalHistorySheet sheet = sheets.findByDepartmentAndStayId(department, stayId)
                .orElseThrow(() -> new NotFoundException("No medical history for stay " + stayId));
        FormSignature s = sheet.signature(slot);
        if (!s.present()) throw new NotFoundException("No signature in slot " + slot);
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG)
                .body(new InputStreamResource(storage.open(s.getImageKey())));
    }
}
```
Check `FileStorage.save(...)`'s exact signature against `com.albudoor.hms.platform.storage.FileStorage` (the premature `SignatureController` calls `storage.save(in, "signature.png", file.getSize())` and `storage.open(key)`) — keep identical usage.

- [ ] **Step 6: Run the IT to verify it passes**

Run: `cd backend && JAVA_HOME=/usr/lib/jvm/java-25-openjdk mvn -pl app -am verify -Dit.test=BedStayFormsIT -Dsurefire.failIfNoSpecifiedTests=false -q`
Expected: PASS (2 tests).

- [ ] **Step 7: Commit**

```bash
git add backend/bed-stay-forms backend/app/src/test/java/com/albudoor/hms/app/bedstayforms
git commit -m "feat(bed-stay-forms): medical history sheet slice (get/upsert/signatures) + IT"
```

---

### Task 5: Nursing Procedures slice (GET / POST) + IT

**Files:**
- Create: `backend/bed-stay-forms/src/main/java/com/albudoor/hms/bedstayforms/api/NursingProcedureDto.java`
- Create: `backend/bed-stay-forms/src/main/java/com/albudoor/hms/bedstayforms/infrastructure/NursingProcedureRepository.java`
- Create: `backend/bed-stay-forms/src/main/java/com/albudoor/hms/bedstayforms/nursingprocedures/{AddNursingProcedureCommand,NursingProceduresHandler,NursingProceduresController}.java`
- Test: add tests to `backend/app/src/test/java/com/albudoor/hms/app/bedstayforms/BedStayFormsIT.java`

- [ ] **Step 1: Write the failing IT tests (append to BedStayFormsIT)**

```java
    String npUrl(String stayId) { return "/api/bed-stays/PREMATURE/" + stayId + "/nursing-procedures"; }

    @Test @SuppressWarnings("unchecked")
    void nursing_rows_append_attribute_and_list_newest_first() {
        String stay = admitUnderCare();

        post(npUrl(stay), Map.of("procedureName", "Umbilical care",
                "performedAt", "2026-06-10T08:00:00Z", "note", "tolerated well"), "nurse", Map.class);
        post(npUrl(stay), Map.of("procedureName", "NG tube feeding",
                "performedAt", "2026-06-10T12:00:00Z"), "nurse", Map.class);

        var r = rest.exchange(npUrl(stay), HttpMethod.GET, new HttpEntity<>(auth("premature")), List.class);
        assertThat(r.getStatusCode().is2xxSuccessful()).isTrue();
        List<Map<String, Object>> rows = r.getBody();
        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).get("procedureName")).isEqualTo("NG tube feeding");   // newest first
        assertThat(rows.get(1).get("procedureName")).isEqualTo("Umbilical care");
        assertThat(rows.get(0).get("nurseName")).asString().isNotBlank();            // auto-attributed
        assertThat(rows.get(0).get("recordedAt")).isNotNull();
    }

    @Test
    void nursing_row_requires_procedure_name() {
        String stay = admitUnderCare();
        var r = rest.exchange(npUrl(stay), HttpMethod.POST,
                new HttpEntity<>(Map.of("performedAt", "2026-06-10T08:00:00Z"), auth("nurse")), String.class);
        assertThat(r.getStatusCode().value()).isEqualTo(400);   // bean validation
    }
```

- [ ] **Step 2: Run to verify failure**

Run: `cd backend && JAVA_HOME=/usr/lib/jvm/java-25-openjdk mvn -pl app -am verify -Dit.test=BedStayFormsIT -Dsurefire.failIfNoSpecifiedTests=false -q`
Expected: the two new tests FAIL with 404 (no endpoint); the Task-4 tests still pass.

- [ ] **Step 3: DTO + repository**

`NursingProcedureDto.java`:
```java
package com.albudoor.hms.bedstayforms.api;

import com.albudoor.hms.bedstayforms.domain.NursingProcedureEntry;

import java.time.Instant;
import java.util.UUID;

public record NursingProcedureDto(UUID id, String procedureName, Instant performedAt,
                                  String note, String nurseName, Instant recordedAt) {
    public static NursingProcedureDto from(NursingProcedureEntry e) {
        return new NursingProcedureDto(e.getId(), e.getProcedureName(), e.getPerformedAt(),
                e.getNote(), e.getNurseName(), e.getCreatedAt());
    }
}
```

`NursingProcedureRepository.java`:
```java
package com.albudoor.hms.bedstayforms.infrastructure;

import com.albudoor.hms.bedstayforms.domain.NursingProcedureEntry;
import com.albudoor.hms.bedstayforms.domain.StayDepartment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NursingProcedureRepository extends JpaRepository<NursingProcedureEntry, UUID> {
    List<NursingProcedureEntry> findAllByDepartmentAndStayIdOrderByPerformedAtDesc(StayDepartment department, UUID stayId);
}
```

- [ ] **Step 4: Command, handler, controller**

`AddNursingProcedureCommand.java`:
```java
package com.albudoor.hms.bedstayforms.nursingprocedures;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record AddNursingProcedureCommand(
        @NotBlank String procedureName,
        @NotNull Instant performedAt,
        String note
) {}
```

`NursingProceduresHandler.java`:
```java
package com.albudoor.hms.bedstayforms.nursingprocedures;

import com.albudoor.hms.bedstayforms.api.NursingProcedureDto;
import com.albudoor.hms.bedstayforms.directory.StayDirectoryRegistry;
import com.albudoor.hms.bedstayforms.domain.NursingProcedureEntry;
import com.albudoor.hms.bedstayforms.domain.StayDepartment;
import com.albudoor.hms.bedstayforms.infrastructure.NursingProcedureRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class NursingProceduresHandler {

    private final NursingProcedureRepository rows;
    private final StayDirectoryRegistry stays;

    public NursingProceduresHandler(NursingProcedureRepository rows, StayDirectoryRegistry stays) {
        this.rows = rows;
        this.stays = stays;
    }

    @Transactional(readOnly = true)
    public List<NursingProcedureDto> list(StayDepartment dept, UUID stayId) {
        stays.require(dept, stayId);
        return rows.findAllByDepartmentAndStayIdOrderByPerformedAtDesc(dept, stayId)
                .stream().map(NursingProcedureDto::from).toList();
    }

    @Transactional
    public NursingProcedureDto add(StayDepartment dept, UUID stayId, AddNursingProcedureCommand cmd,
                                   String nurseName, UUID recordedBy) {
        stays.requireOpen(dept, stayId);
        NursingProcedureEntry e = NursingProcedureEntry.record(dept, stayId,
                cmd.procedureName(), cmd.performedAt(), cmd.note(), nurseName, recordedBy);
        return NursingProcedureDto.from(rows.save(e));
    }
}
```

`NursingProceduresController.java`:
```java
package com.albudoor.hms.bedstayforms.nursingprocedures;

import com.albudoor.hms.bedstayforms.access.BedStayAccess;
import com.albudoor.hms.bedstayforms.access.CurrentUser;
import com.albudoor.hms.bedstayforms.api.NursingProcedureDto;
import com.albudoor.hms.bedstayforms.domain.StayDepartment;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/bed-stays/{department}/{stayId}/nursing-procedures")
public class NursingProceduresController {

    private final NursingProceduresHandler handler;
    private final BedStayAccess access;

    public NursingProceduresController(NursingProceduresHandler handler, BedStayAccess access) {
        this.handler = handler;
        this.access = access;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<NursingProcedureDto> list(@PathVariable StayDepartment department, @PathVariable UUID stayId) {
        access.checkRead(department);
        return handler.list(department, stayId);
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public NursingProcedureDto add(@PathVariable StayDepartment department, @PathVariable UUID stayId,
                                   @Valid @RequestBody AddNursingProcedureCommand cmd) {
        access.checkNurseWrite(department);
        return handler.add(department, stayId, cmd, CurrentUser.displayName(), CurrentUser.id());
    }
}
```

- [ ] **Step 5: Run the IT to verify it passes**

Run: `cd backend && JAVA_HOME=/usr/lib/jvm/java-25-openjdk mvn -pl app -am verify -Dit.test=BedStayFormsIT -Dsurefire.failIfNoSpecifiedTests=false -q`
Expected: PASS (4 tests).

- [ ] **Step 6: Commit**

```bash
git add backend/bed-stay-forms backend/app/src/test/java/com/albudoor/hms/app/bedstayforms
git commit -m "feat(bed-stay-forms): nursing procedures log (append-only, auto-attributed) + IT"
```

---

### Task 6: Treatment Chart slice (GET / PUT per date / signature) + IT

**Files:**
- Create: `backend/bed-stay-forms/src/main/java/com/albudoor/hms/bedstayforms/api/{TreatmentRowDto,TreatmentChartDto}.java`
- Create: `backend/bed-stay-forms/src/main/java/com/albudoor/hms/bedstayforms/infrastructure/TreatmentChartRepository.java`
- Create: `backend/bed-stay-forms/src/main/java/com/albudoor/hms/bedstayforms/treatmentcharts/{UpsertTreatmentChartCommand,TreatmentChartsHandler,TreatmentChartsController}.java`
- Test: add tests to `BedStayFormsIT.java`

- [ ] **Step 1: Write the failing IT tests (append to BedStayFormsIT)**

```java
    String tcUrl(String stayId) { return "/api/bed-stays/PREMATURE/" + stayId + "/treatment-charts"; }

    @Test @SuppressWarnings("unchecked")
    void treatment_chart_upsert_replaces_rows_per_date() {
        String stay = admitUnderCare();

        put(tcUrl(stay) + "/2026-06-10", Map.of("rows", List.of(
                Map.of("medicineName", "Ampicillin", "dose", "50mg/kg", "frequency", "q12h",
                        "timing", List.of("08", "", "", "", "", "20")),
                Map.of("medicineName", "Gentamicin", "dose", "4mg/kg", "frequency", "q24h",
                        "timing", List.of("10")))), "doctor", Map.class);

        // replace with a single row
        put(tcUrl(stay) + "/2026-06-10", Map.of("rows", List.of(
                Map.of("medicineName", "Caffeine citrate", "dose", "5mg/kg", "frequency", "q24h"))),
                "doctor", Map.class);

        var r = rest.exchange(tcUrl(stay), HttpMethod.GET, new HttpEntity<>(auth("premature")), List.class);
        assertThat(r.getStatusCode().is2xxSuccessful()).isTrue();
        List<Map<String, Object>> charts = r.getBody();
        assertThat(charts).hasSize(1);
        assertThat(charts.get(0).get("chartDate")).isEqualTo("2026-06-10");
        List<Map<String, Object>> rows = (List<Map<String, Object>>) charts.get(0).get("rows");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("medicineName")).isEqualTo("Caffeine citrate");
        List<Object> timing = (List<Object>) rows.get(0).get("timing");
        assertThat(timing).hasSize(6);
    }

    @Test @SuppressWarnings("unchecked")
    void treatment_chart_distinct_dates_are_distinct_charts() {
        String stay = admitUnderCare();
        put(tcUrl(stay) + "/2026-06-10", Map.of("rows", List.of(Map.of("medicineName", "A"))), "doctor", Map.class);
        put(tcUrl(stay) + "/2026-06-11", Map.of("rows", List.of(Map.of("medicineName", "B"))), "doctor", Map.class);
        var charts = rest.exchange(tcUrl(stay), HttpMethod.GET, new HttpEntity<>(auth("premature")), List.class).getBody();
        assertThat(charts).hasSize(2);
    }
```

- [ ] **Step 2: Run to verify failure**

Run: `cd backend && JAVA_HOME=/usr/lib/jvm/java-25-openjdk mvn -pl app -am verify -Dit.test=BedStayFormsIT -Dsurefire.failIfNoSpecifiedTests=false -q`
Expected: the two new tests FAIL with 404.

- [ ] **Step 3: DTOs + repository**

`TreatmentRowDto.java`:
```java
package com.albudoor.hms.bedstayforms.api;

import com.albudoor.hms.bedstayforms.domain.TreatmentRow;

import java.util.Arrays;
import java.util.List;

/** timing is always exactly 6 entries (AM/AM/PM/PM/PM/AM), nulls for empty slots. */
public record TreatmentRowDto(String medicineName, String dose, String frequency, List<String> timing) {
    public static TreatmentRowDto from(TreatmentRow r) {
        return new TreatmentRowDto(r.getMedicineName(), r.getDose(), r.getFrequency(),
                Arrays.asList(r.getTiming1(), r.getTiming2(), r.getTiming3(),
                        r.getTiming4(), r.getTiming5(), r.getTiming6()));
    }
}
```

`TreatmentChartDto.java`:
```java
package com.albudoor.hms.bedstayforms.api;

import com.albudoor.hms.bedstayforms.domain.TreatmentChart;

import java.time.LocalDate;
import java.util.List;

public record TreatmentChartDto(LocalDate chartDate, List<TreatmentRowDto> rows, SignatureView doctorSignature) {
    public static TreatmentChartDto from(TreatmentChart c) {
        return new TreatmentChartDto(c.getChartDate(),
                c.getRows().stream().map(TreatmentRowDto::from).toList(),
                SignatureView.from(c.getDoctorSignature()));
    }
}
```

`TreatmentChartRepository.java`:
```java
package com.albudoor.hms.bedstayforms.infrastructure;

import com.albudoor.hms.bedstayforms.domain.StayDepartment;
import com.albudoor.hms.bedstayforms.domain.TreatmentChart;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TreatmentChartRepository extends JpaRepository<TreatmentChart, UUID> {
    Optional<TreatmentChart> findByDepartmentAndStayIdAndChartDate(StayDepartment department, UUID stayId, LocalDate chartDate);
    List<TreatmentChart> findAllByDepartmentAndStayIdOrderByChartDateDesc(StayDepartment department, UUID stayId);
}
```

- [ ] **Step 4: Command, handler, controller**

`UpsertTreatmentChartCommand.java`:
```java
package com.albudoor.hms.bedstayforms.treatmentcharts;

import com.albudoor.hms.bedstayforms.domain.TreatmentRow;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UpsertTreatmentChartCommand(@Valid List<RowCommand> rows) {

    public record RowCommand(
            @NotBlank String medicineName,
            String dose,
            String frequency,
            @Size(max = 6) List<String> timing
    ) {
        public TreatmentRow toRow() {
            String[] t = new String[6];
            if (timing != null) {
                for (int i = 0; i < Math.min(6, timing.size()); i++) {
                    String v = timing.get(i);
                    t[i] = (v == null || v.isBlank()) ? null : v;
                }
            }
            return new TreatmentRow(medicineName, dose, frequency, t[0], t[1], t[2], t[3], t[4], t[5]);
        }
    }

    public List<TreatmentRow> toRows() {
        return rows == null ? List.of() : rows.stream().map(RowCommand::toRow).toList();
    }
}
```

`TreatmentChartsHandler.java`:
```java
package com.albudoor.hms.bedstayforms.treatmentcharts;

import com.albudoor.hms.bedstayforms.api.TreatmentChartDto;
import com.albudoor.hms.bedstayforms.directory.StayDirectoryRegistry;
import com.albudoor.hms.bedstayforms.domain.StayDepartment;
import com.albudoor.hms.bedstayforms.domain.TreatmentChart;
import com.albudoor.hms.bedstayforms.infrastructure.TreatmentChartRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class TreatmentChartsHandler {

    private final TreatmentChartRepository charts;
    private final StayDirectoryRegistry stays;

    public TreatmentChartsHandler(TreatmentChartRepository charts, StayDirectoryRegistry stays) {
        this.charts = charts;
        this.stays = stays;
    }

    @Transactional(readOnly = true)
    public List<TreatmentChartDto> list(StayDepartment dept, UUID stayId) {
        stays.require(dept, stayId);
        return charts.findAllByDepartmentAndStayIdOrderByChartDateDesc(dept, stayId)
                .stream().map(TreatmentChartDto::from).toList();
    }

    @Transactional
    public TreatmentChartDto upsert(StayDepartment dept, UUID stayId, LocalDate date,
                                    UpsertTreatmentChartCommand cmd) {
        stays.requireOpen(dept, stayId);
        TreatmentChart chart = charts.findByDepartmentAndStayIdAndChartDate(dept, stayId, date)
                .orElseGet(() -> TreatmentChart.create(dept, stayId, date));
        chart.replaceRows(cmd.toRows());
        return TreatmentChartDto.from(charts.save(chart));
    }
}
```

`TreatmentChartsController.java`:
```java
package com.albudoor.hms.bedstayforms.treatmentcharts;

import com.albudoor.hms.bedstayforms.access.BedStayAccess;
import com.albudoor.hms.bedstayforms.access.CurrentUser;
import com.albudoor.hms.bedstayforms.api.SignatureView;
import com.albudoor.hms.bedstayforms.api.TreatmentChartDto;
import com.albudoor.hms.bedstayforms.directory.StayDirectoryRegistry;
import com.albudoor.hms.bedstayforms.domain.StayDepartment;
import com.albudoor.hms.bedstayforms.domain.TreatmentChart;
import com.albudoor.hms.bedstayforms.infrastructure.TreatmentChartRepository;
import com.albudoor.hms.platform.exception.DomainException;
import com.albudoor.hms.platform.exception.NotFoundException;
import com.albudoor.hms.platform.storage.FileStorage;
import jakarta.validation.Valid;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/bed-stays/{department}/{stayId}/treatment-charts")
public class TreatmentChartsController {

    private final TreatmentChartsHandler handler;
    private final BedStayAccess access;
    private final TreatmentChartRepository charts;
    private final StayDirectoryRegistry stays;
    private final FileStorage storage;

    public TreatmentChartsController(TreatmentChartsHandler handler, BedStayAccess access,
                                     TreatmentChartRepository charts, StayDirectoryRegistry stays,
                                     FileStorage storage) {
        this.handler = handler;
        this.access = access;
        this.charts = charts;
        this.stays = stays;
        this.storage = storage;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<TreatmentChartDto> list(@PathVariable StayDepartment department, @PathVariable UUID stayId) {
        access.checkRead(department);
        return handler.list(department, stayId);
    }

    @PutMapping("/{date}")
    @PreAuthorize("isAuthenticated()")
    public TreatmentChartDto upsert(@PathVariable StayDepartment department, @PathVariable UUID stayId,
                                    @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                    @Valid @RequestBody UpsertTreatmentChartCommand cmd) {
        access.checkDoctorWrite(department);
        return handler.upsert(department, stayId, date, cmd);
    }

    @PostMapping("/{date}/signature")
    @PreAuthorize("isAuthenticated()")
    @Transactional
    public SignatureView sign(@PathVariable StayDepartment department, @PathVariable UUID stayId,
                              @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                              @RequestParam("file") MultipartFile file,
                              @RequestParam(value = "signerName", required = false) String signerName)
            throws IOException {
        access.checkDoctorWrite(department);
        stays.requireOpen(department, stayId);
        if (file.isEmpty()) throw new DomainException("SIGNATURE_EMPTY", "Signature image is empty");
        String ct = file.getContentType();
        if (ct == null || !ct.startsWith("image/")) {
            throw new DomainException("SIGNATURE_NOT_IMAGE", "Signature must be an image");
        }
        TreatmentChart chart = charts.findByDepartmentAndStayIdAndChartDate(department, stayId, date)
                .orElseThrow(() -> new NotFoundException("No treatment chart for " + date));
        String key;
        try (var in = file.getInputStream()) {
            key = storage.save(in, "signature.png", file.getSize());
        }
        chart.applyDoctorSignature(key, signerName, CurrentUser.id());
        return SignatureView.from(charts.save(chart).getDoctorSignature());
    }

    @GetMapping("/{date}/signature")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Resource> signature(@PathVariable StayDepartment department, @PathVariable UUID stayId,
                                              @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date)
            throws IOException {
        access.checkRead(department);
        TreatmentChart chart = charts.findByDepartmentAndStayIdAndChartDate(department, stayId, date)
                .orElseThrow(() -> new NotFoundException("No treatment chart for " + date));
        if (!chart.getDoctorSignature().present()) throw new NotFoundException("Chart is unsigned");
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG)
                .body(new InputStreamResource(storage.open(chart.getDoctorSignature().getImageKey())));
    }
}
```

- [ ] **Step 5: Run the IT to verify it passes**

Run: `cd backend && JAVA_HOME=/usr/lib/jvm/java-25-openjdk mvn -pl app -am verify -Dit.test=BedStayFormsIT -Dsurefire.failIfNoSpecifiedTests=false -q`
Expected: PASS (6 tests).

- [ ] **Step 6: Commit**

```bash
git add backend/bed-stay-forms backend/app/src/test/java/com/albudoor/hms/app/bedstayforms
git commit -m "feat(bed-stay-forms): per-date treatment charts with replaceable rows + doctor signature + IT"
```

---

### Task 7: Authorization & lifecycle ITs + emergency-side IT

**Files:**
- Test: `backend/app/src/test/java/com/albudoor/hms/app/bedstayforms/BedStayFormsAuthzIT.java`
- Test: `backend/app/src/test/java/com/albudoor/hms/app/bedstayforms/BedStayFormsEmergencyIT.java`

No production code is expected to change in this task — these ITs lock in behavior already built in Tasks 3–6. If any test fails, fix the production code, not the test.

- [ ] **Step 1: Write `BedStayFormsAuthzIT`**

Reuse the same `auth`/`post`/`admitUnderCare` helpers as `BedStayFormsIT` (copy them in; they are package-private per-class by convention in this codebase — `PrematureAuthzIT` does the same).

```java
package com.albudoor.hms.app.bedstayforms;

import com.albudoor.hms.app.IntegrationTest;
import com.albudoor.hms.cashier.domain.PaymentStatus;
import com.albudoor.hms.cashier.infrastructure.PaymentRepository;
import com.albudoor.hms.premature.domain.Bed;
import com.albudoor.hms.premature.infrastructure.BedRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BedStayFormsAuthzIT extends IntegrationTest {

    @Autowired TestRestTemplate rest;
    @Autowired BedRepository beds;
    @Autowired PaymentRepository payments;

    HttpHeaders auth(String user) {
        var login = rest.postForEntity("/api/auth/login", Map.of("username", user, "password", user), Map.class);
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setBearerAuth((String) login.getBody().get("token"));
        return h;
    }

    <T> T post(String path, Object body, String user, Class<T> type) {
        var r = rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body == null ? Map.of() : body, auth(user)), type);
        assertThat(r.getStatusCode().is2xxSuccessful()).as("POST %s -> %s : %s", path, r.getStatusCode(), r.getBody()).isTrue();
        return r.getBody();
    }

    /** Premature admission left AWAITING_ADMISSION_PAYMENT (no cashier approval). */
    @SuppressWarnings("unchecked")
    Map<String, Object> admitPending() {
        var patient = post("/api/patients", Map.of("fullName", "Baby AZ " + System.nanoTime(), "gender", "FEMALE",
                "dateOfBirth", "2026-05-15", "mobileNumber", "0774" + (System.nanoTime() % 10_000_000L), "vip", false),
                "receptionist", Map.class);
        var visit = post("/api/visits", Map.of("patientId", patient.get("id"), "visitType", "PREMATURE"), "receptionist", Map.class);
        Bed bed = beds.save(Bed.create("AZ-" + System.nanoTime(), "IT"));
        return post("/api/premature/admissions",
                Map.of("visitId", visit.get("id"), "bedId", bed.getId().toString(), "stayValue", 1, "stayUnit", "DAYS"),
                "premature", Map.class);
    }

    int statusOf(HttpMethod method, String path, Object body, String user) {
        var entity = body == null ? new HttpEntity<>(auth(user)) : new HttpEntity<>(body, auth(user));
        return rest.exchange(path, method, entity, String.class).getStatusCode().value();
    }

    static final Map<String, Object> MH_BODY = Map.of("chiefComplaint", "x");
    static final Map<String, Object> NP_BODY = Map.of("procedureName", "x", "performedAt", "2026-06-10T08:00:00Z");

    @Test
    void cross_department_staff_get_403() {
        String stay = (String) admitPending().get("id");
        String mh = "/api/bed-stays/PREMATURE/" + stay + "/medical-history";
        // emergency staff may not touch premature stays (read or write)
        assertThat(statusOf(HttpMethod.GET, mh, null, "emergency")).isEqualTo(403);
        assertThat(statusOf(HttpMethod.PUT, mh, MH_BODY, "emergency")).isEqualTo(403);
    }

    @Test
    void write_levels_follow_the_brd_actor_table() {
        String stay = (String) admitPending().get("id");
        String base = "/api/bed-stays/PREMATURE/" + stay;
        // nurse cannot write the medical history sheet
        assertThat(statusOf(HttpMethod.PUT, base + "/medical-history", MH_BODY, "nurse")).isEqualTo(403);
        // doctor cannot append nursing rows
        assertThat(statusOf(HttpMethod.POST, base + "/nursing-procedures", NP_BODY, "doctor")).isEqualTo(403);
        // but both can read
        assertThat(statusOf(HttpMethod.GET, base + "/medical-history", null, "nurse")).isEqualTo(200);
        assertThat(statusOf(HttpMethod.GET, base + "/nursing-procedures", null, "doctor")).isEqualTo(200);
        // premature staff can do both
        assertThat(statusOf(HttpMethod.PUT, base + "/medical-history", MH_BODY, "premature")).isEqualTo(200);
        assertThat(statusOf(HttpMethod.POST, base + "/nursing-procedures", NP_BODY, "premature")).isEqualTo(200);
    }

    @Test
    void unknown_stay_is_404() {
        String mh = "/api/bed-stays/PREMATURE/" + UUID.randomUUID() + "/medical-history";
        assertThat(statusOf(HttpMethod.GET, mh, null, "premature")).isEqualTo(404);
        assertThat(statusOf(HttpMethod.PUT, mh, MH_BODY, "doctor")).isEqualTo(404);
    }

    @Test
    @SuppressWarnings("unchecked")
    void closed_stay_rejects_writes_but_allows_reads() {
        // CANCELLED via initial-payment rejection is the shortest path to a closed stay
        var adm = admitPending();
        String stay = (String) adm.get("id");
        String visitId = (String) adm.get("visitId");
        var initial = payments.findAllByVisitIdOrderByCreatedAtDesc(UUID.fromString(visitId)).stream()
                .filter(p -> p.getStatus() == PaymentStatus.PENDING).findFirst().orElseThrow();
        post("/api/payments/" + initial.getId() + "/reject", Map.of("reason", "IT"), "cashier", Map.class);

        String base = "/api/bed-stays/PREMATURE/" + stay;
        // DomainException STAY_CLOSED -> 422 per GlobalExceptionHandler
        assertThat(statusOf(HttpMethod.PUT, base + "/medical-history", MH_BODY, "doctor")).isEqualTo(422);
        assertThat(statusOf(HttpMethod.POST, base + "/nursing-procedures", NP_BODY, "nurse")).isEqualTo(422);
        assertThat(statusOf(HttpMethod.PUT, base + "/treatment-charts/2026-06-10",
                Map.of("rows", java.util.List.of(Map.of("medicineName", "A"))), "doctor")).isEqualTo(422);
        assertThat(statusOf(HttpMethod.GET, base + "/medical-history", null, "premature")).isEqualTo(200);
    }
}
```
Note: confirm the payment-reject endpoint body against `DischargeFlowIT`/`PrematureCaseIT` usage (`/api/payments/{id}/reject`); adjust the body key if it differs.

- [ ] **Step 2: Write `BedStayFormsEmergencyIT`**

The emergency admit helper must be copied from `backend/app/src/test/java/com/albudoor/hms/app/emergency/EmergencyOrdersIT.java` (it knows the emergency-specific service-selection admit flow) — reuse its method that produces an UNDER_TREATMENT case, then:

```java
package com.albudoor.hms.app.bedstayforms;

import com.albudoor.hms.app.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Proves the shared forms work for EMERGENCY stays via EmergencyStayDirectory. */
class BedStayFormsEmergencyIT extends IntegrationTest {

    @Autowired TestRestTemplate rest;

    // ... auth(...) helper as in BedStayFormsIT, and the admit-under-treatment helper
    // copied verbatim from EmergencyOrdersIT (creates patient -> EMERGENCY visit ->
    // emergency bed -> case with serviceItemId -> approves initial payment).
    // It must return the emergency case id.

    @Test @SuppressWarnings("unchecked")
    void emergency_stay_supports_all_three_forms() {
        String stay = admitEmergencyUnderTreatment();
        String base = "/api/bed-stays/EMERGENCY/" + stay;

        // medical history: emergency staff write + prefill from EmergencyStayDirectory
        var put = rest.exchange(base + "/medical-history", HttpMethod.PUT,
                new HttpEntity<>(Map.of("chiefComplaint", "RTA"), auth("emergency")), Map.class);
        assertThat(put.getStatusCode().is2xxSuccessful()).as("%s", put.getBody()).isTrue();
        var got = rest.exchange(base + "/medical-history", HttpMethod.GET,
                new HttpEntity<>(auth("emergency")), Map.class).getBody();
        assertThat(((Map<String, Object>) got.get("prefill")).get("patientName")).isNotNull();
        assertThat(((Map<String, Object>) got.get("form")).get("chiefComplaint")).isEqualTo("RTA");

        // nursing row
        var np = rest.exchange(base + "/nursing-procedures", HttpMethod.POST,
                new HttpEntity<>(Map.of("procedureName", "Wound dressing", "performedAt", "2026-06-10T09:00:00Z"),
                        auth("nurse")), Map.class);
        assertThat(np.getStatusCode().is2xxSuccessful()).isTrue();

        // treatment chart
        var tc = rest.exchange(base + "/treatment-charts/2026-06-10", HttpMethod.PUT,
                new HttpEntity<>(Map.of("rows", List.of(Map.of("medicineName", "Tetanus toxoid"))),
                        auth("doctor")), Map.class);
        assertThat(tc.getStatusCode().is2xxSuccessful()).isTrue();

        // and premature staff get 403 on an emergency stay
        var denied = rest.exchange(base + "/medical-history", HttpMethod.GET,
                new HttpEntity<>(auth("premature")), String.class);
        assertThat(denied.getStatusCode().value()).isEqualTo(403);
    }
}
```

- [ ] **Step 3: Run both ITs**

Run: `cd backend && JAVA_HOME=/usr/lib/jvm/java-25-openjdk mvn -pl app -am verify -Dit.test='BedStayForms*IT' -Dsurefire.failIfNoSpecifiedTests=false -q`
Expected: PASS. If `cross_department_staff_get_403` fails with 200, the `BedStayAccess` check is wrong — fix it, never the assertion.

- [ ] **Step 4: Commit**

```bash
git add backend/app/src/test/java/com/albudoor/hms/app/bedstayforms
git commit -m "test(bed-stay-forms): authz matrix, closed-stay gating, 404s + emergency-side coverage"
```

---

### Task 8: Patient Case Form (P6) — premature backend + IT

**Files:**
- Create: `backend/premature/src/main/resources/db/migration/V028__premature_patient_case.sql`
- Create: `backend/premature/src/main/java/com/albudoor/hms/premature/domain/{PatientCaseData,PatientCaseForm}.java`
- Create: `backend/premature/src/main/java/com/albudoor/hms/premature/infrastructure/PatientCaseFormRepository.java`
- Create: `backend/premature/src/main/java/com/albudoor/hms/premature/api/PatientCaseFormResponse.java`
- Create: `backend/premature/src/main/java/com/albudoor/hms/premature/upsertcaseform/{UpsertCaseFormCommand,UpsertCaseFormHandler,UpsertCaseFormController}.java`
- Modify: `backend/premature/src/main/java/com/albudoor/hms/premature/api/PrematureCaseResponse.java`
- Modify: `backend/premature/src/main/java/com/albudoor/hms/premature/getcase/GetCaseHandler.java`
- Test: `backend/app/src/test/java/com/albudoor/hms/app/premature/PatientCaseFormIT.java`

- [ ] **Step 1: Write the failing IT**

`PatientCaseFormIT.java` (helpers copied from `PrematureCaseIT`):
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

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PatientCaseFormIT extends IntegrationTest {

    @Autowired TestRestTemplate rest;
    @Autowired BedRepository beds;
    @Autowired PaymentRepository payments;

    // auth(...), post(...) helpers copied from PrematureCaseIT
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

    @SuppressWarnings("unchecked")
    private String admitUnderCare() {
        var patient = post("/api/patients", Map.of("fullName", "Baby P6 " + System.nanoTime(), "gender", "FEMALE",
                "dateOfBirth", "2026-05-20", "mobileNumber", "0775" + (System.nanoTime() % 10_000_000L), "vip", false),
                "receptionist", Map.class);
        var visit = post("/api/visits", Map.of("patientId", patient.get("id"), "visitType", "PREMATURE"), "receptionist", Map.class);
        String visitId = (String) visit.get("id");
        Bed bed = beds.save(Bed.create("P6-" + System.nanoTime(), "IT"));
        var adm = post("/api/premature/admissions",
                Map.of("visitId", visitId, "bedId", bed.getId().toString(), "stayValue", 3, "stayUnit", "DAYS"),
                "premature", Map.class);
        var initial = payments.findAllByVisitIdOrderByCreatedAtDesc(UUID.fromString(visitId)).stream()
                .filter(p -> p.getStatus() == PaymentStatus.PENDING).findFirst().orElseThrow();
        post("/api/payments/" + initial.getId() + "/approve", Map.of("paymentMethod", "CASH"), "cashier", Map.class);
        return (String) adm.get("id");
    }

    @Test @SuppressWarnings("unchecked")
    void case_form_upsert_then_appears_in_case_response_with_prefill() {
        String adm = admitUnderCare();

        var put = rest.exchange("/api/premature/admissions/" + adm + "/case-form", HttpMethod.PUT,
                new HttpEntity<>(Map.ofEntries(
                        Map.entry("wardNumber", "W-3"),
                        Map.entry("nextOfKinAddress", "Basra, Al-Ashar"),
                        Map.entry("nextOfKinPhone", "07701234567"),
                        Map.entry("treatingSpecialist", "Dr. Salim"),
                        Map.entry("initialDiagnosis", "Prematurity, RDS"),
                        Map.entry("finalDiagnosis", "")
                ), auth("doctor")), Map.class);
        assertThat(put.getStatusCode().is2xxSuccessful()).as("%s", put.getBody()).isTrue();

        var caseBody = rest.exchange("/api/premature/admissions/" + adm + "/case", HttpMethod.GET,
                new HttpEntity<>(auth("premature")), Map.class).getBody();
        var caseForm = (Map<String, Object>) caseBody.get("caseForm");
        assertThat(caseForm.get("wardNumber")).isEqualTo("W-3");
        assertThat(caseForm.get("initialDiagnosis")).isEqualTo("Prematurity, RDS");
        var cfPrefill = (Map<String, Object>) caseBody.get("caseFilePrefill");
        assertThat(cfPrefill.get("gender")).isEqualTo("FEMALE");
        assertThat(cfPrefill.containsKey("motherName")).isTrue();   // null for this seed, key must exist
    }
}
```

- [ ] **Step 2: Run to verify failure**

Run: `cd backend && JAVA_HOME=/usr/lib/jvm/java-25-openjdk mvn -pl app -am verify -Dit.test=PatientCaseFormIT -Dsurefire.failIfNoSpecifiedTests=false -q`
Expected: FAIL (404 on `/case-form`).

- [ ] **Step 3: Migration `V028__premature_patient_case.sql`**

```sql
-- HMS-BRD-REC-005 P6 — Patient Case Form (ملف المريض الراقد), one per admission.
-- Registry fields (name, mother, age, sex, address) are prefilled read-only, never stored.
-- One illegible header label on the source scan is excluded pending client confirmation.

CREATE TABLE prem_patient_case (
    id                  UUID PRIMARY KEY,
    admission_id        UUID         NOT NULL,
    ward_number         VARCHAR(60),
    next_of_kin_address VARCHAR(500),
    next_of_kin_phone   VARCHAR(60),
    treating_specialist VARCHAR(200),
    initial_diagnosis   VARCHAR(2000),
    final_diagnosis     VARCHAR(2000),
    version             BIGINT       NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ  NOT NULL,
    created_by          VARCHAR(100),
    updated_at          TIMESTAMPTZ,
    updated_by          VARCHAR(100),
    CONSTRAINT uk_prem_patient_case_admission UNIQUE (admission_id),
    CONSTRAINT fk_prem_patient_case_admission FOREIGN KEY (admission_id) REFERENCES prem_admission(id)
);
```

- [ ] **Step 4: Domain + repository**

`PatientCaseData.java`:
```java
package com.albudoor.hms.premature.domain;

/** Editable Patient Case Form fields (BRD P6); all optional. */
public record PatientCaseData(
        String wardNumber, String nextOfKinAddress, String nextOfKinPhone,
        String treatingSpecialist, String initialDiagnosis, String finalDiagnosis
) {}
```

`PatientCaseForm.java`:
```java
package com.albudoor.hms.premature.domain;

import com.albudoor.hms.platform.domain.AggregateRoot;
import com.albudoor.hms.platform.exception.DomainException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** BRD REC-005 P6 — Patient Case Form (inpatient file), 1:1 with the admission. */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "prem_patient_case")
public class PatientCaseForm extends AggregateRoot {

    @Id
    private UUID id;
    @Column(name = "admission_id", nullable = false)
    private UUID admissionId;

    @Column(name = "ward_number", length = 60) private String wardNumber;
    @Column(name = "next_of_kin_address", length = 500) private String nextOfKinAddress;
    @Column(name = "next_of_kin_phone", length = 60) private String nextOfKinPhone;
    @Column(name = "treating_specialist", length = 200) private String treatingSpecialist;
    @Column(name = "initial_diagnosis", length = 2000) private String initialDiagnosis;
    @Column(name = "final_diagnosis", length = 2000) private String finalDiagnosis;

    public static PatientCaseForm create(UUID admissionId) {
        if (admissionId == null) throw new DomainException("ADMISSION_REQUIRED", "admission is required");
        PatientCaseForm f = new PatientCaseForm();
        f.id = UUID.randomUUID();
        f.admissionId = admissionId;
        return f;
    }

    public void update(PatientCaseData d) {
        this.wardNumber = d.wardNumber();
        this.nextOfKinAddress = d.nextOfKinAddress();
        this.nextOfKinPhone = d.nextOfKinPhone();
        this.treatingSpecialist = d.treatingSpecialist();
        this.initialDiagnosis = d.initialDiagnosis();
        this.finalDiagnosis = d.finalDiagnosis();
    }
}
```

`PatientCaseFormRepository.java`:
```java
package com.albudoor.hms.premature.infrastructure;

import com.albudoor.hms.premature.domain.PatientCaseForm;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PatientCaseFormRepository extends JpaRepository<PatientCaseForm, UUID> {
    Optional<PatientCaseForm> findByAdmissionId(UUID admissionId);
}
```

- [ ] **Step 5: API DTO + upsert slice**

`PatientCaseFormResponse.java`:
```java
package com.albudoor.hms.premature.api;

import com.albudoor.hms.premature.domain.PatientCaseForm;

public record PatientCaseFormResponse(
        String wardNumber, String nextOfKinAddress, String nextOfKinPhone,
        String treatingSpecialist, String initialDiagnosis, String finalDiagnosis
) {
    public static PatientCaseFormResponse from(PatientCaseForm f) {
        return new PatientCaseFormResponse(f.getWardNumber(), f.getNextOfKinAddress(), f.getNextOfKinPhone(),
                f.getTreatingSpecialist(), f.getInitialDiagnosis(), f.getFinalDiagnosis());
    }
}
```

`UpsertCaseFormCommand.java`:
```java
package com.albudoor.hms.premature.upsertcaseform;

import com.albudoor.hms.premature.domain.PatientCaseData;

public record UpsertCaseFormCommand(
        String wardNumber, String nextOfKinAddress, String nextOfKinPhone,
        String treatingSpecialist, String initialDiagnosis, String finalDiagnosis
) {
    public PatientCaseData toData() {
        return new PatientCaseData(wardNumber, nextOfKinAddress, nextOfKinPhone,
                treatingSpecialist, initialDiagnosis, finalDiagnosis);
    }
}
```

`UpsertCaseFormHandler.java`:
```java
package com.albudoor.hms.premature.upsertcaseform;

import com.albudoor.hms.platform.exception.DomainException;
import com.albudoor.hms.platform.exception.NotFoundException;
import com.albudoor.hms.premature.domain.AdmissionStatus;
import com.albudoor.hms.premature.domain.PatientCaseForm;
import com.albudoor.hms.premature.domain.PrematureAdmission;
import com.albudoor.hms.premature.infrastructure.PatientCaseFormRepository;
import com.albudoor.hms.premature.infrastructure.PrematureAdmissionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UpsertCaseFormHandler {

    private final PatientCaseFormRepository forms;
    private final PrematureAdmissionRepository admissions;

    public UpsertCaseFormHandler(PatientCaseFormRepository forms, PrematureAdmissionRepository admissions) {
        this.forms = forms;
        this.admissions = admissions;
    }

    @Transactional
    public PatientCaseForm handle(UUID admissionId, UpsertCaseFormCommand cmd) {
        PrematureAdmission adm = admissions.findById(admissionId)
                .orElseThrow(() -> new NotFoundException("Admission not found: " + admissionId));
        if (adm.getStatus() == AdmissionStatus.CLOSED || adm.getStatus() == AdmissionStatus.CANCELLED) {
            throw new DomainException("STAY_CLOSED", "The case is closed; the case form is read-only");
        }
        PatientCaseForm form = forms.findByAdmissionId(admissionId)
                .orElseGet(() -> PatientCaseForm.create(adm.getId()));
        form.update(cmd.toData());
        return forms.save(form);
    }
}
```

`UpsertCaseFormController.java`:
```java
package com.albudoor.hms.premature.upsertcaseform;

import com.albudoor.hms.premature.api.PatientCaseFormResponse;
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
public class UpsertCaseFormController {

    private final UpsertCaseFormHandler handler;

    public UpsertCaseFormController(UpsertCaseFormHandler handler) {
        this.handler = handler;
    }

    @PutMapping("/{id}/case-form")
    @PreAuthorize("hasAnyRole('PREMATURE_STAFF', 'DOCTOR', 'ADMIN')")
    public PatientCaseFormResponse upsert(@PathVariable UUID id, @Valid @RequestBody UpsertCaseFormCommand cmd) {
        return PatientCaseFormResponse.from(handler.handle(id, cmd));
    }
}
```

- [ ] **Step 6: Fold into the case response**

`PrematureCaseResponse.java` — append two fields (additive; existing constructor callers must be updated):
```java
public record PrematureCaseResponse(
        AdmissionResponse admission,
        PrematureFormResponse form,
        Prefill prefill,
        List<PrematureTourResponse> tours,
        PatientCaseFormResponse caseForm,
        CaseFilePrefill caseFilePrefill
) {
    public record Prefill(
            String ageText,
            BigDecimal birthWeightKg,
            Integer gestationalAgeWeeks, Integer gestationalAgeDays,
            BigDecimal lengthCm, BigDecimal ofcCm
    ) {}

    /** Registry fields the P6 paper form shows read-only (beyond name/MRN on AdmissionResponse). */
    public record CaseFilePrefill(String motherName, String gender) {}
}
```

`GetCaseHandler.handle(...)` — inject `PatientCaseFormRepository caseForms` (constructor) and extend the return:
```java
        PatientCaseFormResponse caseForm = caseForms.findByAdmissionId(admissionId)
                .map(PatientCaseFormResponse::from).orElse(null);
        Patient patient = patients.findById(adm.getPatientId()).orElse(null);
        InfantDetails details = patient == null ? null : patient.getInfantDetails();
        var caseFilePrefill = new PrematureCaseResponse.CaseFilePrefill(
                details == null ? null : details.getMotherName(),
                patient == null || patient.getGender() == null ? null : patient.getGender().name());
        return new PrematureCaseResponse(AdmissionResponse.from(adm), form, prefill(adm), tourList,
                caseForm, caseFilePrefill);
```
(The handler already loads the patient inside `prefill(adm)`; refactor so the patient is fetched once and passed to both — keep the existing `deriveAge` logic untouched.)

- [ ] **Step 7: Run the IT to verify it passes; re-run the premature suite**

Run: `cd backend && JAVA_HOME=/usr/lib/jvm/java-25-openjdk mvn -pl app -am verify -Dit.test='PatientCaseFormIT,PrematureCaseIT' -Dsurefire.failIfNoSpecifiedTests=false -q`
Expected: PASS — including the pre-existing `PrematureCaseIT` (its Map-based assertions tolerate the two new response fields).

- [ ] **Step 8: Commit**

```bash
git add backend/premature backend/app/src/test/java/com/albudoor/hms/app/premature/PatientCaseFormIT.java
git commit -m "feat(premature): Patient Case Form P6 (upsert + case-response fold-in) + IT"
```

---

### Task 9: Frontend foundation — SignaturePad relocation, forms API client, `extraTabs` on BedStayCasePage

**Files:**
- Move: `frontend/src/features/premature/SignaturePad.tsx` → `frontend/src/shared/ui/SignaturePad.tsx`
- Modify: `frontend/src/features/premature/PrematureCasePage.tsx` (import path only, this task)
- Create: `frontend/src/features/beds/case/forms/api.ts`
- Modify: `frontend/src/features/beds/case/BedStayCasePage.tsx`

- [ ] **Step 1: Relocate SignaturePad**

```bash
git mv frontend/src/features/premature/SignaturePad.tsx frontend/src/shared/ui/SignaturePad.tsx
```
In `PrematureCasePage.tsx` change `import { SignaturePad } from './SignaturePad';` → `import { SignaturePad } from '@/shared/ui/SignaturePad';`. Grep for any other importer: `grep -rn "from './SignaturePad'\|premature/SignaturePad" frontend/src` and fix all hits.

- [ ] **Step 2: Shared forms API client**

`frontend/src/features/beds/case/forms/api.ts`:
```ts
import { api } from '@/shared/api/client';

export type StayDepartment = 'PREMATURE' | 'EMERGENCY';
export type MhSlot = 'SPECIALIST' | 'PERMANENT' | 'RESIDENT';

export type StayPrefill = {
  patientId: string;
  patientName: string;
  patientMrn: string;
  ageText: string | null;
  admittedAt: string;
};

export type SignatureView = { signerName: string | null; signedAt: string | null; present: boolean };

export type MedicalHistory = {
  weightKg: number | null;
  heightCm: number | null;
  doctorName: string | null;
  chiefComplaint: string | null;
  presentIllnessHx: string | null;
  psHx: string | null;
  pmHx: string | null;
  familyHx: string | null;
  allergicHx: string | null;
  socialSmoker: string | null;
  socialAlcohol: string | null;
  socialSleep: string | null;
  drugHx: string | null;
  physicalExamination: string | null;
  specialistSignature: SignatureView;
  permanentSignature: SignatureView;
  residentSignature: SignatureView;
};

export type MedicalHistoryResponse = { prefill: StayPrefill; form: MedicalHistory | null };

export type NursingProcedure = {
  id: string;
  procedureName: string;
  performedAt: string;
  note: string | null;
  nurseName: string | null;
  recordedAt: string;
};

export type TreatmentRow = {
  medicineName: string;
  dose: string | null;
  frequency: string | null;
  timing: (string | null)[];
};

export type TreatmentChart = { chartDate: string; rows: TreatmentRow[]; doctorSignature: SignatureView };

const base = (dept: StayDepartment, stayId: string) => `/bed-stays/${dept}/${stayId}`;

export async function getMedicalHistory(dept: StayDepartment, stayId: string): Promise<MedicalHistoryResponse> {
  const res = await api.get(`${base(dept, stayId)}/medical-history`);
  return res.data;
}

export async function saveMedicalHistory(
  dept: StayDepartment, stayId: string, body: Record<string, unknown>,
): Promise<MedicalHistory> {
  const res = await api.put(`${base(dept, stayId)}/medical-history`, body);
  return res.data;
}

export async function uploadMhSignature(
  dept: StayDepartment, stayId: string, slot: MhSlot, blob: Blob, signerName?: string,
): Promise<SignatureView> {
  const fd = new FormData();
  fd.append('file', blob, 'signature.png');
  if (signerName) fd.append('signerName', signerName);
  const res = await api.post(`${base(dept, stayId)}/medical-history/signatures/${slot}`, fd);
  return res.data;
}

export async function listNursingProcedures(dept: StayDepartment, stayId: string): Promise<NursingProcedure[]> {
  const res = await api.get(`${base(dept, stayId)}/nursing-procedures`);
  return res.data;
}

export async function addNursingProcedure(
  dept: StayDepartment, stayId: string,
  body: { procedureName: string; performedAt: string; note?: string },
): Promise<NursingProcedure> {
  const res = await api.post(`${base(dept, stayId)}/nursing-procedures`, body);
  return res.data;
}

export async function listTreatmentCharts(dept: StayDepartment, stayId: string): Promise<TreatmentChart[]> {
  const res = await api.get(`${base(dept, stayId)}/treatment-charts`);
  return res.data;
}

export async function saveTreatmentChart(
  dept: StayDepartment, stayId: string, date: string,
  rows: { medicineName: string; dose?: string; frequency?: string; timing?: (string | null)[] }[],
): Promise<TreatmentChart> {
  const res = await api.put(`${base(dept, stayId)}/treatment-charts/${date}`, { rows });
  return res.data;
}

export async function uploadChartSignature(
  dept: StayDepartment, stayId: string, date: string, blob: Blob, signerName?: string,
): Promise<SignatureView> {
  const fd = new FormData();
  fd.append('file', blob, 'signature.png');
  if (signerName) fd.append('signerName', signerName);
  const res = await api.post(`${base(dept, stayId)}/treatment-charts/${date}/signature`, fd);
  return res.data;
}
```
Check how `PrematureCasePage`'s existing signature upload posts FormData through `api` (whether it sets the multipart header explicitly) and mirror it exactly.

- [ ] **Step 3: `extraTabs` prop on BedStayCasePage**

In `frontend/src/features/beds/case/BedStayCasePage.tsx`:

1. Loosen the tab-state type and export the extra-tab shape. Replace
```ts
type TabKey = 'overview' | 'LABORATORY' | 'RADIOLOGY' | 'ECO' | 'clinical' | 'billing' | 'timeline';
```
with
```ts
type BuiltinTabKey = 'overview' | 'LABORATORY' | 'RADIOLOGY' | 'ECO' | 'clinical' | 'billing' | 'timeline';
type TabKey = BuiltinTabKey | (string & {});

export type ExtraTab = { key: string; label: string; content: ReactNode };
```

2. Add the prop (in the destructured signature and its type):
```ts
export function BedStayCasePage({
  backTo, backLabel, view, orders, ordersLoading, statusLabel, canExtend, clinical, actions, extraTabs,
}: {
  // ...existing props...
  extraTabs?: ExtraTab[];
}) {
```

3. Insert the extra tabs between `clinical` and `billing` in the `tabs` array:
```ts
  const tabs: { key: TabKey; label: string; count?: number }[] = [
    { key: 'overview', label: t('caseView.tabs.overview') },
    { key: 'LABORATORY', label: t('caseView.tabs.laboratory'), count: ordersByTarget.LABORATORY.length },
    { key: 'RADIOLOGY', label: t('caseView.tabs.radiology'), count: ordersByTarget.RADIOLOGY.length },
    { key: 'ECO', label: t('caseView.tabs.eco'), count: ordersByTarget.ECO.length },
    { key: 'clinical', label: t('caseView.tabs.clinical') },
    ...(extraTabs ?? []).map((e) => ({ key: e.key as TabKey, label: e.label })),
    { key: 'billing', label: t('caseView.tabs.billing') },
    { key: 'timeline', label: t('caseView.tabs.timeline') },
  ];
```

4. In the tab-content switch/rendering section, before the fallback, render a matching extra tab:
```ts
  const activeExtra = (extraTabs ?? []).find((e) => e.key === tab);
  // ...inside the content area:
  {activeExtra ? activeExtra.content : /* existing per-tab rendering */}
```
Wire it so the existing builtin-tab rendering is untouched when `activeExtra` is undefined. The tab buttons already render `data-testid={'case-tab-' + key}` — verify and keep that for the new keys (e2e relies on `case-tab-history` etc.).

- [ ] **Step 4: Typecheck**

Run: `cd frontend && npx tsc -b 2>&1 | head -20` (or `npm run build` if that's the repo's check)
Expected: no errors.

- [ ] **Step 5: Commit**

```bash
git add frontend/src
git commit -m "feat(bed-stay-ui): shared clinical-forms api client, SignaturePad to shared/ui, extraTabs on case page"
```

---

### Task 10: The three shared tab components

**Files:**
- Create: `frontend/src/features/beds/case/forms/MedicalHistoryTab.tsx`
- Create: `frontend/src/features/beds/case/forms/NursingTab.tsx`
- Create: `frontend/src/features/beds/case/forms/TreatmentTab.tsx`

All three take `{ department, stayId, readOnly }`. Match the surrounding style: controlled inputs, Tailwind `ink-`/`brand-` classes, `t()` for all labels (keys added in Task 11), `data-testid` on every input/button the e2e touches.

- [ ] **Step 1: MedicalHistoryTab**

```tsx
import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import toast from 'react-hot-toast';
import { extractApiError } from '@/shared/api/client';
import { SignaturePad } from '@/shared/ui/SignaturePad';
import {
  getMedicalHistory, saveMedicalHistory, uploadMhSignature,
  type MedicalHistory, type MhSlot, type StayDepartment,
} from './api';

const TEXT_FIELDS = [
  'doctorName', 'chiefComplaint', 'presentIllnessHx', 'psHx', 'pmHx',
  'familyHx', 'allergicHx', 'socialSmoker', 'socialAlcohol', 'socialSleep',
  'drugHx', 'physicalExamination',
] as const;
const MULTILINE = new Set(['chiefComplaint', 'presentIllnessHx', 'psHx', 'pmHx',
  'familyHx', 'allergicHx', 'drugHx', 'physicalExamination']);
const SLOTS: MhSlot[] = ['SPECIALIST', 'PERMANENT', 'RESIDENT'];

type Draft = Record<string, string>;

function toDraft(form: MedicalHistory | null): Draft {
  const d: Draft = { weightKg: '', heightCm: '' };
  for (const f of TEXT_FIELDS) d[f] = (form?.[f] as string | null) ?? '';
  if (form?.weightKg != null) d.weightKg = String(form.weightKg);
  if (form?.heightCm != null) d.heightCm = String(form.heightCm);
  return d;
}

export function MedicalHistoryTab({ department, stayId, readOnly }: {
  department: StayDepartment; stayId: string; readOnly: boolean;
}) {
  const { t } = useTranslation();
  const qc = useQueryClient();
  const queryKey = ['stay-mh', department, stayId];
  const { data, isLoading } = useQuery({ queryKey, queryFn: () => getMedicalHistory(department, stayId) });
  const [draft, setDraft] = useState<Draft>(toDraft(null));
  useEffect(() => { if (data) setDraft(toDraft(data.form)); }, [data]);

  const save = useMutation({
    mutationFn: () => saveMedicalHistory(department, stayId, {
      ...Object.fromEntries(TEXT_FIELDS.map((f) => [f, draft[f] || null])),
      weightKg: draft.weightKg ? Number(draft.weightKg) : null,
      heightCm: draft.heightCm ? Number(draft.heightCm) : null,
    }),
    onSuccess: () => { toast.success(t('caseView.forms.saved')); qc.invalidateQueries({ queryKey }); },
    onError: (e) => toast.error(extractApiError(e)?.message ?? t('caseView.error')),
  });

  const sign = useMutation({
    mutationFn: ({ slot, blob, name }: { slot: MhSlot; blob: Blob; name?: string }) =>
      uploadMhSignature(department, stayId, slot, blob, name),
    onSuccess: () => { toast.success(t('caseView.forms.signed')); qc.invalidateQueries({ queryKey }); },
    onError: (e) => toast.error(extractApiError(e)?.message ?? t('caseView.error')),
  });

  if (isLoading || !data) return <div className="p-4 text-sm text-ink-500">{t('common.loading')}</div>;
  const set = (k: string) => (e: { target: { value: string } }) => setDraft((d) => ({ ...d, [k]: e.target.value }));
  const sigOf = (slot: MhSlot) =>
    slot === 'SPECIALIST' ? data.form?.specialistSignature
      : slot === 'PERMANENT' ? data.form?.permanentSignature : data.form?.residentSignature;

  return (
    <div className="space-y-4" data-testid="mh-tab">
      <dl className="grid grid-cols-2 gap-2 rounded-md border border-ink-100 bg-ink-50/50 p-3 text-sm md:grid-cols-4">
        <div><dt className="text-ink-500">{t('caseView.forms.ptName')}</dt><dd>{data.prefill.patientName}</dd></div>
        <div><dt className="text-ink-500">{t('caseView.forms.ptCode')}</dt><dd>{data.prefill.patientMrn}</dd></div>
        <div><dt className="text-ink-500">{t('caseView.forms.age')}</dt><dd>{data.prefill.ageText ?? '—'}</dd></div>
        <div><dt className="text-ink-500">{t('caseView.forms.doa')}</dt>
          <dd>{new Date(data.prefill.admittedAt).toLocaleDateString()}</dd></div>
      </dl>

      <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
        <label className="text-sm">
          <span className="text-ink-600">{t('caseView.forms.mh.weightKg')}</span>
          <input data-testid="mh-weightKg" type="number" step="0.01" value={draft.weightKg} onChange={set('weightKg')}
            disabled={readOnly} className="mt-1 w-full rounded-md border border-ink-200 px-2 py-1.5" />
        </label>
        <label className="text-sm">
          <span className="text-ink-600">{t('caseView.forms.mh.heightCm')}</span>
          <input data-testid="mh-heightCm" type="number" step="0.1" value={draft.heightCm} onChange={set('heightCm')}
            disabled={readOnly} className="mt-1 w-full rounded-md border border-ink-200 px-2 py-1.5" />
        </label>
        {TEXT_FIELDS.map((f) => (
          <label key={f} className={MULTILINE.has(f) ? 'text-sm md:col-span-2' : 'text-sm'}>
            <span className="text-ink-600">{t(`caseView.forms.mh.${f}`)}</span>
            {MULTILINE.has(f) ? (
              <textarea data-testid={`mh-${f}`} value={draft[f]} onChange={set(f)} disabled={readOnly}
                rows={2} className="mt-1 w-full rounded-md border border-ink-200 px-2 py-1.5" />
            ) : (
              <input data-testid={`mh-${f}`} value={draft[f]} onChange={set(f)} disabled={readOnly}
                className="mt-1 w-full rounded-md border border-ink-200 px-2 py-1.5" />
            )}
          </label>
        ))}
      </div>

      {!readOnly && (
        <button data-testid="mh-save" onClick={() => save.mutate()} disabled={save.isPending}
          className="rounded-md bg-brand-600 px-3 py-2 text-sm font-medium text-white hover:bg-brand-700 disabled:opacity-50">
          {t('caseView.forms.save')}
        </button>
      )}

      <div className="grid grid-cols-1 gap-3 md:grid-cols-3">
        {SLOTS.map((slot) => {
          const sig = sigOf(slot);
          return (
            <div key={slot} className="rounded-md border border-ink-100 p-3">
              <div className="text-sm font-medium text-ink-700">{t(`caseView.forms.mh.sign.${slot}`)}</div>
              {sig?.present ? (
                <div className="mt-1 text-xs text-ink-500" data-testid={`mh-signed-${slot}`}>
                  {sig.signerName ?? '—'} · {sig.signedAt ? new Date(sig.signedAt).toLocaleString() : ''}
                </div>
              ) : readOnly ? (
                <div className="mt-1 text-xs text-ink-400">—</div>
              ) : (
                <SignaturePad onSave={(blob, name) => sign.mutate({ slot, blob, name })} testId={`mh-pad-${slot}`} />
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}
```
**Check `SignaturePad`'s actual props** (open `frontend/src/shared/ui/SignaturePad.tsx` after the Task-9 move — the premature `FormTab` shows the call shape; it may expose e.g. `onUpload(blob)` + a separate signer-name input, and a per-slot `data-testid` like `canvas-RESIDENT`). Adapt the two call sites in this task to the real props; the rest of the component stands.

- [ ] **Step 2: NursingTab**

```tsx
import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import toast from 'react-hot-toast';
import { extractApiError } from '@/shared/api/client';
import { addNursingProcedure, listNursingProcedures, type StayDepartment } from './api';

export function NursingTab({ department, stayId, readOnly }: {
  department: StayDepartment; stayId: string; readOnly: boolean;
}) {
  const { t } = useTranslation();
  const qc = useQueryClient();
  const queryKey = ['stay-nursing', department, stayId];
  const { data: rows, isLoading } = useQuery({ queryKey, queryFn: () => listNursingProcedures(department, stayId) });

  const [procedureName, setProcedureName] = useState('');
  const [performedAt, setPerformedAt] = useState(() => new Date().toISOString().slice(0, 16));
  const [note, setNote] = useState('');

  const add = useMutation({
    mutationFn: () => addNursingProcedure(department, stayId, {
      procedureName,
      performedAt: new Date(performedAt).toISOString(),
      note: note || undefined,
    }),
    onSuccess: () => {
      toast.success(t('caseView.forms.saved'));
      setProcedureName(''); setNote('');
      qc.invalidateQueries({ queryKey });
    },
    onError: (e) => toast.error(extractApiError(e)?.message ?? t('caseView.error')),
  });

  if (isLoading) return <div className="p-4 text-sm text-ink-500">{t('common.loading')}</div>;

  return (
    <div className="space-y-4" data-testid="nursing-tab">
      {!readOnly && (
        <div className="flex flex-wrap items-end gap-2 rounded-md border border-ink-100 p-3">
          <label className="text-sm">
            <span className="text-ink-600">{t('caseView.forms.nursing.procedureName')}</span>
            <input data-testid="nursing-procedureName" value={procedureName}
              onChange={(e) => setProcedureName(e.target.value)}
              className="mt-1 w-56 rounded-md border border-ink-200 px-2 py-1.5" />
          </label>
          <label className="text-sm">
            <span className="text-ink-600">{t('caseView.forms.nursing.performedAt')}</span>
            <input data-testid="nursing-performedAt" type="datetime-local" value={performedAt}
              onChange={(e) => setPerformedAt(e.target.value)}
              className="mt-1 rounded-md border border-ink-200 px-2 py-1.5" />
          </label>
          <label className="flex-1 text-sm">
            <span className="text-ink-600">{t('caseView.forms.nursing.note')}</span>
            <input data-testid="nursing-note" value={note} onChange={(e) => setNote(e.target.value)}
              className="mt-1 w-full rounded-md border border-ink-200 px-2 py-1.5" />
          </label>
          <button data-testid="nursing-add" onClick={() => add.mutate()}
            disabled={add.isPending || !procedureName.trim()}
            className="rounded-md bg-brand-600 px-3 py-2 text-sm font-medium text-white hover:bg-brand-700 disabled:opacity-50">
            {t('caseView.forms.nursing.add')}
          </button>
        </div>
      )}

      <table className="w-full text-sm">
        <thead>
          <tr className="border-b border-ink-100 text-start text-xs uppercase text-ink-500">
            <th className="px-2 py-1.5 text-start">{t('caseView.forms.nursing.procedureName')}</th>
            <th className="px-2 py-1.5 text-start">{t('caseView.forms.nursing.nurse')}</th>
            <th className="px-2 py-1.5 text-start">{t('caseView.forms.nursing.performedAt')}</th>
            <th className="px-2 py-1.5 text-start">{t('caseView.forms.nursing.note')}</th>
          </tr>
        </thead>
        <tbody data-testid="nursing-rows">
          {(rows ?? []).map((r) => (
            <tr key={r.id} className="border-b border-ink-50">
              <td className="px-2 py-1.5">{r.procedureName}</td>
              <td className="px-2 py-1.5">{r.nurseName ?? '—'}</td>
              <td className="px-2 py-1.5">{new Date(r.performedAt).toLocaleString()}</td>
              <td className="px-2 py-1.5">{r.note ?? '—'}</td>
            </tr>
          ))}
          {(rows ?? []).length === 0 && (
            <tr><td colSpan={4} className="px-2 py-4 text-center text-ink-400">{t('caseView.forms.nursing.empty')}</td></tr>
          )}
        </tbody>
      </table>
    </div>
  );
}
```

- [ ] **Step 3: TreatmentTab**

```tsx
import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import toast from 'react-hot-toast';
import { extractApiError } from '@/shared/api/client';
import { listTreatmentCharts, saveTreatmentChart, type StayDepartment, type TreatmentChart } from './api';

type DraftRow = { medicineName: string; dose: string; frequency: string; timing: string[] };

const emptyRow = (): DraftRow => ({ medicineName: '', dose: '', frequency: '', timing: ['', '', '', '', '', ''] });
const TIMING_HEAD = ['AM', 'AM', 'PM', 'PM', 'PM', 'AM'];

function toDraft(chart: TreatmentChart | undefined): DraftRow[] {
  if (!chart || chart.rows.length === 0) return [emptyRow()];
  return chart.rows.map((r) => ({
    medicineName: r.medicineName,
    dose: r.dose ?? '',
    frequency: r.frequency ?? '',
    timing: Array.from({ length: 6 }, (_, i) => r.timing[i] ?? ''),
  }));
}

export function TreatmentTab({ department, stayId, readOnly }: {
  department: StayDepartment; stayId: string; readOnly: boolean;
}) {
  const { t } = useTranslation();
  const qc = useQueryClient();
  const queryKey = ['stay-charts', department, stayId];
  const { data: charts, isLoading } = useQuery({ queryKey, queryFn: () => listTreatmentCharts(department, stayId) });

  const [date, setDate] = useState(() => new Date().toISOString().slice(0, 10));
  const chart = (charts ?? []).find((c) => c.chartDate === date);
  const [rows, setRows] = useState<DraftRow[]>(toDraft(undefined));
  useEffect(() => { setRows(toDraft(chart)); }, [date, charts]);   // eslint-disable-line react-hooks/exhaustive-deps

  const save = useMutation({
    mutationFn: () => saveTreatmentChart(department, stayId, date,
      rows.filter((r) => r.medicineName.trim()).map((r) => ({
        medicineName: r.medicineName.trim(),
        dose: r.dose || undefined,
        frequency: r.frequency || undefined,
        timing: r.timing.map((v) => v || null),
      }))),
    onSuccess: () => { toast.success(t('caseView.forms.saved')); qc.invalidateQueries({ queryKey }); },
    onError: (e) => toast.error(extractApiError(e)?.message ?? t('caseView.error')),
  });

  if (isLoading) return <div className="p-4 text-sm text-ink-500">{t('common.loading')}</div>;
  const setRow = (i: number, patch: Partial<DraftRow>) =>
    setRows((rs) => rs.map((r, j) => (j === i ? { ...r, ...patch } : r)));
  const setTiming = (i: number, k: number, v: string) =>
    setRows((rs) => rs.map((r, j) => (j === i ? { ...r, timing: r.timing.map((x, m) => (m === k ? v : x)) } : r)));

  return (
    <div className="space-y-3" data-testid="treatment-tab">
      <div className="flex items-center gap-3">
        <label className="text-sm">
          <span className="text-ink-600">{t('caseView.forms.treatment.date')}</span>
          <input data-testid="tc-date" type="date" value={date} onChange={(e) => setDate(e.target.value)}
            className="ms-2 rounded-md border border-ink-200 px-2 py-1.5" />
        </label>
        {chart?.doctorSignature.present && (
          <span className="text-xs text-ink-500">
            {t('caseView.forms.treatment.signedBy')} {chart.doctorSignature.signerName ?? '—'}
          </span>
        )}
      </div>

      <table className="w-full text-sm">
        <thead>
          <tr className="border-b border-ink-100 text-xs uppercase text-ink-500">
            <th className="px-1 py-1.5 text-start">#</th>
            <th className="px-1 py-1.5 text-start">{t('caseView.forms.treatment.medicine')}</th>
            <th className="px-1 py-1.5 text-start">{t('caseView.forms.treatment.dose')}</th>
            <th className="px-1 py-1.5 text-start">{t('caseView.forms.treatment.freq')}</th>
            {TIMING_HEAD.map((h, i) => <th key={i} className="px-1 py-1.5">{h}</th>)}
          </tr>
        </thead>
        <tbody>
          {rows.map((r, i) => (
            <tr key={i} className="border-b border-ink-50">
              <td className="px-1 py-1 text-ink-400">{i + 1}</td>
              <td className="px-1 py-1">
                <input data-testid={`tc-row-${i}-medicine`} value={r.medicineName} disabled={readOnly}
                  onChange={(e) => setRow(i, { medicineName: e.target.value })}
                  className="w-full rounded-md border border-ink-200 px-2 py-1" />
              </td>
              <td className="px-1 py-1">
                <input data-testid={`tc-row-${i}-dose`} value={r.dose} disabled={readOnly}
                  onChange={(e) => setRow(i, { dose: e.target.value })}
                  className="w-24 rounded-md border border-ink-200 px-2 py-1" />
              </td>
              <td className="px-1 py-1">
                <input data-testid={`tc-row-${i}-freq`} value={r.frequency} disabled={readOnly}
                  onChange={(e) => setRow(i, { frequency: e.target.value })}
                  className="w-20 rounded-md border border-ink-200 px-2 py-1" />
              </td>
              {r.timing.map((v, k) => (
                <td key={k} className="px-0.5 py-1">
                  <input value={v} disabled={readOnly} onChange={(e) => setTiming(i, k, e.target.value)}
                    className="w-12 rounded-md border border-ink-200 px-1 py-1 text-center" />
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>

      {!readOnly && (
        <div className="flex gap-2">
          <button data-testid="tc-add-row" onClick={() => setRows((rs) => [...rs, emptyRow()])}
            className="rounded-md border border-ink-200 px-3 py-1.5 text-sm hover:bg-ink-50">
            {t('caseView.forms.treatment.addRow')}
          </button>
          <button data-testid="tc-save" onClick={() => save.mutate()} disabled={save.isPending}
            className="rounded-md bg-brand-600 px-3 py-1.5 text-sm font-medium text-white hover:bg-brand-700 disabled:opacity-50">
            {t('caseView.forms.save')}
          </button>
        </div>
      )}
    </div>
  );
}
```
(Doctor chart signature upload via `uploadChartSignature` can reuse the same SignaturePad block as MedicalHistoryTab; add it under the table guarded by `!readOnly && chart` — same adaptation note as Step 1.)

- [ ] **Step 4: Typecheck**

Run: `cd frontend && npx tsc -b 2>&1 | head -20`
Expected: only "missing i18n keys" is NOT a tsc concern — there must be zero type errors. (i18n keys land in Task 11.)

- [ ] **Step 5: Commit**

```bash
git add frontend/src/features/beds/case/forms
git commit -m "feat(bed-stay-ui): MedicalHistory, Nursing and Treatment tab components"
```

---

### Task 11: Wire the tabs into both case pages + i18n

**Files:**
- Modify: `frontend/src/features/premature/PrematureCasePage.tsx`
- Modify: `frontend/src/features/emergency/EmergencyCasePage.tsx`
- Modify: `frontend/src/shared/i18n/locales/en.ts`, `frontend/src/shared/i18n/locales/ar.ts`

- [ ] **Step 1: PrematureCasePage**

Add imports:
```tsx
import { MedicalHistoryTab } from '@/features/beds/case/forms/MedicalHistoryTab';
import { NursingTab } from '@/features/beds/case/forms/NursingTab';
import { TreatmentTab } from '@/features/beds/case/forms/TreatmentTab';
```
Inside the component, before the `return`:
```tsx
  const readOnly = a.status === 'CLOSED' || a.status === 'CANCELLED';
  const extraTabs = [
    { key: 'history', label: t('caseView.tabs.history'),
      content: <MedicalHistoryTab department="PREMATURE" stayId={id!} readOnly={readOnly} /> },
    { key: 'nursing', label: t('caseView.tabs.nursing'),
      content: <NursingTab department="PREMATURE" stayId={id!} readOnly={readOnly} /> },
    { key: 'treatment', label: t('caseView.tabs.treatment'),
      content: <TreatmentTab department="PREMATURE" stayId={id!} readOnly={readOnly} /> },
  ];
```
And pass `extraTabs={extraTabs}` to `<BedStayCasePage …>`.

- [ ] **Step 2: EmergencyCasePage**

Identical wiring with `department="EMERGENCY"` (its case view exposes the same `status` strings `CLOSED`/`CANCELLED`; the stay id is the emergency case id from `useParams`).

- [ ] **Step 3: i18n keys**

In `en.ts`, inside the existing `caseView.tabs` object add:
```ts
    history: 'History',
    nursing: 'Nursing',
    treatment: 'Treatment',
    caseFile: 'Case file',
```
And add a sibling `forms` object inside `caseView`:
```ts
  forms: {
    save: 'Save',
    saved: 'Saved',
    signed: 'Signature saved',
    ptName: 'Pt. Name',
    ptCode: 'Pt. Code',
    age: 'Age',
    doa: 'DOA',
    mh: {
      weightKg: 'Weight (kg)',
      heightCm: 'Height (cm)',
      doctorName: 'Doctor name',
      chiefComplaint: 'Chief complaint & duration',
      presentIllnessHx: 'Hx of present illness',
      psHx: 'PSHx',
      pmHx: 'PMHx',
      familyHx: 'Family Hx',
      allergicHx: 'Allergic Hx',
      socialSmoker: 'Social Hx — smoker',
      socialAlcohol: 'Social Hx — alcohol',
      socialSleep: 'Social Hx — sleep',
      drugHx: 'Drug Hx',
      physicalExamination: 'Physical examination',
      sign: { SPECIALIST: 'Specialist doctor', PERMANENT: 'Permanent doctor', RESIDENT: 'Resident doctor' },
    },
    nursing: {
      procedureName: 'Procedure',
      nurse: 'Nurse',
      performedAt: 'Time',
      note: 'Note',
      add: 'Add procedure',
      empty: 'No procedures recorded yet',
    },
    treatment: {
      date: 'Chart date',
      medicine: 'Name of medicine',
      dose: 'Dose',
      freq: 'Freq.',
      addRow: 'Add medicine',
      signedBy: 'Signed by',
    },
    caseFile: {
      title: 'Patient case file',
      wardNumber: 'Ward number',
      motherName: "Mother's name",
      gender: 'Sex',
      occupation: 'Occupation',
      address: 'Address',
      nextOfKinAddress: 'Next of kin — address',
      nextOfKinPhone: 'Next of kin — phone',
      treatingSpecialist: 'Treating specialist',
      initialDiagnosis: 'Initial diagnosis',
      finalDiagnosis: 'Final diagnosis',
    },
  },
```
In `ar.ts`, the same structure (labels mirror the paper forms):
```ts
    history: 'التاريخ المرضي',
    nursing: 'التمريض',
    treatment: 'العلاج',
    caseFile: 'ملف المريض',
```
```ts
  forms: {
    save: 'حفظ',
    saved: 'تم الحفظ',
    signed: 'تم حفظ التوقيع',
    ptName: 'اسم المريض',
    ptCode: 'رمز المريض',
    age: 'العمر',
    doa: 'تاريخ الدخول',
    mh: {
      weightKg: 'الوزن (كغم)',
      heightCm: 'الطول (سم)',
      doctorName: 'اسم الطبيب',
      chiefComplaint: 'الشكوى الرئيسية ومدتها',
      presentIllnessHx: 'تاريخ المرض الحالي',
      psHx: 'التاريخ الجراحي السابق',
      pmHx: 'التاريخ المرضي السابق',
      familyHx: 'التاريخ العائلي',
      allergicHx: 'تاريخ الحساسية',
      socialSmoker: 'التاريخ الاجتماعي — التدخين',
      socialAlcohol: 'التاريخ الاجتماعي — الكحول',
      socialSleep: 'التاريخ الاجتماعي — النوم',
      drugHx: 'التاريخ الدوائي',
      physicalExamination: 'الفحص السريري',
      sign: { SPECIALIST: 'الطبيب الاختصاص', PERMANENT: 'الطبيب الدائم', RESIDENT: 'الطبيب المقيم' },
    },
    nursing: {
      procedureName: 'الإجراء',
      nurse: 'الممرض/ة',
      performedAt: 'الوقت',
      note: 'ملاحظة',
      add: 'إضافة إجراء',
      empty: 'لا توجد إجراءات مسجلة بعد',
    },
    treatment: {
      date: 'تاريخ الجدول',
      medicine: 'اسم الدواء',
      dose: 'الجرعة',
      freq: 'التكرار',
      addRow: 'إضافة دواء',
      signedBy: 'وقّعه',
    },
    caseFile: {
      title: 'ملف المريض الراقد',
      wardNumber: 'رقم الردهة',
      motherName: 'اسم الأم',
      gender: 'الجنس',
      occupation: 'المهنة',
      address: 'العنوان',
      nextOfKinAddress: 'عنوان أقرب شخص للمريض',
      nextOfKinPhone: 'رقم هاتف أقرب شخص',
      treatingSpecialist: 'الطبيب الاختصاصي المعالج',
      initialDiagnosis: 'التشخيص الأولي للمريض',
      finalDiagnosis: 'التشخيص النهائي للمريض',
    },
  },
```
Match the exact nesting/quoting style of the surrounding locale files.

- [ ] **Step 4: Typecheck + manual smoke**

Run: `cd frontend && npx tsc -b 2>&1 | head -20`
Expected: clean. Optionally start the dev stack and click through a premature case: History/Nursing/Treatment tabs render between Clinical and Billing for both departments.

- [ ] **Step 5: Commit**

```bash
git add frontend/src
git commit -m "feat(bed-stay-ui): History/Nursing/Treatment tabs wired for premature & emergency + en/ar i18n"
```

---

### Task 12: Case File tab (P6) — premature only

**Files:**
- Modify: `frontend/src/features/premature/api.ts`
- Create: `frontend/src/features/premature/CaseFileTab.tsx`
- Modify: `frontend/src/features/premature/PrematureCasePage.tsx`

- [ ] **Step 1: API types + call**

In `frontend/src/features/premature/api.ts`, extend the `PrematureCase` type (it mirrors `PrematureCaseResponse`) with:
```ts
export type PatientCaseForm = {
  wardNumber: string | null;
  nextOfKinAddress: string | null;
  nextOfKinPhone: string | null;
  treatingSpecialist: string | null;
  initialDiagnosis: string | null;
  finalDiagnosis: string | null;
};

export type CaseFilePrefill = { motherName: string | null; gender: string | null };
```
and on `PrematureCase`: `caseForm: PatientCaseForm | null; caseFilePrefill: CaseFilePrefill;`
Add:
```ts
export async function upsertCaseForm(admissionId: string, body: Partial<PatientCaseForm>): Promise<PatientCaseForm> {
  const res = await api.put(`/premature/admissions/${admissionId}/case-form`, body);
  return res.data;
}
```

- [ ] **Step 2: CaseFileTab component**

`frontend/src/features/premature/CaseFileTab.tsx`:
```tsx
import { useEffect, useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import toast from 'react-hot-toast';
import { extractApiError } from '@/shared/api/client';
import { upsertCaseForm, type PrematureCase } from './api';

const FIELDS = ['wardNumber', 'nextOfKinAddress', 'nextOfKinPhone',
  'treatingSpecialist', 'initialDiagnosis', 'finalDiagnosis'] as const;
const MULTILINE = new Set(['initialDiagnosis', 'finalDiagnosis']);

export function CaseFileTab({ c, admissionId, readOnly, onSaved }: {
  c: PrematureCase; admissionId: string; readOnly: boolean; onSaved: () => void;
}) {
  const { t } = useTranslation();
  const [draft, setDraft] = useState<Record<string, string>>({});
  useEffect(() => {
    setDraft(Object.fromEntries(FIELDS.map((f) => [f, c.caseForm?.[f] ?? ''])));
  }, [c.caseForm]);

  const save = useMutation({
    mutationFn: () => upsertCaseForm(admissionId,
      Object.fromEntries(FIELDS.map((f) => [f, draft[f] || null]))),
    onSuccess: () => { toast.success(t('caseView.forms.saved')); onSaved(); },
    onError: (e) => toast.error(extractApiError(e)?.message ?? t('caseView.error')),
  });

  const a = c.admission;
  const ro: [string, string | null][] = [
    [t('caseView.forms.ptName'), a.patientName],
    [t('caseView.forms.ptCode'), a.patientMrn],
    [t('caseView.forms.caseFile.motherName'), c.caseFilePrefill.motherName],
    [t('caseView.forms.caseFile.gender'), c.caseFilePrefill.gender],
    [t('caseView.forms.age'), c.prefill.ageText],
    [t('caseView.forms.caseFile.occupation'), null],   // not held for infants in the registry
    [t('caseView.forms.caseFile.address'), null],
  ];

  return (
    <div className="space-y-4" data-testid="casefile-tab">
      <dl className="grid grid-cols-2 gap-2 rounded-md border border-ink-100 bg-ink-50/50 p-3 text-sm md:grid-cols-4">
        {ro.map(([label, value]) => (
          <div key={label}><dt className="text-ink-500">{label}</dt><dd>{value ?? '—'}</dd></div>
        ))}
      </dl>

      <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
        {FIELDS.map((f) => (
          <label key={f} className={MULTILINE.has(f) ? 'text-sm md:col-span-2' : 'text-sm'}>
            <span className="text-ink-600">{t(`caseView.forms.caseFile.${f}`)}</span>
            {MULTILINE.has(f) ? (
              <textarea data-testid={`cf-${f}`} value={draft[f] ?? ''} disabled={readOnly} rows={2}
                onChange={(e) => setDraft((d) => ({ ...d, [f]: e.target.value }))}
                className="mt-1 w-full rounded-md border border-ink-200 px-2 py-1.5" />
            ) : (
              <input data-testid={`cf-${f}`} value={draft[f] ?? ''} disabled={readOnly}
                onChange={(e) => setDraft((d) => ({ ...d, [f]: e.target.value }))}
                className="mt-1 w-full rounded-md border border-ink-200 px-2 py-1.5" />
            )}
          </label>
        ))}
      </div>

      {!readOnly && (
        <button data-testid="cf-save" onClick={() => save.mutate()} disabled={save.isPending}
          className="rounded-md bg-brand-600 px-3 py-2 text-sm font-medium text-white hover:bg-brand-700 disabled:opacity-50">
          {t('caseView.forms.save')}
        </button>
      )}
    </div>
  );
}
```

- [ ] **Step 3: Wire into PrematureCasePage**

Append to its `extraTabs` array (after `treatment`):
```tsx
    { key: 'caseFile', label: t('caseView.tabs.caseFile'),
      content: <CaseFileTab c={c} admissionId={id!} readOnly={readOnly}
        onSaved={() => qc.invalidateQueries({ queryKey: ['prem-case', id] })} /> },
```
with `import { CaseFileTab } from './CaseFileTab';`.

- [ ] **Step 4: Typecheck + commit**

Run: `cd frontend && npx tsc -b 2>&1 | head -20` — expected clean.
```bash
git add frontend/src/features/premature frontend/src/features/beds
git commit -m "feat(premature-ui): Case File tab (P6) with registry prefill"
```

---

### Task 13: Playwright e2e

**Files:**
- Test: `frontend/e2e/bed-stay-clinical-forms.spec.ts`

- [ ] **Step 1: Start the stack**

`docker compose up -d --wait db`, then `cd backend && JAVA_HOME=/usr/lib/jvm/java-25-openjdk mvn install -DskipTests -q && JAVA_HOME=/usr/lib/jvm/java-25-openjdk mvn -pl app spring-boot:run` (background), then `cd frontend && npm run dev` (background).

- [ ] **Step 2: Write the spec**

The premature seed helper is the same as `frontend/e2e/premature-form.spec.ts` `seedUnderCare()` — copy it. For the emergency seed, copy the equivalent from `frontend/e2e/emergency-lifecycle-ui.spec.ts` (it knows the service-selection admit + initial payment).

```ts
import { test, expect } from '@playwright/test';
import { login } from './helpers/auth';
import { authedContext } from './helpers/api';
import { registerPatient, API_BASE } from './helpers/seeds';

// seedUnderCare() — copied from premature-form.spec.ts (premature admission at UNDER_CARE)
// seedEmergencyUnderTreatment() — copied from emergency-lifecycle-ui.spec.ts

test.describe('Bed-stay clinical forms (BRD REC-005 §6.6 + P6)', () => {

  test('medical history: fill → save → reload persists', async ({ page }) => {
    const { admissionId } = await seedUnderCare();
    await login(page, 'premature');
    await page.goto(`/premature/admissions/${admissionId}?tab=history`);
    await page.getByTestId('mh-chiefComplaint').fill('Fever for 2 days');
    await page.getByTestId('mh-doctorName').fill('Dr. House');
    await page.getByTestId('mh-save').click();
    await expect(page.getByText('Saved', { exact: false })).toBeVisible();
    await page.reload();
    await expect(page.getByTestId('mh-chiefComplaint')).toHaveValue('Fever for 2 days', { timeout: 10_000 });
  });

  test('nursing: nurse adds a procedure row, auto-attributed', async ({ page }) => {
    const { admissionId } = await seedUnderCare();
    await login(page, 'nurse');
    await page.goto(`/premature/admissions/${admissionId}?tab=nursing`);
    await page.getByTestId('nursing-procedureName').fill('Umbilical care');
    await page.getByTestId('nursing-add').click();
    await expect(page.getByTestId('nursing-rows')).toContainText('Umbilical care', { timeout: 10_000 });
    await page.reload();
    await expect(page.getByTestId('nursing-rows')).toContainText('Umbilical care');
  });

  test('treatment chart: add medicine rows → save → reload persists', async ({ page }) => {
    const { admissionId } = await seedUnderCare();
    await login(page, 'premature');
    await page.goto(`/premature/admissions/${admissionId}?tab=treatment`);
    await page.getByTestId('tc-row-0-medicine').fill('Ampicillin');
    await page.getByTestId('tc-row-0-dose').fill('50mg/kg');
    await page.getByTestId('tc-add-row').click();
    await page.getByTestId('tc-row-1-medicine').fill('Gentamicin');
    await page.getByTestId('tc-save').click();
    await expect(page.getByText('Saved', { exact: false })).toBeVisible();
    await page.reload();
    await expect(page.getByTestId('tc-row-0-medicine')).toHaveValue('Ampicillin', { timeout: 10_000 });
    await expect(page.getByTestId('tc-row-1-medicine')).toHaveValue('Gentamicin');
  });

  test('case file (P6): premature staff save diagnosis; registry prefill shown', async ({ page }) => {
    const { admissionId } = await seedUnderCare();
    await login(page, 'premature');
    await page.goto(`/premature/admissions/${admissionId}?tab=caseFile`);
    await page.getByTestId('cf-wardNumber').fill('W-3');
    await page.getByTestId('cf-initialDiagnosis').fill('Prematurity, RDS');
    await page.getByTestId('cf-save').click();
    await expect(page.getByText('Saved', { exact: false })).toBeVisible();
    await page.reload();
    await expect(page.getByTestId('cf-initialDiagnosis')).toHaveValue('Prematurity, RDS', { timeout: 10_000 });
  });

  test('emergency smoke: shared History tab works on an emergency case', async ({ page }) => {
    const { caseId } = await seedEmergencyUnderTreatment();
    await login(page, 'emergency');
    await page.goto(`/emergency/cases/${caseId}?tab=history`);
    await page.getByTestId('mh-chiefComplaint').fill('RTA — head injury');
    await page.getByTestId('mh-save').click();
    await expect(page.getByText('Saved', { exact: false })).toBeVisible();
    await page.reload();
    await expect(page.getByTestId('mh-chiefComplaint')).toHaveValue('RTA — head injury', { timeout: 10_000 });
  });
});
```
If the toast text assertion is brittle, assert on the persisted value after reload only (the reload assertions are the real proof).

- [ ] **Step 3: Run the new spec**

Run: `cd frontend && npx playwright test bed-stay-clinical-forms --reporter=line`
Expected: 5 passed.

- [ ] **Step 4: Commit**

```bash
git add frontend/e2e/bed-stay-clinical-forms.spec.ts
git commit -m "test(e2e): clinical forms tabs — fill/save/reload for all four forms + emergency smoke"
```

---

### Task 14: Full verification

- [ ] **Step 1: Full backend build + all ITs**

Run: `cd backend && JAVA_HOME=/usr/lib/jvm/java-25-openjdk mvn verify -q`
Expected: BUILD SUCCESS — all unit tests, all ITs (≈120, incl. the ~10 new ones), ArchUnit green.

- [ ] **Step 2: Frontend build + full e2e suite**

Run: `cd frontend && npm run build && npx playwright test --reporter=line`
Expected: build clean; full suite green (pre-existing specs unaffected — the case page only gained tabs).

- [ ] **Step 3: Final commit (if any stragglers) and report**

`git status` must be clean apart from intentionally-untracked files. Report results honestly — if anything is red, fix before claiming done (verification-before-completion).
