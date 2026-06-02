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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that uploading an attachment to a finalized/terminal department case is rejected
 * with 422 CASE_FINALIZED. The case is driven to CLOSED directly via domain methods so the
 * test does not depend on the full forward/pay/findings HTTP choreography.
 */
class CaseAttachmentFinalizedIT extends IntegrationTest {

    @Autowired TestRestTemplate rest;
    @Autowired DepartmentCaseRepository cases;

    private String token(String user) {
        var res = rest.postForEntity("/api/auth/login",
                Map.of("username", user, "password", user), Map.class);
        return (String) res.getBody().get("token");
    }

    @Transactional
    DepartmentCase persistClosedCase() {
        DepartmentCase c = DepartmentCase.open(
                DepartmentCategory.LAB,
                UUID.randomUUID(), "V-ATT-" + System.nanoTime(),
                VisitOrigin.DIRECT_NEW, null,
                UUID.randomUUID(), "MRN-ATT", "Att Patient");
        UUID serviceItemId = UUID.randomUUID();
        c.addService(CaseServiceLine.pending(serviceItemId, "CBC", "Complete Blood Count", new BigDecimal("5000")));
        c.linkPayment(UUID.randomUUID());
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
