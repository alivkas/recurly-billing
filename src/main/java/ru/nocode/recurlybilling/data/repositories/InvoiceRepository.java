package ru.nocode.recurlybilling.data.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.nocode.recurlybilling.data.entities.Invoice;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {
    List<Invoice> findByTenantId(String tenantId);
    Optional<Invoice> findByPaymentId(String paymentId);
    List<Invoice> findBySubscriptionIdAndStatusOrderByCreatedAtDesc(UUID id, String status);
    List<Invoice> findBySubscriptionIdOrderByCreatedAtDesc(UUID subscriptionId);
    @Query("SELECT SUM(i.amountCents) FROM Invoice i WHERE i.tenantId = :tenantId AND i.status = 'paid' AND i.createdAt BETWEEN :start AND :end")
    Optional<Long> findPaidRevenueByTenantAndPeriod(
            @Param("tenantId") String tenantId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end
    );
    @Query("SELECT i FROM Invoice i WHERE i.tenantId = :tenantId AND i.status = :status AND i.nextRetryAt <= :now")
    List<Invoice> findByTenantIdAndStatusAndNextRetryAtBefore(
            @Param("tenantId") String tenantId,
            @Param("status") String status,
            @Param("now") LocalDateTime now
    );
    Optional<Invoice> findBySubscriptionIdAndStatus(UUID id, String status);
}
