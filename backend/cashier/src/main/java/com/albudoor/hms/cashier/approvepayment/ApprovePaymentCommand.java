package com.albudoor.hms.cashier.approvepayment;

import com.albudoor.hms.cashier.domain.PaymentMethod;
import jakarta.validation.constraints.NotNull;

public record ApprovePaymentCommand(
        @NotNull PaymentMethod paymentMethod
) {}
