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
    List<Subscription> findByTenantId(String tenantId);
    List<Subscription> findByTenantIdAndStatus(String tenantId, String status);
    Optional<Subscription> findByIdAndTenantId(UUID id, String tenantId);
    List<Subscription> findByTenantIdAndCustomerId(String tenantId, UUID customerId);
    @Query("SELECT COUNT(s) FROM Subscription s WHERE s.tenantId = :tenantId AND s.status = 'active'")
    long countByTenantIdAndStatus(String tenantId, String status);

    @Query("SELECT COUNT(s) FROM Subscription s " +
            "WHERE s.tenantId = :tenantId AND s.createdAt <= :date AND " +
            "(s.status = 'active' OR s.status = 'trialing' OR " +
            "(s.status = 'cancelled' AND s.cancelAt > :date))")
    long countActiveSubscriptionsAtDate(String tenantId, LocalDate date);

    @Query("SELECT COUNT(s) FROM Subscription s " +
            "WHERE s.tenantId = :tenantId AND s.status = 'cancelled' AND " +
            "s.cancelAt BETWEEN :startDate AND :endDate")
    long countCancelledSubscriptionsInPeriod(String tenantId, LocalDate startDate, LocalDate endDate);
}
