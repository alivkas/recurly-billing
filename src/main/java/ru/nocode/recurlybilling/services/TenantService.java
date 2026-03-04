package ru.nocode.recurlybilling.services;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.nocode.recurlybilling.data.dto.request.TenantOnboardingRequest;
import ru.nocode.recurlybilling.data.dto.response.TenantOnboardingResponse;
import ru.nocode.recurlybilling.data.entities.Tenant;
import ru.nocode.recurlybilling.data.repositories.TenantRepository;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TenantService {

    private final TenantRepository tenantRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public TenantOnboardingResponse onboard(TenantOnboardingRequest request) {
        String tenantId = generateTenantId(request.companyName());

        if (tenantRepository.existsByTenantId(tenantId)) {
            throw new IllegalArgumentException("Tenant with ID '" + tenantId + "' already exists");
        }

        String apiKey = "sk_live_" + UUID.randomUUID().toString().replace("-", "");
        String apiKeyHash = passwordEncoder.encode(apiKey);

        Tenant tenant = new Tenant();
        tenant.setTenantId(tenantId);
        tenant.setName(request.companyName());
        tenant.setContactEmail(null); // TODO шифрование почты
        tenant.setApiKeyHash(apiKeyHash);
        tenant.setIsActive(true);
        tenant.setCreatedAt(LocalDateTime.now());

        tenantRepository.save(tenant);

        return new TenantOnboardingResponse(
                tenantId,
                apiKey,
                tenant.getCreatedAt()
        );
    }

    private String generateTenantId(String companyName) {
        return companyName.toLowerCase()
                .replaceAll("[^a-z0-9\\s]", "")
                .replaceAll("\\s+", "_")
                .substring(0, Math.min(32, companyName.length()));
    }
}
