package ru.nocode.recurlybilling.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.nocode.recurlybilling.data.dto.response.WebhookEventResponse;
import ru.nocode.recurlybilling.data.entities.WebhookEvent;
import ru.nocode.recurlybilling.data.repositories.WebhookEventRepository;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookEventService {

    private final WebhookEventRepository webhookEventRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public WebhookEventResponse logIncomingWebhook(String tenantId, String eventType,
                                                   String paymentId, String signature,
                                                   String rawRequestBody, Object parsedBody) {
        WebhookEvent event = new WebhookEvent();
        event.setId(UUID.randomUUID());
        event.setTenantId(tenantId);
        event.setEventType(eventType);
        event.setPaymentId(paymentId);
        event.setSignature(signature);
        event.setRawRequestBody(rawRequestBody);

        if (parsedBody != null) {
            try {
                event.setParsedBody(objectMapper.valueToTree(parsedBody));
            } catch (Exception e) {
                log.warn("Failed to serialize parsed body for webhook event {}", event.getId(), e);
            }
        }

        event.setStatus("received");
        event.setCreatedAt(LocalDateTime.now());

        WebhookEvent saved = webhookEventRepository.save(event);
        log.info("Webhook event logged: id={}, tenant={}, type={}",
                saved.getId(), saved.getTenantId(), saved.getEventType());

        return new WebhookEventResponse(
                saved.getId(),
                saved.getTenantId(),
                saved.getEventType(),
                saved.getPaymentId(),
                saved.getStatus(),
                saved.getCreatedAt()
        );
    }

    @Transactional
    public void updateWebhookStatus(UUID eventId, String status, String errorMessage) {
        WebhookEvent event = webhookEventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Webhook event not found: " + eventId));

        event.setStatus(status);
        if (errorMessage != null) {
            event.setProcessingError(errorMessage);
        }

        webhookEventRepository.save(event);
        log.info("Webhook event status updated: id={}, status={}", eventId, status);
    }

    @Transactional(readOnly = true)
    public List<WebhookEventResponse> getWebhookEventsByTenant(String tenantId) {
        return webhookEventRepository.findByTenantId(tenantId).stream()
                .map(e -> new WebhookEventResponse(
                        e.getId(),
                        e.getTenantId(),
                        e.getEventType(),
                        e.getPaymentId(),
                        e.getStatus(),
                        e.getCreatedAt()
                ))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<WebhookEventResponse> getWebhookEventsByPayment(String paymentId) {
        return webhookEventRepository.findByPaymentId(paymentId).stream()
                .map(e -> new WebhookEventResponse(
                        e.getId(),
                        e.getTenantId(),
                        e.getEventType(),
                        e.getPaymentId(),
                        e.getStatus(),
                        e.getCreatedAt()
                ))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public long countWebhooksByType(String tenantId, String eventType) {
        return webhookEventRepository.countByTenantIdAndEventType(tenantId, eventType);
    }
}
