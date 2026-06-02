package com.albudoor.hms.app.clinical;

import com.albudoor.hms.app.IntegrationTest;
import com.albudoor.hms.cashier.domain.PaymentStatus;
import com.albudoor.hms.cashier.infrastructure.PaymentRepository;
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
}
