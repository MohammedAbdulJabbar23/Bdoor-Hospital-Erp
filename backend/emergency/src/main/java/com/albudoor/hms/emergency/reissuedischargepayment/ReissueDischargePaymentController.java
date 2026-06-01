package com.albudoor.hms.emergency.reissuedischargepayment;

import com.albudoor.hms.emergency.api.CaseResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController("emergencyReissueDischargePaymentController")
@RequestMapping("/api/emergency/cases")
public class ReissueDischargePaymentController {

    private final ReissueDischargePaymentHandler handler;

    public ReissueDischargePaymentController(ReissueDischargePaymentHandler handler) {
        this.handler = handler;
    }

    @PostMapping("/{id}/reissue-discharge-payment")
    @PreAuthorize("hasAnyRole('EMERGENCY_STAFF', 'DOCTOR', 'ADMIN')")
    public CaseResponse reissue(@PathVariable UUID id) {
        return CaseResponse.from(handler.handle(id));
    }
}
