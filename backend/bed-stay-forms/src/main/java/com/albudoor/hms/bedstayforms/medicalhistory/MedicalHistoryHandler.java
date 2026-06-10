package com.albudoor.hms.bedstayforms.medicalhistory;

import com.albudoor.hms.bedstayforms.api.MedicalHistoryDto;
import com.albudoor.hms.bedstayforms.api.MedicalHistoryResponse;
import com.albudoor.hms.bedstayforms.api.StayPrefillDto;
import com.albudoor.hms.bedstayforms.directory.StayDirectoryRegistry;
import com.albudoor.hms.bedstayforms.directory.StayInfo;
import com.albudoor.hms.bedstayforms.domain.MedicalHistorySheet;
import com.albudoor.hms.bedstayforms.domain.StayDepartment;
import com.albudoor.hms.bedstayforms.infrastructure.MedicalHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class MedicalHistoryHandler {

    private final MedicalHistoryRepository sheets;
    private final StayDirectoryRegistry stays;

    public MedicalHistoryHandler(MedicalHistoryRepository sheets, StayDirectoryRegistry stays) {
        this.sheets = sheets;
        this.stays = stays;
    }

    @Transactional(readOnly = true)
    public MedicalHistoryResponse get(StayDepartment dept, UUID stayId) {
        StayInfo info = stays.require(dept, stayId);
        MedicalHistoryDto form = sheets.findByDepartmentAndStayId(dept, stayId)
                .map(MedicalHistoryDto::from).orElse(null);
        return new MedicalHistoryResponse(StayPrefillDto.from(info), form);
    }

    @Transactional
    public MedicalHistoryDto upsert(StayDepartment dept, UUID stayId, UpsertMedicalHistoryCommand cmd) {
        stays.requireOpen(dept, stayId);
        MedicalHistorySheet sheet = sheets.findByDepartmentAndStayId(dept, stayId)
                .orElseGet(() -> MedicalHistorySheet.create(dept, stayId));
        sheet.update(cmd.toData());
        return MedicalHistoryDto.from(sheets.save(sheet));
    }
}
