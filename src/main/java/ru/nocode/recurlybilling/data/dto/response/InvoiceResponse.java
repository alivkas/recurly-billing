package ru.nocode.recurlybilling.data.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record InvoiceResponse(
        UUID id,
        Long amountCents,
        String status,
        String paymentMethod,
        LocalDateTime createdAt
) {}
