package com.albudoor.hms.app.security;

import com.albudoor.hms.app.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Security contract for the auth path:
 * <ul>
 *   <li>Unauthenticated (no token / garbage token) requests return 401, not 403.</li>
 *   <li>Bad credentials at login return 401, not 422.</li>
 *   <li>Repeated failed logins lock the username (429) after 5 consecutive failures —
 *       even a subsequent correct password is rejected while locked.</li>
 * </ul>
 *
 * <p>The lockout test uses a DEDICATED user ({@code locktest}) created via the admin API so
 * that locking it cannot affect the shared seeded users the rest of the suite logs in as.
 */
class AuthSecurityIT extends IntegrationTest {

    @Autowired TestRestTemplate rest;

    /**
     * The default {@code SimpleClientHttpRequestFactory} (HttpURLConnection) throws
     * ("cannot retry due to server authentication, in streaming mode") instead of returning
     * a 401/429 response to a POST that wrote a body. Swap in the JDK HttpClient factory for
     * this class so we can assert on auth-failure status codes. Scoped here (not the shared base)
     * to leave the other ITs' client untouched.
     */
    private ClientHttpRequestFactory originalFactory;

    @BeforeEach
    void useHttpClientFactory() {
        originalFactory = rest.getRestTemplate().getRequestFactory();
        rest.getRestTemplate().setRequestFactory(new JdkClientHttpRequestFactory());
    }

    @AfterEach
    void restoreFactory() {
        rest.getRestTemplate().setRequestFactory(originalFactory);
    }

    private String token(String user) {
        var res = rest.postForEntity("/api/auth/login",
                Map.of("username", user, "password", user), Map.class);
        return (String) res.getBody().get("token");
    }

    private HttpHeaders adminAuth() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setBearerAuth(token("admin"));
        return h;
    }

    // ---- 401 for unauthenticated requests ----

    @Test
    void noToken_yields401_not403() {
        ResponseEntity<Map> res = rest.exchange("/api/patients?size=5", HttpMethod.GET,
                new HttpEntity<>(new HttpHeaders()), Map.class);
        assertThat(res.getStatusCode().value()).isEqualTo(401);
    }

    @Test
    void garbageToken_yields401_not403() {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth("not-a-real-jwt.garbage.token");
        ResponseEntity<Map> res = rest.exchange("/api/patients?size=5", HttpMethod.GET,
                new HttpEntity<>(h), Map.class);
        assertThat(res.getStatusCode().value()).isEqualTo(401);
    }

    // ---- 401 for bad credentials at login ----

    @Test
    void badCredentials_atLogin_yields401_not422() {
        ResponseEntity<Map> res = rest.postForEntity("/api/auth/login",
                Map.of("username", "admin", "password", "definitely-wrong"), Map.class);
        assertThat(res.getStatusCode().value()).isEqualTo(401);
        assertThat(res.getBody().get("code")).isEqualTo("INVALID_CREDENTIALS");
    }

    @Test
    void unknownUser_atLogin_yields401() {
        ResponseEntity<Map> res = rest.postForEntity("/api/auth/login",
                Map.of("username", "no-such-user-" + System.nanoTime(), "password", "whatever"), Map.class);
        assertThat(res.getStatusCode().value()).isEqualTo(401);
    }

    // ---- lockout after 5 consecutive failed attempts ----

    @Test
    void fiveFailedAttempts_lockUser_evenWithCorrectPassword() {
        // Dedicated user so locking it cannot break other tests.
        String username = "locktest";
        String password = "locktest123";
        var create = rest.exchange("/api/users", HttpMethod.POST,
                new HttpEntity<>(Map.of(
                        "username", username,
                        "password", password,
                        "fullName", "Lockout Test User",
                        "roles", List.of("RECEPTIONIST")), adminAuth()),
                Map.class);
        assertThat(create.getStatusCode().value())
                .as("create locktest user -> %s : %s", create.getStatusCode(), create.getBody())
                .isEqualTo(201);

        // 5 wrong-password attempts: each is 401 (the 5th trips the lock).
        for (int i = 1; i <= 5; i++) {
            ResponseEntity<Map> bad = rest.postForEntity("/api/auth/login",
                    Map.of("username", username, "password", "wrong-" + i), Map.class);
            assertThat(bad.getStatusCode().value())
                    .as("failed attempt %d", i)
                    .isIn(401, 429);
        }

        // 6th attempt — WITH THE CORRECT PASSWORD — is rejected because the account is locked.
        ResponseEntity<Map> locked = rest.postForEntity("/api/auth/login",
                Map.of("username", username, "password", password), Map.class);
        assertThat(locked.getStatusCode().value())
                .as("correct password while locked -> %s : %s", locked.getStatusCode(), locked.getBody())
                .isEqualTo(429);
        assertThat(locked.getBody().get("code")).isEqualTo("TOO_MANY_ATTEMPTS");
    }
}
