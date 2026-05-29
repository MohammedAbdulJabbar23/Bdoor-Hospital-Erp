package com.albudoor.hms.app.premature;

import com.albudoor.hms.app.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class VisitOriginIT extends IntegrationTest {

    @Autowired TestRestTemplate rest;

    private HttpHeaders auth(String user) {
        var login = rest.postForEntity("/api/auth/login",
                Map.of("username", user, "password", user), Map.class);
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setBearerAuth((String) login.getBody().get("token"));
        return h;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> post(String path, Map<String, Object> body, String user) {
        return rest.exchange(path, org.springframework.http.HttpMethod.POST,
                new HttpEntity<>(body, auth(user)), Map.class).getBody();
    }

    @Test
    void origin_defaults_to_returning_but_can_be_set_new() {
        var patient = post("/api/patients", Map.of(
                "fullName", "Origin Test " + System.nanoTime(), "gender", "MALE",
                "dateOfBirth", "1990-01-01",
                "mobileNumber", "0772" + (System.nanoTime() % 10_000_000L), "vip", false), "receptionist");

        var defaulted = post("/api/visits",
                Map.of("patientId", patient.get("id"), "visitType", "PREMATURE"), "receptionist");
        assertThat(defaulted.get("origin")).isEqualTo("DIRECT_RETURNING");

        var explicitNew = post("/api/visits",
                Map.of("patientId", patient.get("id"), "visitType", "PREMATURE", "origin", "DIRECT_NEW"),
                "receptionist");
        assertThat(explicitNew.get("origin")).isEqualTo("DIRECT_NEW");
    }
}
