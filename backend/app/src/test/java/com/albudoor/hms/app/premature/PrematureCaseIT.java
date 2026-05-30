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

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PrematureCaseIT extends IntegrationTest {

    @Autowired TestRestTemplate rest;
    @Autowired BedRepository beds;
    @Autowired PaymentRepository payments;

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
        var patient = post("/api/patients", Map.of("fullName", "Baby F " + System.nanoTime(), "gender", "MALE",
                "dateOfBirth", "2026-05-01", "mobileNumber", "0773" + (System.nanoTime() % 10_000_000L), "vip", false),
                "receptionist", Map.class);
        var visit = post("/api/visits", Map.of("patientId", patient.get("id"), "visitType", "PREMATURE"), "receptionist", Map.class);
        String visitId = (String) visit.get("id");
        Bed bed = beds.save(Bed.create("PREM-FORM-" + System.nanoTime(), "IT"));
        var adm = post("/api/premature/admissions",
                Map.of("visitId", visitId, "bedId", bed.getId().toString(), "stayValue", 3, "stayUnit", "DAYS"),
                "premature", Map.class);
        var initial = payments.findAllByVisitIdOrderByCreatedAtDesc(UUID.fromString(visitId)).stream()
                .filter(p -> p.getStatus() == PaymentStatus.PENDING).findFirst().orElseThrow();
        post("/api/payments/" + initial.getId() + "/approve", Map.of("paymentMethod", "CASH"), "cashier", Map.class);
        return (String) adm.get("id");
    }

    @Test @SuppressWarnings("unchecked")
    void case_get_prefills_then_upsert_form_and_record_tour_persist() {
        String adm = admitUnderCare();

        var caseBody = rest.exchange("/api/premature/admissions/" + adm + "/case", HttpMethod.GET,
                new HttpEntity<>(auth("premature")), Map.class).getBody();
        assertThat(caseBody.get("form")).isNull();
        assertThat((Map<String,Object>) caseBody.get("prefill")).isNotNull();

        var form = rest.exchange("/api/premature/admissions/" + adm + "/form", HttpMethod.PUT,
                new HttpEntity<>(Map.ofEntries(
                        Map.entry("ageText", "12 days"),
                        Map.entry("birthWeightKg", 1.2), Map.entry("currentWeightKg", 1.45),
                        Map.entry("gestationalAgeWeeks", 32), Map.entry("gestationalAgeDays", 4),
                        Map.entry("correctedGaWeeks", 34), Map.entry("correctedGaDays", 1),
                        Map.entry("lengthCm", 42.0), Map.entry("ofcCm", 30.0),
                        Map.entry("feedingType", "EBM"), Map.entry("gir", 6.0)), auth("premature")), Map.class);
        assertThat(form.getStatusCode().value()).isEqualTo(200);
        assertThat(((Map<String,Object>) form.getBody()).get("feedingType")).isEqualTo("EBM");

        post("/api/premature/admissions/" + adm + "/tours", Map.ofEntries(
                Map.entry("tourType", "MORNING"), Map.entry("respRate", 40), Map.entry("spo2", 96),
                Map.entry("pulseRate", 140), Map.entry("respSupport", List.of("CPAP", "NC")),
                Map.entry("uop", "2 ml/kg"), Map.entry("babyTempC", 36.8)), "nurse", Map.class);

        var after = rest.exchange("/api/premature/admissions/" + adm + "/case", HttpMethod.GET,
                new HttpEntity<>(auth("premature")), Map.class).getBody();
        assertThat(after.get("form")).isNotNull();
        assertThat((List<?>) after.get("tours")).hasSize(1);
    }

    @Test
    void upsert_form_rejects_missing_mandatory_field() {
        String adm = admitUnderCare();
        var res = rest.exchange("/api/premature/admissions/" + adm + "/form", HttpMethod.PUT,
                new HttpEntity<>(Map.of("ageText", "12 days"), auth("premature")), String.class);
        assertThat(res.getStatusCode().value()).isEqualTo(400);
    }

    @Test
    void recording_a_tour_is_forbidden_for_cashier() {
        String adm = admitUnderCare();
        var res = rest.exchange("/api/premature/admissions/" + adm + "/tours", HttpMethod.POST,
                new HttpEntity<>(Map.of("tourType","MORNING","respRate",40,"spo2",96,"pulseRate",140,"uop","x","babyTempC",36.8),
                        auth("cashier")), String.class);
        assertThat(res.getStatusCode().value()).isEqualTo(403);
    }
}
