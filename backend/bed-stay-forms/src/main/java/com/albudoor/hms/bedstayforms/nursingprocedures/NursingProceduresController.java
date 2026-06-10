package com.albudoor.hms.bedstayforms.nursingprocedures;

import com.albudoor.hms.bedstayforms.access.BedStayAccess;
import com.albudoor.hms.bedstayforms.access.CurrentUser;
import com.albudoor.hms.bedstayforms.api.NursingProcedureDto;
import com.albudoor.hms.bedstayforms.domain.StayDepartment;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/bed-stays/{department}/{stayId}/nursing-procedures")
public class NursingProceduresController {

    private final NursingProceduresHandler handler;
    private final BedStayAccess access;

    public NursingProceduresController(NursingProceduresHandler handler, BedStayAccess access) {
        this.handler = handler;
        this.access = access;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<NursingProcedureDto> list(@PathVariable StayDepartment department, @PathVariable UUID stayId) {
        access.checkRead(department);
        return handler.list(department, stayId);
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public NursingProcedureDto add(@PathVariable StayDepartment department, @PathVariable UUID stayId,
                                   @Valid @RequestBody AddNursingProcedureCommand cmd) {
        access.checkNurseWrite(department);
        return handler.add(department, stayId, cmd, CurrentUser.displayName(), CurrentUser.id());
    }
}
