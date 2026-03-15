package ru.nocode.recurlybilling.controller;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.nocode.recurlybilling.data.dto.response.SubscriptionResponse;
import ru.nocode.recurlybilling.services.StudentAuthService;
import ru.nocode.recurlybilling.services.SubscriptionService;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/student")
@RequiredArgsConstructor
public class StudentSubscriptionController {
    private final StudentAuthService studentAuthService;
    private final SubscriptionService subscriptionService;

    @GetMapping("/my-subscription")
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
