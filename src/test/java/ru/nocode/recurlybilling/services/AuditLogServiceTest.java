package ru.nocode.recurlybilling.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import ru.nocode.recurlybilling.data.dto.AuditLogEntry;
import ru.nocode.recurlybilling.data.entities.AuditLog;
import ru.nocode.recurlybilling.data.repositories.AuditLogRepository;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuditLogServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private AuditLogService auditLogService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void logEventShouldSaveAuditLogWithCorrectData() {
        String tenantId = "moscow_digital_school";
        String userId = "admin_user";
        String action = "CREATE_SUBSCRIPTION";
        String resourceType = "SUBSCRIPTION";
        String resourceId = "sub_123";
        Map<String, Object> oldValues = null;
        Map<String, Object> newValues = Map.of("status", "active", "plan", "math-autumn-2025");
        String ipAddress = "192.168.1.1";
        String userAgent = "Mozilla/5.0";

        AuditLog savedLog = new AuditLog();
        savedLog.setId(UUID.randomUUID());
        savedLog.setTenantId(tenantId);
        savedLog.setUserId(userId);
        savedLog.setAction(action);
        savedLog.setResourceType(resourceType);
        savedLog.setResourceId(resourceId);
        savedLog.setIpAddress(ipAddress);
        savedLog.setUserAgent(userAgent);
        savedLog.setCreatedAt(LocalDateTime.now());

        when(auditLogRepository.save(any(AuditLog.class))).thenReturn(savedLog);

        AuditLogEntry entry = auditLogService.logEvent(
                tenantId, userId, action, resourceType, resourceId,
                oldValues, newValues, ipAddress, userAgent
        );

        assertThat(entry.getTenantId()).isEqualTo(tenantId);
        assertThat(entry.getUserId()).isEqualTo(userId);
        assertThat(entry.getAction()).isEqualTo(action);
        assertThat(entry.getResourceType()).isEqualTo(resourceType);
        assertThat(entry.getResourceId()).isEqualTo(resourceId);
        assertThat(entry.getIpAddress()).isEqualTo(ipAddress);
        assertThat(entry.getUserAgent()).isEqualTo(userAgent);

        verify(auditLogRepository, times(1)).save(any(AuditLog.class));
    }

    @Test
    void logEventWithNullUserIdShouldUseSystem() {
        AuditLog savedLog = new AuditLog();
        savedLog.setId(UUID.randomUUID());
        savedLog.setTenantId("test_tenant");
        savedLog.setUserId("system"); // ← должно быть "system"
        savedLog.setAction("SYSTEM_EVENT");
        savedLog.setResourceType("PLAN");
        savedLog.setResourceId("plan_123");
        savedLog.setCreatedAt(LocalDateTime.now());

        when(auditLogRepository.save(any(AuditLog.class))).thenReturn(savedLog);

        AuditLogEntry entry = auditLogService.logEvent(
                "test_tenant", null, "SYSTEM_EVENT", "PLAN", "plan_123",
                null, null, null, null
        );

        assertThat(entry.getUserId()).isEqualTo("system");
    }

    @Test
    void getAuditLogsByTenantShouldReturnListOfEntries() {
        String tenantId = "moscow_digital_school";
        AuditLog log1 = createAuditLog(tenantId, "user1", "CREATE_SUBSCRIPTION");
        AuditLog log2 = createAuditLog(tenantId, "user2", "UPDATE_CUSTOMER");

        when(auditLogRepository.findByTenantId(tenantId))
                .thenReturn(Arrays.asList(log1, log2));

        List<AuditLogEntry> entries = auditLogService.getAuditLogsByTenant(tenantId);

        assertThat(entries).hasSize(2);
        assertThat(entries.get(0).getTenantId()).isEqualTo(tenantId);
        assertThat(entries.get(0).getAction()).isEqualTo("CREATE_SUBSCRIPTION");
        assertThat(entries.get(1).getAction()).isEqualTo("UPDATE_CUSTOMER");
    }

    @Test
    void countAuditEventsSinceShouldReturnCorrectCount() {
        String tenantId = "moscow_digital_school";
        LocalDateTime since = LocalDateTime.now().minusDays(7);

        when(auditLogRepository.countByTenantIdSince(tenantId, since))
                .thenReturn(42L);

        long count = auditLogService.countAuditEventsSince(tenantId, since);

        assertThat(count).isEqualTo(42L);
    }

    private AuditLog createAuditLog(String tenantId, String userId, String action) {
        AuditLog log = new AuditLog();
        log.setId(UUID.randomUUID());
        log.setTenantId(tenantId);
        log.setUserId(userId);
        log.setAction(action);
        log.setResourceType("TEST");
        log.setResourceId("test_123");
        log.setCreatedAt(LocalDateTime.now());
        return log;
    }
}