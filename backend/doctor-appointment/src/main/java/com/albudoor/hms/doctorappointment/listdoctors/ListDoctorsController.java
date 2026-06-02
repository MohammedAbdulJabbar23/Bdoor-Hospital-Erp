package com.albudoor.hms.doctorappointment.listdoctors;

import com.albudoor.hms.doctorappointment.api.DoctorResponse;
import com.albudoor.hms.doctorappointment.infrastructure.DoctorRepository;
import com.albudoor.hms.identity.infrastructure.security.HmsUserPrincipal;
import org.springframework.http.ResponseEntity;
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

    /**
     * Resolve the Doctor record linked to the currently authenticated user.
     *
     * <p>Returns 200 with the profile when one is linked, and 200 with an empty
     * (null) body when the authenticated user simply has no Doctor profile
     * (e.g. an admin or cashier viewing /my-schedule). "No profile" is a valid
     * state for an authenticated user, not a missing resource, so it is not a 404.
     */
    @GetMapping("/me")
    public ResponseEntity<DoctorResponse> me(@AuthenticationPrincipal HmsUserPrincipal principal) {
        if (principal == null) return ResponseEntity.ok().build();
        return repo.findByUserId(principal.userId())
                .map(DoctorResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.ok().build());
    }

    @GetMapping("/{id}")
    public DoctorResponse byId(@PathVariable UUID id) {
        return DoctorResponse.from(handler.byId(id));
    }
}
