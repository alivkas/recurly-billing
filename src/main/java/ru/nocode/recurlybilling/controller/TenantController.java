package ru.nocode.recurlybilling.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.nocode.recurlybilling.data.dto.request.TenantOnboardingRequest;
import ru.nocode.recurlybilling.data.dto.request.TenantPaymentSettingsRequest;
import ru.nocode.recurlybilling.data.dto.response.TenantOnboardingResponse;
import ru.nocode.recurlybilling.services.TenantService;

@Slf4j
@RestController
@RequestMapping("/api/v1/tenants")
@RequiredArgsConstructor
public class TenantController {

    private final TenantService tenantService;

    /**
     * Создание нового tenant'а (онбординг)
     * Публичный эндпоинт — не требует аутентификации
     */
    @PostMapping("/onboard")
    public ResponseEntity<TenantOnboardingResponse> onboardTenant(
            @Valid @RequestBody TenantOnboardingRequest request) {

        log.info("Received onboarding request for organization: {}", request.organizationName());

        try {
            TenantOnboardingResponse response = tenantService.onboard(request);
            log.info("Successfully created tenant: {} with ID: {}",
                    request.organizationName(), response.getTenantId());

            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid onboarding request: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Unexpected error during tenant onboarding", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Получение информации о tenant'е
     * Требует аутентификации через X-Tenant-ID и X-API-Key
     */
    @GetMapping("/{tenantId}")
    public ResponseEntity<TenantOnboardingResponse> getTenant(
            @PathVariable String tenantId,
            @RequestHeader("X-Tenant-ID") String authTenantId,
            @RequestHeader("X-API-Key") String apiKey) {

        if (!tenantId.equals(authTenantId)) {
            log.warn("Tenant ID mismatch: path={}, header={}", tenantId, authTenantId);
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            TenantOnboardingResponse response = tenantService.getTenant(tenantId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.warn("Tenant not found: {}", tenantId);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error retrieving tenant: {}", tenantId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PatchMapping("/payment-settings")
    public ResponseEntity<Void> updatePaymentSettings(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-API-Key") String apiKey,
            @Valid @RequestBody TenantPaymentSettingsRequest request) {

        log.info("Updating payment settings for tenant: {}", tenantId);

        try {
            tenantService.validateTenantAndApiKey(tenantId, apiKey);
            tenantService.updatePaymentSettings(tenantId, request);
            log.info("Successfully updated payment settings for tenant: {}", tenantId);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            log.warn("Invalid payment settings request for tenant {}: {}", tenantId, e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error updating payment settings for tenant: {}", tenantId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
