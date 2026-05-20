package com.albudoor.hms.pharmacy.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One drug line on a dispense. Snapshot of the prescription entry plus a pricing snapshot
 * (unit fee + line total) captured at dispense creation time. A line is billable iff
 * {@code drugServiceItemId} resolved to an active catalogue drug at creation time;
 * otherwise the line is informational only.
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DispenseLine {

    @Column(name = "drug_service_item_id")
    private UUID drugServiceItemId;

    @Column(name = "drug_code", length = 50)
    private String drugCode;

    @Column(name = "drug_name", nullable = false, length = 300)
    private String drugName;

    @Column(name = "strength", length = 100)
    private String strength;

    @Column(name = "dose", length = 100)
    private String dose;

    @Column(name = "frequency", length = 100)
    private String frequency;

    @Column(name = "duration", length = 100)
    private String duration;

    @Column(name = "route", length = 50)
    private String route;

    @Column(name = "notes", length = 500)
    private String notes;

    @Column(name = "unit_fee", precision = 12, scale = 2)
    private BigDecimal unitFee;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "line_total", precision = 12, scale = 2)
    private BigDecimal lineTotal;

    public static DispenseLine billable(
            UUID drugServiceItemId, String drugCode, String drugName,
            String strength, String dose, String frequency, String duration,
            String route, String notes,
            BigDecimal unitFee, int quantity
    ) {
        DispenseLine l = new DispenseLine();
        l.drugServiceItemId = drugServiceItemId;
        l.drugCode = drugCode;
        l.drugName = drugName;
        l.strength = strength;
        l.dose = dose;
        l.frequency = frequency;
        l.duration = duration;
        l.route = route;
        l.notes = notes;
        l.unitFee = unitFee;
        l.quantity = Math.max(1, quantity);
        l.lineTotal = unitFee == null ? null : unitFee.multiply(BigDecimal.valueOf(l.quantity));
        return l;
    }

    public static DispenseLine informational(
            String drugName, String strength, String dose, String frequency,
            String duration, String route, String notes
    ) {
        DispenseLine l = new DispenseLine();
        l.drugName = drugName;
        l.strength = strength;
        l.dose = dose;
        l.frequency = frequency;
        l.duration = duration;
        l.route = route;
        l.notes = notes;
        l.quantity = 1;
        return l;
    }

    public boolean isBillable() {
        return drugServiceItemId != null && unitFee != null;
    }
}
