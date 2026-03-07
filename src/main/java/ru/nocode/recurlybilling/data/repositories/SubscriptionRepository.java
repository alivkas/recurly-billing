package ru.nocode.recurlybilling.data.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import ru.nocode.recurlybilling.data.entities.Subscription;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {
    @Query("SELECT s FROM Subscription s " +
            "JOIN Customer c ON s.customerId = c.id " +
            "WHERE c.tenantId = :tenantId AND c.externalId = :externalId AND s.isActive = true")
    List<Subscription> findByTenantIdAndCustomerExternalId(String tenantId, String externalId);
    List<Subscription> findByTenantIdAndStatusAndNextBillingDateBefore(
            String tenantId, String status, LocalDate date
    );
    List<Subscription> findByTenantIdAndStatus(String tenantId, String status);
    Optional<Subscription> findByIdAndTenantId(UUID id, String tenantId);
    List<Subscription> findByTenantIdAndCustomerId(String tenantId, UUID customerId);
}
