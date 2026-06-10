package com.albudoor.hms.app.premature;

import com.albudoor.hms.app.IntegrationTest;
import com.albudoor.hms.cashier.domain.PaymentStatus;
import com.albudoor.hms.cashier.infrastructure.PaymentRepository;
import com.albudoor.hms.premature.domain.Bed;
import com.albudoor.hms.premature.infrastructure.BedRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PatientCaseFormIT extends IntegrationTest {

    @Autowired TestRestTemplate rest;
    @Autowired BedRepository beds;
    @Autowired PaymentRepository payments;

    // auth(...), post(...) helpers copied from PrematureCaseIT
    private HttpHeaders auth(String user) {
        var login = rest.postForEntity("/api/auth/login", Map.of("username", user, "password", user), Map.class);
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setBearerAuth((String) login.getBody().get("token"));
        return h;
    }
    private <T> T post(String path, Object body, String user, Class<T> type) {
        var r = rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body == null ? Map.of() : body, auth(user)), type);
        assertThat(r.getStatusCode().is2xxSuccessful()).as("POST %s -> %s : %s", path, r.getStatusCode(), r.getBody()).isTrue();
        return r.getBody();
    }

    @SuppressWarnings("unchecked")
    private String admitUnderCare() {
        var patient = post("/api/patients", Map.of("fullName", "Baby P6 " + System.nanoTime(), "gender", "FEMALE",
                "dateOfBirth", "2026-05-20", "mobileNumber", "0775" + (System.nanoTime() % 10_000_000L), "vip", false),
                "receptionist", Map.class);
        var visit = post("/api/visits", Map.of("patientId", patient.get("id"), "visitType", "PREMATURE"), "receptionist", Map.class);
        String visitId = (String) visit.get("id");
        Bed bed = beds.save(Bed.create("P6-" + System.nanoTime(), "IT"));
        var adm = post("/api/premature/admissions",
                Map.of("visitId", visitId, "bedId", bed.getId().toString(), "stayValue", 3, "stayUnit", "DAYS"),
                "premature", Map.class);
        var initial = payments.findAllByVisitIdOrderByCreatedAtDesc(UUID.fromString(visitId)).stream()
                .filter(p -> p.getStatus() == PaymentStatus.PENDING).findFirst().orElseThrow();
        post("/api/payments/" + initial.getId() + "/approve", Map.of("paymentMethod", "CASH"), "cashier", Map.class);
        return (String) adm.get("id");
    }

    @Test @SuppressWarnings("unchecked")
    void case_form_upsert_then_appears_in_case_response_with_prefill() {
        String adm = admitUnderCare();

        var put = rest.exchange("/api/premature/admissions/" + adm + "/case-form", HttpMethod.PUT,
                new HttpEntity<>(Map.ofEntries(
                        Map.entry("wardNumber", "W-3"),
                        Map.entry("nextOfKinAddress", "Basra, Al-Ashar"),
                        Map.entry("nextOfKinPhone", "07701234567"),
                        Map.entry("treatingSpecialist", "Dr. Salim"),
                        Map.entry("initialDiagnosis", "Prematurity, RDS"),
                        Map.entry("finalDiagnosis", "")
                ), auth("doctor")), Map.class);
        assertThat(put.getStatusCode().is2xxSuccessful()).as("%s", put.getBody()).isTrue();

        var caseBody = rest.exchange("/api/premature/admissions/" + adm + "/case", HttpMethod.GET,
                new HttpEntity<>(auth("premature")), Map.class).getBody();
        var caseForm = (Map<String, Object>) caseBody.get("caseForm");
        assertThat(caseForm.get("wardNumber")).isEqualTo("W-3");
        assertThat(caseForm.get("initialDiagnosis")).isEqualTo("Prematurity, RDS");
        var cfPrefill = (Map<String, Object>) caseBody.get("caseFilePrefill");
        assertThat(cfPrefill.get("gender")).isEqualTo("FEMALE");
        assertThat(cfPrefill.containsKey("motherName")).isTrue();   // null for this seed, key must exist
    }
}
