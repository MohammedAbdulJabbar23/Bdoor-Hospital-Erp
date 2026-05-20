package com.albudoor.hms.clinicalcase.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * One prescribed drug line. Drug identity is captured by reference (FK to {@code service_item})
 * AND by snapshot — the doctor's prescription remains valid even if the catalogue entry is
 * later renamed or archived.
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class PrescriptionEntry {

    @Column(name = "rx_drug_id")
    private UUID drugServiceItemId;

    @Column(name = "rx_drug_code", length = 50)
    private String drugCode;

    @Column(name = "rx_drug_name", nullable = false, length = 300)
    private String drugName;

    @Column(name = "rx_strength", length = 100)
    private String strength;

    @Column(name = "rx_dose", length = 100)
    private String dose;

    @Column(name = "rx_frequency", length = 100)
    private String frequency;

    @Column(name = "rx_duration", length = 100)
    private String duration;

    @Column(name = "rx_route", length = 50)
    private String route;

    @Column(name = "rx_notes", length = 500)
    private String notes;
}
