package com.albudoor.hms.app.emergency;

import com.albudoor.hms.app.IntegrationTest;
import com.albudoor.hms.cashier.domain.PaymentStatus;
import com.albudoor.hms.cashier.infrastructure.PaymentRepository;
import com.albudoor.hms.emergency.domain.EmergencyBed;
import com.albudoor.hms.emergency.domain.EmergencyCaseStatus;
import com.albudoor.hms.emergency.infrastructure.EmergencyBedRepository;
import com.albudoor.hms.emergency.infrastructure.EmergencyCaseRepository;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Bed-stay orders + results-pending gate + discharge note for the EMERGENCY department —
 * the mirror of {@code PrematureOrdersIT}, against {@code /api/emergency/cases/...} and the
 * {@code UNDER_TREATMENT} status. The final test is a regression guard proving the unchanged
 * doctor "pause-and-wait" forward (DOCTOR_APPOINTMENT → LABORATORY) still pauses to
 * {@code AWAITING_RESULTS} and resumes to {@code IN_PROGRESS} on finalize.
 */
class EmergencyOrdersIT extends IntegrationTest {

    @Autowired TestRestTemplate rest;
    @Autowired EmergencyBedRepository emergencyBeds;
    @Autowired EmergencyCaseRepository cases;
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

    /** POST that does NOT assert success — used to inspect 4xx error bodies / status codes. */
    private ResponseEntity<Map> postRaw(String path, Object body, String user) {
        return rest.exchange(path, HttpMethod.POST,
                new HttpEntity<>(body == null ? Map.of() : body, auth(user)), Map.class);
    }

    private <T> T get(String path, String user, Class<T> type) {
        var res = rest.exchange(path, HttpMethod.GET, new HttpEntity<>(auth(user)), type);
        assertThat(res.getStatusCode().is2xxSuccessful())
                .as("GET %s -> %s : %s", path, res.getStatusCode(), res.getBody()).isTrue();
        return res.getBody();
    }

    // ---- seeding ----

    @SuppressWarnings("unchecked")
    private String anEmergencyServiceId() {
        List<Map<String, Object>> services = get("/api/emergency/services", "emergency", List.class);
        return (String) services.get(0).get("id");
    }

    private String[] admitUnderTreatment() {
        Map<?, ?> patient = post("/api/patients", Map.of(
                "fullName", "Patient Ord " + System.nanoTime(), "gender", "MALE",
                "dateOfBirth", "1990-05-01",
                "mobileNumber", "0773" + (System.nanoTime() % 10_000_000L), "vip", false),
                "receptionist", Map.class);
        Map<?, ?> visit = post("/api/visits",
                Map.of("patientId", patient.get("id"), "visitType", "EMERGENCY"), "receptionist", Map.class);
        String visitId = (String) visit.get("id");
        String bedId = emergencyBeds.save(EmergencyBed.create("EMRG-ORD-" + System.nanoTime(), "IT"))
                .getId().toString();
        String serviceItemId = anEmergencyServiceId();
        Map<?, ?> theCase = post("/api/emergency/cases",
                Map.of("visitId", visitId, "bedId", bedId, "serviceItemId", serviceItemId,
                        "stayValue", 6, "stayUnit", "HOURS"),
                "emergency", Map.class);
        String caseId = (String) theCase.get("id");
        var initial = payments.findAllByVisitIdOrderByCreatedAtDesc(UUID.fromString(visitId)).stream()
                .filter(p -> p.getStatus() == PaymentStatus.PENDING).findFirst().orElseThrow();
        post("/api/payments/" + initial.getId() + "/approve", Map.of("paymentMethod", "CASH"), "cashier", Map.class);
        await().atMost(ofSeconds(5)).untilAsserted(() ->
                assertThat(visits.findById(UUID.fromString(visitId)).get().getStatus())
                        .isEqualTo(VisitStatus.IN_PROGRESS));
        return new String[]{caseId, visitId};
    }

    @SuppressWarnings("unchecked")
    private String aLabServiceItemId() {
        List<Map<String, Object>> items = get("/api/catalogue/items?category=LAB&activeOnly=true", "lab", List.class);
        return (String) items.get(0).get("id");
    }

    /**
     * Drives a forwarded Lab child visit through the real department flow to COMPLETED:
     * opencase → approve REFERRAL payment → upload findings → finalize.
     */
    @SuppressWarnings("unchecked")
    private void driveLabChildToCompleted(String childVisitId) {
        String serviceItemId = aLabServiceItemId();
        Map<String, Object> deptCase = post("/api/dept-cases/open",
                Map.of("category", "LAB", "visitId", childVisitId,
                        "services", List.of(Map.of("serviceItemId", serviceItemId, "quantity", 1))),
                "lab", Map.class);
        String caseId = (String) deptCase.get("id");

        var referral = payments.findAllByVisitIdOrderByCreatedAtDesc(UUID.fromString(childVisitId)).stream()
                .filter(p -> p.getStatus() == PaymentStatus.PENDING).findFirst().orElseThrow();
        post("/api/payments/" + referral.getId() + "/approve", Map.of("paymentMethod", "CASH"), "cashier", Map.class);

        await().atMost(ofSeconds(5)).untilAsserted(() -> {
            Map<String, Object> c = (Map<String, Object>) get("/api/dept-cases/" + caseId, "lab", Map.class);
            assertThat(c.get("status")).isEqualTo("AWAITING_STUDY");
        });

        post("/api/dept-cases/" + caseId + "/findings",
                Map.of("serviceItemId", serviceItemId, "textFindings", "WBC within normal limits"),
                "lab", Map.class);
        post("/api/dept-cases/" + caseId + "/finalize", null, "lab", Map.class);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listOrders(String caseId) {
        return get("/api/emergency/cases/" + caseId + "/orders", "emergency", List.class);
    }

    // ---- scenarios ----

    @Test
    void order_createsForwardedChild_parentStaysInProgress() {
        String[] s = admitUnderTreatment();
        String caseId = s[0], visitId = s[1];

        Map<?, ?> order = post("/api/emergency/cases/" + caseId + "/orders",
                Map.of("targetType", "LABORATORY"), "emergency", Map.class);
        assertThat(order.get("visitType")).isEqualTo("LABORATORY");

        List<Map<String, Object>> orders = listOrders(caseId);
        assertThat(orders).hasSize(1);
        assertThat(orders.get(0).get("visitType")).isEqualTo("LABORATORY");

        assertThat(visits.findById(UUID.fromString(visitId)).get().getStatus())
                .isEqualTo(VisitStatus.IN_PROGRESS);
    }

    @Test
    void finish_blockedWhileOrderOpen() {
        String[] s = admitUnderTreatment();
        String caseId = s[0];

        Map<?, ?> order = post("/api/emergency/cases/" + caseId + "/orders",
                Map.of("targetType", "LABORATORY"), "emergency", Map.class);
        String childDisplayId = (String) order.get("visitDisplayId");

        ResponseEntity<Map> res = postRaw("/api/emergency/cases/" + caseId + "/finish-treatment",
                Map.of("override", false), "emergency");

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(res.getBody().get("code")).isEqualTo("RESULTS_PENDING");
        assertThat((String) res.getBody().get("message")).contains(childDisplayId);
    }

    @Test
    void finish_withOverride_succeedsAndRecordsReason() {
        String[] s = admitUnderTreatment();
        String caseId = s[0];

        post("/api/emergency/cases/" + caseId + "/orders",
                Map.of("targetType", "LABORATORY"), "emergency", Map.class);

        ResponseEntity<Map> blank = postRaw("/api/emergency/cases/" + caseId + "/finish-treatment",
                Map.of("override", true, "overrideReason", "  "), "emergency");
        assertThat(blank.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(blank.getBody().get("code")).isEqualTo("OVERRIDE_REASON_REQUIRED");

        Map<?, ?> result = post("/api/emergency/cases/" + caseId + "/finish-treatment",
                Map.of("override", true, "overrideReason", "will follow"), "emergency", Map.class);
        assertThat(result.get("status")).isEqualTo("AWAITING_DISCHARGE_PAYMENT");
        assertThat(result.get("finishOverrideReason")).isEqualTo("will follow");

        var persisted = cases.findById(UUID.fromString(caseId)).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(EmergencyCaseStatus.AWAITING_DISCHARGE_PAYMENT);
        assertThat(persisted.getFinishOverrideReason()).isEqualTo("will follow");
    }

    @Test
    void finish_afterResultsReturned_needsNoOverride() {
        String[] s = admitUnderTreatment();
        String caseId = s[0], visitId = s[1];

        Map<?, ?> order = post("/api/emergency/cases/" + caseId + "/orders",
                Map.of("targetType", "LABORATORY"), "emergency", Map.class);
        String childVisitId = (String) order.get("visitId");

        driveLabChildToCompleted(childVisitId);

        await().atMost(ofSeconds(5)).untilAsserted(() ->
                assertThat(visits.findById(UUID.fromString(childVisitId)).get().getStatus())
                        .isEqualTo(VisitStatus.COMPLETED));
        assertThat(visits.findById(UUID.fromString(visitId)).get().getStatus())
                .isEqualTo(VisitStatus.IN_PROGRESS);

        Map<?, ?> result = post("/api/emergency/cases/" + caseId + "/finish-treatment",
                Map.of("override", false), "emergency", Map.class);
        assertThat(result.get("status")).isEqualTo("AWAITING_DISCHARGE_PAYMENT");
    }

    @Test
    void dischargeNote_persists() {
        String[] s = admitUnderTreatment();
        String caseId = s[0];

        Map<?, ?> result = post("/api/emergency/cases/" + caseId + "/discharge-note",
                Map.of("note", "Home on feeds"), "emergency", Map.class);
        assertThat(result.get("dischargeNote")).isEqualTo("Home on feeds");

        var persisted = cases.findById(UUID.fromString(caseId)).orElseThrow();
        assertThat(persisted.getDischargeNote()).isEqualTo("Home on feeds");
    }

    @Test
    void order_wrongRole_forbidden() {
        String[] s = admitUnderTreatment();
        String caseId = s[0];

        ResponseEntity<Map> res = postRaw("/api/emergency/cases/" + caseId + "/orders",
                Map.of("targetType", "LABORATORY"), "cashier");
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    /**
     * Regression: the unchanged doctor "pause-and-wait" forward. Forwarding a
     * DOCTOR_APPOINTMENT visit to LABORATORY must still pause the parent to
     * AWAITING_RESULTS, and finalizing the lab case must resume it to IN_PROGRESS.
     */
    @Test
    void doctorForwardToLab_pausesParent_thenResumesOnFinalize() {
        Map<?, ?> patient = post("/api/patients", Map.of(
                "fullName", "Patient Doc " + System.nanoTime(), "gender", "MALE",
                "dateOfBirth", "1990-05-01",
                "mobileNumber", "0774" + (System.nanoTime() % 10_000_000L), "vip", false),
                "receptionist", Map.class);
        Map<?, ?> visit = post("/api/visits",
                Map.of("patientId", patient.get("id"), "visitType", "DOCTOR_APPOINTMENT"), "receptionist", Map.class);
        String parentVisitId = (String) visit.get("id");

        // Drive the doctor visit to IN_PROGRESS via the state machine.
        post("/api/visits/" + parentVisitId + "/transition", Map.of("target", "AWAITING_PAYMENT"), "doctor", Map.class);
        post("/api/visits/" + parentVisitId + "/transition", Map.of("target", "IN_PROGRESS"), "doctor", Map.class);

        // Pausing forward (the doctor path): parent -> AWAITING_RESULTS.
        Map<?, ?> forwarded = post("/api/visits/" + parentVisitId + "/forward",
                Map.of("targetType", "LABORATORY"), "doctor", Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> child = (Map<String, Object>) forwarded.get("child");
        String childVisitId = (String) child.get("id");

        assertThat(visits.findById(UUID.fromString(parentVisitId)).get().getStatus())
                .isEqualTo(VisitStatus.AWAITING_RESULTS);

        driveLabChildToCompleted(childVisitId);

        // Finalize returns results to the paused parent, resuming it to IN_PROGRESS.
        await().atMost(ofSeconds(5)).untilAsserted(() ->
                assertThat(visits.findById(UUID.fromString(parentVisitId)).get().getStatus())
                        .isEqualTo(VisitStatus.IN_PROGRESS));
    }
}
