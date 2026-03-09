package ru.nocode.recurlybilling.data.repositories;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.nocode.recurlybilling.data.entities.WebhookEvent;

@Repository
public interface WebhookEventRepository extends JpaRepository<WebhookEvent, UUID> {
    List<WebhookEvent> findByTenantId(String tenantId);
    List<WebhookEvent> findByPaymentId(String paymentId);
    long countByTenantIdAndEventType(String tenantId, String eventType);
}