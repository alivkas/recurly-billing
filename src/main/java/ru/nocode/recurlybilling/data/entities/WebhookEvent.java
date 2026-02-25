package ru.nocode.recurlybilling.data.entities;

import com.vladmihalcea.hibernate.type.json.JsonType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UuidGenerator;
import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "webhook_events")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WebhookEvent {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Type(JsonType.class)
    @Column(columnDefinition = "jsonb")
    private JsonNode rawPayload;

    @Column(name = "signature_valid", nullable = false)
    private Boolean signatureValid;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    private String error;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}