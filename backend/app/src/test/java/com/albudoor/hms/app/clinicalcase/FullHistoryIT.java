package com.albudoor.hms.app.clinicalcase;

import com.albudoor.hms.app.IntegrationTest;
import com.albudoor.hms.cashier.domain.PaymentStatus;
import com.albudoor.hms.cashier.infrastructure.PaymentRepository;
import com.albudoor.hms.premature.domain.Bed;
import com.albudoor.hms.premature.infrastructure.BedRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FullHistoryIT extends IntegrationTest {

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

    record Seeded(String admissionId, String patientId) {}

    /** Premature admission driven to UNDER_CARE — copied from StayDocumentsIT.admitUnderCare(), also returns the patient id. */
    @SuppressWarnings("unchecked")
    Seeded admitUnderCareReturningPatient() {
        var patient = post("/api/patients", Map.of("fullName", "Baby FH " + System.nanoTime(), "gender", "MALE",
                "dateOfBirth", "2026-05-01", "mobileNumber", "0773" + (System.nanoTime() % 10_000_000L), "vip", false),
                "receptionist", Map.class);
        var visit = post("/api/visits", Map.of("patientId", patient.get("id"), "visitType", "PREMATURE"), "receptionist", Map.class);
        String visitId = (String) visit.get("id");
        Bed bed = beds.save(Bed.create("FH-" + System.nanoTime(), "IT"));
        var adm = post("/api/premature/admissions",
                Map.of("visitId", visitId, "bedId", bed.getId().toString(), "stayValue", 3, "stayUnit", "DAYS"),
                "premature", Map.class);
        var initial = payments.findAllByVisitIdOrderByCreatedAtDesc(UUID.fromString(visitId)).stream()
                .filter(p -> p.getStatus() == PaymentStatus.PENDING).findFirst().orElseThrow();
        post("/api/payments/" + initial.getId() + "/approve", Map.of("paymentMethod", "CASH"), "cashier", Map.class);
        return new Seeded((String) adm.get("id"), (String) patient.get("id"));
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

    @Test @SuppressWarnings("unchecked")
    void timeline_contains_visit_admission_form_and_document_entries() {
        var seeded = admitUnderCareReturningPatient(); // {admissionId, patientId}

        // file a medical history sheet on the stay (doctor write)
        var mh = rest.exchange("/api/bed-stays/PREMATURE/" + seeded.admissionId() + "/medical-history",
                HttpMethod.PUT, new HttpEntity<>(Map.of("chiefComplaint", "x"), auth("doctor")), Map.class);
        assertThat(mh.getStatusCode().is2xxSuccessful()).as("%s", mh.getBody()).isTrue();

        // upload a stay document
        var up = rest.exchange("/api/bed-stays/PREMATURE/" + seeded.admissionId() + "/documents",
                HttpMethod.POST, pngUpload("nurse", "scan.png", "Statistics form"), Map.class);
        assertThat(up.getStatusCode().is2xxSuccessful()).as("%s", up.getBody()).isTrue();

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
