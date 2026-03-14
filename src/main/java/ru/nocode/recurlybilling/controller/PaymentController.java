package ru.nocode.recurlybilling.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.nocode.recurlybilling.components.yoocassa.YooKassaSignatureValidator;
import ru.nocode.recurlybilling.data.dto.response.PaymentResponse;
import ru.nocode.recurlybilling.services.PaymentService;
import ru.nocode.recurlybilling.services.TenantService;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final TenantService tenantService;
    private final YooKassaSignatureValidator signatureValidator;
    private final org.springframework.core.env.Environment environment;

    @PostMapping("/webhook")
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
    public String success() {
        return "Оплата успешна!";
    }
}
