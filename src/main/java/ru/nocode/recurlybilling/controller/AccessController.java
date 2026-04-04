package ru.nocode.recurlybilling.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import ru.nocode.recurlybilling.utils.docs.AccessDocs;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/access")
@RequiredArgsConstructor
@Tag(name = "🔐 Доступ", description = AccessDocs.TAG_DESCRIPTION)
public class AccessController {

    private final AccessService accessService;
    private final SubscriptionService subscriptionService;
    private final CustomerRepository customerRepository;

    @GetMapping("/check")
    @Operation(
            summary = AccessDocs.CHECK_SUMMARY,
            description = AccessDocs.CHECK_DESCRIPTION,
            tags = {"🔐 Доступ"},
            security = { @SecurityRequirement(name = "TenantAuth") }
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "✅ Проверка выполнена (доступ есть или нет)",
                    content = @Content(
                            schema = @Schema(implementation = AccessCheckResponse.class),
                            examples = {
                                    @ExampleObject(
                                            name = "Доступ есть",
                                            value = AccessDocs.CHECK_SUCCESS_EXAMPLE
                                    ),
                                    @ExampleObject(
                                            name = "Нет активной подписки",
                                            value = AccessDocs.CHECK_NO_ACCESS_EXAMPLE
                                    ),
                                    @ExampleObject(
                                            name = "Студент не найден",
                                            value = AccessDocs.CHECK_NOT_FOUND_EXAMPLE
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "🔐 Неверные учётные данные тенанта",
                    content = @Content(examples = @ExampleObject(
                            value = """
                    {
                      "timestamp": "2026-03-31T22:27:38Z",
                      "status": 401,
                      "error": "Unauthorized",
                      "message": "Invalid API key or tenant ID"
                    }
                    """
                    ))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "🚫 Студент не принадлежит этому тенанту",
                    content = @Content(examples = @ExampleObject(
                            value = """
                    {
                      "hasAccess": false,
                      "expiresAt": null,
                      "status": "STUDENT_NOT_FOUND",
                      "reason": "Student does not belong to this tenant"
                    }
                    """
                    ))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "💥 Ошибка при проверке доступа"
            )
    })
    public ResponseEntity<AccessCheckResponse> checkAccess(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-API-Key") String apiKey,
            @RequestParam String studentId,
            @RequestParam String planCode) {

        subscriptionService.validateTenantAndApiKey(tenantId, apiKey);

        if (!accessService.isStudentBelongsToTenantByExternalId(studentId, tenantId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new AccessCheckResponse(false, null, "STUDENT_NOT_FOUND", null));
        }

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
