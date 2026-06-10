package com.albudoor.hms.bedstayforms.nursingprocedures;

import com.albudoor.hms.bedstayforms.api.NursingProcedureDto;
import com.albudoor.hms.bedstayforms.directory.StayDirectoryRegistry;
import com.albudoor.hms.bedstayforms.domain.NursingProcedureEntry;
import com.albudoor.hms.bedstayforms.domain.StayDepartment;
import com.albudoor.hms.bedstayforms.infrastructure.NursingProcedureRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class NursingProceduresHandler {

    private final NursingProcedureRepository rows;
    private final StayDirectoryRegistry stays;

    public NursingProceduresHandler(NursingProcedureRepository rows, StayDirectoryRegistry stays) {
        this.rows = rows;
        this.stays = stays;
    }

    @Transactional(readOnly = true)
    public List<NursingProcedureDto> list(StayDepartment dept, UUID stayId) {
        stays.require(dept, stayId);
        return rows.findAllByDepartmentAndStayIdOrderByPerformedAtDesc(dept, stayId)
                .stream().map(NursingProcedureDto::from).toList();
    }

    @Transactional
    public NursingProcedureDto add(StayDepartment dept, UUID stayId, AddNursingProcedureCommand cmd,
                                   String nurseName, UUID recordedBy) {
        stays.requireOpen(dept, stayId);
        NursingProcedureEntry e = NursingProcedureEntry.record(dept, stayId,
                cmd.procedureName(), cmd.performedAt(), cmd.note(), nurseName, recordedBy);
        return NursingProcedureDto.from(rows.save(e));
    }
}
