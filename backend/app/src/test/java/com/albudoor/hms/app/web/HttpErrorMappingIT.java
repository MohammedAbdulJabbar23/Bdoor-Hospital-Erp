package com.albudoor.hms.app.web;

import com.albudoor.hms.app.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GlobalExceptionHandler HTTP mappings for unmapped routes and wrong verbs. These used to fall
 * through to the catch-all and return 500; now they return clean 405/404 JSON errors.
 */
class HttpErrorMappingIT extends IntegrationTest {

    @Autowired TestRestTemplate rest;

    private HttpHeaders auth(String user) {
        var login = rest.postForEntity("/api/auth/login",
                Map.of("username", user, "password", user), Map.class);
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth((String) login.getBody().get("token"));
        return h;
    }

    @Test
    void wrong_http_method_on_existing_endpoint_returns_405_method_not_allowed() {
        // /api/visits supports GET (list) and POST (create) but not DELETE.
        ResponseEntity<Map> res = rest.exchange("/api/visits", HttpMethod.DELETE,
                new HttpEntity<>(auth("admin")), Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(res.getBody().get("code")).isEqualTo("METHOD_NOT_ALLOWED");
    }

    @Test
    void unmapped_path_returns_404_not_found() {
        ResponseEntity<Map> res = rest.exchange("/api/this-endpoint-does-not-exist", HttpMethod.GET,
                new HttpEntity<>(auth("admin")), Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(res.getBody().get("code")).isEqualTo("NOT_FOUND");
    }

    @Test
    void existing_endpoint_with_correct_method_still_works() {
        ResponseEntity<Map> res = rest.exchange("/api/visits/summary", HttpMethod.GET,
                new HttpEntity<>(auth("receptionist")), Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
