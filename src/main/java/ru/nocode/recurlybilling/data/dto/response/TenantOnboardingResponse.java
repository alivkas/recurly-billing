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
    private String organizationName;
    private String apiKey;
    private String status;
    private LocalDateTime createdAt;
    private PaymentSettingsInfo paymentSettings;

    @Getter
    @Setter
    @AllArgsConstructor
    public static class PaymentSettingsInfo {

        private String provider;
        private boolean shopConfigured;
        private String currency;
    }
}
