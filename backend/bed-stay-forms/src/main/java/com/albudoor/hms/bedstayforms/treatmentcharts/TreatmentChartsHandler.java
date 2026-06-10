package com.albudoor.hms.bedstayforms.treatmentcharts;

import com.albudoor.hms.bedstayforms.api.TreatmentChartDto;
import com.albudoor.hms.bedstayforms.directory.StayDirectoryRegistry;
import com.albudoor.hms.bedstayforms.domain.StayDepartment;
import com.albudoor.hms.bedstayforms.domain.TreatmentChart;
import com.albudoor.hms.bedstayforms.infrastructure.TreatmentChartRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class TreatmentChartsHandler {

    private final TreatmentChartRepository charts;
    private final StayDirectoryRegistry stays;

    public TreatmentChartsHandler(TreatmentChartRepository charts, StayDirectoryRegistry stays) {
        this.charts = charts;
        this.stays = stays;
    }

    @Transactional(readOnly = true)
    public List<TreatmentChartDto> list(StayDepartment dept, UUID stayId) {
        stays.require(dept, stayId);
        return charts.findAllByDepartmentAndStayIdOrderByChartDateDesc(dept, stayId)
                .stream().map(TreatmentChartDto::from).toList();
    }

    @Transactional
    public TreatmentChartDto upsert(StayDepartment dept, UUID stayId, LocalDate date,
                                    UpsertTreatmentChartCommand cmd) {
        stays.requireOpen(dept, stayId);
        TreatmentChart chart = charts.findByDepartmentAndStayIdAndChartDate(dept, stayId, date)
                .orElseGet(() -> TreatmentChart.create(dept, stayId, date));
        chart.replaceRows(cmd.toRows());
        return TreatmentChartDto.from(charts.save(chart));
    }
}
