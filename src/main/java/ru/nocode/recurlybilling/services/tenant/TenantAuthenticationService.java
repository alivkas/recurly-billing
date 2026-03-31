package ru.nocode.recurlybilling.services.tenant;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.nocode.recurlybilling.data.entities.Tenant;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Service
@RequiredArgsConstructor
@Slf4j
public class TenantAuthenticationService {

    private final TenantQueryService tenantQueryService;
    private final EncryptionService encryptionService;

    /**
     * Валидация тенанта и api ключа
     * @param tenantId id тенанта
     * @param providedApiKey полученный api ключ
     */
    @Transactional(readOnly = true)
    public void validateTenantAndApiKey(String tenantId, String providedApiKey) {
        Tenant tenant = tenantQueryService.findTenantById(tenantId);
        if (!tenant.getIsActive()) {
            log.warn("Attempt to access inactive tenant: {}", tenantId);
            throw new IllegalArgumentException("Tenant is inactive: " + tenantId);
        }

        log.info("Stored encrypted key length: {}", tenant.getApiKeyHash());
        String storedApiKey = encryptionService.decrypt(tenant.getApiKeyHash());

        if (!MessageDigest.isEqual(
                providedApiKey.getBytes(StandardCharsets.UTF_8),
                storedApiKey.getBytes(StandardCharsets.UTF_8))) {
            throw new IllegalArgumentException("Invalid API key");
        }
    }
}
