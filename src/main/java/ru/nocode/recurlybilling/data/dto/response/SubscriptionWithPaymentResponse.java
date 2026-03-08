package ru.nocode.recurlybilling.data.dto.response;

public record SubscriptionWithPaymentResponse(
        SubscriptionResponse subscription,
        PaymentResponse payment
) {}
