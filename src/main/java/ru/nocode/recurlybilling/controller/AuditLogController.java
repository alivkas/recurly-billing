package ru.nocode.recurlybilling.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.nocode.recurlybilling.data.dto.AuditLogEntry;
import ru.nocode.recurlybilling.services.AuditLogService;
import ru.nocode.recurlybilling.services.TenantService;
import ru.nocode.recurlybilling.utils.docs.AuditLogDocs;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/audit")
@RequiredArgsConstructor
@Tag(name = "🔍 Аудит", description = AuditLogDocs.TAG_DESCRIPTION)
public class AuditLogController {

    private final AuditLogService auditLogService;
    private final TenantService tenantService;

    @GetMapping
    @Operation(
            summary = AuditLogDocs.LIST_SUMMARY,
            description = AuditLogDocs.LIST_DESCRIPTION,
            tags = {"🔍 Аудит"},
            security = { @SecurityRequirement(name = "TenantAuth") }
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "✅ Список логов получен",
                    content = @Content(schema = @Schema(implementation = AuditLogEntry.class))
            ),
            @ApiResponse(responseCode = "401", description = "🔐 Неверные учётные данные"),
            @ApiResponse(responseCode = "403", description = "🚫 Доступ запрещён"),
            @ApiResponse(responseCode = "500", description = "💥 Ошибка сервера")
    })
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
    @Operation(
            summary = AuditLogDocs.BY_USER_SUMMARY,
            description = AuditLogDocs.BY_USER_DESCRIPTION,
            tags = {"🔍 Аудит"},
            security = { @SecurityRequirement(name = "TenantAuth") }
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "✅ Логи пользователя получены",
                    content = @Content(schema = @Schema(implementation = AuditLogEntry.class))
            ),
            @ApiResponse(responseCode = "401", description = "🔐 Неверные учётные данные"),
            @ApiResponse(responseCode = "403", description = "🚫 Доступ запрещён"),
            @ApiResponse(responseCode = "404", description = "❌ Пользователь не найден"),
            @ApiResponse(responseCode = "500", description = "💥 Ошибка сервера")
    })
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
    @Operation(
            summary = AuditLogDocs.BY_RESOURCE_SUMMARY,
            description = AuditLogDocs.BY_RESOURCE_DESCRIPTION,
            tags = {"🔍 Аудит"},
            security = { @SecurityRequirement(name = "TenantAuth") }
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "✅ История ресурса получена",
                    content = @Content(schema = @Schema(implementation = AuditLogEntry.class))
            ),
            @ApiResponse(responseCode = "401", description = "🔐 Неверные учётные данные"),
            @ApiResponse(responseCode = "403", description = "🚫 Доступ запрещён"),
            @ApiResponse(responseCode = "404", description = "❌ Ресурс не найден"),
            @ApiResponse(responseCode = "500", description = "💥 Ошибка сервера")
    })
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
