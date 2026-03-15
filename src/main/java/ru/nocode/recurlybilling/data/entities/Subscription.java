package ru.nocode.recurlybilling.data.entities;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.*;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "subscriptions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class Subscription {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    @Column(nullable = false)
    private String status;

    private int failedPaymentAttempts = 0;

    private LocalDateTime pastDueSince;

    @Column(name = "payment_method")
    private String paymentMethod;

    @Column(name = "payment_method_id", nullable = true)
    @Comment("Saved payment method ID from YooKassa. Null during trial period.")
    private String paymentMethodId;

    @Column(name = "current_period_start", nullable = false)
    private LocalDate currentPeriodStart;

    @Column(name = "current_period_end", nullable = true)
    private LocalDate currentPeriodEnd;

    @Column(name = "next_billing_date")
    private LocalDate nextBillingDate;

    @Column(name = "cancel_at")
    private LocalDate cancelAt;

    @Column(name = "trial_end")
    private LocalDate trialEnd;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private JsonNode metadata;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "interval")
    private String interval;

    @Column(name = "amount_cents")
    private Long amountCents;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
