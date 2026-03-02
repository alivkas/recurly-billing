package ru.nocode.recurlybilling.data.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.nocode.recurlybilling.data.entities.Subscription;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {
    List<Subscription> findByTenantIdAndCustomerId(String tenantId, UUID customerId);
    List<Subscription> findByTenantIdAndStatusAndNextBillingDateBefore(
            String tenantId, String status, LocalDate date
    );
    List<Subscription> findByTenantIdAndStatus(String tenantId, String status);
    Subscription findByIdAndTenantId(UUID id, String tenantId);
}
