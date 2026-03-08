package ru.nocode.recurlybilling.data.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record SubscriptionCreateRequest(
        @NotBlank
        String customerId,
        @NotBlank
        String planId,
        @NotNull
        LocalDate startDate,
        String paymentMethod
) {}
