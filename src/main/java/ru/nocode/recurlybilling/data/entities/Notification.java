package ru.nocode.recurlybilling.data.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "notifications")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Notification {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "subscription_id", nullable = false)
    private UUID subscriptionId;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false)
    private String channel;

    private byte[] recipient;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "is_delivered")
    private Boolean isDelivered = false;

    @Column(name = "error_message")
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
