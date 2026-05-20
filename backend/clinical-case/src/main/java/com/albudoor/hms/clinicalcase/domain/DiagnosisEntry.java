package com.albudoor.hms.clinicalcase.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One diagnosis line on a doctor's exam. Code is free text in v1 (ICD-10 lookup is a
 * later enhancement); description is required, primary flag drives sorting/display.
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class DiagnosisEntry {

    @Column(name = "dx_code", length = 30)
    private String code;

    @Column(name = "dx_description", nullable = false, length = 500)
    private String description;

    @Column(name = "dx_is_primary", nullable = false)
    private boolean primary;

    @Column(name = "dx_notes", length = 1000)
    private String notes;
}
