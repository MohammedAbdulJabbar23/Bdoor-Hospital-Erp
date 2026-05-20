package com.albudoor.hms.visitmanagement.listvisits;

import com.albudoor.hms.platform.exception.NotFoundException;
import com.albudoor.hms.visitmanagement.domain.Visit;
import com.albudoor.hms.visitmanagement.domain.VisitStatus;
import com.albudoor.hms.visitmanagement.domain.VisitType;
import com.albudoor.hms.visitmanagement.infrastructure.VisitRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ListVisitsHandler {

    private final VisitRepository visits;

    public ListVisitsHandler(VisitRepository visits) {
        this.visits = visits;
    }

    @Transactional(readOnly = true)
    public Page<Visit> search(VisitType type, VisitStatus status, int page, int size) {
        return visits.search(type, status, PageRequest.of(page, Math.min(size, 100)));
    }

    @Transactional(readOnly = true)
    public Visit byId(UUID id) {
        return visits.findById(id)
                .orElseThrow(() -> new NotFoundException("Visit not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<Visit> byPatient(UUID patientId) {
        return visits.findAllByPatientIdOrderByStartedAtDesc(patientId);
    }

    @Transactional(readOnly = true)
    public List<Visit> children(UUID parentId) {
        return visits.findAllByParentVisitIdOrderByStartedAtDesc(parentId);
    }
}
