package com.albudoor.hms.app.prematureorders;

import com.albudoor.hms.app.IntegrationTest;
import com.albudoor.hms.cashier.domain.PaymentStatus;
import com.albudoor.hms.cashier.infrastructure.PaymentRepository;
import com.albudoor.hms.premature.domain.Bed;
import com.albudoor.hms.premature.infrastructure.BedRepository;
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
 * Bed-stay orders + results-pending gate + discharge note for the PREMATURE department.
 *
 * <p>Covers the six plan scenarios: a non-pausing Lab order, the finish-treatment gate
 * (blocked / override / blank-reason), a results-returned finish needing no override
 * (driven through the real department opencase → referral-payment → findings → finalize
 * flow), discharge-note persistence, and the wrong-role 403.
 */
class PrematureOrdersIT extends IntegrationTest {

    @Autowired TestRestTemplate rest;
    @Autowired BedRepository beds;
    @Autowired VisitRepository visits;
    @Autowired PaymentRepository payments;

    // ---- auth + HTTP helpers (copied from the existing premature ITs) ----

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

    private String[] admitUnderCare() {
        Map<?, ?> patient = post("/api/patients", Map.of(
                "fullName", "Baby Ord " + System.nanoTime(), "gender", "FEMALE",
                "dateOfBirth", "2026-05-01",
                "mobileNumber", "0772" + (System.nanoTime() % 10_000_000L), "vip", false),
                "receptionist", Map.class);
        Map<?, ?> visit = post("/api/visits",
                Map.of("patientId", patient.get("id"), "visitType", "PREMATURE"), "receptionist", Map.class);
        String visitId = (String) visit.get("id");
        String bedId = beds.save(Bed.create("PREM-ORD-" + System.nanoTime(), "IT")).getId().toString();
        Map<?, ?> adm = post("/api/premature/admissions",
                Map.of("visitId", visitId, "bedId", bedId, "stayValue", 3, "stayUnit", "DAYS"),
                "premature", Map.class);
        String admissionId = (String) adm.get("id");
        var initial = payments.findAllByVisitIdOrderByCreatedAtDesc(UUID.fromString(visitId)).stream()
                .filter(p -> p.getStatus() == PaymentStatus.PENDING).findFirst().orElseThrow();
        post("/api/payments/" + initial.getId() + "/approve", Map.of("paymentMethod", "CASH"), "cashier", Map.class);
        await().atMost(ofSeconds(5)).untilAsserted(() ->
                assertThat(visits.findById(UUID.fromString(visitId)).get().getStatus())
                        .isEqualTo(VisitStatus.IN_PROGRESS));
        return new String[]{admissionId, visitId};
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

        // The REFERRAL payment for the forwarded visit.
        var referral = payments.findAllByVisitIdOrderByCreatedAtDesc(UUID.fromString(childVisitId)).stream()
                .filter(p -> p.getStatus() == PaymentStatus.PENDING).findFirst().orElseThrow();
        post("/api/payments/" + referral.getId() + "/approve", Map.of("paymentMethod", "CASH"), "cashier", Map.class);

        // The PaymentToCaseBridge fires AFTER_COMMIT, so wait for AWAITING_STUDY before findings.
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
    private List<Map<String, Object>> listOrders(String admissionId) {
        return get("/api/premature/admissions/" + admissionId + "/orders", "premature", List.class);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> admissionView(String admissionId) {
        Map<String, Object> caseView = get("/api/premature/admissions/" + admissionId + "/case", "premature", Map.class);
        return (Map<String, Object>) caseView.get("admission");
    }

    // ---- scenarios ----

    @Test
    void order_createsForwardedChild_parentStaysInProgress() {
        String[] s = admitUnderCare();
        String admissionId = s[0], visitId = s[1];

        Map<?, ?> order = post("/api/premature/admissions/" + admissionId + "/orders",
                Map.of("targetType", "LABORATORY"), "premature", Map.class);
        assertThat(order.get("visitType")).isEqualTo("LABORATORY");

        List<Map<String, Object>> orders = listOrders(admissionId);
        assertThat(orders).hasSize(1);
        assertThat(orders.get(0).get("visitType")).isEqualTo("LABORATORY");

        // The non-pausing forward: the bed-stay parent visit must stay IN_PROGRESS.
        assertThat(visits.findById(UUID.fromString(visitId)).get().getStatus())
                .isEqualTo(VisitStatus.IN_PROGRESS);
    }

    @Test
    void finish_blockedWhileOrderOpen() {
        String[] s = admitUnderCare();
        String admissionId = s[0];

        Map<?, ?> order = post("/api/premature/admissions/" + admissionId + "/orders",
                Map.of("targetType", "LABORATORY"), "premature", Map.class);
        String childDisplayId = (String) order.get("visitDisplayId");

        ResponseEntity<Map> res = postRaw("/api/premature/admissions/" + admissionId + "/finish-treatment",
                Map.of("override", false), "premature");

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(res.getBody().get("code")).isEqualTo("RESULTS_PENDING");
        assertThat((String) res.getBody().get("message")).contains(childDisplayId);
    }

    @Test
    void finish_withOverride_succeedsAndRecordsReason() {
        String[] s = admitUnderCare();
        String admissionId = s[0];

        post("/api/premature/admissions/" + admissionId + "/orders",
                Map.of("targetType", "LABORATORY"), "premature", Map.class);

        // Blank override reason -> 422 OVERRIDE_REASON_REQUIRED.
        ResponseEntity<Map> blank = postRaw("/api/premature/admissions/" + admissionId + "/finish-treatment",
                Map.of("override", true, "overrideReason", "  "), "premature");
        assertThat(blank.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(blank.getBody().get("code")).isEqualTo("OVERRIDE_REASON_REQUIRED");

        // Valid override -> 200, reason recorded, admission awaiting discharge payment.
        Map<?, ?> result = post("/api/premature/admissions/" + admissionId + "/finish-treatment",
                Map.of("override", true, "overrideReason", "will follow"), "premature", Map.class);
        assertThat(result.get("status")).isEqualTo("AWAITING_DISCHARGE_PAYMENT");
        assertThat(result.get("finishOverrideReason")).isEqualTo("will follow");

        Map<String, Object> view = admissionView(admissionId);
        assertThat(view.get("status")).isEqualTo("AWAITING_DISCHARGE_PAYMENT");
        assertThat(view.get("finishOverrideReason")).isEqualTo("will follow");
    }

    @Test
    void finish_afterResultsReturned_needsNoOverride() {
        String[] s = admitUnderCare();
        String admissionId = s[0], visitId = s[1];

        Map<?, ?> order = post("/api/premature/admissions/" + admissionId + "/orders",
                Map.of("targetType", "LABORATORY"), "premature", Map.class);
        String childVisitId = (String) order.get("visitId");

        driveLabChildToCompleted(childVisitId);

        // Child reaches COMPLETED; tolerant return leaves the parent IN_PROGRESS (no exception).
        await().atMost(ofSeconds(5)).untilAsserted(() ->
                assertThat(visits.findById(UUID.fromString(childVisitId)).get().getStatus())
                        .isEqualTo(VisitStatus.COMPLETED));
        assertThat(visits.findById(UUID.fromString(visitId)).get().getStatus())
                .isEqualTo(VisitStatus.IN_PROGRESS);

        // With no open orders, finish without override succeeds.
        Map<?, ?> result = post("/api/premature/admissions/" + admissionId + "/finish-treatment",
                Map.of("override", false), "premature", Map.class);
        assertThat(result.get("status")).isEqualTo("AWAITING_DISCHARGE_PAYMENT");
    }

    @Test
    void dischargeNote_persists() {
        String[] s = admitUnderCare();
        String admissionId = s[0];

        Map<?, ?> result = post("/api/premature/admissions/" + admissionId + "/discharge-note",
                Map.of("note", "Home on feeds"), "premature", Map.class);
        assertThat(result.get("dischargeNote")).isEqualTo("Home on feeds");

        Map<String, Object> view = admissionView(admissionId);
        assertThat(view.get("dischargeNote")).isEqualTo("Home on feeds");
    }

    @Test
    void order_wrongRole_forbidden() {
        String[] s = admitUnderCare();
        String admissionId = s[0];

        ResponseEntity<Map> res = postRaw("/api/premature/admissions/" + admissionId + "/orders",
                Map.of("targetType", "LABORATORY"), "cashier");
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    /**
     * The generic pausing forward must refuse bed-stay parents — they have to use the
     * non-pausing department order flow instead. (FIX M1)
     */
    @Test
    void genericPausingForward_onBedStayVisit_isRejected() {
        String[] s = admitUnderCare();
        String visitId = s[1];

        ResponseEntity<Map> res = postRaw("/api/visits/" + visitId + "/forward",
                Map.of("targetType", "LABORATORY"), "premature");

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(res.getBody().get("code")).isEqualTo("INVALID_FORWARD_SOURCE");
    }
}
