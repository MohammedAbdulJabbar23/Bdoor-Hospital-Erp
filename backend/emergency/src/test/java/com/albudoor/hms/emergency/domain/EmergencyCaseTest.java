package com.albudoor.hms.emergency.domain;

import com.albudoor.hms.platform.exception.DomainException;
import org.junit.jupiter.api.Test;

import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmergencyCaseTest {

    private EmergencyCase open(StayUnit unit, int value) {
        return EmergencyCase.open(
                UUID.randomUUID(), "VST-2026-000200",
                UUID.randomUUID(), "ALB-2026-000200", "Adult Test",
                UUID.randomUUID(), "EMRG-01",
                UUID.randomUUID(), "EM-001", "Emergency Admission",
                value, unit);
    }

    @Test
    void open_starts_awaiting_payment_with_expiry_and_service() {
        EmergencyCase c = open(StayUnit.HOURS, 6);
        assertThat(c.getStatus()).isEqualTo(EmergencyCaseStatus.AWAITING_INITIAL_PAYMENT);
        assertThat(c.getServiceCode()).isEqualTo("EM-001");
        assertThat(c.getStayExpiresAt()).isEqualTo(c.getAdmittedAt().plus(6, ChronoUnit.HOURS));
    }

    @Test
    void open_requires_positive_stay_and_service() {
        assertThatThrownBy(() -> open(StayUnit.HOURS, 0)).isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> EmergencyCase.open(UUID.randomUUID(), "V", UUID.randomUUID(), "M", "N",
                UUID.randomUUID(), "EMRG-01", null, null, null, 6, StayUnit.HOURS))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void full_happy_path() {
        EmergencyCase c = open(StayUnit.DAYS, 1);
        c.linkInitialPayment(UUID.randomUUID());
        c.markUnderTreatment();
        assertThat(c.getStatus()).isEqualTo(EmergencyCaseStatus.UNDER_TREATMENT);
        c.finishTreatment();
        assertThat(c.getStatus()).isEqualTo(EmergencyCaseStatus.TREATMENT_FINISHED);
        assertThat(c.getTreatmentFinishedAt()).isNotNull();
        c.scheduleDischargePayment(UUID.randomUUID());
        assertThat(c.getStatus()).isEqualTo(EmergencyCaseStatus.AWAITING_DISCHARGE_PAYMENT);
        c.close();
        assertThat(c.getStatus()).isEqualTo(EmergencyCaseStatus.CLOSED);
        assertThat(c.getClosedAt()).isNotNull();
    }

    @Test
    void cancel_only_from_awaiting_initial_payment() {
        EmergencyCase c = open(StayUnit.DAYS, 1);
        c.cancel();
        assertThat(c.getStatus()).isEqualTo(EmergencyCaseStatus.CANCELLED);
    }

    @Test
    void reissue_from_awaiting_discharge_payment() {
        EmergencyCase c = open(StayUnit.DAYS, 1);
        c.linkInitialPayment(UUID.randomUUID());
        c.markUnderTreatment();
        c.finishTreatment();
        c.scheduleDischargePayment(UUID.randomUUID());
        UUID p2 = UUID.randomUUID();
        c.reissueDischargePayment(p2);
        assertThat(c.getStatus()).isEqualTo(EmergencyCaseStatus.AWAITING_DISCHARGE_PAYMENT);
        assertThat(c.getFinalPaymentId()).isEqualTo(p2);
    }

    @Test
    void cannot_finish_unless_under_treatment() {
        EmergencyCase c = open(StayUnit.DAYS, 1);
        assertThatThrownBy(c::finishTreatment).isInstanceOf(DomainException.class);
    }
}
