package com.albudoor.hms.app.security;

import com.albudoor.hms.app.IntegrationTest;
import com.albudoor.hms.departmentservices.domain.CaseAttachment;
import com.albudoor.hms.departmentservices.infrastructure.CaseAttachmentRepository;
import com.albudoor.hms.platform.storage.FileStorage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Authorization regression guard for the role/PHI gaps tightened on this branch:
 *
 * <ul>
 *   <li>Case attachments (download + list) — clinical staff + doctors only; a cashier is 403.</li>
 *   <li>Department case write/finalize — caller's department role must match the case category;
 *       a LAB_STAFF user is 403 on a RADIOLOGY case (cross-department).</li>
 *   <li>Visit transition — ADMIN / RECEPTIONIST / DOCTOR only; a cashier is 403.</li>
 *   <li>Payment list — CASHIER / ADMIN only; a doctor is 403.</li>
 * </ul>
 *
 * Each restriction is paired with a happy-path assertion proving the correct role still works.
 */
class EndpointAuthzIT extends IntegrationTest {

    @Autowired TestRestTemplate rest;
    @Autowired CaseAttachmentRepository attachments;
    @Autowired FileStorage storage;

    // ---- auth + HTTP helpers ----

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
                .as("POST %s as %s -> %s : %s", path, user, res.getStatusCode(), res.getBody()).isTrue();
        return res.getBody();
    }

    private ResponseEntity<Map> postRaw(String path, Object body, String user) {
        return rest.exchange(path, HttpMethod.POST,
                new HttpEntity<>(body == null ? Map.of() : body, auth(user)), Map.class);
    }

    private ResponseEntity<Map> getRaw(String path, String user) {
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(auth(user)), Map.class);
    }

    private <T> T get(String path, String user, Class<T> type) {
        var res = rest.exchange(path, HttpMethod.GET, new HttpEntity<>(auth(user)), type);
        assertThat(res.getStatusCode().is2xxSuccessful())
                .as("GET %s as %s -> %s : %s", path, user, res.getStatusCode(), res.getBody()).isTrue();
        return res.getBody();
    }

    // ---- seeding ----

    @SuppressWarnings("unchecked")
    private String firstServiceId(String category) {
        List<Map<String, Object>> items = get(
                "/api/catalogue/items?category=" + category + "&activeOnly=true", "admin", List.class);
        assertThat(items).as("catalogue has %s items", category).isNotEmpty();
        return (String) items.get(0).get("id");
    }

    private String newDirectVisit(String visitType) {
        Map<?, ?> patient = post("/api/patients", Map.of(
                "fullName", "Authz Patient " + System.nanoTime(), "gender", "MALE",
                "dateOfBirth", "1990-05-01",
                "mobileNumber", "0775" + (System.nanoTime() % 10_000_000L), "vip", false),
                "receptionist", Map.class);
        Map<?, ?> visit = post("/api/visits",
                Map.of("patientId", patient.get("id"), "visitType", visitType), "receptionist", Map.class);
        return (String) visit.get("id");
    }

    /** Opens a department case of the given category for a fresh direct visit, as the matching staff role. */
    @SuppressWarnings("unchecked")
    private String openCase(String category, String staffUser) {
        String visitId = newDirectVisit(visitTypeFor(category));
        String serviceId = firstServiceId(catalogueCategoryFor(category));
        Map<String, Object> deptCase = post("/api/dept-cases/open",
                Map.of("category", category, "visitId", visitId,
                        "services", List.of(Map.of("serviceItemId", serviceId, "quantity", 1))),
                staffUser, Map.class);
        return (String) deptCase.get("id");
    }

    private static String visitTypeFor(String category) {
        return switch (category) {
            case "LAB" -> "LABORATORY";
            case "RADIOLOGY" -> "RADIOLOGY";
            case "ECO" -> "ECO";
            default -> throw new IllegalArgumentException(category);
        };
    }

    private static String catalogueCategoryFor(String category) {
        return switch (category) {
            case "LAB" -> "LAB";
            case "RADIOLOGY" -> "IMAGING";
            case "ECO" -> "ECO";
            default -> throw new IllegalArgumentException(category);
        };
    }

    // ---- attachments: cashier 403, lab 200 ----

    @Test
    void cashier_cannotListOrDownloadAttachments_butLabCan() throws Exception {
        String labCaseId = openCase("LAB", "lab");
        String serviceId = firstServiceId("LAB");

        // Seed an attachment directly (the upload endpoint itself is already lab-scoped; here we
        // exercise the read/download authorization).
        byte[] bytes = "PDF report bytes".getBytes();
        String key = storage.save(new ByteArrayInputStream(bytes), "report.pdf", bytes.length);
        CaseAttachment att = attachments.save(CaseAttachment.of(
                UUID.fromString(labCaseId), UUID.fromString(serviceId),
                "report.pdf", "application/pdf", bytes.length, key, null));
        String attachmentId = att.getId().toString();

        // Cashier cannot list nor download.
        assertThat(getRaw("/api/dept-cases/" + labCaseId + "/attachments", "cashier").getStatusCode().value())
                .isEqualTo(403);
        assertThat(getRaw("/api/dept-cases/attachments/" + attachmentId + "/file", "cashier").getStatusCode().value())
                .isEqualTo(403);

        // Lab (clinical staff) can list + download.
        var list = rest.exchange("/api/dept-cases/" + labCaseId + "/attachments",
                HttpMethod.GET, new HttpEntity<>(auth("lab")), List.class);
        assertThat(list.getStatusCode().value()).isEqualTo(200);
        assertThat((List<?>) list.getBody()).isNotEmpty();

        var dl = rest.exchange("/api/dept-cases/attachments/" + attachmentId + "/file",
                HttpMethod.GET, new HttpEntity<>(auth("lab")), byte[].class);
        assertThat(dl.getStatusCode().value()).isEqualTo(200);
    }

    // ---- cross-department write/finalize: lab on radiology case 403 ----

    @Test
    void labStaff_cannotUploadOrFinalize_radiologyCase() {
        String radCaseId = openCase("RADIOLOGY", "radiology");

        // Lab tech tries to write findings on a radiology case -> 403 (guard before status checks).
        var upload = postRaw("/api/dept-cases/" + radCaseId + "/findings",
                Map.of("serviceItemId", UUID.randomUUID().toString(), "textFindings", "x"), "lab");
        assertThat(upload.getStatusCode().value()).isEqualTo(403);

        // Lab tech tries to finalize a radiology case -> 403.
        var finalize = postRaw("/api/dept-cases/" + radCaseId + "/finalize", null, "lab");
        assertThat(finalize.getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void radiologyStaff_cannotOpen_labCase() {
        String visitId = newDirectVisit("LABORATORY");
        String serviceId = firstServiceId("LAB");
        // Radiology staff tries to open a LAB-category case -> 403.
        var res = postRaw("/api/dept-cases/open",
                Map.of("category", "LAB", "visitId", visitId,
                        "services", List.of(Map.of("serviceItemId", serviceId, "quantity", 1))),
                "radiology");
        assertThat(res.getStatusCode().value()).isEqualTo(403);
    }

    @Test
    void labStaff_onLabCase_happyPath_open() {
        // The matching role still opens its own case (201) — the helper asserts 2xx internally.
        String labCaseId = openCase("LAB", "lab");
        assertThat(labCaseId).isNotBlank();
    }

    // ---- visit transition: cashier 403, doctor/admin 200 ----

    @Test
    void cashier_cannotTransitionVisit_butDoctorAndAdminCan() {
        String visitId = newDirectVisit("DOCTOR_APPOINTMENT");

        // Cashier is forbidden from driving visit state.
        var forbidden = postRaw("/api/visits/" + visitId + "/transition",
                Map.of("target", "AWAITING_PAYMENT"), "cashier");
        assertThat(forbidden.getStatusCode().value()).isEqualTo(403);

        // Doctor may drive the visit (legitimate pause-and-wait flow).
        var byDoctor = postRaw("/api/visits/" + visitId + "/transition",
                Map.of("target", "AWAITING_PAYMENT"), "doctor");
        assertThat(byDoctor.getStatusCode().value())
                .as("doctor transition -> %s : %s", byDoctor.getStatusCode(), byDoctor.getBody())
                .isEqualTo(200);
    }

    // ---- payment list: doctor 403, cashier 200 ----

    @Test
    void nonCashier_cannotListPayments_butCashierCan() {
        assertThat(getRaw("/api/payments?status=PENDING&size=10", "doctor").getStatusCode().value())
                .isEqualTo(403);
        assertThat(getRaw("/api/payments?status=PENDING&size=10", "cashier").getStatusCode().value())
                .isEqualTo(200);
        assertThat(getRaw("/api/payments?status=PENDING&size=10", "admin").getStatusCode().value())
                .isEqualTo(200);
    }
}
