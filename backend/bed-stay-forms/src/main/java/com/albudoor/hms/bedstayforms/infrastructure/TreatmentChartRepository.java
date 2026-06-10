package com.albudoor.hms.bedstayforms.infrastructure;

import com.albudoor.hms.bedstayforms.domain.StayDepartment;
import com.albudoor.hms.bedstayforms.domain.TreatmentChart;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TreatmentChartRepository extends JpaRepository<TreatmentChart, UUID> {
    Optional<TreatmentChart> findByDepartmentAndStayIdAndChartDate(StayDepartment department, UUID stayId, LocalDate chartDate);
    List<TreatmentChart> findAllByDepartmentAndStayIdOrderByChartDateDesc(StayDepartment department, UUID stayId);
}
