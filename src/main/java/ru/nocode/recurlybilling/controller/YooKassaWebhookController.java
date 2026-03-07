package ru.nocode.recurlybilling.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.nocode.recurlybilling.components.yoocassa.YooKassaSignatureValidator;
import ru.nocode.recurlybilling.services.PaymentService;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/webhooks/yookassa")
@RequiredArgsConstructor
public class YooKassaWebhookController {

    private final PaymentService paymentService;
    private final YooKassaSignatureValidator signatureValidator;
    private final org.springframework.core.env.Environment environment;

    @PostMapping
    public ResponseEntity<Void> handleWebhook(
            @RequestHeader("X-Signature") String signature,
            @RequestBody String requestBody) {

        String secretKey = environment.getProperty("yookassa.secret-key");
        if (secretKey == null || secretKey.trim().isEmpty()) {
            log.error("YooKassa secret key not configured in application.properties");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }

        if (!signatureValidator.isValid(signature, requestBody, secretKey)) {
            log.warn("Invalid webhook signature");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        try {
            ObjectMapper mapper = new ObjectMapper();
            Map<String, Object> webhook = mapper.readValue(requestBody, Map.class);
            String eventType = (String) webhook.get("event");

            if (eventType != null && eventType.startsWith("payment.")) {
                Map<String, Object> object = (Map<String, Object>) webhook.get("object");
                Map<String, Object> payment = (Map<String, Object>) object.get("payment");

                String paymentId = (String) payment.get("id");
                String status = (String) payment.get("status");

                paymentService.handleYooKassaWebhook(paymentId, status, webhook);
            }

            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Error processing webhook", e);
            return ResponseEntity.badRequest().build();
        }
    }
}
