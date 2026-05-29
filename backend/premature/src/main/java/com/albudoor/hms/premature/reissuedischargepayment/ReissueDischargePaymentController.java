package com.albudoor.hms.premature.reissuedischargepayment;

import com.albudoor.hms.premature.api.AdmissionResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/premature/admissions")
public class ReissueDischargePaymentController {

    private final ReissueDischargePaymentHandler handler;

    public ReissueDischargePaymentController(ReissueDischargePaymentHandler handler) {
        this.handler = handler;
    }

    @PostMapping("/{id}/reissue-discharge-payment")
    @PreAuthorize("hasAnyRole('PREMATURE_STAFF', 'DOCTOR', 'ADMIN')")
    public AdmissionResponse reissue(@PathVariable UUID id) {
        return AdmissionResponse.from(handler.handle(id));
    }
}
