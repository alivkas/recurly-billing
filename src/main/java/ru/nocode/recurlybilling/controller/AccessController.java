package ru.nocode.recurlybilling.controller;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.nocode.recurlybilling.services.AccessService;
import ru.nocode.recurlybilling.services.SubscriptionService;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/access")
@RequiredArgsConstructor
public class AccessController {

    private final AccessService accessService;
    private final SubscriptionService subscriptionService;

    @GetMapping("/check")
    public ResponseEntity<AccessCheckResponse> checkAccess(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-API-Key") String apiKey,
            @RequestParam String studentId,
            @RequestParam String planCode) {

        subscriptionService.validateTenantAndApiKey(tenantId, apiKey);

        if (!accessService.isStudentBelongsToTenant(studentId, tenantId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new AccessCheckResponse(false, null, "STUDENT_NOT_FOUND", null));
        }

        boolean hasAccess = accessService.hasActiveAccess(UUID.fromString(studentId), planCode);
        if (hasAccess) {
            LocalDate expiresAt = accessService.getAccessExpiry(UUID.fromString(studentId), planCode);
            return ResponseEntity.ok(new AccessCheckResponse(true, expiresAt, "HAS_ACCESS", null));
        } else {
            return ResponseEntity.ok(new AccessCheckResponse(false, null, "NO_ACTIVE_ACCESS", null));
        }
    }

    @Data
    @AllArgsConstructor
    public static class AccessCheckResponse {
        private boolean hasAccess;
        private LocalDate expiresAt;
        private String status;
        private String reason;
    }
}
