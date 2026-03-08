package ru.nocode.recurlybilling.data.repositories;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.nocode.recurlybilling.data.entities.Notification;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    List<Notification> findByTenantId(String tenantId);
    List<Notification> findBySubscriptionId(UUID subscriptionId);
    List<Notification> findByStatus(String status);
}