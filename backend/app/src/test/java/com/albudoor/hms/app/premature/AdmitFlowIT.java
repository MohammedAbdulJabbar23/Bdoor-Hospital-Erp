package com.albudoor.hms.app.premature;

import com.albudoor.hms.app.IntegrationTest;
import com.albudoor.hms.cashier.domain.PaymentStatus;
import com.albudoor.hms.cashier.infrastructure.PaymentRepository;
import com.albudoor.hms.premature.domain.AdmissionStatus;
import com.albudoor.hms.premature.domain.BedStatus;
import com.albudoor.hms.premature.infrastructure.BedRepository;
import com.albudoor.hms.premature.infrastructure.PrematureAdmissionRepository;
import com.albudoor.hms.visitmanagement.domain.VisitStatus;
import com.albudoor.hms.visitmanagement.infrastructure.VisitRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

import java.util.Map;
import java.util.UUID;

import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class AdmitFlowIT extends IntegrationTest {

    @Autowired TestRestTemplate rest;
    @Autowired BedRepository beds;
    @Autowired PrematureAdmissionRepository admissions;
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

    private String[] seedPrematureVisit() {
        Map<?, ?> patient = post("/api/patients", Map.of(
                "fullName", "Baby Test " + System.nanoTime(),
                "gender", "MALE", "dateOfBirth", "2026-05-01",
                "mobileNumber", "0770" + (System.nanoTime() % 10_000_000L), "vip", false),
                "receptionist", Map.class);
        String patientId = (String) patient.get("id");
        String mrn = (String) patient.get("mrn");
        Map<?, ?> visit = post("/api/visits",
                Map.of("patientId", patientId, "visitType", "PREMATURE"), "receptionist", Map.class);
        return new String[]{patientId, mrn, (String) visit.get("id")};
    }

    private String availableBedId() {
        return beds.findAllByOrderByCodeAsc().stream()
                .filter(b -> b.getStatus() == BedStatus.AVAILABLE && b.isActive())
                .findFirst().orElseThrow().getId().toString();
    }

    @Test
    void admit_then_approve_initial_marks_under_care_and_occupies_bed() {
        String[] s = seedPrematureVisit();
        String visitId = s[2];
        String bedId = availableBedId();

        Map<?, ?> admission = post("/api/premature/admissions",
                Map.of("visitId", visitId, "bedId", bedId, "stayValue", 3, "stayUnit", "DAYS"),
                "premature", Map.class);
        String admissionId = (String) admission.get("id");

        assertThat(beds.findById(UUID.fromString(bedId)).get().getStatus()).isEqualTo(BedStatus.PENDING_PAYMENT);
        assertThat(visits.findById(UUID.fromString(visitId)).get().getStatus()).isEqualTo(VisitStatus.AWAITING_PAYMENT);

        var pending = payments.findAllByVisitIdOrderByCreatedAtDesc(UUID.fromString(visitId)).stream()
                .filter(p -> p.getStatus() == PaymentStatus.PENDING).findFirst().orElseThrow();
        post("/api/payments/" + pending.getId() + "/approve", Map.of("paymentMethod", "CASH"), "cashier", Map.class);

        await().atMost(ofSeconds(5)).untilAsserted(() -> {
            assertThat(admissions.findById(UUID.fromString(admissionId)).get().getStatus()).isEqualTo(AdmissionStatus.UNDER_CARE);
            assertThat(beds.findById(UUID.fromString(bedId)).get().getStatus()).isEqualTo(BedStatus.OCCUPIED);
            assertThat(visits.findById(UUID.fromString(visitId)).get().getStatus()).isEqualTo(VisitStatus.IN_PROGRESS);
        });
    }

    @Test
    void admit_then_reject_initial_releases_bed_and_cancels() {
        String[] s = seedPrematureVisit();
        String visitId = s[2];
        String bedId = availableBedId();

        Map<?, ?> admission = post("/api/premature/admissions",
                Map.of("visitId", visitId, "bedId", bedId, "stayValue", 2, "stayUnit", "DAYS"),
                "premature", Map.class);
        String admissionId = (String) admission.get("id");

        var pending = payments.findAllByVisitIdOrderByCreatedAtDesc(UUID.fromString(visitId)).stream()
                .filter(p -> p.getStatus() == PaymentStatus.PENDING).findFirst().orElseThrow();
        post("/api/payments/" + pending.getId() + "/reject", Map.of("reason", "Cannot pay"), "cashier", Map.class);

        await().atMost(ofSeconds(5)).untilAsserted(() -> {
            assertThat(admissions.findById(UUID.fromString(admissionId)).get().getStatus()).isEqualTo(AdmissionStatus.CANCELLED);
            assertThat(beds.findById(UUID.fromString(bedId)).get().getStatus()).isEqualTo(BedStatus.AVAILABLE);
            assertThat(visits.findById(UUID.fromString(visitId)).get().getStatus()).isEqualTo(VisitStatus.CANCELLED);
        });
    }
}
