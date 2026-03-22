package ru.nocode.recurlybilling.controller;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.nocode.recurlybilling.data.entities.Customer;
import ru.nocode.recurlybilling.data.repositories.CustomerRepository;
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
    private final CustomerRepository customerRepository;

    @GetMapping("/check")
    public ResponseEntity<AccessCheckResponse> checkAccess(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-API-Key") String apiKey,
            @RequestParam String studentId,  // ← это externalId (например "alivka")
            @RequestParam String planCode) {

        subscriptionService.validateTenantAndApiKey(tenantId, apiKey);

        // Проверяем принадлежность по externalId (строка)
        if (!accessService.isStudentBelongsToTenantByExternalId(studentId, tenantId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new AccessCheckResponse(false, null, "STUDENT_NOT_FOUND", null));
        }

        // Получаем клиента по externalId для проверки доступа
        Customer customer = customerRepository.findByTenantIdAndExternalId(tenantId, studentId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found"));

        boolean hasAccess = accessService.hasActiveAccess(customer.getId(), planCode);
        if (hasAccess) {
            LocalDate expiresAt = accessService.getAccessExpiry(customer.getId(), planCode);
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
