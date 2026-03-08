package ru.nocode.recurlybilling.data.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import ru.nocode.recurlybilling.data.entities.AuditLog;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    List<AuditLog> findByTenantId(String tenantId);

    List<AuditLog> findByTenantIdAndUserId(String tenantId, String userId);

    List<AuditLog> findByTenantIdAndAction(String tenantId, String action);

    List<AuditLog> findByTenantIdAndResourceTypeAndResourceId(
            String tenantId, String resourceType, String resourceId);

    @Query("SELECT COUNT(a) FROM AuditLog a WHERE a.tenantId = :tenantId AND a.createdAt >= :since")
    long countByTenantIdSince(String tenantId, LocalDateTime since);
}
