package com.albudoor.hms.app.bedstayforms;

import com.albudoor.hms.app.IntegrationTest;
import com.albudoor.hms.cashier.domain.PaymentStatus;
import com.albudoor.hms.cashier.infrastructure.PaymentRepository;
import com.albudoor.hms.emergency.domain.EmergencyBed;
import com.albudoor.hms.emergency.infrastructure.EmergencyBedRepository;
import com.albudoor.hms.visitmanagement.domain.VisitStatus;
import com.albudoor.hms.visitmanagement.infrastructure.VisitRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** Proves the shared forms work for EMERGENCY stays via EmergencyStayDirectory. */
class BedStayFormsEmergencyIT extends IntegrationTest {

    @Autowired TestRestTemplate rest;
    @Autowired EmergencyBedRepository emergencyBeds;
    @Autowired VisitRepository visits;
    @Autowired PaymentRepository payments;

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

    @SuppressWarnings("unchecked")
    private String anEmergencyServiceId() {
        var r = rest.exchange("/api/emergency/services", HttpMethod.GET, new HttpEntity<>(auth("emergency")), List.class);
        assertThat(r.getStatusCode().is2xxSuccessful()).as("GET /api/emergency/services -> %s", r.getStatusCode()).isTrue();
        List<Map<String, Object>> services = r.getBody();
        return (String) services.get(0).get("id");
    }

    /**
     * Emergency admission driven to UNDER_TREATMENT — copied from
     * EmergencyOrdersIT.admitUnderTreatment(): patient → EMERGENCY visit → emergency bed →
     * case with serviceItemId → initial payment approved. Returns the emergency case id.
     */
    private String admitEmergencyUnderTreatment() {
        Map<?, ?> patient = post("/api/patients", Map.of(
                "fullName", "Patient BSF-E " + System.nanoTime(), "gender", "MALE",
                "dateOfBirth", "1990-05-01",
                "mobileNumber", "0773" + (System.nanoTime() % 10_000_000L), "vip", false),
                "receptionist", Map.class);
        Map<?, ?> visit = post("/api/visits",
                Map.of("patientId", patient.get("id"), "visitType", "EMERGENCY"), "receptionist", Map.class);
        String visitId = (String) visit.get("id");
        String bedId = emergencyBeds.save(EmergencyBed.create("EMRG-BSF-" + System.nanoTime(), "IT"))
                .getId().toString();
        String serviceItemId = anEmergencyServiceId();
        Map<?, ?> theCase = post("/api/emergency/cases",
                Map.of("visitId", visitId, "bedId", bedId, "serviceItemId", serviceItemId,
                        "stayValue", 6, "stayUnit", "HOURS"),
                "emergency", Map.class);
        String caseId = (String) theCase.get("id");
        var initial = payments.findAllByVisitIdOrderByCreatedAtDesc(UUID.fromString(visitId)).stream()
                .filter(p -> p.getStatus() == PaymentStatus.PENDING).findFirst().orElseThrow();
        post("/api/payments/" + initial.getId() + "/approve", Map.of("paymentMethod", "CASH"), "cashier", Map.class);
        await().atMost(ofSeconds(5)).untilAsserted(() ->
                assertThat(visits.findById(UUID.fromString(visitId)).get().getStatus())
                        .isEqualTo(VisitStatus.IN_PROGRESS));
        return caseId;
    }

    @Test @SuppressWarnings("unchecked")
    void emergency_stay_supports_all_three_forms() {
        String stay = admitEmergencyUnderTreatment();
        String base = "/api/bed-stays/EMERGENCY/" + stay;

        // medical history: emergency staff write + prefill from EmergencyStayDirectory
        var put = rest.exchange(base + "/medical-history", HttpMethod.PUT,
                new HttpEntity<>(Map.of("chiefComplaint", "RTA"), auth("emergency")), Map.class);
        assertThat(put.getStatusCode().is2xxSuccessful()).as("%s", put.getBody()).isTrue();
        var got = rest.exchange(base + "/medical-history", HttpMethod.GET,
                new HttpEntity<>(auth("emergency")), Map.class).getBody();
        assertThat(((Map<String, Object>) got.get("prefill")).get("patientName")).isNotNull();
        assertThat(((Map<String, Object>) got.get("form")).get("chiefComplaint")).isEqualTo("RTA");

        // nursing row
        var np = rest.exchange(base + "/nursing-procedures", HttpMethod.POST,
                new HttpEntity<>(Map.of("procedureName", "Wound dressing", "performedAt", "2026-06-10T09:00:00Z"),
                        auth("nurse")), Map.class);
        assertThat(np.getStatusCode().is2xxSuccessful()).isTrue();

        // treatment chart
        var tc = rest.exchange(base + "/treatment-charts/2026-06-10", HttpMethod.PUT,
                new HttpEntity<>(Map.of("rows", List.of(Map.of("medicineName", "Tetanus toxoid"))),
                        auth("doctor")), Map.class);
        assertThat(tc.getStatusCode().is2xxSuccessful()).isTrue();

        // and premature staff get 403 on an emergency stay. The seeded "premature" user is a
        // department doctor (PREMATURE_STAFF + DOCTOR) and DOCTOR is hospital-wide by design,
        // so use a user holding ONLY the dept-staff role.
        String prematureStaff = "it" + String.format("%09d", System.nanoTime() % 1_000_000_000L);
        post("/api/users", Map.of("username", prematureStaff, "password", prematureStaff,
                "fullName", "IT PREMATURE_STAFF", "roles", List.of("PREMATURE_STAFF")), "admin", Map.class);
        var denied = rest.exchange(base + "/medical-history", HttpMethod.GET,
                new HttpEntity<>(auth(prematureStaff)), String.class);
        assertThat(denied.getStatusCode().value()).isEqualTo(403);
    }
}
