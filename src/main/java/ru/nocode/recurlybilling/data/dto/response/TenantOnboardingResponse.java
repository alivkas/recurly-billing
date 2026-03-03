package ru.nocode.recurlybilling.data.dto.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
public class TenantOnboardingResponse {
    private String tenantId;
    private String apiKey;
    private LocalDateTime createdAt;
}
