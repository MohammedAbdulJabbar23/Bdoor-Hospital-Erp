package com.albudoor.hms.premature.domain;

import com.albudoor.hms.platform.exception.DomainException;
import org.junit.jupiter.api.Test;

import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PrematureAdmissionTest {

    private PrematureAdmission open(StayUnit unit, int value) {
        return PrematureAdmission.open(
                UUID.randomUUID(), "VST-2026-000123",
                UUID.randomUUID(), "ALB-2026-000123", "Baby Test",
                UUID.randomUUID(), "PREM-01", value, unit);
    }

    @Test
    void open_starts_awaiting_payment_with_computed_expiry() {
        PrematureAdmission a = open(StayUnit.DAYS, 3);
        assertThat(a.getStatus()).isEqualTo(AdmissionStatus.AWAITING_ADMISSION_PAYMENT);
        assertThat(a.getStayExpiresAt()).isEqualTo(a.getAdmittedAt().plus(3, ChronoUnit.DAYS));
    }

    @Test
    void open_requires_positive_stay() {
        assertThatThrownBy(() -> open(StayUnit.HOURS, 0)).isInstanceOf(DomainException.class);
    }

    @Test
    void full_happy_path_transitions() {
        PrematureAdmission a = open(StayUnit.DAYS, 2);
        a.linkInitialPayment(UUID.randomUUID());
        a.markUnderCare();
        assertThat(a.getStatus()).isEqualTo(AdmissionStatus.UNDER_CARE);
        a.finishTreatment();
        assertThat(a.getStatus()).isEqualTo(AdmissionStatus.TREATMENT_FINISHED);
        assertThat(a.getTreatmentFinishedAt()).isNotNull();
        a.scheduleDischargePayment(UUID.randomUUID());
        assertThat(a.getStatus()).isEqualTo(AdmissionStatus.AWAITING_DISCHARGE_PAYMENT);
        a.close();
        assertThat(a.getStatus()).isEqualTo(AdmissionStatus.CLOSED);
        assertThat(a.getClosedAt()).isNotNull();
    }

    @Test
    void extend_stay_pushes_expiry() {
        PrematureAdmission a = open(StayUnit.DAYS, 2);
        a.linkInitialPayment(UUID.randomUUID());
        a.markUnderCare();
        var before = a.getStayExpiresAt();
        a.extendStay(1, StayUnit.DAYS);
        assertThat(a.getStayExpiresAt()).isEqualTo(before.plus(1, ChronoUnit.DAYS));
    }

    @Test
    void cannot_extend_before_under_care() {
        PrematureAdmission a = open(StayUnit.DAYS, 2);
        assertThatThrownBy(() -> a.extendStay(1, StayUnit.DAYS)).isInstanceOf(DomainException.class);
    }

    @Test
    void cancel_only_from_awaiting_admission_payment() {
        PrematureAdmission a = open(StayUnit.DAYS, 2);
        a.cancel();
        assertThat(a.getStatus()).isEqualTo(AdmissionStatus.CANCELLED);
    }

    @Test
    void cannot_finish_treatment_unless_under_care() {
        PrematureAdmission a = open(StayUnit.DAYS, 2);
        assertThatThrownBy(a::finishTreatment).isInstanceOf(DomainException.class);
    }

    @Test
    void close_only_from_awaiting_discharge_payment() {
        PrematureAdmission a = open(StayUnit.DAYS, 2);
        a.linkInitialPayment(UUID.randomUUID());
        a.markUnderCare();
        assertThatThrownBy(a::close).isInstanceOf(DomainException.class);
    }
}
