package ru.nocode.recurlybilling.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import ru.nocode.recurlybilling.utils.docs.SubscriptionDocs;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/subscriptions")
@RequiredArgsConstructor
@Tag(name = "🔄 Подписки", description = SubscriptionDocs.TAG_DESCRIPTION)
public class SubscriptionController {

    private final SubscriptionService subscriptionService;
    private final PaymentService paymentService;

    @PostMapping
    @Operation(
            summary = SubscriptionDocs.CREATE_SUMMARY,
            description = SubscriptionDocs.CREATE_DESCRIPTION,
            tags = {"🔄 Подписки"},
            security = { @SecurityRequirement(name = "TenantAuth") }
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "✅ Подписка создана (с платёжом)",
                    content = @Content(
                            schema = @Schema(implementation = SubscriptionWithPaymentResponse.class),
                            examples = @ExampleObject(
                                    name = "Успех с платёжом",
                                    value = SubscriptionDocs.CREATE_SUCCESS_EXAMPLE
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "201",
                    description = "✅ Подписка создана (пробный период)",
                    content = @Content(
                            schema = @Schema(implementation = SubscriptionResponse.class),
                            examples = @ExampleObject(
                                    name = "Успех с триалом",
                                    value = SubscriptionDocs.CREATE_TRIAL_EXAMPLE
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "❌ Ошибка валидации или бизнес-правил",
                    content = @Content(examples = {
                            @ExampleObject(
                                    name = "Неверный planId",
                                    value = """
                        {
                          "timestamp": "2026-03-31T22:27:38Z",
                          "status": 400,
                          "error": "Bad Request",
                          "message": "Plan with ID 'plan_invalid' not found"
                        }
                        """
                            ),
                            @ExampleObject(
                                    name = "Клиент не найден",
                                    value = """
                        {
                          "timestamp": "2026-03-31T22:27:38Z",
                          "status": 400,
                          "error": "Bad Request",
                          "message": "Customer with ID 'cust_unknown' not found"
                        }
                        """
                            )
                    })
            ),
            @ApiResponse(responseCode = "401", description = "🔐 Отсутствуют заголовки авторизации"),
            @ApiResponse(responseCode = "403", description = "🚫 Недостаточно прав"),
            @ApiResponse(responseCode = "500", description = "💥 Ошибка сервера")
    })
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
            PaymentResponse paymentResponse = null;
            if (!"trialing".equals(response.status())) {
                paymentResponse = paymentService.getLastPaymentForSubscription(response.id());
            }
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
    @Operation(
            summary = SubscriptionDocs.CANCEL_SUMMARY,
            description = SubscriptionDocs.CANCEL_DESCRIPTION,
            tags = {"🔄 Подписки"},
            security = { @SecurityRequirement(name = "TenantAuth") }
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "✅ Подписка отменена",
                    content = @Content(schema = @Schema(implementation = SubscriptionResponse.class))
            ),
            @ApiResponse(responseCode = "400", description = "❌ Нельзя отменить: уже отменена или неверные параметры"),
            @ApiResponse(responseCode = "401", description = "🔐 Отсутствуют заголовки авторизации"),
            @ApiResponse(responseCode = "403", description = "🚫 Недостаточно прав"),
            @ApiResponse(responseCode = "404", description = "❌ Подписка не найдена"),
            @ApiResponse(responseCode = "500", description = "💥 Ошибка сервера")
    })
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
    @Operation(
            summary = SubscriptionDocs.GET_SUMMARY,
            description = SubscriptionDocs.GET_DESCRIPTION,
            tags = {"🔄 Подписки"},
            security = { @SecurityRequirement(name = "TenantAuth") }
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "✅ Данные подписки получены",
                    content = @Content(schema = @Schema(implementation = SubscriptionResponse.class))
            ),
            @ApiResponse(responseCode = "401", description = "🔐 Отсутствуют заголовки авторизации"),
            @ApiResponse(responseCode = "403", description = "🚫 Недостаточно прав"),
            @ApiResponse(responseCode = "404", description = "❌ Подписка не найдена"),
            @ApiResponse(responseCode = "500", description = "💥 Ошибка сервера")
    })
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
    @Operation(
            summary = SubscriptionDocs.LIST_BY_CUSTOMER_SUMMARY,
            description = SubscriptionDocs.LIST_BY_CUSTOMER_DESCRIPTION,
            tags = {"🔄 Подписки"},
            security = { @SecurityRequirement(name = "TenantAuth") }
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "✅ Список подписок получен",
                    content = @Content(schema = @Schema(implementation = SubscriptionResponse.class))
            ),
            @ApiResponse(responseCode = "401", description = "🔐 Отсутствуют заголовки авторизации"),
            @ApiResponse(responseCode = "403", description = "🚫 Недостаточно прав"),
            @ApiResponse(responseCode = "500", description = "💥 Ошибка сервера")
    })
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