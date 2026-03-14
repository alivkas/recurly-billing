package ru.nocode.recurlybilling.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.nocode.recurlybilling.data.dto.AuditLogEntry;
import ru.nocode.recurlybilling.services.AuditLogService;
import ru.nocode.recurlybilling.services.TenantService;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/audit")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;
    private final TenantService tenantService;

    @GetMapping
    public ResponseEntity<List<AuditLogEntry>> getAuditLogs(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-API-Key") String apiKey) {

        try {
            tenantService.validateTenantAndApiKey(tenantId, apiKey);
            List<AuditLogEntry> logs = auditLogService.getAuditLogsByTenant(tenantId);
            return ResponseEntity.ok(logs);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid tenant or API key: {}", tenantId);
            return ResponseEntity.status(401).build();
        } catch (Exception e) {
            log.error("Error retrieving audit logs for tenant: {}", tenantId, e);
            return ResponseEntity.status(500).build();
        }
    }

    @GetMapping("/user/{externalId}")
    public ResponseEntity<List<AuditLogEntry>> getAuditLogsByUser(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-API-Key") String apiKey,
            @PathVariable String externalId) {

        try {
            tenantService.validateTenantAndApiKey(tenantId, apiKey);
            List<AuditLogEntry> logs = auditLogService.getAuditLogsByUser(tenantId, externalId);
            return ResponseEntity.ok(logs);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid tenant or API key: {}", tenantId);
            return ResponseEntity.status(401).build();
        } catch (Exception e) {
            log.error("Error retrieving audit logs for user: {}", externalId, e);
            return ResponseEntity.status(500).build();
        }
    }

    @GetMapping("/resource/{resourceType}/{resourceId}")
    public ResponseEntity<List<AuditLogEntry>> getAuditLogsByResource(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-API-Key") String apiKey,
            @PathVariable String resourceType,
            @PathVariable String resourceId) {

        try {
            tenantService.validateTenantAndApiKey(tenantId, apiKey);
            List<AuditLogEntry> logs = auditLogService.getAuditLogsByResource(tenantId, resourceType, resourceId);
            return ResponseEntity.ok(logs);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid tenant or API key: {}", tenantId);
            return ResponseEntity.status(401).build();
        } catch (Exception e) {
            log.error("Error retrieving audit logs for resource: {}/{}", resourceType, resourceId, e);
            return ResponseEntity.status(500).build();
        }
    }
}
