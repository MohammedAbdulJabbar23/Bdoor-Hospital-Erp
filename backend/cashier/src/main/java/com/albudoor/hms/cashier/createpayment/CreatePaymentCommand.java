package com.albudoor.hms.cashier.createpayment;

import com.albudoor.hms.cashier.domain.PaymentStage;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;
import java.util.UUID;

public record CreatePaymentCommand(
        @NotNull UUID visitId,
        @NotNull PaymentStage stage,
        @NotEmpty @Valid List<Line> lines,
        String currency
) {
    public record Line(
            @NotNull UUID serviceItemId,
            @Positive int quantity
    ) {}
}
