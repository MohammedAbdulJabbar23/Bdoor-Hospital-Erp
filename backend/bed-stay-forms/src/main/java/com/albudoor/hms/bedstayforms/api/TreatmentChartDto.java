package com.albudoor.hms.bedstayforms.api;

import com.albudoor.hms.bedstayforms.domain.TreatmentChart;

import java.time.LocalDate;
import java.util.List;

public record TreatmentChartDto(LocalDate chartDate, List<TreatmentRowDto> rows, SignatureView doctorSignature) {
    public static TreatmentChartDto from(TreatmentChart c) {
        return new TreatmentChartDto(c.getChartDate(),
                c.getRows().stream().map(TreatmentRowDto::from).toList(),
                SignatureView.from(c.getDoctorSignature()));
    }
}
