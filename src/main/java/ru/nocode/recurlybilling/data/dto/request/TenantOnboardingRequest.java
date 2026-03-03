package ru.nocode.recurlybilling.data.dto.request;

import jakarta.validation.constraints.NotNull;

public record TenantOnboardingRequest(
        @NotNull
        String companyName,
        @NotNull
        String contactEmail
) {}
