package com.albudoor.hms.bedstayforms.treatmentcharts;

import com.albudoor.hms.bedstayforms.domain.TreatmentRow;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UpsertTreatmentChartCommand(@Valid List<RowCommand> rows) {

    public record RowCommand(
            @NotBlank String medicineName,
            String dose,
            String frequency,
            @Size(max = 6) List<String> timing
    ) {
        public TreatmentRow toRow() {
            String[] t = new String[6];
            if (timing != null) {
                for (int i = 0; i < Math.min(6, timing.size()); i++) {
                    String v = timing.get(i);
                    t[i] = (v == null || v.isBlank()) ? null : v;
                }
            }
            return new TreatmentRow(medicineName, dose, frequency, t[0], t[1], t[2], t[3], t[4], t[5]);
        }
    }

    public List<TreatmentRow> toRows() {
        return rows == null ? List.of() : rows.stream().map(RowCommand::toRow).toList();
    }
}
