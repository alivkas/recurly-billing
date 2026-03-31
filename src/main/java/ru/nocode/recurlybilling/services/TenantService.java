package ru.nocode.recurlybilling.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.nocode.recurlybilling.data.dto.request.TenantOnboardingRequest;
import ru.nocode.recurlybilling.data.dto.request.TenantPaymentSettingsRequest;
import ru.nocode.recurlybilling.data.dto.response.TenantOnboardingResponse;
import ru.nocode.recurlybilling.services.tenant.*;
import java.util.Optional;


@Slf4j
@Service
@RequiredArgsConstructor
public class TenantService {

    private final TenantAuthenticationService tenantAuthenticationService;
    private final TenantLifeCycleService tenantLifeCycleService;
    private final TenantPaymentSettingsService tenantPaymentSettingsService;
    private final TenantQueryService tenantQueryService;

    /**
     * Создание тенанта
     * @param request запрос на создание
     * @return тело созданного тенанта в ответе
     */
    @Transactional
    public TenantOnboardingResponse onboard(TenantOnboardingRequest request) {
        return tenantLifeCycleService.onboard(request);
    }

    /**
     * Обновление тенанта
     * @param tenantId id тенанта
     * @param request запрос на обновление настроек тенанта
     */
    @Transactional
    public void updatePaymentSettings(String tenantId, TenantPaymentSettingsRequest request) {
        tenantPaymentSettingsService.updatePaymentSettings(tenantId, request);
    }

    /**
     * Получение тенанта
     * @param tenantId id тенанта
     * @return тело ответа тенанта
     */
    @Transactional(readOnly = true)
    public TenantOnboardingResponse getTenant(String tenantId) {
        return tenantLifeCycleService.getTenant(tenantId);
    }

    /**
     * Валидация тенанта и api ключа
     * @param tenantId id тенанта
     * @param providedApiKey полученный api ключ
     */
    @Transactional(readOnly = true)
    public void validateTenantAndApiKey(String tenantId, String providedApiKey) {
        tenantAuthenticationService.validateTenantAndApiKey(tenantId, providedApiKey);
    }

    @Transactional(readOnly = true)
    public Optional<String> findTenantIdByYooKassaShopId(String shopId) {
        return tenantQueryService.findTenantIdByYooKassaShopId(shopId);
    }
}
