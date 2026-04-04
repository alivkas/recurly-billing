package ru.nocode.recurlybilling.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.nocode.recurlybilling.components.yoocassa.YooKassaSignatureValidator;
import ru.nocode.recurlybilling.data.dto.response.PaymentResponse;
import ru.nocode.recurlybilling.services.PaymentService;
import ru.nocode.recurlybilling.services.TenantService;
import ru.nocode.recurlybilling.utils.docs.PaymentDocs;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Tag(name = "💳 Платежи", description = PaymentDocs.TAG_DESCRIPTION)
public class PaymentController {

    private final PaymentService paymentService;
    private final TenantService tenantService;
    private final YooKassaSignatureValidator signatureValidator;
    private final org.springframework.core.env.Environment environment;

    @PostMapping("/webhook")
    @Operation(
            summary = PaymentDocs.WEBHOOK_SUMMARY,
            description = PaymentDocs.WEBHOOK_DESCRIPTION,
            tags = {"💳 Платежи"},
            security = {}
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "✅ Вебхук принят и обработан",
                    content = @Content(examples = @ExampleObject(value = ""))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "❌ Ошибка парсинга или обработки вебхука",
                    content = @Content(examples = @ExampleObject(
                            value = """
                    {
                      "timestamp": "2026-03-31T22:27:38Z",
                      "status": 400,
                      "error": "Bad Request",
                      "message": "Invalid webhook payload format"
                    }
                    """
                    ))
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "🚫 Неверная подпись X-Signature",
                    content = @Content(examples = @ExampleObject(
                            value = """
                    {
                      "timestamp": "2026-03-31T22:27:38Z",
                      "status": 403,
                      "error": "Forbidden",
                      "message": "Invalid webhook signature"
                    }
                    """
                    ))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "💥 Ошибка конфигурации или сервера",
                    content = @Content(examples = @ExampleObject(
                            value = """
                    {
                      "timestamp": "2026-03-31T22:27:38Z",
                      "status": 500,
                      "error": "Internal Server Error",
                      "message": "YooKassa secret key not configured"
                    }
                    """
                    ))
            )
    })
    public ResponseEntity<Void> handleYooKassaWebhook(
            @RequestHeader(value = "X-Signature", required = false) String signature,
            @RequestBody String requestBody) {

        log.info("Received webhook from YooKassa");

        String secretKey = environment.getProperty("yookassa.secret-key");
        if (secretKey == null || secretKey.trim().isEmpty()) {
            log.error("YooKassa secret key not configured");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }

//        if (!signatureValidator.isValid(signature, requestBody, secretKey)) {
//            log.warn("Invalid webhook signature");
//            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
//        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> webhook = mapper.readValue(requestBody, Map.class);
            String eventType = (String) webhook.get("event");

            if (eventType != null && eventType.startsWith("payment.")) {
                Map<String, Object> payment = (Map<String, Object>) webhook.get("object");

                String paymentId = (String) payment.get("id");
                String status = (String) payment.get("status");

                Map<String, Object> recipient = (Map<String, Object>) payment.get("recipient");
                String shopId = (String) recipient.get("account_id");

                String tenantId = tenantService.findTenantIdByYooKassaShopId(shopId)
                        .orElseThrow(() -> new SecurityException("Unknown YooKassa shop ID: " + shopId));

                paymentService.handleYooKassaWebhook(paymentId, status, webhook, tenantId);
                log.info("Successfully processed webhook for payment: {} in tenant: {}", paymentId, tenantId);
            }

            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Error processing YooKassa webhook", e);
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/{paymentId}")
    @Operation(
            summary = PaymentDocs.GET_SUMMARY,
            description = PaymentDocs.GET_DESCRIPTION,
            tags = {"💳 Платежи"},
            security = { @SecurityRequirement(name = "TenantAuth") }
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "✅ Данные платежа получены",
                    content = @Content(schema = @Schema(implementation = PaymentResponse.class))
            ),
            @ApiResponse(responseCode = "401", description = "🔐 Неверные учётные данные тенанта"),
            @ApiResponse(responseCode = "403", description = "🚫 Платёж не принадлежит этому тенанту"),
            @ApiResponse(responseCode = "404", description = "❌ Платёж не найден"),
            @ApiResponse(responseCode = "500", description = "💥 Ошибка сервера")
    })
    public ResponseEntity<PaymentResponse> getPayment(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-API-Key") String apiKey,
            @PathVariable String paymentId) {

        try {
            tenantService.validateTenantAndApiKey(tenantId, apiKey);

            PaymentResponse response = paymentService.getPaymentByTenant(paymentId, tenantId);
            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.warn("Invalid tenant or API key: {}", tenantId);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        } catch (IllegalStateException e) {
            log.warn("Payment not found or access denied: {} for tenant: {}", paymentId, tenantId);
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error retrieving payment: {}", paymentId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/success")
    @Operation(
            summary = PaymentDocs.SUCCESS_SUMMARY,
            description = PaymentDocs.SUCCESS_DESCRIPTION,
            tags = {"💳 Платежи"},
            security = {}
    )
    @ApiResponse(responseCode = "200", description = "✅ Простая страница подтверждения")
    public String success() {
        return "Оплата успешна!";
    }
}
