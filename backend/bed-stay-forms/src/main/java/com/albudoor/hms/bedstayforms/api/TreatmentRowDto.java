package com.albudoor.hms.bedstayforms.api;

import com.albudoor.hms.bedstayforms.domain.TreatmentRow;

import java.util.Arrays;
import java.util.List;

/** timing is always exactly 6 entries (AM/AM/PM/PM/PM/AM), nulls for empty slots. */
public record TreatmentRowDto(String medicineName, String dose, String frequency, List<String> timing) {
    public static TreatmentRowDto from(TreatmentRow r) {
        return new TreatmentRowDto(r.getMedicineName(), r.getDose(), r.getFrequency(),
                Arrays.asList(r.getTiming1(), r.getTiming2(), r.getTiming3(),
                        r.getTiming4(), r.getTiming5(), r.getTiming6()));
    }
}
