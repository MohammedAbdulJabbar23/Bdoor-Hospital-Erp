package com.albudoor.hms.cashier.rejectpayment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectPaymentCommand(
        @NotBlank @Size(max = 500) String reason
) {}
