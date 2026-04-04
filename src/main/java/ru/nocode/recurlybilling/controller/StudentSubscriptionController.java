package ru.nocode.recurlybilling.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.nocode.recurlybilling.data.dto.response.SubscriptionResponse;
import ru.nocode.recurlybilling.services.StudentAuthService;
import ru.nocode.recurlybilling.services.SubscriptionService;
import ru.nocode.recurlybilling.utils.docs.StudentSubscriptionDocs;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/student")
@RequiredArgsConstructor
@Tag(name = "🎓 Подписка студента", description = StudentSubscriptionDocs.TAG_DESCRIPTION)
public class StudentSubscriptionController {
    private final StudentAuthService studentAuthService;
    private final SubscriptionService subscriptionService;

    @GetMapping("/my-subscription")
    @Operation(
            summary = StudentSubscriptionDocs.GET_SUMMARY,
            description = StudentSubscriptionDocs.GET_DESCRIPTION,
            tags = {"🎓 Подписка студента"}
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "✅ Данные подписки получены",
                    content = @Content(
                            schema = @Schema(implementation = SubscriptionResponse.class),
                            examples = @ExampleObject(
                                    name = "Активная подписка",
                                    value = StudentSubscriptionDocs.GET_SUCCESS_EXAMPLE
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "❌ У студента нет подписок",
                    content = @Content(examples = @ExampleObject(value = ""))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "🔐 Неверный или истёкший токен",
                    content = @Content(examples = @ExampleObject(
                            value = """
                    { "error": "Invalid token" }
                    """
                    ))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "💥 Внутренняя ошибка сервера",
                    content = @Content(examples = @ExampleObject(
                            value = """
                    { "error": "Internal server error" }
                    """
                    ))
            )
    })
    public ResponseEntity<?> getMySubscription(@RequestHeader("X-Student-Token") String token) {
        try {
            String tenantId = studentAuthService.getTenantIdByToken(token);
            String studentExternalId = studentAuthService.getStudentExternalIdByToken(token);

            List<SubscriptionResponse> subscriptions = subscriptionService
                    .getSubscriptionsByCustomer(tenantId, studentExternalId);

            if (subscriptions.isEmpty()) {
                return ResponseEntity.notFound().build();
            }

            SubscriptionResponse active = subscriptions.stream()
                    .filter(s -> "active".equals(s.status()) || "trialing".equals(s.status()))
                    .findFirst()
                    .orElse(subscriptions.get(0));

            return ResponseEntity.ok(active);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid token: {}", e.getMessage());
            return ResponseEntity.status(401).body(Map.of("error", "Invalid token"));
        } catch (Exception e) {
            log.error("Error retrieving subscription", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Internal server error"));
        }
    }

    @PostMapping("/my-subscription/cancel")
    @Operation(
            summary = StudentSubscriptionDocs.CANCEL_SUMMARY,
            description = StudentSubscriptionDocs.CANCEL_DESCRIPTION,
            tags = {"🎓 Подписка студента"}
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "✅ Подписка отменена",
                    content = @Content(
                            schema = @Schema(),
                            examples = @ExampleObject(
                                    name = "Успешная отмена",
                                    value = StudentSubscriptionDocs.CANCEL_SUCCESS_EXAMPLE
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "❌ Нельзя отменить: нет активной подписки",
                    content = @Content(examples = @ExampleObject(
                            value = """
                    { "error": "No active subscription found" }
                    """
                    ))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "🔐 Неверный токен",
                    content = @Content(examples = @ExampleObject(
                            value = """
                    { "error": "Invalid token" }
                    """
                    ))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "💥 Ошибка сервера"
            )
    })
    public ResponseEntity<?> cancelMySubscription(
            @RequestHeader("X-Student-Token") String token,
            @RequestBody CancelRequest request) {
        try {
            String tenantId = studentAuthService.getTenantIdByToken(token);
            String studentExternalId = studentAuthService.getStudentExternalIdByToken(token);

            List<SubscriptionResponse> subscriptions = subscriptionService
                    .getSubscriptionsByCustomer(tenantId, studentExternalId);

            SubscriptionResponse active = subscriptions.stream()
                    .filter(s -> "active".equals(s.status()) || "trialing".equals(s.status()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("No active subscription found"));

            subscriptionService.cancelSubscription(
                    tenantId,
                    active.id().toString(),
                    new ru.nocode.recurlybilling.data.dto.request.SubscriptionCancelRequest(request.isCancelImmediately())
            );

            //TODO
            // Если немедленная отмена — отзыв доступа
            if (request.isCancelImmediately()) {
                log.info("Immediate access revocation triggered for student {}", studentExternalId);
            }

            return ResponseEntity.ok(Map.of("message", "Subscription cancelled successfully"));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid token: {}", e.getMessage());
            return ResponseEntity.status(401).body(Map.of("error", "Invalid token"));
        } catch (IllegalStateException e) {
            log.warn("Cancellation failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error cancelling subscription", e);
            return ResponseEntity.internalServerError().body(Map.of("error", "Internal server error"));
        }
    }

    @Data
    public static class CancelRequest {
        private boolean cancelImmediately;
    }
}
