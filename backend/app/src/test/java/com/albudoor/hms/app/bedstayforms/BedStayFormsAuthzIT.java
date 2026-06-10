package com.albudoor.hms.app.bedstayforms;

import com.albudoor.hms.app.IntegrationTest;
import com.albudoor.hms.cashier.domain.PaymentStatus;
import com.albudoor.hms.cashier.infrastructure.PaymentRepository;
import com.albudoor.hms.premature.domain.AdmissionStatus;
import com.albudoor.hms.premature.domain.Bed;
import com.albudoor.hms.premature.infrastructure.BedRepository;
import com.albudoor.hms.premature.infrastructure.PrematureAdmissionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import java.util.Map;
import java.util.UUID;

import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class BedStayFormsAuthzIT extends IntegrationTest {

    @Autowired TestRestTemplate rest;
    @Autowired BedRepository beds;
    @Autowired PaymentRepository payments;
    @Autowired PrematureAdmissionRepository admissions;

    HttpHeaders auth(String user) {
        var login = rest.postForEntity("/api/auth/login", Map.of("username", user, "password", user), Map.class);
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setBearerAuth((String) login.getBody().get("token"));
        return h;
    }

    <T> T post(String path, Object body, String user, Class<T> type) {
        var r = rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body == null ? Map.of() : body, auth(user)), type);
        assertThat(r.getStatusCode().is2xxSuccessful()).as("POST %s -> %s : %s", path, r.getStatusCode(), r.getBody()).isTrue();
        return r.getBody();
    }

    /** Premature admission left AWAITING_ADMISSION_PAYMENT (no cashier approval). */
    @SuppressWarnings("unchecked")
    Map<String, Object> admitPending() {
        var patient = post("/api/patients", Map.of("fullName", "Baby AZ " + System.nanoTime(), "gender", "FEMALE",
                "dateOfBirth", "2026-05-15", "mobileNumber", "0774" + (System.nanoTime() % 10_000_000L), "vip", false),
                "receptionist", Map.class);
        var visit = post("/api/visits", Map.of("patientId", patient.get("id"), "visitType", "PREMATURE"), "receptionist", Map.class);
        Bed bed = beds.save(Bed.create("AZ-" + System.nanoTime(), "IT"));
        return post("/api/premature/admissions",
                Map.of("visitId", visit.get("id"), "bedId", bed.getId().toString(), "stayValue", 1, "stayUnit", "DAYS"),
                "premature", Map.class);
    }

    /**
     * The seeded "emergency"/"premature" users are department doctors (DEPT_STAFF + DOCTOR),
     * and DOCTOR is hospital-wide by design — so cross-department scoping must be asserted
     * with a user holding ONLY the dept-staff role.
     */
    String pureStaff(String role) {
        String username = "it" + String.format("%09d", System.nanoTime() % 1_000_000_000L);
        post("/api/users", Map.of("username", username, "password", username,
                "fullName", "IT " + role, "roles", java.util.List.of(role)), "admin", Map.class);
        return username;
    }

    int statusOf(HttpMethod method, String path, Object body, String user) {
        var entity = body == null ? new HttpEntity<>(auth(user)) : new HttpEntity<>(body, auth(user));
        return rest.exchange(path, method, entity, String.class).getStatusCode().value();
    }

    static final Map<String, Object> MH_BODY = Map.of("chiefComplaint", "x");
    static final Map<String, Object> NP_BODY = Map.of("procedureName", "x", "performedAt", "2026-06-10T08:00:00Z");

    @Test
    void cross_department_staff_get_403() {
        String stay = (String) admitPending().get("id");
        String mh = "/api/bed-stays/PREMATURE/" + stay + "/medical-history";
        // emergency staff may not touch premature stays (read or write)
        String emergencyStaff = pureStaff("EMERGENCY_STAFF");
        assertThat(statusOf(HttpMethod.GET, mh, null, emergencyStaff)).isEqualTo(403);
        assertThat(statusOf(HttpMethod.PUT, mh, MH_BODY, emergencyStaff)).isEqualTo(403);
    }

    @Test
    void write_levels_follow_the_brd_actor_table() {
        String stay = (String) admitPending().get("id");
        String base = "/api/bed-stays/PREMATURE/" + stay;
        // nurse cannot write the medical history sheet
        assertThat(statusOf(HttpMethod.PUT, base + "/medical-history", MH_BODY, "nurse")).isEqualTo(403);
        // doctor cannot append nursing rows
        assertThat(statusOf(HttpMethod.POST, base + "/nursing-procedures", NP_BODY, "doctor")).isEqualTo(403);
        // but both can read
        assertThat(statusOf(HttpMethod.GET, base + "/medical-history", null, "nurse")).isEqualTo(200);
        assertThat(statusOf(HttpMethod.GET, base + "/nursing-procedures", null, "doctor")).isEqualTo(200);
        // premature staff can do both (pure dept-staff role, no DOCTOR/NURSE side-roles)
        String prematureStaff = pureStaff("PREMATURE_STAFF");
        assertThat(statusOf(HttpMethod.PUT, base + "/medical-history", MH_BODY, prematureStaff)).isEqualTo(200);
        assertThat(statusOf(HttpMethod.POST, base + "/nursing-procedures", NP_BODY, prematureStaff)).isEqualTo(200);
    }

    @Test
    void unknown_stay_is_404() {
        String mh = "/api/bed-stays/PREMATURE/" + UUID.randomUUID() + "/medical-history";
        assertThat(statusOf(HttpMethod.GET, mh, null, "premature")).isEqualTo(404);
        assertThat(statusOf(HttpMethod.PUT, mh, MH_BODY, "doctor")).isEqualTo(404);
    }

    @Test
    @SuppressWarnings("unchecked")
    void closed_stay_rejects_writes_but_allows_reads() {
        // CANCELLED via initial-payment rejection is the shortest path to a closed stay
        var adm = admitPending();
        String stay = (String) adm.get("id");
        String visitId = (String) adm.get("visitId");
        var initial = payments.findAllByVisitIdOrderByCreatedAtDesc(UUID.fromString(visitId)).stream()
                .filter(p -> p.getStatus() == PaymentStatus.PENDING).findFirst().orElseThrow();
        post("/api/payments/" + initial.getId() + "/reject", Map.of("reason", "IT"), "cashier", Map.class);
        // cancellation is event-driven (cf. AdmitFlowIT) — wait for the admission to flip
        await().atMost(ofSeconds(5)).untilAsserted(() ->
                assertThat(admissions.findById(UUID.fromString(stay)).get().getStatus())
                        .isEqualTo(AdmissionStatus.CANCELLED));

        String base = "/api/bed-stays/PREMATURE/" + stay;
        // DomainException STAY_CLOSED -> 422 per GlobalExceptionHandler
        assertThat(statusOf(HttpMethod.PUT, base + "/medical-history", MH_BODY, "doctor")).isEqualTo(422);
        assertThat(statusOf(HttpMethod.POST, base + "/nursing-procedures", NP_BODY, "nurse")).isEqualTo(422);
        assertThat(statusOf(HttpMethod.PUT, base + "/treatment-charts/2026-06-10",
                Map.of("rows", java.util.List.of(Map.of("medicineName", "A"))), "doctor")).isEqualTo(422);
        assertThat(statusOf(HttpMethod.GET, base + "/medical-history", null, "premature")).isEqualTo(200);
    }
}
