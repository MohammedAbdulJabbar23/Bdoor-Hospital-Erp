package com.albudoor.hms.app.platform;

import com.albudoor.hms.app.IntegrationTest;
import com.albudoor.hms.bedstayforms.domain.StayDepartment;
import com.albudoor.hms.bedstayforms.infrastructure.StayDocumentRepository;
import com.albudoor.hms.cashier.domain.PaymentStatus;
import com.albudoor.hms.cashier.infrastructure.PaymentRepository;
import com.albudoor.hms.premature.domain.Bed;
import com.albudoor.hms.premature.infrastructure.BedRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class StorageVerifyIT extends IntegrationTest {

    /** 1x1 transparent PNG. */
    static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    @Autowired TestRestTemplate rest;
    @Autowired BedRepository beds;
    @Autowired PaymentRepository payments;
    @Autowired StayDocumentRepository stayDocuments;
    @Value("${hms.attachments.dir:data/attachments}") String attachmentsDir;

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

    /** Premature admission driven to UNDER_CARE — copied from StayDocumentsIT.admitUnderCare(). */
    @SuppressWarnings("unchecked")
    String admitUnderCare() {
        var patient = post("/api/patients", Map.of("fullName", "Baby SV " + System.nanoTime(), "gender", "MALE",
                "dateOfBirth", "2026-05-01", "mobileNumber", "0775" + (System.nanoTime() % 10_000_000L), "vip", false),
                "receptionist", Map.class);
        var visit = post("/api/visits", Map.of("patientId", patient.get("id"), "visitType", "PREMATURE"), "receptionist", Map.class);
        String visitId = (String) visit.get("id");
        Bed bed = beds.save(Bed.create("SV-" + System.nanoTime(), "IT"));
        var adm = post("/api/premature/admissions",
                Map.of("visitId", visitId, "bedId", bed.getId().toString(), "stayValue", 3, "stayUnit", "DAYS"),
                "premature", Map.class);
        var initial = payments.findAllByVisitIdOrderByCreatedAtDesc(UUID.fromString(visitId)).stream()
                .filter(p -> p.getStatus() == PaymentStatus.PENDING).findFirst().orElseThrow();
        post("/api/payments/" + initial.getId() + "/approve", Map.of("paymentMethod", "CASH"), "cashier", Map.class);
        return (String) adm.get("id");
    }

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

    @Test
    @SuppressWarnings("unchecked")
    void verify_reports_missing_blob_after_file_deleted() throws Exception {
        // Upload a stay document, capture its storageKey via the repository, then delete the blob on disk.
        String stay = admitUnderCare();
        var up = rest.exchange("/api/bed-stays/PREMATURE/" + stay + "/documents", HttpMethod.POST,
                pngUpload("nurse", "scan.png", "Integrity probe"), Map.class);
        assertThat(up.getStatusCode().is2xxSuccessful()).as("%s", up.getBody()).isTrue();
        String docId = (String) up.getBody().get("id");
        String storageKeyOfDeleted = stayDocuments
                .findAllByDepartmentAndStayIdOrderByCreatedAtDesc(StayDepartment.PREMATURE, UUID.fromString(stay))
                .stream().filter(d -> d.getId().equals(UUID.fromString(docId)))
                .findFirst().orElseThrow().getStorageKey();
        Files.delete(Path.of(attachmentsDir).toAbsolutePath().resolve(storageKeyOfDeleted));

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
