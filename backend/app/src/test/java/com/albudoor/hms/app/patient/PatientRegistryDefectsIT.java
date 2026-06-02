package com.albudoor.hms.app.patient;

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

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration coverage for the patient-registry defect fixes: national-ID duplicate
 * detection (409), Apgar @Max(10) validation, LIKE-metacharacter escaping in search, and
 * the lenient mobile @Pattern (accepts the e2e 077… data, rejects clearly-bogus values).
 */
class PatientRegistryDefectsIT extends IntegrationTest {

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

    @SuppressWarnings("unchecked")
    private ResponseEntity<Map> registerAdult(Map<String, Object> overrides) {
        Map<String, Object> body = new HashMap<>(Map.of(
                "fullName", "Adult " + System.nanoTime(),
                "gender", "MALE", "dateOfBirth", "1980-01-01", "vip", false));
        body.putAll(overrides);
        return rest.exchange("/api/patients", HttpMethod.POST,
                new HttpEntity<>(body, auth("receptionist")), Map.class);
    }

    @Test
    void e2e_style_mobile_is_accepted() {
        // Mirrors the e2e/test data: "077" + 7 digits = 10 numeric chars.
        ResponseEntity<Map> res = registerAdult(Map.of(
                "mobileNumber", "0771234567"));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void bogus_mobile_is_rejected_400() {
        ResponseEntity<Map> res = registerAdult(Map.of(
                "mobileNumber", "abc-not-a-phone!!!"));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody().get("code")).isEqualTo("VALIDATION_FAILED");
    }

    @Test
    void duplicate_national_id_is_rejected_409() {
        String nid = "NID-" + System.nanoTime();
        ResponseEntity<Map> first = registerAdult(Map.of(
                "nationalId", nid, "mobileNumber", "0770000001"));
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<Map> dup = registerAdult(Map.of(
                "nationalId", nid, "mobileNumber", "0770000002"));
        assertThat(dup.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(dup.getBody().get("code")).isEqualTo("DUPLICATE_PATIENT");
    }

    @Test
    void apgar_over_ten_is_rejected_400() {
        Map<String, Object> body = new HashMap<>(Map.of(
                "gender", "MALE", "dateOfBirth", "2026-05-01",
                "placeOfBirth", "HOSPITAL", "deliveryType", "NORMAL",
                "guardianName", "Guardian X", "motherName", "Mother X",
                "apgar1Min", 11, "vip", false));
        ResponseEntity<Map> res = rest.exchange("/api/patients/infants", HttpMethod.POST,
                new HttpEntity<>(body, auth("receptionist")), Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody().get("code")).isEqualTo("VALIDATION_FAILED");
    }

    @Test
    @SuppressWarnings("unchecked")
    void search_with_percent_does_not_dump_all_patients() {
        // Seed a distinctive patient so the registry is non-empty.
        registerAdult(Map.of("fullName", "Zztop Search Seed " + System.nanoTime(),
                "mobileNumber", "0779999999"));

        ResponseEntity<Map> res = rest.exchange("/api/patients?q=%25", HttpMethod.GET,
                new HttpEntity<>(auth("receptionist")), Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);

        // '%' is now a literal substring, not a wildcard — no patient name contains it,
        // so the page must be empty.
        Number total = (Number) res.getBody().get("totalElements");
        assertThat(total.longValue())
                .as("q=%% should match literal '%%' substrings only (none), not dump everything")
                .isZero();
    }
}
