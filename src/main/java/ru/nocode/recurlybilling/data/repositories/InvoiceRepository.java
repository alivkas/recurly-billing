package ru.nocode.recurlybilling.data.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.nocode.recurlybilling.data.entities.Invoice;

import java.util.List;
import java.util.UUID;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {
    List<Invoice> findByTenantId(String tenantId);
    List<Invoice> findBySubscriptionId(UUID subscriptionId);
    Invoice findByPaymentId(String paymentId);
}
