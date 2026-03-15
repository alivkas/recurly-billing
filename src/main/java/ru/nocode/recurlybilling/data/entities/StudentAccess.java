package ru.nocode.recurlybilling.data.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "student_access")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StudentAccess {
    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "tenant_id", nullable = false)
    private String tenantId;

    @Column(name = "plan_code", nullable = false)
    private String planCode;

    @Column(name = "access_granted_at", nullable = false)
    private LocalDateTime accessGrantedAt = LocalDateTime.now();

    @Column(name = "access_expires_at", nullable = false)
    private LocalDate accessExpiresAt;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private AccessStatus status = AccessStatus.ACTIVE;

    public enum AccessStatus {
        ACTIVE, EXPIRED, REVOKED
    }
}