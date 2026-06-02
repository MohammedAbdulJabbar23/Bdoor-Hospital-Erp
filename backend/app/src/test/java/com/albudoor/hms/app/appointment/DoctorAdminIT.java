package com.albudoor.hms.app.appointment;

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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration coverage for the doctor-admin defect fixes: schedule validation (overlapping
 * same-day blocks and slotMinutes > block length are rejected) and the new admin endpoints
 * PUT /api/doctors/{id} and activate/deactivate.
 */
class DoctorAdminIT extends IntegrationTest {

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

    private <T> ResponseEntity<T> exchange(String path, HttpMethod method, Object body, String user, Class<T> type) {
        return rest.exchange(path, method, new HttpEntity<>(body, auth(user)), type);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> postOk(String path, Object body, String user) {
        ResponseEntity<Map> res = exchange(path, HttpMethod.POST, body, user, Map.class);
        assertThat(res.getStatusCode().is2xxSuccessful())
                .as("POST %s -> %s : %s", path, res.getStatusCode(), res.getBody()).isTrue();
        return res.getBody();
    }

    private String createDoctor() {
        Map<String, Object> doctor = postOk("/api/doctors", Map.of(
                "fullName", "Dr. Admin Tester " + System.nanoTime(),
                "specialty", "Testing", "consultationFee", 10000, "currency", "IQD"), "admin");
        return (String) doctor.get("id");
    }

    private ResponseEntity<Map> setSchedule(String doctorId, List<Map<String, Object>> blocks) {
        return exchange("/api/doctors/" + doctorId + "/schedule", HttpMethod.PUT,
                Map.of("blocks", blocks), "admin", Map.class);
    }

    @Test
    void rejects_overlapping_same_day_blocks() {
        String doctorId = createDoctor();
        ResponseEntity<Map> res = setSchedule(doctorId, List.of(
                Map.of("dayOfWeek", "MONDAY", "startTime", "09:00:00", "endTime", "12:00:00", "slotMinutes", 30),
                Map.of("dayOfWeek", "MONDAY", "startTime", "11:00:00", "endTime", "14:00:00", "slotMinutes", 30)));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(res.getBody().get("code")).isEqualTo("SCHEDULE_BLOCKS_OVERLAP");
    }

    @Test
    void accepts_touching_same_day_blocks() {
        String doctorId = createDoctor();
        // end == next start is allowed (no overlap).
        ResponseEntity<Map> res = setSchedule(doctorId, List.of(
                Map.of("dayOfWeek", "MONDAY", "startTime", "09:00:00", "endTime", "12:00:00", "slotMinutes", 30),
                Map.of("dayOfWeek", "MONDAY", "startTime", "12:00:00", "endTime", "14:00:00", "slotMinutes", 30)));
        assertThat(res.getStatusCode().is2xxSuccessful())
                .as("touching blocks -> %s : %s", res.getStatusCode(), res.getBody()).isTrue();
    }

    @Test
    void rejects_slot_longer_than_block() {
        String doctorId = createDoctor();
        ResponseEntity<Map> res = setSchedule(doctorId, List.of(
                Map.of("dayOfWeek", "TUESDAY", "startTime", "09:00:00", "endTime", "09:20:00", "slotMinutes", 30)));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(res.getBody().get("code")).isEqualTo("SLOT_LONGER_THAN_BLOCK");
    }

    @Test
    void update_changes_profile_fields() {
        String doctorId = createDoctor();
        ResponseEntity<Map> res = exchange("/api/doctors/" + doctorId, HttpMethod.PUT, Map.of(
                "fullName", "Dr. Renamed", "specialty", "Cardiology",
                "consultationFee", 25000, "phone", "+9647700000000"), "admin", Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody().get("fullName")).isEqualTo("Dr. Renamed");
        assertThat(res.getBody().get("specialty")).isEqualTo("Cardiology");
        assertThat(((Number) res.getBody().get("consultationFee")).intValue()).isEqualTo(25000);
    }

    @Test
    void deactivate_then_activate_toggles_active_flag() {
        String doctorId = createDoctor();

        ResponseEntity<Map> off = exchange("/api/doctors/" + doctorId + "/deactivate",
                HttpMethod.POST, null, "admin", Map.class);
        assertThat(off.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(off.getBody().get("active")).isEqualTo(false);

        ResponseEntity<Map> on = exchange("/api/doctors/" + doctorId + "/activate",
                HttpMethod.POST, null, "admin", Map.class);
        assertThat(on.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(on.getBody().get("active")).isEqualTo(true);
    }

    @Test
    void update_requires_admin() {
        String doctorId = createDoctor();
        ResponseEntity<Map> res = exchange("/api/doctors/" + doctorId, HttpMethod.PUT, Map.of(
                "fullName", "Hacker"), "receptionist", Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
