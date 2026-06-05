package com.albudoor.hms.app.identity;

import com.albudoor.hms.app.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end contract for editing users (PUT /api/users/{id}) and admin password reset
 * (POST /api/users/{id}/password). Drives real HTTP against live controllers with the dev-seeded
 * users (username == password). Tests only create/edit their OWN throwaway users (unique names) and
 * never persist a mutation to a shared seed user, so the shared Postgres container stays clean for
 * sibling IT classes.
 */
class UserAdminIT extends IntegrationTest {

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

    /**
     * Login status via {@link HttpClient} rather than {@link TestRestTemplate}: the JDK
     * HttpURLConnection behind TestRestTemplate cannot read a 401 response to a streamed POST body
     * ("cannot retry due to server authentication"), which is exactly the negative case we assert.
     */
    private int loginStatus(String username, String password) {
        try {
            String json = "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(rest.getRootUri() + "/api/auth/login"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            return HttpClient.newHttpClient()
                    .send(req, HttpResponse.BodyHandlers.discarding())
                    .statusCode();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String adminId() {
        ResponseEntity<Map> me = rest.exchange("/api/users/me", HttpMethod.GET,
                new HttpEntity<>(auth("admin")), Map.class);
        return (String) me.getBody().get("id");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> createReceptionist(String username) {
        Map<String, Object> body = Map.of(
                "username", username, "password", "secret123",
                "fullName", "Temp User", "roles", List.of("RECEPTIONIST"));
        ResponseEntity<Map> res = rest.exchange("/api/users", HttpMethod.POST,
                new HttpEntity<>(body, auth("admin")), Map.class);
        assertThat(res.getStatusCode().value()).as("create user").isEqualTo(201);
        return res.getBody();
    }

    private String freshUsername() {
        return "ua_" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    @SuppressWarnings("unchecked")
    void admin_updates_name_roles_and_active_andItPersists() {
        String id = (String) createReceptionist(freshUsername()).get("id");

        Map<String, Object> update = Map.of(
                "fullName", "Renamed Person",
                "roles", List.of("DOCTOR", "NURSE"),
                "active", false);
        ResponseEntity<Map> res = rest.exchange("/api/users/" + id, HttpMethod.PUT,
                new HttpEntity<>(update, auth("admin")), Map.class);

        assertThat(res.getStatusCode().value()).isEqualTo(200);
        assertThat(res.getBody().get("fullName")).isEqualTo("Renamed Person");
        assertThat((List<String>) res.getBody().get("roles")).containsExactlyInAnyOrder("DOCTOR", "NURSE");
        assertThat(res.getBody().get("active")).isEqualTo(false);

        // Re-fetch proves it persisted, not just echoed.
        ResponseEntity<Map> got = rest.exchange("/api/users/" + id, HttpMethod.GET,
                new HttpEntity<>(auth("admin")), Map.class);
        assertThat(got.getBody().get("active")).isEqualTo(false);
        assertThat((List<String>) got.getBody().get("roles")).containsExactlyInAnyOrder("DOCTOR", "NURSE");

        // Reactivate works.
        Map<String, Object> reactivate = Map.of(
                "fullName", "Renamed Person", "roles", List.of("DOCTOR"), "active", true);
        ResponseEntity<Map> res2 = rest.exchange("/api/users/" + id, HttpMethod.PUT,
                new HttpEntity<>(reactivate, auth("admin")), Map.class);
        assertThat(res2.getBody().get("active")).isEqualTo(true);
    }

    @Test
    void admin_resetsPassword_thenUserLogsInWithNewPasswordAndOldFails() {
        String username = freshUsername();
        String id = (String) createReceptionist(username).get("id");
        assertThat(loginStatus(username, "secret123")).as("old password works before reset").isEqualTo(200);

        ResponseEntity<Void> res = rest.exchange("/api/users/" + id + "/password", HttpMethod.POST,
                new HttpEntity<>(Map.of("newPassword", "brandnew99"), auth("admin")), Void.class);
        assertThat(res.getStatusCode().value()).isEqualTo(204);

        assertThat(loginStatus(username, "brandnew99")).as("new password works").isEqualTo(200);
        assertThat(loginStatus(username, "secret123")).as("old password rejected").isEqualTo(401);
    }

    @Test
    @SuppressWarnings("unchecked")
    void admin_cannotDeactivateSelf_andRemainsActive() {
        String adminId = adminId();
        Map<String, Object> body = Map.of(
                "fullName", "Dr. Ahmed Al-Saadi",
                "roles", List.of("ADMIN", "RECEPTIONIST"),
                "active", false);
        ResponseEntity<Map> res = rest.exchange("/api/users/" + adminId, HttpMethod.PUT,
                new HttpEntity<>(body, auth("admin")), Map.class);

        assertThat(res.getStatusCode().value()).isEqualTo(422);
        assertThat(res.getBody().get("code")).isEqualTo("CANNOT_DEACTIVATE_SELF");
        // The blocked edit must not have partially applied — admin can still log in.
        assertThat(loginStatus("admin", "admin")).isEqualTo(200);
    }

    @Test
    void nonAdmin_cannotUpdateOrResetPassword() {
        String adminId = adminId();
        ResponseEntity<String> put = rest.exchange("/api/users/" + adminId, HttpMethod.PUT,
                new HttpEntity<>(Map.of("fullName", "x", "roles", List.of("ADMIN"), "active", true),
                        auth("receptionist")), String.class);
        assertThat(put.getStatusCode().value()).isEqualTo(403);

        ResponseEntity<String> reset = rest.exchange("/api/users/" + adminId + "/password", HttpMethod.POST,
                new HttpEntity<>(Map.of("newPassword", "whatever9"), auth("receptionist")), String.class);
        assertThat(reset.getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void unknownId_returns404() {
        ResponseEntity<Map> res = rest.exchange("/api/users/" + UUID.randomUUID(), HttpMethod.PUT,
                new HttpEntity<>(Map.of("fullName", "x", "roles", List.of("NURSE"), "active", true),
                        auth("admin")), Map.class);
        assertThat(res.getStatusCode().value()).isEqualTo(404);
    }
}
