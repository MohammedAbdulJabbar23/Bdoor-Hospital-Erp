package com.albudoor.hms.app.visits;

import com.albudoor.hms.app.IntegrationTest;
import com.albudoor.hms.visitmanagement.domain.Visit;
import com.albudoor.hms.visitmanagement.domain.VisitStatus;
import com.albudoor.hms.visitmanagement.domain.VisitType;
import com.albudoor.hms.visitmanagement.forwardvisit.ForwardVisitCommand;
import com.albudoor.hms.visitmanagement.forwardvisit.ForwardVisitHandler;
import com.albudoor.hms.visitmanagement.infrastructure.VisitRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration coverage for the reception-visit defect fixes:
 *
 * <ul>
 *   <li>#1 {@code GET /api/visits/summary} — DB-aggregated per-status counts + date-scoped
 *       "completed today", not paging+summing the capped listing.</li>
 *   <li>#4 archived-patient guard on visit creation → 422 PATIENT_ARCHIVED.</li>
 *   <li>#5 cancelling a parent cascade-cancels its open forwarded children.</li>
 *   <li>#6 returning results before the child is IN_PROGRESS → 422 SUBVISIT_NOT_IN_PROGRESS.</li>
 * </ul>
 */
class VisitQueueDefectsIT extends IntegrationTest {

    @Autowired TestRestTemplate rest;
    @Autowired VisitRepository visits;
    @Autowired ForwardVisitHandler forwardVisitHandler;

    // ---- HTTP helpers ----------------------------------------------------------------------

    private HttpHeaders auth(String user) {
        var login = rest.postForEntity("/api/auth/login",
                Map.of("username", user, "password", user), Map.class);
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setBearerAuth((String) login.getBody().get("token"));
        return h;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> post(String path, Map<String, Object> body, String user) {
        ResponseEntity<Map> res = rest.exchange(path, HttpMethod.POST,
                new HttpEntity<>(body, auth(user)), Map.class);
        assertThat(res.getStatusCode().is2xxSuccessful())
                .as("POST %s -> %s : %s", path, res.getStatusCode(), res.getBody()).isTrue();
        return res.getBody();
    }

    private ResponseEntity<Map> postRaw(String path, Map<String, Object> body, String user) {
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, auth(user)), Map.class);
    }

    @SuppressWarnings("unchecked")
    private void putVoid(String path, String user) {
        ResponseEntity<Map> res = rest.exchange(path, HttpMethod.PUT,
                new HttpEntity<>(Map.of(), auth(user)), Map.class);
        assertThat(res.getStatusCode().is2xxSuccessful())
                .as("PUT %s -> %s : %s", path, res.getStatusCode(), res.getBody()).isTrue();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getMap(String path, String user) {
        ResponseEntity<Map> res = rest.exchange(path, HttpMethod.GET,
                new HttpEntity<>(auth(user)), Map.class);
        assertThat(res.getStatusCode())
                .as("GET %s -> %s : %s", path, res.getStatusCode(), res.getBody())
                .isEqualTo(HttpStatus.OK);
        return res.getBody();
    }

    private Map<String, Object> registerPatient() {
        return post("/api/patients", Map.of(
                "fullName", "Visit IT " + UUID.randomUUID(), "gender", "MALE",
                "dateOfBirth", "1990-01-01",
                "mobileNumber", "0773" + (System.nanoTime() % 10_000_000L), "vip", false), "receptionist");
    }

    // ---- #1 summary ------------------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void summary_counts_per_status_from_db_aggregate_and_increases_when_a_visit_is_created() {
        Map<String, Object> before = getMap("/api/visits/summary", "receptionist");
        Map<String, Object> byStatusBefore = (Map<String, Object>) before.get("byStatus");
        long createdBefore = byStatusBefore == null ? 0
                : ((Number) byStatusBefore.getOrDefault("CREATED", 0)).longValue();

        var patient = registerPatient();
        post("/api/visits", Map.of("patientId", patient.get("id"), "visitType", "LABORATORY"), "receptionist");

        Map<String, Object> after = getMap("/api/visits/summary", "receptionist");
        Map<String, Object> byStatusAfter = (Map<String, Object>) after.get("byStatus");
        long createdAfter = ((Number) byStatusAfter.getOrDefault("CREATED", 0)).longValue();

        assertThat(createdAfter).isEqualTo(createdBefore + 1);
        // completedToday is present and numeric (only COMPLETED-with-endedAt-today are counted).
        assertThat(after.get("completedToday")).isInstanceOf(Number.class);
    }

    @Test
    void summary_requires_authentication() {
        ResponseEntity<Map> res = rest.exchange("/api/visits/summary", HttpMethod.GET,
                new HttpEntity<>(new HttpHeaders()), Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ---- #4 archived guard -----------------------------------------------------------------

    @Test
    void creating_a_visit_for_an_archived_patient_is_rejected_422() {
        var patient = registerPatient();
        String patientId = (String) patient.get("id");

        // Happy path first: a non-archived patient can start a visit.
        post("/api/visits", Map.of("patientId", patientId, "visitType", "LABORATORY"), "receptionist");

        // Archive, then a second create must be refused with PATIENT_ARCHIVED (422).
        putVoid("/api/patients/" + patientId + "/archive", "receptionist");
        ResponseEntity<Map> res = postRaw("/api/visits",
                Map.of("patientId", patientId, "visitType", "LABORATORY"), "receptionist");
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(res.getBody().get("code")).isEqualTo("PATIENT_ARCHIVED");
    }

    // ---- #5 cancel-propagation + #6 return-before-in-progress -------------------------------

    /** Sets up an IN_PROGRESS parent with one CREATED forwarded child via the real handler. */
    private ForwardedPair seedForwardedChild() {
        var patient = registerPatient();
        var created = post("/api/visits",
                Map.of("patientId", patient.get("id"), "visitType", "DOCTOR_APPOINTMENT"), "receptionist");
        UUID parentId = UUID.fromString((String) created.get("id"));

        // Move the parent to IN_PROGRESS so it can be forwarded (the doctor pause-and-wait path).
        Visit parent = visits.findById(parentId).orElseThrow();
        parent.transitionTo(VisitStatus.AWAITING_PAYMENT);
        parent.transitionTo(VisitStatus.IN_PROGRESS);
        visits.save(parent);

        var result = forwardVisitHandler.handle(parentId, new ForwardVisitCommand(VisitType.LABORATORY));
        return new ForwardedPair(parentId, result.child().getId());
    }

    @Test
    void returning_results_before_child_is_in_progress_gives_clear_422_subvisit_error() {
        ForwardedPair pair = seedForwardedChild();
        // Child is CREATED (payment not approved) — /return must be a clear domain error, not 500
        // and not the generic INVALID_VISIT_TRANSITION.
        ResponseEntity<Map> res = postRaw("/api/visits/" + pair.childId + "/return",
                Map.of("resultsSummary", "premature attempt"), "lab");
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(res.getBody().get("code")).isEqualTo("SUBVISIT_NOT_IN_PROGRESS");
    }

    @Test
    void cancelling_a_parent_cascade_cancels_its_open_forwarded_children() {
        ForwardedPair pair = seedForwardedChild();
        assertThat(visits.findById(pair.childId).orElseThrow().getStatus())
                .isNotEqualTo(VisitStatus.CANCELLED);

        post("/api/visits/" + pair.parentId + "/transition",
                Map.of("target", "CANCELLED", "reason", "Patient left"), "receptionist");

        assertThat(visits.findById(pair.parentId).orElseThrow().getStatus())
                .isEqualTo(VisitStatus.CANCELLED);
        assertThat(visits.findById(pair.childId).orElseThrow().getStatus())
                .as("open forwarded child must be cancelled with its parent")
                .isEqualTo(VisitStatus.CANCELLED);
    }

    private record ForwardedPair(UUID parentId, UUID childId) {}
}
