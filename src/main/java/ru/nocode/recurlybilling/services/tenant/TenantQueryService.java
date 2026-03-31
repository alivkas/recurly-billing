package ru.nocode.recurlybilling.services.tenant;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.nocode.recurlybilling.data.entities.Tenant;
import ru.nocode.recurlybilling.data.repositories.TenantRepository;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TenantQueryService {

    private final TenantRepository tenantRepository;

    /**
     * Получить тенанта по id
     * @param tenantId id тенанта
     * @return тенант
     */
    public Tenant findTenantById(String tenantId) {
        return tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + tenantId));
    }

    /**
     * Получить id тенанта по id магазина ЮКасса
     * @param shopId id магазина ЮКасса
     * @return id тенанта
     */
    @Transactional(readOnly = true)
    public Optional<String> findTenantIdByYooKassaShopId(String shopId) {
        return tenantRepository.findByYooKassaShopId(shopId)
                .map(Tenant::getTenantId);
    }
}
