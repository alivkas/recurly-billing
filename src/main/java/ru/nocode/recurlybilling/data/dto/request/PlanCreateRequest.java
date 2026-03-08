package ru.nocode.recurlybilling.data.dto.request;

import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.util.Map;

public record PlanCreateRequest(

        @NotBlank(message = "Plan code is required")
        @Size(max = 100, message = "Plan code must be less than 100 characters")
        String code,

        @NotBlank(message = "Plan name is required")
        @Size(max = 255, message = "Plan name must be less than 255 characters")
        String name,

        @NotNull(message = "Price is required")
        @Min(value = 0, message = "Price must be >= 0")
        Long priceCents,

        @NotBlank(message = "Currency is required")
        @Pattern(regexp = "RUB|USD|EUR", message = "Supported currencies: RUB, USD, EUR")
        String currency,

        @NotBlank(message = "Interval is required")
        @Pattern(regexp = "month|semester|year|custom", message = "Supported intervals: month, semester, year, custom")
        String interval,

        @NotNull(message = "Interval count is required")
        @Min(value = 1, message = "Interval count must be >= 1")
        Integer intervalCount,

        @NotNull(message = "Trial days is required")
        @Min(value = 0, message = "Trial days must be >= 0")
        Integer trialDays,

        LocalDate startDate,

        LocalDate endDate,

        Map<String, Object> metadata

) {}
