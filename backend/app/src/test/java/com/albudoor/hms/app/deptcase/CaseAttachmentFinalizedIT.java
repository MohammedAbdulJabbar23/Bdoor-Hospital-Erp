package com.albudoor.hms.app.deptcase;

import com.albudoor.hms.app.IntegrationTest;
import com.albudoor.hms.departmentservices.domain.CaseServiceLine;
import com.albudoor.hms.departmentservices.domain.DepartmentCase;
import com.albudoor.hms.departmentservices.domain.DepartmentCaseStatus;
import com.albudoor.hms.departmentservices.domain.DepartmentCategory;
import com.albudoor.hms.departmentservices.infrastructure.DepartmentCaseRepository;
import com.albudoor.hms.visitmanagement.domain.VisitOrigin;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that uploading an attachment to a finalized/terminal department case is rejected
 * with 422 CASE_FINALIZED. A real patient + visit + service item + payment are created over
 * HTTP so the case's FKs (visit, payment, service item) all resolve, then the case is driven
 * to CLOSED via domain methods (avoiding the full forward/findings HTTP choreography).
 */
class CaseAttachmentFinalizedIT extends IntegrationTest {

    @Autowired TestRestTemplate rest;
    @Autowired DepartmentCaseRepository cases;

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

    @SuppressWarnings("unchecked")
    private Map<String, Object> postOk(String path, Object body, String user) {
        ResponseEntity<Map> res = rest.exchange(path, HttpMethod.POST,
                new HttpEntity<>(body, auth(user)), Map.class);
        assertThat(res.getStatusCode().is2xxSuccessful())
                .as("POST %s -> %s : %s", path, res.getStatusCode(), res.getBody()).isTrue();
        return res.getBody();
    }

    private DepartmentCase persistClosedCase() {
        Map<String, Object> item = postOk("/api/catalogue/items", Map.of(
                "category", "LAB", "code", "CBC-" + System.nanoTime(),
                "nameEn", "Complete Blood Count", "fee", 5000, "currency", "IQD"), "admin");
        UUID serviceItemId = UUID.fromString((String) item.get("id"));

        Map<String, Object> patient = postOk("/api/patients", Map.of(
                "fullName", "Att Patient " + System.nanoTime(),
                "gender", "MALE", "dateOfBirth", "1990-01-01",
                "mobileNumber", "0770" + (System.nanoTime() % 10_000_000L), "vip", false),
                "receptionist");
        Map<String, Object> visit = postOk("/api/visits", Map.of(
                "patientId", patient.get("id"), "visitType", "LABORATORY"), "receptionist");

        Map<String, Object> payment = postOk("/api/payments", Map.of(
                "visitId", visit.get("id"), "stage", "INITIAL",
                "lines", List.of(Map.of("serviceItemId", serviceItemId.toString(), "quantity", 1)),
                "currency", "IQD"), "cashier");

        DepartmentCase c = DepartmentCase.open(
                DepartmentCategory.LAB,
                UUID.fromString((String) visit.get("id")), (String) visit.get("visitDisplayId"),
                VisitOrigin.DIRECT_NEW, null,
                UUID.fromString((String) patient.get("id")),
                (String) patient.get("mrn"), (String) patient.get("fullName"));
        c.addService(CaseServiceLine.pending(serviceItemId, (String) item.get("code"),
                "Complete Blood Count", new BigDecimal("5000")));
        c.linkPayment(UUID.fromString((String) payment.get("id")));
        c.onPaymentApproved(); // AWAITING_STUDY
        c.uploadFindings(serviceItemId, "WBC normal", null, null, null, "NORMAL",
                null, null, null, null, UUID.randomUUID()); // FINDINGS_COMPLETE
        c.markFinalized(DepartmentCaseStatus.CLOSED, c.buildResultsSummary());
        return cases.save(c);
    }

    @Test
    void upload_to_finalized_case_is_rejected_422() {
        DepartmentCase closed = persistClosedCase();
        UUID serviceItemId = closed.getServices().get(0).getServiceItemId();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token("admin"));
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        ByteArrayResource file = new ByteArrayResource("late scan".getBytes()) {
            @Override public String getFilename() { return "late.pdf"; }
        };
        form.add("file", file);

        ResponseEntity<Map> res = rest.exchange(
                "/api/dept-cases/" + closed.getId() + "/services/" + serviceItemId + "/attachments",
                HttpMethod.POST, new HttpEntity<>(form, headers), Map.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(res.getBody().get("code")).isEqualTo("CASE_FINALIZED");
    }
}
