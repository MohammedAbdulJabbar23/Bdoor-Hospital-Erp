package com.albudoor.hms.premature.history;

import com.albudoor.hms.bedstayforms.domain.StayDepartment;
import com.albudoor.hms.bedstayforms.infrastructure.MedicalHistoryRepository;
import com.albudoor.hms.bedstayforms.infrastructure.NursingProcedureRepository;
import com.albudoor.hms.bedstayforms.infrastructure.TreatmentChartRepository;
import com.albudoor.hms.clinicalcase.history.HistoryContributor;
import com.albudoor.hms.clinicalcase.history.HistoryEntry;
import com.albudoor.hms.clinicalcase.history.HistoryEntryType;
import com.albudoor.hms.clinicalcase.history.HistoryRefs;
import com.albudoor.hms.premature.infrastructure.PrematureAdmissionRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class PrematureHistoryContributor implements HistoryContributor {

    private final PrematureAdmissionRepository admissions;
    private final MedicalHistoryRepository sheets;
    private final TreatmentChartRepository charts;
    private final NursingProcedureRepository nursing;

    public PrematureHistoryContributor(PrematureAdmissionRepository admissions,
                                       MedicalHistoryRepository sheets,
                                       TreatmentChartRepository charts,
                                       NursingProcedureRepository nursing) {
        this.admissions = admissions;
        this.sheets = sheets;
        this.charts = charts;
        this.nursing = nursing;
    }

    @Override
    public List<HistoryEntry> contribute(UUID patientId) {
        List<HistoryEntry> out = new ArrayList<>();
        admissions.findAllByPatientIdOrderByAdmittedAtDesc(patientId).forEach(a -> {
            out.add(new HistoryEntry(a.getAdmittedAt(), HistoryEntryType.ADMISSION, "PREMATURE",
                    "Admitted to premature bed " + a.getBedCode(), null, HistoryRefs.stay(a.getId())));
            if (a.getClosedAt() != null) {
                out.add(new HistoryEntry(a.getClosedAt(), HistoryEntryType.ADMISSION, "PREMATURE",
                        "Discharged from premature bed " + a.getBedCode(),
                        a.getDischargeNote(), HistoryRefs.stay(a.getId())));
            }
            sheets.findByDepartmentAndStayId(StayDepartment.PREMATURE, a.getId()).ifPresent(s ->
                    out.add(new HistoryEntry(s.getUpdatedAt() != null ? s.getUpdatedAt() : s.getCreatedAt(),
                            HistoryEntryType.FORM, "PREMATURE", "Medical history sheet",
                            s.getChiefComplaint(), HistoryRefs.stay(a.getId()))));
            charts.findAllByDepartmentAndStayIdOrderByChartDateDesc(StayDepartment.PREMATURE, a.getId()).forEach(tc ->
                    out.add(new HistoryEntry(tc.getCreatedAt(), HistoryEntryType.FORM, "PREMATURE",
                            "Treatment chart — " + tc.getChartDate(), null, HistoryRefs.stay(a.getId()))));
            var rows = nursing.findAllByDepartmentAndStayIdOrderByPerformedAtDescCreatedAtDesc(StayDepartment.PREMATURE, a.getId());
            if (!rows.isEmpty()) {
                out.add(new HistoryEntry(rows.get(0).getPerformedAt(), HistoryEntryType.FORM, "PREMATURE",
                        "Nursing procedures — " + rows.size() + " recorded", null, HistoryRefs.stay(a.getId())));
            }
        });
        return out;
    }
}
