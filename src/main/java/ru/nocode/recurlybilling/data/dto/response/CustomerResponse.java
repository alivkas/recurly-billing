package ru.nocode.recurlybilling.data.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record CustomerResponse(
        UUID id,
        String externalId,
        String email,
        String fullName,
        Boolean isStudent,
        LocalDateTime createdAt
) {}
