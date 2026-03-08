package ru.nocode.recurlybilling.data.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.nocode.recurlybilling.data.entities.Invoice;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {
    List<Invoice> findByTenantId(String tenantId);
    List<Invoice> findBySubscriptionId(UUID subscriptionId);
    Optional<Invoice> findByPaymentId(String paymentId);
    List<Invoice> findByStatusAndAttemptCountLessThan(String status, int maxAttempts);
    List<Invoice> findBySubscriptionIdAndStatusOrderByCreatedAtDesc(UUID id, String status);
    List<Invoice> findBySubscriptionIdOrderByCreatedAtDesc(UUID subscriptionId);
}
