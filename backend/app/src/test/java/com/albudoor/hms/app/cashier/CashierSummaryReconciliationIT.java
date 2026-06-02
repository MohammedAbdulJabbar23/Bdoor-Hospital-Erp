package com.albudoor.hms.app.cashier;

import com.albudoor.hms.app.IntegrationTest;
import com.albudoor.hms.cashier.domain.PaymentStatus;
import com.albudoor.hms.cashier.infrastructure.PaymentRepository;
import com.albudoor.hms.premature.domain.Bed;
import com.albudoor.hms.premature.infrastructure.BedRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration coverage for the server-side cashier reporting endpoints:
 *
 * <ul>
 *   <li>{@code GET /api/payments/summary} — uncapped KPIs, VIP-bypass excluded from
 *       {@code receivedToday} / {@code approvedTodayCount}.</li>
 *   <li>{@code GET /api/payments?q=…} — case-insensitive search matching patient name, MRN,
 *       payment display id, visit display id across the whole queue (not just one page).</li>
 *   <li>{@code GET /api/payments/reconciliation} — APPROVED totals grouped by method/stage,
 *       grand total, VIP-bypass broken out, cash-collected excluding VIP.</li>
 * </ul>
 */
class CashierSummaryReconciliationIT extends IntegrationTest {

    @Autowired TestRestTemplate rest;
    @Autowired PaymentRepository payments;
    @Autowired BedRepository beds;

    // ---- HTTP helpers ----------------------------------------------------------------------

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

    @SuppressWarnings("unchecked")
    private Map<String, Object> getMap(String path, String user) {
        ResponseEntity<Map> res = rest.exchange(path, HttpMethod.GET,
                new HttpEntity<>(auth(user)), Map.class);
        assertThat(res.getStatusCode())
                .as("GET %s -> %s : %s", path, res.getStatusCode(), res.getBody())
                .isEqualTo(HttpStatus.OK);
        return res.getBody();
    }

    private double dbl(Map<String, Object> body, String key) {
        Object v = body.get(key);
        assertThat(v).as("field %s present and numeric", key).isInstanceOf(Number.class);
        return ((Number) v).doubleValue();
    }

    // ---- Seeding ---------------------------------------------------------------------------

    /** Seeds a premature visit (creating an unapproved PENDING payment) and returns identifiers. */
    private Seed seedPrematureAdmission(boolean vip) {
        String name = "Cashier IT " + (vip ? "VIP " : "") + UUID.randomUUID();
        Map<?, ?> patient = post("/api/patients", Map.of(
                        "fullName", name,
                        "gender", "MALE", "dateOfBirth", "2026-05-01",
                        "mobileNumber", "0770" + (System.nanoTime() % 10_000_000L), "vip", vip),
                "receptionist", Map.class);
        String patientId = (String) patient.get("id");
        String mrn = (String) patient.get("mrn");

        Map<?, ?> visit = post("/api/visits",
                Map.of("patientId", patientId, "visitType", "PREMATURE"), "receptionist", Map.class);
        String visitId = (String) visit.get("id");

        Bed bed = beds.save(Bed.create("PREM-CASHIT-" + System.nanoTime(), "CASHIT"));
        post("/api/premature/admissions",
                Map.of("visitId", visitId, "bedId", bed.getId().toString(), "stayValue", 2, "stayUnit", "DAYS"),
                "premature", Map.class);

        return new Seed(name, mrn, UUID.fromString(visitId));
    }

    private record Seed(String patientName, String mrn, UUID visitId) {}

    // ---- Tests -----------------------------------------------------------------------------

    @Test
    void summary_counts_pending_and_excludes_vip_bypass_from_received_today() {
        Map<String, Object> before = getMap("/api/payments/summary", "cashier");
        long pendingBefore = (long) dbl(before, "pendingCount");
        double receivedBefore = dbl(before, "receivedToday");
        long approvedTodayBefore = (long) dbl(before, "approvedTodayCount");
        double pendingTotalBefore = dbl(before, "pendingTotal");

        // (1) A non-VIP pending payment bumps pendingCount + pendingTotal.
        Seed normal = seedPrematureAdmission(false);
        var normalPending = payments.findAllByVisitIdOrderByCreatedAtDesc(normal.visitId()).stream()
                .filter(p -> p.getStatus() == PaymentStatus.PENDING).findFirst().orElseThrow();
        double normalDue = normalPending.getTotalDue().doubleValue();

        Map<String, Object> afterPending = getMap("/api/payments/summary", "cashier");
        assertThat((long) dbl(afterPending, "pendingCount")).isEqualTo(pendingBefore + 1);
        assertThat(dbl(afterPending, "pendingTotal")).isCloseTo(pendingTotalBefore + normalDue, org.assertj.core.data.Offset.offset(0.01));

        // (2) A VIP admission auto-approves a VIP_BYPASS payment today. It must NOT count toward
        //     receivedToday / approvedTodayCount (no money changed hands).
        seedPrematureAdmission(true);
        Map<String, Object> afterVip = getMap("/api/payments/summary", "cashier");
        assertThat(dbl(afterVip, "receivedToday"))
                .as("VIP-bypass approvals do not count as cash received")
                .isCloseTo(receivedBefore, org.assertj.core.data.Offset.offset(0.01));
        assertThat((long) dbl(afterVip, "approvedTodayCount")).isEqualTo(approvedTodayBefore);

        // (3) Approving the normal pending payment (real method) DOES bump receivedToday.
        post("/api/payments/" + normalPending.getId() + "/approve", Map.of("paymentMethod", "CASH"), "cashier", Map.class);
        Map<String, Object> afterApprove = getMap("/api/payments/summary", "cashier");
        assertThat(dbl(afterApprove, "receivedToday")).isCloseTo(receivedBefore + normalDue, org.assertj.core.data.Offset.offset(0.01));
        assertThat((long) dbl(afterApprove, "approvedTodayCount")).isEqualTo(approvedTodayBefore + 1);
        assertThat((long) dbl(afterApprove, "pendingCount")).isEqualTo(pendingBefore);

        // Per-stage pending counts present.
        assertThat(afterApprove.get("pendingByStage")).isInstanceOf(Map.class);
    }

    @Test
    @SuppressWarnings("unchecked")
    void search_q_matches_name_mrn_and_display_ids_across_whole_queue() {
        Seed s = seedPrematureAdmission(false);
        var pending = payments.findAllByVisitIdOrderByCreatedAtDesc(s.visitId()).stream()
                .filter(p -> p.getStatus() == PaymentStatus.PENDING).findFirst().orElseThrow();
        String paymentDisplayId = pending.getPaymentDisplayId();
        String visitDisplayId = pending.getVisitDisplayId();

        // By full patient name (lower-cased — verifies case-insensitivity); this is the exact
        // path the lifecycle e2e exercise (type the full name, expect the row).
        assertThat(displayIdsFor("q=" + url(s.patientName().toLowerCase()))).contains(paymentDisplayId);
        // By MRN.
        assertThat(displayIdsFor("q=" + url(s.mrn()))).contains(paymentDisplayId);
        // By payment display id.
        assertThat(displayIdsFor("q=" + url(paymentDisplayId))).contains(paymentDisplayId);
        // By visit display id.
        assertThat(displayIdsFor("q=" + url(visitDisplayId))).contains(paymentDisplayId);
        // A query that cannot match returns no rows for this payment.
        assertThat(displayIdsFor("q=" + url("zzz-no-such-" + UUID.randomUUID()))).doesNotContain(paymentDisplayId);
    }

    /** Calls the list endpoint with the given query string suffix and returns paymentDisplayIds. */
    @SuppressWarnings("unchecked")
    private List<String> displayIdsFor(String query) {
        ResponseEntity<Map> res = rest.exchange("/api/payments?status=PENDING&size=100&" + query,
                HttpMethod.GET, new HttpEntity<>(auth("cashier")), Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> content = (List<Map<String, Object>>) res.getBody().get("content");
        return content.stream().map(m -> (String) m.get("paymentDisplayId")).toList();
    }

    private static String url(String v) {
        return java.net.URLEncoder.encode(v, java.nio.charset.StandardCharsets.UTF_8);
    }

    @Test
    @SuppressWarnings("unchecked")
    void reconciliation_groups_approved_by_method_and_stage_and_breaks_out_vip() {
        LocalDate today = LocalDate.now();

        // Approve a normal payment via CARD today.
        Seed normal = seedPrematureAdmission(false);
        var pending = payments.findAllByVisitIdOrderByCreatedAtDesc(normal.visitId()).stream()
                .filter(p -> p.getStatus() == PaymentStatus.PENDING).findFirst().orElseThrow();
        double cardDue = pending.getTotalDue().doubleValue();
        post("/api/payments/" + pending.getId() + "/approve", Map.of("paymentMethod", "CARD"), "cashier", Map.class);

        // A VIP admission auto-approves a VIP_BYPASS payment today.
        Seed vip = seedPrematureAdmission(true);
        var vipApproved = payments.findAllByVisitIdOrderByCreatedAtDesc(vip.visitId()).stream()
                .filter(p -> p.getStatus() == PaymentStatus.APPROVED).findFirst().orElseThrow();
        double vipDue = vipApproved.getTotalDue().doubleValue();

        Map<String, Object> recon = getMap("/api/payments/reconciliation?date=" + today, "cashier");

        assertThat(recon.get("date")).isEqualTo(today.toString());

        // by-method contains CARD and VIP_BYPASS rows.
        List<Map<String, Object>> byMethod = (List<Map<String, Object>>) recon.get("byMethod");
        assertThat(byMethod).extracting(m -> (String) m.get("key")).contains("CARD", "VIP_BYPASS");

        // by-stage contains the INITIAL stage (premature admission INITIAL payment).
        List<Map<String, Object>> byStage = (List<Map<String, Object>>) recon.get("byStage");
        assertThat(byStage).extracting(m -> (String) m.get("key")).contains("INITIAL");

        // VIP-bypass is broken out and equals the VIP payment due (at least — other VIPs may exist).
        Map<String, Object> vipTotal = (Map<String, Object>) recon.get("vipBypass");
        assertThat(((Number) vipTotal.get("total")).doubleValue()).isGreaterThanOrEqualTo(vipDue);
        assertThat(((Number) vipTotal.get("count")).longValue()).isGreaterThanOrEqualTo(1);

        // grandTotal = cashCollected + vipBypass (cash collected excludes VIP-bypass).
        Map<String, Object> grand = (Map<String, Object>) recon.get("grandTotal");
        Map<String, Object> cash = (Map<String, Object>) recon.get("cashCollected");
        double grandV = ((Number) grand.get("total")).doubleValue();
        double cashV = ((Number) cash.get("total")).doubleValue();
        double vipV = ((Number) vipTotal.get("total")).doubleValue();
        assertThat(cashV).isCloseTo(grandV - vipV, org.assertj.core.data.Offset.offset(0.01));
        // The CARD approval contributes to cash collected.
        assertThat(cashV).isGreaterThanOrEqualTo(cardDue);
    }

    @Test
    void summary_and_reconciliation_require_cashier_or_admin() {
        ResponseEntity<Map> summary = rest.exchange("/api/payments/summary", HttpMethod.GET,
                HttpEntity.EMPTY, Map.class);
        assertThat(summary.getStatusCode().is2xxSuccessful()).isFalse();

        ResponseEntity<Map> recon = rest.exchange("/api/payments/reconciliation", HttpMethod.GET,
                HttpEntity.EMPTY, Map.class);
        assertThat(recon.getStatusCode().is2xxSuccessful()).isFalse();
    }
}
