package ru.nocode.recurlybilling.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.nocode.recurlybilling.data.dto.AuditLogEntry;
import ru.nocode.recurlybilling.data.entities.AuditLog;
import ru.nocode.recurlybilling.data.repositories.AuditLogRepository;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public AuditLogEntry logEvent(String tenantId, String userId, String action,
                                  String resourceType, String resourceId,
                                  Map<String, Object> oldValues, Map<String, Object> newValues,
                                  String ipAddress, String userAgent) {

        try {
            AuditLog auditLog = new AuditLog();
            auditLog.setTenantId(tenantId);
            auditLog.setUserId(userId != null ? userId : "system");
            auditLog.setAction(action);
            auditLog.setResourceType(resourceType);
            auditLog.setResourceId(resourceId);
            auditLog.setIpAddress(ipAddress);
            auditLog.setUserAgent(userAgent);
            auditLog.setCreatedAt(LocalDateTime.now());

            if (oldValues != null && !oldValues.isEmpty()) {
                auditLog.setOldValues(objectMapper.valueToTree(oldValues));
            }
            if (newValues != null && !newValues.isEmpty()) {
                auditLog.setNewValues(objectMapper.valueToTree(newValues));
            }

            AuditLog saved = auditLogRepository.save(auditLog);
            log.info("✅ Audit event logged: tenant={}, action={}, resource={}/{}",
                    tenantId, action, resourceType, resourceId);
            return convertToDto(saved);
        } catch (Exception e) {
            log.error("❌ Failed to log audit event: tenant={}, action={}, resource={}/{}",
                    tenantId, action, resourceType, resourceId, e);
            throw new RuntimeException("Audit logging failed", e);
        }
    }

    public void logCreate(String tenantId, String userId, String resourceType, String resourceId,
                          Map<String, Object> newValues, String ipAddress, String userAgent) {
        logEvent(tenantId, userId, "create", resourceType, resourceId,
                null, newValues, ipAddress, userAgent);
    }

    public void logUpdate(String tenantId, String userId, String resourceType, String resourceId,
                          Map<String, Object> oldValues, Map<String, Object> newValues,
                          String ipAddress, String userAgent) {
        logEvent(tenantId, userId, "update", resourceType, resourceId,
                oldValues, newValues, ipAddress, userAgent);
    }

    public void logDelete(String tenantId, String userId, String resourceType, String resourceId,
                          Map<String, Object> oldValues, String ipAddress, String userAgent) {
        logEvent(tenantId, userId, "delete", resourceType, resourceId,
                oldValues, null, ipAddress, userAgent);
    }

    public void logPaymentSuccess(String tenantId, String customerId, String paymentId,
                                  Long amountCents, String ipAddress, String userAgent) {
        Map<String, Object> values = new HashMap<>();
        values.put("payment_id", paymentId);
        values.put("amount_cents", amountCents);
        values.put("status", "paid");

        logEvent(tenantId, customerId, "payment_success", "payment", paymentId,
                null, values, ipAddress, userAgent);
    }

    public void logPaymentFailed(String tenantId, String customerId, String paymentId,
                                 Long amountCents, int attemptCount, String ipAddress, String userAgent) {
        Map<String, Object> values = new HashMap<>();
        values.put("payment_id", paymentId);
        values.put("amount_cents", amountCents);
        values.put("attempt_count", attemptCount);
        values.put("status", "failed");

        logEvent(tenantId, customerId, "payment_failed", "payment", paymentId,
                null, values, ipAddress, userAgent);
    }

    @Transactional(readOnly = true)
    public List<AuditLogEntry> getAuditLogsByTenant(String tenantId) {
        return auditLogRepository.findByTenantId(tenantId).stream()
                .map(this::convertToDto)
                .sorted(Comparator.comparing(AuditLogEntry::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<AuditLogEntry> getAuditLogsByUser(String tenantId, String userId) {
        return auditLogRepository.findByTenantIdAndUserId(tenantId, userId).stream()
                .map(this::convertToDto)
                .sorted(Comparator.comparing(AuditLogEntry::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<AuditLogEntry> getAuditLogsByResource(String tenantId, String resourceType, String resourceId) {
        return auditLogRepository.findByTenantIdAndResourceTypeAndResourceId(
                        tenantId, resourceType, resourceId
                ).stream()
                .map(this::convertToDto)
                .sorted(Comparator.comparing(AuditLogEntry::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public long countAuditEventsSince(String tenantId, LocalDateTime since) {
        return auditLogRepository.countByTenantIdSince(tenantId, since);
    }

    private AuditLogEntry convertToDto(AuditLog auditLog) {
        Map<String, Object> oldValues = null;
        Map<String, Object> newValues = null;

        if (auditLog.getOldValues() != null) {
            try {
                oldValues = objectMapper.convertValue(auditLog.getOldValues(), Map.class);
            } catch (Exception e) {
                log.warn("Failed to deserialize old values for audit log {}", auditLog.getId(), e);
            }
        }
        if (auditLog.getNewValues() != null) {
            try {
                newValues = objectMapper.convertValue(auditLog.getNewValues(), Map.class);
            } catch (Exception e) {
                log.warn("Failed to deserialize new values for audit log {}", auditLog.getId(), e);
            }
        }

        return new AuditLogEntry(
                auditLog.getId(),
                auditLog.getTenantId(),
                auditLog.getUserId(),
                auditLog.getAction(),
                auditLog.getResourceType(),
                auditLog.getResourceId(),
                oldValues,
                newValues,
                auditLog.getIpAddress(),
                auditLog.getUserAgent(),
                auditLog.getCreatedAt()
        );
    }
}
