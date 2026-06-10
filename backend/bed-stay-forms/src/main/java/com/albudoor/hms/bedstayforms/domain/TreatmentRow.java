package com.albudoor.hms.bedstayforms.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One medicine line on the Treatment Chart. The six timing columns mirror the paper's
 * AM/AM/PM/PM/PM/AM grid; free short text because the paper is hand-annotated with times.
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class TreatmentRow {
    @Column(name = "medicine_name", nullable = false, length = 300) private String medicineName;
    @Column(length = 120) private String dose;
    @Column(length = 120) private String frequency;
    @Column(name = "timing1", length = 60) private String timing1;
    @Column(name = "timing2", length = 60) private String timing2;
    @Column(name = "timing3", length = 60) private String timing3;
    @Column(name = "timing4", length = 60) private String timing4;
    @Column(name = "timing5", length = 60) private String timing5;
    @Column(name = "timing6", length = 60) private String timing6;
}
