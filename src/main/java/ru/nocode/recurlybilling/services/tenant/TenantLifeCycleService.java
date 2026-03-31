package ru.nocode.recurlybilling.services.tenant;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import ru.nocode.recurlybilling.data.dto.request.TenantOnboardingRequest;
import ru.nocode.recurlybilling.data.dto.response.TenantOnboardingResponse;
import ru.nocode.recurlybilling.data.entities.Tenant;
import ru.nocode.recurlybilling.data.repositories.TenantRepository;
import ru.nocode.recurlybilling.utils.TenantUtils;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TenantLifeCycleService {

    private final TenantRepository tenantRepository;
    private final EncryptionService encryptionService;
    private final TenantQueryService tenantQueryService;

    private static final String KEY_PREFIX = "sk_live_";

    /**
     * Создание тенанта
     * @param request запрос на создание
     * @return тело созданного тенанта в ответе
     */
    public TenantOnboardingResponse onboard(TenantOnboardingRequest request) {
        String tenantId = TenantUtils.generateTenantId(request.organizationName());
        if (tenantRepository.existsById(tenantId)) {
            throw new IllegalArgumentException("Tenant with ID '" + tenantId + "' already exists");
        }

        String apiKey = KEY_PREFIX + UUID.randomUUID().toString().replace("-", "");
        byte[] encryptedEmail = encryptionService.encrypt(request.contactEmail()).getBytes();

        Tenant tenant = new Tenant(
                tenantId,
                request.organizationName(),
                encryptedEmail,
                encryptionService.encrypt(apiKey),
                Boolean.TRUE,
                null,
                null,
                null,
                LocalDateTime.now(),
                null);

        Tenant saved = tenantRepository.save(tenant);

        return new TenantOnboardingResponse(
                saved.getTenantId(),
                apiKey,
                saved.getCreatedAt());
    }

    /**
     * Получение тенанта
     * @param tenantId id тенанта
     * @return тело ответа тенанта
     */
    public TenantOnboardingResponse getTenant(String tenantId) {
        Tenant tenant = tenantQueryService.findTenantById(tenantId);
        String dummyApiKey = "sk_live_********"; //TODO логика восстановления

        return new TenantOnboardingResponse(tenantId, dummyApiKey, tenant.getCreatedAt());
    }
}
