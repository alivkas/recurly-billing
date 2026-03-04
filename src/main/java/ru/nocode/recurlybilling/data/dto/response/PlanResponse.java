package ru.nocode.recurlybilling.data.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

public record PlanResponse(
        UUID id,
        String code,
        String name,
        Long priceCents,
        String currency,
        String interval,
        Integer intervalCount,
        Integer trialDays,
        LocalDate endDate,
        Map<String, Object> metadata,
        LocalDateTime createdAt
) {}