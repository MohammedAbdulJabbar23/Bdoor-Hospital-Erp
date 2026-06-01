package com.albudoor.hms.app.emergency;

import com.albudoor.hms.app.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EmergencyAuthzIT extends IntegrationTest {

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

    @Test
    void nurse_cannot_admit_patient() {
        Map<String, Object> body = Map.of(
                "visitId", UUID.randomUUID(),
                "bedId", UUID.randomUUID(),
                "serviceItemId", UUID.randomUUID(),
                "stayValue", 6,
                "stayUnit", "HOURS");
        var res = rest.exchange("/api/emergency/cases", HttpMethod.POST,
                new HttpEntity<>(body, auth("nurse")), String.class);
        assertThat(res.getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void lab_cannot_create_bed() {
        Map<String, Object> body = Map.of("code", "EMRG-99", "room", "ER");
        var res = rest.exchange("/api/emergency/beds", HttpMethod.POST,
                new HttpEntity<>(body, auth("lab")), String.class);
        assertThat(res.getStatusCode().value()).isEqualTo(403);
    }
}
