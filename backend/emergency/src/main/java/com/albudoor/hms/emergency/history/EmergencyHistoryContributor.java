package com.albudoor.hms.emergency.history;

import com.albudoor.hms.bedstayforms.domain.StayDepartment;
import com.albudoor.hms.bedstayforms.domain.TreatmentChart;
import com.albudoor.hms.bedstayforms.infrastructure.MedicalHistoryRepository;
import com.albudoor.hms.bedstayforms.infrastructure.NursingProcedureRepository;
import com.albudoor.hms.bedstayforms.infrastructure.TreatmentChartRepository;
import com.albudoor.hms.clinicalcase.history.HistoryContributor;
import com.albudoor.hms.clinicalcase.history.HistoryEntry;
import com.albudoor.hms.clinicalcase.history.HistoryEntryType;
import com.albudoor.hms.clinicalcase.history.HistoryRefs;
import com.albudoor.hms.emergency.infrastructure.EmergencyCaseRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class EmergencyHistoryContributor implements HistoryContributor {

    private final EmergencyCaseRepository cases;
    private final MedicalHistoryRepository sheets;
    private final TreatmentChartRepository charts;
    private final NursingProcedureRepository nursing;

    public EmergencyHistoryContributor(EmergencyCaseRepository cases,
                                       MedicalHistoryRepository sheets,
                                       TreatmentChartRepository charts,
                                       NursingProcedureRepository nursing) {
        this.cases = cases;
        this.sheets = sheets;
        this.charts = charts;
        this.nursing = nursing;
    }

    @Override
    public List<HistoryEntry> contribute(UUID patientId) {
        List<HistoryEntry> out = new ArrayList<>();
        cases.findAllByPatientIdOrderByAdmittedAtDesc(patientId).forEach(c -> {
            out.add(new HistoryEntry(c.getAdmittedAt(), HistoryEntryType.ADMISSION, "EMERGENCY",
                    "Admitted to emergency bed " + c.getBedCode(), null,
                    "admissionOpened", Map.of("bed", c.getBedCode()), HistoryRefs.stay(c.getId())));
            if (c.getClosedAt() != null) {
                out.add(new HistoryEntry(c.getClosedAt(), HistoryEntryType.ADMISSION, "EMERGENCY",
                        "Discharged from emergency bed " + c.getBedCode(),
                        c.getDischargeNote(),
                        "admissionClosed", Map.of("bed", c.getBedCode()), HistoryRefs.stay(c.getId())));
            }
            sheets.findByDepartmentAndStayId(StayDepartment.EMERGENCY, c.getId()).ifPresent(s ->
                    out.add(new HistoryEntry(s.getUpdatedAt() != null ? s.getUpdatedAt() : s.getCreatedAt(),
                            HistoryEntryType.FORM, "EMERGENCY", "Medical history sheet",
                            s.getChiefComplaint(),
                            "medicalHistorySheet", Map.of(), HistoryRefs.stay(c.getId()))));
            var chartDays = charts.findAllByDepartmentAndStayIdOrderByChartDateDesc(StayDepartment.EMERGENCY, c.getId());
            if (!chartDays.isEmpty()) {
                Instant latest = chartDays.stream().map(TreatmentChart::getCreatedAt)
                        .max(Comparator.naturalOrder()).orElseThrow();
                out.add(new HistoryEntry(latest, HistoryEntryType.FORM, "EMERGENCY",
                        "Treatment charts — " + chartDays.size() + " day(s)", null,
                        "treatmentCharts", Map.of("count", String.valueOf(chartDays.size())),
                        HistoryRefs.stay(c.getId())));
            }
            var rows = nursing.findAllByDepartmentAndStayIdOrderByPerformedAtDescCreatedAtDesc(StayDepartment.EMERGENCY, c.getId());
            if (!rows.isEmpty()) {
                out.add(new HistoryEntry(rows.get(0).getPerformedAt(), HistoryEntryType.FORM, "EMERGENCY",
                        "Nursing procedures — " + rows.size() + " recorded", null,
                        "nursingLog", Map.of("count", String.valueOf(rows.size())),
                        HistoryRefs.stay(c.getId())));
            }
        });
        return out;
    }
}
