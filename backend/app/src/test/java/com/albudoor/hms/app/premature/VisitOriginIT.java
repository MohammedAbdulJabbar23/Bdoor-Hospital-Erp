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
    void origin_is_derived_server_side_first_visit_new_then_returning() {
        var patient = post("/api/patients", Map.of(
                "fullName", "Origin Test " + System.nanoTime(), "gender", "MALE",
                "dateOfBirth", "1990-01-01",
                "mobileNumber", "0772" + (System.nanoTime() % 10_000_000L), "vip", false), "receptionist");

        // A brand-new patient's FIRST visit is correctly DIRECT_NEW (was previously, wrongly,
        // always DIRECT_RETURNING). The server derives this from prior-visit history.
        var first = post("/api/visits",
                Map.of("patientId", patient.get("id"), "visitType", "PREMATURE"), "receptionist");
        assertThat(first.get("origin")).isEqualTo("DIRECT_NEW");

        // The SECOND visit for the same patient is DIRECT_RETURNING.
        var second = post("/api/visits",
                Map.of("patientId", patient.get("id"), "visitType", "PREMATURE"), "receptionist");
        assertThat(second.get("origin")).isEqualTo("DIRECT_RETURNING");

        // Client-sent origin is IGNORED for direct creates: even asking for DIRECT_NEW on a
        // patient who already has visits still yields the derived DIRECT_RETURNING.
        var thirdForcedNew = post("/api/visits",
                Map.of("patientId", patient.get("id"), "visitType", "PREMATURE", "origin", "DIRECT_NEW"),
                "receptionist");
        assertThat(thirdForcedNew.get("origin")).isEqualTo("DIRECT_RETURNING");
    }
}
