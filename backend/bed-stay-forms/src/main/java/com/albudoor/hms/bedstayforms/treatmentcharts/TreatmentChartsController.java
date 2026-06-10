package com.albudoor.hms.bedstayforms.treatmentcharts;

import com.albudoor.hms.bedstayforms.access.BedStayAccess;
import com.albudoor.hms.bedstayforms.access.CurrentUser;
import com.albudoor.hms.bedstayforms.api.SignatureView;
import com.albudoor.hms.bedstayforms.api.TreatmentChartDto;
import com.albudoor.hms.bedstayforms.directory.StayDirectoryRegistry;
import com.albudoor.hms.bedstayforms.domain.StayDepartment;
import com.albudoor.hms.bedstayforms.domain.TreatmentChart;
import com.albudoor.hms.bedstayforms.infrastructure.TreatmentChartRepository;
import com.albudoor.hms.bedstayforms.signatures.SignatureStore;
import com.albudoor.hms.platform.exception.NotFoundException;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/bed-stays/{department}/{stayId}/treatment-charts")
public class TreatmentChartsController {

    private final TreatmentChartsHandler handler;
    private final BedStayAccess access;
    private final TreatmentChartRepository charts;
    private final StayDirectoryRegistry stays;
    private final SignatureStore signatures;

    public TreatmentChartsController(TreatmentChartsHandler handler, BedStayAccess access,
                                     TreatmentChartRepository charts, StayDirectoryRegistry stays,
                                     SignatureStore signatures) {
        this.handler = handler;
        this.access = access;
        this.charts = charts;
        this.stays = stays;
        this.signatures = signatures;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<TreatmentChartDto> list(@PathVariable StayDepartment department, @PathVariable UUID stayId) {
        access.checkRead(department);
        return handler.list(department, stayId);
    }

    @PutMapping("/{date}")
    @PreAuthorize("isAuthenticated()")
    public TreatmentChartDto upsert(@PathVariable StayDepartment department, @PathVariable UUID stayId,
                                    @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                    @Valid @RequestBody UpsertTreatmentChartCommand cmd) {
        access.checkDoctorWrite(department);
        return handler.upsert(department, stayId, date, cmd);
    }

    @PostMapping("/{date}/signature")
    @PreAuthorize("isAuthenticated()")
    @Transactional
    public SignatureView sign(@PathVariable StayDepartment department, @PathVariable UUID stayId,
                              @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                              @RequestParam("file") MultipartFile file,
                              @RequestParam(value = "signerName", required = false) String signerName)
            throws IOException {
        access.checkDoctorWrite(department);
        stays.requireOpen(department, stayId);
        TreatmentChart chart = charts.findByDepartmentAndStayIdAndChartDate(department, stayId, date)
                .orElseThrow(() -> new NotFoundException("No treatment chart for " + date));
        String key = signatures.store(file);
        chart.applyDoctorSignature(key, signerName, CurrentUser.id());
        return SignatureView.from(charts.save(chart).getDoctorSignature());
    }

    @GetMapping("/{date}/signature")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Resource> signature(@PathVariable StayDepartment department, @PathVariable UUID stayId,
                                              @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date)
            throws IOException {
        access.checkRead(department);
        TreatmentChart chart = charts.findByDepartmentAndStayIdAndChartDate(department, stayId, date)
                .orElseThrow(() -> new NotFoundException("No treatment chart for " + date));
        return signatures.stream(chart.getDoctorSignature(), "DOCTOR");
    }
}
