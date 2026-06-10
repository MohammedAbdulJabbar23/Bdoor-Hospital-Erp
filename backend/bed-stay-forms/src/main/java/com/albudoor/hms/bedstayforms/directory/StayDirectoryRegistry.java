package com.albudoor.hms.bedstayforms.directory;

import com.albudoor.hms.bedstayforms.domain.StayDepartment;
import com.albudoor.hms.platform.exception.DomainException;
import com.albudoor.hms.platform.exception.NotFoundException;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class StayDirectoryRegistry {

    private final Map<StayDepartment, StayDirectory> byDepartment = new EnumMap<>(StayDepartment.class);

    public StayDirectoryRegistry(List<StayDirectory> directories) {
        for (StayDirectory d : directories) {
            if (byDepartment.putIfAbsent(d.department(), d) != null) {
                throw new IllegalStateException("Duplicate StayDirectory for " + d.department());
            }
        }
    }

    /** 404 if the department has no directory bean. */
    public StayDirectory directory(StayDepartment department) {
        StayDirectory d = byDepartment.get(department);
        if (d == null) throw new NotFoundException("No bed-stay directory for " + department);
        return d;
    }

    /** 404 if the department has no directory bean or the stay doesn't exist. */
    public StayInfo require(StayDepartment department, UUID stayId) {
        StayDirectory d = byDepartment.get(department);
        if (d == null) throw new NotFoundException("No bed-stay directory for " + department);
        return d.find(stayId)
                .orElseThrow(() -> new NotFoundException("Stay not found: " + department + "/" + stayId));
    }

    /** Writes are rejected once the case is closed/cancelled (forms become read-only). */
    public StayInfo requireOpen(StayDepartment department, UUID stayId) {
        StayInfo info = require(department, stayId);
        if (!info.open()) {
            throw new DomainException("STAY_CLOSED", "The case is closed; clinical forms are read-only");
        }
        return info;
    }
}
