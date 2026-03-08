package ru.nocode.recurlybilling.data.dto.response;

import java.time.LocalDateTime;

public record PaymentResponse(
        String paymentId,
        String status,
        String confirmationUrl,
        Long amountCents,
        String currency,
        LocalDateTime createdAt
) {}
