package ru.nocode.recurlybilling.data.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.Map;


public record PlanCreateRequest(
        @NotBlank
        String code,
        @NotBlank
        String name,
        @NotNull
        @Min(0)
        Long priceCents,
        @NotBlank
        String currency,
        @NotBlank
        String interval,
        @Min(1)
        Integer intervalCount,
        Integer trialDays,
        LocalDate startDate,
        LocalDate endDate,
        Map<String, Object> metadata
) {}
