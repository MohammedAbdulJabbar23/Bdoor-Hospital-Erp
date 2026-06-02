package com.albudoor.hms.app.errorcontract;

import com.albudoor.hms.app.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end contract for the hardened {@link com.albudoor.hms.platform.web.GlobalExceptionHandler}.
 * Drives real HTTP requests against live controllers and asserts that inputs which previously fell
 * through to a raw HTTP 500 now return precise 4xx codes with stable {@code ApiError.code} values
 * and no leaked internals. Authentication uses the dev-seeded users (username == password).
 */
class ErrorContractIT extends IntegrationTest {

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
    private String code(ResponseEntity<Map> res) {
        assertThat(res.getBody()).as("error body present").isNotNull();
        return (String) res.getBody().get("code");
    }

    @Test
    @SuppressWarnings("unchecked")
    void nonUuid_pathParam_yields_400_invalidParameter_not_500() {
        // GET /api/payments/{id} declares @PathVariable UUID id; "not-a-uuid" cannot be coerced.
        ResponseEntity<Map> res = rest.exchange("/api/payments/not-a-uuid", HttpMethod.GET,
                new HttpEntity<>(auth("admin")), Map.class);

        assertThat(res.getStatusCode().value()).isEqualTo(400);
        assertThat(code(res)).isEqualTo("INVALID_PARAMETER");
    }

    @Test
    @SuppressWarnings("unchecked")
    void badEnumValue_inBody_yields_400_malformedRequest_not_500() {
        // POST /api/patients body has a Gender enum; an unknown value triggers a Jackson parse failure
        // surfaced as HttpMessageNotReadableException -> MALFORMED_REQUEST (the accepted enum list is NOT leaked).
        Map<String, Object> body = Map.of(
                "fullName", "Enum Tester",
                "gender", "WIZARD",
                "dateOfBirth", "2000-01-01",
                "vip", false);
        ResponseEntity<Map> res = rest.exchange("/api/patients", HttpMethod.POST,
                new HttpEntity<>(body, auth("receptionist")), Map.class);

        assertThat(res.getStatusCode().value()).isEqualTo(400);
        assertThat(code(res)).isEqualTo("MALFORMED_REQUEST");
        // Must not leak the accepted enum values to the client.
        assertThat((String) res.getBody().get("message")).doesNotContain("MALE", "FEMALE");
    }

    @Test
    @SuppressWarnings("unchecked")
    void malformedJson_body_yields_400_not_500() {
        // Raw, syntactically-broken JSON -> HttpMessageNotReadableException -> MALFORMED_REQUEST.
        HttpHeaders headers = auth("receptionist");
        String brokenJson = "{ \"fullName\": \"Broken\", "; // truncated / invalid JSON
        ResponseEntity<Map> res = rest.exchange("/api/patients", HttpMethod.POST,
                new HttpEntity<>(brokenJson, headers), Map.class);

        assertThat(res.getStatusCode().value()).isEqualTo(400);
        assertThat(code(res)).isEqualTo("MALFORMED_REQUEST");
    }
}
