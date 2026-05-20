package com.albudoor.hms.cashier.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One line on a payment — a snapshot of a service-catalogue item at the time of billing.
 * Stored in {@code payment_line_item} via {@code @ElementCollection}.
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class PaymentLineItem {

    /** Catalogue service item; null for ad-hoc lines (e.g. consult fee priced from doctor.consultationFee). */
    @Column(name = "service_item_id")
    private UUID serviceItemId;

    @Column(name = "service_code", nullable = false, length = 50)
    private String serviceCode;

    @Column(name = "service_name", nullable = false, length = 300)
    private String serviceName;

    @Column(name = "unit_fee", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitFee;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "line_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal lineTotal;

    public static PaymentLineItem of(
            UUID serviceItemId, String code, String name, BigDecimal unitFee, int quantity
    ) {
        BigDecimal total = unitFee.multiply(BigDecimal.valueOf(quantity));
        return new PaymentLineItem(serviceItemId, code, name, unitFee, quantity, total);
    }

    /** Line that does not reference a catalogue item — used for doctor consult fees. */
    public static PaymentLineItem adHoc(String code, String name, BigDecimal unitFee, int quantity) {
        return of(null, code, name, unitFee, quantity);
    }
}
