package ru.nocode.recurlybilling.data.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import ru.nocode.recurlybilling.data.entities.Invoice;
import ru.nocode.recurlybilling.data.entities.Tenant;

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

    @Query(value = """
        SELECT DATE(i.paid_at) as date, SUM(i.amount_cents) as revenue
        FROM invoices i
        WHERE i.tenant_id = :tenantId 
          AND i.status = 'paid'
          AND i.paid_at BETWEEN :start AND :end
        GROUP BY DATE(i.paid_at)
        ORDER BY DATE(i.paid_at)
        """, nativeQuery = true)
    List<Object[]> findDailyRevenueByTenantAndPeriod(
            @Param("tenantId") String tenantId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("""
        SELECT i.status, COUNT(*)
        FROM Invoice i
        WHERE i.tenantId = :tenantId 
          AND i.createdAt BETWEEN :start AND :end
        GROUP BY i.status
        """)
    List<Object[]> countInvoicesByStatusAndPeriod(
            @Param("tenantId") String tenantId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("""
        SELECT i.failureReason, COUNT(*)
        FROM Invoice i
        WHERE i.tenantId = :tenantId 
          AND i.status = 'failed'
          AND i.createdAt BETWEEN :start AND :end
          AND i.failureReason IS NOT NULL
        GROUP BY i.failureReason
        """)
    List<Object[]> countFailureReasonsByPeriod(
            @Param("tenantId") String tenantId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("""
        SELECT COUNT(DISTINCT i.subscriptionId)
        FROM Invoice i
        WHERE i.tenantId = :tenantId 
          AND i.status = 'paid'
          AND i.paidAt BETWEEN :start AND :end
        """)
    Long countActivePayersByPeriod(
            @Param("tenantId") String tenantId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("""
        SELECT SUM(i.amountCents)
        FROM Invoice i
        WHERE i.tenantId = :tenantId 
          AND i.status = 'paid'
          AND i.paidAt BETWEEN :start AND :end
        """)
    Optional<Long> findPaidRevenueByPaidAtPeriod(
            @Param("tenantId") String tenantId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query(value = """
        SELECT DATE(i.paid_at) as date, SUM(i.amount_cents) as revenue
        FROM invoices i
        WHERE i.tenant_id = :tenantId 
          AND i.status = 'paid'
          AND i.paid_at BETWEEN :start AND :end
        GROUP BY DATE(i.paid_at)
        ORDER BY DATE(i.paid_at)
        """, nativeQuery = true)
    List<Object[]> findDailyRevenueByPaidAt(
            @Param("tenantId") String tenantId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    Optional<Invoice> findFirstByTenantIdOrderByCreatedAtAsc(String tenantId);

    @Query("""
        SELECT COUNT(c)
        FROM Customer c
        WHERE c.tenantId = :tenantId 
          AND c.createdAt BETWEEN :start AND :end
        """)
    Long countNewCustomersByPeriod(
            @Param("tenantId") String tenantId,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);
}
