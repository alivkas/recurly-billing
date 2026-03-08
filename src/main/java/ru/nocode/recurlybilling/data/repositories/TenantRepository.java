package ru.nocode.recurlybilling.data.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.nocode.recurlybilling.data.entities.Tenant;

import java.util.List;
import java.util.Optional;

@Repository
public interface TenantRepository extends JpaRepository<Tenant, String> {
    boolean existsByTenantId(String tenantId);
    List<String> findAllActiveTenantIds();
    Optional<Tenant> findByYooKassaShopId(String shopId);
}
