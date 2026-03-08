package ru.nocode.recurlybilling.data.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.nocode.recurlybilling.data.entities.Plan;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlanRepository extends JpaRepository<Plan, UUID> {
    List<Plan> findByTenantId(String tenantId);
    Plan findByTenantIdAndCode(String tenantId, String code);
    boolean existsByTenantIdAndCode(String tenantId, String code);
    Optional<Plan> findByIdAndTenantId(UUID id, String tenantId);
}