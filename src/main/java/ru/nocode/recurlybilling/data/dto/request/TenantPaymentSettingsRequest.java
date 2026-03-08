package ru.nocode.recurlybilling.data.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record TenantPaymentSettingsRequest(

        @NotBlank(message = "YooKassa Shop ID is required")
        @Pattern(regexp = "\\d+", message = "Shop ID must contain only digits")
        String yookassaShopId,

        @NotBlank(message = "YooKassa Secret Key is required")
        @Pattern(regexp = "^(live|test)_[a-zA-Z0-9\\-_]{32,}$",
                message = "Secret key must be a valid YooKassa key (starts with 'live_' or 'test_')")
        String yookassaSecretKey

) {}
