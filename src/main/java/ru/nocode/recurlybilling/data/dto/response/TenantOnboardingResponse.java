package ru.nocode.recurlybilling.data.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class TenantOnboardingResponse {
    private String tenantId;
    private String apiKey;
    private LocalDateTime createdAt;
}
