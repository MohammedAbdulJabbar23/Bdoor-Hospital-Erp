package com.albudoor.hms.doctorappointment.listdoctors;

import com.albudoor.hms.doctorappointment.api.DoctorResponse;
import com.albudoor.hms.doctorappointment.domain.Doctor;
import com.albudoor.hms.doctorappointment.infrastructure.DoctorRepository;
import com.albudoor.hms.identity.infrastructure.security.HmsUserPrincipal;
import com.albudoor.hms.platform.exception.NotFoundException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/doctors")
@PreAuthorize("isAuthenticated()")
public class ListDoctorsController {

    private final ListDoctorsHandler handler;
    private final DoctorRepository repo;

    public ListDoctorsController(ListDoctorsHandler handler, DoctorRepository repo) {
        this.handler = handler;
        this.repo = repo;
    }

    @GetMapping
    public List<DoctorResponse> list(@RequestParam(value = "activeOnly", defaultValue = "true") boolean activeOnly) {
        return handler.list(activeOnly).stream().map(DoctorResponse::from).toList();
    }

    /** Resolve the Doctor record linked to the currently authenticated user. */
    @GetMapping("/me")
    public DoctorResponse me(@AuthenticationPrincipal HmsUserPrincipal principal) {
        if (principal == null) throw new NotFoundException("No authenticated user");
        Doctor doctor = repo.findByUserId(principal.userId())
                .orElseThrow(() -> new NotFoundException(
                        "No doctor profile linked to this user account"));
        return DoctorResponse.from(doctor);
    }

    @GetMapping("/{id}")
    public DoctorResponse byId(@PathVariable UUID id) {
        return DoctorResponse.from(handler.byId(id));
    }
}
