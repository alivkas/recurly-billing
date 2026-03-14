package ru.nocode.recurlybilling.data.dto.request;

import jakarta.validation.constraints.*;

public record CustomerCreateRequest(

        @NotBlank(message = "External ID is required")
        @Size(max = 255, message = "External ID must be less than 255 characters")
        String externalId,

        @NotBlank(message = "Email is required")
        @Email(message = "Must be a valid email address")
        String email,

        @NotBlank(message = "Full name is required")
        @Size(max = 255, message = "Full name must be less than 255 characters")
        String fullName,

        @Pattern(regexp = "^\\+7\\d{10}$", message = "Phone must be in format +7XXXXXXXXXX")
        String phone,

        @NotNull(message = "Student status is required")
        Boolean isStudent,

        @Pattern(regexp = "^[a-zA-Z0-9_]{5,32}$", message = "Telegram username must be 5-32 characters (letters, digits, underscore)")
        String telegramUsername

) {}
