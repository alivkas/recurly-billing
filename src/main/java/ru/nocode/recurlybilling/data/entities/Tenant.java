package ru.nocode.recurlybilling.data.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "tenants", uniqueConstraints = @UniqueConstraint(columnNames = "tenant_id"))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class Tenant {

    @Id
    private String tenantId;

    @Column(nullable = false)
    private String name;

    @Column(name = "contact_email")
    private byte[] contactEmail;

    @Column(name = "api_key_hash", nullable = false)
    private String apiKeyHash;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "payment_return_url")
    private String paymentReturnUrl;

    @Column(name = "yookassa_shop_id")
    private String yooKassaShopId;

    @Column(name = "yookassa_secret_key", columnDefinition = "bytea")
    private byte[] yooKassaSecretKey;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
