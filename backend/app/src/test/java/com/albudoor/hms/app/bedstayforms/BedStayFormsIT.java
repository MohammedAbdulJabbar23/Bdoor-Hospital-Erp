package com.albudoor.hms.app.bedstayforms;

import com.albudoor.hms.app.IntegrationTest;
import com.albudoor.hms.cashier.domain.PaymentStatus;
import com.albudoor.hms.cashier.infrastructure.PaymentRepository;
import com.albudoor.hms.premature.domain.Bed;
import com.albudoor.hms.premature.infrastructure.BedRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BedStayFormsIT extends IntegrationTest {

    /** 1x1 transparent PNG. */
    static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    @Autowired TestRestTemplate rest;
    @Autowired BedRepository beds;
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

    <T> T put(String path, Object body, String user, Class<T> type) {
        var r = rest.exchange(path, HttpMethod.PUT, new HttpEntity<>(body, auth(user)), type);
        assertThat(r.getStatusCode().is2xxSuccessful()).as("PUT %s -> %s : %s", path, r.getStatusCode(), r.getBody()).isTrue();
        return r.getBody();
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> get(String path, String user) {
        var r = rest.exchange(path, HttpMethod.GET, new HttpEntity<>(auth(user)), Map.class);
        assertThat(r.getStatusCode().is2xxSuccessful()).as("GET %s -> %s : %s", path, r.getStatusCode(), r.getBody()).isTrue();
        return r.getBody();
    }

    /** Premature admission driven to UNDER_CARE — copied from PrematureCaseIT.admitUnderCare(). */
    @SuppressWarnings("unchecked")
    String admitUnderCare() {
        var patient = post("/api/patients", Map.of("fullName", "Baby BSF " + System.nanoTime(), "gender", "MALE",
                "dateOfBirth", "2026-05-01", "mobileNumber", "0773" + (System.nanoTime() % 10_000_000L), "vip", false),
                "receptionist", Map.class);
        var visit = post("/api/visits", Map.of("patientId", patient.get("id"), "visitType", "PREMATURE"), "receptionist", Map.class);
        String visitId = (String) visit.get("id");
        Bed bed = beds.save(Bed.create("BSF-" + System.nanoTime(), "IT"));
        var adm = post("/api/premature/admissions",
                Map.of("visitId", visitId, "bedId", bed.getId().toString(), "stayValue", 3, "stayUnit", "DAYS"),
                "premature", Map.class);
        var initial = payments.findAllByVisitIdOrderByCreatedAtDesc(UUID.fromString(visitId)).stream()
                .filter(p -> p.getStatus() == PaymentStatus.PENDING).findFirst().orElseThrow();
        post("/api/payments/" + initial.getId() + "/approve", Map.of("paymentMethod", "CASH"), "cashier", Map.class);
        return (String) adm.get("id");
    }

    String mhUrl(String stayId) { return "/api/bed-stays/PREMATURE/" + stayId + "/medical-history"; }

    @Test @SuppressWarnings("unchecked")
    void medical_history_upsert_get_roundtrip_with_prefill() {
        String stay = admitUnderCare();

        // GET before any save: prefill present, form null
        var before = get(mhUrl(stay), "premature");
        var prefill = (Map<String, Object>) before.get("prefill");
        assertThat(prefill.get("patientName")).asString().startsWith("Baby BSF");
        assertThat(prefill.get("patientMrn")).isNotNull();
        assertThat(prefill.get("admittedAt")).isNotNull();
        assertThat(before.get("form")).isNull();

        // Doctor saves the sheet
        put(mhUrl(stay), Map.ofEntries(
                Map.entry("weightKg", 3.2), Map.entry("heightCm", 49),
                Map.entry("doctorName", "Dr. House"),
                Map.entry("chiefComplaint", "Fever for 2 days"),
                Map.entry("presentIllnessHx", "Gradual onset"),
                Map.entry("psHx", "None"), Map.entry("pmHx", "Neonatal jaundice"),
                Map.entry("familyHx", "Diabetes (mother)"), Map.entry("allergicHx", "NKDA"),
                Map.entry("socialSmoker", "No"), Map.entry("socialAlcohol", "No"), Map.entry("socialSleep", "Normal"),
                Map.entry("drugHx", "None"), Map.entry("physicalExamination", "Chest clear")
        ), "doctor", Map.class);

        var after = get(mhUrl(stay), "premature");
        var form = (Map<String, Object>) after.get("form");
        assertThat(form.get("chiefComplaint")).isEqualTo("Fever for 2 days");
        assertThat(form.get("physicalExamination")).isEqualTo("Chest clear");
        var spec = (Map<String, Object>) form.get("specialistSignature");
        assertThat(spec.get("present")).isEqualTo(false);
    }

    @Test @SuppressWarnings("unchecked")
    void medical_history_signature_upload_and_stream() {
        String stay = admitUnderCare();

        HttpHeaders h = auth("doctor");
        h.setContentType(MediaType.MULTIPART_FORM_DATA);
        var filePart = new HttpHeaders();
        filePart.setContentType(MediaType.IMAGE_PNG);
        var form = new LinkedMultiValueMap<String, Object>();
        form.add("file", new HttpEntity<>(new ByteArrayResource(PNG) {
            @Override public String getFilename() { return "sig.png"; }
        }, filePart));
        form.add("signerName", "Dr. Specialist");
        var up = rest.exchange(mhUrl(stay) + "/signatures/SPECIALIST", HttpMethod.POST, new HttpEntity<>(form, h), Map.class);
        assertThat(up.getStatusCode().is2xxSuccessful()).as("upload: %s %s", up.getStatusCode(), up.getBody()).isTrue();

        var img = rest.exchange(mhUrl(stay) + "/signatures/SPECIALIST", HttpMethod.GET,
                new HttpEntity<>(auth("premature")), byte[].class);
        assertThat(img.getStatusCode().is2xxSuccessful()).isTrue();
        // app sets server.servlet.encoding.force=true, which appends ;charset=UTF-8 to every Content-Type
        assertThat(img.getHeaders().getContentType().isCompatibleWith(MediaType.IMAGE_PNG)).isTrue();
        assertThat(img.getBody()).isNotEmpty();

        var sheet = (Map<String, Object>) get(mhUrl(stay), "premature").get("form");
        var spec = (Map<String, Object>) sheet.get("specialistSignature");
        assertThat(spec.get("present")).isEqualTo(true);
        assertThat(spec.get("signerName")).isEqualTo("Dr. Specialist");
    }
}
