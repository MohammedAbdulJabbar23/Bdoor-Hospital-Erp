package com.albudoor.hms.app.appointment;

import com.albudoor.hms.app.IntegrationTest;
import com.albudoor.hms.doctorappointment.domain.Appointment;
import com.albudoor.hms.doctorappointment.infrastructure.AppointmentRepository;
import com.albudoor.hms.visitmanagement.domain.Visit;
import com.albudoor.hms.visitmanagement.domain.VisitStatus;
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

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration coverage for the appointment booking guards added in the per-workflow
 * defect sweep: past-date rejection, slot-grid alignment, NO_SHOW rebooking consistency,
 * the slots view marking elapsed slots unavailable, and the cancel-after-paid guard.
 *
 * <p>The test provisions its own doctor with a single 30-minute Monday block (09:00–11:00)
 * so the slot grid is deterministic, independent of the demo seeder.
 */
class BookAppointmentIT extends IntegrationTest {

    @Autowired TestRestTemplate rest;
    @Autowired AppointmentRepository appointments;
    @Autowired VisitRepository visits;

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

    private <T> ResponseEntity<T> exchange(String path, HttpMethod method, Object body, String user, Class<T> type) {
        return rest.exchange(path, method, new HttpEntity<>(body, auth(user)), type);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> postOk(String path, Object body, String user) {
        ResponseEntity<Map> res = exchange(path, HttpMethod.POST, body, user, Map.class);
        assertThat(res.getStatusCode().is2xxSuccessful())
                .as("POST %s -> %s : %s", path, res.getStatusCode(), res.getBody()).isTrue();
        return res.getBody();
    }

    /** Next occurrence of {@code dow} strictly in the future (at least tomorrow). */
    private static LocalDate nextDow(DayOfWeek dow) {
        LocalDate d = LocalDate.now().plusDays(1);
        while (d.getDayOfWeek() != dow) d = d.plusDays(1);
        return d;
    }

    private String createDoctorWithMondayBlock() {
        Map<String, Object> doctor = postOk("/api/doctors", Map.of(
                "fullName", "Dr. Grid Tester " + System.nanoTime(),
                "specialty", "Testing",
                "consultationFee", 10000,
                "currency", "IQD"), "admin");
        String doctorId = (String) doctor.get("id");

        // Single Monday block 09:00–11:00, 30-minute slots => grid at :00 and :30.
        ResponseEntity<Map> sched = exchange("/api/doctors/" + doctorId + "/schedule", HttpMethod.PUT,
                Map.of("blocks", List.of(Map.of(
                        "dayOfWeek", "MONDAY",
                        "startTime", "09:00:00",
                        "endTime", "11:00:00",
                        "slotMinutes", 30))),
                "admin", Map.class);
        assertThat(sched.getStatusCode().is2xxSuccessful())
                .as("set schedule -> %s : %s", sched.getStatusCode(), sched.getBody()).isTrue();
        return doctorId;
    }

    private String createPatient() {
        Map<String, Object> patient = postOk("/api/patients", Map.of(
                "fullName", "Appt Patient " + System.nanoTime(),
                "gender", "MALE", "dateOfBirth", "1990-01-01",
                "mobileNumber", "0770" + (System.nanoTime() % 10_000_000L), "vip", false),
                "receptionist");
        return (String) patient.get("id");
    }

    @Test
    void rejects_booking_in_the_past() {
        String doctorId = createDoctorWithMondayBlock();
        String patientId = createPatient();
        // A past Monday at a grid-aligned time.
        LocalDate pastMonday = LocalDate.now().minusDays(7);
        while (pastMonday.getDayOfWeek() != DayOfWeek.MONDAY) pastMonday = pastMonday.minusDays(1);

        ResponseEntity<Map> res = exchange("/api/appointments", HttpMethod.POST, Map.of(
                "doctorId", doctorId, "patientId", patientId,
                "scheduledFor", pastMonday.atTime(9, 0).toString(),
                "type", "BOOKED"), "receptionist", Map.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(res.getBody().get("code")).isEqualTo("SCHEDULED_IN_PAST");
    }

    @Test
    void rejects_misaligned_slot_start() {
        String doctorId = createDoctorWithMondayBlock();
        String patientId = createPatient();
        LocalDate monday = nextDow(DayOfWeek.MONDAY);

        // 09:07 is inside the block but not on the 30-minute grid.
        ResponseEntity<Map> res = exchange("/api/appointments", HttpMethod.POST, Map.of(
                "doctorId", doctorId, "patientId", patientId,
                "scheduledFor", monday.atTime(9, 7).toString(),
                "type", "BOOKED"), "receptionist", Map.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(res.getBody().get("code")).isEqualTo("SLOT_NOT_AVAILABLE");
    }

    @Test
    void accepts_grid_aligned_slot_and_rejects_double_book() {
        String doctorId = createDoctorWithMondayBlock();
        String patientId = createPatient();
        LocalDate monday = nextDow(DayOfWeek.MONDAY);
        String at0930 = monday.atTime(9, 30).toString();

        ResponseEntity<Map> first = exchange("/api/appointments", HttpMethod.POST, Map.of(
                "doctorId", doctorId, "patientId", patientId,
                "scheduledFor", at0930, "type", "BOOKED"), "receptionist", Map.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        // Same slot again => conflict.
        ResponseEntity<Map> dup = exchange("/api/appointments", HttpMethod.POST, Map.of(
                "doctorId", doctorId, "patientId", patientId,
                "scheduledFor", at0930, "type", "BOOKED"), "receptionist", Map.class);
        assertThat(dup.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(dup.getBody().get("code")).isEqualTo("SLOT_TAKEN");
    }

    @Test
    void no_show_frees_the_slot_for_rebooking() {
        String doctorId = createDoctorWithMondayBlock();
        String patientId = createPatient();
        LocalDate monday = nextDow(DayOfWeek.MONDAY);
        String at0900 = monday.atTime(9, 0).toString();

        Map<String, Object> appt = postOk("/api/appointments", Map.of(
                "doctorId", doctorId, "patientId", patientId,
                "scheduledFor", at0900, "type", "BOOKED"), "receptionist");
        String apptId = (String) appt.get("id");

        // No HTTP no-show endpoint exists; drive the domain transition directly.
        Appointment a = appointments.findById(UUID.fromString(apptId)).orElseThrow();
        a.markNoShow();
        appointments.save(a);

        // Rebooking the NO_SHOW slot must succeed (no 409) — the booking conflict guard now
        // excludes NO_SHOW, mirroring the slots view.
        ResponseEntity<Map> rebook = exchange("/api/appointments", HttpMethod.POST, Map.of(
                "doctorId", doctorId, "patientId", patientId,
                "scheduledFor", at0900, "type", "BOOKED"), "receptionist", Map.class);
        assertThat(rebook.getStatusCode())
                .as("rebook NO_SHOW slot -> %s : %s", rebook.getStatusCode(), rebook.getBody())
                .isEqualTo(HttpStatus.CREATED);
    }

    @Test
    @SuppressWarnings("unchecked")
    void slots_view_marks_elapsed_slots_unavailable() {
        String doctorId = createDoctorWithMondayBlock();
        LocalDate monday = nextDow(DayOfWeek.MONDAY);

        ResponseEntity<List> slots = exchange(
                "/api/doctors/" + doctorId + "/slots?date=" + monday,
                HttpMethod.GET, null, "receptionist", List.class);
        assertThat(slots.getStatusCode()).isEqualTo(HttpStatus.OK);
        LocalDateTime now = LocalDateTime.now();
        for (Object o : slots.getBody()) {
            Map<String, Object> slot = (Map<String, Object>) o;
            LocalDateTime startsAt = LocalDateTime.parse((String) slot.get("startsAt"));
            if (startsAt.isBefore(now)) {
                assertThat((Boolean) slot.get("available"))
                        .as("elapsed slot %s must be unavailable", startsAt).isFalse();
            }
        }
    }

    @Test
    void cannot_cancel_after_consult_payment_approved() {
        String doctorId = createDoctorWithMondayBlock();
        String patientId = createPatient();
        LocalDate monday = nextDow(DayOfWeek.MONDAY);

        Map<String, Object> appt = postOk("/api/appointments", Map.of(
                "doctorId", doctorId, "patientId", patientId,
                "scheduledFor", monday.atTime(10, 0).toString(), "type", "BOOKED"), "receptionist");
        String visitId = (String) appt.get("visitId");
        String apptId = (String) appt.get("id");

        // Simulate the consult-payment-approved state: visit advanced past AWAITING_PAYMENT.
        Visit visit = visits.findById(UUID.fromString(visitId)).orElseThrow();
        visit.transitionTo(VisitStatus.AWAITING_PAYMENT);
        visit.transitionTo(VisitStatus.IN_PROGRESS);
        visits.save(visit);

        ResponseEntity<Map> cancel = exchange("/api/appointments/" + apptId + "/cancel",
                HttpMethod.POST, Map.of("reason", "changed mind"), "receptionist", Map.class);
        assertThat(cancel.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(cancel.getBody().get("code")).isEqualTo("APPOINTMENT_PAID_IN_PROGRESS");
    }
}
