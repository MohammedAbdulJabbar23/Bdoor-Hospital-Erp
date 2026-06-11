package com.albudoor.hms.bedstayforms.directory;

import com.albudoor.hms.bedstayforms.domain.StayDepartment;

import java.util.UUID;

/** Identifies the bed-stay (department + stay id) a forwarded order visit belongs to. */
public record StayRef(StayDepartment department, UUID stayId) {}
