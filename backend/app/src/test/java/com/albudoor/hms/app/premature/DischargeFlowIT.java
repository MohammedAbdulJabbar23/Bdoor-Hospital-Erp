package com.albudoor.hms.app.premature;

import com.albudoor.hms.app.IntegrationTest;
import com.albudoor.hms.cashier.domain.PaymentStage;
import com.albudoor.hms.cashier.domain.PaymentStatus;
import com.albudoor.hms.cashier.infrastructure.PaymentRepository;
import com.albudoor.hms.premature.domain.AdmissionStatus;
import com.albudoor.hms.premature.domain.Bed;
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

class DischargeFlowIT extends IntegrationTest {

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
        var res = rest.exchange(path, HttpMethod.POST,
                new HttpEntity<>(body == null ? Map.of() : body, auth(user)), type);
        assertThat(res.getStatusCode().is2xxSuccessful())
                .as("POST %s -> %s : %s", path, res.getStatusCode(), res.getBody()).isTrue();
        return res.getBody();
    }

    private String[] admitUnderCare() {
        Map<?, ?> patient = post("/api/patients", Map.of(
                "fullName", "Baby D " + System.nanoTime(), "gender", "FEMALE",
                "dateOfBirth", "2026-05-01",
                "mobileNumber", "0771" + (System.nanoTime() % 10_000_000L), "vip", false),
                "receptionist", Map.class);
        Map<?, ?> visit = post("/api/visits",
                Map.of("patientId", patient.get("id"), "visitType", "PREMATURE"), "receptionist", Map.class);
        String visitId = (String) visit.get("id");
        String bedId = beds.save(Bed.create("PREM-IT-" + System.nanoTime(), "IT")).getId().toString();
        Map<?, ?> adm = post("/api/premature/admissions",
                Map.of("visitId", visitId, "bedId", bedId, "stayValue", 2, "stayUnit", "DAYS"),
                "premature", Map.class);
        var initial = payments.findAllByVisitIdOrderByCreatedAtDesc(UUID.fromString(visitId)).stream()
                .filter(p -> p.getStatus() == PaymentStatus.PENDING).findFirst().orElseThrow();
        post("/api/payments/" + initial.getId() + "/approve", Map.of("paymentMethod", "CASH"), "cashier", Map.class);
        return new String[]{(String) adm.get("id"), visitId, bedId};
    }

    private UUID pendingFinal(String visitId) {
        return payments.findAllByVisitIdOrderByCreatedAtDesc(UUID.fromString(visitId)).stream()
                .filter(p -> p.getStage() == PaymentStage.FINAL && p.getStatus() == PaymentStatus.PENDING)
                .findFirst().orElseThrow().getId();
    }

    @Test
    void finish_then_approve_final_closes_case_and_discharges_bed() {
        String[] s = admitUnderCare();
        String admissionId = s[0], visitId = s[1], bedId = s[2];
        await().atMost(ofSeconds(5)).untilAsserted(() ->
                assertThat(admissions.findById(UUID.fromString(admissionId)).get().getStatus())
                        .isEqualTo(AdmissionStatus.UNDER_CARE));

        post("/api/premature/admissions/" + admissionId + "/finish-treatment", null, "premature", Map.class);
        assertThat(visits.findById(UUID.fromString(visitId)).get().getStatus())
                .isEqualTo(VisitStatus.AWAITING_FINAL_PAYMENT);

        post("/api/payments/" + pendingFinal(visitId) + "/approve",
                Map.of("paymentMethod", "CASH"), "cashier", Map.class);

        await().atMost(ofSeconds(5)).untilAsserted(() -> {
            assertThat(admissions.findById(UUID.fromString(admissionId)).get().getStatus())
                    .isEqualTo(AdmissionStatus.CLOSED);
            assertThat(beds.findById(UUID.fromString(bedId)).get().getStatus())
                    .isEqualTo(BedStatus.AVAILABLE);
            assertThat(visits.findById(UUID.fromString(visitId)).get().getStatus())
                    .isEqualTo(VisitStatus.COMPLETED);
        });
    }

    @Test
    void rejected_final_payment_keeps_case_open_for_retry_p12b() {
        String[] s = admitUnderCare();
        String admissionId = s[0], visitId = s[1], bedId = s[2];
        await().atMost(ofSeconds(5)).untilAsserted(() ->
                assertThat(admissions.findById(UUID.fromString(admissionId)).get().getStatus())
                        .isEqualTo(AdmissionStatus.UNDER_CARE));

        post("/api/premature/admissions/" + admissionId + "/finish-treatment", null, "premature", Map.class);
        post("/api/payments/" + pendingFinal(visitId) + "/reject",
                Map.of("reason", "Family will pay tomorrow"), "cashier", Map.class);

        await().atMost(ofSeconds(5)).untilAsserted(() -> {
            assertThat(admissions.findById(UUID.fromString(admissionId)).get().getStatus())
                    .isEqualTo(AdmissionStatus.AWAITING_DISCHARGE_PAYMENT);
            assertThat(visits.findById(UUID.fromString(visitId)).get().getStatus())
                    .isEqualTo(VisitStatus.AWAITING_FINAL_PAYMENT);
        });

        // P12b: re-issue a fresh discharge payment and approve it to genuinely close the case.
        post("/api/premature/admissions/" + admissionId + "/reissue-discharge-payment",
                null, "premature", Map.class);
        post("/api/payments/" + pendingFinal(visitId) + "/approve",
                Map.of("paymentMethod", "CASH"), "cashier", Map.class);

        await().atMost(ofSeconds(5)).untilAsserted(() -> {
            assertThat(admissions.findById(UUID.fromString(admissionId)).get().getStatus())
                    .isEqualTo(AdmissionStatus.CLOSED);
            assertThat(beds.findById(UUID.fromString(bedId)).get().getStatus())
                    .isEqualTo(BedStatus.AVAILABLE);
            assertThat(visits.findById(UUID.fromString(visitId)).get().getStatus())
                    .isEqualTo(VisitStatus.COMPLETED);
        });
    }
}
