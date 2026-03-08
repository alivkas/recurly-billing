package ru.nocode.recurlybilling.data.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@AllArgsConstructor
@Getter
@Setter
public class AuditLogEntry {
    private UUID id;
    private String tenantId;
    private String userId;
    private String action;
    private String resourceType;
    private String resourceId;
    private Map<String, Object> oldValues;
    private Map<String, Object> newValues;
    private String ipAddress;
    private String userAgent;
    private LocalDateTime createdAt;
}