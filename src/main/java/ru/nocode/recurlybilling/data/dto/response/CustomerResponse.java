package ru.nocode.recurlybilling.data.dto.response;

import java.time.LocalDateTime;

public record CustomerResponse(
        String id,
        String externalId,
        String email,
        String fullName,
        Boolean isStudent,
        LocalDateTime createdAt
) {}
