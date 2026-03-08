package ru.nocode.recurlybilling.data.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.UUID;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Data
@Entity
@Table(name = "webhook_events")
public class WebhookEvent {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "payment_id")
    private String paymentId;

    @Column(name = "signature", nullable = false)
    private String signature;

    @Column(columnDefinition = "text")
    private String rawRequestBody;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private JsonNode parsedBody;

    @Column(nullable = false)
    private String status; // received, processed, failed

    @Column(name = "processing_error", columnDefinition = "text")
    private String processingError;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}