package com.albudoor.hms.app.dashboard;

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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies {@code GET /api/dashboard/summary} returns 200 for an authed user, exposes the
 * full summary shape, and that the numbers reflect freshly-seeded data (a new visit bumps
 * "patients today" and an unapproved premature admission leaves a PENDING payment).
 */
class DashboardSummaryIT extends IntegrationTest {

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

    private <T> T post(String path, Object body, String user, Class<T> type) {
        var res = rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, auth(user)), type);
        assertThat(res.getStatusCode().is2xxSuccessful())
                .as("POST %s -> %s : %s", path, res.getStatusCode(), res.getBody()).isTrue();
        return res.getBody();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getSummary() {
        ResponseEntity<Map> res = rest.exchange("/api/dashboard/summary", HttpMethod.GET,
                new HttpEntity<>(auth("admin")), Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        return res.getBody();
    }

    private long num(Map<String, Object> body, String key) {
        Object v = body.get(key);
        assertThat(v).as("field %s present", key).isInstanceOf(Number.class);
        return ((Number) v).longValue();
    }

    @Test
    void unauthenticated_request_is_rejected() {
        ResponseEntity<Map> res = rest.exchange("/api/dashboard/summary", HttpMethod.GET,
                HttpEntity.EMPTY, Map.class);
        assertThat(res.getStatusCode().is2xxSuccessful()).isFalse();
    }

    @Test
    void returns_summary_reflecting_seeded_data() {
        Map<String, Object> before = getSummary();
        long pendingBefore = num(before, "pendingPayments");

        // Seed a patient + a premature visit (bumps patientsToday) and an admission
        // (creates an unapproved PENDING payment), occupying an active bed.
        Map<?, ?> patient = post("/api/patients", Map.of(
                        "fullName", "Baby Dash " + System.nanoTime(),
                        "gender", "MALE", "dateOfBirth", "2026-05-01",
                        "mobileNumber", "0770" + (System.nanoTime() % 10_000_000L), "vip", false),
                "receptionist", Map.class);
        String patientId = (String) patient.get("id");

        Map<?, ?> visit = post("/api/visits",
                Map.of("patientId", patientId, "visitType", "PREMATURE"), "receptionist", Map.class);
        String visitId = (String) visit.get("id");

        Map<?, ?> bed = post("/api/premature/beds",
                Map.of("code", "PREM-DASH-" + System.nanoTime(), "room", "DASH"), "premature", Map.class);
        String bedId = (String) bed.get("id");

        post("/api/premature/admissions",
                Map.of("visitId", visitId, "bedId", bedId, "stayValue", 3, "stayUnit", "DAYS"),
                "premature", Map.class);

        Map<String, Object> after = getSummary();

        // Shape: all eight fields present and non-negative.
        for (String key : new String[]{
                "patientsToday", "pendingPayments", "bedsOccupied", "bedsTotal",
                "activeQueues", "pendingPaymentsCount", "labResultsAwaiting", "bedsExpiringSoon"}) {
            assertThat(num(after, key)).as("%s non-negative", key).isGreaterThanOrEqualTo(0);
        }

        // Invariants + reflection of the seeded data.
        assertThat(num(after, "patientsToday")).isGreaterThanOrEqualTo(1);
        assertThat(num(after, "pendingPayments")).isGreaterThan(pendingBefore);
        assertThat(num(after, "pendingPaymentsCount")).isEqualTo(num(after, "pendingPayments"));
        assertThat(num(after, "bedsTotal")).isGreaterThanOrEqualTo(num(after, "bedsOccupied"));
        assertThat(num(after, "bedsTotal")).isGreaterThanOrEqualTo(1);
        assertThat(num(after, "activeQueues")).isGreaterThanOrEqualTo(1);
    }
}
