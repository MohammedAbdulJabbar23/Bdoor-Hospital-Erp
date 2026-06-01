package com.albudoor.hms.app.emergency;

import com.albudoor.hms.app.IntegrationTest;
import com.albudoor.hms.cashier.domain.PaymentStatus;
import com.albudoor.hms.cashier.infrastructure.PaymentRepository;
import com.albudoor.hms.emergency.domain.BedStatus;
import com.albudoor.hms.emergency.domain.EmergencyBed;
import com.albudoor.hms.emergency.domain.EmergencyCaseStatus;
import com.albudoor.hms.emergency.infrastructure.EmergencyBedRepository;
import com.albudoor.hms.emergency.infrastructure.EmergencyCaseRepository;
import com.albudoor.hms.visitmanagement.domain.VisitStatus;
import com.albudoor.hms.visitmanagement.infrastructure.VisitRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class AdmitFlowIT extends IntegrationTest {

    @Autowired TestRestTemplate rest;
    @Autowired EmergencyBedRepository emergencyBeds;
    @Autowired EmergencyCaseRepository cases;
    @Autowired VisitRepository visits;
    @Autowired PaymentRepository payments;

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

    private <T> T post(String path, Object body, String user, Class<T> type) {
        var res = rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, auth(user)), type);
        assertThat(res.getStatusCode().is2xxSuccessful())
                .as("POST %s -> %s : %s", path, res.getStatusCode(), res.getBody()).isTrue();
        return res.getBody();
    }

    private <T> T get(String path, String user, Class<T> type) {
        var res = rest.exchange(path, HttpMethod.GET, new HttpEntity<>(auth(user)), type);
        assertThat(res.getStatusCode().is2xxSuccessful())
                .as("GET %s -> %s : %s", path, res.getStatusCode(), res.getBody()).isTrue();
        return res.getBody();
    }

    private String seedEmergencyVisit() {
        Map<?, ?> patient = post("/api/patients", Map.of(
                "fullName", "Patient Test " + System.nanoTime(),
                "gender", "MALE", "dateOfBirth", "1990-05-01",
                "mobileNumber", "0770" + (System.nanoTime() % 10_000_000L), "vip", false),
                "receptionist", Map.class);
        String patientId = (String) patient.get("id");
        Map<?, ?> visit = post("/api/visits",
                Map.of("patientId", patientId, "visitType", "EMERGENCY"), "receptionist", Map.class);
        return (String) visit.get("id");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> anEmergencyService() {
        List<Map<String, Object>> services = get("/api/emergency/services", "emergency", List.class);
        return services.get(0);
    }

    private String freshBedId() {
        EmergencyBed bed = emergencyBeds.save(EmergencyBed.create("EMRG-IT-" + System.nanoTime(), "IT"));
        return bed.getId().toString();
    }

    @Test
    void admit_then_approve_initial_marks_under_treatment_and_occupies_bed() {
        String visitId = seedEmergencyVisit();
        String bedId = freshBedId();
        Map<String, Object> service = anEmergencyService();
        String serviceItemId = (String) service.get("id");
        BigDecimal serviceFee = new BigDecimal(String.valueOf(service.get("fee")));

        Map<?, ?> theCase = post("/api/emergency/cases",
                Map.of("visitId", visitId, "bedId", bedId, "serviceItemId", serviceItemId,
                        "stayValue", 6, "stayUnit", "HOURS"),
                "emergency", Map.class);
        String caseId = (String) theCase.get("id");

        assertThat(emergencyBeds.findById(UUID.fromString(bedId)).get().getStatus()).isEqualTo(BedStatus.PENDING_PAYMENT);
        assertThat(visits.findById(UUID.fromString(visitId)).get().getStatus()).isEqualTo(VisitStatus.AWAITING_PAYMENT);

        var pending = payments.findAllByVisitIdOrderByCreatedAtDesc(UUID.fromString(visitId)).stream()
                .filter(p -> p.getStatus() == PaymentStatus.PENDING).findFirst().orElseThrow();
        assertThat(pending.getTotalDue())
                .as("INITIAL payment should bill the selected service fee (%s)", serviceFee)
                .isEqualByComparingTo(serviceFee);
        post("/api/payments/" + pending.getId() + "/approve", Map.of("paymentMethod", "CASH"), "cashier", Map.class);

        await().atMost(ofSeconds(5)).untilAsserted(() -> {
            assertThat(cases.findById(UUID.fromString(caseId)).get().getStatus()).isEqualTo(EmergencyCaseStatus.UNDER_TREATMENT);
            assertThat(emergencyBeds.findById(UUID.fromString(bedId)).get().getStatus()).isEqualTo(BedStatus.OCCUPIED);
            assertThat(visits.findById(UUID.fromString(visitId)).get().getStatus()).isEqualTo(VisitStatus.IN_PROGRESS);
        });
    }

    @Test
    void admit_then_reject_initial_releases_bed_and_cancels() {
        String visitId = seedEmergencyVisit();
        String bedId = freshBedId();
        String serviceItemId = (String) anEmergencyService().get("id");

        Map<?, ?> theCase = post("/api/emergency/cases",
                Map.of("visitId", visitId, "bedId", bedId, "serviceItemId", serviceItemId,
                        "stayValue", 6, "stayUnit", "HOURS"),
                "emergency", Map.class);
        String caseId = (String) theCase.get("id");

        var pending = payments.findAllByVisitIdOrderByCreatedAtDesc(UUID.fromString(visitId)).stream()
                .filter(p -> p.getStatus() == PaymentStatus.PENDING).findFirst().orElseThrow();
        post("/api/payments/" + pending.getId() + "/reject", Map.of("reason", "Cannot pay"), "cashier", Map.class);

        await().atMost(ofSeconds(5)).untilAsserted(() -> {
            assertThat(cases.findById(UUID.fromString(caseId)).get().getStatus()).isEqualTo(EmergencyCaseStatus.CANCELLED);
            assertThat(emergencyBeds.findById(UUID.fromString(bedId)).get().getStatus()).isEqualTo(BedStatus.AVAILABLE);
            assertThat(visits.findById(UUID.fromString(visitId)).get().getStatus()).isEqualTo(VisitStatus.CANCELLED);
        });
    }
}
