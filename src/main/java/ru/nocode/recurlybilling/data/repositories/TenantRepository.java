package ru.nocode.recurlybilling.data.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.nocode.recurlybilling.data.entities.Tenant;

import java.util.List;

@Repository
public interface TenantRepository extends JpaRepository<Tenant, String> {
    boolean existsByTenantId(String tenantId);
    List<String> findAllActiveTenantIds();
}
