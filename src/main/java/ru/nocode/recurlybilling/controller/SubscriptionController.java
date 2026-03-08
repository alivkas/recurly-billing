package ru.nocode.recurlybilling.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.nocode.recurlybilling.data.dto.request.SubscriptionCancelRequest;
import ru.nocode.recurlybilling.data.dto.request.SubscriptionCreateRequest;
import ru.nocode.recurlybilling.data.dto.response.PaymentResponse;
import ru.nocode.recurlybilling.data.dto.response.SubscriptionResponse;
import ru.nocode.recurlybilling.data.dto.response.SubscriptionWithPaymentResponse;
import ru.nocode.recurlybilling.services.PaymentService;
import ru.nocode.recurlybilling.services.SubscriptionService;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;
    private final PaymentService paymentService;

    @PostMapping
    public ResponseEntity<?> createSubscription(
                                                  @RequestHeader("X-Tenant-ID") String tenantId,
                                                  @RequestHeader("X-API-Key") String apiKey,
                                                  @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                                  @Valid @RequestBody SubscriptionCreateRequest request) {

        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            idempotencyKey = UUID.randomUUID().toString();
        }

        log.info("Creating subscription for tenant: {} with customer: {}", tenantId, request.customerId());

        try {
            subscriptionService.validateTenantAndApiKey(tenantId, apiKey);
            SubscriptionResponse response = subscriptionService.createSubscription(tenantId, request, idempotencyKey);
            PaymentResponse paymentResponse = paymentService.getLastPaymentForSubscription(response.id());
            log.info("Successfully created subscription: {} for tenant: {}", response.id(), tenantId);

            if (paymentResponse != null && paymentResponse.confirmationUrl() != null) {
                SubscriptionWithPaymentResponse combined = new SubscriptionWithPaymentResponse(response, paymentResponse);
                return ResponseEntity.status(HttpStatus.CREATED).body(combined);
            }

            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid subscription creation request for tenant {}: {}", tenantId, e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Unexpected error creating subscription for tenant: {}", tenantId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/{subscriptionId}/cancel")
    public ResponseEntity<SubscriptionResponse> cancelSubscription(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-API-Key") String apiKey,
            @PathVariable String subscriptionId,
            @Valid @RequestBody SubscriptionCancelRequest request) {

        try {
            subscriptionService.validateTenantAndApiKey(tenantId, apiKey);

            SubscriptionResponse response = subscriptionService.cancelSubscription(tenantId, subscriptionId, request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.warn("Subscription not found or invalid cancel request: {} for tenant: {}", subscriptionId, tenantId);
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            log.warn("Cannot cancel subscription: {} - {}", subscriptionId, e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error canceling subscription: {} for tenant: {}", subscriptionId, tenantId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/{subscriptionId}")
    public ResponseEntity<SubscriptionResponse> getSubscription(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-API-Key") String apiKey,
            @PathVariable String subscriptionId) {

        try {
            subscriptionService.validateTenantAndApiKey(tenantId, apiKey);

            SubscriptionResponse response = subscriptionService.getSubscription(tenantId, subscriptionId);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.warn("Subscription not found: {} for tenant: {}", subscriptionId, tenantId);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error retrieving subscription: {} for tenant: {}", subscriptionId, tenantId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/customer/{externalId}")
    public ResponseEntity<List<SubscriptionResponse>> getSubscriptionsByCustomer(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-API-Key") String apiKey,
            @PathVariable String externalId) {

        try {
            subscriptionService.validateTenantAndApiKey(tenantId, apiKey);

            List<SubscriptionResponse> subscriptions = subscriptionService.getSubscriptionsByCustomer(tenantId, externalId);
            return ResponseEntity.ok(subscriptions);
        } catch (Exception e) {
            log.error("Error retrieving subscriptions for customer: {} in tenant: {}", externalId, tenantId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}