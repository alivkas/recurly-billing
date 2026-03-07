package ru.nocode.recurlybilling.data.dto.request;

import java.util.Map;

public record PaymentCreateRequest(
        String subscriptionId,
        Long amountCents,
        String paymentMethod,
        String returnUrl,
        String description,
        Map<String, Object> metadata
) {}
