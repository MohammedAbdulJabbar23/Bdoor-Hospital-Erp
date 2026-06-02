package com.albudoor.hms.app.pharmacy;

import com.albudoor.hms.app.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reservation ("committed stock") coverage for the pharmacy.
 *
 * <p>Stock is not decremented until mark-given, so without a reservation two in-flight
 * dispenses for the same drug could each pass a bare available-stock check yet only the
 * first could actually be handed over (the second fails at the counter after the patient
 * paid). {@code ChargeDispenseHandler} now treats already-in-flight dispenses
 * (AWAITING_PAYMENT + READY_TO_GIVE) as committed and requires
 * {@code available - committedByOthers >= wanted}, rejecting 422 OUT_OF_STOCK before any
 * payment is created. Each OTC walk-in sale creates a PENDING dispense and immediately
 * charges it, so it is the most direct way to stand up an in-flight dispense end-to-end.
 *
 * <p>Reject/cancel/give need no explicit release: a no-longer-in-flight dispense simply
 * stops counting toward committed (natural release).
 */
class PharmacyStockReservationIT extends IntegrationTest {

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
                "nameEn", "Reserved Drug",
                "fee", 500,
                "currency", "IQD"), "admin");
        return (String) drug.get("id");
    }

    private String createPatient() {
        Map<String, Object> patient = postOk("/api/patients", Map.of(
                "fullName", "Reservation Patient " + System.nanoTime(),
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

    @SuppressWarnings("unchecked")
    private ResponseEntity<Map> otcSale(String patientId, String drugId, int qty) {
        return exchange("/api/pharmacy/walk-in-sales", HttpMethod.POST, Map.of(
                "patientId", patientId,
                "lines", List.of(Map.of("drugServiceItemId", drugId, "quantity", qty))),
                "pharmacist", Map.class);
    }

    @SuppressWarnings("unchecked")
    private String dispenseIdOf(Map<String, Object> otcResponse) {
        return (String) ((Map<String, Object>) otcResponse.get("dispense")).get("id");
    }

    @SuppressWarnings("unchecked")
    private String paymentIdOf(Map<String, Object> otcResponse) {
        return (String) ((Map<String, Object>) otcResponse.get("dispense")).get("chargePaymentId");
    }

    @SuppressWarnings("unchecked")
    private String dispenseStatus(String dispenseId) {
        ResponseEntity<Map> res = exchange("/api/dispenses/" + dispenseId, HttpMethod.GET, null, "pharmacist", Map.class);
        return (String) res.getBody().get("status");
    }

    private void approvePayment(String paymentId) {
        postOk("/api/payments/" + paymentId + "/approve", Map.of("paymentMethod", "CASH"), "cashier");
    }

    private void markGiven(String dispenseId) {
        postOk("/api/dispenses/" + dispenseId + "/mark-given", null, "pharmacist");
    }

    private void cancel(String dispenseId) {
        postOk("/api/dispenses/" + dispenseId + "/cancel", Map.of("reason", "test release"), "pharmacist");
    }

    /**
     * Two in-flight dispenses for the same drug whose combined quantity exceeds stock: the
     * first charge succeeds, the second is rejected 422 OUT_OF_STOCK (reserved by the first).
     * After the first is GIVEN, the freed quantity becomes chargeable again.
     */
    @Test
    void second_concurrent_charge_is_blocked_then_released_after_first_is_given() {
        String drugId = createDrug();
        String patientId = createPatient();
        receiveBatch(drugId, 5); // available = 5

        // First OTC sale of 3 → succeeds and is now in-flight (AWAITING_PAYMENT), committing 3.
        ResponseEntity<Map> first = otcSale(patientId, drugId, 3);
        assertThat(first.getStatusCode().is2xxSuccessful())
                .as("first sale of 3 (stock 5) succeeds: %s", first.getBody()).isTrue();
        String firstDispenseId = dispenseIdOf(first.getBody());
        String firstPaymentId = paymentIdOf(first.getBody());

        // Second OTC sale of 3 → available 5 minus 3 committed = 2 < 3 → 422 OUT_OF_STOCK.
        ResponseEntity<Map> second = otcSale(patientId, drugId, 3);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(second.getBody().get("code")).isEqualTo("OUT_OF_STOCK");

        // Release: approve + mark-given the first → DISPENSED, drawing 3 from the batch
        // (remaining 2) and dropping out of the committed set.
        approvePayment(firstPaymentId);
        assertThat(dispenseStatus(firstDispenseId)).isEqualTo("READY_TO_GIVE");
        markGiven(firstDispenseId);
        assertThat(dispenseStatus(firstDispenseId)).isEqualTo("DISPENSED");

        // The freed quantity (2 remaining, nothing committed) is now chargeable.
        ResponseEntity<Map> third = otcSale(patientId, drugId, 2);
        assertThat(third.getStatusCode().is2xxSuccessful())
                .as("sale of 2 after first given (2 remaining) succeeds: %s", third.getBody()).isTrue();
    }

    /**
     * Cancelling the first in-flight dispense releases its committed quantity without an explicit
     * release step — the next charge for the freed quantity succeeds.
     */
    @Test
    void cancelling_first_in_flight_releases_committed_stock() {
        String drugId = createDrug();
        String patientId = createPatient();
        receiveBatch(drugId, 5); // available = 5

        ResponseEntity<Map> first = otcSale(patientId, drugId, 4);
        assertThat(first.getStatusCode().is2xxSuccessful())
                .as("first sale of 4 (stock 5) succeeds: %s", first.getBody()).isTrue();
        String firstDispenseId = dispenseIdOf(first.getBody());

        // available 5 minus 4 committed = 1 < 4 → blocked.
        ResponseEntity<Map> blocked = otcSale(patientId, drugId, 4);
        assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(blocked.getBody().get("code")).isEqualTo("OUT_OF_STOCK");

        // Cancel the first (still AWAITING_PAYMENT) → committed drops to 0, full 5 available again.
        cancel(firstDispenseId);
        assertThat(dispenseStatus(firstDispenseId)).isEqualTo("CANCELLED");

        ResponseEntity<Map> afterRelease = otcSale(patientId, drugId, 5);
        assertThat(afterRelease.getStatusCode().is2xxSuccessful())
                .as("sale of 5 after cancel releases stock: %s", afterRelease.getBody()).isTrue();
    }

    /** Regression guard: a single dispense within available stock still charges fine. */
    @Test
    void single_dispense_within_stock_still_charges() {
        String drugId = createDrug();
        String patientId = createPatient();
        receiveBatch(drugId, 10);

        ResponseEntity<Map> sale = otcSale(patientId, drugId, 4);
        assertThat(sale.getStatusCode().is2xxSuccessful())
                .as("single sale of 4 (stock 10) succeeds: %s", sale.getBody()).isTrue();
        assertThat(((Map<?, ?>) sale.getBody().get("dispense")).get("status"))
                .isEqualTo("AWAITING_PAYMENT");
    }
}
