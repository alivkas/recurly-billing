package ru.nocode.recurlybilling.controllers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ru.nocode.recurlybilling.data.dto.request.PaymentCreateRequest;
import ru.nocode.recurlybilling.data.dto.request.YooKassaWebhookRequest;
import ru.nocode.recurlybilling.data.dto.response.PaymentResponse;
import ru.nocode.recurlybilling.services.PaymentService;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;

    @PostMapping
    public ResponseEntity<PaymentResponse> createPayment(@RequestBody PaymentCreateRequest request) {
        PaymentResponse response = paymentService.createPayment(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> handleYooKassaWebhook(@RequestBody YooKassaWebhookRequest request) {
        log.info("Received webhook from YooKassa: id={}, type={}", request.id(), request.type());

        try {
            Map<String, Object> event = objectMapper.convertValue(request.object(), Map.class);
            String paymentId = (String) ((Map<String, Object>) event.get("payment")).get("id");
            String status = (String) ((Map<String, Object>) event.get("payment")).get("status");

            paymentService.handleYooKassaWebhook(paymentId, status, event);

            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("Error processing webhook", e);
            return ResponseEntity.badRequest().build();
        }
    }
}
