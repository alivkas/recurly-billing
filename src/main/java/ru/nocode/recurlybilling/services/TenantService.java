package ru.nocode.recurlybilling.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.nocode.recurlybilling.data.dto.request.TenantOnboardingRequest;
import ru.nocode.recurlybilling.data.dto.request.TenantPaymentSettingsRequest;
import ru.nocode.recurlybilling.data.dto.response.TenantOnboardingResponse;
import ru.nocode.recurlybilling.data.entities.Tenant;
import ru.nocode.recurlybilling.data.repositories.TenantRepository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TenantService {

    private final TenantRepository tenantRepository;
    private final EncryptionService encryptionService;

    @Transactional
    public TenantOnboardingResponse onboard(TenantOnboardingRequest request) {
        String tenantId = generateTenantId(request.organizationName());

        if (tenantRepository.existsById(tenantId)) {
            throw new IllegalArgumentException("Tenant with ID '" + tenantId + "' already exists");
        }

        byte[] encryptedEmail = encryptionService.encrypt(request.contactEmail()).getBytes();

        String apiKey = "sk_live_" + UUID.randomUUID().toString().replace("-", "");
        log.info("Generated API key: {}", apiKey);
        String encryptedKey = encryptionService.encrypt(apiKey);
        log.info("Encrypted key length: {}", encryptedKey);

        Tenant tenant = new Tenant();
        tenant.setApiKeyHash(encryptionService.encrypt(apiKey));
        tenant.setTenantId(tenantId);
        tenant.setName(request.organizationName());
        tenant.setContactEmail(encryptedEmail);

        tenant.setIsActive(true);
        tenant.setCreatedAt(LocalDateTime.now());

        tenantRepository.save(tenant);

        return new TenantOnboardingResponse(tenantId, apiKey, tenant.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public TenantOnboardingResponse getTenant(String tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + tenantId));

        String dummyApiKey = "sk_live_********"; //TODO логика восстановления

        return new TenantOnboardingResponse(tenantId, dummyApiKey, tenant.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public void validateTenantAndApiKey(String tenantId, String providedApiKey) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + tenantId));

        if (!tenant.getIsActive()) {
            throw new IllegalArgumentException("Tenant is inactive: " + tenantId);
        }

        try {
            log.info("Stored encrypted key length: {}", tenant.getApiKeyHash());
            String storedApiKey = encryptionService.decrypt(tenant.getApiKeyHash());
            log.info("Decrypted API key: {}", storedApiKey);
            log.info("Provided API key: {}", providedApiKey);

            if (!providedApiKey.equals(storedApiKey)) {
                throw new IllegalArgumentException("Invalid API key");
            }
        } catch (Exception e) {
            log.error("Decryption failed", e);
            throw new IllegalArgumentException("Invalid API key format", e);
        }
    }

    @Transactional(readOnly = true)
    public Optional<String> findTenantIdByYooKassaShopId(String shopId) {
        return tenantRepository.findByYooKassaShopId(shopId)
                .map(Tenant::getTenantId);
    }

    @Transactional
    public void updatePaymentSettings(String tenantId, TenantPaymentSettingsRequest request) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + tenantId));

        tenant.setYooKassaShopId(request.yookassaShopId());
        tenant.setYooKassaSecretKey(encryptionService.encrypt(request.yookassaSecretKey()));

        tenantRepository.save(tenant);

        log.info("Updated YooKassa settings for tenant: {} (shopId: {})",
                tenantId, request.yookassaShopId());
    }

    private String generateTenantId(String organizationName) {
        String cleanName = organizationName.toLowerCase()
                .replaceAll("[^a-zа-я0-9\\s]", "")
                .replaceAll("\\s+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");

        if (cleanName.isEmpty()) {
            return "tenant_" + UUID.randomUUID().toString().substring(0, 8);
        }

        return cleanName;
    }
}
