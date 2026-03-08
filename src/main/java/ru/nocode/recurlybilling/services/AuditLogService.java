package ru.nocode.recurlybilling.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.nocode.recurlybilling.data.dto.AuditLogEntry;
import ru.nocode.recurlybilling.data.entities.AuditLog;
import ru.nocode.recurlybilling.data.repositories.AuditLogRepository;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

        AuditLog logs = new AuditLog();
        logs.setId(UUID.randomUUID());
        logs.setTenantId(tenantId);
        logs.setUserId(userId != null ? userId : "system");
        logs.setAction(action);
        logs.setResourceType(resourceType);
        logs.setResourceId(resourceId);

        if (oldValues != null && !oldValues.isEmpty()) {
            try {
                logs.setOldValues(objectMapper.valueToTree(oldValues));
            } catch (Exception e) {
                log.warn("Failed to serialize old values for audit log {}", logs.getId(), e);
            }
        }

        if (newValues != null && !newValues.isEmpty()) {
            try {
                logs.setNewValues(objectMapper.valueToTree(newValues));
            } catch (Exception e) {
                log.warn("Failed to serialize new values for audit log {}", logs.getId(), e);
            }
        }

        logs.setIpAddress(ipAddress);
        logs.setUserAgent(userAgent);
        logs.setCreatedAt(LocalDateTime.now());

        AuditLog saved = auditLogRepository.save(logs);
        log.info("Audit event logged: tenant={}, action={}, resource={}",
                tenantId, action, resourceId);

        return convertToDto(saved);
    }

    @Transactional(readOnly = true)
    public List<AuditLogEntry> getAuditLogsByTenant(String tenantId) {
        return auditLogRepository.findByTenantId(tenantId).stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<AuditLogEntry> getAuditLogsByUser(String tenantId, String userId) {
        return auditLogRepository.findByTenantIdAndUserId(tenantId, userId).stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<AuditLogEntry> getAuditLogsByResource(String tenantId, String resourceType, String resourceId) {
        return auditLogRepository.findByTenantIdAndResourceTypeAndResourceId(
                        tenantId, resourceType, resourceId
                ).stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public long countAuditEventsSince(String tenantId, LocalDateTime since) {
        return auditLogRepository.countByTenantIdSince(tenantId, since);
    }

    private AuditLogEntry convertToDto(AuditLog logs) {
        Map<String, Object> oldValues = null;
        Map<String, Object> newValues = null;

        if (logs.getOldValues() != null) {
            try {
                oldValues = objectMapper.convertValue(logs.getOldValues(), Map.class);
            } catch (Exception e) {
                log.warn("Failed to deserialize old values for audit log {}", logs.getId(), e);
            }
        }

        if (logs.getNewValues() != null) {
            try {
                newValues = objectMapper.convertValue(logs.getNewValues(), Map.class);
            } catch (Exception e) {
                log.warn("Failed to deserialize new values for audit log {}", logs.getId(), e);
            }
        }

        return new AuditLogEntry(
                logs.getId(),
                logs.getTenantId(),
                logs.getUserId(),
                logs.getAction(),
                logs.getResourceType(),
                logs.getResourceId(),
                oldValues,
                newValues,
                logs.getIpAddress(),
                logs.getUserAgent(),
                logs.getCreatedAt()
        );
    }
}
