package com.albudoor.hms.pharmacy.api;

import com.albudoor.hms.pharmacy.domain.DispenseLine;
import com.albudoor.hms.pharmacy.domain.DispenseStatus;
import com.albudoor.hms.pharmacy.domain.PharmacyDispense;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DispenseResponse(
        UUID id,
        String dispenseDisplayId,
        UUID examId,
        UUID visitId,
        String visitDisplayId,
        UUID patientId,
        String patientMrn,
        String patientName,
        UUID doctorId,
        String doctorName,
        DispenseStatus status,
        UUID chargePaymentId,
        Instant chargedAt,
        Instant paidAt,
        Instant givenAt,
        UUID givenByUserId,
        Instant cancelledAt,
        String cancelledReason,
        BigDecimal billableTotal,
        Instant createdAt,
        List<Line> lines
) {
    public record Line(
            UUID drugServiceItemId,
            String drugCode,
            String drugName,
            String strength,
            String dose,
            String frequency,
            String duration,
            String route,
            String notes,
            BigDecimal unitFee,
            int quantity,
            BigDecimal lineTotal,
            boolean billable
    ) {}

    public static DispenseResponse from(PharmacyDispense d) {
        List<Line> lines = d.getLines().stream().map(DispenseResponse::lineOf).toList();
        return new DispenseResponse(
                d.getId(), d.getDispenseDisplayId(),
                d.getExamId(), d.getVisitId(), d.getVisitDisplayId(),
                d.getPatientId(), d.getPatientMrn(), d.getPatientName(),
                d.getDoctorId(), d.getDoctorName(),
                d.getStatus(),
                d.getChargePaymentId(), d.getChargedAt(),
                d.getPaidAt(),
                d.getGivenAt(), d.getGivenByUserId(),
                d.getCancelledAt(), d.getCancelledReason(),
                d.billableTotal(),
                d.getCreatedAt(),
                lines
        );
    }

    private static Line lineOf(DispenseLine l) {
        return new Line(
                l.getDrugServiceItemId(), l.getDrugCode(), l.getDrugName(),
                l.getStrength(), l.getDose(), l.getFrequency(), l.getDuration(),
                l.getRoute(), l.getNotes(),
                l.getUnitFee(), l.getQuantity(), l.getLineTotal(),
                l.isBillable()
        );
    }
}
