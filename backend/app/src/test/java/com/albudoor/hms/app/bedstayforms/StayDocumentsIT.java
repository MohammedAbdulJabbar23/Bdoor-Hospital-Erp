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
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;

import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class StayDocumentsIT extends IntegrationTest {

    /** 1x1 transparent PNG. */
    static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

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

    /**
     * CANCELLED via initial-payment rejection is the shortest path to a closed stay
     * — copied from BedStayFormsAuthzIT.closed_stay_rejects_writes_but_allows_reads().
     */
    @SuppressWarnings("unchecked")
    String admitPendingThenRejectAndAwaitCancelled() {
        var patient = post("/api/patients", Map.of("fullName", "Baby AZ " + System.nanoTime(), "gender", "FEMALE",
                "dateOfBirth", "2026-05-15", "mobileNumber", "0774" + (System.nanoTime() % 10_000_000L), "vip", false),
                "receptionist", Map.class);
        var visit = post("/api/visits", Map.of("patientId", patient.get("id"), "visitType", "PREMATURE"), "receptionist", Map.class);
        Bed bed = beds.save(Bed.create("AZ-" + System.nanoTime(), "IT"));
        var adm = post("/api/premature/admissions",
                Map.of("visitId", visit.get("id"), "bedId", bed.getId().toString(), "stayValue", 1, "stayUnit", "DAYS"),
                "premature", Map.class);
        String stay = (String) adm.get("id");
        String visitId = (String) adm.get("visitId");
        var initial = payments.findAllByVisitIdOrderByCreatedAtDesc(UUID.fromString(visitId)).stream()
                .filter(p -> p.getStatus() == PaymentStatus.PENDING).findFirst().orElseThrow();
        post("/api/payments/" + initial.getId() + "/reject", Map.of("reason", "IT"), "cashier", Map.class);
        // cancellation is event-driven (cf. AdmitFlowIT) — wait for the admission to flip
        await().atMost(ofSeconds(5)).untilAsserted(() ->
                assertThat(admissions.findById(UUID.fromString(stay)).get().getStatus())
                        .isEqualTo(AdmissionStatus.CANCELLED));
        return stay;
    }

    String docsUrl(String stayId) { return "/api/bed-stays/PREMATURE/" + stayId + "/documents"; }

    HttpEntity<LinkedMultiValueMap<String, Object>> pngUpload(String user, String fileName, String label) {
        HttpHeaders h = auth(user);
        h.setContentType(MediaType.MULTIPART_FORM_DATA);
        var filePart = new HttpHeaders();
        filePart.setContentType(MediaType.IMAGE_PNG);
        var form = new LinkedMultiValueMap<String, Object>();
        form.add("file", new HttpEntity<>(new ByteArrayResource(PNG) {
            @Override public String getFilename() { return fileName; }
        }, filePart));
        if (label != null) form.add("label", label);
        return new HttpEntity<>(form, h);
    }

    @Test @SuppressWarnings("unchecked")
    void upload_list_stream_archive_roundtrip() {
        String stay = admitUnderCare();

        var up = rest.exchange(docsUrl(stay), HttpMethod.POST, pngUpload("nurse", "scan.png", "Statistics form"), Map.class);
        assertThat(up.getStatusCode().is2xxSuccessful()).as("%s", up.getBody()).isTrue();
        assertThat(up.getBody().get("sha256"))
                .isEqualTo("c414cd0e204de974f73753c7e28d7638e7b3691bb8b1a2bab6b25bb7fed7ce77");
        assertThat(((Number) up.getBody().get("sizeBytes")).longValue()).isEqualTo(PNG.length);
        String docId = (String) up.getBody().get("id");

        var list = rest.exchange(docsUrl(stay), HttpMethod.GET, new HttpEntity<>(auth("premature")), List.class);
        List<Map<String, Object>> docs = list.getBody();
        assertThat(docs).hasSize(1);
        assertThat(docs.get(0).get("source")).isEqualTo("UPLOAD");
        assertThat(docs.get(0).get("fileName")).isEqualTo("scan.png");
        assertThat(docs.get(0).get("label")).isEqualTo("Statistics form");
        assertThat(docs.get(0).get("archived")).isEqualTo(false);

        var file = rest.exchange(docsUrl(stay) + "/" + docId + "/file", HttpMethod.GET,
                new HttpEntity<>(auth("doctor")), byte[].class);
        assertThat(file.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(file.getBody()).isEqualTo(PNG);

        var arch = rest.exchange(docsUrl(stay) + "/" + docId + "/archive", HttpMethod.POST,
                new HttpEntity<>(auth("premature")), Map.class);
        assertThat(arch.getStatusCode().is2xxSuccessful()).isTrue();
        var after = rest.exchange(docsUrl(stay), HttpMethod.GET, new HttpEntity<>(auth("premature")), List.class);
        assertThat(((Map<String, Object>) after.getBody().get(0)).get("archived")).isEqualTo(true);

        // Arabic filename survives upload, listing and streaming (RFC 5987 disposition)
        var arabic = rest.exchange(docsUrl(stay), HttpMethod.POST, pngUpload("nurse", "تقرير.png", null), Map.class);
        assertThat(arabic.getStatusCode().is2xxSuccessful()).as("%s", arabic.getBody()).isTrue();
        String arabicId = (String) arabic.getBody().get("id");
        var withArabic = rest.exchange(docsUrl(stay), HttpMethod.GET, new HttpEntity<>(auth("premature")), List.class);
        assertThat((List<Map<String, Object>>) withArabic.getBody())
                .extracting(m -> m.get("fileName")).contains("تقرير.png");
        var arabicFile = rest.exchange(docsUrl(stay) + "/" + arabicId + "/file", HttpMethod.GET,
                new HttpEntity<>(auth("doctor")), byte[].class);
        assertThat(arabicFile.getStatusCode().is2xxSuccessful()).isTrue();

        // a different admission's URL cannot stream this stay's document
        String otherStay = admitUnderCare();
        var foreign = rest.exchange(docsUrl(otherStay) + "/" + docId + "/file", HttpMethod.GET,
                new HttpEntity<>(auth("premature")), String.class);
        assertThat(foreign.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void upload_policy_rejects_bad_type_and_nurse_cannot_archive() {
        String stay = admitUnderCare();
        // bad content type
        HttpHeaders h = auth("nurse");
        h.setContentType(MediaType.MULTIPART_FORM_DATA);
        var part = new HttpHeaders();
        part.setContentType(MediaType.TEXT_PLAIN);
        var form = new LinkedMultiValueMap<String, Object>();
        form.add("file", new HttpEntity<>(new ByteArrayResource("hi".getBytes()) {
            @Override public String getFilename() { return "notes.txt"; }
        }, part));
        var bad = rest.exchange(docsUrl(stay), HttpMethod.POST, new HttpEntity<>(form, h), String.class);
        assertThat(bad.getStatusCode().value()).isEqualTo(422);
        assertThat(bad.getBody()).contains("DOCUMENT_TYPE_NOT_ALLOWED");

        // nurse cannot archive
        var up = rest.exchange(docsUrl(stay), HttpMethod.POST, pngUpload("nurse", "x.png", null), Map.class);
        String docId = (String) up.getBody().get("id");
        var arch = rest.exchange(docsUrl(stay) + "/" + docId + "/archive", HttpMethod.POST,
                new HttpEntity<>(auth("nurse")), String.class);
        assertThat(arch.getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void oversize_closed_stay_and_cross_department_are_rejected() {
        String stay = admitUnderCare();

        // oversize: 21 MB body -> 422 DOCUMENT_TOO_LARGE (multipart limit is 25MB so it reaches the handler)
        HttpHeaders h = auth("nurse");
        h.setContentType(MediaType.MULTIPART_FORM_DATA);
        var part = new HttpHeaders();
        part.setContentType(MediaType.IMAGE_PNG);
        var form = new LinkedMultiValueMap<String, Object>();
        form.add("file", new HttpEntity<>(new ByteArrayResource(new byte[21 * 1024 * 1024]) {
            @Override public String getFilename() { return "huge.png"; }
        }, part));
        var big = rest.exchange(docsUrl(stay), HttpMethod.POST, new HttpEntity<>(form, h), String.class);
        assertThat(big.getStatusCode().value()).isEqualTo(422);
        assertThat(big.getBody()).contains("DOCUMENT_TOO_LARGE");

        // cross-department: a user whose ONLY role is EMERGENCY_STAFF gets 403 on a premature stay
        String pureEmergency = pureStaff("EMERGENCY_STAFF");
        var denied = rest.exchange(docsUrl(stay), HttpMethod.GET, new HttpEntity<>(auth(pureEmergency)), String.class);
        assertThat(denied.getStatusCode().value()).isEqualTo(403);

        // closed stay: reject the initial payment of a FRESH pending admission -> CANCELLED -> upload 422
        String cancelled = admitPendingThenRejectAndAwaitCancelled();
        var closedUp = rest.exchange(docsUrl(cancelled), HttpMethod.POST, pngUpload("nurse", "late.png", null), String.class);
        assertThat(closedUp.getStatusCode().value()).isEqualTo(422);
        assertThat(closedUp.getBody()).contains("STAY_CLOSED");
    }

    record LabOrder(String visitId, String caseId, String serviceItemId, String attachmentId) {}

    /** Orders LAB from the stay as doctor (waits for UNDER_CARE first); returns the forwarded visit id. */
    @SuppressWarnings("unchecked")
    String orderLab(String stay) {
        // payment approval flips the admission to UNDER_CARE asynchronously; orders need UNDER_CARE
        await().atMost(ofSeconds(5)).untilAsserted(() ->
                assertThat(admissions.findById(UUID.fromString(stay)).get().getStatus())
                        .isEqualTo(AdmissionStatus.UNDER_CARE));
        var order = post("/api/premature/admissions/" + stay + "/orders",
                Map.of("targetType", "LABORATORY"), "doctor", Map.class);
        return (String) order.get("visitId");
    }

    /**
     * Orders LAB from the stay as doctor, opens the dept case as lab (synchronous —
     * cf. PrematureOrdersIT.driveLabChildToCompleted) and uploads result.png as lab.
     */
    @SuppressWarnings("unchecked")
    LabOrder orderLabAndUploadResult(String stay) {
        String forwardedVisitId = orderLab(stay);

        // the receiving lab opens the case on the forwarded visit (creates the DepartmentCase)
        var item = post("/api/catalogue/items", Map.of("category", "LAB", "code", "CBC-" + System.nanoTime(),
                "nameEn", "Complete Blood Count", "fee", 5000, "currency", "IQD"), "admin", Map.class);
        var deptCase = post("/api/dept-cases/open",
                Map.of("category", "LAB", "visitId", forwardedVisitId,
                        "services", List.of(Map.of("serviceItemId", item.get("id"), "quantity", 1))),
                "lab", Map.class);

        // lab attaches the result file — multipart per CaseAttachmentFinalizedIT
        HttpHeaders h = auth("lab");
        h.setContentType(MediaType.MULTIPART_FORM_DATA);
        var filePart = new HttpHeaders();
        filePart.setContentType(MediaType.IMAGE_PNG);
        var form = new LinkedMultiValueMap<String, Object>();
        form.add("file", new HttpEntity<>(new ByteArrayResource(PNG) {
            @Override public String getFilename() { return "result.png"; }
        }, filePart));
        var up = rest.exchange(
                "/api/dept-cases/" + deptCase.get("id") + "/services/" + item.get("id") + "/attachments",
                HttpMethod.POST, new HttpEntity<>(form, h), Map.class);
        assertThat(up.getStatusCode().is2xxSuccessful()).as("%s", up.getBody()).isTrue();
        return new LabOrder(forwardedVisitId, (String) deptCase.get("id"),
                (String) item.get("id"), (String) up.getBody().get("id"));
    }

    /**
     * Drives the real lab findings flow (cf. PrematureOrdersIT.driveLabChildToCompleted):
     * approve the REFERRAL payment → await AWAITING_STUDY → POST findings text.
     */
    @SuppressWarnings("unchecked")
    void approveReferralAndUploadFindings(LabOrder order, String textFindings) {
        var referral = payments.findAllByVisitIdOrderByCreatedAtDesc(UUID.fromString(order.visitId())).stream()
                .filter(p -> p.getStatus() == PaymentStatus.PENDING).findFirst().orElseThrow();
        post("/api/payments/" + referral.getId() + "/approve", Map.of("paymentMethod", "CASH"), "cashier", Map.class);
        // the PaymentToCaseBridge fires AFTER_COMMIT, so wait for AWAITING_STUDY before findings
        await().atMost(ofSeconds(5)).untilAsserted(() -> {
            var c = rest.exchange("/api/dept-cases/" + order.caseId(), HttpMethod.GET,
                    new HttpEntity<>(auth("lab")), Map.class);
            assertThat(c.getBody().get("status")).isEqualTo("AWAITING_STUDY");
        });
        post("/api/dept-cases/" + order.caseId() + "/findings",
                Map.of("serviceItemId", order.serviceItemId(), "textFindings", textFindings),
                "lab", Map.class);
    }

    String resultsUrl(String stayId, String visitId) {
        return "/api/bed-stays/PREMATURE/" + stayId + "/orders/" + visitId + "/results";
    }

    @Test @SuppressWarnings("unchecked")
    void lab_result_attachment_appears_in_merged_list_and_streams_stay_scoped() {
        String stay = admitUnderCare();
        orderLabAndUploadResult(stay);

        var list = rest.exchange(docsUrl(stay), HttpMethod.GET, new HttpEntity<>(auth("premature")), List.class);
        List<Map<String, Object>> docs = list.getBody();
        var result = docs.stream().filter(d -> "LABORATORY".equals(d.get("source"))).findFirst().orElseThrow();
        assertThat(result.get("fileName")).isEqualTo("result.png");
        String fileUrl = (String) result.get("fileUrl");

        var img = rest.exchange(fileUrl, HttpMethod.GET, new HttpEntity<>(auth("premature")), byte[].class);
        assertThat(img.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(img.getBody()).isEqualTo(PNG);
    }

    @Test
    void result_attachment_of_another_stay_is_not_streamable() {
        // two stays; attachment belongs to stay A's order; stream via stay B's URL -> 404
        String stayA = admitUnderCare();
        String stayB = admitUnderCare();
        String attachmentId = orderLabAndUploadResult(stayA).attachmentId();
        var denied = rest.exchange(docsUrl(stayB) + "/results/" + attachmentId + "/file",
                HttpMethod.GET, new HttpEntity<>(auth("premature")), String.class);
        assertThat(denied.getStatusCode().value()).isEqualTo(404);
    }

    @Test @SuppressWarnings("unchecked")
    void order_results_round_trip_returns_findings_and_documents() {
        String stay = admitUnderCare();
        LabOrder lab = orderLabAndUploadResult(stay);
        approveReferralAndUploadFindings(lab, "WBC within normal limits");

        var resp = rest.exchange(resultsUrl(stay, lab.visitId()), HttpMethod.GET,
                new HttpEntity<>(auth("premature")), Map.class);
        assertThat(resp.getStatusCode().is2xxSuccessful()).as("%s", resp.getBody()).isTrue();

        List<Map<String, Object>> services = (List<Map<String, Object>>) resp.getBody().get("services");
        assertThat(services).hasSize(1);
        assertThat(services.get(0).get("serviceName")).isEqualTo("Complete Blood Count");
        assertThat(services.get(0).get("findings")).isEqualTo("WBC within normal limits");

        List<Map<String, Object>> documents = (List<Map<String, Object>>) resp.getBody().get("documents");
        assertThat(documents).hasSize(1);
        assertThat(documents.get(0).get("fileName")).isEqualTo("result.png");
        assertThat(documents.get(0).get("source")).isEqualTo("LABORATORY");
        String fileUrl = (String) documents.get(0).get("fileUrl");
        assertThat(fileUrl).contains("/documents/results/");

        // the advertised fileUrl streams the bytes on the stay-scoped route
        var img = rest.exchange(fileUrl, HttpMethod.GET, new HttpEntity<>(auth("premature")), byte[].class);
        assertThat(img.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(img.getBody()).isEqualTo(PNG);
    }

    @Test
    void order_results_of_another_stay_is_404() {
        String stayA = admitUnderCare();
        String stayB = admitUnderCare();
        LabOrder lab = orderLabAndUploadResult(stayA);
        var denied = rest.exchange(resultsUrl(stayB, lab.visitId()), HttpMethod.GET,
                new HttpEntity<>(auth("premature")), String.class);
        assertThat(denied.getStatusCode().value()).isEqualTo(404);
    }

    @Test @SuppressWarnings("unchecked")
    void order_results_before_dept_case_opened_is_200_with_empty_lists() {
        String stay = admitUnderCare();
        String visitId = orderLab(stay); // ordered, but the lab never opened the case

        var resp = rest.exchange(resultsUrl(stay, visitId), HttpMethod.GET,
                new HttpEntity<>(auth("premature")), Map.class);
        assertThat(resp.getStatusCode().is2xxSuccessful()).as("%s", resp.getBody()).isTrue();
        assertThat((List<Object>) resp.getBody().get("services")).isEmpty();
        assertThat((List<Object>) resp.getBody().get("documents")).isEmpty();
    }
}
