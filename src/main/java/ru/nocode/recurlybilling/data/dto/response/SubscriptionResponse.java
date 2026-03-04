package ru.nocode.recurlybilling.data.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

public record SubscriptionResponse(
        UUID id,
        String status,
        LocalDate currentPeriodStart,
        LocalDate currentPeriodEnd,
        LocalDate nextBillingDate,
        Map<String, Object> metadata,
        LocalDateTime createdAt
) {}
