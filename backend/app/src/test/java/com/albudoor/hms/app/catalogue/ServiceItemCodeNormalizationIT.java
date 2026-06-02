package com.albudoor.hms.app.catalogue;

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
 * Verifies the catalogue add-item code normalisation: the uniqueness check trims the code
 * BEFORE comparing (so " PRB1 " can't bypass the guard → 500) and is case-insensitive (so
 * "prb1" and "PRB1" cannot coexist in one category — both return 409 SERVICE_CODE_EXISTS).
 */
class ServiceItemCodeNormalizationIT extends IntegrationTest {

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

    private ResponseEntity<Map> add(String code) {
        Map<String, Object> body = new HashMap<>(Map.of(
                "category", "LAB",
                "code", code,
                "nameEn", "Test Item",
                "fee", 1000,
                "currency", "IQD"));
        return rest.exchange("/api/catalogue/items", HttpMethod.POST,
                new HttpEntity<>(body, auth("admin")), Map.class);
    }

    @Test
    void trimmed_then_padded_duplicate_is_rejected_409_not_500() {
        String base = "PRB" + (System.nanoTime() % 100_000L);
        assertThat(add(base).getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Same code with surrounding whitespace — must be normalised and rejected as a
        // duplicate (409), not blow up on the DB unique constraint (would be 409 CONSTRAINT
        // or 500). We assert the friendly application-level code.
        ResponseEntity<Map> padded = add("  " + base + "  ");
        assertThat(padded.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(padded.getBody().get("code")).isEqualTo("SERVICE_CODE_EXISTS");
    }

    @Test
    void case_insensitive_duplicate_is_rejected_409() {
        String base = "ABC" + (System.nanoTime() % 100_000L);
        assertThat(add(base.toUpperCase()).getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<Map> lower = add(base.toLowerCase());
        assertThat(lower.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(lower.getBody().get("code")).isEqualTo("SERVICE_CODE_EXISTS");
    }
}
