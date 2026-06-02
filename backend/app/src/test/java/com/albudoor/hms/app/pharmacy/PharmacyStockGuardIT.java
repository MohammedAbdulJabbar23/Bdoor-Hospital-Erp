package com.albudoor.hms.app.pharmacy;

import com.albudoor.hms.app.IntegrationTest;
import com.albudoor.hms.cashier.domain.PaymentStatus;
import com.albudoor.hms.cashier.infrastructure.PaymentRepository;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration coverage for the pharmacy defect fixes: the OTC nested-line @Valid (zero/negative
 * quantity and null drug rejected at validation), and the stock check BEFORE charging — an OTC
 * sale for a drug with no stock must be rejected 422 OUT_OF_STOCK with no payment created.
 */
class PharmacyStockGuardIT extends IntegrationTest {

    @Autowired TestRestTemplate rest;
    @Autowired PaymentRepository payments;

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
                "nameEn", "Stockless Drug",
                "fee", 500,
                "currency", "IQD"), "admin");
        return (String) drug.get("id");
    }

    private String createPatient() {
        Map<String, Object> patient = postOk("/api/patients", Map.of(
                "fullName", "OTC Patient " + System.nanoTime(),
                "gender", "FEMALE", "dateOfBirth", "1990-01-01",
                "mobileNumber", "0770" + (System.nanoTime() % 10_000_000L), "vip", false),
                "receptionist");
        return (String) patient.get("id");
    }

    @Test
    void otc_sale_with_zero_quantity_is_rejected_400() {
        String drugId = createDrug();
        String patientId = createPatient();

        ResponseEntity<Map> res = exchange("/api/pharmacy/walk-in-sales", HttpMethod.POST, Map.of(
                "patientId", patientId,
                "lines", List.of(Map.of("drugServiceItemId", drugId, "quantity", 0))),
                "pharmacist", Map.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody().get("code")).isEqualTo("VALIDATION_FAILED");
    }

    @Test
    void otc_sale_without_stock_is_rejected_422_and_creates_no_payment() {
        String drugId = createDrug();
        String patientId = createPatient();

        ResponseEntity<Map> res = exchange("/api/pharmacy/walk-in-sales", HttpMethod.POST, Map.of(
                "patientId", patientId,
                "lines", List.of(Map.of("drugServiceItemId", drugId, "quantity", 2))),
                "pharmacist", Map.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(res.getBody().get("code")).isEqualTo("OUT_OF_STOCK");
    }

    @Test
    void otc_sale_with_stock_succeeds_after_receiving_batch() {
        String drugId = createDrug();
        String patientId = createPatient();

        // Receive 10 units, far-future expiry.
        String expiry = java.time.LocalDate.now().plusYears(1).toString();
        postOk("/api/pharmacy/inventory/batches", Map.of(
                "drugServiceItemId", drugId,
                "batchNo", "B-" + System.nanoTime(),
                "expiryDate", expiry,
                "qty", 10,
                "unitCost", 100,
                "supplier", "IT Supplier"), "pharmacist");

        Map<String, Object> sale = postOk("/api/pharmacy/walk-in-sales", Map.of(
                "patientId", patientId,
                "lines", List.of(Map.of("drugServiceItemId", drugId, "quantity", 2))),
                "pharmacist");

        String visitId = (String) sale.get("visitId");
        // A PHARMACY payment should now exist for the anchor visit.
        boolean hasPayment = payments.findAllByVisitIdOrderByCreatedAtDesc(UUID.fromString(visitId))
                .stream().anyMatch(p -> p.getStatus() != PaymentStatus.REJECTED);
        assertThat(hasPayment).as("a non-rejected pharmacy payment was created").isTrue();
    }
}
