package com.albudoor.hms.app.clinical;

import com.albudoor.hms.app.IntegrationTest;
import com.albudoor.hms.cashier.infrastructure.PaymentRepository;
import com.albudoor.hms.pharmacy.domain.DispenseLine;
import com.albudoor.hms.pharmacy.domain.PharmacyDispense;
import com.albudoor.hms.pharmacy.infrastructure.PharmacyDispenseRepository;
import com.albudoor.hms.visitmanagement.domain.VisitStatus;
import com.albudoor.hms.visitmanagement.infrastructure.VisitRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Backend defect fixes for the DOCTOR clinical-exam / consultation workflow.
 *
 * <p>Each scenario asserts a newly-hardened behavior AND keeps a paired happy-path assertion
 * so the normal flow (approve consult → IN_PROGRESS → upsert vitals → finalize → dispense)
 * is proven not to regress.
 */
class ClinicalExamDefectsIT extends IntegrationTest {

    @Autowired TestRestTemplate rest;
    @Autowired VisitRepository visits;
    @Autowired PaymentRepository payments;
    @Autowired PharmacyDispenseRepository dispenses;

    // ---- auth + HTTP helpers ----

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

    private ResponseEntity<Map> postRaw(String path, Object body, String user) {
        return rest.exchange(path, HttpMethod.POST,
                new HttpEntity<>(body == null ? Map.of() : body, auth(user)), Map.class);
    }

    private ResponseEntity<Map> putRaw(String path, Object body, String user) {
        return rest.exchange(path, HttpMethod.PUT,
                new HttpEntity<>(body == null ? Map.of() : body, auth(user)), Map.class);
    }

    private <T> T put(String path, Object body, String user, Class<T> type) {
        var res = rest.exchange(path, HttpMethod.PUT,
                new HttpEntity<>(body == null ? Map.of() : body, auth(user)), type);
        assertThat(res.getStatusCode().is2xxSuccessful())
                .as("PUT %s -> %s : %s", path, res.getStatusCode(), res.getBody()).isTrue();
        return res.getBody();
    }

    private <T> T get(String path, String user, Class<T> type) {
        var res = rest.exchange(path, HttpMethod.GET, new HttpEntity<>(auth(user)), type);
        assertThat(res.getStatusCode().is2xxSuccessful())
                .as("GET %s -> %s : %s", path, res.getStatusCode(), res.getBody()).isTrue();
        return res.getBody();
    }

    private ResponseEntity<Map> getRaw(String path, String user) {
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(auth(user)), Map.class);
    }

    // ---- seeding ----

    private String registerPatient() {
        Map<?, ?> patient = post("/api/patients", Map.of(
                "fullName", "Exam Patient " + System.nanoTime(), "gender", "MALE",
                "dateOfBirth", "1990-05-01",
                "mobileNumber", "0772" + (System.nanoTime() % 10_000_000L), "vip", false),
                "receptionist", Map.class);
        return (String) patient.get("id");
    }

    /** Creates a DOCTOR_APPOINTMENT visit; returns the visit id. Status = CREATED. */
    private String newDoctorVisit(String patientId) {
        Map<?, ?> visit = post("/api/visits",
                Map.of("patientId", patientId, "visitType", "DOCTOR_APPOINTMENT"), "receptionist", Map.class);
        return (String) visit.get("id");
    }

    /**
     * Drives a fresh DOCTOR_APPOINTMENT visit to IN_PROGRESS via the visit state machine
     * (CREATED → AWAITING_PAYMENT → IN_PROGRESS), the same pattern EmergencyOrdersIT uses
     * for the doctor pause-and-wait regression. This simulates the consult being approved.
     */
    private String inProgressDoctorVisit() {
        String patientId = registerPatient();
        String visitId = newDoctorVisit(patientId);
        post("/api/visits/" + visitId + "/transition", Map.of("target", "AWAITING_PAYMENT"), "doctor", Map.class);
        post("/api/visits/" + visitId + "/transition", Map.of("target", "IN_PROGRESS"), "doctor", Map.class);
        assertThat(visits.findById(UUID.fromString(visitId)).get().getStatus())
                .isEqualTo(VisitStatus.IN_PROGRESS);
        return visitId;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> findActiveItem(String category) {
        List<Map<String, Object>> items =
                get("/api/catalogue/items?category=" + category + "&activeOnly=true", "admin", List.class);
        return items.stream().filter(i -> i.get("fee") != null).findFirst().orElse(items.get(0));
    }

    private Map<String, Object> examBody(String visitId) {
        Map<String, Object> body = new HashMap<>();
        body.put("visitId", visitId);
        Map<String, Object> vitals = new HashMap<>();
        vitals.put("systolicBp", 120);
        vitals.put("diastolicBp", 80);
        vitals.put("heartRate", 72);
        vitals.put("temperatureC", 36.8);
        body.put("vitals", vitals);
        body.put("chiefComplaint", "Headache");
        body.put("diagnoses", List.of(Map.of("code", "R51", "description", "Headache", "primary", true)));
        return body;
    }

    // ---- Fix #1: payment gate ----

    @Test
    void upsert_blockedWhileAwaitingPayment() {
        String patientId = registerPatient();
        String visitId = newDoctorVisit(patientId);
        // Visit is CREATED / AWAITING_PAYMENT — no approved consult yet.

        ResponseEntity<Map> res = putRaw("/api/exams", examBody(visitId), "doctor");

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(res.getBody().get("code")).isEqualTo("VISIT_NOT_IN_PROGRESS");
    }

    @Test
    void happyPath_inProgress_upsert_finalize_closesVisit() {
        String visitId = inProgressDoctorVisit();
        Map<?, ?> exam = put("/api/exams", examBody(visitId), "doctor", Map.class);
        Map<?, ?> finalized = post("/api/exams/" + exam.get("id") + "/finalize", null, "doctor", Map.class);
        assertThat(finalized.get("status")).isEqualTo("FINALIZED");
        await().atMost(ofSeconds(5)).untilAsserted(() ->
                assertThat(visits.findById(UUID.fromString(visitId)).get().getStatus())
                        .isEqualTo(VisitStatus.COMPLETED));
    }

    @Test
    void finalize_blockedWhenVisitNoLongerInProgress() {
        // Exam created legitimately while IN_PROGRESS...
        String visitId = inProgressDoctorVisit();
        Map<?, ?> exam = put("/api/exams", examBody(visitId), "doctor", Map.class);

        // ...but the visit is then cancelled (terminal) before the doctor finalizes.
        post("/api/visits/" + visitId + "/transition",
                Map.of("target", "CANCELLED", "reason", "patient left"), "doctor", Map.class);

        ResponseEntity<Map> res = postRaw("/api/exams/" + exam.get("id") + "/finalize", null, "doctor");
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(res.getBody().get("code")).isEqualTo("VISIT_NOT_IN_PROGRESS");
    }

    // ---- Fix #2: vitals range validation ----

    @Test
    void upsert_rejectsOutOfRangeVitals_with400() {
        String visitId = inProgressDoctorVisit();
        Map<String, Object> body = examBody(visitId);
        Map<String, Object> vitals = new HashMap<>();
        vitals.put("systolicBp", 999);   // far above the 300 ceiling
        vitals.put("heartRate", 70);
        body.put("vitals", vitals);

        ResponseEntity<Map> res = putRaw("/api/exams", body, "doctor");
        // Bean-validation failure → 400 (not a DB-overflow 409).
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void upsert_acceptsNormalVitals_120_80_36_8() {
        String visitId = inProgressDoctorVisit();
        // examBody() already uses BP 120/80, HR 72, temp 36.8 — must remain valid.
        Map<?, ?> exam = put("/api/exams", examBody(visitId), "doctor", Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> v = (Map<String, Object>) exam.get("vitals");
        assertThat(v.get("systolicBp")).isEqualTo(120);
        assertThat(v.get("diastolicBp")).isEqualTo(80);
        assertThat(v.get("heartRate")).isEqualTo(72);
    }

    // ---- Fix #3: nested validation cascade ----

    @Test
    void upsert_rejectsBlankDiagnosisDescription_with400() {
        String visitId = inProgressDoctorVisit();
        Map<String, Object> body = examBody(visitId);
        Map<String, Object> blank = new HashMap<>();
        blank.put("code", "X");
        blank.put("description", "   "); // @NotBlank must now fire via @Valid cascade
        blank.put("primary", true);
        body.put("diagnoses", List.of(blank));

        ResponseEntity<Map> res = putRaw("/api/exams", body, "doctor");
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void upsert_rejectsBlankPrescriptionDrugName_with400() {
        String visitId = inProgressDoctorVisit();
        Map<String, Object> body = examBody(visitId);
        Map<String, Object> rx = new HashMap<>();
        rx.put("drugName", ""); // @NotBlank must now fire via @Valid cascade
        rx.put("dose", "1 tab");
        body.put("prescriptions", List.of(rx));

        ResponseEntity<Map> res = putRaw("/api/exams", body, "doctor");
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ---- Fix #5: pharmacy bridge must only bill DRUG-category items ----

    private String finalizeExamWithPrescription(String drugServiceItemId, String drugName) {
        String visitId = inProgressDoctorVisit();
        Map<String, Object> body = examBody(visitId);
        Map<String, Object> rx = new HashMap<>();
        rx.put("drugServiceItemId", drugServiceItemId);
        rx.put("drugName", drugName);
        rx.put("dose", "1 tab");
        rx.put("frequency", "BID");
        rx.put("duration", "5d");
        rx.put("route", "PO");
        body.put("prescriptions", List.of(rx));
        Map<?, ?> exam = put("/api/exams", body, "doctor", Map.class);
        post("/api/exams/" + exam.get("id") + "/finalize", null, "doctor", Map.class);
        return (String) exam.get("id");
    }

    @Test
    void bridge_nonDrugCatalogueItem_isInformational_notBilled() {
        // A doctor mistakenly references a LAB catalogue item id as a "drug".
        Map<String, Object> lab = findActiveItem("LAB");
        String examId = finalizeExamWithPrescription((String) lab.get("id"), "Some lab-coded entry");

        await().atMost(ofSeconds(10)).untilAsserted(() -> {
            PharmacyDispense d = dispenses.findByExamId(UUID.fromString(examId)).orElseThrow();
            assertThat(d.getLines()).hasSize(1);
            DispenseLine line = d.getLines().get(0);
            // Non-DRUG item must NOT be priced as a dispensed drug.
            assertThat(line.isBillable()).isFalse();
            assertThat(line.getLineTotal()).isNull();
            assertThat(line.getDrugServiceItemId()).isNull();
        });
    }

    @Test
    void bridge_drugCatalogueItem_isBilled() {
        // Happy path regression: a real DRUG item is still priced/billed.
        Map<String, Object> drug = findActiveItem("DRUG");
        String examId = finalizeExamWithPrescription((String) drug.get("id"), (String) drug.get("nameEn"));

        await().atMost(ofSeconds(10)).untilAsserted(() -> {
            PharmacyDispense d = dispenses.findByExamId(UUID.fromString(examId)).orElseThrow();
            assertThat(d.getLines()).hasSize(1);
            DispenseLine line = d.getLines().get(0);
            assertThat(line.isBillable()).isTrue();
            assertThat(line.getDrugServiceItemId()).isNotNull();
            assertThat(line.getLineTotal()).isNotNull();
        });
    }
}
