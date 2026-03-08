package ru.nocode.recurlybilling.data.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TenantOnboardingRequest(

        @NotBlank(message = "Organization name is required")
        @Size(max = 255, message = "Organization name must be less than 255 characters")
        String organizationName,

        @NotBlank(message = "Contact email is required")
        @Email(message = "Must be a valid email address")
        String contactEmail

) {}
