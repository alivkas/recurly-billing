package ru.nocode.recurlybilling.data.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;

public record SubscriptionCreateRequest(

        @NotBlank(message = "Customer ID is required")
        String customerId,

        @NotBlank(message = "Plan ID is required")
        String planId,

        LocalDate startDate,

        @Pattern(regexp = "bank_card|sbp|mir|apple_pay|google_pay",
                message = "Supported payment methods: bank_card, sbp, mir, apple_pay, google_pay")
        String paymentMethod

) {}
