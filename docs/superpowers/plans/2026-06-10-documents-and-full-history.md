# Reliable Documents + Documents Tab + Patient Full History Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Harden document storage (SHA-256, typed missing-file errors, integrity check, canonical root), add a stay-scoped Documents layer + Documents tab to the bed-stay case page (uploads + Lab/Radiology/ECO result attachments), and rebuild the patient profile page into a full-history timeline with documents.

**Architecture:** Storage hardening lives in `platform` (richer `FileStorage`, `StorageMissingException`, inventory-contributor port + verify endpoint). Stay documents live in `bed-stay-forms` (`StayDocument` aggregate; result attachments resolved through a new `orders()` method on the `StayDirectory` port and a new dependency on `department-services`). Patient history uses a `HistoryContributor` port in `clinical-case` (same inward-pointing pattern), implemented by premature, emergency, and bed-stay-forms. Frontend: shared `DocumentPreview`, a `DocumentsTab` on `BedStayCasePage`, and a redesigned `PatientProfilePage` composed from new components under `features/patients/history/`.

**Tech Stack:** Spring Boot 3.3 modules + Flyway/Postgres + Failsafe ITs, React + TanStack Query + Tailwind, Playwright.

**Build commands (this machine):** no `./mvnw`; ALWAYS `cd backend && JAVA_HOME=/usr/lib/jvm/java-25-openjdk mvn …` (env does not persist between commands). ITs are `*IT` under Failsafe: `mvn -pl app -am verify -Dit.test=<X> -Dsurefire.failIfNoSpecifiedTests=false`. Frontend typecheck: `cd frontend && npx tsc -b`. E2E needs db (`docker compose up -d --wait db`), backend (`mvn -pl app spring-boot:run`), frontend (`npm run dev`). Testcontainers fix if needed: `~/.docker-java.properties` → `api.version=1.40`.

**Spec:** `docs/superpowers/specs/2026-06-10-documents-and-full-history-design.md`

---

## File structure

**platform:** `storage/StoredBlob.java` (new record), `storage/FileStorage.java` (+saveVerified default), `storage/LocalFileSystemStorage.java` (saveVerified impl, StorageMissingException on open-miss, startup WARN), `exception/StorageMissingException.java` (new), `web/GlobalExceptionHandler.java` (+mapping), `storage/inventory/{DocumentInventoryContributor,DocumentRef,StorageVerifyController,StorageVerifyHandler,StorageVerifyResponse}.java` (new).

**bed-stay-forms:** `domain/StayDocument.java`, `infrastructure/StayDocumentRepository.java`, `api/StayDocumentDto.java`, `documents/{UploadDocumentController... see Task 3}`, `directory/StayDirectory.java` (+orders), `directory/StayOrderRef.java`, `inventory/BedStayFormsInventoryContributor.java`, `history/BedStayFormsHistoryContributor.java`, `src/main/resources/db/migration/V029__stay_document.sql`, pom (+department-services, +clinical-case).

**premature:** `staydirectory/PrematureStayDirectory.java` (+orders), `history/PrematureHistoryContributor.java`, `inventory/PrematureInventoryContributor.java`, pom (+clinical-case).
**emergency:** `staydirectory/EmergencyStayDirectory.java` (+orders), `history/EmergencyHistoryContributor.java`, pom (+clinical-case).
**department-services:** `inventory/DeptServicesInventoryContributor.java`.
**clinical-case:** `history/{HistoryContributor,HistoryEntry,HistoryEntryType,HistoryRefs}.java`, `patienthistory/{PatientHistoryHandler,PatientHistoryResponse}.java` (modified — adds `timeline`).
**app:** `application.yml` (multipart limits + attachments dir), `ArchitectureTest` (+rule). ITs: `app/src/test/.../bedstayforms/StayDocumentsIT.java`, `.../platform/StorageVerifyIT.java`, `.../clinicalcase/FullHistoryIT.java`.

**Repo root:** `ops/consolidate-attachments.sh`, `docs/OPERATIONS.md`, `docker-compose.yml` untouched (db only — attachments dir is set via application.yml).

**frontend:** `shared/ui/DocumentPreview.tsx`, `features/beds/case/forms/documentsApi.ts`, `features/beds/case/forms/DocumentsTab.tsx`, `features/beds/case/BedStayCasePage.tsx` (tab count badge support for extra tabs), `features/premature/PrematureCasePage.tsx` + `features/emergency/EmergencyCasePage.tsx` (wire tab), `features/patients/api.ts`(history types), `features/patients/history/{ProfileHeader,SummaryChips,UnifiedTimeline,TimelineEntryCard}.tsx`, `features/patients/PatientProfilePage.tsx` (recompose), locales `en.ts`/`ar.ts`. E2E: `e2e/case-documents.spec.ts`, `e2e/patient-full-history.spec.ts`.

**Migration number:** V029 next-free as of now (max V028). Re-check; bump if taken.

---

### Task 1: Storage hardening core (platform)

**Files:**
- Create: `backend/platform/src/main/java/com/albudoor/hms/platform/exception/StorageMissingException.java`
- Create: `backend/platform/src/main/java/com/albudoor/hms/platform/storage/StoredBlob.java`
- Modify: `backend/platform/src/main/java/com/albudoor/hms/platform/storage/FileStorage.java`
- Modify: `backend/platform/src/main/java/com/albudoor/hms/platform/storage/LocalFileSystemStorage.java`
- Modify: `backend/platform/src/main/java/com/albudoor/hms/platform/web/GlobalExceptionHandler.java`
- Modify: `backend/app/src/main/resources/application.yml`
- Test: `backend/platform/src/test/java/com/albudoor/hms/platform/storage/LocalFileSystemStorageTest.java`

- [ ] **Step 1: Failing unit tests**

Read `backend/platform/src/test/java/com/albudoor/hms/platform/web/GlobalExceptionHandlerTest.java` first to mirror test style. Create `LocalFileSystemStorageTest.java`:

```java
package com.albudoor.hms.platform.storage;

import com.albudoor.hms.platform.exception.StorageMissingException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalFileSystemStorageTest {

    @TempDir Path dir;

    private LocalFileSystemStorage storage() throws IOException {
        return new LocalFileSystemStorage(dir.toString());
    }

    @Test
    void saveVerified_records_sha256_and_size_and_roundtrips() throws IOException {
        var s = storage();
        byte[] payload = "hello documents".getBytes(StandardCharsets.UTF_8);
        StoredBlob blob = s.saveVerified(new ByteArrayInputStream(payload), "report.pdf");
        // sha256 of "hello documents"
        assertThat(blob.sha256()).isEqualTo("d3556a82dd97829e57e9b977a142fc3e3766aef83b59ea1d3e3da9b6e30d02cd");
        assertThat(blob.sizeBytes()).isEqualTo(payload.length);
        assertThat(blob.storageKey()).endsWith(".pdf");
        try (var in = s.open(blob.storageKey())) {
            assertThat(in.readAllBytes()).isEqualTo(payload);
        }
    }

    @Test
    void open_missing_key_throws_typed_exception() throws IOException {
        var s = storage();
        assertThatThrownBy(() -> s.open("2026-06-10/does-not-exist.pdf"))
                .isInstanceOf(StorageMissingException.class);
    }
}
```
NOTE: verify the expected sha256 by computing it yourself before relying on it: `printf 'hello documents' | sha256sum`. If it differs, fix the constant in the test (compute, don't guess).

- [ ] **Step 2: Run to verify failure**

Run: `cd backend && JAVA_HOME=/usr/lib/jvm/java-25-openjdk mvn -pl platform test -q`
Expected: COMPILATION ERROR (StoredBlob, StorageMissingException, saveVerified missing).

- [ ] **Step 3: Implement**

`StorageMissingException.java` — read `backend/platform/src/main/java/com/albudoor/hms/platform/exception/NotFoundException.java` first and mirror its code mechanism exactly (it exposes `getCode()` used by the handler). Shape:
```java
package com.albudoor.hms.platform.exception;

/** A DB-referenced blob is gone from the storage backend — surfaces as 404 DOCUMENT_MISSING. */
public class StorageMissingException extends RuntimeException {
    public StorageMissingException(String storageKey) {
        super("Stored document is missing: " + storageKey);
    }
}
```

`StoredBlob.java`:
```java
package com.albudoor.hms.platform.storage;

/** Result of a verified save: where it lives, what it hashes to, how big it is. */
public record StoredBlob(String storageKey, String sha256, long sizeBytes) {}
```

`FileStorage.java` — add below the existing methods:
```java
    /**
     * Persist bytes computing SHA-256 + size in the same pass. Default implementation for
     * any non-local backend wraps {@link #save}; LocalFileSystemStorage overrides for a
     * single-pass streaming hash.
     */
    StoredBlob saveVerified(InputStream in, String suggestedName) throws IOException;
```
(No default body — `LocalFileSystemStorage` is the only implementation; implement it there.)

`LocalFileSystemStorage.java` changes:
1. Constructor: after `log.info("FileStorage rooted at {}", this.root);` add
```java
        if ("data/attachments".equals(dir)) {
            log.warn("hms.attachments.dir is the RELATIVE default ('data/attachments') — the "
                    + "storage root depends on the launch directory. Set an absolute path in "
                    + "configuration to avoid scattered document roots (see docs/OPERATIONS.md).");
        }
```
2. `open(...)`: replace the final `return Files.newInputStream(p);` with
```java
        if (!Files.exists(p)) throw new StorageMissingException(storageKey);
        return Files.newInputStream(p);
```
(import `com.albudoor.hms.platform.exception.StorageMissingException`).
3. Add:
```java
    @Override
    public StoredBlob saveVerified(InputStream in, String suggestedName) throws IOException {
        String ext = extOf(suggestedName);
        String key = LocalDate.now() + "/" + UUID.randomUUID() + (ext.isEmpty() ? "" : "." + ext);
        Path target = root.resolve(key);
        Files.createDirectories(target.getParent());
        java.security.MessageDigest md;
        try {
            md = java.security.MessageDigest.getInstance("SHA-256");
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e); // JDK guarantees SHA-256
        }
        long size;
        try (var digestIn = new java.security.DigestInputStream(in, md)) {
            size = Files.copy(digestIn, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return new StoredBlob(key, java.util.HexFormat.of().formatHex(md.digest()), size);
    }
```

`GlobalExceptionHandler.java` — add (import the exception), placed next to handleNotFound:
```java
    @ExceptionHandler(StorageMissingException.class)
    public ResponseEntity<ApiError> handleStorageMissing(StorageMissingException ex) {
        log.error("Document blob missing: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of(404, "DOCUMENT_MISSING", ex.getMessage()));
    }
```

`backend/app/src/main/resources/application.yml` — add under the existing `spring:` key (match indentation; multipart is currently absent so Boot's 1 MB default applies):
```yaml
  servlet:
    multipart:
      max-file-size: 25MB
      max-request-size: 30MB
```
And add a top-level block (or extend the existing `hms:` block if one exists — check):
```yaml
hms:
  attachments:
    dir: ${HMS_ATTACHMENTS_DIR:data/attachments}
```
This keeps the dev default but makes the env override explicit and documented.

- [ ] **Step 4: Run tests to verify pass**

Run: `cd backend && JAVA_HOME=/usr/lib/jvm/java-25-openjdk mvn -pl platform -am test -q`
Expected: PASS (both new tests + existing platform tests).

- [ ] **Step 5: Build downstream + commit**

Run: `cd backend && JAVA_HOME=/usr/lib/jvm/java-25-openjdk mvn -pl app -am install -DskipTests -q` — SUCCESS (no call site breaks: saveVerified is new; existing implementations is only LocalFileSystemStorage which now implements it).
```bash
git add backend/platform backend/app/src/main/resources/application.yml
git commit -m "feat(platform): verified blob saves (sha-256), typed DOCUMENT_MISSING 404, multipart limits"
```

---

### Task 2: StayDocument — migration, aggregate, upload/list/stream/archive (uploads only) + IT

**Files:**
- Create: `backend/bed-stay-forms/src/main/resources/db/migration/V029__stay_document.sql`
- Create: `backend/bed-stay-forms/src/main/java/com/albudoor/hms/bedstayforms/domain/StayDocument.java`
- Create: `backend/bed-stay-forms/src/main/java/com/albudoor/hms/bedstayforms/infrastructure/StayDocumentRepository.java`
- Create: `backend/bed-stay-forms/src/main/java/com/albudoor/hms/bedstayforms/api/StayDocumentDto.java`
- Create: `backend/bed-stay-forms/src/main/java/com/albudoor/hms/bedstayforms/documents/StayDocumentsController.java`
- Create: `backend/bed-stay-forms/src/main/java/com/albudoor/hms/bedstayforms/documents/StayDocumentsHandler.java`
- Test: `backend/app/src/test/java/com/albudoor/hms/app/bedstayforms/StayDocumentsIT.java`

- [ ] **Step 1: Failing IT**

`StayDocumentsIT.java` — the `auth`/`post`/`admitUnderCare` helpers are copied from `backend/app/src/test/java/com/albudoor/hms/app/bedstayforms/BedStayFormsIT.java` (same package, same seeded users; copy them in verbatim, including the `PNG` constant):

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

class StayDocumentsIT extends IntegrationTest {

    static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    @Autowired TestRestTemplate rest;
    @Autowired BedRepository beds;
    @Autowired PaymentRepository payments;

    // auth(...), post(...), admitUnderCare() — copy verbatim from BedStayFormsIT

    String docsUrl(String stayId) { return "/api/bed-stays/PREMATURE/" + stayId + "/documents"; }

    HttpEntity<LinkedMultiValueMap<String, Object>> pngUpload(String user, String fileName, String label) {
        HttpHeaders h = auth(user);
        h.setContentType(MediaType.MULTIPART_FORM_DATA);
        var filePart = new HttpHeaders();
        filePart.setContentType(MediaType.IMAGE_PNG);
        var form = new LinkedMultiValueMap<String, Object>();
        form.add("file", new HttpEntity<>(new ByteArrayResource(PNG) {
            @Override public String getFilename() { return fileName; }
        }, filePart));
        if (label != null) form.add("label", label);
        return new HttpEntity<>(form, h);
    }

    @Test @SuppressWarnings("unchecked")
    void upload_list_stream_archive_roundtrip() {
        String stay = admitUnderCare();

        var up = rest.exchange(docsUrl(stay), HttpMethod.POST, pngUpload("nurse", "scan.png", "Statistics form"), Map.class);
        assertThat(up.getStatusCode().is2xxSuccessful()).as("%s", up.getBody()).isTrue();
        assertThat(up.getBody().get("sha256")).asString().hasSize(64);
        assertThat(((Number) up.getBody().get("sizeBytes")).longValue()).isEqualTo(PNG.length);
        String docId = (String) up.getBody().get("id");

        var list = rest.exchange(docsUrl(stay), HttpMethod.GET, new HttpEntity<>(auth("premature")), List.class);
        List<Map<String, Object>> docs = list.getBody();
        assertThat(docs).hasSize(1);
        assertThat(docs.get(0).get("source")).isEqualTo("UPLOAD");
        assertThat(docs.get(0).get("fileName")).isEqualTo("scan.png");
        assertThat(docs.get(0).get("label")).isEqualTo("Statistics form");
        assertThat(docs.get(0).get("archived")).isEqualTo(false);

        var file = rest.exchange(docsUrl(stay) + "/" + docId + "/file", HttpMethod.GET,
                new HttpEntity<>(auth("doctor")), byte[].class);
        assertThat(file.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(file.getBody()).isEqualTo(PNG);

        var arch = rest.exchange(docsUrl(stay) + "/" + docId + "/archive", HttpMethod.POST,
                new HttpEntity<>(auth("premature")), Map.class);
        assertThat(arch.getStatusCode().is2xxSuccessful()).isTrue();
        var after = rest.exchange(docsUrl(stay), HttpMethod.GET, new HttpEntity<>(auth("premature")), List.class);
        assertThat(((Map<String, Object>) after.getBody().get(0)).get("archived")).isEqualTo(true);
    }

    @Test
    void upload_policy_rejects_bad_type_and_nurse_cannot_archive() {
        String stay = admitUnderCare();
        // bad content type
        HttpHeaders h = auth("nurse");
        h.setContentType(MediaType.MULTIPART_FORM_DATA);
        var part = new HttpHeaders();
        part.setContentType(MediaType.TEXT_PLAIN);
        var form = new LinkedMultiValueMap<String, Object>();
        form.add("file", new HttpEntity<>(new ByteArrayResource("hi".getBytes()) {
            @Override public String getFilename() { return "notes.txt"; }
        }, part));
        var bad = rest.exchange(docsUrl(stay), HttpMethod.POST, new HttpEntity<>(form, h), String.class);
        assertThat(bad.getStatusCode().value()).isEqualTo(422);

        // nurse cannot archive
        var up = rest.exchange(docsUrl(stay), HttpMethod.POST, pngUpload("nurse", "x.png", null), Map.class);
        String docId = (String) up.getBody().get("id");
        var arch = rest.exchange(docsUrl(stay) + "/" + docId + "/archive", HttpMethod.POST,
                new HttpEntity<>(auth("nurse")), String.class);
        assertThat(arch.getStatusCode().value()).isEqualTo(403);
    }
}
```

Also add a third test covering the remaining spec-promised cases (assemble from existing helpers — the reject-payment→CANCELLED flow and `pureStaff` user-creation helper are in `BedStayFormsAuthzIT`; copy them):

```java
    @Test
    void oversize_closed_stay_and_cross_department_are_rejected() {
        String stay = admitUnderCare();

        // oversize: 21 MB body -> 422 DOCUMENT_TOO_LARGE (multipart limit is 25MB so it reaches the handler)
        HttpHeaders h = auth("nurse");
        h.setContentType(MediaType.MULTIPART_FORM_DATA);
        var part = new HttpHeaders();
        part.setContentType(MediaType.IMAGE_PNG);
        var form = new LinkedMultiValueMap<String, Object>();
        form.add("file", new HttpEntity<>(new ByteArrayResource(new byte[21 * 1024 * 1024]) {
            @Override public String getFilename() { return "huge.png"; }
        }, part));
        var big = rest.exchange(docsUrl(stay), HttpMethod.POST, new HttpEntity<>(form, h), String.class);
        assertThat(big.getStatusCode().value()).isEqualTo(422);

        // cross-department: a user whose ONLY role is EMERGENCY_STAFF gets 403 on a premature stay
        // (create via POST /api/users as admin — copy the pureStaff helper from BedStayFormsAuthzIT)
        String pureEmergency = pureStaff("EMERGENCY_STAFF");
        var denied = rest.exchange(docsUrl(stay), HttpMethod.GET, new HttpEntity<>(auth(pureEmergency)), String.class);
        assertThat(denied.getStatusCode().value()).isEqualTo(403);

        // closed stay: reject the initial payment of a FRESH pending admission -> CANCELLED -> upload 422
        // (copy admitPending + the awaitility CANCELLED wait from BedStayFormsAuthzIT)
        String cancelled = admitPendingThenRejectAndAwaitCancelled();
        var closedUp = rest.exchange(docsUrl(cancelled), HttpMethod.POST, pngUpload("nurse", "late.png", null), String.class);
        assertThat(closedUp.getStatusCode().value()).isEqualTo(422);
    }
```

- [ ] **Step 2: Run to verify failure (404)**

Run: `cd backend && JAVA_HOME=/usr/lib/jvm/java-25-openjdk mvn -pl app -am verify -Dit.test=StayDocumentsIT -Dsurefire.failIfNoSpecifiedTests=false -q` (timeout 600000)
Expected: FAIL — 404 (endpoints absent).

- [ ] **Step 3: Migration `V029__stay_document.sql`**

```sql
-- Direct document uploads on a bed-stay (incl. BRD REC-005 P7c scanned statistics forms).
-- Result attachments from forwarded Lab/Rad/ECO visits are NOT copied here — they are
-- aggregated at read time from case_attachment via the stay's forwarded visits.

CREATE TABLE stay_document (
    id              UUID PRIMARY KEY,
    department      VARCHAR(20)  NOT NULL,
    stay_id         UUID         NOT NULL,
    patient_id      UUID         NOT NULL,
    file_name       VARCHAR(300) NOT NULL,
    content_type    VARCHAR(150) NOT NULL,
    size_bytes      BIGINT       NOT NULL,
    sha256          VARCHAR(64)  NOT NULL,
    storage_key     VARCHAR(500) NOT NULL,
    label           VARCHAR(200),
    uploaded_by     UUID,
    archived        BOOLEAN      NOT NULL DEFAULT FALSE,
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL,
    created_by      VARCHAR(100),
    updated_at      TIMESTAMPTZ,
    updated_by      VARCHAR(100),
    CONSTRAINT chk_stay_doc_dept CHECK (department IN ('PREMATURE', 'EMERGENCY'))
);
CREATE INDEX idx_stay_doc_stay ON stay_document (department, stay_id, created_at DESC);
CREATE INDEX idx_stay_doc_patient ON stay_document (patient_id);
```

- [ ] **Step 4: Aggregate, repository, DTO**

`StayDocument.java`:
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

import java.util.UUID;

/**
 * A document uploaded directly onto a bed-stay (scanned forms, referral letters, the BRD's
 * P7c statistics sheets). Archived, never deleted (BRD §8.2).
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "stay_document")
public class StayDocument extends AggregateRoot {

    @Id
    private UUID id;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StayDepartment department;
    @Column(name = "stay_id", nullable = false)
    private UUID stayId;
    @Column(name = "patient_id", nullable = false)
    private UUID patientId;
    @Column(name = "file_name", nullable = false, length = 300)
    private String fileName;
    @Column(name = "content_type", nullable = false, length = 150)
    private String contentType;
    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;
    @Column(nullable = false, length = 64)
    private String sha256;
    @Column(name = "storage_key", nullable = false, length = 500)
    private String storageKey;
    @Column(length = 200)
    private String label;
    @Column(name = "uploaded_by")
    private UUID uploadedBy;
    @Column(nullable = false)
    private boolean archived;

    public static StayDocument upload(StayDepartment department, UUID stayId, UUID patientId,
                                      String fileName, String contentType, long sizeBytes,
                                      String sha256, String storageKey, String label, UUID uploadedBy) {
        if (department == null || stayId == null || patientId == null) {
            throw new DomainException("STAY_REF_REQUIRED", "department, stay and patient are required");
        }
        if (storageKey == null || sha256 == null) {
            throw new DomainException("BLOB_REF_REQUIRED", "storage key and checksum are required");
        }
        StayDocument d = new StayDocument();
        d.id = UUID.randomUUID();
        d.department = department;
        d.stayId = stayId;
        d.patientId = patientId;
        d.fileName = fileName;
        d.contentType = contentType;
        d.sizeBytes = sizeBytes;
        d.sha256 = sha256;
        d.storageKey = storageKey;
        d.label = label;
        d.uploadedBy = uploadedBy;
        d.archived = false;
        return d;
    }

    public void archive() { this.archived = true; }
}
```

`StayDocumentRepository.java`:
```java
package com.albudoor.hms.bedstayforms.infrastructure;

import com.albudoor.hms.bedstayforms.domain.StayDepartment;
import com.albudoor.hms.bedstayforms.domain.StayDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface StayDocumentRepository extends JpaRepository<StayDocument, UUID> {
    List<StayDocument> findAllByDepartmentAndStayIdOrderByCreatedAtDesc(StayDepartment department, UUID stayId);
    List<StayDocument> findAllByPatientIdOrderByCreatedAtDesc(UUID patientId);
}
```

`StayDocumentDto.java` (one merged row shape — `source` distinguishes uploads from result attachments added in Task 3):
```java
package com.albudoor.hms.bedstayforms.api;

import com.albudoor.hms.bedstayforms.domain.StayDocument;

import java.time.Instant;
import java.util.UUID;

/** One row of the merged documents list. source: UPLOAD | LABORATORY | RADIOLOGY | ECO. */
public record StayDocumentDto(
        UUID id, String source, String fileName, String contentType, long sizeBytes,
        String label, UUID uploadedBy, Instant uploadedAt, boolean archived, String fileUrl
) {
    public static StayDocumentDto fromUpload(StayDocument d, String fileUrl) {
        return new StayDocumentDto(d.getId(), "UPLOAD", d.getFileName(), d.getContentType(),
                d.getSizeBytes(), d.getLabel(), d.getUploadedBy(), d.getCreatedAt(), d.isArchived(), fileUrl);
    }
}
```

- [ ] **Step 5: Handler + controller (uploads only; results merge lands in Task 3)**

`StayDocumentsHandler.java`:
```java
package com.albudoor.hms.bedstayforms.documents;

import com.albudoor.hms.bedstayforms.api.StayDocumentDto;
import com.albudoor.hms.bedstayforms.directory.StayDirectoryRegistry;
import com.albudoor.hms.bedstayforms.directory.StayInfo;
import com.albudoor.hms.bedstayforms.domain.StayDepartment;
import com.albudoor.hms.bedstayforms.domain.StayDocument;
import com.albudoor.hms.bedstayforms.infrastructure.StayDocumentRepository;
import com.albudoor.hms.platform.exception.DomainException;
import com.albudoor.hms.platform.exception.NotFoundException;
import com.albudoor.hms.platform.storage.FileStorage;
import com.albudoor.hms.platform.storage.StoredBlob;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class StayDocumentsHandler {

    static final long MAX_BYTES = 20L * 1024 * 1024;

    private final StayDocumentRepository documents;
    private final StayDirectoryRegistry stays;
    private final FileStorage storage;

    public StayDocumentsHandler(StayDocumentRepository documents, StayDirectoryRegistry stays,
                                FileStorage storage) {
        this.documents = documents;
        this.stays = stays;
        this.storage = storage;
    }

    @Transactional(readOnly = true)
    public List<StayDocumentDto> list(StayDepartment dept, UUID stayId) {
        stays.require(dept, stayId);
        List<StayDocumentDto> out = new ArrayList<>();
        String base = "/api/bed-stays/" + dept + "/" + stayId + "/documents/";
        for (StayDocument d : documents.findAllByDepartmentAndStayIdOrderByCreatedAtDesc(dept, stayId)) {
            out.add(StayDocumentDto.fromUpload(d, base + d.getId() + "/file"));
        }
        return out;
    }

    @Transactional
    public StayDocumentDto upload(StayDepartment dept, UUID stayId, MultipartFile file,
                                  String label, UUID uploadedBy) throws IOException {
        StayInfo info = stays.requireOpen(dept, stayId);
        if (file.isEmpty()) throw new DomainException("DOCUMENT_EMPTY", "Document is empty");
        if (file.getSize() > MAX_BYTES) {
            throw new DomainException("DOCUMENT_TOO_LARGE", "Documents are limited to 20 MB");
        }
        String ct = file.getContentType();
        if (ct == null || !(ct.startsWith("image/") || ct.equals("application/pdf"))) {
            throw new DomainException("DOCUMENT_TYPE_NOT_ALLOWED", "Only images and PDF documents are allowed");
        }
        StoredBlob blob;
        try (var in = file.getInputStream()) {
            blob = storage.saveVerified(in, file.getOriginalFilename() == null ? "document" : file.getOriginalFilename());
        }
        StayDocument d = StayDocument.upload(dept, stayId, info.patientId(),
                file.getOriginalFilename(), ct, blob.sizeBytes(), blob.sha256(), blob.storageKey(),
                (label == null || label.isBlank()) ? null : label.trim(), uploadedBy);
        String base = "/api/bed-stays/" + dept + "/" + stayId + "/documents/";
        return StayDocumentDto.fromUpload(documents.save(d), base + d.getId() + "/file");
    }

    @Transactional
    public StayDocumentDto archive(StayDepartment dept, UUID stayId, UUID documentId) {
        stays.requireOpen(dept, stayId);
        StayDocument d = requireDoc(dept, stayId, documentId);
        d.archive();
        String base = "/api/bed-stays/" + dept + "/" + stayId + "/documents/";
        return StayDocumentDto.fromUpload(documents.save(d), base + d.getId() + "/file");
    }

    @Transactional(readOnly = true)
    public StayDocument requireDoc(StayDepartment dept, UUID stayId, UUID documentId) {
        StayDocument d = documents.findById(documentId)
                .orElseThrow(() -> new NotFoundException("Document not found: " + documentId));
        if (d.getDepartment() != dept || !d.getStayId().equals(stayId)) {
            throw new NotFoundException("Document not found on this stay: " + documentId);
        }
        return d;
    }
}
```

`StayDocumentsController.java`:
```java
package com.albudoor.hms.bedstayforms.documents;

import com.albudoor.hms.bedstayforms.access.BedStayAccess;
import com.albudoor.hms.bedstayforms.access.CurrentUser;
import com.albudoor.hms.bedstayforms.api.StayDocumentDto;
import com.albudoor.hms.bedstayforms.domain.StayDepartment;
import com.albudoor.hms.bedstayforms.domain.StayDocument;
import com.albudoor.hms.platform.storage.FileStorage;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/bed-stays/{department}/{stayId}/documents")
public class StayDocumentsController {

    private final StayDocumentsHandler handler;
    private final BedStayAccess access;
    private final FileStorage storage;

    public StayDocumentsController(StayDocumentsHandler handler, BedStayAccess access, FileStorage storage) {
        this.handler = handler;
        this.access = access;
        this.storage = storage;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<StayDocumentDto> list(@PathVariable StayDepartment department, @PathVariable UUID stayId) {
        access.checkRead(department);
        return handler.list(department, stayId);
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public StayDocumentDto upload(@PathVariable StayDepartment department, @PathVariable UUID stayId,
                                  @RequestParam("file") MultipartFile file,
                                  @RequestParam(value = "label", required = false) String label)
            throws IOException {
        access.checkRead(department); // read-level: dept staff, DOCTOR, NURSE, ADMIN may upload
        return handler.upload(department, stayId, file, label, CurrentUser.id());
    }

    @GetMapping("/{documentId}/file")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Resource> file(@PathVariable StayDepartment department, @PathVariable UUID stayId,
                                         @PathVariable UUID documentId) throws IOException {
        access.checkRead(department);
        StayDocument d = handler.requireDoc(department, stayId, documentId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(d.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + d.getFileName().replace("\"", "_") + "\"")
                .contentLength(d.getSizeBytes())
                .body(new InputStreamResource(storage.open(d.getStorageKey())));
    }

    @PostMapping("/{documentId}/archive")
    @PreAuthorize("isAuthenticated()")
    public StayDocumentDto archive(@PathVariable StayDepartment department, @PathVariable UUID stayId,
                                   @PathVariable UUID documentId) {
        access.checkDoctorWrite(department); // dept staff, DOCTOR, ADMIN — nurses may not archive
        return handler.archive(department, stayId, documentId);
    }
}
```
NOTE the role decisions: upload = checkRead level (nurses upload scanned forms); archive = checkDoctorWrite. The spec table says archive = "dept staff, ADMIN" — `checkDoctorWrite` also admits DOCTOR; that is an accepted widening consistent with every other doctor-level write in the module (note it in the commit message).

- [ ] **Step 6: Run the IT to verify pass**

Run: `cd backend && JAVA_HOME=/usr/lib/jvm/java-25-openjdk mvn -pl app -am verify -Dit.test=StayDocumentsIT -Dsurefire.failIfNoSpecifiedTests=false -q` (timeout 600000)
Expected: PASS (2 tests).

- [ ] **Step 7: Commit**

```bash
git add backend/bed-stay-forms backend/app/src/test/java/com/albudoor/hms/app/bedstayforms/StayDocumentsIT.java
git commit -m "feat(bed-stay-forms): stay documents — verified upload, stream, archive-not-delete + IT (P7c)"
```

---

### Task 3: Result attachments in the merged list + stay-scoped streaming

**Files:**
- Modify: `backend/bed-stay-forms/pom.xml` (+`department-services` dependency)
- Modify: `backend/bed-stay-forms/src/main/java/com/albudoor/hms/bedstayforms/directory/StayDirectory.java`
- Create: `backend/bed-stay-forms/src/main/java/com/albudoor/hms/bedstayforms/directory/StayOrderRef.java`
- Modify: `backend/premature/src/main/java/com/albudoor/hms/premature/staydirectory/PrematureStayDirectory.java`
- Modify: `backend/emergency/src/main/java/com/albudoor/hms/emergency/staydirectory/EmergencyStayDirectory.java`
- Modify: `backend/bed-stay-forms/src/main/java/com/albudoor/hms/bedstayforms/documents/{StayDocumentsHandler,StayDocumentsController}.java`
- Test: extend `StayDocumentsIT.java`

- [ ] **Step 1: Failing IT tests (append to StayDocumentsIT)**

The lab-side upload flow: order LAB from the stay (`POST /api/premature/admissions/{id}/orders` body `{"targetType":"LABORATORY"}` as doctor), find the forwarded visit's DepartmentCase, upload an attachment as `lab` user via the existing `POST /api/dept-cases/{caseId}/services/{serviceItemId}/attachments`. Read `backend/app/src/test/java/com/albudoor/hms/app/deptcase/CaseAttachmentFinalizedIT.java` FIRST — it shows exactly how to drive a dept-case + attachment upload in an IT (case lookup, serviceItemId, multipart). Reuse its approach; inject `DepartmentCaseRepository` to find the case by the forwarded visit id (`findByVisitId`).

```java
    @Autowired com.albudoor.hms.departmentservices.infrastructure.DepartmentCaseRepository deptCases;

    @Test @SuppressWarnings("unchecked")
    void lab_result_attachment_appears_in_merged_list_and_streams_stay_scoped() {
        String stay = admitUnderCare();
        // order LAB from the stay
        var order = post("/api/premature/admissions/" + stay + "/orders",
                Map.of("targetType", "LABORATORY"), "doctor", Map.class);
        String forwardedVisitId = (String) order.get("visitId");
        // ... drive the dept-case attachment upload as in CaseAttachmentFinalizedIT
        // (find DepartmentCase by visitId — poll briefly, case creation is event-driven;
        //  then POST multipart PNG to /api/dept-cases/{caseId}/services/{serviceItemId}/attachments as "lab")

        var list = rest.exchange(docsUrl(stay), HttpMethod.GET, new HttpEntity<>(auth("premature")), List.class);
        List<Map<String, Object>> docs = list.getBody();
        var result = docs.stream().filter(d -> "LABORATORY".equals(d.get("source"))).findFirst().orElseThrow();
        assertThat(result.get("fileName")).isEqualTo("result.png");
        String fileUrl = (String) result.get("fileUrl");

        var img = rest.exchange(fileUrl, HttpMethod.GET, new HttpEntity<>(auth("premature")), byte[].class);
        assertThat(img.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(img.getBody()).isEqualTo(PNG);
    }

    @Test
    void result_attachment_of_another_stay_is_not_streamable() {
        // two stays; attachment belongs to stay A's order; stream via stay B's URL -> 404
        String stayA = admitUnderCare();
        String stayB = admitUnderCare();
        // ... create LAB order + attachment on stayA exactly as above; capture attachmentId
        var denied = rest.exchange(docsUrl(stayB) + "/results/" + attachmentId + "/file",
                HttpMethod.GET, new HttpEntity<>(auth("premature")), String.class);
        assertThat(denied.getStatusCode().value()).isEqualTo(404);
    }
```
(The `// ...` sections must be REAL code in the actual test — derive them from CaseAttachmentFinalizedIT; they are elided here only because that file is the authoritative template. If the dept-case is created synchronously on order, drop the polling.)

- [ ] **Step 2: Run to verify the new tests fail**

Same IT command. Expected: new tests fail (no `source=LABORATORY` rows; `/results/...` 404s for the wrong reason — endpoint absent). Existing 2 pass.

- [ ] **Step 3: Port extension**

`StayOrderRef.java`:
```java
package com.albudoor.hms.bedstayforms.directory;

import java.time.Instant;
import java.util.UUID;

/** A forwarded order placed from a stay: the child visit + where it went. */
public record StayOrderRef(UUID visitId, String targetType, Instant orderedAt) {}
```
`StayDirectory.java` — add:
```java
    /** Forwarded Lab/Radiology/ECO orders placed from this stay (child visits). */
    java.util.List<StayOrderRef> orders(UUID stayId);
```
Both impls — premature shown; emergency is identical with its case repository (anchor visit id from `EmergencyCase.getVisitId()`):
```java
    @Override
    public List<StayOrderRef> orders(UUID stayId) {
        return admissions.findById(stayId)
                .map(a -> visits.findAllByParentVisitIdOrderByStartedAtDesc(a.getVisitId()).stream()
                        .map(v -> new StayOrderRef(v.getId(), v.getVisitType().name(), v.getStartedAt()))
                        .toList())
                .orElse(List.of());
    }
```
(Each impl gains a `VisitRepository visits` constructor dependency — `com.albudoor.hms.visitmanagement.infrastructure.VisitRepository`, already a module dependency; the finder `findAllByParentVisitIdOrderByStartedAtDesc` already exists — it's what `ListOrdersController` uses. Verify `Visit` getter names there.)

- [ ] **Step 4: Merge results into the list + scoped stream**

`backend/bed-stay-forms/pom.xml` — add:
```xml
        <dependency>
            <groupId>com.albudoor.hms</groupId>
            <artifactId>department-services</artifactId>
        </dependency>
```
`StayDocumentsHandler` — add dependencies `DepartmentCaseRepository deptCases` and `CaseAttachmentRepository caseAttachments` (both from `com.albudoor.hms.departmentservices.infrastructure`), plus `StayDirectoryRegistry` already present. Extend `list(...)`:
```java
        for (StayOrderRef order : stays.directory(dept).orders(stayId)) {
            deptCases.findByVisitId(order.visitId()).ifPresent(c ->
                    caseAttachments.findAllByCaseIdOrderByUploadedAtAsc(c.getId()).forEach(a ->
                            out.add(new StayDocumentDto(a.getId(), order.targetType(), a.getFileName(),
                                    a.getContentType(), a.getSizeBytes(), null, a.getUploadedBy(),
                                    a.getUploadedAt(), false,
                                    base + "results/" + a.getId() + "/file"))));
        }
        out.sort(java.util.Comparator.comparing(StayDocumentDto::uploadedAt).reversed());
        return out;
```
Add to `StayDirectoryRegistry` the accessor used above:
```java
    public StayDirectory directory(StayDepartment department) {
        StayDirectory d = byDepartment.get(department);
        if (d == null) throw new NotFoundException("No bed-stay directory for " + department);
        return d;
    }
```
Add to the handler the scoped resolution used by the stream endpoint:
```java
    @Transactional(readOnly = true)
    public com.albudoor.hms.departmentservices.domain.CaseAttachment requireResultAttachment(
            StayDepartment dept, UUID stayId, UUID attachmentId) {
        stays.require(dept, stayId);
        var attachment = caseAttachments.findById(attachmentId)
                .orElseThrow(() -> new NotFoundException("Attachment not found: " + attachmentId));
        boolean belongs = stays.directory(dept).orders(stayId).stream()
                .map(o -> deptCases.findByVisitId(o.visitId()))
                .flatMap(java.util.Optional::stream)
                .anyMatch(c -> c.getId().equals(attachment.getCaseId()));
        if (!belongs) throw new NotFoundException("Attachment not found on this stay: " + attachmentId);
        return attachment;
    }
```
`StayDocumentsController` — add:
```java
    @GetMapping("/results/{attachmentId}/file")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Resource> resultFile(@PathVariable StayDepartment department,
                                               @PathVariable UUID stayId,
                                               @PathVariable UUID attachmentId) throws IOException {
        access.checkRead(department);
        var a = handler.requireResultAttachment(department, stayId, attachmentId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(a.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + a.getFileName().replace("\"", "_") + "\"")
                .contentLength(a.getSizeBytes())
                .body(new InputStreamResource(storage.open(a.getStorageKey())));
    }
```

- [ ] **Step 5: Run the IT — all 4 pass; ArchUnit still green**

`mvn -pl app -am verify -Dit.test=StayDocumentsIT -Dsurefire.failIfNoSpecifiedTests=false -q` then `mvn -pl app test -Dtest=ArchitectureTest -Dsurefire.failIfNoSpecifiedTests=false -q` (the bed-stay-forms rule only forbids premature/emergency deps — department-services is allowed).

- [ ] **Step 6: Commit**

```bash
git add backend/bed-stay-forms backend/premature backend/emergency backend/app/src/test
git commit -m "feat(bed-stay-forms): merge Lab/Rad/ECO result attachments into stay documents, stay-scoped streaming (closes §13 path)"
```

---

### Task 4: Integrity verification + inventory contributors + ops

**Files:**
- Create: `backend/platform/src/main/java/com/albudoor/hms/platform/storage/inventory/{DocumentRef,DocumentInventoryContributor,StorageVerifyResponse,StorageVerifyHandler,StorageVerifyController}.java`
- Create: `backend/bed-stay-forms/src/main/java/com/albudoor/hms/bedstayforms/inventory/BedStayFormsInventoryContributor.java`
- Create: `backend/department-services/src/main/java/com/albudoor/hms/departmentservices/inventory/DeptServicesInventoryContributor.java`
- Create: `backend/premature/src/main/java/com/albudoor/hms/premature/inventory/PrematureInventoryContributor.java`
- Create: `ops/consolidate-attachments.sh`, `docs/OPERATIONS.md`
- Test: `backend/app/src/test/java/com/albudoor/hms/app/platform/StorageVerifyIT.java`

- [ ] **Step 1: Failing IT**

```java
package com.albudoor.hms.app.platform;

import com.albudoor.hms.app.IntegrationTest;
import com.albudoor.hms.platform.storage.FileStorage;
import com.albudoor.hms.platform.storage.LocalFileSystemStorage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StorageVerifyIT extends IntegrationTest {

    @Autowired TestRestTemplate rest;
    @Value("${hms.attachments.dir:data/attachments}") String attachmentsDir;

    // auth(...) helper as in other ITs

    @Test
    @SuppressWarnings("unchecked")
    void verify_reports_missing_blob_after_file_deleted() throws Exception {
        // Upload a stay document first (reuse the full admit+upload flow from StayDocumentsIT —
        // copy admitUnderCare + pngUpload helpers), capture its storageKey via the repository
        // (inject StayDocumentRepository) — then delete the file on disk:
        // Files.delete(Path.of(attachmentsDir).toAbsolutePath().resolve(storageKey));

        var r = rest.exchange("/api/admin/storage/verify", HttpMethod.POST,
                new HttpEntity<>(auth("admin")), Map.class);
        assertThat(r.getStatusCode().is2xxSuccessful()).isTrue();
        Map<String, Object> body = r.getBody();
        assertThat(((Number) body.get("checked")).intValue()).isGreaterThanOrEqualTo(1);
        List<Map<String, Object>> missing = (List<Map<String, Object>>) body.get("missing");
        assertThat(missing).anySatisfy(m -> assertThat(m.get("storageKey")).isEqualTo(storageKeyOfDeleted));
    }

    @Test
    void verify_is_admin_only() {
        var r = rest.exchange("/api/admin/storage/verify", HttpMethod.POST,
                new HttpEntity<>(auth("doctor")), String.class);
        assertThat(r.getStatusCode().value()).isEqualTo(403);
    }
}
```
(The elided setup must be real code in the actual file, assembled from StayDocumentsIT's helpers.)

- [ ] **Step 2: Verify failure (404), then implement the port**

`DocumentRef.java`:
```java
package com.albudoor.hms.platform.storage.inventory;

/** One DB-referenced blob: where it lives + its recorded hash (null when not recorded). */
public record DocumentRef(String owner, String refId, String storageKey, String sha256) {}
```
`DocumentInventoryContributor.java`:
```java
package com.albudoor.hms.platform.storage.inventory;

import java.util.List;

/** Implemented by modules that store blobs, so the integrity check can walk everything. */
public interface DocumentInventoryContributor {
    List<DocumentRef> documentRefs();
}
```
`StorageVerifyResponse.java`:
```java
package com.albudoor.hms.platform.storage.inventory;

import java.util.List;

public record StorageVerifyResponse(int checked, List<DocumentRef> missing,
                                    List<DocumentRef> corrupt, List<String> orphanedFiles) {}
```
`StorageVerifyHandler.java`:
```java
package com.albudoor.hms.platform.storage.inventory;

import com.albudoor.hms.platform.exception.StorageMissingException;
import com.albudoor.hms.platform.storage.FileStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

@Service
public class StorageVerifyHandler {

    private final List<DocumentInventoryContributor> contributors;
    private final FileStorage storage;
    private final Path root;

    public StorageVerifyHandler(List<DocumentInventoryContributor> contributors, FileStorage storage,
                                @Value("${hms.attachments.dir:data/attachments}") String dir) {
        this.contributors = contributors;
        this.storage = storage;
        this.root = Path.of(dir).toAbsolutePath().normalize();
    }

    public StorageVerifyResponse verify() throws IOException {
        List<DocumentRef> missing = new ArrayList<>();
        List<DocumentRef> corrupt = new ArrayList<>();
        Set<String> referenced = new HashSet<>();
        int checked = 0;
        for (DocumentInventoryContributor c : contributors) {
            for (DocumentRef ref : c.documentRefs()) {
                checked++;
                referenced.add(ref.storageKey());
                try (InputStream in = storage.open(ref.storageKey())) {
                    if (ref.sha256() != null) {
                        MessageDigest md;
                        try {
                            md = MessageDigest.getInstance("SHA-256");
                        } catch (java.security.NoSuchAlgorithmException e) {
                            throw new IllegalStateException(e);
                        }
                        try (var din = new DigestInputStream(in, md)) {
                            din.transferTo(OutputStream.nullOutputStream());
                        }
                        if (!HexFormat.of().formatHex(md.digest()).equals(ref.sha256())) corrupt.add(ref);
                    }
                } catch (StorageMissingException e) {
                    missing.add(ref);
                }
            }
        }
        List<String> orphaned = new ArrayList<>();
        if (Files.isDirectory(root)) {
            try (Stream<Path> walk = Files.walk(root)) {
                walk.filter(Files::isRegularFile)
                        .map(p -> root.relativize(p).toString().replace('\\', '/'))
                        .filter(k -> !referenced.contains(k))
                        .forEach(orphaned::add);
            }
        }
        return new StorageVerifyResponse(checked, missing, corrupt, orphaned);
    }
}
```
(import `java.io.OutputStream`.)
`StorageVerifyController.java`:
```java
package com.albudoor.hms.platform.storage.inventory;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping("/api/admin/storage")
public class StorageVerifyController {

    private final StorageVerifyHandler handler;

    public StorageVerifyController(StorageVerifyHandler handler) {
        this.handler = handler;
    }

    @PostMapping("/verify")
    @PreAuthorize("hasRole('ADMIN')")
    public StorageVerifyResponse verify() throws IOException {
        return handler.verify();
    }
}
```
IMPORTANT: check that platform `@Component`s are scanned by the app (LocalFileSystemStorage is — find the config that scans `com.albudoor.hms.platform` and confirm controllers in platform get registered; if platform has no component-scan including `.storage.inventory`, extend the relevant `@ComponentScan`/auto-config accordingly).

- [ ] **Step 3: Contributors**

`BedStayFormsInventoryContributor.java` (stay documents with sha; signature keys from the three form repos without sha):
```java
package com.albudoor.hms.bedstayforms.inventory;

import com.albudoor.hms.bedstayforms.domain.MhSignatureSlot;
import com.albudoor.hms.bedstayforms.infrastructure.MedicalHistoryRepository;
import com.albudoor.hms.bedstayforms.infrastructure.StayDocumentRepository;
import com.albudoor.hms.bedstayforms.infrastructure.TreatmentChartRepository;
import com.albudoor.hms.platform.storage.inventory.DocumentInventoryContributor;
import com.albudoor.hms.platform.storage.inventory.DocumentRef;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class BedStayFormsInventoryContributor implements DocumentInventoryContributor {

    private final StayDocumentRepository documents;
    private final MedicalHistoryRepository sheets;
    private final TreatmentChartRepository charts;

    public BedStayFormsInventoryContributor(StayDocumentRepository documents,
                                            MedicalHistoryRepository sheets,
                                            TreatmentChartRepository charts) {
        this.documents = documents;
        this.sheets = sheets;
        this.charts = charts;
    }

    @Override
    public List<DocumentRef> documentRefs() {
        List<DocumentRef> out = new ArrayList<>();
        documents.findAll().forEach(d ->
                out.add(new DocumentRef("stay_document", d.getId().toString(), d.getStorageKey(), d.getSha256())));
        sheets.findAll().forEach(s -> {
            for (MhSignatureSlot slot : MhSignatureSlot.values()) {
                var sig = s.signature(slot);
                if (sig.present()) out.add(new DocumentRef("stay_medical_history:" + slot,
                        s.getId().toString(), sig.getImageKey(), null));
            }
        });
        charts.findAll().forEach(c -> {
            if (c.getDoctorSignature().present()) out.add(new DocumentRef("stay_treatment_chart",
                    c.getId().toString(), c.getDoctorSignature().getImageKey(), null));
        });
        return out;
    }
}
```
`DeptServicesInventoryContributor.java` — same shape over `CaseAttachmentRepository.findAll()` (`owner="case_attachment"`, sha null). `PrematureInventoryContributor.java` — over `PrematureFormRepository.findAll()`, the three premature `SignatureSlot` values via `form.signature(slot)` (read `PrematureForm` for the accessor; sha null). Full code per the same pattern — write it out, don't abbreviate.

- [ ] **Step 4: ops files**

`ops/consolidate-attachments.sh` (chmod +x):
```bash
#!/usr/bin/env bash
# Consolidates legacy attachment roots into the canonical one. Storage keys are
# yyyy-MM-dd/uuid.ext, so a plain move preserves every DB reference; UUID keys
# make collisions impossible. Run with the backend STOPPED.
set -euo pipefail
CANONICAL="${1:?usage: consolidate-attachments.sh <canonical-dir> [legacy-dir...]}"
shift
LEGACY=("${@:-data/attachments backend/data/attachments backend/app/data/attachments}")
mkdir -p "$CANONICAL"
for legacy in ${LEGACY[@]}; do
  [ -d "$legacy" ] || continue
  [ "$(cd "$legacy" && pwd -P)" = "$(cd "$CANONICAL" && pwd -P)" ] && continue
  echo "Merging $legacy -> $CANONICAL"
  (cd "$legacy" && find . -type f -print0) | while IFS= read -r -d '' f; do
    dest="$CANONICAL/${f#./}"
    mkdir -p "$(dirname "$dest")"
    if [ -e "$dest" ]; then echo "SKIP (exists): $f"; else mv "$legacy/$f" "$dest"; fi
  done
done
echo "Done. Verify with POST /api/admin/storage/verify (ADMIN)."
```
`docs/OPERATIONS.md` — new file with a "Document storage" section: canonical dir via `HMS_ATTACHMENTS_DIR` (absolute path in production), what the startup WARN means, backup expectation (the attachments dir + Postgres together), how to run the consolidation script, and what `/api/admin/storage/verify` returns. Write it as ~30 lines of plain prose; no placeholders.

- [ ] **Step 5: Run IT to verify pass + commit**

`mvn -pl app -am verify -Dit.test='StorageVerifyIT,StayDocumentsIT' -Dsurefire.failIfNoSpecifiedTests=false -q` → all pass.
```bash
git add backend/platform backend/bed-stay-forms backend/department-services backend/premature ops docs/OPERATIONS.md backend/app/src/test
git commit -m "feat(platform): storage integrity verification across modules + consolidation script + ops doc"
```

---

### Task 5: Frontend — DocumentPreview, documents API, DocumentsTab, wiring + i18n

**Files:**
- Create: `frontend/src/shared/ui/DocumentPreview.tsx`
- Create: `frontend/src/features/beds/case/forms/documentsApi.ts`
- Create: `frontend/src/features/beds/case/forms/DocumentsTab.tsx`
- Modify: `frontend/src/features/beds/case/BedStayCasePage.tsx` (ExtraTab gains optional `count`)
- Modify: `frontend/src/features/premature/PrematureCasePage.tsx`, `frontend/src/features/emergency/EmergencyCasePage.tsx`
- Modify: `frontend/src/shared/i18n/locales/en.ts`, `frontend/src/shared/i18n/locales/ar.ts`

- [ ] **Step 1: documentsApi.ts**

```ts
import { api } from '@/shared/api/client';
import type { StayDepartment } from './api';

export type DocumentSource = 'UPLOAD' | 'LABORATORY' | 'RADIOLOGY' | 'ECO';

export type StayDoc = {
  id: string;
  source: DocumentSource;
  fileName: string;
  contentType: string;
  sizeBytes: number;
  label: string | null;
  uploadedBy: string | null;
  uploadedAt: string;
  archived: boolean;
  fileUrl: string;
};

export async function listDocuments(dept: StayDepartment, stayId: string): Promise<StayDoc[]> {
  const res = await api.get(`/bed-stays/${dept}/${stayId}/documents`);
  return res.data;
}

export async function uploadDocument(
  dept: StayDepartment, stayId: string, file: File, label?: string,
): Promise<StayDoc> {
  const fd = new FormData();
  fd.append('file', file);
  if (label) fd.append('label', label);
  const res = await api.post(`/bed-stays/${dept}/${stayId}/documents`, fd,
    { headers: { 'Content-Type': 'multipart/form-data' } });
  return res.data;
}

export async function archiveDocument(dept: StayDepartment, stayId: string, id: string): Promise<StayDoc> {
  const res = await api.post(`/bed-stays/${dept}/${stayId}/documents/${id}/archive`, {});
  return res.data;
}

/** fileUrl from the API includes the /api prefix; the axios client baseURL is /api — strip it. */
export async function fetchDocumentBlobUrl(fileUrl: string): Promise<string> {
  const path = fileUrl.startsWith('/api/') ? fileUrl.slice(4) : fileUrl;
  const res = await api.get(path, { responseType: 'blob' });
  return URL.createObjectURL(res.data);
}
```

- [ ] **Step 2: DocumentPreview.tsx (shared — Part 2 reuses it)**

```tsx
import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { X as XIcon, Download } from 'lucide-react';
import { fetchDocumentBlobUrl } from '@/features/beds/case/forms/documentsApi';

export function DocumentPreview({ fileUrl, fileName, contentType, onClose }: {
  fileUrl: string; fileName: string; contentType: string; onClose: () => void;
}) {
  const { t } = useTranslation();
  const [blobUrl, setBlobUrl] = useState<string | null>(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    let url: string | null = null;
    fetchDocumentBlobUrl(fileUrl)
      .then((u) => { url = u; setBlobUrl(u); })
      .catch(() => setFailed(true));
    return () => { if (url) URL.revokeObjectURL(url); };
  }, [fileUrl]);

  const isImage = contentType.startsWith('image/');
  const isPdf = contentType === 'application/pdf';

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-ink-900/60 p-4" data-testid="doc-preview">
      <div className="flex max-h-[90vh] w-full max-w-3xl flex-col rounded-lg bg-white shadow-xl">
        <div className="flex items-center justify-between border-b border-ink-100 px-4 py-2.5">
          <span className="truncate text-sm font-medium text-ink-900">{fileName}</span>
          <div className="flex items-center gap-2">
            {blobUrl && (
              <a href={blobUrl} download={fileName} data-testid="doc-download"
                className="inline-flex items-center gap-1 rounded-md border border-ink-200 px-2 py-1 text-xs hover:bg-ink-50">
                <Download size={13} /> {t('caseView.documents.download')}
              </a>
            )}
            <button onClick={onClose} data-testid="doc-preview-close"
              className="rounded-md p-1 text-ink-500 hover:bg-ink-50"><XIcon size={16} /></button>
          </div>
        </div>
        <div className="min-h-48 flex-1 overflow-auto p-3">
          {failed && <p className="p-6 text-center text-sm text-rose-600">{t('caseView.documents.loadFailed')}</p>}
          {!failed && !blobUrl && <p className="p-6 text-center text-sm text-ink-500">{t('common.loading')}</p>}
          {blobUrl && isImage && <img src={blobUrl} alt={fileName} className="mx-auto max-h-[70vh] object-contain" />}
          {blobUrl && isPdf && <iframe src={blobUrl} title={fileName} className="h-[70vh] w-full rounded border border-ink-100" />}
          {blobUrl && !isImage && !isPdf && (
            <p className="p-6 text-center text-sm text-ink-500">{t('caseView.documents.noPreview')}</p>
          )}
        </div>
      </div>
    </div>
  );
}
```

- [ ] **Step 3: DocumentsTab.tsx**

```tsx
import { useRef, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import toast from 'react-hot-toast';
import { FileText, Image as ImageIcon, Upload, Archive, Eye } from 'lucide-react';
import { extractApiError } from '@/shared/api/client';
import { DocumentPreview } from '@/shared/ui/DocumentPreview';
import { archiveDocument, listDocuments, uploadDocument, type StayDoc } from './documentsApi';
import type { StayDepartment } from './api';

const SOURCE_TONE: Record<string, string> = {
  UPLOAD: 'bg-ink-100 text-ink-700',
  LABORATORY: 'bg-emerald-50 text-emerald-700',
  RADIOLOGY: 'bg-sky-50 text-sky-700',
  ECO: 'bg-violet-50 text-violet-700',
};

function fmtSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

export function DocumentsTab({ department, stayId, readOnly }: {
  department: StayDepartment; stayId: string; readOnly: boolean;
}) {
  const { t } = useTranslation();
  const qc = useQueryClient();
  const queryKey = ['stay-docs', department, stayId];
  const { data: docs, isLoading } = useQuery({ queryKey, queryFn: () => listDocuments(department, stayId) });

  const fileRef = useRef<HTMLInputElement>(null);
  const [label, setLabel] = useState('');
  const [showArchived, setShowArchived] = useState(false);
  const [preview, setPreview] = useState<StayDoc | null>(null);

  const upload = useMutation({
    mutationFn: (file: File) => uploadDocument(department, stayId, file, label.trim() || undefined),
    onSuccess: () => {
      toast.success(t('caseView.documents.uploaded'));
      setLabel('');
      if (fileRef.current) fileRef.current.value = '';
      qc.invalidateQueries({ queryKey });
    },
    onError: (e) => toast.error(extractApiError(e)?.message ?? t('caseView.error')),
  });

  const archive = useMutation({
    mutationFn: (id: string) => archiveDocument(department, stayId, id),
    onSuccess: () => { toast.success(t('caseView.documents.archived')); qc.invalidateQueries({ queryKey }); },
    onError: (e) => toast.error(extractApiError(e)?.message ?? t('caseView.error')),
  });

  if (isLoading) return <div className="p-4 text-sm text-ink-500">{t('common.loading')}</div>;
  const visible = (docs ?? []).filter((d) => showArchived || !d.archived);
  const archivedCount = (docs ?? []).filter((d) => d.archived).length;

  return (
    <div className="space-y-4" data-testid="documents-tab">
      {!readOnly && (
        <div className="flex flex-wrap items-end gap-2 rounded-md border border-ink-100 p-3">
          <label className="text-sm">
            <span className="text-ink-600">{t('caseView.documents.labelField')}</span>
            <input data-testid="doc-label" value={label} onChange={(e) => setLabel(e.target.value)}
              className="mt-1 w-56 rounded-md border border-ink-200 px-2 py-1.5" />
          </label>
          <input ref={fileRef} data-testid="doc-file" type="file" accept="image/*,application/pdf"
            className="text-sm" onChange={(e) => { const f = e.target.files?.[0]; if (f) upload.mutate(f); }} />
          <span className="inline-flex items-center gap-1 text-xs text-ink-400">
            <Upload size={12} /> {t('caseView.documents.policy')}
          </span>
        </div>
      )}

      <ul className="divide-y divide-ink-50" data-testid="doc-rows">
        {visible.map((d) => (
          <li key={d.id} className={`flex items-center gap-3 px-2 py-2.5 ${d.archived ? 'opacity-50' : ''}`}>
            {d.contentType.startsWith('image/')
              ? <ImageIcon size={18} className="shrink-0 text-ink-400" />
              : <FileText size={18} className="shrink-0 text-ink-400" />}
            <div className="min-w-0 flex-1">
              <div className="flex items-center gap-2">
                <span className="truncate text-sm font-medium text-ink-900">{d.fileName}</span>
                <span className={`rounded px-1.5 py-0.5 text-[10px] font-medium ${SOURCE_TONE[d.source]}`}>
                  {t(`caseView.documents.source.${d.source}`)}
                </span>
                {d.label && <span className="truncate text-xs text-ink-500">{d.label}</span>}
              </div>
              <div className="text-xs text-ink-400">
                {new Date(d.uploadedAt).toLocaleString()} · {fmtSize(d.sizeBytes)}
              </div>
            </div>
            <button onClick={() => setPreview(d)} data-testid={`doc-view-${d.fileName}`}
              className="inline-flex items-center gap-1 rounded-md border border-ink-200 px-2 py-1 text-xs hover:bg-ink-50">
              <Eye size={13} /> {t('caseView.documents.view')}
            </button>
            {!readOnly && d.source === 'UPLOAD' && !d.archived && (
              <button data-testid={`doc-archive-${d.fileName}`}
                onClick={() => { if (window.confirm(t('caseView.documents.archiveConfirm'))) archive.mutate(d.id); }}
                className="inline-flex items-center gap-1 rounded-md border border-ink-200 px-2 py-1 text-xs text-ink-500 hover:bg-ink-50">
                <Archive size={13} /> {t('caseView.documents.archive')}
              </button>
            )}
          </li>
        ))}
        {visible.length === 0 && (
          <li className="px-2 py-6 text-center text-sm text-ink-400">{t('caseView.documents.empty')}</li>
        )}
      </ul>

      {archivedCount > 0 && (
        <button data-testid="doc-toggle-archived" onClick={() => setShowArchived((v) => !v)}
          className="text-xs text-ink-500 hover:text-ink-900 hover:underline">
          {showArchived ? t('caseView.documents.hideArchived') : t('caseView.documents.showArchived', { count: archivedCount })}
        </button>
      )}

      {preview && (
        <DocumentPreview fileUrl={preview.fileUrl} fileName={preview.fileName}
          contentType={preview.contentType} onClose={() => setPreview(null)} />
      )}
    </div>
  );
}
```
NOTE: uploads remain possible while a case is open even though `readOnly` only flips on CLOSED/CANCELLED — matches the backend gate.

- [ ] **Step 4: Wire + ExtraTab count**

`BedStayCasePage.tsx`: extend `ExtraTab` to `{ key: string; label: string; content: ReactNode; count?: number }` and pass `count: e.count` when mapping extra tabs into the `tabs` array (the tab button already renders a count badge for builtin order tabs — reuse that rendering).
`PrematureCasePage.tsx` and `EmergencyCasePage.tsx`: append to `extraTabs` after `treatment` (premature: before `caseFile`):
```tsx
    { key: 'documents', label: t('caseView.tabs.documents'),
      content: <DocumentsTab department="PREMATURE" stayId={id!} readOnly={readOnly} /> },
```
(emergency: `department="EMERGENCY"`). Import `DocumentsTab` from `@/features/beds/case/forms/DocumentsTab`.

- [ ] **Step 5: i18n**

`en.ts` — `caseView.tabs` gains `documents: 'Documents'`; new sibling block in `caseView`:
```ts
  documents: {
    labelField: 'Label (optional)',
    policy: 'Images or PDF, up to 20 MB',
    uploaded: 'Document uploaded',
    archived: 'Document archived',
    archive: 'Archive',
    archiveConfirm: 'Archive this document? It will be hidden but never deleted.',
    showArchived: 'Show archived ({{count}})',
    hideArchived: 'Hide archived',
    view: 'View',
    download: 'Download',
    empty: 'No documents yet',
    loadFailed: 'Could not load the document',
    noPreview: 'No preview for this file type — use Download',
    source: { UPLOAD: 'Uploaded', LABORATORY: 'Lab result', RADIOLOGY: 'Radiology', ECO: 'ECO' },
  },
```
`ar.ts` mirror:
```ts
  documents: {
    labelField: 'وصف (اختياري)',
    policy: 'صور أو PDF، بحد أقصى 20 ميغابايت',
    uploaded: 'تم رفع المستند',
    archived: 'تمت أرشفة المستند',
    archive: 'أرشفة',
    archiveConfirm: 'أرشفة هذا المستند؟ سيتم إخفاؤه دون حذفه.',
    showArchived: 'عرض المؤرشفة ({{count}})',
    hideArchived: 'إخفاء المؤرشفة',
    view: 'عرض',
    download: 'تنزيل',
    empty: 'لا توجد مستندات بعد',
    loadFailed: 'تعذر تحميل المستند',
    noPreview: 'لا توجد معاينة لهذا النوع — استخدم التنزيل',
    source: { UPLOAD: 'مرفوع', LABORATORY: 'نتيجة مختبر', RADIOLOGY: 'الأشعة', ECO: 'إيكو' },
  },
```
`tabs`: `documents: 'المستندات'`.

- [ ] **Step 6: Typecheck + commit**

`cd frontend && npx tsc -b` → clean.
```bash
git add frontend/src
git commit -m "feat(bed-stay-ui): Documents tab — merged list, upload, preview dialog, archive (both departments)"
```

---

### Task 6: E2E — case documents journey

**Files:**
- Test: `frontend/e2e/case-documents.spec.ts`

- [ ] **Step 1: Stack up** (db + backend rebuilt with `mvn install -DskipTests` + spring-boot:run + vite, as in previous plans; verify :5173/:8080 respond).

- [ ] **Step 2: Spec** — copy `seedUnderCare` from `frontend/e2e/bed-stay-clinical-forms.spec.ts`. Tests:

```ts
import { test, expect } from '@playwright/test';
import { login } from './helpers/auth';
import { authedContext } from './helpers/api';
import { registerPatient, API_BASE } from './helpers/seeds';
// + seedUnderCare copied verbatim

const PNG_B64 = 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==';

test('upload → tagged list → preview → archive → hidden behind toggle', async ({ page }) => {
  const { admissionId } = await seedUnderCare();
  await login(page, 'premature');
  await page.goto(`/premature/admissions/${admissionId}?tab=documents`);
  await page.getByTestId('doc-label').fill('Statistics form');
  await page.getByTestId('doc-file').setInputFiles({
    name: 'stats.png', mimeType: 'image/png', buffer: Buffer.from(PNG_B64, 'base64'),
  });
  await expect(page.getByTestId('doc-rows')).toContainText('stats.png', { timeout: 10_000 });
  await expect(page.getByTestId('doc-rows')).toContainText('Statistics form');

  await page.getByTestId('doc-view-stats.png').click();
  await expect(page.getByTestId('doc-preview')).toBeVisible();
  await expect(page.getByTestId('doc-preview').locator('img')).toBeVisible({ timeout: 10_000 });
  await page.getByTestId('doc-preview-close').click();

  page.on('dialog', (d) => d.accept());
  await page.getByTestId('doc-archive-stats.png').click();
  await expect(page.getByTestId('doc-rows')).not.toContainText('stats.png', { timeout: 10_000 });
  await page.getByTestId('doc-toggle-archived').click();
  await expect(page.getByTestId('doc-rows')).toContainText('stats.png');
});

test('lab result document appears with LABORATORY badge', async ({ page }) => {
  const { admissionId } = await seedUnderCare();
  // order LAB via API as doctor; upload an attachment to the dept-case as lab via API
  // (find the forwarded visit id from the order response; GET /dept-cases?... or use the
  //  case lookup the backend IT used — mirror StayDocumentsIT's flow over HTTP with authedContext('lab'))
  await login(page, 'premature');
  await page.goto(`/premature/admissions/${admissionId}?tab=documents`);
  await expect(page.getByTestId('doc-rows')).toContainText('result.png', { timeout: 10_000 });
  await expect(page.getByTestId('doc-rows')).toContainText('Lab result');
});
```
(The elided API seeding must be real code — derive the dept-case lookup from how `StayDocumentsIT` does it; if a REST path to find the dept-case by visit id doesn't exist, list pending cases as the lab user via the dept workspace API — read `frontend/src/features/departments/api.ts` for the endpoint.)

- [ ] **Step 3: Run** `npx playwright test case-documents --reporter=line` → 2 passed. Fix selectors/timing if needed; never weaken intent.

- [ ] **Step 4: Commit** `git add frontend/e2e/case-documents.spec.ts && git commit -m "test(e2e): case documents — upload/preview/archive + lab result badge"`

---

### Task 7: HistoryContributor port + full timeline endpoint + IT

**Files:**
- Create: `backend/clinical-case/src/main/java/com/albudoor/hms/clinicalcase/history/{HistoryEntryType,HistoryRefs,HistoryEntry,HistoryContributor}.java`
- Modify: `backend/clinical-case/src/main/java/com/albudoor/hms/clinicalcase/patienthistory/{PatientHistoryResponse,PatientHistoryHandler}.java`
- Create: `backend/premature/src/main/java/com/albudoor/hms/premature/history/PrematureHistoryContributor.java`
- Create: `backend/emergency/src/main/java/com/albudoor/hms/emergency/history/EmergencyHistoryContributor.java`
- Create: `backend/bed-stay-forms/src/main/java/com/albudoor/hms/bedstayforms/history/BedStayFormsHistoryContributor.java`
- Modify: `backend/premature/pom.xml`, `backend/emergency/pom.xml`, `backend/bed-stay-forms/pom.xml` (+`clinical-case`)
- Modify: `backend/app/src/test/java/com/albudoor/hms/app/ArchitectureTest.java`
- Test: `backend/app/src/test/java/com/albudoor/hms/app/clinicalcase/FullHistoryIT.java`

- [ ] **Step 1: Failing IT**

```java
package com.albudoor.hms.app.clinicalcase;

// imports as in other ITs

class FullHistoryIT extends IntegrationTest {

    // auth/post helpers + admitUnderCare copied from BedStayFormsIT (returns admission id;
    //  ALSO capture the patient id from the created patient — adjust the helper to return both)

    @Test @SuppressWarnings("unchecked")
    void timeline_contains_visit_admission_form_and_document_entries() {
        var seeded = admitUnderCareReturningPatient(); // {admissionId, patientId}
        // file a medical history sheet on the stay (PUT as doctor, body {"chiefComplaint":"x"})
        // upload a stay document (pngUpload helper from StayDocumentsIT)

        var r = rest.exchange("/api/patients/" + seeded.patientId() + "/clinical-history",
                HttpMethod.GET, new HttpEntity<>(auth("doctor")), Map.class);
        assertThat(r.getStatusCode().is2xxSuccessful()).isTrue();
        List<Map<String, Object>> timeline = (List<Map<String, Object>>) r.getBody().get("timeline");
        assertThat(timeline).isNotEmpty();
        var types = timeline.stream().map(e -> (String) e.get("type")).toList();
        assertThat(types).contains("VISIT", "ADMISSION", "FORM", "DOCUMENT");
        // newest-first
        var times = timeline.stream().map(e -> java.time.Instant.parse((String) e.get("at"))).toList();
        assertThat(times).isSortedAccordingTo(java.util.Comparator.reverseOrder());
        // existing fields still present (backward compat)
        assertThat(r.getBody().get("entries")).isNotNull();
        assertThat(r.getBody().get("totalVisits")).isNotNull();
    }
}
```
(Elided setup = real code assembled from existing IT helpers.)

- [ ] **Step 2: Verify failure, then the port**

`HistoryEntryType.java`:
```java
package com.albudoor.hms.clinicalcase.history;

public enum HistoryEntryType { VISIT, ADMISSION, EXAM, FORM, DOCUMENT, ORDER }
```
`HistoryRefs.java`:
```java
package com.albudoor.hms.clinicalcase.history;

import java.util.UUID;

public record HistoryRefs(UUID visitId, UUID stayId, UUID documentId, String fileUrl) {
    public static HistoryRefs none() { return new HistoryRefs(null, null, null, null); }
    public static HistoryRefs stay(UUID stayId) { return new HistoryRefs(null, stayId, null, null); }
    public static HistoryRefs visit(UUID visitId) { return new HistoryRefs(visitId, null, null, null); }
    public static HistoryRefs document(UUID documentId, String fileUrl) { return new HistoryRefs(null, null, documentId, fileUrl); }
}
```
`HistoryEntry.java`:
```java
package com.albudoor.hms.clinicalcase.history;

import java.time.Instant;

public record HistoryEntry(Instant at, HistoryEntryType type, String department,
                           String title, String detail, HistoryRefs refs) {}
```
`HistoryContributor.java`:
```java
package com.albudoor.hms.clinicalcase.history;

import java.util.List;
import java.util.UUID;

/** Implemented by modules that own part of a patient's record (same pattern as StayDirectory). */
public interface HistoryContributor {
    List<HistoryEntry> contribute(UUID patientId);
}
```

- [ ] **Step 3: Response + handler**

`PatientHistoryResponse` — add a `timeline` component: `List<HistoryEntry> timeline` appended to the record (import the history package). `PatientHistoryHandler` — inject `List<HistoryContributor> contributors` (may be empty in module-only tests); after building the existing entries, build the timeline:
```java
        List<HistoryEntry> timeline = new ArrayList<>();
        for (HistoryContributor c : contributors) timeline.addAll(c.contribute(patientId));
        // clinical-case's own contributions: visits + finalized exams
        for (Visit v : patientVisits) {
            timeline.add(new HistoryEntry(v.getStartedAt(), HistoryEntryType.VISIT,
                    v.getVisitType().name(), v.getVisitType().name() + " visit " + v.getVisitDisplayId(),
                    v.getResultsSummary(), HistoryRefs.visit(v.getId())));
        }
        for (DoctorExam e : patientExams) {
            if (e.getFinalizedAt() != null) {
                timeline.add(new HistoryEntry(e.getFinalizedAt(), HistoryEntryType.EXAM, "CLINICAL",
                        "Doctor exam finalized", e.getDiagnosis(), HistoryRefs.visit(e.getVisitId())));
            }
        }
        timeline.sort(Comparator.comparing(HistoryEntry::at).reversed());
```
VERIFY field/getter names against the actual `Visit` and `DoctorExam` classes before writing (e.g. exam finalization timestamp + diagnosis accessor — read `backend/clinical-case/.../domain/DoctorExam.java`; adapt titles to real fields, keep the entry types fixed).

- [ ] **Step 4: Contributors**

`PrematureHistoryContributor.java`:
```java
package com.albudoor.hms.premature.history;

import com.albudoor.hms.clinicalcase.history.HistoryContributor;
import com.albudoor.hms.clinicalcase.history.HistoryEntry;
import com.albudoor.hms.clinicalcase.history.HistoryEntryType;
import com.albudoor.hms.clinicalcase.history.HistoryRefs;
import com.albudoor.hms.premature.infrastructure.PrematureAdmissionRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class PrematureHistoryContributor implements HistoryContributor {

    private final PrematureAdmissionRepository admissions;

    public PrematureHistoryContributor(PrematureAdmissionRepository admissions) {
        this.admissions = admissions;
    }

    @Override
    public List<HistoryEntry> contribute(UUID patientId) {
        List<HistoryEntry> out = new ArrayList<>();
        admissions.findAllByPatientIdOrderByAdmittedAtDesc(patientId).forEach(a -> {
            out.add(new HistoryEntry(a.getAdmittedAt(), HistoryEntryType.ADMISSION, "PREMATURE",
                    "Admitted to premature bed " + a.getBedCode(), null, HistoryRefs.stay(a.getId())));
            if (a.getClosedAt() != null) {
                out.add(new HistoryEntry(a.getClosedAt(), HistoryEntryType.ADMISSION, "PREMATURE",
                        "Discharged from premature bed " + a.getBedCode(),
                        a.getDischargeNote(), HistoryRefs.stay(a.getId())));
            }
        });
        return out;
    }
}
```
If `findAllByPatientIdOrderByAdmittedAtDesc` doesn't exist on the repository, add it (derived query; the column exists). `EmergencyHistoryContributor` — identical shape over `EmergencyCaseRepository` (department "EMERGENCY", bed code + discharge note getters per `EmergencyCase`).

`BedStayFormsHistoryContributor.java` — FORM entries (medical history sheet per stay, each treatment chart date, one nursing summary per stay) + DOCUMENT entries (uploads via `findAllByPatientIdOrderByCreatedAtDesc`; result attachments via dept-cases by patient):
```java
package com.albudoor.hms.bedstayforms.history;

import com.albudoor.hms.bedstayforms.infrastructure.MedicalHistoryRepository;
import com.albudoor.hms.bedstayforms.infrastructure.NursingProcedureRepository;
import com.albudoor.hms.bedstayforms.infrastructure.StayDocumentRepository;
import com.albudoor.hms.bedstayforms.infrastructure.TreatmentChartRepository;
import com.albudoor.hms.clinicalcase.history.HistoryContributor;
import com.albudoor.hms.clinicalcase.history.HistoryEntry;
import com.albudoor.hms.clinicalcase.history.HistoryEntryType;
import com.albudoor.hms.clinicalcase.history.HistoryRefs;
import com.albudoor.hms.departmentservices.infrastructure.CaseAttachmentRepository;
import com.albudoor.hms.departmentservices.infrastructure.DepartmentCaseRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class BedStayFormsHistoryContributor implements HistoryContributor {

    private final StayDocumentRepository documents;
    private final DepartmentCaseRepository deptCases;
    private final CaseAttachmentRepository caseAttachments;

    public BedStayFormsHistoryContributor(StayDocumentRepository documents,
                                          DepartmentCaseRepository deptCases,
                                          CaseAttachmentRepository caseAttachments) {
        this.documents = documents;
        this.deptCases = deptCases;
        this.caseAttachments = caseAttachments;
    }

    @Override
    public List<HistoryEntry> contribute(UUID patientId) {
        List<HistoryEntry> out = new ArrayList<>();
        documents.findAllByPatientIdOrderByCreatedAtDesc(patientId).forEach(d ->
                out.add(new HistoryEntry(d.getCreatedAt(), HistoryEntryType.DOCUMENT, d.getDepartment().name(),
                        d.getFileName() + (d.getLabel() != null ? " — " + d.getLabel() : ""),
                        d.isArchived() ? "archived" : null,
                        HistoryRefs.document(d.getId(),
                                "/api/bed-stays/" + d.getDepartment() + "/" + d.getStayId()
                                        + "/documents/" + d.getId() + "/file"))));
        deptCases.findAllByPatientIdOrderByCreatedAtDesc(patientId).forEach(c ->
                caseAttachments.findAllByCaseIdOrderByUploadedAtAsc(c.getId()).forEach(a ->
                        out.add(new HistoryEntry(a.getUploadedAt(), HistoryEntryType.DOCUMENT,
                                c.getCategory() == null ? "RESULTS" : c.getCategory().name(),
                                a.getFileName(), "Result document",
                                HistoryRefs.document(a.getId(),
                                        "/api/dept-cases/attachments/" + a.getId() + "/file")))));
        return out;
    }
}
```
VERIFY `DepartmentCase.getCategory()` (read the entity; if the type/name differs, adapt the department string accordingly). FORM entries: the medical-history/treatment-chart tables key on stayId, not patientId — getting them per patient needs the stay→patient mapping the module doesn't hold. SIMPLIFICATION (allowed): skip per-form timeline entries from bed-stay-forms in this task; the admissions entries (premature/emergency) plus documents cover the timeline's clinical narrative. Note the omission in the commit message; the spec's FORM type still appears via this contributor for stay documents? No — instead: the FORM type is contributed by NOTHING initially except... To keep the IT's `contains("FORM")` honest, add `stayDocuments`-independent FORM support the cheap correct way: `MedicalHistoryRepository.findAll()` is wrong (no patient key). Instead add `patient_id` resolution through StayDirectoryRegistry? Registry needs stayId not patientId.
**Decision (do this):** add FORM entries from the medical-history sheets by joining through StayDocument's pattern — i.e., add a repository method to `MedicalHistoryRepository`:
```java
    @org.springframework.data.jpa.repository.Query(
        "select m from MedicalHistorySheet m where m.stayId in :stayIds")
    List<MedicalHistorySheet> findAllByStayIdIn(java.util.Collection<UUID> stayIds);
```
…and give the contributor the stay ids per patient via the directory port? The directories key by stayId, not patient. SIMPLEST CORRECT PATH: premature/emergency contributors already iterate the patient's admissions — have THEM emit the FORM entries for their own stays by injecting the bed-stay-forms repositories (their modules already depend on bed-stay-forms). In `PrematureHistoryContributor`, add `MedicalHistoryRepository sheets` + `TreatmentChartRepository charts` + `NursingProcedureRepository nursing` and inside the admissions loop:
```java
            sheets.findByDepartmentAndStayId(StayDepartment.PREMATURE, a.getId()).ifPresent(s ->
                    out.add(new HistoryEntry(s.getUpdatedAt() != null ? s.getUpdatedAt() : s.getCreatedAt(),
                            HistoryEntryType.FORM, "PREMATURE", "Medical history sheet filed",
                            s.getChiefComplaint(), HistoryRefs.stay(a.getId()))));
            charts.findAllByDepartmentAndStayIdOrderByChartDateDesc(StayDepartment.PREMATURE, a.getId()).forEach(tc ->
                    out.add(new HistoryEntry(tc.getCreatedAt(), HistoryEntryType.FORM, "PREMATURE",
                            "Treatment chart — " + tc.getChartDate(), null, HistoryRefs.stay(a.getId()))));
            var rows = nursing.findAllByDepartmentAndStayIdOrderByPerformedAtDescCreatedAtDesc(StayDepartment.PREMATURE, a.getId());
            if (!rows.isEmpty()) {
                out.add(new HistoryEntry(rows.get(0).getPerformedAt(), HistoryEntryType.FORM, "PREMATURE",
                        "Nursing procedures — " + rows.size() + " recorded", null, HistoryRefs.stay(a.getId())));
            }
```
(same in Emergency with EMERGENCY). Then `BedStayFormsHistoryContributor` contributes ONLY document entries — rename mentally accordingly but keep the class (its document logic stands).

- [ ] **Step 5: Poms + ArchUnit**

`premature/pom.xml`, `emergency/pom.xml`, `bed-stay-forms/pom.xml`: add `clinical-case` dependency block. ArchitectureTest — add:
```java
    @Test
    void clinicalCaseDoesNotDependOnDepartmentOrFormsModules() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..clinicalcase..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("..premature..", "..emergency..", "..bedstayforms..");
        rule.check(CLASSES);
    }
```

- [ ] **Step 6: Run** `mvn -pl app -am verify -Dit.test=FullHistoryIT -Dsurefire.failIfNoSpecifiedTests=false -q` → PASS; ArchitectureTest → all green; ALSO re-run the doctor-flow IT that consumes clinical-history (`grep -rln "clinical-history" backend/app/src/test` — run whatever hits it) to prove backward compatibility.

- [ ] **Step 7: Commit**

```bash
git add backend/clinical-case backend/premature backend/emergency backend/bed-stay-forms backend/app/src/test
git commit -m "feat(clinical-case): HistoryContributor port — unified patient timeline (visits, admissions, forms, documents)"
```

---

### Task 8: Patient profile redesign

**Files:**
- Modify: `frontend/src/features/patients/api.ts` (or wherever `PatientHistory` types live — find with `grep -rn "clinical-history" frontend/src`) — add timeline types
- Create: `frontend/src/features/patients/history/ProfileHeader.tsx`
- Create: `frontend/src/features/patients/history/SummaryChips.tsx`
- Create: `frontend/src/features/patients/history/UnifiedTimeline.tsx`
- Modify: `frontend/src/features/patients/PatientProfilePage.tsx`
- Modify: `frontend/src/shared/i18n/locales/{en,ar}.ts`

The profile page is 963 lines — work surgically. READ IT FULLY FIRST. Constraints: keep ALL existing actions (start visit, VIP toggle, archive, print + the print summary markup) and the existing exam-detail rendering; replace the page's header block with `ProfileHeader` and the existing history/visits list section with `UnifiedTimeline`; insert `SummaryChips` between them. Existing testids and visible texts used by e2e (`grep -n "patient" frontend/e2e/*.spec.ts` for selectors touching the profile — at minimum `vip-toggle.spec.ts`, `brd-followup-fixes.spec.ts` archive test, `premature-detail.spec.ts` patient link) MUST keep working — list the selectors before editing.

- [ ] **Step 1: Types**

```ts
export type HistoryEntryType = 'VISIT' | 'ADMISSION' | 'EXAM' | 'FORM' | 'DOCUMENT' | 'ORDER';
export type TimelineEntry = {
  at: string;
  type: HistoryEntryType;
  department: string;
  title: string;
  detail: string | null;
  refs: { visitId: string | null; stayId: string | null; documentId: string | null; fileUrl: string | null };
};
```
added to the history response type (field `timeline: TimelineEntry[]`).

- [ ] **Step 2: ProfileHeader.tsx** — identity card: avatar circle (initials), name + MRN + VIP badge + archived badge, chips row for gender / age (derive from dateOfBirth like the page already does — reuse its helper if present) / mobile-or-guardian. Accepts `{ patient }` typed from the existing PatientResponse. Keep markup compact (~70 lines), tone `bg-white rounded-lg border border-ink-100 p-4`, testid `profile-header`.

- [ ] **Step 3: SummaryChips.tsx** — `{ history }` prop; chips: visits (`history.totalVisits`), admissions (count of timeline ADMISSION entries with "Admitted" — count unique stayIds), documents (count of DOCUMENT entries), last seen (max `at`). Testids `chip-visits`, `chip-admissions`, `chip-documents`, `chip-lastseen`. ~50 lines.

- [ ] **Step 4: UnifiedTimeline.tsx** — `{ timeline, renderExamFor }` props (`renderExamFor?: (visitId: string) => ReactNode` lets the page keep its existing exam expansion for VISIT entries). Filter pills All/Visits/Admissions/Forms/Documents (testid `timeline-filter-<key>`, counts in the pill), entries as cards (testid `timeline-entry-<type>` on each) with: time, department color dot (PREMATURE → `bg-brand-500`, EMERGENCY → `bg-amber-500`, else `bg-ink-400`), title, optional detail, and per-type affordances — ADMISSION links to `/premature/admissions/{stayId}` or `/emergency/cases/{stayId}` by department; VISIT renders `renderExamFor(visitId)` when provided; DOCUMENT renders a View button opening `DocumentPreview` (import from `@/shared/ui/DocumentPreview`; pass `fileUrl` from refs, fileName from title, contentType guessed: if title ends with .pdf → application/pdf else image/*; ACCEPTABLE simplification — note it). Empty state per filter. ~140 lines.

- [ ] **Step 5: Recompose PatientProfilePage** — minimal diff: import the three components; replace header block; insert chips; replace the visits/history list with `<UnifiedTimeline timeline={history?.timeline ?? []} renderExamFor={...existing exam renderer wrapped...} />`. Preserve print markup and all mutation buttons byte-for-byte.

- [ ] **Step 6: i18n** — `patientProfile.timeline.*` keys (filters all/visits/admissions/forms/documents, empty, lastSeen, chips labels) in en + ar (Arabic: الكل/الزيارات/الرقود/الاستمارات/المستندات, آخر زيارة...). Exact blocks written in both locales.

- [ ] **Step 7: Typecheck + targeted e2e** — `npx tsc -b` clean; run `npx playwright test vip-toggle brd-followup-fixes premature-detail --reporter=line` (stack up) → all pass (these touch the profile page).

- [ ] **Step 8: Commit** `git add frontend/src && git commit -m "feat(patients-ui): full-history profile — identity header, summary chips, unified filterable timeline"`

---

### Task 9: E2E — patient full history

**Files:**
- Test: `frontend/e2e/patient-full-history.spec.ts`

- [ ] **Step 1: Spec** — seed: premature admission (seedUnderCare) + file a medical-history sheet via API (PUT as doctor) + upload a stay document via API; then `login(page, 'doctor')`, goto `/patients/{patientId}`:
```ts
test('profile shows header, chips and a filterable timeline with documents', async ({ page }) => {
  // ...seed as above; capture patientId from registerPatient
  await login(page, 'doctor');
  await page.goto(`/patients/${patientId}`);
  await expect(page.getByTestId('profile-header')).toContainText(patientName);
  await expect(page.getByTestId('chip-visits')).toBeVisible();
  await expect(page.getByTestId('chip-documents')).toContainText('1');

  await page.getByTestId('timeline-filter-documents').click();
  await expect(page.getByTestId('doc-rows').or(page.locator('[data-testid^="timeline-entry-"]')).first()).toBeVisible();
  await expect(page.locator('[data-testid="timeline-entry-DOCUMENT"]')).toContainText('stats.png');

  await page.getByTestId('timeline-filter-admissions').click();
  await page.locator('[data-testid="timeline-entry-ADMISSION"] a').first().click();
  await expect(page).toHaveURL(/premature\/admissions/);
});
```
(Adapt selectors to the components as built; the testids above are the contract from Task 8.)

- [ ] **Step 2: Run** `npx playwright test patient-full-history --reporter=line` → 1 passed.

- [ ] **Step 3: Commit** `git add frontend/e2e/patient-full-history.spec.ts && git commit -m "test(e2e): patient profile full-history timeline with documents"`

---

### Task 10: Full verification

- [ ] **Step 1:** `cd backend && JAVA_HOME=/usr/lib/jvm/java-25-openjdk mvn verify -q` → BUILD SUCCESS (all units + ITs + ArchUnit).
- [ ] **Step 2:** `cd frontend && npm run build` → clean.
- [ ] **Step 3:** restart the dev backend (it must include all new commits: kill old spring-boot:run, `mvn install -DskipTests -q`, relaunch), then `npx playwright test --reporter=line` → ALL pass (was 84 specs before this plan; now 84 + 3 new files).
- [ ] **Step 4:** `git status` sane; report results honestly (verification-before-completion).
