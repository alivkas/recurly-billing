package ru.nocode.recurlybilling.services.tenant;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.nocode.recurlybilling.data.dto.request.TenantPaymentSettingsRequest;
import ru.nocode.recurlybilling.data.entities.Tenant;
import ru.nocode.recurlybilling.data.repositories.TenantRepository;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class TenantPaymentSettingsService {

    private final TenantRepository tenantRepository;
    private final EncryptionService encryptionService;

    /**
     * Обновление тенанта
     * @param tenantId id тенанта
     * @param request запрос на обновление настроек тенанта
     */
    public void updatePaymentSettings(String tenantId, TenantPaymentSettingsRequest request) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + tenantId));

        tenant.setYooKassaShopId(request.yookassaShopId());
        tenant.setYooKassaSecretKey(encryptionService.encrypt(request.yookassaSecretKey()));
        tenant.setUpdatedAt(LocalDateTime.now());

        tenantRepository.save(tenant);

        log.info("Updated YooKassa settings for tenant: {} (shopId: {})",
                tenantId, request.yookassaShopId());
    }
}
