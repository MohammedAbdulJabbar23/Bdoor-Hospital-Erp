package com.albudoor.hms.app.pharmacy;

import com.albudoor.hms.app.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Coverage for {@code GET /api/dispenses/summary} — the server-side pharmacy-queue KPI summary that
 * replaced the client-side {@code .filter().length} over a capped 200-row fetch (which under-counted
 * once dispenses exceeded 200).
 *
 * <p>The shared Postgres container is reused across IT classes, so this asserts on DELTAS against a
 * baseline summary captured up front rather than absolute totals: each seeded state transition must
 * move exactly its status's count, and a freshly given dispense must land in {@code dispensedToday}.
 *
 * <p>OTC walk-in sales are the most direct way to stand a dispense up end-to-end: each creates a
 * PENDING dispense and immediately charges it (→ AWAITING_PAYMENT); approving the linked payment
 * moves it to READY_TO_GIVE; mark-given → DISPENSED (sets {@code givenAt = now}); cancel → CANCELLED.
 */
class PharmacyDispenseSummaryIT extends IntegrationTest {

    @Autowired TestRestTemplate rest;

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

    private <T> ResponseEntity<T> exchange(String path, HttpMethod method, Object body, String user, Class<T> type) {
        return rest.exchange(path, method, new HttpEntity<>(body, auth(user)), type);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> postOk(String path, Object body, String user) {
        ResponseEntity<Map> res = exchange(path, HttpMethod.POST, body, user, Map.class);
        assertThat(res.getStatusCode().is2xxSuccessful())
                .as("POST %s -> %s : %s", path, res.getStatusCode(), res.getBody()).isTrue();
        return res.getBody();
    }

    private String createDrug() {
        Map<String, Object> drug = postOk("/api/catalogue/items", Map.of(
                "category", "DRUG",
                "code", "DRG-" + System.nanoTime(),
                "nameEn", "Summary Drug",
                "fee", 500,
                "currency", "IQD"), "admin");
        return (String) drug.get("id");
    }

    private String createPatient() {
        Map<String, Object> patient = postOk("/api/patients", Map.of(
                "fullName", "Summary Patient " + System.nanoTime(),
                "gender", "FEMALE", "dateOfBirth", "1990-01-01",
                "mobileNumber", "0770" + (System.nanoTime() % 10_000_000L), "vip", false),
                "receptionist");
        return (String) patient.get("id");
    }

    private void receiveBatch(String drugId, int qty) {
        postOk("/api/pharmacy/inventory/batches", Map.of(
                "drugServiceItemId", drugId,
                "batchNo", "B-" + System.nanoTime(),
                "expiryDate", LocalDate.now().plusYears(1).toString(),
                "qty", qty,
                "unitCost", 100,
                "supplier", "IT Supplier"), "pharmacist");
    }

    /** Creates a PENDING dispense already charged to AWAITING_PAYMENT; returns [dispenseId, paymentId]. */
    @SuppressWarnings("unchecked")
    private String[] otcSale(String patientId, String drugId, int qty) {
        Map<String, Object> body = postOk("/api/pharmacy/walk-in-sales", Map.of(
                "patientId", patientId,
                "lines", List.of(Map.of("drugServiceItemId", drugId, "quantity", qty))),
                "pharmacist");
        Map<String, Object> dispense = (Map<String, Object>) body.get("dispense");
        return new String[]{ (String) dispense.get("id"), (String) dispense.get("chargePaymentId") };
    }

    private void approvePayment(String paymentId) {
        postOk("/api/payments/" + paymentId + "/approve", Map.of("paymentMethod", "CASH"), "cashier");
    }

    private void markGiven(String dispenseId) {
        postOk("/api/dispenses/" + dispenseId + "/mark-given", null, "pharmacist");
    }

    private void cancel(String dispenseId) {
        postOk("/api/dispenses/" + dispenseId + "/cancel", Map.of("reason", "summary IT"), "pharmacist");
    }

    private Summary fetchSummary() {
        ResponseEntity<Summary> res = rest.exchange(
                "/api/dispenses/summary", HttpMethod.GET,
                new HttpEntity<>(auth("pharmacist")),
                new ParameterizedTypeReference<Summary>() {});
        assertThat(res.getStatusCode().is2xxSuccessful())
                .as("GET /api/dispenses/summary -> %s : %s", res.getStatusCode(), res.getBody()).isTrue();
        return res.getBody();
    }

    private record Summary(Map<String, Long> byStatus, long dispensedToday) {
        long count(String status) {
            Long v = byStatus == null ? null : byStatus.get(status);
            return v == null ? 0L : v;
        }
    }

    @Test
    void summary_counts_reflect_seeded_dispenses_and_dispensed_today_is_date_scoped() {
        String drugId = createDrug();
        String patientId = createPatient();
        receiveBatch(drugId, 100);

        Summary baseline = fetchSummary();

        // One left AWAITING_PAYMENT.
        otcSale(patientId, drugId, 1);

        // One driven all the way to DISPENSED (sets givenAt = now → counts toward dispensedToday).
        String[] given = otcSale(patientId, drugId, 1);
        approvePayment(given[1]);
        markGiven(given[0]);

        // Two cancelled.
        String[] c1 = otcSale(patientId, drugId, 1);
        String[] c2 = otcSale(patientId, drugId, 1);
        cancel(c1[0]);
        cancel(c2[0]);

        Summary after = fetchSummary();

        // AWAITING_PAYMENT: +1 (the still-charged sale). The other three left this state
        // (one → DISPENSED, two → CANCELLED), so the net delta is exactly +1.
        assertThat(after.count("AWAITING_PAYMENT") - baseline.count("AWAITING_PAYMENT"))
                .as("AWAITING_PAYMENT delta").isEqualTo(1L);

        // DISPENSED: +1, and that same dispense must be reflected in the date-scoped dispensedToday.
        assertThat(after.count("DISPENSED") - baseline.count("DISPENSED"))
                .as("DISPENSED delta").isEqualTo(1L);
        assertThat(after.dispensedToday() - baseline.dispensedToday())
                .as("dispensedToday delta — the just-given dispense is today-scoped").isEqualTo(1L);

        // CANCELLED: +2.
        assertThat(after.count("CANCELLED") - baseline.count("CANCELLED"))
                .as("CANCELLED delta").isEqualTo(2L);

        // dispensedToday can never exceed the all-time DISPENSED total.
        assertThat(after.dispensedToday()).isLessThanOrEqualTo(after.count("DISPENSED"));
    }
}
