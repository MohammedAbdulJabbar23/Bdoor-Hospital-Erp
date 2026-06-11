package com.albudoor.hms.app.clinicalcase;

import com.albudoor.hms.app.IntegrationTest;
import com.albudoor.hms.cashier.domain.PaymentStatus;
import com.albudoor.hms.cashier.infrastructure.PaymentRepository;
import com.albudoor.hms.premature.domain.AdmissionStatus;
import com.albudoor.hms.premature.domain.Bed;
import com.albudoor.hms.premature.infrastructure.BedRepository;
import com.albudoor.hms.premature.infrastructure.PrematureAdmissionRepository;
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

import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class FullHistoryIT extends IntegrationTest {

    /** 1x1 transparent PNG. */
    static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    @Autowired TestRestTemplate rest;
    @Autowired BedRepository beds;
    @Autowired PaymentRepository payments;
    @Autowired PrematureAdmissionRepository admissions;

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

        // a plain outpatient visit — the PREMATURE anchor visit is folded into its ADMISSION entry
        post("/api/visits", Map.of("patientId", seeded.patientId(), "visitType", "DOCTOR_APPOINTMENT"),
                "receptionist", Map.class);

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

    record LabOrder(String visitId, String caseId, String serviceItemId, String attachmentId) {}

    /**
     * Orders LAB from the stay as doctor, opens the dept case as lab and uploads result.png —
     * copied from StayDocumentsIT.orderLabAndUploadResult().
     */
    @SuppressWarnings("unchecked")
    LabOrder orderLabAndUploadResult(String stay) {
        // payment approval flips the admission to UNDER_CARE asynchronously; orders need UNDER_CARE
        await().atMost(ofSeconds(5)).untilAsserted(() ->
                assertThat(admissions.findById(UUID.fromString(stay)).get().getStatus())
                        .isEqualTo(AdmissionStatus.UNDER_CARE));
        var order = post("/api/premature/admissions/" + stay + "/orders",
                Map.of("targetType", "LABORATORY"), "doctor", Map.class);
        String forwardedVisitId = (String) order.get("visitId");

        var item = post("/api/catalogue/items", Map.of("category", "LAB", "code", "CBC-" + System.nanoTime(),
                "nameEn", "Complete Blood Count", "fee", 5000, "currency", "IQD"), "admin", Map.class);
        var deptCase = post("/api/dept-cases/open",
                Map.of("category", "LAB", "visitId", forwardedVisitId,
                        "services", List.of(Map.of("serviceItemId", item.get("id"), "quantity", 1))),
                "lab", Map.class);

        HttpHeaders h = auth("lab");
        h.setContentType(MediaType.MULTIPART_FORM_DATA);
        var filePart = new HttpHeaders();
        filePart.setContentType(MediaType.IMAGE_PNG);
        var form = new LinkedMultiValueMap<String, Object>();
        form.add("file", new HttpEntity<>(new ByteArrayResource(PNG) {
            @Override public String getFilename() { return "result.png"; }
        }, filePart));
        var up = rest.exchange(
                "/api/dept-cases/" + deptCase.get("id") + "/services/" + item.get("id") + "/attachments",
                HttpMethod.POST, new HttpEntity<>(form, h), Map.class);
        assertThat(up.getStatusCode().is2xxSuccessful()).as("%s", up.getBody()).isTrue();
        return new LabOrder(forwardedVisitId, (String) deptCase.get("id"),
                (String) item.get("id"), (String) up.getBody().get("id"));
    }

    void putChart(String stay, String date) {
        var r = rest.exchange("/api/bed-stays/PREMATURE/" + stay + "/treatment-charts/" + date,
                HttpMethod.PUT,
                new HttpEntity<>(Map.of("rows", List.of(Map.of("medicineName", "Amoxicillin"))), auth("doctor")),
                Map.class);
        assertThat(r.getStatusCode().is2xxSuccessful()).as("%s", r.getBody()).isTrue();
    }

    @Test @SuppressWarnings("unchecked")
    void timeline_dedupes_visits_rolls_up_charts_and_uses_stay_scoped_result_urls() {
        var seeded = admitUnderCareReturningPatient();
        LabOrder lab = orderLabAndUploadResult(seeded.admissionId());
        putChart(seeded.admissionId(), "2026-06-10");
        putChart(seeded.admissionId(), "2026-06-11");

        var r = rest.exchange("/api/patients/" + seeded.patientId() + "/clinical-history",
                HttpMethod.GET, new HttpEntity<>(auth("doctor")), Map.class);
        assertThat(r.getStatusCode().is2xxSuccessful()).isTrue();
        List<Map<String, Object>> timeline = (List<Map<String, Object>>) r.getBody().get("timeline");
        List<Map<String, Object>> entries = (List<Map<String, Object>>) r.getBody().get("entries");
        assertThat(timeline).isNotEmpty();

        // legacy entries still carry the forwarded child visit (byte-identical contract)
        String childDisplayId = (String) entries.stream()
                .filter(e -> lab.visitId().equals(e.get("visitId")))
                .findFirst().orElseThrow().get("visitDisplayId");
        assertThat(childDisplayId).isNotBlank();

        var titles = timeline.stream().map(e -> (String) e.get("title")).toList();
        // de-dup: no entry titled with the forwarded visit's display id, no anchor PREMATURE visit entry
        assertThat(titles).noneMatch(t -> t != null && t.contains(childDisplayId));
        assertThat(titles).noneMatch(t -> t != null && t.contains("PREMATURE visit"));

        // chart roll-up: exactly one treatmentCharts entry with params.count == "2"
        var chartEntries = timeline.stream()
                .filter(e -> "treatmentCharts".equals(e.get("kind"))).toList();
        assertThat(chartEntries).hasSize(1);
        assertThat(((Map<String, Object>) chartEntries.get(0).get("params")).get("count")).isEqualTo("2");

        // every entry carries a machine-readable kind
        assertThat(timeline).allMatch(e -> e.get("kind") != null);

        // the result document is exposed on the stay-scoped route
        var resultDoc = timeline.stream()
                .filter(e -> "resultDocument".equals(e.get("kind"))).findFirst().orElseThrow();
        String fileUrl = (String) ((Map<String, Object>) resultDoc.get("refs")).get("fileUrl");
        assertThat(fileUrl).startsWith("/api/bed-stays/PREMATURE/");
    }
}
