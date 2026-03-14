package ru.nocode.recurlybilling.data.entities;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Comment;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "customers")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class Customer {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "external_id", nullable = false)
    private String externalId;

    @Column(name = "email", columnDefinition = "text")
    @Comment("Encrypted using AES/GCM")
    private String email;

    @Column(name = "full_name", columnDefinition = "text")
    private String fullName;

    @Column(name = "phone", columnDefinition = "text")
    private String phone;

    @Column(name = "is_student")
    private Boolean isStudent = true;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}