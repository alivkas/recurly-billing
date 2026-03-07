package ru.nocode.recurlybilling.data.dto.request;

public record CustomerCreateRequest(
        String externalId,
        String email,
        String fullName,
        String phone,
        Boolean isStudent
) {}
